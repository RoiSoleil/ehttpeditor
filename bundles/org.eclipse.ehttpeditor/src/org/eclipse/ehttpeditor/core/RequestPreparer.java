package org.eclipse.ehttpeditor.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;

/** Turns a request of a file into a {@link PreparedRequest}: variables, files, encoding, authentication. */
public final class RequestPreparer {

	private static final Pattern BASIC_USER_PASSWORD = Pattern.compile("^Basic\\s+(\\S+)\\s+(\\S+)$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern BASIC_USER_COLON_PASSWORD = Pattern.compile("^Basic\\s+([^\\s:]*:\\S*)$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern DIGEST = Pattern.compile("^Digest\\s+(\\S+)\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern FILE_INCLUSION = Pattern.compile("^<(@?)\\s+(\\S.*?)\\s*$");
	private static final Pattern DURATION = Pattern.compile("^(\\d+)\\s*(ms|s|m)?$", Pattern.CASE_INSENSITIVE);
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private RequestPreparer() {
	}

	/**
	 * Prepares a request.
	 *
	 * @param spec          the request as written
	 * @param variables     the variables
	 * @param baseDir       the folder of the file, for the relative paths
	 * @param globalHeaders the headers added by {@code client.global.headers}
	 * @param settings      the settings of the client
	 * @param trustAll      whether the environment asks to trust all the certificates
	 * @throws IOException              when a file of the body cannot be read
	 * @throws IllegalArgumentException when the URL is not valid
	 */
	public static PreparedRequest prepare(HttpRequestSpec spec, Variables variables, File baseDir,
			Map<String, String> globalHeaders, ClientSettings settings, boolean trustAll) throws IOException {
		Set<String> unresolved = new LinkedHashSet<>();
		boolean autoEncoding = !spec.hasDirective("no-auto-encoding");

		String url = variables.substitute(spec.url());
		unresolved.addAll(variables.unresolved());
		URI uri = toUri(url, autoEncoding);

		List<Header> headers = new ArrayList<>();
		String digestUser = null;
		String digestPassword = null;
		for (Header header : spec.headers()) {
			String name = variables.substitute(header.name());
			unresolved.addAll(variables.unresolved());
			String value = variables.substitute(header.value());
			unresolved.addAll(variables.unresolved());
			if (name.equalsIgnoreCase("Authorization")) {
				Matcher basic = BASIC_USER_PASSWORD.matcher(value);
				Matcher basicColon = BASIC_USER_COLON_PASSWORD.matcher(value);
				Matcher digest = DIGEST.matcher(value);
				if (basic.matches()) {
					value = "Basic " + base64(basic.group(1) + ":" + basic.group(2));
				} else if (basicColon.matches()) {
					value = "Basic " + base64(basicColon.group(1));
				} else if (digest.matches()) {
					digestUser = digest.group(1);
					digestPassword = digest.group(2);
					continue;
				}
			}
			headers.add(new Header(name, value));
		}
		for (Map.Entry<String, String> global : globalHeaders.entrySet()) {
			if (find(headers, global.getKey()) == null) {
				headers.add(new Header(global.getKey(), variables.substitute(global.getValue())));
				unresolved.addAll(variables.unresolved());
			}
		}
		if (settings.userAgent() != null && find(headers, "User-Agent") == null) {
			headers.add(new Header("User-Agent", settings.userAgent()));
		}

		byte[] body = null;
		if (spec.body() != null) {
			String text = variables.substitute(spec.body());
			unresolved.addAll(variables.unresolved());
			String contentType = find(headers, "Content-Type");
			body = buildBody(text, contentType, baseDir, variables, autoEncoding, unresolved);
		}

		Duration connectTimeout = duration(spec.directives().get("connection-timeout"), settings.connectTimeout());
		Duration readTimeout = duration(spec.directives().get("timeout"), settings.readTimeout());

		File output = null;
		boolean overwrite = false;
		if (spec.output() != null) {
			String path = variables.substitute(spec.output().path());
			unresolved.addAll(variables.unresolved());
			output = resolve(baseDir, path);
			overwrite = spec.output().overwrite();
		}

		HttpClient.Version version = null;
		if (spec.httpVersion() != null) {
			version = spec.httpVersion().startsWith("HTTP/2") ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1;
		}
		return new PreparedRequest(spec.method(), uri, version, List.copyOf(headers), body, digestUser, digestPassword,
				!spec.hasDirective("no-redirect"), !spec.hasDirective("no-cookie-jar"), !spec.hasDirective("no-log"),
				connectTimeout, readTimeout, trustAll, output, overwrite, List.copyOf(unresolved));
	}

	private static String find(List<Header> headers, String name) {
		for (Header header : headers) {
			if (header.name().equalsIgnoreCase(name)) {
				return header.value();
			}
		}
		return null;
	}

	private static String base64(String s) {
		return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	/** A path relative to the folder of the file, or absolute. */
	public static File resolve(File baseDir, String path) {
		String p = path.strip();
		if (p.startsWith("~/") || p.equals("~")) {
			p = System.getProperty("user.home") + p.substring(1);
		}
		File file = new File(p);
		if (!file.isAbsolute() && baseDir != null) {
			file = new File(baseDir, p);
		}
		return file;
	}

	/** {@code 600} (seconds), {@code 500 ms}, {@code 30 s}, {@code 2 m}; the default value when absent or bad. */
	static Duration duration(String value, Duration defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		Matcher matcher = DURATION.matcher(value.strip());
		if (!matcher.matches()) {
			return defaultValue;
		}
		long amount = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2) == null ? "s" : matcher.group(2).toLowerCase(Locale.ROOT);
		return switch (unit) {
		case "ms" -> Duration.ofMillis(amount);
		case "m" -> Duration.ofMinutes(amount);
		default -> Duration.ofSeconds(amount);
		};
	}

	private static byte[] buildBody(String text, String contentType, File baseDir, Variables variables,
			boolean autoEncoding, Set<String> unresolved) throws IOException {
		String mime = Bodies.mimeType(contentType);
		boolean multipart = mime.startsWith("multipart/");
		String[] lines = text.split("\n", -1);
		boolean hasInclusion = false;
		for (String line : lines) {
			if (FILE_INCLUSION.matcher(line.strip()).matches()) {
				hasInclusion = true;
				break;
			}
		}
		if (!hasInclusion) {
			if (multipart) {
				return text.replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
			}
			if (mime.equals("application/x-www-form-urlencoded")) {
				return formBody(text, autoEncoding).getBytes(StandardCharsets.UTF_8);
			}
			return text.getBytes(Bodies.charset(contentType));
		}
		String newLine = multipart ? "\r\n" : "\n";
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (line.endsWith("\r")) {
				line = line.substring(0, line.length() - 1);
			}
			Matcher inclusion = FILE_INCLUSION.matcher(line.strip());
			if (inclusion.matches()) {
				File file = resolve(baseDir, inclusion.group(2));
				if (!file.isFile()) {
					throw new IOException("File not found: " + file.getPath());
				}
				byte[] content = Files.readAllBytes(file.toPath());
				if (!inclusion.group(1).isEmpty()) {
					// <@ file: the variables of the file are replaced too.
					String replaced = variables.substitute(new String(content, StandardCharsets.UTF_8));
					unresolved.addAll(variables.unresolved());
					content = replaced.getBytes(StandardCharsets.UTF_8);
				}
				out.write(content);
			} else {
				out.write(line.getBytes(Bodies.charset(contentType)));
			}
			if (i < lines.length - 1) {
				out.write(newLine.getBytes(StandardCharsets.US_ASCII));
			}
		}
		return out.toByteArray();
	}

	/**
	 * The body of a form: the parameters may be on several lines and spaced ({@code key1 = value1 &}); the names
	 * and the values are encoded.
	 */
	static String formBody(String text, boolean autoEncoding) {
		StringBuilder joined = new StringBuilder();
		for (String line : text.split("\n")) {
			joined.append(line.strip());
		}
		StringBuilder sb = new StringBuilder();
		for (String parameter : joined.toString().split("&")) {
			String p = parameter.strip();
			if (p.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('&');
			}
			int equals = p.indexOf('=');
			String name = equals >= 0 ? p.substring(0, equals).strip() : p;
			String value = equals >= 0 ? p.substring(equals + 1).strip() : null;
			sb.append(autoEncoding ? encodeComponent(name) : name);
			if (value != null) {
				sb.append('=').append(autoEncoding ? encodeComponent(value) : value);
			}
		}
		return sb.toString();
	}

	/**
	 * The URI of a URL. Without a scheme, http:// is added. With the auto encoding, the characters not allowed are
	 * encoded, and in the query every character but the unreserved ones (a slash gives %2F); the sequences
	 * {@code %XX} already there stay.
	 */
	public static URI toUri(String url, boolean autoEncoding) {
		String u = url.strip();
		if (!u.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) {
			u = "http://" + u;
		}
		if (!autoEncoding) {
			try {
				return new URI(u);
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("Invalid URL (@no-auto-encoding): " + e.getMessage(), e);
			}
		}
		// A fragment is never sent: a # in the query is a character of a value (name=@#$x), encoded as %23.
		String query = null;
		int question = u.indexOf('?');
		if (question >= 0) {
			query = u.substring(question + 1);
			u = u.substring(0, question);
		}
		String fragment = null;
		int hash = u.indexOf('#');
		if (hash >= 0) {
			fragment = u.substring(hash + 1);
			u = u.substring(0, hash);
		}
		StringBuilder sb = new StringBuilder(encodeLoose(u));
		if (query != null) {
			sb.append('?');
			boolean first = true;
			for (String parameter : query.split("&", -1)) {
				if (!first) {
					sb.append('&');
				}
				first = false;
				int equals = parameter.indexOf('=');
				if (equals >= 0) {
					sb.append(encodeComponent(parameter.substring(0, equals))).append('=')
							.append(encodeComponent(parameter.substring(equals + 1)));
				} else {
					sb.append(encodeComponent(parameter));
				}
			}
		}
		if (fragment != null) {
			sb.append('#').append(encodeLoose(fragment));
		}
		try {
			return new URI(sb.toString());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid URL: " + e.getMessage(), e);
		}
	}

	/** Encodes the characters which are not allowed in a URI (spaces, non-ASCII...), keeps the others. */
	static String encodeLoose(String s) {
		StringBuilder sb = new StringBuilder();
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < bytes.length; i++) {
			int b = bytes[i] & 0xff;
			if (b == '%' && isEscape(bytes, i)) {
				sb.append('%');
			} else if (b > 0x20 && b < 0x7f && "\"<>\\^`{|}%".indexOf(b) < 0) {
				sb.append((char) b);
			} else {
				appendEscape(sb, b);
			}
		}
		return sb.toString();
	}

	/** Encodes all but the unreserved characters ({@code A-Z a-z 0-9 - . _ ~}); keeps the {@code %XX}. */
	public static String encodeComponent(String s) {
		StringBuilder sb = new StringBuilder();
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < bytes.length; i++) {
			int b = bytes[i] & 0xff;
			if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') || b == '-' || b == '.'
					|| b == '_' || b == '~') {
				sb.append((char) b);
			} else if (b == '%' && isEscape(bytes, i)) {
				sb.append('%');
			} else {
				appendEscape(sb, b);
			}
		}
		return sb.toString();
	}

	private static boolean isEscape(byte[] bytes, int i) {
		return i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2]);
	}

	private static boolean isHex(byte b) {
		return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
	}

	private static void appendEscape(StringBuilder sb, int b) {
		sb.append('%').append(HEX[b >> 4]).append(HEX[b & 0xf]);
	}
}

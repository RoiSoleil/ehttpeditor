package org.eclipse.ehttpeditor.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Helpers for the bodies: content types, charsets, compression and display. */
public final class Bodies {

	private Bodies() {
	}

	/** The MIME type of a Content-Type header ({@code application/json}), in lower case, or "". */
	public static String mimeType(String contentType) {
		if (contentType == null) {
			return "";
		}
		int semicolon = contentType.indexOf(';');
		return (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).strip().toLowerCase(Locale.ROOT);
	}

	/** The charset of a Content-Type header, or null. */
	public static String charsetName(String contentType) {
		if (contentType == null) {
			return null;
		}
		for (String part : contentType.split(";")) {
			String p = part.strip();
			if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
				String name = p.substring("charset=".length()).strip();
				if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
					name = name.substring(1, name.length() - 1);
				}
				return name;
			}
		}
		return null;
	}

	/** The charset of a Content-Type header, UTF-8 by default. */
	public static Charset charset(String contentType) {
		String name = charsetName(contentType);
		if (name != null) {
			try {
				return Charset.forName(name);
			} catch (RuntimeException e) {
				// Unknown charset.
			}
		}
		return StandardCharsets.UTF_8;
	}

	/** Whether the MIME type is JSON: {@code application/json}, {@code application/problem+json}... */
	public static boolean isJson(String contentType) {
		String mime = mimeType(contentType);
		return mime.equals("application/json") || mime.endsWith("+json") || mime.equals("text/json");
	}

	/** Whether the MIME type is XML or HTML. */
	public static boolean isXml(String contentType) {
		String mime = mimeType(contentType);
		return mime.endsWith("/xml") || mime.endsWith("+xml") || mime.equals("text/html");
	}

	/** Whether a body is text: a text MIME type, or bytes without control characters. */
	public static boolean isText(byte[] body, String contentType) {
		String mime = mimeType(contentType);
		if (mime.startsWith("text/") || isJson(contentType) || isXml(contentType)
				|| mime.equals("application/javascript") || mime.equals("application/x-www-form-urlencoded")) {
			return true;
		}
		if (mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/")
				|| mime.equals("application/octet-stream") || mime.equals("application/pdf")
				|| mime.equals("application/zip")) {
			return false;
		}
		int controls = 0;
		int length = Math.min(body.length, 4096);
		for (int i = 0; i < length; i++) {
			int b = body[i] & 0xff;
			if (b == 0) {
				return false;
			}
			if (b < 0x09 || (b > 0x0d && b < 0x20)) {
				controls++;
			}
		}
		return controls * 20 <= length;
	}

	/** The body as text for the user, or a summary when it is binary. */
	public static String toDisplayText(byte[] body, String contentType) {
		if (body == null) {
			return "";
		}
		if (!isText(body, contentType)) {
			return "<" + body.length + " bytes of " + (mimeType(contentType).isEmpty() ? "binary content"
					: mimeType(contentType)) + ">";
		}
		return new String(body, charset(contentType));
	}

	/** The body for the user, the JSON indented. */
	public static String toPrettyText(byte[] body, String contentType) {
		String text = toDisplayText(body, contentType);
		if (isJson(contentType) || (mimeType(contentType).isEmpty() && looksLikeJson(text))) {
			return Json.prettyPrint(text);
		}
		return text;
	}

	private static boolean looksLikeJson(String text) {
		String t = text.strip();
		return t.startsWith("{") && t.endsWith("}") || t.startsWith("[") && t.endsWith("]");
	}

	/** Decompresses a body according to its Content-Encoding (gzip, deflate); other encodings stay as is. */
	public static byte[] decode(byte[] body, String contentEncoding) throws IOException {
		if (contentEncoding == null || body.length == 0) {
			return body;
		}
		String encoding = contentEncoding.strip().toLowerCase(Locale.ROOT);
		switch (encoding) {
		case "gzip", "x-gzip":
			try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
				return in.readAllBytes();
			}
		case "deflate":
			// zlib by the standard, raw deflate by some servers.
			try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(body))) {
				return in.readAllBytes();
			} catch (IOException e) {
				try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(body), new Inflater(true))) {
					return in.readAllBytes();
				}
			}
		default:
			return body;
		}
	}

	/** The usual extension of the files of a content type, without the dot. */
	public static String extension(String contentType) {
		String mime = mimeType(contentType);
		if (isJson(contentType)) {
			return "json";
		}
		return switch (mime) {
		case "text/html" -> "html";
		case "application/xml", "text/xml" -> "xml";
		case "text/css" -> "css";
		case "application/javascript", "text/javascript" -> "js";
		case "text/csv" -> "csv";
		case "image/png" -> "png";
		case "image/jpeg" -> "jpg";
		case "image/gif" -> "gif";
		case "image/svg+xml" -> "svg";
		case "application/pdf" -> "pdf";
		case "application/zip" -> "zip";
		case "text/plain", "" -> "txt";
		default -> mime.endsWith("+xml") ? "xml" : "bin";
		};
	}
}

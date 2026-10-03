package org.eclipse.ehttpeditor.core;

import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;

/**
 * The Java side of the API of the scripts ({@code ehttpeditor-api.js} builds {@code client}, {@code request},
 * {@code response} and {@code crypto} on it). Every method takes and returns strings, numbers or booleans; the
 * lists go through JSON.
 */
public final class ScriptHost {

	private final HttpRequestSpec spec;
	private final Variables variables;
	private final ClientSession session;
	private final PreparedRequest prepared;
	private final HttpResponseData response;
	private final Consumer<String> log;
	private final List<TestResult> tests;
	private final BooleanSupplier cancelled;

	ScriptHost(HttpRequestSpec spec, Variables variables, ClientSession session, PreparedRequest prepared,
			HttpResponseData response, Consumer<String> log, List<TestResult> tests, BooleanSupplier cancelled) {
		this.spec = spec;
		this.variables = variables;
		this.session = session;
		this.prepared = prepared;
		this.response = response;
		this.log = log;
		this.tests = tests;
		this.cancelled = cancelled;
	}

	public void log(String text) {
		log.accept(text);
	}

	// client.global

	public void globalSet(String name, String value) {
		if (value == null) {
			session.globals().remove(name);
		} else {
			session.globals().put(name, value);
		}
	}

	public String globalGet(String name) {
		return session.globals().get(name);
	}

	public boolean globalIsEmpty() {
		return session.globals().isEmpty();
	}

	public void globalClear(String name) {
		session.globals().remove(name);
	}

	public void globalClearAll() {
		session.globals().clear();
	}

	public void globalHeaderSet(String name, String value) {
		session.globalHeaders().put(name, value);
	}

	public void globalHeaderClear(String name) {
		session.globalHeaders().keySet().removeIf(key -> key.equalsIgnoreCase(name));
	}

	// request

	public void requestVariableSet(String name, String value) {
		variables.requestVariables().put(name, value);
	}

	public String requestVariableGet(String name) {
		return variables.requestVariables().get(name);
	}

	public String environmentGet(String name) {
		Object value = variables.environment().get(name);
		if (value == null && !variables.environment().containsKey(name)) {
			return null;
		}
		return Variables.toText(value);
	}

	public String requestMethod() {
		return prepared != null ? prepared.method() : spec.method();
	}

	public String requestUrlRaw() {
		return spec.url();
	}

	public String requestUrlSubstituted() {
		return prepared != null ? prepared.uri().toString() : variables.substitute(spec.url());
	}

	public String requestBodyRaw() {
		return spec.body() != null ? spec.body() : "";
	}

	public String requestBodySubstituted() {
		if (prepared != null) {
			return prepared.bodyAsString();
		}
		return spec.body() != null ? variables.substitute(spec.body()) : "";
	}

	/** {@code [{"name": "...", "raw": "...", "value": "..."}]} */
	public String requestHeadersJson() {
		List<Object> list = new ArrayList<>();
		for (Header header : spec.headers()) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("name", header.name());
			map.put("raw", header.value());
			String value = prepared != null ? prepared.header(header.name()) : null;
			map.put("value", value != null ? value : variables.substitute(header.value()));
			list.add(map);
		}
		return Json.stringify(list);
	}

	// response

	public int responseStatus() {
		return response.status();
	}

	public String responseBody() {
		return response.text();
	}

	/** {@code [["name", "value"], ...]} */
	public String responseHeadersJson() {
		List<Object> list = new ArrayList<>();
		for (Header header : response.headers()) {
			list.add(List.of(header.name(), header.value()));
		}
		return Json.stringify(list);
	}

	public String responseMimeType() {
		return Bodies.mimeType(response.contentType());
	}

	public String responseCharset() {
		String charset = Bodies.charsetName(response.contentType());
		return charset != null ? charset : "UTF-8";
	}

	public boolean responseIsJson() {
		return Bodies.isJson(response.contentType());
	}

	/** {@code [{"name", "value", "domain", "path", "maxAge", "secure", "httpOnly"}]} */
	public String responseCookiesJson() {
		List<Object> list = new ArrayList<>();
		for (String setCookie : response.headers("Set-Cookie")) {
			try {
				for (HttpCookie cookie : HttpCookie.parse(setCookie)) {
					Map<String, Object> map = new LinkedHashMap<>();
					map.put("name", cookie.getName());
					map.put("value", cookie.getValue());
					map.put("domain", cookie.getDomain() != null ? cookie.getDomain() : response.uri().getHost());
					map.put("path", cookie.getPath() != null ? cookie.getPath() : "/");
					map.put("maxAge", cookie.getMaxAge());
					map.put("secure", cookie.getSecure());
					map.put("httpOnly", cookie.isHttpOnly());
					list.add(map);
				}
			} catch (IllegalArgumentException e) {
				// Not a valid cookie.
			}
		}
		return Json.stringify(list);
	}

	// tests

	public void testPassed(String name) {
		tests.add(new TestResult(name, true, null));
	}

	public void testFailed(String name, String message) {
		tests.add(new TestResult(name, false, message));
	}

	// timers

	public void sleep(double millis) throws InterruptedException {
		long end = System.currentTimeMillis() + (long) millis;
		while (System.currentTimeMillis() < end) {
			if (cancelled.getAsBoolean()) {
				throw new CancellationException("Script cancelled");
			}
			Thread.sleep(Math.min(50, Math.max(1, end - System.currentTimeMillis())));
		}
	}

	// crypto

	/** The hash or the HMAC (when keyHex is not null) of the bytes in hexadecimal; result in hexadecimal. */
	public String digest(String algorithm, String dataHex, String keyHex) throws Exception {
		byte[] data = HexFormat.of().parseHex(dataHex);
		String name = algorithm.toUpperCase(Locale.ROOT);
		if (keyHex != null) {
			String macName = "Hmac" + name.replace("-", "");
			Mac mac = Mac.getInstance(macName);
			mac.init(new SecretKeySpec(HexFormat.of().parseHex(keyHex), macName));
			return HexFormat.of().formatHex(mac.doFinal(data));
		}
		return HexFormat.of().formatHex(MessageDigest.getInstance(name).digest(data));
	}

	public String textToHex(String text, String encoding) {
		Charset charset = encoding == null || encoding.isEmpty() ? StandardCharsets.UTF_8 : Charset.forName(encoding);
		return HexFormat.of().formatHex(text.getBytes(charset));
	}

	public String base64ToHex(String base64, boolean urlSafe) {
		Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getMimeDecoder();
		return HexFormat.of().formatHex(decoder.decode(base64));
	}

	public String hexToBase64(String hex, boolean urlSafe) {
		byte[] bytes = HexFormat.of().parseHex(hex);
		return urlSafe ? Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
				: Base64.getEncoder().encodeToString(bytes);
	}

	public String textToBase64(String text, boolean urlSafe) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		return urlSafe ? Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
				: Base64.getEncoder().encodeToString(bytes);
	}

	public String base64ToText(String base64, boolean urlSafe) {
		Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getMimeDecoder();
		return new String(decoder.decode(base64), StandardCharsets.UTF_8);
	}

	public boolean cancelled() {
		return cancelled.getAsBoolean();
	}
}

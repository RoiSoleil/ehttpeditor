package org.eclipse.ehttpeditor.core;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the requests share during a session of the workbench: the global variables and headers set by the scripts
 * ({@code client.global}) and the cookies received.
 */
public final class ClientSession {

	private final Map<String, String> globals = new ConcurrentHashMap<>();
	private final Map<String, String> globalHeaders = new ConcurrentHashMap<>();
	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

	/** The global variables ({@code client.global.set}). */
	public Map<String, String> globals() {
		return globals;
	}

	/** The headers added to every request ({@code client.global.headers.set}). */
	public Map<String, String> globalHeaders() {
		return globalHeaders;
	}

	/** The cookie jar. */
	public CookieManager cookies() {
		return cookies;
	}

	/** Forgets the cookies. */
	public void clearCookies() {
		cookies.getCookieStore().removeAll();
	}

	/** Saves the cookies which have not expired, one per line as JSON. */
	public void saveCookies(File file) throws IOException {
		// The URI each cookie was received from, when the store still knows it (not for a Domain=... cookie).
		Map<HttpCookie, URI> received = new java.util.IdentityHashMap<>();
		for (URI uri : cookies.getCookieStore().getURIs()) {
			for (HttpCookie cookie : cookies.getCookieStore().get(uri)) {
				received.putIfAbsent(cookie, uri);
			}
		}
		List<String> lines = new ArrayList<>();
		for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
			if (cookie.hasExpired() || cookie.getMaxAge() == -1) {
				// Session cookies end with the session.
				continue;
			}
			URI uri = received.get(cookie);
			if (uri == null) {
				if (cookie.getDomain() == null) {
					continue;
				}
				String host = cookie.getDomain().startsWith(".") ? cookie.getDomain().substring(1) : cookie.getDomain();
				uri = URI.create((cookie.getSecure() ? "https://" : "http://") + host
						+ (cookie.getPath() != null ? cookie.getPath() : "/"));
			}
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("uri", uri.toString());
			map.put("name", cookie.getName());
			map.put("value", cookie.getValue());
			map.put("domain", cookie.getDomain());
			map.put("path", cookie.getPath());
			map.put("expires", System.currentTimeMillis() / 1000 + cookie.getMaxAge());
			map.put("secure", cookie.getSecure());
			map.put("httpOnly", cookie.isHttpOnly());
			lines.add(Json.stringify(map));
		}
		File parent = file.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
	}

	/** Loads the cookies saved by {@link #saveCookies(File)}; the bad lines are ignored. */
	public void loadCookies(File file) throws IOException {
		if (!file.isFile()) {
			return;
		}
		long now = System.currentTimeMillis() / 1000;
		for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
			try {
				if (!(Json.parse(line) instanceof Map<?, ?> map)) {
					continue;
				}
				long expires = ((Number) map.get("expires")).longValue();
				if (expires <= now) {
					continue;
				}
				HttpCookie cookie = new HttpCookie((String) map.get("name"), (String) map.get("value"));
				cookie.setDomain((String) map.get("domain"));
				cookie.setPath((String) map.get("path"));
				cookie.setMaxAge(expires - now);
				cookie.setSecure(Boolean.TRUE.equals(map.get("secure")));
				cookie.setHttpOnly(Boolean.TRUE.equals(map.get("httpOnly")));
				cookie.setVersion(0);
				cookies.getCookieStore().add(URI.create((String) map.get("uri")), cookie);
			} catch (RuntimeException e) {
				// A line which is not a cookie.
			}
		}
	}
}

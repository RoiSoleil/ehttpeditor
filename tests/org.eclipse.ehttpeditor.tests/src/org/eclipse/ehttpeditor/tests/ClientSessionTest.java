package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ehttpeditor.core.ClientSession;
import org.junit.jupiter.api.Test;

/** The cookie jar kept between the sessions of the workbench. */
public class ClientSessionTest {

	private static HttpCookie cookie(String name, String value, long maxAge) {
		HttpCookie cookie = new HttpCookie(name, value);
		cookie.setDomain("example.com");
		cookie.setPath("/");
		cookie.setMaxAge(maxAge);
		cookie.setVersion(0);
		return cookie;
	}

	@Test
	public void savedCookiesComeBack() throws IOException {
		ClientSession session = new ClientSession();
		URI uri = URI.create("http://example.com/");
		HttpCookie lasting = cookie("lasting", "1", 3600);
		lasting.setSecure(true);
		lasting.setHttpOnly(true);
		session.cookies().getCookieStore().add(uri, lasting);
		// A session cookie ends with the session.
		session.cookies().getCookieStore().add(uri, cookie("session", "2", -1));
		// A cookie received from a server, without Domain.
		session.cookies().put(URI.create("http://other.org/a"),
				java.util.Map.of("Set-Cookie", List.of("received=3; Max-Age=600; Path=/")));
		File file = new File(Files.createTempDirectory("ehttp").toFile(), "sub/cookies.jsonl");
		session.saveCookies(file);
		List<String> lines = Files.readAllLines(file.toPath());
		assertEquals(2, lines.size(), lines.toString());
		String lastingLine = lines.stream().filter(l -> l.contains("lasting")).findFirst().orElseThrow();
		// A line which is not a cookie and an expired cookie are ignored.
		Files.write(file.toPath(), List.of(lines.get(0), lines.get(1), "not json", "[1]",
				lastingLine.replaceAll("\"expires\":\\d+", "\"expires\":1").replace("lasting", "expired")));

		ClientSession restored = new ClientSession();
		restored.loadCookies(file);
		List<HttpCookie> cookies = restored.cookies().getCookieStore().getCookies();
		assertEquals("lasting,received", cookies.stream().map(HttpCookie::getName).sorted()
				.collect(Collectors.joining(",")));
		assertEquals(List.of("received=3"),
				restored.cookies().get(URI.create("http://other.org/b"), java.util.Map.of()).get("Cookie"));
		HttpCookie back = cookies.stream().filter(c -> c.getName().equals("lasting")).findFirst().orElseThrow();
		assertEquals("1", back.getValue());
		assertTrue(back.getSecure());
		assertTrue(back.isHttpOnly());
		assertTrue(back.getMaxAge() > 3500);

		restored.clearCookies();
		assertTrue(restored.cookies().getCookieStore().getCookies().isEmpty());
		// No file: nothing to load.
		restored.loadCookies(new File(file.getParentFile(), "missing"));
		assertTrue(restored.cookies().getCookieStore().getCookies().isEmpty());
	}
}

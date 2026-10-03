package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ehttpeditor.core.ClientSettings;
import org.eclipse.ehttpeditor.core.DynamicVariables;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.PreparedRequest;
import org.eclipse.ehttpeditor.core.RequestPreparer;
import org.eclipse.ehttpeditor.core.Variables;
import org.junit.jupiter.api.Test;

public class RequestPreparerTest {

	private static PreparedRequest prepare(String text, File baseDir, Map<String, String> environment)
			throws IOException {
		HttpFile file = HttpFile.parse(text);
		Variables variables = new Variables(new HashMap<>(), new HashMap<>(), file.variables(),
				new HashMap<>(environment), new DynamicVariables(null, null));
		return RequestPreparer.prepare(file.requests().get(0), variables, baseDir, Map.of("X-Global", "g"),
				new ClientSettings(Duration.ofSeconds(1), Duration.ofSeconds(2), null), false);
	}

	@Test
	public void variablesAndDefaults() throws IOException {
		PreparedRequest request = prepare("""
				@path = api
				# @timeout 500 ms
				// @connection-timeout 2 m
				POST {{host}}/{{path}}?q={{query}}
				Content-Type: application/json
				X-Token: {{token}}

				{"name": "{{name}}"}
				""", null, Map.of("host", "localhost:8080", "query", "a b/c", "name", "Ada"));
		assertEquals("http://localhost:8080/api?q=a%20b%2Fc", request.uri().toString());
		assertEquals("POST", request.method());
		assertEquals("{{token}}", request.header("X-Token"));
		assertEquals("g", request.header("X-Global"));
		assertEquals(List.of("token"), request.unresolved());
		assertEquals("{\"name\": \"Ada\"}", new String(request.body(), StandardCharsets.UTF_8));
		assertEquals(Duration.ofMillis(500), request.readTimeout());
		assertEquals(Duration.ofMinutes(2), request.connectTimeout());
		assertTrue(request.followRedirects());
		assertTrue(request.saveCookies());
		assertTrue(request.log());
	}

	@Test
	public void encoding() {
		assertEquals("https://h/a%20b/%C3%A9?x=%2F%40&y=%40%23&z#frag%20ment",
				RequestPreparer.toUri("https://h/a b/é?x=/@&y=%40%23&z#frag ment", true).toString());
		assertEquals("http://h/p?name=%40%23%24somebody&qwerty=%40%23%24",
				RequestPreparer.toUri("h/p?name=@%23$somebody&qwerty=%40%23%24", true).toString());
		assertEquals("http://h/p?a=%40", RequestPreparer.toUri("http://h/p?a=%40", false).toString());
		assertThrows(IllegalArgumentException.class, () -> RequestPreparer.toUri("http://h/a b", false));
		assertEquals("a%2Fb%20c%25", RequestPreparer.encodeComponent("a/b c%"));
	}

	@Test
	public void authorization() throws IOException {
		PreparedRequest basic = prepare("GET http://h\nAuthorization: Basic user pass\n", null, Map.of());
		assertEquals("Basic dXNlcjpwYXNz", basic.header("Authorization"));
		PreparedRequest colon = prepare("GET http://h\nAuthorization: Basic user:pass\n", null, Map.of());
		assertEquals("Basic dXNlcjpwYXNz", colon.header("Authorization"));
		PreparedRequest encoded = prepare("GET http://h\nAuthorization: Basic dXNlcjpwYXNz\n", null, Map.of());
		assertEquals("Basic dXNlcjpwYXNz", encoded.header("Authorization"));
		PreparedRequest digest = prepare("GET http://h\nAuthorization: Digest user pass\n", null, Map.of());
		assertNull(digest.header("Authorization"));
		assertEquals("user", digest.digestUser());
		assertEquals("pass", digest.digestPassword());
	}

	@Test
	public void directives() throws IOException {
		PreparedRequest request = prepare("""
				# @no-redirect
				# @no-cookie-jar
				# @no-log
				# @no-auto-encoding
				GET http://h/p?a=%40
				""", null, Map.of());
		assertFalse(request.followRedirects());
		assertFalse(request.saveCookies());
		assertFalse(request.log());
		assertEquals("http://h/p?a=%40", request.uri().toString());
	}

	@Test
	public void formBody() throws IOException {
		PreparedRequest request = prepare("""
				POST http://h/post
				Content-Type: application/x-www-form-urlencoded

				key1 = value 1 &
				key2 = a/b &
				key3 = {{v}}
				""", null, Map.of("v", "x y"));
		assertEquals("key1=value%201&key2=a%2Fb&key3=x%20y", new String(request.body(), StandardCharsets.UTF_8));
	}

	@Test
	public void filesInTheBody() throws IOException {
		File dir = Files.createTempDirectory("ehttpeditor").toFile();
		Files.writeString(new File(dir, "input.txt").toPath(), "file content");
		Files.writeString(new File(dir, "template.json").toPath(), "{\"id\": \"{{id}}\"}");
		PreparedRequest single = prepare("""
				POST http://h
				Content-Type: application/json

				<@ ./template.json
				""", dir, Map.of("id", "7"));
		assertEquals("{\"id\": \"7\"}", new String(single.body(), StandardCharsets.UTF_8));
		PreparedRequest multipart = prepare("""
				POST http://h
				Content-Type: multipart/form-data; boundary=boundary

				--boundary
				Content-Disposition: form-data; name="first"; filename="input.txt"

				< ./input.txt
				--boundary
				Content-Disposition: form-data; name="second"

				Text
				--boundary--
				""", dir, Map.of());
		assertArrayEquals(("--boundary\r\nContent-Disposition: form-data; name=\"first\"; filename=\"input.txt\"\r\n"
				+ "\r\nfile content\r\n--boundary\r\nContent-Disposition: form-data; name=\"second\"\r\n\r\nText\r\n"
				+ "--boundary--").getBytes(StandardCharsets.UTF_8), multipart.body());
		assertThrows(IOException.class, () -> prepare("POST http://h\n\n< ./missing.txt\n", dir, Map.of()));
	}

	@Test
	public void output() throws IOException {
		File dir = Files.createTempDirectory("ehttpeditor").toFile();
		PreparedRequest request = prepare("GET http://h\n\n>> out/{{name}}.json\n", dir, Map.of("name", "r"));
		assertEquals(new File(dir, "out/r.json"), request.output());
		assertFalse(request.overwrite());
	}
}

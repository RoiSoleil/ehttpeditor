package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.ehttpeditor.core.ClientSession;
import org.eclipse.ehttpeditor.core.ClientSettings;
import org.eclipse.ehttpeditor.core.Environments;
import org.eclipse.ehttpeditor.core.Execution;
import org.eclipse.ehttpeditor.core.HttpExecutor;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.RequestRunner;
import org.eclipse.ehttpeditor.core.RequestRunner.RunContext;
import org.eclipse.ehttpeditor.core.TestResult;
import org.eclipse.ehttpeditor.tests.TestServer.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The whole API of the scripts, the authentications and the corner cases of the HTTP client. */
public class ScriptApiTest {

	private TestServer server;
	private HttpExecutor executor;
	private ClientSession session;
	private File dir;
	private final AtomicBoolean cancelled = new AtomicBoolean();

	@BeforeEach
	public void setUp() throws IOException {
		server = new TestServer();
		executor = new HttpExecutor();
		session = new ClientSession();
		dir = Files.createTempDirectory("ehttp").toFile();
		Files.writeString(new File(dir, Environments.PUBLIC_FILE).toPath(),
				"{ \"local\": { \"base\": \"" + server.url() + "\", \"user\": {\"name\": \"Ada\"} } }");
	}

	@AfterEach
	public void tearDown() throws IOException {
		executor.close();
		server.close();
	}

	private Execution run(String text) {
		HttpFile file = HttpFile.parse(text);
		RunContext context = new RunContext("api.http", dir, dir, null, "local", session,
				new ClientSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), null), executor, cancelled::get);
		return RequestRunner.run(file, file.requests().get(0), context);
	}

	@Test
	public void clientAndConsole() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution execution = run("""
				GET {{base}}

				> {%
				client.global.set("a", 1);
				client.global.set("o", {x: 1});
				client.log(client.global.get("a"), client.global.get("o"), client.global.get("none"), client.global.isEmpty());
				client.global.clear("a");
				client.log(client.global.get("a"));
				client.global.set("o", null);
				client.global.set("keep", "k");
				client.global.headers.set("X-Global", "g");
				client.global.headers.clear("x-global");
				client.log("headers", JSON.stringify([]));
				console.info("info"); console.debug("debug"); console.warn("warn"); console.error("error", new Error("e"));
				client.assert(1 === 2, "outside of a test");
				client.assert(false);
				client.global.clearAll();
				client.log("empty", client.global.isEmpty());
				%}
				""");
		assertNull(execution.error(), execution.error());
		assertEquals(List.of("1 {\"x\":1} null false", "null", "headers []", "info", "debug", "WARN: warn",
				"ERROR: error Error: e", "empty true"), execution.console());
		assertEquals(List.of(new TestResult("outside of a test", false, "outside of a test"),
				new TestResult("Assertion failed", false, "Assertion failed")), execution.tests());
		assertTrue(session.globals().isEmpty());
		assertTrue(session.globalHeaders().isEmpty());
	}

	@Test
	public void requestAndResponse() {
		server.handler(r -> new Response(200, new java.util.LinkedHashMap<>(java.util.Map.of(
				"Content-Type", "application/json; charset=utf-8",
				"Set-Cookie", "sid=abc; Path=/; HttpOnly\nlang=fr; Max-Age=60; Secure")),
				"{not json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		Execution execution = run("""
				< {%
				client.log("pre", request.method, request.variables.get("none"), request.environment.get("missing"),
				  request.environment.get("user"), request.headers.all().length, request.headers.findByName("x-raw").getRawValue(),
				  request.headers.findByName("X-Raw").tryGetSubstitutedValue(), request.headers.findByName("nope"),
				  request.body.getRaw(), request.body.tryGetSubstituted(), request.iteration(), request.templateValue(0));
				request.variables.set("v", {a: 1});
				request.variables.set("empty", null);
				%}
				POST {{base}}/p
				X-Raw: {{base}}
				Content-Type: text/plain

				body {{v}}

				> {%
				client.log("body", response.body, response.contentType.charset, response.headers.valueOf("missing"));
				client.log("cookies", response.cookies().length, response.cookiesByName("lang")[0].secure,
				  response.cookiesByName("sid")[0].httpOnly, response.cookiesByName("sid")[0].path);
				client.log("request", request.url.tryGetSubstituted() === "SERVER/p", request.body.tryGetSubstituted(),
				  request.headers.findByName("X-Raw").value === "SERVER", request.variables.get("empty") === "");
				%}
				""".replace("SERVER", server.url()));
		assertNull(execution.error(), execution.error());
		assertEquals(List.of(
				"pre POST null null {\"name\":\"Ada\"} 2 {{base}} " + server.url() + " null body {{v}} body {{v}} 0 null",
				"body {not json utf-8 null", "cookies 2 true true /", "request true body {\"a\":1} true true"),
				execution.console());
		assertEquals("body {\"a\":1}", server.lastRequest().bodyText());
	}

	@Test
	public void crypto() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution execution = run("""
				GET {{base}}

				> {%
				client.log(crypto.sha1().updateWithText("abc").digest().toHex());
				client.log(crypto.sha384().updateWithHex("616263").digest().toBase64());
				client.log(crypto.sha512().updateWithBase64("YWJj").digest().toBase64(true));
				client.log(crypto.md5().updateWithText("\\u00e9", "ISO-8859-1").digest().toHex());
				client.log(crypto.hmac.sha1().withHexSecret("6B6579").updateWithText("abc").digest().toHex());
				client.log(crypto.hmac.sha512().withBase64Secret("a2V5").updateWithText("abc").digest().toHex().length);
				client.log(crypto.hmac.md5().withTextSecret("key").updateWithText("abc").digest().toHex());
				client.log(crypto.hmac.sha384().withBase64Secret("a2V5", true).updateWithBase64("YWJj", true).digest().toHex().length);
				client.log(btoa("\\u00e9"), atob("w6k="));
				%}
				""");
		assertNull(execution.error(), execution.error());
		// Expected values computed apart (Python hashlib, hmac, base64).
		assertEquals(List.of("a9993e364706816aba3e25717850c26c9cd0d89d",
				"ywB1P0WjXou1oD1pmsZQBycsMqsO3tFjGotgWkP/W+2AhgcroefMI1i67KE0yCWn",
				"3a81oZNherrMQXNJriBBMRLm-k6JqX6iCp7u5ktV05ohkpkqJ0_BqDa6PCOj_uu9RU1EI2Q86A4qmslPpUyknw",
				"3406877694691ddd1dfb0aca54681407", "4fd0b215276ef12f2b3e4c8ecac2811498b656fc", "128",
				"d2fe98063f876b03193afb49b4979591", "96", "w6k= é"), execution.console());
	}

	@Test
	public void scriptsWithJavaErrors() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution badHex = run("GET {{base}}\n\n> {% crypto.sha1().updateWithHex('zz').digest(); %}\n");
		assertTrue(badHex.error().startsWith("Script error: "), badHex.error());
		Execution thrown = run("GET {{base}}\n\n> {% throw new Error('boom'); %}\n");
		assertTrue(thrown.error().contains("boom"), thrown.error());
		Execution syntax = run("< ./missing-pre.js\nGET {{base}}\n");
		assertTrue(syntax.error().startsWith("Script not found"), syntax.error());
		assertNull(syntax.response());
	}

	@Test
	public void cancellation() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		cancelled.set(true);
		assertThrows(CancellationException.class, () -> run("""
				< {%
				sleep(5000);
				%}
				GET {{base}}
				"""));
		assertThrows(CancellationException.class, () -> run("""
				< {%
				client.test("swallowed?", function () { sleep(5000); });
				%}
				GET {{base}}
				"""));
		// The request itself: the server waits, the cancellation stops the wait.
		server.handler(r -> {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return Response.of(200, "text/plain", "late");
		});
		long start = System.currentTimeMillis();
		assertThrows(CancellationException.class, () -> run("GET {{base}}\n"));
		assertTrue(System.currentTimeMillis() - start < 2500);
	}

	@Test
	public void digestVariants() {
		for (String challenge : new String[] { "Digest realm=\"r\", nonce=\"n\", algorithm=SHA-256, qop=\"auth-int, auth\"",
				"Digest realm=\"r\", nonce=\"n\", algorithm=MD5-sess, qop=auth",
				"Digest realm=\"r\\\"x\", nonce=\"n\", algorithm=SHA-512-256",
				"Digest realm=r, nonce=n" }) {
			server.handler(r -> r.header("Authorization") == null
					? Response.of(401, "text/plain", "no").with("WWW-Authenticate", challenge)
					: Response.of(200, "text/plain", r.header("Authorization")));
			Execution execution = run("GET {{base}}/d?x=1\nAuthorization: Digest ada secret\n");
			assertEquals(200, execution.response().status(), challenge);
			String header = execution.response().text();
			assertTrue(header.contains("uri=\"/d?x=1\"") && header.contains("response=\""), header);
		}
		// Not a digest challenge: the 401 is the answer.
		server.handler(r -> Response.of(401, "text/plain", "no").with("WWW-Authenticate", "Basic realm=\"r\""));
		assertEquals(401, run("GET {{base}}\nAuthorization: Digest ada secret\n").response().status());
	}

	@Test
	public void redirectionsAcrossServers() throws IOException {
		try (TestServer other = new TestServer()) {
			other.handler(r -> Response.of(200, "text/plain", "auth=" + r.header("Authorization")));
			server.handler(r -> switch (r.target()) {
			case "/away" -> Response.of(307, null, "").with("Location", other.url() + "/there");
			case "/head" -> Response.of(302, null, "").with("Location", "/target path");
			default -> Response.of(200, "text/plain", r.method() + " " + r.target());
			});
			// The credentials stay with their server.
			Execution away = run("GET {{base}}/away\nAuthorization: Bearer t\n");
			assertEquals("auth=null", away.response().text());
			// HEAD stays HEAD; a space in the Location is encoded.
			Execution head = run("HEAD {{base}}/head\n");
			assertEquals(200, head.response().status());
			assertEquals("HEAD /target%20path", server.lastRequest().method() + " " + server.lastRequest().target());
		}
		server.handler(r -> Response.of(302, null, "").with("Location", "http://[bad"));
		Execution bad = run("GET {{base}}\n");
		assertTrue(bad.error().startsWith("Invalid redirection"), bad.error());
	}

	@Test
	public void badHeadersAndBodies() {
		server.handler(r -> new Response(200, java.util.Map.of("Content-Encoding", "gzip", "Content-Type",
				"text/plain"), "not gzip".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		Execution notGzip = run("GET {{base}}\n");
		assertEquals("not gzip", notGzip.response().text());
		assertTrue(notGzip.console().get(0).startsWith("The body could not be decompressed"), notGzip.console().toString());
		Execution badHeader = run("GET {{base}}\nX-Bad\u0001: v\n");
		assertTrue(badHeader.error() != null, String.valueOf(badHeader.error()));
		Execution badUrl = run("GET http://[::1\n");
		assertTrue(badUrl.error().startsWith("Invalid URL"), badUrl.error());
		Execution timeout = run("# @connection-timeout 1 s\nGET http://10.255.255.1:81/\n");
		assertTrue(timeout.error() != null, "no error for an unreachable host");
	}
}

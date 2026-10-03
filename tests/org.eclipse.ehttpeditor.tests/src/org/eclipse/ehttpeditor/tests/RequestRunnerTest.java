package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.eclipse.ehttpeditor.core.ClientSession;
import org.eclipse.ehttpeditor.core.ClientSettings;
import org.eclipse.ehttpeditor.core.Environments;
import org.eclipse.ehttpeditor.core.Execution;
import org.eclipse.ehttpeditor.core.HttpExecutor;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.HttpRequestSpec;
import org.eclipse.ehttpeditor.core.RequestRunner;
import org.eclipse.ehttpeditor.core.RequestRunner.RunContext;
import org.eclipse.ehttpeditor.core.TestResult;
import org.eclipse.ehttpeditor.tests.TestServer.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RequestRunnerTest {

	private TestServer server;
	private HttpExecutor executor;
	private ClientSession session;
	private File dir;

	@BeforeEach
	public void setUp() throws IOException {
		server = new TestServer();
		executor = new HttpExecutor();
		session = new ClientSession();
		dir = Files.createTempDirectory("ehttpeditor").toFile();
		Files.writeString(new File(dir, Environments.PUBLIC_FILE).toPath(),
				"{ \"local\": { \"base\": \"" + server.url() + "\", \"user\": \"ada\" } }");
	}

	@AfterEach
	public void tearDown() throws IOException {
		executor.close();
		server.close();
	}

	private List<Execution> run(String text) {
		HttpFile file = HttpFile.parse(text);
		RunContext context = new RunContext("test.http", dir, dir, new File(dir, "history"), "local", session,
				new ClientSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), "EHttpEditor-Test"), executor, () -> false);
		return file.requests().stream().map(request -> RequestRunner.run(file, request, context)).toList();
	}

	private Execution runOne(String text) {
		return run(text).get(0);
	}

	@Test
	public void getWithEnvironment() {
		server.handler(r -> Response.of(200, "application/json", "{\"hello\":\"" + r.target() + "\"}"));
		Execution execution = runOne("GET {{base}}/greet?name={{user}}\nAccept: application/json\n");
		assertNull(execution.error(), execution.error());
		assertEquals("local", execution.environment());
		assertEquals(200, execution.response().status());
		assertEquals("{\"hello\":\"/greet?name=ada\"}", execution.response().text());
		assertEquals("application/json", server.lastRequest().header("Accept"));
		assertEquals("EHttpEditor-Test", server.lastRequest().header("User-Agent"));
		assertTrue(execution.succeeded());
	}

	@Test
	public void postAndResponseHandler() {
		server.handler(r -> Response.of(201, "application/json; charset=utf-8",
				"{\"id\": 42, \"token\": \"abc\", \"items\": [{\"n\": 1}, {\"n\": 2}]}").with("X-Trace", "t1"));
		List<Execution> executions = run("""
				### Login
				POST {{base}}/login
				Content-Type: application/json

				{"user": "{{user}}"}

				> {%
				client.test("Created", function () {
				  client.assert(response.status === 201, "status " + response.status);
				});
				client.test("Body is JSON", () => {
				  client.assert(response.body.id === 42);
				  client.assert(response.contentType.mimeType === "application/json");
				  client.assert(response.contentType.charset === "utf-8");
				  client.assert(response.headers.valueOf("x-trace") === "t1");
				  client.assert(jsonPath(response.body, "$.items[1].n") === 2);
				  client.assert(jsonPath(response.body, "$.items[*].n").length === 2);
				});
				client.test("Fails", function () {
				  client.assert(false, "expected failure");
				});
				client.global.set("token", response.body.token);
				client.global.headers.set("X-Session", "{{token}}");
				client.log("logged in as", request.environment.get("user"), {a: 1});
				%}

				### Me
				GET {{base}}/me
				Authorization: Bearer {{token}}
				""");
		Execution login = executions.get(0);
		assertNull(login.error(), login.error());
		assertEquals("{\"user\": \"ada\"}", server.requests().get(0).bodyText());
		assertEquals(List.of(new TestResult("Created", true, null), new TestResult("Body is JSON", true, null),
				new TestResult("Fails", false, "expected failure")), login.tests());
		assertFalse(login.succeeded());
		assertTrue(login.console().contains("logged in as ada {\"a\":1}"), login.console().toString());
		assertEquals("abc", session.globals().get("token"));
		assertEquals("Bearer abc", server.lastRequest().header("Authorization"));
		assertEquals("abc", server.lastRequest().header("X-Session"));
	}

	@Test
	public void preRequestScript() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution execution = runOne("""
				< {%
				  const id = 40 + 2;
				  request.variables.set("id", id);
				  const start = Date.now();
				  await sleep(20);
				  let order = [];
				  setTimeout(() => order.push("timer"), 5);
				  order.push("script");
				  client.log(request.method, request.url.getRaw(), request.url.tryGetSubstituted(), Date.now() - start >= 20);
				  client.log(crypto.sha256().updateWithText("abc").digest().toHex());
				  client.log(crypto.hmac.sha256().withTextSecret("key").updateWithText("The quick brown fox jumps over the lazy dog").digest().toHex());
				  client.log(crypto.md5().updateWithText("abc").digest().toBase64());
				  client.log(btoa("user:pass"), atob("dXNlcjpwYXNz"));
				  setTimeout(() => client.log(order.join(",")), 10);
				%}
				GET {{base}}/items/{{id}}
				""");
		assertNull(execution.error(), execution.error());
		assertEquals("/items/42", server.lastRequest().target());
		assertEquals(List.of("GET {{base}}/items/{{id}} " + server.url() + "/items/42 true",
				"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
				"f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", "kAFQmDzST7DWlj99KOF/cg==",
				"dXNlcjpwYXNz user:pass", "script,timer"), execution.console());
	}

	@Test
	public void scriptErrors() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution syntax = runOne("""
				GET {{base}}

				> {%
				  client.log("a");
				  this is not javascript;
				%}
				""");
		assertNotNull(syntax.response());
		assertTrue(syntax.error().startsWith("Script error: test.http:5"), syntax.error());
		Execution exception = runOne("""
				< {%
				  undefinedFunction();
				%}
				GET {{base}}
				""");
		assertNull(exception.response());
		assertTrue(exception.error().contains("test.http:2") && exception.error().contains("undefinedFunction"),
				exception.error());
		Execution exit = runOne("""
				GET {{base}}

				> {%
				  client.log("before");
				  client.exit();
				  client.log("after");
				%}
				""");
		assertNull(exit.error(), exit.error());
		assertEquals(List.of("before"), exit.console());
	}

	@Test
	public void scriptFiles() throws IOException {
		Files.writeString(new File(dir, "handler.js").toPath(),
				"client.test('ok', function () { client.assert(response.body === 'ok'); });");
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution execution = runOne("GET {{base}}\n\n> ./handler.js\n");
		assertEquals(List.of(new TestResult("ok", true, null)), execution.tests());
		Execution missing = runOne("GET {{base}}\n\n> ./missing.js\n");
		assertTrue(missing.error().startsWith("Script not found"), missing.error());
	}

	@Test
	public void redirectsAndCookies() {
		server.handler(r -> switch (r.target()) {
		case "/login" -> Response.of(302, null, "").with("Location", "/home").with("Set-Cookie",
				"session=s1; Path=/");
		case "/home" -> Response.of(200, "text/plain", "home " + r.header("Cookie"));
		default -> Response.of(404, "text/plain", "no");
		});
		Execution followed = runOne("POST {{base}}/login\nContent-Type: text/plain\n\nx\n");
		assertEquals(200, followed.response().status());
		assertEquals("home session=s1", followed.response().text());
		assertEquals("GET", server.lastRequest().method());
		assertEquals(List.of("302 " + server.url() + "/home"), followed.response().redirects());

		Execution notFollowed = runOne("# @no-redirect\nGET {{base}}/login\n");
		assertEquals(302, notFollowed.response().status());

		session.clearCookies();
		runOne("# @no-cookie-jar\n# @no-redirect\nGET {{base}}/login\n");
		Execution withoutCookie = runOne("GET {{base}}/home\n");
		assertEquals("home null", withoutCookie.response().text());
	}

	@Test
	public void digestAuthentication() {
		server.handler(r -> {
			String authorization = r.header("Authorization");
			if (authorization == null) {
				return Response.of(401, "text/plain", "no").with("WWW-Authenticate",
						"Digest realm=\"test\", qop=\"auth\", nonce=\"n0nce\", opaque=\"op\"");
			}
			return Response.of(200, "text/plain", authorization);
		});
		Execution execution = runOne("GET {{base}}/secret\nAuthorization: Digest ada secret\n");
		assertEquals(200, execution.response().status());
		String header = execution.response().text();
		assertTrue(header.startsWith("Digest username=\"ada\", realm=\"test\", nonce=\"n0nce\", uri=\"/secret\""),
				header);
		assertTrue(header.contains("qop=auth") && header.contains("opaque=\"op\""), header);
	}

	@Test
	public void gzipAndOutput() throws IOException {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
			gzip.write("{\"zipped\": true}".getBytes(StandardCharsets.UTF_8));
		}
		server.handler(r -> new Response(200, java.util.Map.of("Content-Type", "application/json",
				"Content-Encoding", "gzip"), compressed.toByteArray()));
		Execution first = runOne("GET {{base}}\nAccept-Encoding: gzip\n\n>> out/result.json\n");
		assertEquals("{\"zipped\": true}", first.response().text());
		assertEquals(new File(dir, "out/result.json"), first.savedTo());
		Execution second = runOne("GET {{base}}\n\n>> out/result.json\n");
		assertEquals(new File(dir, "out/result-1.json"), second.savedTo());
		Execution third = runOne("GET {{base}}\n\n>>! out/result.json\n");
		assertEquals(new File(dir, "out/result.json"), third.savedTo());
		assertEquals("{\"zipped\": true}", Files.readString(third.savedTo().toPath()));
	}

	@Test
	public void connectionErrorsAndUnresolvedVariables() throws IOException {
		int port;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Execution refused = runOne("GET http://127.0.0.1:" + port + "/\n");
		assertNull(refused.response());
		assertTrue(refused.error().startsWith("Connection refused"), refused.error());
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution unresolved = runOne("GET {{base}}/{{nothing}}\n");
		assertTrue(unresolved.console().contains("Unresolved variables: nothing"), unresolved.console().toString());
	}

	@Test
	public void restrictedHeadersAreDropped() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		Execution execution = runOne("POST {{base}}\nContent-Length: 999\nConnection: keep-alive\n\nabc\n");
		assertNull(execution.error(), execution.error());
		assertEquals("abc", server.lastRequest().bodyText());
		assertEquals(2, execution.console().size());
	}

	@Test
	public void environmentNotDefined() {
		server.handler(r -> Response.of(200, "text/plain", "ok"));
		HttpFile file = HttpFile.parse("GET " + server.url() + "\n");
		HttpRequestSpec request = file.requests().get(0);
		RunContext context = new RunContext("test.http", dir, dir, null, "prod", session, ClientSettings.defaults(),
				executor, () -> false);
		Execution execution = RequestRunner.run(file, request, context);
		assertNull(execution.environment());
		assertTrue(execution.console().get(0).contains("'prod' is not defined"), execution.console().toString());
	}
}

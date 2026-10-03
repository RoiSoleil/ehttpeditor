package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ehttpeditor.core.DynamicVariables;
import org.eclipse.ehttpeditor.core.Environments;
import org.eclipse.ehttpeditor.core.Environments.Environment;
import org.eclipse.ehttpeditor.core.Json;
import org.eclipse.ehttpeditor.core.Variables;
import org.junit.jupiter.api.Test;

public class VariablesTest {

	private static Variables variables(Map<String, String> request, Map<String, String> globals,
			Map<String, String> file, Map<String, Object> environment) {
		return new Variables(new HashMap<>(request), new HashMap<>(globals), file, environment,
				new DynamicVariables("/project", "/history"));
	}

	@Test
	public void precedence() {
		Variables variables = variables(Map.of("a", "request"), Map.of("a", "global", "b", "global"),
				Map.of("a", "file", "b", "file", "c", "file"),
				Map.of("a", "env", "b", "env", "c", "env", "d", "env"));
		assertEquals("request global file env", variables.substitute("{{a}} {{b}} {{c}} {{d}}"));
	}

	@Test
	public void nestedAndUnresolved() {
		Variables variables = variables(Map.of(), Map.of(),
				Map.of("base", "http://{{host}}:{{port}}", "port", "8080"), Map.of("host", "localhost"));
		assertEquals("http://localhost:8080/api/{{ missing }}", variables.substitute("{{base}}/api/{{ missing }}"));
		assertEquals(List.of("missing"), variables.unresolved());
		assertEquals("http://localhost:8080", variables.resolve("base"));
	}

	@Test
	public void cycleStops() {
		Variables variables = variables(Map.of(), Map.of(), Map.of("a", "{{b}}", "b", "{{a}}"), Map.of());
		String result = variables.substitute("{{a}}");
		assertTrue(result.contains("{{"), result);
	}

	@Test
	public void objectsOfTheEnvironment() {
		@SuppressWarnings("unchecked")
		Map<String, Object> environment = (Map<String, Object>) Json
				.parse("{\"user\": {\"name\": \"Ada\", \"roles\": [\"admin\", \"dev\"]}, \"ids\": [1, 2]}");
		Variables variables = variables(Map.of(), Map.of("token", "{\"value\": \"t\"}"), Map.of(), environment);
		assertEquals("Ada admin dev 2 t",
				variables.substitute("{{user.name}} {{user.roles[0]}} {{user.roles[1]}} {{ids[1]}} {{token.value}}"));
		assertEquals("{\"name\":\"Ada\",\"roles\":[\"admin\",\"dev\"]}", variables.substitute("{{user}}"));
		assertEquals("[1,2]", variables.substitute("{{ids}}"));
	}

	@Test
	public void dynamicVariables() {
		Variables variables = variables(Map.of(), Map.of(), Map.of(), Map.of());
		assertTrue(variables.substitute("{{$uuid}}").matches("[0-9a-f-]{36}"));
		assertTrue(variables.substitute("{{$random.uuid}}").matches("[0-9a-f-]{36}"));
		assertTrue(variables.substitute("{{$timestamp}}").matches("\\d{10,}"));
		assertTrue(variables.substitute("{{$isoTimestamp}}").matches("\\d{4}-\\d\\d-\\d\\dT.*Z"));
		int value = Integer.parseInt(variables.substitute("{{$random.integer(5, 7)}}"));
		assertTrue(value == 5 || value == 6);
		assertTrue(variables.substitute("{{$random.alphabetic(12)}}").matches("[A-Za-z]{12}"));
		assertTrue(variables.substitute("{{$random.hexadecimal(8)}}").matches("[0-9a-f]{8}"));
		assertTrue(variables.substitute("{{$random.email}}").matches("[a-z]+@[a-z]+\\.com"));
		assertEquals("/project /history", variables.substitute("{{$projectRoot}} {{$historyFolder}}"));
		assertEquals(System.getenv("PATH"), variables.substitute("{{$env.PATH}}"));
		assertEquals("{{$nope}}", variables.substitute("{{$nope}}"));
	}

	@Test
	public void environments() throws IOException {
		File root = Files.createTempDirectory("ehttpeditor").toFile();
		File sub = new File(root, "api");
		sub.mkdirs();
		Files.writeString(new File(root, Environments.PUBLIC_FILE).toPath(), """
				{
				  "$shared": { "version": "v1", "host": "shared" },
				  "dev": { "host": "localhost", "user": "dev" },
				  "prod": { "host": "example.com" }
				}
				""");
		Files.writeString(new File(sub, Environments.PUBLIC_FILE).toPath(), """
				{ "dev": { "user": "api-dev", "SSLConfiguration": { "verifyHostCertificate": false } } }
				""");
		Files.writeString(new File(root, Environments.PRIVATE_FILE).toPath(), """
				{ "dev": { "password": "secret" }, "$shared": { "version": "v2" } }
				""");
		Environments environments = Environments.load(sub, root);
		assertEquals(List.of("dev", "prod"), environments.names());
		assertEquals(3, environments.files().size());
		Environment dev = environments.get("dev");
		assertEquals("localhost", dev.variables().get("host"));
		assertEquals("api-dev", dev.variables().get("user"));
		assertEquals("secret", dev.variables().get("password"));
		assertEquals("v2", dev.variables().get("version"));
		assertNull(dev.variables().get("SSLConfiguration"));
		assertTrue(dev.trustAllCertificates());
		Environment prod = environments.get("prod");
		assertEquals("example.com", prod.variables().get("host"));
		assertTrue(!prod.trustAllCertificates());
		Environment none = environments.get(null);
		assertEquals("shared", none.variables().get("host"));
		assertNull(none.name());
	}

	@Test
	public void environmentErrors() {
		Environments environments = Environments.of("{ \"dev\": { \"a\": 1, } }", "{ broken");
		assertEquals(List.of("dev"), environments.names());
		assertEquals(1L, environments.get("dev").variables().get("a"));
		assertEquals(1, environments.errors().size());
	}
}

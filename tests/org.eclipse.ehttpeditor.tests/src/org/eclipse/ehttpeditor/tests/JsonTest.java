package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.eclipse.ehttpeditor.core.Json;
import org.junit.jupiter.api.Test;

public class JsonTest {

	@Test
	public void parseAndStringify() {
		Object value = Json.parse("{\"a\": [1, 2.5, true, null, \"x\\n\\u00e9\"], \"b\": {}}");
		assertEquals(Map.of("a", java.util.Arrays.asList(1L, 2.5, true, null, "x\né"), "b", Map.of()), value);
		assertEquals("{\"a\":[1,2.5,true,null,\"x\\né\"],\"b\":{}}", Json.stringify(value));
		assertEquals(List.of(), Json.parse(" [ ] "));
	}

	@Test
	public void errors() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Json.parse("{\n  \"a\" 1 }"));
		assertEquals("Expected ':' (line 2, column 7)", e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2"));
		assertThrows(IllegalArgumentException.class, () -> Json.parse("{} x"));
	}

	@Test
	public void prettyPrint() {
		assertEquals("""
				{
				  "a": [
				    1,
				    1.50
				  ],
				  "b": {},
				  "c": "x, {y}: [z]"
				}""", Json.prettyPrint("{\"a\":[1,1.50],\"b\":{ },\"c\":\"x, {y}: [z]\"}"));
		assertEquals("not json", Json.prettyPrint("not json"));
		assertEquals("", Json.prettyPrint(""));
		assertEquals("[\n  \"a\\\"]\",\n  []\n]", Json.prettyPrint("[\"a\\\"]\", [ ]]"));
	}

	@Test
	public void escapesAndNumbers() {
		assertEquals("\t\b\f\r/\"\\x", Json.parse("\"\\t\\b\\f\\r\\/\\\"\\\\\\x\""));
		assertEquals(123456789012345678901234567890.0, Json.parse("123456789012345678901234567890"));
		assertEquals(-1.5e3, Json.parse("-1.5e3"));
		assertEquals(Boolean.FALSE, Json.parse("false"));
		assertEquals(null, Json.parse(" null "));
		// Comments and trailing commas, tolerated in the environment files.
		assertEquals(Map.of("a", List.of(1L)), Json.parse("// the a\n{\"a\": [1, ], }"));
	}

	@Test
	public void moreErrors() {
		for (String bad : new String[] { "", "\"abc", "\"\\", "\"\\u12\"", "\"\\uzzzz\"", "{1: 2}", "[1 2]",
				"{\"a\": 1 \"b\"}", "tru", "-", "@" }) {
			assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
		}
	}

	@Test
	public void stringifyOfEverything() {
		Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("double", 2.5);
		map.put("round", 3.0);
		map.put("big", 1e20);
		map.put("bool", true);
		map.put("other", java.time.Duration.ofSeconds(1));
		map.put("controls", "\t\b\f\r\u0001");
		assertEquals("{\"double\":2.5,\"round\":3,\"big\":1.0E20,\"bool\":true,\"other\":\"PT1S\","
				+ "\"controls\":\"\\t\\b\\f\\r\\u0001\"}", Json.stringify(map));
		assertEquals("null", Json.stringify(null));
	}
}

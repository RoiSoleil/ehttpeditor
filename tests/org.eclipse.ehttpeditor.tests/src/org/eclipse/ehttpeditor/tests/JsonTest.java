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
	}
}

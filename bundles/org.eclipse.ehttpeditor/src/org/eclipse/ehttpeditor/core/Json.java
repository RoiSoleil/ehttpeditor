package org.eclipse.ehttpeditor.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer: the environment files, the values of the variables and the pretty printing of
 * the responses. Objects are read as {@link LinkedHashMap}, arrays as {@link ArrayList}, numbers as {@link Long} or
 * {@link Double}.
 */
public final class Json {

	private final String text;
	private int pos;

	private Json(String text) {
		this.text = text;
	}

	/** Reads a JSON text; throws {@link IllegalArgumentException} with the position of the error. */
	public static Object parse(String text) {
		Json json = new Json(text);
		json.skipWhitespace();
		Object value = json.readValue();
		json.skipWhitespace();
		if (json.pos < text.length()) {
			throw json.error("Unexpected character '" + text.charAt(json.pos) + "'");
		}
		return value;
	}

	private Object readValue() {
		if (pos >= text.length()) {
			throw error("Unexpected end of the JSON text");
		}
		char c = text.charAt(pos);
		switch (c) {
		case '{':
			return readObject();
		case '[':
			return readArray();
		case '"':
			return readString();
		case 't':
			expect("true");
			return Boolean.TRUE;
		case 'f':
			expect("false");
			return Boolean.FALSE;
		case 'n':
			expect("null");
			return null;
		default:
			if (c == '-' || (c >= '0' && c <= '9')) {
				return readNumber();
			}
			throw error("Unexpected character '" + c + "'");
		}
	}

	private Map<String, Object> readObject() {
		Map<String, Object> map = new LinkedHashMap<>();
		pos++;
		skipWhitespace();
		if (peek() == '}') {
			pos++;
			return map;
		}
		while (true) {
			skipWhitespace();
			if (peek() != '"') {
				throw error("Expected a property name");
			}
			String key = readString();
			skipWhitespace();
			if (peek() != ':') {
				throw error("Expected ':'");
			}
			pos++;
			skipWhitespace();
			map.put(key, readValue());
			skipWhitespace();
			char c = peek();
			pos++;
			if (c == '}') {
				return map;
			}
			if (c != ',') {
				throw error("Expected ',' or '}'");
			}
			skipWhitespace();
			// Trailing comma, accepted as in the environment files of IntelliJ.
			if (peek() == '}') {
				pos++;
				return map;
			}
		}
	}

	private List<Object> readArray() {
		List<Object> list = new ArrayList<>();
		pos++;
		skipWhitespace();
		if (peek() == ']') {
			pos++;
			return list;
		}
		while (true) {
			skipWhitespace();
			list.add(readValue());
			skipWhitespace();
			char c = peek();
			pos++;
			if (c == ']') {
				return list;
			}
			if (c != ',') {
				throw error("Expected ',' or ']'");
			}
			skipWhitespace();
			if (peek() == ']') {
				pos++;
				return list;
			}
		}
	}

	private String readString() {
		StringBuilder sb = new StringBuilder();
		pos++;
		while (true) {
			if (pos >= text.length()) {
				throw error("Unterminated string");
			}
			char c = text.charAt(pos++);
			if (c == '"') {
				return sb.toString();
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			if (pos >= text.length()) {
				throw error("Unterminated string");
			}
			char e = text.charAt(pos++);
			switch (e) {
			case 'n' -> sb.append('\n');
			case 't' -> sb.append('\t');
			case 'r' -> sb.append('\r');
			case 'b' -> sb.append('\b');
			case 'f' -> sb.append('\f');
			case 'u' -> {
				if (pos + 4 > text.length()) {
					throw error("Invalid unicode escape");
				}
				try {
					sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
				} catch (NumberFormatException ex) {
					throw error("Invalid unicode escape");
				}
				pos += 4;
			}
			default -> sb.append(e);
			}
		}
	}

	private Object readNumber() {
		int start = pos;
		if (peek() == '-') {
			pos++;
		}
		boolean decimal = false;
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (c >= '0' && c <= '9') {
				pos++;
			} else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
				decimal = true;
				pos++;
			} else {
				break;
			}
		}
		String number = text.substring(start, pos);
		try {
			if (!decimal) {
				try {
					return Long.valueOf(number);
				} catch (NumberFormatException e) {
					// Too big for a long.
				}
			}
			return Double.valueOf(number);
		} catch (NumberFormatException e) {
			throw error("Invalid number " + number);
		}
	}

	private void expect(String word) {
		if (!text.startsWith(word, pos)) {
			throw error("Expected " + word);
		}
		pos += word.length();
	}

	private char peek() {
		return pos < text.length() ? text.charAt(pos) : '\0';
	}

	private void skipWhitespace() {
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (Character.isWhitespace(c)) {
				pos++;
			} else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
				// Comments are tolerated in the environment files.
				while (pos < text.length() && text.charAt(pos) != '\n') {
					pos++;
				}
			} else {
				break;
			}
		}
	}

	private IllegalArgumentException error(String message) {
		int line = 1;
		int column = 1;
		for (int i = 0; i < Math.min(pos, text.length()); i++) {
			if (text.charAt(i) == '\n') {
				line++;
				column = 1;
			} else {
				column++;
			}
		}
		return new IllegalArgumentException(message + " (line " + line + ", column " + column + ")");
	}

	/** Writes a value read by {@link #parse(String)} (or made of maps, lists, strings, numbers and booleans). */
	public static String stringify(Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value);
		return sb.toString();
	}

	private static void write(StringBuilder sb, Object value) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof String s) {
			quote(sb, s);
		} else if (value instanceof Double d) {
			if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
				sb.append(d.longValue());
			} else {
				sb.append(d);
			}
		} else if (value instanceof Number || value instanceof Boolean) {
			sb.append(value);
		} else if (value instanceof Map<?, ?> map) {
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				quote(sb, String.valueOf(entry.getKey()));
				sb.append(':');
				write(sb, entry.getValue());
			}
			sb.append('}');
		} else if (value instanceof List<?> list) {
			sb.append('[');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				write(sb, list.get(i));
			}
			sb.append(']');
		} else {
			quote(sb, value.toString());
		}
	}

	/** Appends a JSON string literal. */
	public static void quote(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"' -> sb.append("\\\"");
			case '\\' -> sb.append("\\\\");
			case '\n' -> sb.append("\\n");
			case '\r' -> sb.append("\\r");
			case '\t' -> sb.append("\\t");
			case '\b' -> sb.append("\\b");
			case '\f' -> sb.append("\\f");
			default -> {
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
			}
		}
		sb.append('"');
	}

	/**
	 * Indents a JSON text without reading it as values: the numbers and the strings stay as the server wrote them.
	 * Returns the text unchanged when it does not look like JSON.
	 */
	public static String prettyPrint(String json) {
		String trimmed = json.strip();
		if (trimmed.isEmpty() || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) {
			return json;
		}
		StringBuilder sb = new StringBuilder(trimmed.length() * 2);
		int indent = 0;
		boolean inString = false;
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (inString) {
				sb.append(c);
				if (c == '\\' && i + 1 < trimmed.length()) {
					sb.append(trimmed.charAt(++i));
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			switch (c) {
			case '"' -> {
				inString = true;
				sb.append(c);
			}
			case '{', '[' -> {
				// Empty object or array on one line.
				int next = nextNonBlank(trimmed, i + 1);
				char closing = c == '{' ? '}' : ']';
				if (next < trimmed.length() && trimmed.charAt(next) == closing) {
					sb.append(c).append(closing);
					i = next;
				} else {
					sb.append(c);
					indent++;
					newLine(sb, indent);
				}
			}
			case '}', ']' -> {
				indent = Math.max(0, indent - 1);
				newLine(sb, indent);
				sb.append(c);
			}
			case ',' -> {
				sb.append(c);
				newLine(sb, indent);
			}
			case ':' -> sb.append(": ");
			default -> {
				if (!Character.isWhitespace(c)) {
					sb.append(c);
				}
			}
			}
		}
		return sb.toString();
	}

	private static int nextNonBlank(String s, int from) {
		int i = from;
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		return i;
	}

	private static void newLine(StringBuilder sb, int indent) {
		sb.append('\n');
		for (int i = 0; i < indent; i++) {
			sb.append("  ");
		}
	}
}

package org.eclipse.ehttpeditor.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The variables of a request and the replacement of the {@code {{variables}}}. The first found wins:
 * <ol>
 * <li>the variables of the request ({@code request.variables.set} in a pre-request script),</li>
 * <li>the global variables ({@code client.global.set} in a script),</li>
 * <li>the in-place variables of the file ({@code @host = localhost}),</li>
 * <li>the variables of the environment,</li>
 * <li>the dynamic variables ({@code $uuid}, {@code $random.integer(1, 10)}...).</li>
 * </ol>
 * A name such as {@code user.address.city} or {@code ids[0]} reads into an object or an array of the environment
 * (or into a value which is JSON).
 */
public final class Variables {

	/** The pattern of a {@code {{variable}}}. */
	public static final Pattern REFERENCE = Pattern.compile("\\{\\{\\s*(.*?)\\s*\\}\\}");

	private static final int MAX_DEPTH = 10;

	/** Where a value comes from. */
	public enum Source {
		REQUEST("request variable"), GLOBAL("global variable"), FILE("in-place variable"),
		ENVIRONMENT("environment variable"), DYNAMIC("dynamic variable");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		/** For the user: "global variable"... */
		public String label() {
			return label;
		}
	}

	/** A value found for a name, before the replacement of the variables it contains. */
	public record Value(String raw, Source source) {
	}

	private final Map<String, String> requestVariables;
	private final Map<String, String> globals;
	private final Map<String, String> inPlace;
	private final Map<String, Object> environment;
	private final DynamicVariables dynamic;
	private final Set<String> unresolved = new LinkedHashSet<>();

	/**
	 * @param requestVariables the variables of the request, changed by the pre-request scripts
	 * @param globals          the global variables, changed by the scripts
	 * @param inPlace          the in-place variables of the file
	 * @param environment      the variables of the environment
	 * @param dynamic          the dynamic variables
	 */
	public Variables(Map<String, String> requestVariables, Map<String, String> globals, Map<String, String> inPlace,
			Map<String, Object> environment, DynamicVariables dynamic) {
		this.requestVariables = requestVariables;
		this.globals = globals;
		this.inPlace = inPlace;
		this.environment = environment;
		this.dynamic = dynamic;
	}

	/** The variables of the request (changed by the pre-request scripts). */
	public Map<String, String> requestVariables() {
		return requestVariables;
	}

	/** The variables of the environment. */
	public Map<String, Object> environment() {
		return environment;
	}

	/** All the names known, for the content assist: request, global, in-place and environment variables. */
	public Map<String, Value> all() {
		Map<String, Value> all = new LinkedHashMap<>();
		environment.forEach((name, value) -> all.put(name, new Value(toText(value), Source.ENVIRONMENT)));
		inPlace.forEach((name, value) -> all.put(name, new Value(value, Source.FILE)));
		globals.forEach((name, value) -> all.put(name, new Value(value, Source.GLOBAL)));
		requestVariables.forEach((name, value) -> all.put(name, new Value(value, Source.REQUEST)));
		return all;
	}

	/** The names of the variables which were not found by {@link #substitute(String)} since the last call. */
	public List<String> unresolved() {
		return new ArrayList<>(unresolved);
	}

	/** The value of a variable, raw (it may contain other variables), or null. */
	public Value lookup(String name) {
		if (name.startsWith("$")) {
			String value = dynamic.resolve(name);
			return value != null ? new Value(value, Source.DYNAMIC) : null;
		}
		Value value = lookupSimple(name);
		if (value != null) {
			return value;
		}
		// A path into an object: user.address.city, ids[0], users[1].name.
		List<Object> path = parsePath(name);
		if (path.size() < 2 || !(path.get(0) instanceof String root)) {
			return null;
		}
		Object current;
		Source source;
		if (requestVariables.containsKey(root)) {
			current = parseJson(requestVariables.get(root));
			source = Source.REQUEST;
		} else if (globals.containsKey(root)) {
			current = parseJson(globals.get(root));
			source = Source.GLOBAL;
		} else if (inPlace.containsKey(root)) {
			current = parseJson(substitute(inPlace.get(root), 1));
			source = Source.FILE;
		} else if (environment.containsKey(root)) {
			current = environment.get(root);
			if (current instanceof String s) {
				current = parseJson(s);
			}
			source = Source.ENVIRONMENT;
		} else {
			return null;
		}
		for (int i = 1; i < path.size(); i++) {
			Object step = path.get(i);
			if (step instanceof String key && current instanceof Map<?, ?> map && map.containsKey(key)) {
				current = map.get(key);
			} else if (step instanceof Integer index && current instanceof List<?> list && index >= 0
					&& index < list.size()) {
				current = list.get(index);
			} else {
				return null;
			}
		}
		return new Value(toText(current), source);
	}

	private Value lookupSimple(String name) {
		if (requestVariables.containsKey(name)) {
			return new Value(requestVariables.get(name), Source.REQUEST);
		}
		if (globals.containsKey(name)) {
			return new Value(globals.get(name), Source.GLOBAL);
		}
		if (inPlace.containsKey(name)) {
			return new Value(inPlace.get(name), Source.FILE);
		}
		if (environment.containsKey(name)) {
			return new Value(toText(environment.get(name)), Source.ENVIRONMENT);
		}
		return null;
	}

	/** The value of a variable with the variables it contains replaced, or null. */
	public String resolve(String name) {
		Value value = lookup(name);
		if (value == null) {
			return null;
		}
		return value.source() == Source.DYNAMIC ? value.raw() : substitute(value.raw(), 1);
	}

	/** Replaces the {@code {{variables}}} of a text; the unknown ones stay and are kept in {@link #unresolved()}. */
	public String substitute(String text) {
		unresolved.clear();
		return substitute(text, 0);
	}

	private String substitute(String text, int depth) {
		if (text == null || text.indexOf("{{") < 0) {
			return text;
		}
		Matcher matcher = REFERENCE.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			Value value = depth < MAX_DEPTH ? lookup(name) : null;
			String replacement;
			if (value == null) {
				unresolved.add(name);
				replacement = matcher.group();
			} else if (value.source() == Source.DYNAMIC) {
				replacement = value.raw();
			} else {
				replacement = substitute(value.raw(), depth + 1);
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/** A value of the environment as text: a string as is, an object or an array as JSON. */
	public static String toText(Object value) {
		if (value instanceof String s) {
			return s;
		}
		return Json.stringify(value);
	}

	private static Object parseJson(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.strip();
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			try {
				return Json.parse(trimmed);
			} catch (IllegalArgumentException e) {
				return text;
			}
		}
		return text;
	}

	/** Splits {@code users[1].name} in "users", 1, "name". */
	static List<Object> parsePath(String name) {
		List<Object> path = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '.') {
				if (current.length() > 0) {
					path.add(current.toString());
					current.setLength(0);
				}
			} else if (c == '[') {
				if (current.length() > 0) {
					path.add(current.toString());
					current.setLength(0);
				}
				int end = name.indexOf(']', i);
				if (end < 0) {
					return List.of();
				}
				String index = name.substring(i + 1, end).strip();
				if (index.length() >= 2 && (index.startsWith("'") || index.startsWith("\""))) {
					path.add(index.substring(1, index.length() - 1));
				} else {
					try {
						path.add(Integer.valueOf(index));
					} catch (NumberFormatException e) {
						return List.of();
					}
				}
				i = end;
			} else {
				current.append(c);
			}
		}
		if (current.length() > 0) {
			path.add(current.toString());
		}
		return path;
	}
}

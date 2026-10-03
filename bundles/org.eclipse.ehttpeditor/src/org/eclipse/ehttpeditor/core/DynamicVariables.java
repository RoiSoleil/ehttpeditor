package org.eclipse.ehttpeditor.core;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dynamic variables, whose name starts with {@code $}: {@code {{$uuid}}}, {@code {{$random.integer(1, 10)}}},
 * {@code {{$env.HOME}}}...
 */
public final class DynamicVariables {

	/** The dynamic variables proposed by the content assist, with their description. */
	public static final List<String[]> NAMES = List.of( //
			new String[] { "$uuid", "A random UUID" }, //
			new String[] { "$random.uuid", "A random UUID" }, //
			new String[] { "$timestamp", "The current UNIX timestamp, in seconds" }, //
			new String[] { "$isoTimestamp", "The current time, ISO-8601, UTC" }, //
			new String[] { "$randomInt", "A random integer between 0 and 1000" }, //
			new String[] { "$random.integer(0, 100)", "A random integer, from (included) to (excluded)" }, //
			new String[] { "$random.float(0, 1)", "A random float, from (included) to (excluded)" }, //
			new String[] { "$random.alphabetic(10)", "A random sequence of letters" }, //
			new String[] { "$random.alphanumeric(10)", "A random sequence of letters, digits and _" }, //
			new String[] { "$random.hexadecimal(10)", "A random hexadecimal string" }, //
			new String[] { "$random.email", "A random email address" }, //
			new String[] { "$random.name.firstName", "A random first name" }, //
			new String[] { "$random.name.lastName", "A random last name" }, //
			new String[] { "$random.name.fullName", "A random full name" }, //
			new String[] { "$env.", "An environment variable of the system: $env.HOME" }, //
			new String[] { "$projectRoot", "The root folder of the project" }, //
			new String[] { "$historyFolder", "The folder of the responses kept by EHttpEditor" });

	private static final Pattern CALL = Pattern.compile("^(\\$[\\w.]+)\\s*\\((.*)\\)$");
	private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final String DIGITS = "0123456789";
	private static final List<String> FIRST_NAMES = List.of("Alice", "Bob", "Camille", "David", "Emma", "Florian",
			"Gabriel", "Hélène", "Inès", "Jules", "Karim", "Léa", "Manon", "Nathan", "Olivia", "Paul");
	private static final List<String> LAST_NAMES = List.of("Martin", "Bernard", "Dubois", "Thomas", "Robert",
			"Richard", "Petit", "Durand", "Leroy", "Moreau", "Simon", "Laurent", "Lefebvre", "Michel");

	private final Random random = new SecureRandom();
	private final String projectRoot;
	private final String historyFolder;

	/**
	 * @param projectRoot   value of {@code $projectRoot}, or null
	 * @param historyFolder value of {@code $historyFolder}, or null
	 */
	public DynamicVariables(String projectRoot, String historyFolder) {
		this.projectRoot = projectRoot;
		this.historyFolder = historyFolder;
	}

	/** The value of a dynamic variable (its name starts with $), or null when it is unknown. */
	public String resolve(String expression) {
		String name = expression.strip();
		Matcher call = CALL.matcher(name);
		String[] args = new String[0];
		if (call.matches()) {
			name = call.group(1);
			String argList = call.group(2).strip();
			args = argList.isEmpty() ? new String[0] : argList.split("\\s*,\\s*");
			for (int i = 0; i < args.length; i++) {
				args[i] = unquote(args[i]);
			}
		}
		if (name.startsWith("$env.")) {
			return System.getenv(name.substring("$env.".length()));
		}
		try {
			return switch (name) {
			case "$uuid", "$random.uuid" -> UUID.randomUUID().toString();
			case "$timestamp" -> Long.toString(Instant.now().getEpochSecond());
			case "$isoTimestamp" -> Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
			case "$randomInt" -> Integer.toString(random.nextInt(1001));
			case "$random.integer" -> {
				long from = args.length > 0 ? Long.parseLong(args[0]) : 0;
				long to = args.length > 1 ? Long.parseLong(args[1]) : 1000;
				yield Long.toString(to <= from ? from : random.nextLong(from, to));
			}
			case "$random.float" -> {
				double from = args.length > 0 ? Double.parseDouble(args[0]) : 0;
				double to = args.length > 1 ? Double.parseDouble(args[1]) : 1000;
				yield Double.toString(to <= from ? from : random.nextDouble(from, to));
			}
			case "$random.alphabetic" -> randomString(LETTERS, length(args));
			case "$random.alphanumeric" -> randomString(LETTERS + DIGITS + "_", length(args));
			case "$random.hexadecimal" -> randomString("0123456789abcdef", length(args));
			case "$random.email" -> randomString(LETTERS.substring(0, 26), 8) + "@"
					+ randomString(LETTERS.substring(0, 26), 6) + ".com";
			case "$random.name.firstName" -> pick(FIRST_NAMES);
			case "$random.name.lastName" -> pick(LAST_NAMES);
			case "$random.name.fullName", "$random.name" -> pick(FIRST_NAMES) + " " + pick(LAST_NAMES);
			case "$random.name.username" -> stripAccents(pick(FIRST_NAMES).toLowerCase(Locale.ROOT))
					+ random.nextInt(100);
			case "$projectRoot" -> projectRoot;
			case "$historyFolder" -> historyFolder;
			default -> null;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String stripAccents(String s) {
		return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private String pick(List<String> values) {
		return values.get(random.nextInt(values.size()));
	}

	private static int length(String[] args) {
		return args.length > 0 ? Math.max(0, Math.min(Integer.parseInt(args[0]), 100_000)) : 10;
	}

	private String randomString(String alphabet, int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return sb.toString();
	}

	private static String unquote(String s) {
		if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}
}

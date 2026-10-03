package org.eclipse.ehttpeditor.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The environments of an HTTP file, read in {@code http-client.env.json} and {@code http-client.private.env.json}
 * in its folder and the folders above it, up to the root of the project:
 *
 * <pre>
 * {
 *   "$shared": { "version": "v1" },
 *   "dev": { "host": "localhost:8080", "SSLConfiguration": { "verifyHostCertificate": false } },
 *   "prod": { "host": "example.com" }
 * }
 * </pre>
 *
 * The private files (with the secrets, not committed) override the public files, the nearest folders the farthest,
 * and an environment its {@code $shared} variables.
 */
public final class Environments {

	/** The file of the environments. */
	public static final String PUBLIC_FILE = "http-client.env.json";
	/** The file of the private variables of the environments. */
	public static final String PRIVATE_FILE = "http-client.private.env.json";
	/** The environment whose variables are in every environment. */
	public static final String SHARED = "$shared";

	private static final Set<String> SETTINGS = Set.of("SSLConfiguration", "Security");

	/** An environment: its variables and its SSL settings. */
	public record Environment(String name, Map<String, Object> variables, Map<String, Object> sslConfiguration) {

		/** Whether the certificates of the servers are not checked ({@code "verifyHostCertificate": false}). */
		public boolean trustAllCertificates() {
			return Boolean.FALSE.equals(sslConfiguration.get("verifyHostCertificate"));
		}
	}

	private final List<File> files = new ArrayList<>();
	private final List<String> errors = new ArrayList<>();
	/** The contents of the files, the farthest first, the public files before the private ones. */
	private final List<Map<String, Object>> contents = new ArrayList<>();

	private Environments() {
	}

	/** No environment. */
	public static Environments empty() {
		return new Environments();
	}

	/**
	 * Reads the environment files of the folder dir and of its parents, up to stopDir (included) or to the root of
	 * the file system when stopDir is null or not a parent of dir.
	 */
	public static Environments load(File dir, File stopDir) {
		Environments environments = new Environments();
		List<File> dirs = new ArrayList<>();
		File stop = stopDir != null ? stopDir.getAbsoluteFile() : null;
		boolean stopIsParent = false;
		for (File d = dir != null ? dir.getAbsoluteFile() : null; d != null; d = d.getParentFile()) {
			if (d.equals(stop)) {
				stopIsParent = true;
				break;
			}
		}
		for (File d = dir != null ? dir.getAbsoluteFile() : null; d != null; d = d.getParentFile()) {
			dirs.add(0, d);
			if (stopIsParent && d.equals(stop)) {
				break;
			}
		}
		for (String fileName : List.of(PUBLIC_FILE, PRIVATE_FILE)) {
			for (File d : dirs) {
				File file = new File(d, fileName);
				if (file.isFile()) {
					environments.read(file);
				}
			}
		}
		return environments;
	}

	/** Reads environments from texts, the public one first (for the tests). */
	public static Environments of(String... jsonTexts) {
		Environments environments = new Environments();
		for (String json : jsonTexts) {
			environments.read(json, "environment");
		}
		return environments;
	}

	private void read(File file) {
		files.add(file);
		try {
			read(Files.readString(file.toPath(), StandardCharsets.UTF_8), file.getPath());
		} catch (IOException e) {
			errors.add(file.getPath() + ": " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private void read(String json, String source) {
		if (json.isBlank()) {
			return;
		}
		try {
			Object value = Json.parse(json);
			if (value instanceof Map<?, ?> map) {
				contents.add((Map<String, Object>) map);
			} else {
				errors.add(source + ": an object is expected");
			}
		} catch (IllegalArgumentException e) {
			errors.add(source + ": " + e.getMessage());
		}
	}

	/** The files read. */
	public List<File> files() {
		return Collections.unmodifiableList(files);
	}

	/** The errors met while reading the files. */
	public List<String> errors() {
		return Collections.unmodifiableList(errors);
	}

	/** The names of the environments, without {@code $shared}. */
	public List<String> names() {
		Set<String> names = new LinkedHashSet<>();
		for (Map<String, Object> content : contents) {
			for (Map.Entry<String, Object> entry : content.entrySet()) {
				if (!entry.getKey().equals(SHARED) && entry.getValue() instanceof Map) {
					names.add(entry.getKey());
				}
			}
		}
		return new ArrayList<>(names);
	}

	/** The environment with this name; with null or an unknown name, the {@code $shared} variables only. */
	@SuppressWarnings("unchecked")
	public Environment get(String name) {
		Map<String, Object> variables = new LinkedHashMap<>();
		Map<String, Object> ssl = new LinkedHashMap<>();
		for (Map<String, Object> content : contents) {
			for (String key : name == null || name.equals(SHARED) ? List.of(SHARED) : List.of(SHARED, name)) {
				if (content.get(key) instanceof Map<?, ?> environment) {
					for (Map.Entry<String, Object> entry : ((Map<String, Object>) environment).entrySet()) {
						if (entry.getKey().equals("SSLConfiguration") && entry.getValue() instanceof Map<?, ?> map) {
							ssl.putAll((Map<String, Object>) map);
						} else if (!SETTINGS.contains(entry.getKey())) {
							variables.put(entry.getKey(), entry.getValue());
						}
					}
				}
			}
		}
		return new Environment(name != null && names().contains(name) ? name : null,
				Collections.unmodifiableMap(variables), Collections.unmodifiableMap(ssl));
	}
}

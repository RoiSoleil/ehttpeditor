package org.eclipse.ehttpeditor.core;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.net.ssl.SSLException;

import org.eclipse.ehttpeditor.core.Environments.Environment;
import org.eclipse.ehttpeditor.core.HttpRequestSpec.Script;
import org.eclipse.ehttpeditor.core.ScriptRunner.ScriptException;

/** Runs a request of an HTTP file: pre-request scripts, sending, saving of the response, response handlers. */
public final class RequestRunner {

	/**
	 * Where and how the requests run.
	 *
	 * @param fileName        the name of the HTTP file, for the history
	 * @param baseDir         the folder of the HTTP file: relative paths, environment files
	 * @param projectRoot     the root of the project ({@code $projectRoot}), or null
	 * @param historyFolder   the folder of {@code $historyFolder}, or null
	 * @param environmentName the selected environment, or null
	 * @param session         the global variables, headers and cookies
	 * @param settings        the settings of the client
	 * @param executor        sends the requests
	 * @param cancelled       tells whether the user cancelled
	 */
	public record RunContext(String fileName, File baseDir, File projectRoot, File historyFolder,
			String environmentName, ClientSession session, ClientSettings settings, HttpExecutor executor,
			BooleanSupplier cancelled) {
	}

	private RequestRunner() {
	}

	/** Runs a request of the file. Never throws but for a cancellation: the errors are in the execution. */
	public static Execution run(HttpFile file, HttpRequestSpec spec, RunContext context) {
		List<String> console = Collections.synchronizedList(new ArrayList<>());
		List<TestResult> tests = Collections.synchronizedList(new ArrayList<>());
		Environments environments = Environments.load(context.baseDir(), context.projectRoot());
		for (String error : environments.errors()) {
			console.add("Environment file: " + error);
		}
		String environmentName = context.environmentName();
		if (environmentName != null && !environments.names().contains(environmentName)) {
			if (!environments.names().isEmpty()) {
				console.add("The environment '" + environmentName + "' is not defined here: "
						+ String.join(", ", environments.names()) + ".");
			}
			environmentName = null;
		}
		Environment environment = environments.get(environmentName);
		Variables variables = new Variables(Collections.synchronizedMap(new LinkedHashMap<>()),
				context.session().globals(), file.variables(), environment.variables(),
				new DynamicVariables(path(context.projectRoot()), path(context.historyFolder())));

		PreparedRequest prepared = null;
		HttpResponseData response = null;
		String error = null;
		File savedTo = null;
		Instant time = Instant.now();
		try {
			for (Script script : spec.preScripts()) {
				ScriptHost host = new ScriptHost(spec, variables, context.session(), null, null, console::add, tests,
						context.cancelled());
				runScript(script, spec, context, host, false);
			}
			prepared = RequestPreparer.prepare(spec, variables, context.baseDir(), context.session().globalHeaders(),
					context.settings(), environment.trustAllCertificates());
			if (!prepared.unresolved().isEmpty()) {
				console.add("Unresolved variables: " + String.join(", ", prepared.unresolved()));
			}
			time = Instant.now();
			response = context.executor().execute(prepared, context.session().cookies(), context.cancelled(),
					console::add);
			if (prepared.output() != null) {
				savedTo = save(response, prepared.output(), prepared.overwrite());
				console.add("Response saved to " + savedTo.getPath());
			}
			for (Script script : spec.handlers()) {
				ScriptHost host = new ScriptHost(spec, variables, context.session(), prepared, response, console::add,
						tests, context.cancelled());
				runScript(script, spec, context, host, true);
			}
		} catch (ScriptException e) {
			error = "Script error: " + e.getMessage();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			error = "Interrupted";
		} catch (IOException | IllegalArgumentException e) {
			error = describe(e);
		}
		return new Execution(context.fileName(), spec, environmentName, prepared, response, error, List.copyOf(tests),
				List.copyOf(console), savedTo, time);
	}

	private static void runScript(Script script, HttpRequestSpec spec, RunContext context, ScriptHost host,
			boolean response) throws ScriptException, IOException {
		if (script.text() != null) {
			ScriptRunner.run(script.text(), context.fileName() != null ? context.fileName() : spec.displayName(),
					script.line(), host, response);
		} else {
			File file = RequestPreparer.resolve(context.baseDir(), script.path());
			if (!file.isFile()) {
				throw new IOException("Script not found: " + file.getPath());
			}
			ScriptRunner.run(Files.readString(file.toPath(), StandardCharsets.UTF_8), file.getName(), 0, host,
					response);
		}
	}

	/** Writes the body; with overwrite false, an existing file is kept and a suffix is added: name-1.json. */
	static File save(HttpResponseData response, File file, boolean overwrite) throws IOException {
		File target = file;
		if (!overwrite) {
			String name = file.getName();
			int dot = name.lastIndexOf('.');
			String base = dot > 0 ? name.substring(0, dot) : name;
			String extension = dot > 0 ? name.substring(dot) : "";
			for (int i = 1; target.exists(); i++) {
				target = new File(file.getParentFile(), base + "-" + i + extension);
			}
		}
		File parent = target.getAbsoluteFile().getParentFile();
		if (parent != null) {
			Files.createDirectories(parent.toPath());
		}
		Files.write(target.toPath(), response.body());
		return target;
	}

	private static String describe(Exception e) {
		String message = e.getMessage();
		if (e instanceof HttpConnectTimeoutException) {
			return "Connection timed out" + (message != null ? ": " + message : "");
		}
		if (e instanceof HttpTimeoutException) {
			return "No response in time (@timeout)" + (message != null ? ": " + message : "");
		}
		if (e instanceof ConnectException) {
			return "Connection refused" + (message != null ? ": " + message : "");
		}
		if (e instanceof UnknownHostException) {
			return "Unknown host: " + message;
		}
		if (e instanceof SSLException) {
			return "SSL error: " + message
					+ " (\"SSLConfiguration\": {\"verifyHostCertificate\": false} in the environment accepts any certificate)";
		}
		if (message == null || message.isBlank()) {
			Throwable cause = e.getCause();
			return e.getClass().getSimpleName() + (cause != null ? ": " + cause : "");
		}
		return message;
	}

	private static String path(File file) {
		return file != null ? file.getAbsolutePath() : null;
	}
}

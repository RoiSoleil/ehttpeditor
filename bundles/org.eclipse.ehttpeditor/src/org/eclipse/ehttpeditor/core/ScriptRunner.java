package org.eclipse.ehttpeditor.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

/**
 * Runs the pre-request scripts and the response handlers with Rhino, in ES6 mode, without access to Java. The API
 * ({@code client}, {@code request}, {@code response}...) is defined by {@code ehttpeditor-api.js}.
 */
public final class ScriptRunner {

	/** The script failed: syntax error, exception... */
	public static final class ScriptException extends Exception {
		private static final long serialVersionUID = 1L;

		ScriptException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static final String API = loadApi();
	// Everything is synchronous here: "await sleep(100)" is "sleep(100)", "async () =>" is "() =>".
	private static final Pattern AWAIT = Pattern.compile("\\bawait\\s+");
	private static final Pattern ASYNC = Pattern
			.compile("\\basync\\s+(?=function\\b|\\(|[A-Za-z_$][\\w$]*\\s*=>)");

	private ScriptRunner() {
	}

	private static String loadApi() {
		try (InputStream in = ScriptRunner.class.getResourceAsStream("ehttpeditor-api.js")) {
			if (in == null) {
				throw new IllegalStateException("ehttpeditor-api.js not found");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Runs a script.
	 *
	 * @param source     the text of the script
	 * @param sourceName the name for the errors (file, or name of the request)
	 * @param firstLine  the line (from 0) of the first line of the text, for the errors
	 * @param host       the Java side of the API
	 * @param response   whether {@code response} is defined (response handler)
	 */
	@SuppressWarnings("deprecation") // setInterpretedMode does not exist before Rhino 1.8, which may be the one wired.
	public static void run(String source, String sourceName, int firstLine, ScriptHost host, boolean response)
			throws ScriptException {
		Context cx = Context.enter();
		try {
			cx.setLanguageVersion(Context.VERSION_ES6);
			// Interpreted: no class generated, nothing for the class loaders of OSGi.
			cx.setOptimizationLevel(-1);
			cx.setApplicationClassLoader(ScriptRunner.class.getClassLoader());
			cx.getWrapFactory().setJavaPrimitiveWrap(false);
			ScriptableObject scope = cx.initSafeStandardObjects();
			ScriptableObject.putProperty(scope, "__host", Context.javaToJS(host, scope));
			ScriptableObject.putProperty(scope, "__hasResponse", response);
			cx.evaluateString(scope, API, "ehttpeditor-api.js", 1, null);
			String code = ASYNC.matcher(AWAIT.matcher(source).replaceAll("")).replaceAll("");
			// On one line before the script, so that the lines of the errors are the lines of the file.
			Object script = cx.evaluateString(scope, "(function () {" + code + "\n})", sourceName, firstLine + 1,
					null);
			Function run = (Function) ScriptableObject.getProperty(scope, "__ehttpRun");
			run.call(cx, scope, scope, new Object[] { script });
		} catch (WrappedException e) {
			Throwable wrapped = e.getWrappedException();
			if (wrapped instanceof CancellationException cancellation) {
				throw cancellation;
			}
			if (wrapped instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				throw new CancellationException("Script interrupted");
			}
			throw new ScriptException(location(e) + wrapped, wrapped);
		} catch (JavaScriptException e) {
			if (e.getValue() instanceof Scriptable value
					&& Boolean.TRUE.equals(ScriptableObject.getProperty(value, "__ehttpExit"))) {
				// client.exit()
				return;
			}
			cancelled(host);
			throw new ScriptException(location(e) + e.details(), e);
		} catch (RhinoException e) {
			cancelled(host);
			throw new ScriptException(location(e) + e.details(), e);
		} finally {
			Context.exit();
		}
	}

	/** A cancellation caught and thrown again by a script (in client.test) is still a cancellation. */
	private static void cancelled(ScriptHost host) {
		if (host.cancelled()) {
			throw new CancellationException("Script cancelled");
		}
	}

	private static String location(RhinoException e) {
		if (e.sourceName() == null || e.sourceName().equals("ehttpeditor-api.js")) {
			return "";
		}
		return e.sourceName() + (e.lineNumber() > 0 ? ":" + e.lineNumber() : "") + ": ";
	}
}

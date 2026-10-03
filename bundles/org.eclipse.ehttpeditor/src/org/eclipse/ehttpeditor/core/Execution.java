package org.eclipse.ehttpeditor.core;

import java.io.File;
import java.time.Instant;
import java.util.List;

/**
 * The run of a request: what was sent, what was received, the tests and the logs of the scripts.
 *
 * @param fileName    the name of the HTTP file
 * @param request     the request as written
 * @param environment the name of the environment, or null
 * @param prepared    the request sent, or null when it could not be prepared
 * @param response    the response, or null when there is none
 * @param error       the error (connection refused, script error...), or null
 * @param tests       the tests of the response handlers
 * @param console     the logs of the scripts and the warnings
 * @param savedTo     the file where the response was written ({@code >>}), or null
 * @param time        when the request was sent
 */
public record Execution(String fileName, HttpRequestSpec request, String environment, PreparedRequest prepared,
		HttpResponseData response, String error, List<TestResult> tests, List<String> console, File savedTo,
		Instant time) {

	/** Whether the request got a response, no error and no failed test. */
	public boolean succeeded() {
		return response != null && error == null && tests.stream().allMatch(TestResult::passed);
	}

	/** The number of failed tests. */
	public long failedTests() {
		return tests.stream().filter(t -> !t.passed()).count();
	}
}

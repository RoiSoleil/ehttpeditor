package org.eclipse.ehttpeditor.core;

/**
 * The result of a test of a response handler ({@code client.test}), or of an assertion outside a test.
 *
 * @param name    the name of the test
 * @param passed  whether it passed
 * @param message the error, or null
 */
public record TestResult(String name, boolean passed, String message) {
}

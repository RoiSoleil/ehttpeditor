package org.eclipse.ehttpeditor.core;

import java.util.List;
import java.util.Map;

/**
 * A request of an HTTP file, as written: the variables are not replaced yet.
 *
 * @param index           position of the request in the file, from 1
 * @param name            name given by {@code ### Name}, {@code # @name Name} or {@code # @name = Name}, or null
 * @param method          method in upper case ({@code GET} when the request line has none)
 * @param url             URL, the continuation lines appended
 * @param httpVersion     {@code HTTP/1.1}, {@code HTTP/2}... or null
 * @param headers         the headers in their order
 * @param body            the body, or null when the request has none
 * @param directives      the tags such as {@code @no-redirect} or {@code @timeout 10 s}, value "" when they have none
 * @param preScripts      the pre-request scripts ({@code < {% %}} or {@code < script.js})
 * @param handlers        the response handlers ({@code > {% %}} or {@code > script.js})
 * @param output          where the response is written ({@code >>} or {@code >>!}), or null
 * @param start           offset of the first character of the request block
 * @param end             offset after the last character of the request block
 * @param requestLine     line (from 0) of the request line
 * @param bodyLine        line (from 0) of the first line of the body, or -1
 */
public record HttpRequestSpec(int index, String name, String method, String url, String httpVersion,
		List<Header> headers, String body, Map<String, String> directives, List<Script> preScripts,
		List<Script> handlers, Output output, int start, int end, int requestLine, int bodyLine) {

	/** A header as written. */
	public record Header(String name, String value) {
	}

	/**
	 * A script: either its text ({@code {% %}}) or the path of its file.
	 *
	 * @param text text of an inline script, or null
	 * @param path path of a script file, or null
	 * @param line line (from 0) of the first line of the text, for the errors
	 */
	public record Script(String text, String path, int line) {
	}

	/** Where to write the response: {@code >> path} or {@code >>! path} (overwrite). */
	public record Output(String path, boolean overwrite) {
	}

	/** The name shown to the user: the name of the request or its position ({@code #2}). */
	public String displayName() {
		return name != null ? name : "#" + index;
	}

	/** Whether the request has the tag, such as {@code no-redirect}. */
	public boolean hasDirective(String directive) {
		return directives.containsKey(directive);
	}
}

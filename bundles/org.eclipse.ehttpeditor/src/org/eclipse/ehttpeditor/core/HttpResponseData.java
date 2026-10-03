package org.eclipse.ehttpeditor.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;

/**
 * A response received.
 *
 * @param status    the status code
 * @param version   {@code HTTP/1.1} or {@code HTTP/2}
 * @param headers   the headers
 * @param body      the body, decompressed
 * @param millis    the time from the sending of the request to the end of the body, redirections included
 * @param uri       the URI of the response, after the redirections
 * @param redirects the redirections followed, as {@code 302 https://...}
 */
public record HttpResponseData(int status, String version, List<Header> headers, byte[] body, long millis, URI uri,
		List<String> redirects) {

	/** The first value of a header, or null. */
	public String header(String name) {
		for (Header header : headers) {
			if (header.name().equalsIgnoreCase(name)) {
				return header.value();
			}
		}
		return null;
	}

	/** All the values of a header. */
	public List<String> headers(String name) {
		List<String> values = new ArrayList<>();
		for (Header header : headers) {
			if (header.name().equalsIgnoreCase(name)) {
				values.add(header.value());
			}
		}
		return values;
	}

	/** The Content-Type, or null. */
	public String contentType() {
		return header("Content-Type");
	}

	/** The body as text, in the charset of the response. */
	public String text() {
		return new String(body, Bodies.charset(contentType()));
	}

	/** The status line: {@code HTTP/1.1 200 OK}. */
	public String statusLine() {
		String reason = HttpStatus.reason(status);
		return version + " " + status + (reason.isEmpty() ? "" : " " + reason);
	}

	/** The status line and the headers. */
	public String headersText() {
		StringBuilder sb = new StringBuilder(statusLine()).append('\n');
		for (Header header : headers) {
			sb.append(header.name()).append(": ").append(header.value()).append('\n');
		}
		return sb.toString();
	}
}

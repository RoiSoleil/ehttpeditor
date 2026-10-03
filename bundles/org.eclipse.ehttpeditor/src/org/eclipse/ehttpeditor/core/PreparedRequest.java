package org.eclipse.ehttpeditor.core;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;

/**
 * A request ready to be sent: the variables replaced, the files of the body read, the URL encoded.
 *
 * @param method          the method
 * @param uri             the URI
 * @param version         the version of HTTP asked by the request line, or null
 * @param headers         the headers
 * @param body            the body, or null
 * @param digestUser      user of an {@code Authorization: Digest user password} header, or null
 * @param digestPassword  password of the digest authentication
 * @param followRedirects whether the redirections are followed (no {@code @no-redirect})
 * @param saveCookies     whether the cookies received go to the cookie jar (no {@code @no-cookie-jar})
 * @param log             whether the request is kept in the history (no {@code @no-log})
 * @param connectTimeout  timeout of the connection
 * @param readTimeout     timeout of the response
 * @param trustAll        whether the certificates are not checked
 * @param output          the file where the response is written, or null
 * @param overwrite       whether that file is overwritten ({@code >>!})
 * @param unresolved      the variables not found
 */
public record PreparedRequest(String method, URI uri, HttpClient.Version version, List<Header> headers, byte[] body,
		String digestUser, String digestPassword, boolean followRedirects, boolean saveCookies, boolean log,
		Duration connectTimeout, Duration readTimeout, boolean trustAll, File output, boolean overwrite,
		List<String> unresolved) {

	/** The first value of a header, or null. */
	public String header(String name) {
		for (Header header : headers) {
			if (header.name().equalsIgnoreCase(name)) {
				return header.value();
			}
		}
		return null;
	}

	/** The body as text for the user: UTF-8, or a summary when it is binary. */
	public String bodyText() {
		return Bodies.toDisplayText(body, header("Content-Type"));
	}

	/** The request as text: request line, headers and body. */
	public String toText() {
		StringBuilder sb = new StringBuilder();
		sb.append(method).append(' ').append(uri);
		if (version != null) {
			sb.append(version == HttpClient.Version.HTTP_2 ? " HTTP/2" : " HTTP/1.1");
		}
		sb.append('\n');
		for (Header header : headers) {
			sb.append(header.name()).append(": ").append(header.value()).append('\n');
		}
		if (digestUser != null) {
			sb.append("Authorization: Digest ").append(digestUser).append(" ****\n");
		}
		if (body != null && body.length > 0) {
			sb.append('\n').append(bodyText());
		}
		return sb.toString();
	}

	/** The body as text, UTF-8 (for the scripts). */
	public String bodyAsString() {
		return body == null ? "" : new String(body, StandardCharsets.UTF_8);
	}
}

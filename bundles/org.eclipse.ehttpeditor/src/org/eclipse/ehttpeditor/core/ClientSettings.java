package org.eclipse.ehttpeditor.core;

import java.time.Duration;

/**
 * The settings of the client, from the preferences.
 *
 * @param connectTimeout default timeout of the connection
 * @param readTimeout    default timeout of the response
 * @param userAgent      User-Agent sent when the request has none, or null for none
 */
public record ClientSettings(Duration connectTimeout, Duration readTimeout, String userAgent) {

	/** The default settings. */
	public static ClientSettings defaults() {
		return new ClientSettings(Duration.ofSeconds(30), Duration.ofSeconds(60), "EHttpEditor (Java)");
	}
}

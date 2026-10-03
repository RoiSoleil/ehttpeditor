package org.eclipse.ehttpeditor.core;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The digest authentication (RFC 7616): the answer to a {@code WWW-Authenticate: Digest} challenge. */
final class DigestAuth {

	private DigestAuth() {
	}

	/** Whether a WWW-Authenticate header is a digest challenge. */
	static boolean isDigest(String challenge) {
		return challenge != null && challenge.strip().regionMatches(true, 0, "Digest ", 0, 7);
	}

	/** The Authorization header answering the challenge. */
	static String authorization(String challenge, String method, URI uri, String user, String password) {
		Map<String, String> params = parse(challenge.strip().substring(7));
		String realm = params.getOrDefault("realm", "");
		String nonce = params.getOrDefault("nonce", "");
		String opaque = params.get("opaque");
		String algorithm = params.getOrDefault("algorithm", "MD5");
		String qopOptions = params.get("qop");
		String qop = null;
		if (qopOptions != null) {
			for (String option : qopOptions.split(",")) {
				if (option.strip().equalsIgnoreCase("auth")) {
					qop = "auth";
				}
			}
		}
		boolean session = algorithm.toUpperCase(Locale.ROOT).endsWith("-SESS");
		String hashName = switch (algorithm.toUpperCase(Locale.ROOT).replace("-SESS", "")) {
		case "SHA-256" -> "SHA-256";
		case "SHA-512-256" -> "SHA-512/256";
		default -> "MD5";
		};
		String digestUri = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
		if (uri.getRawQuery() != null) {
			digestUri += "?" + uri.getRawQuery();
		}
		String cnonce = HexFormat.of().formatHex(new SecureRandom().generateSeed(8));
		String nc = "00000001";
		String ha1 = hash(hashName, user + ":" + realm + ":" + password);
		if (session) {
			ha1 = hash(hashName, ha1 + ":" + nonce + ":" + cnonce);
		}
		String ha2 = hash(hashName, method + ":" + digestUri);
		String response = qop != null ? hash(hashName, ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2)
				: hash(hashName, ha1 + ":" + nonce + ":" + ha2);
		StringBuilder sb = new StringBuilder("Digest ");
		sb.append("username=\"").append(user).append("\", realm=\"").append(realm).append("\", nonce=\"")
				.append(nonce).append("\", uri=\"").append(digestUri).append("\", algorithm=").append(algorithm)
				.append(", response=\"").append(response).append('"');
		if (opaque != null) {
			sb.append(", opaque=\"").append(opaque).append('"');
		}
		if (qop != null) {
			sb.append(", qop=").append(qop).append(", nc=").append(nc).append(", cnonce=\"").append(cnonce)
					.append('"');
		}
		return sb.toString();
	}

	private static String hash(String algorithm, String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance(algorithm);
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The parameters of a challenge: {@code realm="x", qop="auth,auth-int", nonce="y"}. */
	static Map<String, String> parse(String s) {
		Map<String, String> params = new LinkedHashMap<>();
		int i = 0;
		while (i < s.length()) {
			while (i < s.length() && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) {
				i++;
			}
			int equals = s.indexOf('=', i);
			if (equals < 0) {
				break;
			}
			String key = s.substring(i, equals).strip().toLowerCase(Locale.ROOT);
			i = equals + 1;
			String value;
			if (i < s.length() && s.charAt(i) == '"') {
				StringBuilder sb = new StringBuilder();
				i++;
				while (i < s.length() && s.charAt(i) != '"') {
					if (s.charAt(i) == '\\' && i + 1 < s.length()) {
						i++;
					}
					sb.append(s.charAt(i++));
				}
				i++;
				value = sb.toString();
			} else {
				int comma = s.indexOf(',', i);
				int end = comma < 0 ? s.length() : comma;
				value = s.substring(i, end).strip();
				i = end;
			}
			params.put(key, value);
		}
		return params;
	}
}

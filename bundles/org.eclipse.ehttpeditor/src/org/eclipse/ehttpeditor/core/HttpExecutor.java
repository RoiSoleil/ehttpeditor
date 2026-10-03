package org.eclipse.ehttpeditor.core;

import java.io.IOException;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;

/**
 * Sends the requests with the HTTP client of the JDK. The redirections are followed here, not by the client, so
 * that the cookies they set reach the cookie jar and {@code @no-redirect} applies per request.
 */
public final class HttpExecutor implements AutoCloseable {

	private static final int MAX_REDIRECTS = 20;
	/** Headers the client of the JDK computes itself and refuses. */
	private static final Set<String> RESTRICTED = Set.of("content-length", "host", "connection", "expect", "upgrade");

	private record ClientKey(Duration connectTimeout, HttpClient.Version version, boolean trustAll) {
	}

	private final Map<ClientKey, HttpClient> clients = new ConcurrentHashMap<>();

	/**
	 * Sends a request.
	 *
	 * @param request   the request
	 * @param cookies   the cookie jar
	 * @param cancelled tells whether the user cancelled
	 * @param log       receives the warnings (headers dropped...)
	 * @throws CancellationException when cancelled
	 */
	public HttpResponseData execute(PreparedRequest request, CookieManager cookies, BooleanSupplier cancelled,
			Consumer<String> log) throws IOException, InterruptedException {
		HttpClient client = client(request);
		URI uri = request.uri();
		String method = request.method();
		byte[] body = request.body();
		List<Header> headers = new ArrayList<>();
		for (Header header : request.headers()) {
			if (RESTRICTED.contains(header.name().toLowerCase(Locale.ROOT))) {
				log.accept("The header " + header.name() + " is set by the HTTP client and was not sent.");
			} else {
				headers.add(header);
			}
		}
		List<String> redirects = new ArrayList<>();
		String digestAuthorization = null;
		boolean digestTried = false;
		long start = System.nanoTime();
		for (int hop = 0;; hop++) {
			HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(request.readTimeout());
			String explicitCookies = null;
			for (Header header : headers) {
				if (header.name().equalsIgnoreCase("Cookie")) {
					explicitCookies = explicitCookies == null ? header.value() : explicitCookies + "; " + header.value();
					continue;
				}
				try {
					builder.header(header.name(), header.value());
				} catch (IllegalArgumentException e) {
					throw new IOException("Invalid header " + header.name() + ": " + e.getMessage(), e);
				}
			}
			String cookieHeader = cookieHeader(cookies, uri, explicitCookies);
			if (cookieHeader != null) {
				builder.header("Cookie", cookieHeader);
			}
			if (digestAuthorization != null) {
				builder.header("Authorization", digestAuthorization);
			}
			HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofByteArray(body);
			builder.method(method, publisher);
			HttpResponse<byte[]> response = send(client, builder.build(), cancelled);
			if (request.saveCookies()) {
				try {
					cookies.put(uri, response.headers().map());
				} catch (IOException | IllegalArgumentException e) {
					log.accept("Cookie not saved: " + e.getMessage());
				}
			}
			int status = response.statusCode();
			String challenge = response.headers().firstValue("WWW-Authenticate").orElse(null);
			if (status == 401 && request.digestUser() != null && !digestTried && DigestAuth.isDigest(challenge)) {
				digestTried = true;
				digestAuthorization = DigestAuth.authorization(challenge, method, uri, request.digestUser(),
						request.digestPassword());
				hop--;
				continue;
			}
			String location = response.headers().firstValue("Location").orElse(null);
			if (request.followRedirects() && location != null && isRedirect(status) && hop < MAX_REDIRECTS) {
				URI next;
				try {
					next = uri.resolve(new URI(RequestPreparer.encodeLoose(location.strip())));
				} catch (java.net.URISyntaxException | IllegalArgumentException e) {
					throw new IOException("Invalid redirection to " + location, e);
				}
				redirects.add(status + " " + next);
				if (status == 303 || ((status == 301 || status == 302) && method.equals("POST"))) {
					if (!method.equals("HEAD")) {
						method = "GET";
					}
					body = null;
					headers.removeIf(h -> h.name().equalsIgnoreCase("Content-Type"));
				}
				if (!sameOrigin(uri, next)) {
					// The credentials stay with their server.
					headers.removeIf(h -> h.name().equalsIgnoreCase("Authorization"));
					digestAuthorization = null;
				}
				uri = next;
				continue;
			}
			long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			List<Header> responseHeaders = new ArrayList<>();
			response.headers().map().forEach((name, values) -> {
				if (!name.startsWith(":")) {
					for (String value : values) {
						responseHeaders.add(new Header(name, value));
					}
				}
			});
			byte[] responseBody = response.body() == null ? new byte[0] : response.body();
			String encoding = response.headers().firstValue("Content-Encoding").orElse(null);
			try {
				responseBody = Bodies.decode(responseBody, encoding);
			} catch (IOException e) {
				log.accept("The body could not be decompressed (" + encoding + "): " + e.getMessage());
			}
			String version = response.version() == HttpClient.Version.HTTP_2 ? "HTTP/2" : "HTTP/1.1";
			return new HttpResponseData(status, version, List.copyOf(responseHeaders), responseBody, millis, uri,
					List.copyOf(redirects));
		}
	}

	private static boolean sameOrigin(URI a, URI b) {
		return a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost()) && a.getPort() == b.getPort()
				&& String.valueOf(a.getScheme()).equalsIgnoreCase(b.getScheme());
	}

	private static boolean isRedirect(int status) {
		return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
	}

	private static String cookieHeader(CookieManager cookies, URI uri, String explicit) throws IOException {
		Map<String, List<String>> fromJar = cookies.get(uri, Map.of());
		List<String> values = new ArrayList<>();
		if (explicit != null) {
			values.add(explicit);
		}
		for (String value : fromJar.getOrDefault("Cookie", List.of())) {
			values.add(value);
		}
		return values.isEmpty() ? null : String.join("; ", values);
	}

	private static HttpResponse<byte[]> send(HttpClient client, HttpRequest request, BooleanSupplier cancelled)
			throws IOException, InterruptedException {
		CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request,
				HttpResponse.BodyHandlers.ofByteArray());
		while (true) {
			try {
				return future.get(100, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				if (cancelled.getAsBoolean()) {
					future.cancel(true);
					throw new CancellationException("Request cancelled");
				}
			} catch (InterruptedException e) {
				future.cancel(true);
				throw e;
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();
				if (cause instanceof IOException io) {
					throw io;
				}
				throw new IOException(cause.getMessage() != null ? cause.getMessage() : cause.toString(), cause);
			}
		}
	}

	private HttpClient client(PreparedRequest request) {
		HttpClient.Version version = request.version() != null ? request.version() : HttpClient.Version.HTTP_1_1;
		ClientKey key = new ClientKey(request.connectTimeout(), version, request.trustAll());
		return clients.computeIfAbsent(key, k -> {
			HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
					.connectTimeout(k.connectTimeout()).version(k.version()).proxy(ProxySelector.getDefault());
			if (k.trustAll()) {
				builder.sslContext(trustAllContext());
			}
			return builder.build();
		});
	}

	/** An SSL context which accepts every certificate and every host name ({@code verifyHostCertificate: false}). */
	private static SSLContext trustAllContext() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new TrustAll() }, null);
			return context;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Accepts all: with an extended trust manager, the check of the host name is skipped too. */
	private static final class TrustAll extends X509ExtendedTrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	@Override
	public void close() {
		for (HttpClient client : clients.values()) {
			client.shutdownNow();
		}
		clients.clear();
	}
}

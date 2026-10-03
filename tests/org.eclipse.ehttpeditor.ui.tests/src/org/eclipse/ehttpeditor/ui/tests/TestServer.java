package org.eclipse.ehttpeditor.ui.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** A small HTTP/1.1 server for the tests: one response per connection, the requests recorded. */
final class TestServer implements AutoCloseable {

	/** A request received. */
	record Request(String method, String target, Map<String, String> headers, byte[] body) {

		String header(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}

		String bodyText() {
			return new String(body, StandardCharsets.UTF_8);
		}
	}

	/** A response to send. */
	record Response(int status, Map<String, String> headers, byte[] body) {

		static Response of(int status, String contentType, String body) {
			Map<String, String> headers = new LinkedHashMap<>();
			if (contentType != null) {
				headers.put("Content-Type", contentType);
			}
			return new Response(status, headers, body.getBytes(StandardCharsets.UTF_8));
		}
	}

	private final ServerSocket socket;
	private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
	private volatile Function<Request, Response> handler = r -> Response.of(200, "text/plain", "ok");

	TestServer() throws IOException {
		socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(this::serve, "TestServer");
		thread.setDaemon(true);
		thread.start();
	}

	String url() {
		return "http://127.0.0.1:" + socket.getLocalPort();
	}

	void handler(Function<Request, Response> handler) {
		this.handler = handler;
	}

	List<Request> requests() {
		return requests;
	}

	Request lastRequest() {
		return requests.get(requests.size() - 1);
	}

	private void serve() {
		while (!socket.isClosed()) {
			try (Socket client = socket.accept()) {
				handle(client);
			} catch (IOException e) {
				// Closed.
			}
		}
	}

	private void handle(Socket client) throws IOException {
		InputStream in = client.getInputStream();
		String requestLine = readLine(in);
		if (requestLine == null || requestLine.isEmpty()) {
			return;
		}
		String[] parts = requestLine.split(" ");
		Map<String, String> headers = new LinkedHashMap<>();
		String line;
		while ((line = readLine(in)) != null && !line.isEmpty()) {
			int colon = line.indexOf(':');
			headers.merge(line.substring(0, colon).strip().toLowerCase(Locale.ROOT), line.substring(colon + 1).strip(),
					(a, b) -> a + ", " + b);
		}
		int length = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
		Request request = new Request(parts[0], parts[1], headers, in.readNBytes(length));
		requests.add(request);
		Response response = handler.apply(request);
		StringBuilder head = new StringBuilder("HTTP/1.1 ").append(response.status()).append(" X\r\n");
		for (Map.Entry<String, String> header : response.headers().entrySet()) {
			head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
		}
		head.append("Content-Length: ").append(response.body().length).append("\r\n");
		head.append("Connection: close\r\n\r\n");
		OutputStream out = client.getOutputStream();
		out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
		if (!request.method().equals("HEAD")) {
			out.write(response.body());
		}
		out.flush();
	}

	private static String readLine(InputStream in) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) != -1 && b != '\n') {
			if (b != '\r') {
				line.write(b);
			}
		}
		if (b == -1 && line.size() == 0) {
			return null;
		}
		return line.toString(StandardCharsets.ISO_8859_1);
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}
}

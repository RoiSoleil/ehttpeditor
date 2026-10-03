package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.ehttpeditor.core.Bodies;
import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;
import org.eclipse.ehttpeditor.core.HttpResponseData;
import org.eclipse.ehttpeditor.core.HttpStatus;
import org.eclipse.ehttpeditor.core.PreparedRequest;
import org.junit.jupiter.api.Test;

/** The content types, the charsets, the compression and the display of the bodies, requests and responses. */
public class BodiesTest {

	@Test
	public void mimeTypesAndCharsets() {
		assertEquals("application/json", Bodies.mimeType("Application/JSON; charset=UTF-8"));
		assertEquals("text/plain", Bodies.mimeType(" text/plain "));
		assertEquals("", Bodies.mimeType(null));
		assertEquals("ISO-8859-1", Bodies.charsetName("text/plain; charset=\"ISO-8859-1\""));
		assertEquals("utf-8", Bodies.charsetName("text/plain;CHARSET=utf-8"));
		assertNull(Bodies.charsetName("text/plain"));
		assertNull(Bodies.charsetName(null));
		assertEquals(StandardCharsets.ISO_8859_1, Bodies.charset("text/plain; charset=latin1"));
		assertEquals(StandardCharsets.UTF_8, Bodies.charset("text/plain; charset=nonexistent-42"));
		assertEquals(StandardCharsets.UTF_8, Bodies.charset(null));
	}

	@Test
	public void kindsOfContent() {
		assertTrue(Bodies.isJson("application/problem+json"));
		assertTrue(Bodies.isJson("text/json"));
		assertFalse(Bodies.isJson("text/plain"));
		assertTrue(Bodies.isXml("application/atom+xml"));
		assertTrue(Bodies.isXml("text/html"));
		assertFalse(Bodies.isXml("application/json"));
		byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
		assertTrue(Bodies.isText(text, "application/javascript"));
		assertTrue(Bodies.isText(text, "application/x-www-form-urlencoded"));
		assertFalse(Bodies.isText(text, "image/png"));
		assertFalse(Bodies.isText(text, "application/octet-stream"));
		// Without a content type, the bytes decide.
		assertTrue(Bodies.isText(text, null));
		assertFalse(Bodies.isText(new byte[] { 'a', 0, 'b' }, null));
		assertFalse(Bodies.isText(new byte[] { 1, 2, 3, 4, 5, 6, 'a' }, null));
	}

	@Test
	public void display() {
		assertEquals("", Bodies.toDisplayText(null, null));
		assertEquals("<3 bytes of image/png>", Bodies.toDisplayText(new byte[] { 1, 2, 3 }, "image/png"));
		assertEquals("<3 bytes of binary content>", Bodies.toDisplayText(new byte[] { 0, 2, 3 }, null));
		assertEquals("café", Bodies.toDisplayText(new byte[] { 'c', 'a', 'f', (byte) 0xe9 },
				"text/plain; charset=ISO-8859-1"));
		// JSON without a content type is indented too; text stays as is.
		assertEquals("{\n  \"a\": 1\n}", Bodies.toPrettyText("{\"a\":1}".getBytes(StandardCharsets.UTF_8), null));
		assertEquals("[1]", Bodies.toPrettyText("[1]".getBytes(StandardCharsets.UTF_8), "text/plain"));
		assertEquals("{\n  \"b\": true\n}",
				Bodies.toPrettyText("{\"b\":true}".getBytes(StandardCharsets.UTF_8), "application/json"));
	}

	@Test
	public void decompression() throws IOException {
		byte[] data = "deflated body".getBytes(StandardCharsets.UTF_8);
		assertArrayEquals(data, Bodies.decode(deflate(data, false), "deflate"));
		// Some servers send raw deflate, without the zlib header.
		assertArrayEquals(data, Bodies.decode(deflate(data, true), " Deflate "));
		assertArrayEquals(data, Bodies.decode(data, null));
		assertArrayEquals(data, Bodies.decode(data, "br"));
		assertArrayEquals(new byte[0], Bodies.decode(new byte[0], "gzip"));
	}

	private static byte[] deflate(byte[] data, boolean raw) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (DeflaterOutputStream deflater = new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, raw))) {
			deflater.write(data);
		}
		return out.toByteArray();
	}

	@Test
	public void extensions() {
		assertEquals("json", Bodies.extension("application/vnd.api+json"));
		assertEquals("html", Bodies.extension("text/html; charset=utf-8"));
		assertEquals("xml", Bodies.extension("text/xml"));
		assertEquals("xml", Bodies.extension("application/rss+xml"));
		assertEquals("css", Bodies.extension("text/css"));
		assertEquals("js", Bodies.extension("text/javascript"));
		assertEquals("csv", Bodies.extension("text/csv"));
		assertEquals("png", Bodies.extension("image/png"));
		assertEquals("jpg", Bodies.extension("image/jpeg"));
		assertEquals("gif", Bodies.extension("image/gif"));
		assertEquals("svg", Bodies.extension("image/svg+xml"));
		assertEquals("pdf", Bodies.extension("application/pdf"));
		assertEquals("zip", Bodies.extension("application/zip"));
		assertEquals("txt", Bodies.extension(null));
		assertEquals("bin", Bodies.extension("application/x-custom"));
	}

	@Test
	public void responses() {
		HttpResponseData response = new HttpResponseData(299, "HTTP/2",
				List.of(new Header("Set-Cookie", "a=1"), new Header("set-cookie", "b=2"),
						new Header("Content-Type", "text/plain; charset=UTF-8")),
				"été".getBytes(StandardCharsets.UTF_8), 12, URI.create("http://h/"), List.of());
		assertEquals("HTTP/2 299", response.statusLine());
		assertEquals(List.of("a=1", "b=2"), response.headers("SET-COOKIE"));
		assertNull(response.header("X-None"));
		assertEquals("été", response.text());
		assertEquals("HTTP/2 299\nSet-Cookie: a=1\nset-cookie: b=2\nContent-Type: text/plain; charset=UTF-8\n",
				response.headersText());
		assertEquals("Not Found", HttpStatus.reason(404));
		assertEquals("", HttpStatus.reason(799));
	}

	@Test
	public void requestsAsText() {
		PreparedRequest request = new PreparedRequest("POST", URI.create("http://h/p"), HttpClient.Version.HTTP_2,
				List.of(new Header("Content-Type", "application/json")), "{}".getBytes(StandardCharsets.UTF_8),
				"ada", "secret", true, true, true, Duration.ofSeconds(1), Duration.ofSeconds(1), false, null, false,
				List.of());
		assertEquals("POST http://h/p HTTP/2\nContent-Type: application/json\nAuthorization: Digest ada ****\n\n{}",
				request.toText());
		assertEquals("{}", request.bodyAsString());
		PreparedRequest get = new PreparedRequest("GET", URI.create("http://h/"), HttpClient.Version.HTTP_1_1,
				List.of(), null, null, null, true, true, true, Duration.ofSeconds(1), Duration.ofSeconds(1), false,
				null, false, List.of());
		assertEquals("GET http://h/ HTTP/1.1\n", get.toText());
		assertEquals("", get.bodyAsString());
		assertEquals("", get.bodyText());
	}
}

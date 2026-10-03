package org.eclipse.ehttpeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.HttpRequestSpec;
import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;
import org.junit.jupiter.api.Test;

public class HttpFileTest {

	@Test
	public void simpleRequests() {
		HttpFile file = HttpFile.parse("""
				GET https://example.com/a/

				###

				https://example.com:8080/api/html/get?id=123
				""");
		assertEquals(2, file.requests().size());
		HttpRequestSpec first = file.requests().get(0);
		assertEquals("GET", first.method());
		assertEquals("https://example.com/a/", first.url());
		assertNull(first.body());
		assertEquals("#1", first.displayName());
		HttpRequestSpec second = file.requests().get(1);
		assertEquals("GET", second.method());
		assertEquals("https://example.com:8080/api/html/get?id=123", second.url());
		assertEquals("#2", second.displayName());
	}

	@Test
	public void headersBodyAndVersion() {
		HttpFile file = HttpFile.parse("""
				POST https://example.com/api HTTP/1.1
				Content-Type: application/json
				Authorization: Bearer {{token}}

				{ "key" : "value", "list": [1, 2, 3] }
				""");
		HttpRequestSpec request = file.requests().get(0);
		assertEquals("POST", request.method());
		assertEquals("https://example.com/api", request.url());
		assertEquals("HTTP/1.1", request.httpVersion());
		assertEquals(List.of(new Header("Content-Type", "application/json"),
				new Header("Authorization", "Bearer {{token}}")), request.headers());
		assertEquals("{ \"key\" : \"value\", \"list\": [1, 2, 3] }", request.body());
		assertEquals(4, request.bodyLine());
	}

	@Test
	public void names() {
		HttpFile file = HttpFile.parse("""
				### First
				GET http://a

				###
				# @name Second
				GET http://b

				###
				// @name = Third
				GET http://c
				""");
		assertEquals(List.of("First", "Second", "Third"),
				file.requests().stream().map(HttpRequestSpec::displayName).toList());
		assertSame(file.requests().get(1), file.request("Second"));
	}

	@Test
	public void urlOnSeveralLines() {
		HttpFile file = HttpFile.parse("""
				GET http://example.com:8080
				    /api
				    /html
				    /get
				    ?id=123
				    &value=content
				Accept: text/html
				""");
		HttpRequestSpec request = file.requests().get(0);
		assertEquals("http://example.com:8080/api/html/get?id=123&value=content", request.url());
		assertEquals(1, request.headers().size());
	}

	@Test
	public void queryOnSeveralLinesWithoutIndentation() {
		HttpFile file = HttpFile.parse("""
				GET https://example.com:8080/api/get/html?
				firstname=John&
				lastname=Doe&
				planet=Tatooine
				Accept: */*
				""");
		HttpRequestSpec request = file.requests().get(0);
		assertEquals("https://example.com:8080/api/get/html?firstname=John&lastname=Doe&planet=Tatooine",
				request.url());
		assertEquals(1, request.headers().size());
		HttpFile indented = HttpFile.parse("""
				GET https://example.com:8080/api/get/html?
				  firstname=John&
				  lastname=Doe
				""");
		assertEquals("https://example.com:8080/api/get/html?firstname=John&lastname=Doe",
				indented.requests().get(0).url());
	}

	@Test
	public void commentsAndDirectives() {
		HttpFile file = HttpFile.parse("""
				// A basic request
				# @no-redirect
				// @no-log
				# @timeout 600
				// @connection-timeout 2 m
				# @no-cookie-jar
				GET example.com/status/301
				# a comment between the headers
				Accept: */*
				""");
		HttpRequestSpec request = file.requests().get(0);
		assertTrue(request.hasDirective("no-redirect"));
		assertTrue(request.hasDirective("no-log"));
		assertTrue(request.hasDirective("no-cookie-jar"));
		assertFalse(request.hasDirective("no-auto-encoding"));
		assertEquals("600", request.directives().get("timeout"));
		assertEquals("2 m", request.directives().get("connection-timeout"));
		assertEquals(List.of(new Header("Accept", "*/*")), request.headers());
	}

	@Test
	public void inPlaceVariables() {
		HttpFile file = HttpFile.parse("""
				@host = example.com
				@port=8080
				@base = http://{{host}}:{{port}}

				GET {{base}}/api
				""");
		assertEquals("example.com", file.variables().get("host"));
		assertEquals("8080", file.variables().get("port"));
		assertEquals("http://{{host}}:{{port}}", file.variables().get("base"));
		assertEquals("{{base}}/api", file.requests().get(0).url());
	}

	@Test
	public void scriptsAndOutput() {
		HttpFile file = HttpFile.parse("""
				< {%
				  request.variables.set("id", "42");
				%}
				< ./pre.js
				POST https://httpbin.org/post
				Content-Type: application/json

				{
				  "id": 999
				}

				> {%
				client.global.set("my_cookie", response.headers.valuesOf("Set-Cookie")[0]);
				%}
				> ./handler.js
				>>! {{$historyFolder}}/myFile.json
				""");
		HttpRequestSpec request = file.requests().get(0);
		assertEquals(2, request.preScripts().size());
		assertEquals("\n  request.variables.set(\"id\", \"42\");\n", request.preScripts().get(0).text());
		assertEquals(0, request.preScripts().get(0).line());
		assertEquals("./pre.js", request.preScripts().get(1).path());
		assertEquals("{\n  \"id\": 999\n}", request.body());
		assertEquals(2, request.handlers().size());
		assertTrue(request.handlers().get(0).text().contains("client.global.set"));
		assertEquals("./handler.js", request.handlers().get(1).path());
		assertEquals("{{$historyFolder}}/myFile.json", request.output().path());
		assertTrue(request.output().overwrite());
	}

	@Test
	public void handlerOnOneLineWithoutBody() {
		HttpFile file = HttpFile.parse("""
				GET http://a
				> {% client.log("x") %}
				>> out.json
				""");
		HttpRequestSpec request = file.requests().get(0);
		assertNull(request.body());
		assertEquals(" client.log(\"x\") ", request.handlers().get(0).text());
		assertEquals("out.json", request.output().path());
		assertFalse(request.output().overwrite());
	}

	@Test
	public void bodyFromFileAndMultipart() {
		HttpFile file = HttpFile.parse("""
				POST https://example.com/api/upload HTTP/1.1
				Content-Type: multipart/form-data; boundary=boundary

				--boundary
				Content-Disposition: form-data; name="first"; filename="input.txt"

				< ./input.txt
				--boundary--
				""");
		assertEquals("""
				--boundary
				Content-Disposition: form-data; name="first"; filename="input.txt"

				< ./input.txt
				--boundary--""", file.requests().get(0).body());
	}

	@Test
	public void trailingCommentsAreNotInTheBody() {
		HttpFile file = HttpFile.parse("""
				POST http://a
				Content-Type: text/plain

				hello
				# the next request

				###
				GET http://b

				// nothing
				""");
		assertEquals("hello", file.requests().get(0).body());
		assertNull(file.requests().get(1).body());
	}

	@Test
	public void requestAtOffset() {
		String text = "GET http://a\n\n###\nGET http://b\n";
		HttpFile file = HttpFile.parse(text);
		assertEquals("http://a", file.requestAt(0).url());
		assertEquals("http://a", file.requestAt(5).url());
		assertEquals("http://b", file.requestAt(text.indexOf("http://b")).url());
		assertEquals("http://b", file.requestAt(text.length()).url());
	}

	@Test
	public void otherMethodsAndCrLf() {
		HttpFile file = HttpFile.parse("PROPFIND http://a/dav\r\nDepth: 1\r\n\r\n<x/>\r\n");
		HttpRequestSpec request = file.requests().get(0);
		assertEquals("PROPFIND", request.method());
		assertEquals("http://a/dav", request.url());
		assertEquals(List.of(new Header("Depth", "1")), request.headers());
		assertEquals("<x/>", request.body());
	}

	@Test
	public void emptyFileAndVariablesOnly() {
		assertTrue(HttpFile.parse("").requests().isEmpty());
		HttpFile file = HttpFile.parse("@a = 1\n# comment\n");
		assertTrue(file.requests().isEmpty());
		assertEquals("1", file.variables().get("a"));
	}
}

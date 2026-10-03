package org.eclipse.ehttpeditor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ehttpeditor.core.HttpRequestSpec.Header;
import org.eclipse.ehttpeditor.core.HttpRequestSpec.Output;
import org.eclipse.ehttpeditor.core.HttpRequestSpec.Script;

/**
 * An HTTP file ({@code .http} or {@code .rest}) read with the syntax of the HTTP client of IntelliJ: requests
 * separated by {@code ###}, in-place variables ({@code @host = example.com}), tags, pre-request scripts and response
 * handlers.
 */
public final class HttpFile {

	/** The methods recognized at the start of a request line. */
	public static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS",
			"TRACE", "CONNECT");

	private static final Pattern SEPARATOR = Pattern.compile("^###(.*)$");
	private static final Pattern COMMENT = Pattern.compile("^\\s*(#|//)(.*)$");
	private static final Pattern DIRECTIVE = Pattern.compile("^\\s*@([A-Za-z][\\w-]*)(?:\\s*=\\s*|\\s+|$)(.*)$");
	private static final Pattern VARIABLE = Pattern.compile("^@([\\w.\\-]+)\\s*=\\s*(.*?)\\s*$");
	private static final Pattern METHOD = Pattern.compile("^([A-Z][A-Z-]*)\\s+(\\S.*)$");
	private static final Pattern VERSION = Pattern.compile("^(.*?)\\s+(HTTP/[\\d.]+)\\s*$");
	private static final Pattern HEADER = Pattern.compile("^([^:\\s][^:]*?)\\s*:\\s?(.*)$");
	private static final Pattern HANDLER = Pattern.compile("^>\\s*(\\{%.*|\\S.*)$");
	private static final Pattern OUTPUT = Pattern.compile("^>>(!?)\\s*(\\S.*?)\\s*$");
	private static final Pattern PRE_SCRIPT = Pattern.compile("^<\\s*(\\{%.*|\\S.*)$");

	private final String text;
	private final List<HttpRequestSpec> requests;
	private final Map<String, String> variables;

	private HttpFile(String text, List<HttpRequestSpec> requests, Map<String, String> variables) {
		this.text = text;
		this.requests = Collections.unmodifiableList(requests);
		this.variables = Collections.unmodifiableMap(variables);
	}

	/** The text of the file. */
	public String text() {
		return text;
	}

	/** The requests in their order. */
	public List<HttpRequestSpec> requests() {
		return requests;
	}

	/** The in-place variables ({@code @name = value}), raw: their values may contain {@code {{variables}}}. */
	public Map<String, String> variables() {
		return variables;
	}

	/** The request whose block contains the offset, or null. */
	public HttpRequestSpec requestAt(int offset) {
		HttpRequestSpec found = null;
		for (HttpRequestSpec request : requests) {
			if (offset >= request.start() && offset <= request.end()) {
				found = request;
			}
		}
		return found;
	}

	/** The request with this name, or null. */
	public HttpRequestSpec request(String name) {
		for (HttpRequestSpec request : requests) {
			if (name.equals(request.name())) {
				return request;
			}
		}
		return null;
	}

	/** Reads the text of an HTTP file. Never fails: what is not understood is ignored or kept in a body. */
	public static HttpFile parse(String text) {
		return new Parser(text).parse();
	}

	private static final class Parser {

		private final String text;
		private final List<String> lines = new ArrayList<>();
		private final List<Integer> lineStarts = new ArrayList<>();
		private final List<HttpRequestSpec> requests = new ArrayList<>();
		private final Map<String, String> variables = new LinkedHashMap<>();

		Parser(String text) {
			this.text = text;
			int start = 0;
			for (int i = 0; i <= text.length(); i++) {
				if (i == text.length() || text.charAt(i) == '\n') {
					int end = i > start && text.charAt(i - 1) == '\r' ? i - 1 : i;
					lines.add(text.substring(start, end));
					lineStarts.add(start);
					start = i + 1;
				}
			}
		}

		HttpFile parse() {
			int blockStart = 0;
			String blockName = null;
			for (int i = 0; i < lines.size(); i++) {
				Matcher separator = SEPARATOR.matcher(lines.get(i));
				if (separator.matches()) {
					parseBlock(blockStart, i, blockName);
					blockStart = i + 1;
					String name = separator.group(1).strip();
					blockName = name.isEmpty() ? null : name;
				}
			}
			parseBlock(blockStart, lines.size(), blockName);
			return new HttpFile(text, requests, variables);
		}

		/** Reads the lines [from, to) between two separators. */
		private void parseBlock(int from, int to, String blockName) {
			String name = blockName;
			Map<String, String> directives = new LinkedHashMap<>();
			List<Script> preScripts = new ArrayList<>();
			int i = from;
			// Before the request line: comments, tags, in-place variables and pre-request scripts.
			for (; i < to; i++) {
				String line = lines.get(i);
				String trimmed = line.strip();
				if (trimmed.isEmpty()) {
					continue;
				}
				Matcher comment = COMMENT.matcher(line);
				if (comment.matches()) {
					Matcher directive = DIRECTIVE.matcher(comment.group(2));
					if (directive.matches()) {
						String key = directive.group(1);
						String value = directive.group(2).strip();
						if (key.equals("name")) {
							if (!value.isEmpty()) {
								name = value;
							}
						} else {
							directives.put(key, value);
						}
					}
					continue;
				}
				Matcher variable = VARIABLE.matcher(trimmed);
				if (variable.matches()) {
					variables.put(variable.group(1), variable.group(2));
					continue;
				}
				Matcher preScript = PRE_SCRIPT.matcher(trimmed);
				if (preScript.matches()) {
					i = readScript(i, to, preScript.group(1), preScripts);
					continue;
				}
				break;
			}
			if (i >= to) {
				return;
			}
			int requestLine = i;
			String line = lines.get(i).strip();
			String method = "GET";
			Matcher methodMatcher = METHOD.matcher(line);
			if (methodMatcher.matches() && !methodMatcher.group(1).contains("-")
					&& !methodMatcher.group(1).endsWith(":")) {
				method = methodMatcher.group(1);
				line = methodMatcher.group(2).strip();
			}
			StringBuilder url = new StringBuilder(line);
			i++;
			// Continuation lines of the URL: indented, or starting with ? & or /.
			while (i < to) {
				String next = lines.get(i);
				if (next.isBlank()) {
					break;
				}
				boolean indented = Character.isWhitespace(next.charAt(0));
				String nextTrimmed = next.strip();
				boolean afterQuery = (url.charAt(url.length() - 1) == '?' || url.charAt(url.length() - 1) == '&')
						&& !HEADER.matcher(nextTrimmed).matches() && !COMMENT.matcher(next).matches();
				if (indented || afterQuery || nextTrimmed.startsWith("?") || nextTrimmed.startsWith("&")
						|| (nextTrimmed.startsWith("/") && !nextTrimmed.startsWith("//"))) {
					url.append(nextTrimmed);
					i++;
				} else {
					break;
				}
			}
			String httpVersion = null;
			Matcher version = VERSION.matcher(url);
			String finalUrl = url.toString();
			if (version.matches()) {
				finalUrl = version.group(1);
				httpVersion = version.group(2);
			}
			// Headers, until an empty line.
			List<Header> headers = new ArrayList<>();
			List<Script> handlers = new ArrayList<>();
			Output output = null;
			boolean bodyAllowed = true;
			for (; i < to; i++) {
				String headerLine = lines.get(i);
				if (headerLine.isBlank()) {
					i++;
					break;
				}
				String trimmed = headerLine.strip();
				if (trimmed.startsWith(">")) {
					bodyAllowed = false;
					break;
				}
				if (COMMENT.matcher(headerLine).matches()) {
					continue;
				}
				Matcher header = HEADER.matcher(trimmed);
				if (header.matches()) {
					headers.add(new Header(header.group(1).strip(), header.group(2).strip()));
				} else {
					// Not a header: the body starts without an empty line.
					break;
				}
			}
			// Body, until a response handler or a redirection of the response.
			int bodyStart = i;
			int bodyEnd = i;
			if (bodyAllowed) {
				for (; i < to; i++) {
					String trimmed = lines.get(i).strip();
					if (OUTPUT.matcher(trimmed).matches() || HANDLER.matcher(trimmed).matches()) {
						break;
					}
					bodyEnd = i + 1;
				}
			}
			// Response handlers and redirections.
			for (; i < to; i++) {
				String trimmed = lines.get(i).strip();
				Matcher out = OUTPUT.matcher(trimmed);
				if (out.matches()) {
					output = new Output(out.group(2), !out.group(1).isEmpty());
					continue;
				}
				Matcher handler = HANDLER.matcher(trimmed);
				if (handler.matches()) {
					i = readScript(i, to, handler.group(1), handlers);
				}
			}
			int bodyLine = -1;
			String body = null;
			// The body without the empty lines and the comments around it.
			while (bodyStart < bodyEnd && isBlankOrComment(lines.get(bodyStart), true)) {
				bodyStart++;
			}
			while (bodyEnd > bodyStart && isBlankOrComment(lines.get(bodyEnd - 1), false)) {
				bodyEnd--;
			}
			if (bodyStart < bodyEnd) {
				bodyLine = bodyStart;
				body = String.join("\n", lines.subList(bodyStart, bodyEnd));
			}
			int start = lineStarts.get(from);
			int end = to >= lines.size() ? text.length() : lineStarts.get(to) - 1;
			requests.add(new HttpRequestSpec(requests.size() + 1, name, method, finalUrl.strip(), httpVersion,
					List.copyOf(headers), body, Collections.unmodifiableMap(directives), List.copyOf(preScripts),
					List.copyOf(handlers), output, start, Math.max(start, end), requestLine, bodyLine));
		}

		/**
		 * Whether a line around the body is dropped: empty lines, and comments ({@code #} or {@code //} followed
		 * by a space or nothing, so that a body such as {@code //path} or {@code #id} stays).
		 */
		private static boolean isBlankOrComment(String line, boolean leading) {
			String trimmed = line.strip();
			if (trimmed.isEmpty()) {
				return true;
			}
			if (leading) {
				// A leading comment would be in the headers: the body really starts here.
				return false;
			}
			return trimmed.equals("#") || trimmed.equals("//") || trimmed.startsWith("# ")
					|| trimmed.startsWith("// ");
		}

		/**
		 * Reads a script starting on line i, whose text after {@code <} or {@code >} is start: an inline script
		 * {@code {% ... %}}, on one line or more, or the path of a file. Returns the last line of the script.
		 */
		private int readScript(int i, int to, String start, List<Script> scripts) {
			if (!start.startsWith("{%")) {
				scripts.add(new Script(null, start.strip(), i));
				return i;
			}
			String first = start.substring(2);
			int close = first.indexOf("%}");
			if (close >= 0) {
				scripts.add(new Script(first.substring(0, close), null, i));
				return i;
			}
			StringBuilder script = new StringBuilder(first);
			int firstLine = i;
			for (int j = i + 1; j < to; j++) {
				String line = lines.get(j);
				int end = line.indexOf("%}");
				script.append('\n');
				if (end >= 0) {
					script.append(line, 0, end);
					scripts.add(new Script(script.toString(), null, firstLine));
					return j;
				}
				script.append(line);
			}
			// Not closed: the script goes to the end of the block.
			scripts.add(new Script(script.toString(), null, firstLine));
			return to - 1;
		}
	}
}

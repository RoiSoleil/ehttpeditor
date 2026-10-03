package org.eclipse.ehttpeditor.ui.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ehttpeditor.Activator;
import org.eclipse.ehttpeditor.core.DynamicVariables;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.HttpRequestSpec;
import org.eclipse.ehttpeditor.core.Variables;
import org.eclipse.ehttpeditor.ui.HttpDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.swt.graphics.Image;

/**
 * The completion in the HTTP files: the {@code {{variables}}} (environment, in-place, global, dynamic), the tags
 * after {@code # @}, the methods, the headers and the content types.
 */
public class HttpContentAssistProcessor implements IContentAssistProcessor {

	static final List<String[]> DIRECTIVES = List.of( //
			new String[] { "name", "The name of the request: # @name login" }, //
			new String[] { "no-redirect", "Do not follow the redirections (3xx)" }, //
			new String[] { "no-log", "Do not keep the request in the history (sensitive data)" }, //
			new String[] { "no-cookie-jar", "Do not save the cookies received" }, //
			new String[] { "no-auto-encoding", "Do not encode the URL and the parameters" }, //
			new String[] { "timeout", "Timeout of the response: # @timeout 600, 500 ms, 2 m" }, //
			new String[] { "connection-timeout", "Timeout of the connection: # @connection-timeout 2 m" });

	static final List<String> HEADERS = List.of("Accept", "Accept-Charset", "Accept-Encoding", "Accept-Language",
			"Authorization", "Cache-Control", "Connection", "Content-Disposition", "Content-Encoding",
			"Content-Language", "Content-Type", "Cookie", "Date", "If-Match", "If-Modified-Since", "If-None-Match",
			"If-Unmodified-Since", "Origin", "Pragma", "Range", "Referer", "User-Agent", "X-Correlation-ID",
			"X-Forwarded-For", "X-Request-ID", "X-Requested-With");

	static final List<String> MIME_TYPES = List.of("application/json", "application/xml",
			"application/x-www-form-urlencoded", "multipart/form-data; boundary=boundary", "text/plain", "text/html",
			"text/xml", "application/octet-stream", "application/graphql", "*/*");

	private static final Pattern VARIABLE_PREFIX = Pattern.compile("\\{\\{\\s*([^{}\\s]*)$");
	private static final Pattern DIRECTIVE_PREFIX = Pattern.compile("^\\s*(#|//)\\s*@([\\w-]*)$");
	private static final Pattern HEADER_VALUE_PREFIX = Pattern.compile("^\\s*(Content-Type|Accept)\\s*:\\s*(\\S*)$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern AUTHORIZATION_PREFIX = Pattern.compile("^\\s*Authorization\\s*:\\s*(\\S*)$",
			Pattern.CASE_INSENSITIVE);

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IDocument document = viewer.getDocument();
		try {
			IRegion lineRegion = document.getLineInformationOfOffset(offset);
			int lineNumber = document.getLineOfOffset(offset);
			String prefix = document.get(lineRegion.getOffset(), offset - lineRegion.getOffset());
			String after = document.get(offset, Math.min(2, document.getLength() - offset));
			List<ICompletionProposal> proposals = new ArrayList<>();

			Matcher variable = VARIABLE_PREFIX.matcher(prefix);
			if (variable.find()) {
				variables(document, offset, variable.group(1), after.startsWith("}}"), proposals);
				return proposals.toArray(ICompletionProposal[]::new);
			}
			Matcher directive = DIRECTIVE_PREFIX.matcher(prefix);
			if (directive.matches()) {
				String typed = directive.group(2);
				for (String[] d : DIRECTIVES) {
					if (d[0].startsWith(typed)) {
						String replacement = d[0].equals("name") || d[0].endsWith("timeout") ? d[0] + " " : d[0];
						proposals.add(proposal(replacement, offset - typed.length(), typed.length(), "@" + d[0], d[1],
								null));
					}
				}
				return proposals.toArray(ICompletionProposal[]::new);
			}
			Matcher headerValue = HEADER_VALUE_PREFIX.matcher(prefix);
			if (headerValue.matches()) {
				String typed = headerValue.group(2);
				for (String mime : MIME_TYPES) {
					if (mime.startsWith(typed.toLowerCase(Locale.ROOT))) {
						proposals.add(proposal(mime, offset - typed.length(), typed.length(), mime, null, null));
					}
				}
				return proposals.toArray(ICompletionProposal[]::new);
			}
			Matcher authorization = AUTHORIZATION_PREFIX.matcher(prefix);
			if (authorization.matches()) {
				String typed = authorization.group(1);
				for (String[] scheme : new String[][] { { "Basic ", "Basic user password: encoded in Base64" },
						{ "Bearer ", "Bearer token" }, { "Digest ", "Digest user password: answers the challenge" } }) {
					if (scheme[0].toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
						proposals.add(proposal(scheme[0], offset - typed.length(), typed.length(), scheme[0].strip(),
								scheme[1], null));
					}
				}
				return proposals.toArray(ICompletionProposal[]::new);
			}
			if (prefix.isBlank() || !prefix.contains(" ") && !prefix.contains(":")) {
				String typed = prefix.strip();
				HttpFile file = HttpFile.parse(document.get());
				HttpRequestSpec request = file.requestAt(offset);
				if (request == null || lineNumber <= request.requestLine()) {
					for (String method : HttpFile.METHODS) {
						if (method.startsWith(typed.toUpperCase(Locale.ROOT))) {
							proposals.add(proposal(method + " ", offset - typed.length(), typed.length(), method,
									null, null));
						}
					}
					if ("###".startsWith(typed)) {
						proposals.add(proposal("###\n", offset - typed.length(), typed.length(), "###",
								"Separates the requests", null));
					}
				} else if (inHeaders(document, request, lineNumber)) {
					for (String header : HEADERS) {
						if (header.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
							proposals.add(proposal(header + ": ", offset - typed.length(), typed.length(), header,
									null, null));
						}
					}
				}
			}
			return proposals.toArray(ICompletionProposal[]::new);
		} catch (BadLocationException e) {
			return new ICompletionProposal[0];
		}
	}

	/** Whether a line is in the headers of the request: after the request line, before the first empty line. */
	private static boolean inHeaders(IDocument document, HttpRequestSpec request, int lineNumber)
			throws BadLocationException {
		for (int line = request.requestLine() + 1; line < lineNumber; line++) {
			IRegion region = document.getLineInformation(line);
			if (document.get(region.getOffset(), region.getLength()).isBlank()) {
				return false;
			}
		}
		return lineNumber > request.requestLine();
	}

	private static void variables(IDocument document, int offset, String typed, boolean closed,
			List<ICompletionProposal> proposals) {
		HttpDocument httpDocument = HttpDocument.of(document);
		HttpFile file = HttpFile.parse(document.get());
		String suffix = closed ? "" : "}}";
		if (httpDocument != null) {
			Variables variables = httpDocument.variables(file);
			for (Map.Entry<String, Variables.Value> entry : variables.all().entrySet()) {
				String name = entry.getKey();
				if (name.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
					String value = entry.getValue().raw();
					proposals.add(proposal(name + suffix, offset - typed.length(), typed.length(),
							name + " - " + entry.getValue().source().label(),
							value != null && value.length() > 200 ? value.substring(0, 200) + "..." : value,
							Activator.IMG_ENVIRONMENT));
				}
			}
		} else {
			for (String name : file.variables().keySet()) {
				if (name.startsWith(typed)) {
					proposals.add(proposal(name + suffix, offset - typed.length(), typed.length(), name,
							file.variables().get(name), Activator.IMG_ENVIRONMENT));
				}
			}
		}
		for (String[] dynamic : DynamicVariables.NAMES) {
			if (dynamic[0].startsWith(typed)) {
				String replacement = dynamic[0].endsWith(".") ? dynamic[0] : dynamic[0] + suffix;
				proposals.add(proposal(replacement, offset - typed.length(), typed.length(), dynamic[0],
						dynamic[1], null));
			}
		}
	}

	private static ICompletionProposal proposal(String replacement, int offset, int length, String display,
			String info, String image) {
		// The proposals may be computed outside of the UI thread: the images only in it.
		Image img = image != null && org.eclipse.swt.widgets.Display.getCurrent() != null
				? Activator.getDefault().getImageRegistry().get(image)
				: null;
		return new CompletionProposal(replacement, offset, length, replacement.length(), img, display, null, info);
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return null;
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { '{', '@', '$' };
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}
}

package org.eclipse.ehttpeditor.ui.editor;

import java.util.regex.Matcher;

import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.Variables;
import org.eclipse.ehttpeditor.ui.HttpDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;

/** The value of a {@code {{variable}}} under the mouse, and where it comes from. */
public class HttpTextHover implements ITextHover, ITextHoverExtension2 {

	@Override
	public IRegion getHoverRegion(ITextViewer viewer, int offset) {
		IDocument document = viewer.getDocument();
		try {
			IRegion line = document.getLineInformationOfOffset(offset);
			String text = document.get(line.getOffset(), line.getLength());
			Matcher matcher = Variables.REFERENCE.matcher(text);
			int column = offset - line.getOffset();
			while (matcher.find()) {
				if (column >= matcher.start() && column <= matcher.end()) {
					return new Region(line.getOffset() + matcher.start(), matcher.end() - matcher.start());
				}
			}
		} catch (BadLocationException e) {
			// Outside the document.
		}
		return null;
	}

	@Override
	public Object getHoverInfo2(ITextViewer viewer, IRegion region) {
		return info(viewer, region);
	}

	@Override
	@Deprecated
	public String getHoverInfo(ITextViewer viewer, IRegion region) {
		return info(viewer, region);
	}

	private static String info(ITextViewer viewer, IRegion region) {
		if (region == null) {
			return null;
		}
		IDocument document = viewer.getDocument();
		HttpDocument httpDocument = HttpDocument.of(document);
		if (httpDocument == null) {
			return null;
		}
		try {
			Matcher matcher = Variables.REFERENCE.matcher(document.get(region.getOffset(), region.getLength()));
			if (!matcher.matches()) {
				return null;
			}
			String name = matcher.group(1);
			Variables variables = httpDocument.variables(HttpFile.parse(document.get()));
			Variables.Value value = variables.lookup(name);
			if (value == null) {
				return name + ": unresolved variable"
						+ (name.startsWith("$") ? "" : " (it may be set by a script: request.variables.set)");
			}
			if (value.source() == Variables.Source.DYNAMIC) {
				return name + ": dynamic variable, for example " + value.raw();
			}
			String resolved = variables.resolve(name);
			StringBuilder sb = new StringBuilder(name).append(" = ").append(resolved);
			if (!resolved.equals(value.raw())) {
				sb.append("\n(").append(value.raw()).append(')');
			}
			sb.append("\n").append(value.source().label());
			return sb.toString();
		} catch (BadLocationException e) {
			return null;
		}
	}
}

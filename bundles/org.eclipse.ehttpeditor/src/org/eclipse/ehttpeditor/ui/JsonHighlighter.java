package org.eclipse.ehttpeditor.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/** Colors the keys, the strings, the numbers and the keywords of a JSON text. */
final class JsonHighlighter {

	private static final int MAX_LENGTH = 1_000_000;

	private JsonHighlighter() {
	}

	static void highlight(StyledText widget) {
		String text = widget.getText();
		if (text.length() > MAX_LENGTH) {
			return;
		}
		boolean dark = Display.isSystemDarkTheme();
		Color key = dark ? new Color(0x9c, 0xdc, 0xfe) : new Color(0x87, 0x10, 0x94);
		Color string = dark ? new Color(0xce, 0x91, 0x78) : new Color(0x06, 0x7d, 0x17);
		Color number = dark ? new Color(0xb5, 0xce, 0xa8) : new Color(0x17, 0x50, 0xeb);
		Color keyword = dark ? new Color(0x56, 0x9c, 0xd6) : new Color(0x00, 0x33, 0xb3);
		List<StyleRange> ranges = new ArrayList<>();
		int i = 0;
		int length = text.length();
		while (i < length) {
			char c = text.charAt(i);
			if (c == '"') {
				int start = i++;
				while (i < length && text.charAt(i) != '"') {
					if (text.charAt(i) == '\\') {
						i++;
					}
					i++;
				}
				i = Math.min(i + 1, length);
				int next = i;
				while (next < length && Character.isWhitespace(text.charAt(next))) {
					next++;
				}
				boolean isKey = next < length && text.charAt(next) == ':';
				ranges.add(new StyleRange(start, i - start, isKey ? key : string, null));
			} else if (c == '-' || Character.isDigit(c)) {
				int start = i;
				while (i < length && "+-.eE0123456789".indexOf(text.charAt(i)) >= 0) {
					i++;
				}
				ranges.add(new StyleRange(start, i - start, number, null));
			} else if (text.startsWith("true", i) || text.startsWith("null", i)) {
				ranges.add(new StyleRange(i, 4, keyword, null));
				i += 4;
			} else if (text.startsWith("false", i)) {
				ranges.add(new StyleRange(i, 5, keyword, null));
				i += 5;
			} else {
				i++;
			}
		}
		widget.setStyleRanges(ranges.toArray(StyleRange[]::new));
	}
}

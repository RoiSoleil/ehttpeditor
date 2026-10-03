package org.eclipse.ehttpeditor.ui.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.HttpRequestSpec;
import org.eclipse.ehttpeditor.ui.EnvironmentSelection;
import org.eclipse.ehttpeditor.ui.HttpDocument;
import org.eclipse.ehttpeditor.ui.RunRequests;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.swt.events.MouseEvent;

/**
 * Above each request of an HTTP file: "▶ Send request", and above the first one the environment ("Environment:
 * dev"), which a click changes. The gutter icons of IntelliJ, as code minings.
 */
public class HttpCodeMiningProvider extends AbstractCodeMiningProvider {

	@Override
	public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
			IProgressMonitor monitor) {
		IDocument document = viewer.getDocument();
		return CompletableFuture.supplyAsync(() -> {
			HttpDocument httpDocument = HttpDocument.of(document);
			if (httpDocument == null || monitor.isCanceled()) {
				return List.of();
			}
			HttpFile file = HttpFile.parse(document.get());
			List<ICodeMining> minings = new ArrayList<>();
			boolean first = true;
			for (HttpRequestSpec request : file.requests()) {
				int line = request.requestLine();
				int index = request.index();
				try {
					minings.add(new ActionMining(line, document, this, "▶ Send request",
							e -> runRequest(viewer, index)));
					if (first) {
						if (file.requests().size() > 1) {
							minings.add(new ActionMining(line, document, this, "▶▶ Run all requests",
									e -> runAll(viewer)));
						}
						minings.add(new ActionMining(line, document, this,
								"Environment: " + EnvironmentSelection.label(),
								e -> EnvironmentSelection.choose(viewer.getTextWidget().getShell(), httpDocument)));
						first = false;
					}
				} catch (BadLocationException e) {
					// The document changed: the minings are computed again.
				}
			}
			return minings;
		});
	}

	private static void runRequest(ITextViewer viewer, int index) {
		IDocument document = viewer.getDocument();
		HttpDocument httpDocument = HttpDocument.of(document);
		if (httpDocument == null) {
			return;
		}
		HttpFile file = HttpFile.parse(document.get());
		if (index <= file.requests().size()) {
			RunRequests.run(httpDocument, file, List.of(file.requests().get(index - 1)));
		}
	}

	private static void runAll(ITextViewer viewer) {
		IDocument document = viewer.getDocument();
		HttpDocument httpDocument = HttpDocument.of(document);
		if (httpDocument != null) {
			HttpFile file = HttpFile.parse(document.get());
			RunRequests.run(httpDocument, file, file.requests());
		}
	}

	/** A clickable mining above a line. */
	private static final class ActionMining extends LineHeaderCodeMining {

		ActionMining(int line, IDocument document, ICodeMiningProvider provider, String label,
				Consumer<MouseEvent> action) throws BadLocationException {
			super(line, document, provider, action);
			setLabel(label);
		}
	}
}

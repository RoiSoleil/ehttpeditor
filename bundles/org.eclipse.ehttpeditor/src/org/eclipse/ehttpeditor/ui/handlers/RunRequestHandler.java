package org.eclipse.ehttpeditor.ui.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.HttpRequestSpec;
import org.eclipse.ehttpeditor.ui.HttpDocument;
import org.eclipse.ehttpeditor.ui.RunRequests;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Runs the request under the cursor (parameter {@code scope} absent) or all the requests of the file
 * ({@code scope=all}).
 */
public class RunRequestHandler extends AbstractHandler {

	public static final String COMMAND_RUN = "org.eclipse.ehttpeditor.commands.runRequest";
	public static final String COMMAND_RUN_ALL = "org.eclipse.ehttpeditor.commands.runAllRequests";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		ITextEditor editor = part != null ? part.getAdapter(ITextEditor.class) : null;
		HttpDocument document = HttpDocument.of(editor);
		if (document == null) {
			return null;
		}
		IDocument text = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		HttpFile file = HttpFile.parse(text.get());
		if (COMMAND_RUN_ALL.equals(event.getCommand().getId())) {
			if (file.requests().isEmpty()) {
				status(editor, "No request in the file");
				return null;
			}
			RunRequests.run(document, file, file.requests());
			return null;
		}
		ISelection selection = editor.getSelectionProvider().getSelection();
		int offset = selection instanceof ITextSelection textSelection ? textSelection.getOffset() : 0;
		HttpRequestSpec request = file.requestAt(offset);
		if (request == null) {
			status(editor, "No request at the cursor");
			return null;
		}
		RunRequests.run(document, file, List.of(request));
		return null;
	}

	private static void status(ITextEditor editor, String message) {
		editor.getEditorSite().getActionBars().getStatusLineManager().setMessage(message);
	}
}

package org.eclipse.ehttpeditor.ui.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ehttpeditor.ui.EnvironmentSelection;
import org.eclipse.ehttpeditor.ui.HttpDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/** Asks for the environment of the requests. */
public class SelectEnvironmentHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		HttpDocument document = part != null ? HttpDocument.of(part.getAdapter(ITextEditor.class)) : null;
		EnvironmentSelection.choose(HandlerUtil.getActiveShell(event), document);
		return null;
	}
}

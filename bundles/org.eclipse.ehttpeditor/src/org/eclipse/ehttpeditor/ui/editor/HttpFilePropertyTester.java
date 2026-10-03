package org.eclipse.ehttpeditor.ui.editor;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.ehttpeditor.ui.HttpDocument;
import org.eclipse.ui.IEditorInput;

/**
 * {@code org.eclipse.ehttpeditor.httpFile}: whether an editor input is an HTTP file (.http, .rest), in the workspace or
 * not.
 */
public class HttpFilePropertyTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		return receiver instanceof IEditorInput input && input.getName() != null
				&& HttpDocument.isHttpFileName(input.getName());
	}
}

package org.eclipse.ehttpeditor.app;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.swt.program.Program;

/** Help > Documentation: the guide on GitHub, in the browser of the system. */
public class DocumentationHandler extends AbstractHandler {

	static final String URL = "https://github.com/RoiSoleil/ehttpeditor/blob/main/docs/guide.md"; //$NON-NLS-1$

	@Override
	public Object execute(ExecutionEvent event) {
		Program.launch(URL);
		return null;
	}
}

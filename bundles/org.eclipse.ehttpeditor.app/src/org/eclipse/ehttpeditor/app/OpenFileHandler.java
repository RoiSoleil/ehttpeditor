package org.eclipse.ehttpeditor.app;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/** File > Open File...: the HTTP files first, then the environment files. */
public class OpenFileHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		FileDialog dialog = new FileDialog(window.getShell(), SWT.OPEN | SWT.MULTI);
		dialog.setFilterExtensions(new String[] { "*.http;*.rest", "*.env.json", "*" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		dialog.setFilterNames(new String[] { "HTTP Request Files (*.http, *.rest)", //$NON-NLS-1$
				"Environment Files (*.env.json)", "All Files" }); //$NON-NLS-1$ //$NON-NLS-2$
		if (dialog.open() != null) {
			for (String name : dialog.getFileNames()) {
				OpenDocuments.open(window, new File(dialog.getFilterPath(), name));
			}
		}
		return null;
	}
}

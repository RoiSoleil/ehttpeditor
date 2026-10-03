package org.eclipse.ehttpeditor.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/** File > New HTTP File...: a file with the commented examples of templates/requests.http, opened in its editor. */
public class NewFileHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		FileDialog dialog = new FileDialog(window.getShell(), SWT.SAVE);
		dialog.setFilterExtensions(new String[] { "*.http" }); //$NON-NLS-1$
		dialog.setFileName("requests.http"); //$NON-NLS-1$
		dialog.setOverwrite(true);
		String path = dialog.open();
		if (path == null) {
			return null;
		}
		File file = new File(new File(path).getName().contains(".") ? path : path + ".http"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			create(file);
		} catch (IOException e) {
			throw new ExecutionException("Cannot create " + file, e); //$NON-NLS-1$
		}
		OpenDocuments.open(window, file);
		return null;
	}

	/** Writes the examples in the file. */
	static void create(File file) throws IOException {
		file.getAbsoluteFile().getParentFile().mkdirs();
		try (InputStream template = NewFileHandler.class.getResourceAsStream("/templates/requests.http")) { //$NON-NLS-1$
			Files.copy(template, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}

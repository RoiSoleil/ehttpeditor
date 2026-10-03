package org.eclipse.ehttpeditor.app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * Opens the files given to the launcher: kept until the window is open, then opened at once. Without them, the
 * window opens requests.http of the workspace, with commented examples at the first start.
 */
class OpenDocuments implements Listener {

	private final List<String> pending = new ArrayList<>();

	private IWorkbenchWindow window;

	OpenDocuments(Display display) {
		display.addListener(SWT.OpenDocument, this);
	}

	@Override
	public void handleEvent(Event event) {
		if (window == null) {
			pending.add(event.text);
		} else {
			open(window, new File(event.text));
		}
	}

	void open(IWorkbenchWindow opened) {
		window = opened;
		if (pending.isEmpty()) {
			openRequests(window);
		}
		pending.forEach(path -> open(window, new File(path)));
		pending.clear();
	}

	private static void openRequests(IWorkbenchWindow window) {
		Location workspace = Platform.getInstanceLocation();
		if (workspace == null || !workspace.isSet()) {
			return;
		}
		File file = new File(workspace.getURL().getFile(), Templates.REQUESTS);
		try {
			if (!file.exists()) {
				Templates.write(Templates.REQUESTS, file);
			}
		} catch (IOException e) {
			ILog.of(OpenDocuments.class).error("Cannot create " + file, e); //$NON-NLS-1$
			return;
		}
		open(window, file);
	}

	/** Opens a file of the disk in its editor (the Generic Editor for the HTTP files). */
	static void open(IWorkbenchWindow window, File file) {
		try {
			IDE.openEditorOnFileStore(window.getActivePage(), EFS.getLocalFileSystem().fromLocalFile(file));
		} catch (PartInitException e) {
			StatusManager.getManager().handle(e.getStatus(), StatusManager.SHOW | StatusManager.LOG);
		}
	}
}

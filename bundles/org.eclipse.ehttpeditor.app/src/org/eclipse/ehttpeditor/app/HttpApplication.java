package org.eclipse.ehttpeditor.app;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

/**
 * The standalone EHttpEditor: a window with the editors of the HTTP files and the view of the responses, without
 * the rest of the IDE.
 */
public class HttpApplication implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		Display display = PlatformUI.createDisplay();
		// The files given to the launcher (--launcher.openFile, a double click), received before the window opens
		OpenDocuments documents = new OpenDocuments(display);
		Location workspace = Platform.getInstanceLocation();
		try {
			if (workspace != null && workspace.isSet() && !workspace.lock()) {
				MessageDialog.openError(null, "EHttpEditor", //$NON-NLS-1$
						"EHttpEditor is already running with the workspace " + workspace.getURL().getFile()); //$NON-NLS-1$
				return EXIT_OK;
			}
			int code = PlatformUI.createAndRunWorkbench(display, new HttpWorkbenchAdvisor(documents));
			return code == PlatformUI.RETURN_RESTART ? EXIT_RESTART : EXIT_OK;
		} finally {
			if (workspace != null && workspace.isLocked()) {
				workspace.release();
			}
			display.dispose();
		}
	}

	@Override
	public void stop() {
		if (!PlatformUI.isWorkbenchRunning()) {
			return;
		}
		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDisplay().syncExec(() -> {
			if (!workbench.getDisplay().isDisposed()) {
				workbench.close();
			}
		});
	}
}

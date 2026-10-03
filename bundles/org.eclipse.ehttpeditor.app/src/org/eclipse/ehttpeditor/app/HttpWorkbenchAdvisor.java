package org.eclipse.ehttpeditor.app;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.eclipse.ui.ide.IDE;

/** The window: no toolbar and no perspective, the layout of {@link HttpPerspective} at each start. */
class HttpWorkbenchAdvisor extends WorkbenchAdvisor {

	/**
	 * The Open File... of the IDE (the one of plugin.xml shows the HTTP files first) and Convert Line Delimiters To,
	 * added to the menu File by the IDE and the text editors.
	 */
	private static final String[] HIDDEN_ACTION_SETS = { "org.eclipse.ui.actionSet.openFiles", //$NON-NLS-1$
			"org.eclipse.ui.edit.text.actionSet.convertLineDelimitersTo" }; //$NON-NLS-1$

	private final OpenDocuments documents;

	HttpWorkbenchAdvisor(OpenDocuments documents) {
		this.documents = documents;
	}

	@Override
	public void initialize(IWorkbenchConfigurer configurer) {
		// The icons and the labels of the files of the workspace
		IDE.registerAdapters();
	}

	@Override
	public String getInitialWindowPerspectiveId() {
		return HttpPerspective.ID;
	}

	@Override
	public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer) {
		return new WorkbenchWindowAdvisor(configurer) {

			@Override
			public void preWindowOpen() {
				IWorkbenchWindowConfigurer window = getWindowConfigurer();
				window.setInitialSize(new Point(1100, 800));
				window.setShowCoolBar(false);
				window.setShowPerspectiveBar(false);
				window.setShowStatusLine(true);
				window.setShowProgressIndicator(true);
			}

			@Override
			public ActionBarAdvisor createActionBarAdvisor(IActionBarConfigurer actionBars) {
				return new HttpActionBarAdvisor(actionBars);
			}

			@Override
			public void postWindowOpen() {
				IWorkbenchWindow window = getWindowConfigurer().getWindow();
				IWorkbenchPage page = window.getActivePage();
				if (page != null) {
					for (String actionSet : HIDDEN_ACTION_SETS) {
						page.hideActionSet(actionSet);
					}
				}
				documents.open(window);
			}
		};
	}

	@Override
	public void postShutdown() {
		try {
			ResourcesPlugin.getWorkspace().save(true, new NullProgressMonitor());
		} catch (CoreException e) {
			ILog.of(getClass()).log(e.getStatus());
		}
	}
}

package org.eclipse.ehttpeditor.app;

import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

/** The editors of the HTTP files, and below them the view of the responses, which stays open. */
public class HttpPerspective implements IPerspectiveFactory {

	static final String ID = "org.eclipse.ehttpeditor.app.perspective"; //$NON-NLS-1$

	/** HttpResponseView.ID: the plug-in does not export its packages. */
	private static final String RESPONSE_VIEW = "org.eclipse.ehttpeditor.views.response"; //$NON-NLS-1$

	@Override
	public void createInitialLayout(IPageLayout layout) {
		layout.addStandaloneView(RESPONSE_VIEW, true, IPageLayout.BOTTOM, 0.55f, layout.getEditorArea());
		layout.getViewLayout(RESPONSE_VIEW).setCloseable(false);
		layout.getViewLayout(RESPONSE_VIEW).setMoveable(false);
	}
}

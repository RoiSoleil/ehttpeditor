package org.eclipse.ehttpeditor.ui.editor;

import org.eclipse.ehttpeditor.ui.HttpDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextService;

/**
 * Activates the context of the HTTP editors (its key bindings: Ctrl+Enter...) in the site of each Generic Editor
 * open on an HTTP file: the context is then active while that editor is.
 */
public class HttpEditorContextActivator implements IStartup {

	public static final String CONTEXT_ID = "org.eclipse.ehttpeditor.editorScope";
	/** The editors whose site has the context (UI thread only). */
	private static final java.util.Set<IEditorPart> ACTIVATED = java.util.Collections
			.newSetFromMap(new java.util.WeakHashMap<>());

	private final IPartListener2 partListener = new IPartListener2() {
		@Override
		public void partOpened(IWorkbenchPartReference reference) {
			activate(reference.getPart(false));
		}

		@Override
		public void partActivated(IWorkbenchPartReference reference) {
			activate(reference.getPart(false));
		}

		@Override
		public void partInputChanged(IWorkbenchPartReference reference) {
			activate(reference.getPart(false));
		}
	};

	@Override
	public void earlyStartup() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDisplay().asyncExec(() -> {
			for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
				hook(window);
			}
			workbench.addWindowListener(new IWindowListener() {
				@Override
				public void windowOpened(IWorkbenchWindow window) {
					hook(window);
				}

				@Override
				public void windowActivated(IWorkbenchWindow window) {
				}

				@Override
				public void windowDeactivated(IWorkbenchWindow window) {
				}

				@Override
				public void windowClosed(IWorkbenchWindow window) {
				}
			});
		});
	}

	private void hook(IWorkbenchWindow window) {
		window.getPartService().addPartListener(partListener);
		for (IWorkbenchPage page : window.getPages()) {
			for (org.eclipse.ui.IEditorReference reference : page.getEditorReferences()) {
				activate(reference.getPart(false));
			}
		}
	}

	private static void activate(IWorkbenchPart part) {
		if (!(part instanceof IEditorPart editor) || !HttpDocument.isHttpFileName(editor.getEditorInput().getName())
				|| !ACTIVATED.add(editor)) {
			// Once per editor: the activation lasts as long as its site.
			return;
		}
		IContextService service = editor.getSite().getService(IContextService.class);
		if (service != null) {
			service.activateContext(CONTEXT_ID);
		}
	}
}

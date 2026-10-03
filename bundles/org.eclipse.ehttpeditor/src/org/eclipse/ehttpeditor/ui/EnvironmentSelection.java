package org.eclipse.ehttpeditor.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ehttpeditor.Activator;
import org.eclipse.ehttpeditor.core.Environments;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.ISourceViewerExtension5;

/** The choice of the environment, shared by the editors and the view. */
public final class EnvironmentSelection {

	private static final String NONE = "No environment";

	private EnvironmentSelection() {
	}

	/** The label of the selected environment. */
	public static String label() {
		String environment = Activator.getDefault().environment();
		return environment != null ? environment : NONE;
	}

	/**
	 * Asks for the environment among the ones of the document (or of the active HTTP editor when document is
	 * null), and selects it.
	 */
	public static void choose(Shell shell, HttpDocument document) {
		HttpDocument source = document != null ? document : activeDocument();
		Environments environments = source != null ? source.environments() : Environments.empty();
		List<String> names = new ArrayList<>(environments.names());
		String current = Activator.getDefault().environment();
		if (current != null && !names.contains(current)) {
			names.add(current);
		}
		names.add(0, NONE);
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider());
		dialog.setTitle("HTTP Client Environment");
		dialog.setMessage(names.size() == 1
				? "No environment found: define them in " + Environments.PUBLIC_FILE + " and "
						+ Environments.PRIVATE_FILE + " next to the HTTP file or above it."
				: "Environment of the requests (" + Environments.PUBLIC_FILE + "):");
		dialog.setElements(names.toArray());
		dialog.setMultipleSelection(false);
		dialog.setInitialSelections(current != null ? current : NONE);
		if (dialog.open() == Window.OK && dialog.getFirstResult() != null) {
			String chosen = (String) dialog.getFirstResult();
			select(chosen.equals(NONE) ? null : chosen);
		}
	}

	/** Selects an environment and refreshes the code minings of the HTTP editors. */
	public static void select(String environment) {
		Activator.getDefault().setEnvironment(environment);
		refreshEditors();
	}

	/** Refreshes the code minings of the open HTTP editors (the environment they show). */
	public static void refreshEditors() {
		for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
			for (IWorkbenchPage page : window.getPages()) {
				for (IEditorReference reference : page.getEditorReferences()) {
					IWorkbenchPart part = reference.getPart(false);
					if (part == null || !HttpDocument.isHttpFileName(reference.getName())) {
						continue;
					}
					Object target = part.getAdapter(ITextOperationTarget.class);
					if (target instanceof ISourceViewerExtension5 viewer) {
						viewer.updateCodeMinings();
					}
				}
			}
		}
	}

	/**
	 * The HTTP document of the active editor. When the active editor is an environment file, the environments of its
	 * folder; when it is another file, the first open HTTP editor. Null without any.
	 */
	public static HttpDocument activeDocument() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window != null ? window.getActivePage() : null;
		if (page == null) {
			return null;
		}
		IEditorPart active = page.getActiveEditor();
		if (active != null) {
			HttpDocument document = HttpDocument.of(active.getAdapter(ITextEditor.class));
			if (document == null) {
				document = HttpDocument.ofEnvironmentFile(active.getEditorInput());
			}
			if (document != null) {
				return document;
			}
		}
		for (IEditorReference reference : page.getEditorReferences()) {
			IEditorPart editor = HttpDocument.isHttpFileName(reference.getName()) ? reference.getEditor(false) : null;
			HttpDocument document = editor != null ? HttpDocument.of(editor.getAdapter(ITextEditor.class)) : null;
			if (document != null) {
				return document;
			}
		}
		return null;
	}
}

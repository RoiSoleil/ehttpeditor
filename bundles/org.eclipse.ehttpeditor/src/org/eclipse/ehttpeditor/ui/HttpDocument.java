package org.eclipse.ehttpeditor.ui;

import java.io.File;
import java.util.Locale;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.ehttpeditor.Activator;
import org.eclipse.ehttpeditor.core.ClientSession;
import org.eclipse.ehttpeditor.core.DynamicVariables;
import org.eclipse.ehttpeditor.core.Environments;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.Variables;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Where an HTTP document is: its file, its folder (relative paths, environment files) and the root of its project.
 *
 * @param name        the name of the file
 * @param file        the file on the disk, or null
 * @param workspace   the file of the workspace, or null
 * @param baseDir     the folder of the file, or null
 * @param projectRoot the root of the project, or the folder of the file outside the workspace
 */
public record HttpDocument(String name, File file, IFile workspace, File baseDir, File projectRoot) {

	/** Whether a file name is an HTTP file. */
	public static boolean isHttpFileName(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".http") || lower.endsWith(".rest");
	}

	/** The HTTP document behind a document of an editor, or null when it is not an HTTP file. */
	public static HttpDocument of(IDocument document) {
		if (document == null) {
			return null;
		}
		ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(document);
		if (buffer == null) {
			return null;
		}
		IPath location = buffer.getLocation();
		if (location != null) {
			IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(location);
			if (resource instanceof IFile file) {
				return of(file);
			}
		}
		IFileStore store = buffer.getFileStore();
		if (store != null) {
			try {
				File file = store.toLocalFile(0, null);
				if (file != null) {
					return of(file);
				}
			} catch (CoreException e) {
				// Not a local file.
			}
			return isHttpFileName(store.getName()) ? new HttpDocument(store.getName(), null, null, null, null) : null;
		}
		return null;
	}

	/** The HTTP document of the input of an editor, or null. */
	public static HttpDocument of(ITextEditor editor) {
		if (editor == null) {
			return null;
		}
		IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		HttpDocument httpDocument = of(document);
		if (httpDocument != null) {
			return httpDocument;
		}
		IEditorInput input = editor.getEditorInput();
		IFile file = input.getAdapter(IFile.class);
		if (file != null) {
			return of(file);
		}
		if (input instanceof IURIEditorInput uriInput && "file".equals(uriInput.getURI().getScheme())) {
			return of(new File(uriInput.getURI()));
		}
		return isHttpFileName(input.getName()) ? new HttpDocument(input.getName(), null, null, null, null) : null;
	}

	private static HttpDocument of(IFile file) {
		if (!isHttpFileName(file.getName())) {
			return null;
		}
		IPath location = file.getLocation();
		File local = location != null ? location.toFile() : null;
		IPath projectLocation = file.getProject().getLocation();
		File projectRoot = projectLocation != null ? projectLocation.toFile()
				: local != null ? local.getParentFile() : null;
		return new HttpDocument(file.getName(), local, file, local != null ? local.getParentFile() : null,
				projectRoot);
	}

	private static HttpDocument of(File file) {
		if (!isHttpFileName(file.getName())) {
			return null;
		}
		File dir = file.getAbsoluteFile().getParentFile();
		return new HttpDocument(file.getName(), file, null, dir, dir);
	}

	/** The environments of the file. */
	public Environments environments() {
		return baseDir != null ? Environments.load(baseDir, projectRoot) : Environments.empty();
	}

	/** The variables of a request of this file, before its pre-request scripts, in the selected environment. */
	public Variables variables(HttpFile file) {
		Activator activator = Activator.getDefault();
		ClientSession session = activator.session();
		Environments environments = environments();
		return new Variables(new java.util.HashMap<>(), session.globals(), file.variables(),
				environments.get(activator.environment()).variables(),
				new DynamicVariables(projectRoot != null ? projectRoot.getAbsolutePath() : null,
						activator.historyFolder().getAbsolutePath()));
	}
}

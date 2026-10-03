package org.eclipse.ehttpeditor.app;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Environments > Edit Environments and Edit Private Environments: opens http-client.env.json (or
 * http-client.private.env.json, the data of the class in plugin.xml) of the folder of the active file, the folder
 * where EHttpEditor looks for them. The file is created with examples when it does not exist.
 */
public class EditEnvironmentHandler extends AbstractHandler implements IExecutableExtension {

	private String fileName = "http-client.env.json"; //$NON-NLS-1$

	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data) {
		if (data instanceof String name && !name.isBlank()) {
			fileName = name;
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		File folder = folder(window);
		if (folder == null) {
			throw new ExecutionException("No folder for " + fileName); //$NON-NLS-1$
		}
		File file = new File(folder, fileName);
		if (!file.exists()) {
			try {
				Templates.write(fileName, file);
			} catch (IOException e) {
				throw new ExecutionException("Cannot create " + file, e); //$NON-NLS-1$
			}
		}
		OpenDocuments.open(window, file);
		return null;
	}

	/** The folder of the active editor, or the workspace (where requests.http is) without editor. */
	private static File folder(IWorkbenchWindow window) {
		IEditorPart editor = window.getActivePage() != null ? window.getActivePage().getActiveEditor() : null;
		if (editor != null) {
			IEditorInput input = editor.getEditorInput();
			IFile resource = input.getAdapter(IFile.class);
			IPath location = resource != null ? resource.getLocation() : null;
			if (location != null) {
				return location.toFile().getParentFile();
			}
			if (input instanceof IURIEditorInput uriInput) {
				URI uri = uriInput.getURI();
				if (uri != null && "file".equals(uri.getScheme())) { //$NON-NLS-1$
					return new File(uri).getParentFile();
				}
			}
		}
		Location workspace = Platform.getInstanceLocation();
		return workspace != null && workspace.isSet() ? new File(workspace.getURL().getFile()) : null;
	}
}

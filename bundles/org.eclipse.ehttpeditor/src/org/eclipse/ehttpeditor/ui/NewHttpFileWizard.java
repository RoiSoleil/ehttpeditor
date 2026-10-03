package org.eclipse.ehttpeditor.ui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.ehttpeditor.Activator;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.WizardNewFileCreationPage;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

/** File > New > HTTP Request File: an .http file with a few examples. */
public class NewHttpFileWizard extends BasicNewResourceWizard {

	static final String TEMPLATE = """
			# Requests separated by ###. Ctrl+Enter, or "Send request" above a request, runs it.
			# {{name}} comes from the environment (http-client.env.json), from @name = value, from
			# client.global.set in a script, or is dynamic: {{$uuid}}, {{$timestamp}}, {{$random.integer(1, 100)}}.
			@host = https://httpbin.org

			### Simple GET
			GET {{host}}/get?id={{$uuid}}
			Accept: application/json

			### POST with a response handler
			POST {{host}}/post
			Content-Type: application/json

			{
			  "name": "EHttpEditor",
			  "time": "{{$isoTimestamp}}"
			}

			> {%
			client.test("Request executed successfully", function () {
			  client.assert(response.status === 200, "Response status is not 200");
			});
			client.global.set("lastName", response.body.json.name);
			%}

			### Global variable
			GET {{host}}/anything/{{lastName}}
			""";

	private WizardNewFileCreationPage page;

	@Override
	public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
		super.init(workbench, currentSelection);
		setWindowTitle("New HTTP Request File");
		setNeedsProgressMonitor(true);
	}

	@Override
	public void addPages() {
		page = new WizardNewFileCreationPage("newHttpFile", getSelection()) {
			@Override
			protected InputStream getInitialContents() {
				return new ByteArrayInputStream(TEMPLATE.getBytes(StandardCharsets.UTF_8));
			}
		};
		page.setTitle("HTTP Request File");
		page.setDescription("Create a file of HTTP requests (.http), to run them in the editor.");
		page.setFileExtension("http");
		page.setFileName("requests.http");
		addPage(page);
	}

	@Override
	public boolean performFinish() {
		IFile file = page.createNewFile();
		if (file == null) {
			return false;
		}
		selectAndReveal(file);
		IWorkbenchPage workbenchPage = getWorkbench().getActiveWorkbenchWindow() != null
				? getWorkbench().getActiveWorkbenchWindow().getActivePage()
				: null;
		if (workbenchPage != null) {
			try {
				IDE.openEditor(workbenchPage, file, true);
			} catch (PartInitException e) {
				Activator.log(e);
			}
		}
		return true;
	}
}

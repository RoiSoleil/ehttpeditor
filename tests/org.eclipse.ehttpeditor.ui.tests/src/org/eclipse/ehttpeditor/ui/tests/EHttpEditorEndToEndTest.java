package org.eclipse.ehttpeditor.ui.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ehttpeditor.Activator;
import org.eclipse.ehttpeditor.core.Environments;
import org.eclipse.ehttpeditor.core.Execution;
import org.eclipse.ehttpeditor.ui.EnvironmentSelection;
import org.eclipse.ehttpeditor.ui.HttpResponseView;
import org.eclipse.ehttpeditor.ui.editor.HttpCodeMiningProvider;
import org.eclipse.ehttpeditor.ui.editor.HttpTextHover;
import org.eclipse.ehttpeditor.ui.handlers.RunRequestHandler;
import org.eclipse.ehttpeditor.ui.tests.TestServer.Response;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.junit5.SWTBotJunit5Extension;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.keys.IBindingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * End to end, as a user: an .http file is created with the wizard or opened in the editor, the environment is
 * chosen, the requests are run (context menu, key bindings, code minings) against a local server, and the HTTP
 * Client view shows the responses, the tests and the history. The completion and the hover of the variables are
 * checked in the real editor.
 */
@ExtendWith(SWTBotJunit5Extension.class)
class EHttpEditorEndToEndTest {

	private static final String PROJECT = "ehttp-demo";
	private static final String CONTENT_ASSIST = "org.eclipse.ui.edit.text.contentAssist.proposals";

	private static final String REQUESTS = """
			@name = ada

			### Create
			POST {{base}}/items
			Content-Type: application/json

			{"name": "{{name}}"}

			> {%
			client.test("Created", function () {
			  client.assert(response.status === 201, "status " + response.status);
			  client.assert(response.body.name === "ada");
			});
			client.global.set("itemId", response.body.id);
			%}

			### Read
			GET {{base}}/items/{{itemId}}
			Accept: application/json
			""";

	private final SWTWorkbenchBot bot = new SWTWorkbenchBot();
	private TestServer server;
	private IProject project;

	@BeforeEach
	void setUp() throws Exception {
		try {
			bot.viewByTitle("Welcome").close();
		} catch (RuntimeException e) {
			// no welcome page
		}
		server = new TestServer();
		server.handler(r -> switch (r.method() + " " + r.target()) {
		case "POST /items" -> Response.of(201, "application/json",
				"{\"id\":42,\"name\":" + nameOf(r.bodyText()) + "}");
		case "GET /items/42" -> Response.of(200, "application/json", "{\"id\":42,\"name\":\"ada\"}");
		default -> Response.of(404, "text/plain", "not found: " + r.method() + " " + r.target());
		});
		project = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT);
		if (!project.exists()) {
			project.create(null);
		}
		project.open(null);
		write(Environments.PUBLIC_FILE, "{ \"local\": { \"base\": \"" + server.url() + "\" }, \"prod\": { \"base\": \"https://example.com\" } }");
		UIThreadRunnable.syncExec(() -> EnvironmentSelection.select(null));
		Activator.getDefault().history().clear();
		Activator.getDefault().session().globals().clear();
	}

	@AfterEach
	void tearDown() throws Exception {
		bot.closeAllShells();
		bot.saveAllEditors();
		bot.closeAllEditors();
		if (project != null && project.exists()) {
			project.delete(true, true, new NullProgressMonitor());
		}
		if (server != null) {
			server.close();
		}
	}

	@Test
	void theWizardCreatesAnHttpFileOpenInTheEditor() {
		bot.menu("File").menu("New").menu("Other...").click();
		SWTBotShell wizard = bot.shell("New");
		wizard.activate();
		bot.tree().expandNode("HTTP Client").select("HTTP Request File");
		bot.button("Next >").click();
		bot.textWithLabel("Enter or select the parent folder:").setText(PROJECT);
		bot.textWithLabel("File name:").setText("first.http");
		bot.button("Finish").click();

		SWTBotEditor editor = waitFor("the editor of first.http", () -> editor("first.http"));
		String text = editor.toTextEditor().getText();
		assertTrue(text.contains("### Simple GET"), text);
		assertTrue(project.getFile("first.http").exists());
		// the Generic Editor, given to the .http files by the content type
		assertEquals("org.eclipse.ui.genericeditor.GenericEditor", editor.getReference().getId());
	}

	@Test
	void theContextMenuRunsTheRequestsInTheChosenEnvironment() throws Exception {
		SWTBotEclipseEditor editor = open("api.http", REQUESTS);

		// Select HTTP Client Environment... > local
		editor.contextMenu("Select HTTP Client Environment...").click();
		SWTBotShell dialog = bot.shell("HTTP Client Environment");
		dialog.activate();
		dialog.bot().table().select("local");
		dialog.bot().button("OK").click();
		assertEquals("local", Activator.getDefault().environment());

		// the cursor in the first request, then Run HTTP Request
		editor.navigateTo(3, 2);
		editor.contextMenu("Run HTTP Request").click();
		Execution created = waitForExecutions(1).get(0);
		assertEquals(201, created.response().status(), String.valueOf(created.error()));
		assertEquals("{\"name\": \"ada\"}", server.requests().get(0).bodyText());
		assertEquals("42", Activator.getDefault().session().globals().get("itemId"));

		SWTBotView view = bot.viewById(HttpResponseView.ID);
		SWTBotTable history = view.bot().table();
		waitFor("the response in the view", () -> history.rowCount() == 1 ? Boolean.TRUE : null);
		assertEquals("201", history.cell(0, 0));
		assertTrue(history.cell(0, 1).startsWith("POST " + server.url() + "/items"), history.cell(0, 1));
		// only the text of the selected tab is visible, so found by SWTBot
		String body = view.bot().styledText(0).getText();
		assertTrue(body.contains("\"id\": 42"), body);
		view.bot().cTabItem("Tests (1/1)").activate();
		String tests = view.bot().styledText(0).getText();
		assertTrue(tests.contains("✔ Created"), tests);

		// the second request uses the global variable set by the handler of the first one
		editor.navigateTo(17, 0);
		editor.contextMenu("Run HTTP Request").click();
		Execution read = waitForExecutions(2).get(0);
		assertEquals(200, read.response().status(), String.valueOf(read.error()));
		assertEquals("/items/42", server.lastRequest().target());
		waitFor("the second response in the view", () -> history.rowCount() == 2 ? Boolean.TRUE : null);
		assertEquals("200", history.cell(0, 0));
	}

	@Test
	void theKeyBindingsAreActiveInTheHttpEditors() throws Exception {
		SWTBotEclipseEditor editor = open("keys.http", REQUESTS);
		editor.setFocus();
		IBindingService bindings = PlatformUI.getWorkbench().getService(IBindingService.class);
		String run = waitFor("the key binding of Run HTTP Request",
				() -> UIThreadRunnable.syncExec(() -> bindings.getBestActiveBindingFormattedFor(RunRequestHandler.COMMAND_RUN)));
		assertTrue(run.endsWith("Enter"), run);
		String runAll = UIThreadRunnable
				.syncExec(() -> bindings.getBestActiveBindingFormattedFor(RunRequestHandler.COMMAND_RUN_ALL));
		assertNotNull(runAll);
		assertTrue(runAll.endsWith("Enter") && runAll.contains("Shift"), runAll);

		// Run All HTTP Requests, as with its key binding
		UIThreadRunnable.syncExec(() -> EnvironmentSelection.select("local"));
		execute(RunRequestHandler.COMMAND_RUN_ALL);
		List<Execution> executions = waitForExecutions(2);
		assertEquals(200, executions.get(0).response().status(), String.valueOf(executions.get(0).error()));
		assertEquals(201, executions.get(1).response().status(), String.valueOf(executions.get(1).error()));
	}

	@Test
	void theCodeMiningsAreShownAndRunTheRequests() throws Exception {
		SWTBotEclipseEditor editor = open("minings.http", REQUESTS);
		UIThreadRunnable.syncExec(() -> EnvironmentSelection.select("local"));
		ITextViewer viewer = viewer("minings.http");

		// the editor shows them: the line of each request has room above it for its minings
		waitFor("the code minings above the requests", () -> UIThreadRunnable.syncExec(() -> {
			StyledText widget = viewer.getTextWidget();
			return widget.getLineVerticalIndent(3) > 0 && widget.getLineVerticalIndent(17) > 0 ? Boolean.TRUE : null;
		}));

		List<? extends ICodeMining> minings = new HttpCodeMiningProvider()
				.provideCodeMinings(viewer, new NullProgressMonitor()).get();
		List<String> labels = new ArrayList<>();
		for (ICodeMining mining : minings) {
			labels.add(mining.getLabel());
		}
		assertEquals(List.of("▶ Send request", "▶▶ Run all requests", "Environment: local",
				"▶ Send request"), labels);

		// a click on "Send request" of the first request
		UIThreadRunnable.syncExec(() -> minings.get(0).getAction().accept(null));
		Execution execution = waitForExecutions(1).get(0);
		assertEquals("Create", execution.request().displayName());
		assertEquals(201, execution.response().status(), String.valueOf(execution.error()));
		assertTrue(editor.isActive());
	}

	@Test
	void theCompletionAndTheHoverKnowTheVariables() throws Exception {
		SWTBotEclipseEditor editor = open("assist.http", "GET {{\n");
		UIThreadRunnable.syncExec(() -> EnvironmentSelection.select("local"));
		editor.navigateTo(0, 6);
		UIThreadRunnable.asyncExec(() -> {
			try {
				PlatformUI.getWorkbench().getService(IHandlerService.class).executeCommand(CONTENT_ASSIST, null);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		List<String> proposals = waitFor("the completion proposals", () -> {
			List<String> items = proposals();
			return items.stream().anyMatch(p -> p.startsWith("base")) ? items : null;
		});
		assertTrue(proposals.contains("base - environment variable"), proposals.toString());
		assertTrue(proposals.contains("$uuid"), proposals.toString());
		UIThreadRunnable.syncExec(() -> {
			for (Shell shell : Display.getDefault().getShells()) {
				if (shell.isVisible() && findTable(shell) != null && shell.getParent() != null) {
					shell.close();
				}
			}
		});

		editor.setText("GET {{base}}/items\n");
		ITextViewer viewer = viewer("assist.http");
		String hover = UIThreadRunnable.syncExec(() -> {
			HttpTextHover textHover = new HttpTextHover();
			IRegion region = textHover.getHoverRegion(viewer, 7);
			return region == null ? null : (String) textHover.getHoverInfo2(viewer, region);
		});
		assertEquals("base = " + server.url() + "\nenvironment variable", hover);
	}

	@Test
	void theViewRunsAgainAndClearsTheHistory() throws Exception {
		open("view.http", REQUESTS);
		UIThreadRunnable.syncExec(() -> EnvironmentSelection.select("local"));
		execute(RunRequestHandler.COMMAND_RUN_ALL);
		waitForExecutions(2);

		SWTBotView view = bot.viewById(HttpResponseView.ID);
		view.show();
		SWTBotTable history = view.bot().table();
		waitFor("the two responses", () -> history.rowCount() == 2 ? Boolean.TRUE : null);
		history.select(1);
		history.contextMenu("Run Again").click();
		waitForExecutions(3);
		waitFor("the third response", () -> history.rowCount() == 3 ? Boolean.TRUE : null);
		assertEquals("201", history.cell(0, 0));

		view.toolbarButton("Clear History").click();
		waitFor("an empty history", () -> history.rowCount() == 0 ? Boolean.TRUE : null);
		assertTrue(Activator.getDefault().history().executions().isEmpty());
	}

	@Test
	void thePreferencesAreInTheirPage() {
		bot.menu("Window").menu("Preferences").click();
		SWTBotShell preferences = bot.shell("Preferences");
		preferences.activate();
		preferences.bot().tree().select("HTTP Client");
		assertEquals("30", preferences.bot().textWithLabel("Connection timeout (seconds):").getText());
		assertEquals("60", preferences.bot().textWithLabel("Response timeout (seconds):").getText());
		preferences.bot().button("Cancel").click();
	}

	// ---- the files and the editor --------------------------------------------------------

	private static String nameOf(String json) {
		int start = json.indexOf("\"name\": \"");
		return start < 0 ? "null" : "\"" + json.substring(start + 9, json.indexOf('"', start + 9)) + "\"";
	}

	private IFile write(String name, String content) throws CoreException {
		IFile file = project.getFile(name);
		ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		if (file.exists()) {
			file.setContents(in, IResource.FORCE, null);
		} else {
			file.create(in, IResource.FORCE, null);
		}
		return file;
	}

	private SWTBotEclipseEditor open(String name, String content) throws CoreException {
		IFile file = write(name, content);
		UIThreadRunnable.syncExec(() -> {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IDE.openEditor(page, file, true);
			} catch (CoreException e) {
				throw new IllegalStateException(e);
			}
		});
		SWTBotEditor editor = waitFor("the editor of " + name, () -> editor(name));
		editor.show();
		return editor.toTextEditor();
	}

	private SWTBotEditor editor(String title) {
		for (SWTBotEditor editor : bot.editors()) {
			if (editor.getTitle().equals(title)) {
				return editor;
			}
		}
		return null;
	}

	private ITextViewer viewer(String title) {
		IEditorPart part = UIThreadRunnable.syncExec(() -> editor(title).getReference().getEditor(false));
		ITextViewer viewer = UIThreadRunnable.syncExec(() -> part.getAdapter(ITextViewer.class));
		assertNotNull(viewer, "no text viewer for " + title);
		return viewer;
	}

	private static void execute(String command) {
		UIThreadRunnable.syncExec(() -> {
			try {
				PlatformUI.getWorkbench().getService(IHandlerService.class).executeCommand(command, null);
			} catch (Exception e) {
				throw new IllegalStateException("Cannot run " + command, e);
			}
		});
	}

	/** The executions of the history, the newest first, once there are count of them. */
	private List<Execution> waitForExecutions(int count) {
		return waitFor(count + " executions in the history", () -> {
			List<Execution> executions = Activator.getDefault().history().executions();
			return executions.size() >= count ? executions : null;
		});
	}

	/** The texts of the items of the table of the completion popup. */
	private static List<String> proposals() {
		return UIThreadRunnable.syncExec(() -> {
			List<String> items = new ArrayList<>();
			for (Shell shell : Display.getDefault().getShells()) {
				if (!shell.isVisible() || shell.getParent() == null) {
					continue;
				}
				Table table = findTable(shell);
				if (table != null) {
					for (TableItem item : table.getItems()) {
						items.add(item.getText());
					}
				}
			}
			return items;
		});
	}

	private static Table findTable(Control control) {
		if (control instanceof Table table) {
			return table;
		}
		if (control instanceof Composite composite) {
			for (Control child : composite.getChildren()) {
				Table table = findTable(child);
				if (table != null) {
					return table;
				}
			}
		}
		return null;
	}

	// ---- plumbing ------------------------------------------------------------------------

	/** The open windows and the executions, to understand a failure. */
	private static String state() {
		StringBuilder sb = new StringBuilder("Windows:\n");
		UIThreadRunnable.syncExec(() -> {
			for (Shell shell : Display.getDefault().getShells()) {
				if (shell.isVisible()) {
					sb.append("- ").append(shell.getText()).append('\n');
				}
			}
		});
		sb.append("Executions:\n");
		for (Execution execution : Activator.getDefault().history().executions()) {
			sb.append("- ").append(execution.request().displayName()).append(' ')
					.append(execution.response() != null ? execution.response().status() : "no response").append(' ')
					.append(execution.error() != null ? execution.error() : "").append(' ').append(execution.console())
					.append('\n');
		}
		return sb.toString();
	}

	private <T> T waitFor(String what, Supplier<T> probe) {
		Object[] result = new Object[1];
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				try {
					result[0] = probe.get();
				} catch (RuntimeException e) {
					result[0] = null;
				}
				return result[0] != null;
			}

			@Override
			public String getFailureMessage() {
				return "Timed out waiting for " + what + "\n" + state();
			}
		}, 60_000);
		@SuppressWarnings("unchecked")
		T t = (T) result[0];
		return t;
	}
}

package org.eclipse.ehttpeditor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.ehttpeditor.Activator;
import org.eclipse.ehttpeditor.core.Bodies;
import org.eclipse.ehttpeditor.core.Execution;
import org.eclipse.ehttpeditor.core.HttpResponseData;
import org.eclipse.ehttpeditor.core.TestResult;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

/** The responses of the requests: the history on the left, the selected response on the right. */
public class HttpResponseView extends ViewPart implements ExecutionHistory.Listener {

	public static final String ID = "org.eclipse.ehttpeditor.views.response";

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private TableViewer table;
	private Label summary;
	private CTabFolder tabs;
	private CTabItem bodyTab;
	private CTabItem headersTab;
	private CTabItem requestTab;
	private CTabItem testsTab;
	private CTabItem consoleTab;
	private StyledText body;
	private StyledText headers;
	private StyledText request;
	private StyledText tests;
	private StyledText console;
	private Execution current;
	private boolean unseen;
	private Action rerunAction;
	private Action openAction;
	private Action environmentAction;
	private Action wrapAction;
	private final IPropertyChangeListener preferenceListener = event -> {
		if (Activator.PREF_ENVIRONMENT.equals(event.getProperty())) {
			asyncExec(this::updateEnvironment);
		}
	};
	private final IPartListener2 partListener = new IPartListener2() {
		@Override
		public void partVisible(IWorkbenchPartReference reference) {
			if (reference.getPart(false) == HttpResponseView.this) {
				unseen = false;
				updateTitleImage();
			}
		}
	};

	@Override
	public void createPartControl(Composite parent) {
		SashForm sash = new SashForm(parent, SWT.HORIZONTAL);

		Composite tableComposite = new Composite(sash, SWT.NONE);
		TableColumnLayout tableLayout = new TableColumnLayout();
		tableComposite.setLayout(tableLayout);
		table = new TableViewer(tableComposite, SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
		table.getTable().setHeaderVisible(true);
		table.setContentProvider(ArrayContentProvider.getInstance());
		column(tableLayout, "Status", 12, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				Execution execution = (Execution) element;
				return execution.response() != null ? Integer.toString(execution.response().status()) : "Error";
			}

			@Override
			public Image getImage(Object element) {
				return Activator.getDefault().getImageRegistry()
						.get(((Execution) element).succeeded() ? Activator.IMG_SUCCESS : Activator.IMG_FAILURE);
			}
		});
		column(tableLayout, "Request", 60, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				Execution execution = (Execution) element;
				String url = execution.prepared() != null ? execution.prepared().uri().toString()
						: execution.request().url();
				return execution.request().method() + " " + url;
			}

			@Override
			public String getToolTipText(Object element) {
				Execution execution = (Execution) element;
				return execution.fileName() + " " + execution.request().displayName();
			}
		});
		column(tableLayout, "Time", 15, new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				Execution execution = (Execution) element;
				String time = TIME.format(execution.time());
				return execution.response() != null ? time + " (" + execution.response().millis() + " ms)" : time;
			}
		});
		org.eclipse.jface.viewers.ColumnViewerToolTipSupport.enableFor(table);
		table.addSelectionChangedListener(event -> {
			Object selected = event.getStructuredSelection().getFirstElement();
			show((Execution) selected);
		});
		table.addDoubleClickListener(event -> rerun());

		Composite details = new Composite(sash, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		details.setLayout(layout);
		summary = new Label(details, SWT.NONE);
		summary.setFont(JFaceResources.getBannerFont());
		GridDataFactory.fillDefaults().grab(true, false).indent(4, 2).applyTo(summary);
		tabs = new CTabFolder(details, SWT.TOP | SWT.FLAT);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tabs);
		bodyTab = new CTabItem(tabs, SWT.NONE);
		bodyTab.setText("Response");
		body = text(tabs, bodyTab);
		headersTab = new CTabItem(tabs, SWT.NONE);
		headersTab.setText("Headers");
		headers = text(tabs, headersTab);
		requestTab = new CTabItem(tabs, SWT.NONE);
		requestTab.setText("Request");
		request = text(tabs, requestTab);
		testsTab = new CTabItem(tabs, SWT.NONE);
		testsTab.setText("Tests");
		tests = text(tabs, testsTab);
		consoleTab = new CTabItem(tabs, SWT.NONE);
		consoleTab.setText("Console");
		console = text(tabs, consoleTab);
		tabs.setSelection(bodyTab);
		sash.setWeights(35, 65);

		createActions();
		Activator activator = Activator.getDefault();
		activator.history().addListener(this);
		activator.getPreferenceStore().addPropertyChangeListener(preferenceListener);
		getSite().getPage().addPartListener(partListener);
		getSite().setSelectionProvider(table);
		refresh(null);
		show(null);
	}

	private void column(TableColumnLayout layout, String title, int weight, ColumnLabelProvider provider) {
		TableViewerColumn column = new TableViewerColumn(table, SWT.NONE);
		column.getColumn().setText(title);
		column.setLabelProvider(provider);
		layout.setColumnData(column.getColumn(), new ColumnWeightData(weight, 50));
	}

	private static StyledText text(CTabFolder folder, CTabItem item) {
		StyledText text = new StyledText(folder, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
		text.setFont(JFaceResources.getTextFont());
		text.setMargins(4, 2, 4, 2);
		item.setControl(text);
		return text;
	}

	private void createActions() {
		rerunAction = new Action("Run Again", Activator.getDefault().getImageRegistry().getDescriptor(Activator.IMG_RUN)) {
			@Override
			public void run() {
				rerun();
			}
		};
		rerunAction.setToolTipText("Run the selected request again");
		openAction = new Action("Open Response in Editor") {
			@Override
			public void run() {
				openInEditor();
			}
		};
		openAction.setImageDescriptor(org.eclipse.ui.PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(org.eclipse.ui.ISharedImages.IMG_OBJ_FILE));
		environmentAction = new Action("", Activator.getDefault().getImageRegistry().getDescriptor(Activator.IMG_ENVIRONMENT)) {
			@Override
			public void run() {
				EnvironmentSelection.choose(getSite().getShell(), null);
			}
		};
		Action clearAction = new Action("Clear History") {
			@Override
			public void run() {
				Activator.getDefault().history().clear();
			}
		};
		clearAction.setImageDescriptor(org.eclipse.ui.PlatformUI.getWorkbench().getSharedImages()
				.getImageDescriptor(org.eclipse.ui.ISharedImages.IMG_ELCL_REMOVEALL));
		Action clearSessionAction = new Action("Clear Cookies and Global Variables...") {
			@Override
			public void run() {
				if (MessageDialog.openConfirm(getSite().getShell(), "HTTP Client",
						"Forget the cookies and the global variables and headers set by the scripts?")) {
					Activator.getDefault().session().clearCookies();
					Activator.getDefault().session().globals().clear();
					Activator.getDefault().session().globalHeaders().clear();
				}
			}
		};
		wrapAction = new Action("Wrap Lines", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				for (StyledText text : List.of(body, headers, request, tests, console)) {
					text.setWordWrap(isChecked());
				}
			}
		};
		IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();
		toolbar.add(environmentAction);
		toolbar.add(new Separator());
		toolbar.add(rerunAction);
		toolbar.add(openAction);
		toolbar.add(clearAction);
		IMenuManager menu = getViewSite().getActionBars().getMenuManager();
		menu.add(environmentAction);
		menu.add(wrapAction);
		menu.add(new Separator());
		menu.add(clearSessionAction);
		menu.add(clearAction);
		MenuManager contextMenu = new MenuManager();
		contextMenu.add(rerunAction);
		contextMenu.add(openAction);
		table.getTable().setMenu(contextMenu.createContextMenu(table.getTable()));
		updateEnvironment();
	}

	private void updateEnvironment() {
		if (environmentAction == null) {
			return;
		}
		String label = "Environment: " + EnvironmentSelection.label();
		environmentAction.setText(label);
		environmentAction.setToolTipText(label + " (click to change)");
	}

	private void rerun() {
		Execution execution = selected();
		if (execution != null) {
			Activator.getDefault().history().rerun(execution);
		}
	}

	private Execution selected() {
		IStructuredSelection selection = table.getStructuredSelection();
		return (Execution) selection.getFirstElement();
	}

	private void openInEditor() {
		Execution execution = current;
		if (execution == null || execution.response() == null) {
			return;
		}
		HttpResponseData response = execution.response();
		try {
			File folder = Activator.getDefault().historyFolder();
			Files.createDirectories(folder.toPath());
			String name = execution.request().displayName().replaceAll("[^\\w.-]", "_");
			File file = new File(folder, name + "-" + execution.time().toEpochMilli() + "."
					+ Bodies.extension(response.contentType()));
			byte[] content = response.body();
			if (Bodies.isJson(response.contentType())) {
				content = Bodies.toPrettyText(content, response.contentType())
						.getBytes(Bodies.charset(response.contentType()));
			}
			Files.write(file.toPath(), content);
			IWorkbenchPage page = getSite().getPage();
			IDE.openEditorOnFileStore(page, EFS.getLocalFileSystem().fromLocalFile(file));
		} catch (IOException | PartInitException e) {
			MessageDialog.openError(getSite().getShell(), "HTTP Client", "The response could not be opened: " + e);
		}
	}

	@Override
	public void changed(Execution added) {
		asyncExec(() -> refresh(added));
	}

	@Override
	public void runningChanged(int running) {
		asyncExec(this::updateTitleImage);
	}

	private void refresh(Execution added) {
		table.setInput(Activator.getDefault().history().executions());
		if (added != null) {
			table.setSelection(new StructuredSelection(added), true);
			show(added);
			IWorkbenchPage page = getSite().getPage();
			unseen = !page.isPartVisible(this);
		} else if (current != null && !Activator.getDefault().history().executions().contains(current)) {
			show(null);
		}
		updateTitleImage();
	}

	private void updateTitleImage() {
		String image = Activator.getDefault().history().running() > 0 ? Activator.IMG_EHTTP_RUNNING
				: unseen ? Activator.IMG_EHTTP_DONE : Activator.IMG_EHTTP;
		setTitleImage(Activator.getDefault().getImageRegistry().get(image));
		if (Activator.getDefault().history().running() > 0 && current == null) {
			summary.setText("Running...");
		}
	}

	private void show(Execution execution) {
		current = execution;
		rerunAction.setEnabled(execution != null);
		openAction.setEnabled(execution != null && execution.response() != null);
		if (execution == null) {
			summary.setText(Activator.getDefault().history().running() > 0 ? "Running..."
					: "Run a request of an .http file: ▶ above it, or Ctrl+Enter in it.");
			for (StyledText text : List.of(body, headers, request, tests, console)) {
				text.setText("");
			}
			testsTab.setText("Tests");
			consoleTab.setText("Console");
			summary.getParent().layout();
			return;
		}
		HttpResponseData response = execution.response();
		StringBuilder line = new StringBuilder();
		if (response != null) {
			line.append(response.status());
			String reason = org.eclipse.ehttpeditor.core.HttpStatus.reason(response.status());
			if (!reason.isEmpty()) {
				line.append(' ').append(reason);
			}
			line.append("  ·  ").append(response.millis()).append(" ms  ·  ").append(size(response.body().length));
			String mime = Bodies.mimeType(response.contentType());
			if (!mime.isEmpty()) {
				line.append("  ·  ").append(mime);
			}
		}
		if (execution.error() != null) {
			if (line.length() > 0) {
				line.append("  ·  ");
			}
			line.append(execution.error());
		}
		if (!execution.tests().isEmpty()) {
			long passed = execution.tests().stream().filter(TestResult::passed).count();
			line.append("  ·  ").append(passed).append('/').append(execution.tests().size()).append(" tests passed");
		}
		if (execution.environment() != null) {
			line.append("  ·  ").append(execution.environment());
		}
		summary.setText(line.toString());
		summary.setToolTipText(line.toString());
		summary.getParent().layout();

		if (response != null) {
			body.setText(Bodies.toPrettyText(response.body(), response.contentType()));
			if (Bodies.isJson(response.contentType()) || body.getText().strip().startsWith("{")) {
				JsonHighlighter.highlight(body);
			}
			StringBuilder headerText = new StringBuilder();
			for (String redirect : response.redirects()) {
				headerText.append("Redirected: ").append(redirect).append('\n');
			}
			if (!response.redirects().isEmpty()) {
				headerText.append('\n');
			}
			headerText.append(response.headersText());
			headers.setText(headerText.toString());
		} else {
			body.setText(execution.error() != null ? execution.error() : "");
			headers.setText("");
		}
		request.setText(execution.prepared() != null ? execution.prepared().toText() : execution.request().method()
				+ " " + execution.request().url());
		tests.setText(execution.tests().stream()
				.map(t -> (t.passed() ? "✔ " : "✘ ") + t.name() + (t.message() != null && !t.message().equals(t.name()) ? ": " + t.message() : ""))
				.collect(Collectors.joining("\n")));
		testsTab.setText(execution.tests().isEmpty() ? "Tests"
				: "Tests (" + execution.tests().stream().filter(TestResult::passed).count() + "/"
						+ execution.tests().size() + ")");
		StringBuilder consoleText = new StringBuilder();
		for (String message : execution.console()) {
			consoleText.append(message).append('\n');
		}
		if (execution.error() != null) {
			consoleText.append(execution.error()).append('\n');
		}
		if (execution.savedTo() != null) {
			consoleText.append("Saved to ").append(execution.savedTo().getPath()).append('\n');
		}
		console.setText(consoleText.toString());
		consoleTab.setText(execution.console().isEmpty() ? "Console" : "Console (" + execution.console().size() + ")");
		if (execution.failedTests() > 0 && tabs.getSelection() == bodyTab) {
			tabs.setSelection(testsTab);
		} else if (response == null && tabs.getSelection() != consoleTab) {
			tabs.setSelection(consoleTab);
		}
	}

	private static String size(int bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
		}
		return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
	}

	private void asyncExec(Runnable runnable) {
		if (table == null || table.getControl().isDisposed()) {
			return;
		}
		table.getControl().getDisplay().asyncExec(() -> {
			if (!table.getControl().isDisposed()) {
				runnable.run();
			}
		});
	}

	@Override
	public void setFocus() {
		table.getControl().setFocus();
	}

	@Override
	public void dispose() {
		Activator activator = Activator.getDefault();
		if (activator != null) {
			activator.history().removeListener(this);
			activator.getPreferenceStore().removePropertyChangeListener(preferenceListener);
		}
		getSite().getPage().removePartListener(partListener);
		super.dispose();
	}
}

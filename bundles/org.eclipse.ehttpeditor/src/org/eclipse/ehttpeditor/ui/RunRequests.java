package org.eclipse.ehttpeditor.ui;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ehttpeditor.Activator;
import org.eclipse.ehttpeditor.core.Execution;
import org.eclipse.ehttpeditor.core.HttpFile;
import org.eclipse.ehttpeditor.core.HttpRequestSpec;
import org.eclipse.ehttpeditor.core.RequestRunner;
import org.eclipse.ehttpeditor.core.RequestRunner.RunContext;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/** Runs requests of an HTTP file in a job, and shows their responses in the view. */
public final class RunRequests {

	/** The family of the jobs, to wait for them or cancel them. */
	public static final Object FAMILY = RunRequests.class;

	private RunRequests() {
	}

	/** Runs the requests, one after the other; to be called in the UI thread. */
	public static void run(HttpDocument document, HttpFile file, List<HttpRequestSpec> requests) {
		if (requests.isEmpty()) {
			return;
		}
		showView(false);
		Activator activator = Activator.getDefault();
		String environment = activator.environment();
		String title = requests.size() == 1 ? "HTTP request " + requests.get(0).displayName()
				: requests.size() + " HTTP requests of " + document.name();
		Job job = new Job(title) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				SubMonitor progress = SubMonitor.convert(monitor, title, requests.size());
				activator.history().running(true);
				try {
					RunContext context = new RunContext(document.name(), document.baseDir(), document.projectRoot(),
							activator.historyFolder(), environment, activator.session(), activator.settings(),
							activator.executor(), monitor::isCanceled);
					for (HttpRequestSpec request : requests) {
						if (progress.isCanceled()) {
							return Status.CANCEL_STATUS;
						}
						progress.subTask(request.method() + " " + request.url());
						Execution execution;
						try {
							execution = RequestRunner.run(file, request, context);
						} catch (java.util.concurrent.CancellationException e) {
							return Status.CANCEL_STATUS;
						}
						activator.history().add(execution,
								() -> RunRequests.run(document, file, List.of(request)));
						progress.worked(1);
					}
					return Status.OK_STATUS;
				} finally {
					activator.history().running(false);
				}
			}

			@Override
			public boolean belongsTo(Object family) {
				return family == FAMILY;
			}
		};
		job.setUser(false);
		job.schedule();
	}

	/** Shows the view of the responses; activate gives it the focus. */
	public static void showView(boolean activate) {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null
				? PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				: null;
		if (page == null) {
			return;
		}
		try {
			page.showView(HttpResponseView.ID, null,
					activate ? IWorkbenchPage.VIEW_ACTIVATE : IWorkbenchPage.VIEW_VISIBLE);
		} catch (PartInitException e) {
			Activator.log(e);
		}
	}
}

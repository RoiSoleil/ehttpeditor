package org.eclipse.ehttpeditor;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ehttpeditor.core.ClientSession;
import org.eclipse.ehttpeditor.core.ClientSettings;
import org.eclipse.ehttpeditor.core.HttpExecutor;
import org.eclipse.ehttpeditor.ui.ExecutionHistory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/** The plug-in: the session of the HTTP client (globals, cookies), the history and the preferences. */
public class Activator extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "org.eclipse.ehttpeditor";

	/** The selected environment ("" for none). */
	public static final String PREF_ENVIRONMENT = "environment";
	/** Timeout of the connection, in seconds. */
	public static final String PREF_CONNECT_TIMEOUT = "connectTimeout";
	/** Timeout of the response, in seconds. */
	public static final String PREF_READ_TIMEOUT = "readTimeout";
	/** User-Agent sent when the request has none ("" for none). */
	public static final String PREF_USER_AGENT = "userAgent";
	/** Number of executions kept in the view. */
	public static final String PREF_HISTORY_SIZE = "historySize";
	/** Whether the cookies are kept between the sessions. */
	public static final String PREF_KEEP_COOKIES = "keepCookies";

	public static final String IMG_EHTTP = "ehttpeditor";
	public static final String IMG_EHTTP_RUNNING = "ehttpeditor-running";
	public static final String IMG_EHTTP_DONE = "ehttpeditor-done";
	public static final String IMG_RUN = "run";
	public static final String IMG_SUCCESS = "success";
	public static final String IMG_FAILURE = "failure";
	public static final String IMG_ENVIRONMENT = "environment";

	private static Activator plugin;

	private final ClientSession session = new ClientSession();
	private final ExecutionHistory history = new ExecutionHistory();
	private HttpExecutor executor;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		executor = new HttpExecutor();
		history.setLimit(getPreferenceStore().getInt(PREF_HISTORY_SIZE));
		if (getPreferenceStore().getBoolean(PREF_KEEP_COOKIES)) {
			try {
				session.loadCookies(cookiesFile());
			} catch (IOException e) {
				log(e);
			}
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		try {
			if (getPreferenceStore().getBoolean(PREF_KEEP_COOKIES)) {
				session.saveCookies(cookiesFile());
			}
		} catch (IOException e) {
			log(e);
		}
		if (executor != null) {
			executor.close();
		}
		plugin = null;
		super.stop(context);
	}

	private File cookiesFile() {
		return getStateLocation().append("cookies.jsonl").toFile();
	}

	public static Activator getDefault() {
		return plugin;
	}

	/** The global variables, headers and cookies. */
	public ClientSession session() {
		return session;
	}

	/** The executions shown in the view. */
	public ExecutionHistory history() {
		return history;
	}

	/** Sends the requests. */
	public HttpExecutor executor() {
		return executor;
	}

	/** The folder of {@code $historyFolder} and of the responses opened in an editor. */
	public File historyFolder() {
		return getStateLocation().append("history").toFile();
	}

	/** The selected environment, or null. */
	public String environment() {
		String environment = getPreferenceStore().getString(PREF_ENVIRONMENT);
		return environment.isEmpty() ? null : environment;
	}

	/** Selects an environment (null for none). */
	public void setEnvironment(String environment) {
		getPreferenceStore().setValue(PREF_ENVIRONMENT, environment == null ? "" : environment);
	}

	/** The settings of the client, from the preferences. */
	public ClientSettings settings() {
		IPreferenceStore store = getPreferenceStore();
		String userAgent = store.getString(PREF_USER_AGENT);
		return new ClientSettings(Duration.ofSeconds(Math.max(1, store.getInt(PREF_CONNECT_TIMEOUT))),
				Duration.ofSeconds(Math.max(1, store.getInt(PREF_READ_TIMEOUT))),
				userAgent.isBlank() ? null : userAgent);
	}

	@Override
	protected void initializeImageRegistry(ImageRegistry registry) {
		for (String name : new String[] { IMG_EHTTP, IMG_EHTTP_RUNNING, IMG_EHTTP_DONE, IMG_RUN, IMG_SUCCESS,
				IMG_FAILURE, IMG_ENVIRONMENT }) {
			// The @2x variants are found by the platform next to the images.
			registry.put(name, ImageDescriptor.createFromURL(getBundle().getEntry("icons/" + name + ".png")));
		}
	}

	public static void log(Throwable e) {
		ILog.of(Activator.class).log(Status.error(e.getMessage() != null ? e.getMessage() : e.toString(), e));
	}

	public static void log(IStatus status) {
		ILog.of(Activator.class).log(status);
	}
}

package org.eclipse.ehttpeditor.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.ehttpeditor.core.Execution;

/** The executions of the session, the newest first, and the requests running. */
public final class ExecutionHistory {

	/** Told of the changes, in any thread. */
	public interface Listener {
		/** An execution was added, or the history was cleared (execution null). */
		void changed(Execution added);

		/** The number of requests running changed. */
		default void runningChanged(int running) {
		}
	}

	private final List<Execution> executions = new ArrayList<>();
	/** How to run again the request of an execution (executions are records: by identity). */
	private final Map<Execution, Runnable> reruns = new IdentityHashMap<>();
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private int limit = 100;
	private int running;

	public void setLimit(int limit) {
		synchronized (executions) {
			this.limit = Math.max(1, limit);
			trim();
		}
	}

	/** Adds an execution, unless its request asked for {@code @no-log}: it is shown but not kept. */
	public void add(Execution execution, Runnable rerun) {
		synchronized (executions) {
			executions.removeIf(e -> e.prepared() != null && !e.prepared().log());
			executions.add(0, execution);
			reruns.put(execution, rerun);
			trim();
		}
		for (Listener listener : listeners) {
			listener.changed(execution);
		}
	}

	/** Runs again the request of an execution: same file, same request, current environment. */
	public void rerun(Execution execution) {
		Runnable rerun;
		synchronized (executions) {
			rerun = reruns.get(execution);
		}
		if (rerun != null) {
			rerun.run();
		}
	}

	private void trim() {
		while (executions.size() > limit) {
			executions.remove(executions.size() - 1);
		}
		reruns.keySet().removeIf(e -> executions.stream().noneMatch(x -> x == e));
	}

	public List<Execution> executions() {
		synchronized (executions) {
			return new ArrayList<>(executions);
		}
	}

	public void clear() {
		synchronized (executions) {
			executions.clear();
			reruns.clear();
		}
		for (Listener listener : listeners) {
			listener.changed(null);
		}
	}

	/** A run of requests starts (true) or ends (false). */
	public void running(boolean started) {
		int count;
		synchronized (executions) {
			running += started ? 1 : -1;
			count = running;
		}
		for (Listener listener : listeners) {
			listener.runningChanged(count);
		}
	}

	public int running() {
		synchronized (executions) {
			return running;
		}
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}
}

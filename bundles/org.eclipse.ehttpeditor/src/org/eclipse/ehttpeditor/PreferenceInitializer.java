package org.eclipse.ehttpeditor;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

/** The default values of the preferences. */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(Activator.PREF_ENVIRONMENT, "");
		store.setDefault(Activator.PREF_CONNECT_TIMEOUT, 30);
		store.setDefault(Activator.PREF_READ_TIMEOUT, 60);
		store.setDefault(Activator.PREF_USER_AGENT, "EHttpEditor (Java/" + Runtime.version().feature() + ")");
		store.setDefault(Activator.PREF_HISTORY_SIZE, 100);
		store.setDefault(Activator.PREF_KEEP_COOKIES, true);
	}
}

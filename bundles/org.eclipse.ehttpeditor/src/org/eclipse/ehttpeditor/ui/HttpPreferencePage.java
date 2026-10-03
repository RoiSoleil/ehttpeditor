package org.eclipse.ehttpeditor.ui;

import org.eclipse.ehttpeditor.Activator;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/** Window > Preferences > HTTP Client. */
public class HttpPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public HttpPreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Settings of the requests of the .http files. A request overrides the timeouts with "
				+ "# @timeout and # @connection-timeout.");
	}

	@Override
	public void init(IWorkbench workbench) {
	}

	@Override
	protected void createFieldEditors() {
		IntegerFieldEditor connect = new IntegerFieldEditor(Activator.PREF_CONNECT_TIMEOUT,
				"Connection timeout (seconds):", getFieldEditorParent());
		connect.setValidRange(1, 3600);
		addField(connect);
		IntegerFieldEditor read = new IntegerFieldEditor(Activator.PREF_READ_TIMEOUT, "Response timeout (seconds):",
				getFieldEditorParent());
		read.setValidRange(1, 86400);
		addField(read);
		addField(new StringFieldEditor(Activator.PREF_USER_AGENT, "User-Agent (empty for none):",
				getFieldEditorParent()));
		IntegerFieldEditor history = new IntegerFieldEditor(Activator.PREF_HISTORY_SIZE,
				"Responses kept in the view:", getFieldEditorParent());
		history.setValidRange(1, 10000);
		addField(history);
		addField(new BooleanFieldEditor(Activator.PREF_KEEP_COOKIES, "Keep the cookies between the sessions",
				getFieldEditorParent()));
	}

	@Override
	public boolean performOk() {
		boolean ok = super.performOk();
		Activator.getDefault().history().setLimit(getPreferenceStore().getInt(Activator.PREF_HISTORY_SIZE));
		return ok;
	}
}

package org.eclipse.ehttpeditor.app;

import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;

/**
 * The menus File, Edit, Window and Help. File > New HTTP File..., File > Open File... and Help > Documentation
 * are the commands of plugin.xml.
 */
class HttpActionBarAdvisor extends ActionBarAdvisor {

	HttpActionBarAdvisor(IActionBarConfigurer configurer) {
		super(configurer);
	}

	@Override
	protected void makeActions(IWorkbenchWindow window) {
		for (ActionFactory factory : new ActionFactory[] { ActionFactory.CLOSE, ActionFactory.CLOSE_ALL,
				ActionFactory.SAVE, ActionFactory.SAVE_AS, ActionFactory.SAVE_ALL, ActionFactory.QUIT,
				ActionFactory.UNDO, ActionFactory.REDO, ActionFactory.CUT, ActionFactory.COPY, ActionFactory.PASTE,
				ActionFactory.DELETE, ActionFactory.SELECT_ALL, ActionFactory.FIND, ActionFactory.PREFERENCES,
				ActionFactory.ABOUT }) {
			register(factory.create(window));
		}
	}

	@Override
	protected void fillMenuBar(IMenuManager menuBar) {
		MenuManager file = new MenuManager("&File", IWorkbenchActionConstants.M_FILE); //$NON-NLS-1$
		file.add(new GroupMarker("open")); //$NON-NLS-1$
		file.add(new Separator());
		add(file, ActionFactory.CLOSE, ActionFactory.CLOSE_ALL);
		file.add(new Separator());
		add(file, ActionFactory.SAVE, ActionFactory.SAVE_AS, ActionFactory.SAVE_ALL);
		// No group "additions": the editors would add their Open File... and Convert Line Delimiters To
		file.add(new Separator());
		add(file, ActionFactory.QUIT);
		menuBar.add(file);

		MenuManager edit = new MenuManager("&Edit", IWorkbenchActionConstants.M_EDIT); //$NON-NLS-1$
		add(edit, ActionFactory.UNDO, ActionFactory.REDO);
		edit.add(new Separator());
		add(edit, ActionFactory.CUT, ActionFactory.COPY, ActionFactory.PASTE, ActionFactory.DELETE);
		edit.add(new Separator());
		add(edit, ActionFactory.SELECT_ALL);
		edit.add(new Separator());
		add(edit, ActionFactory.FIND);
		edit.add(new GroupMarker(IWorkbenchActionConstants.FIND_EXT));
		edit.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		menuBar.add(edit);

		MenuManager window = new MenuManager("&Window", IWorkbenchActionConstants.M_WINDOW); //$NON-NLS-1$
		add(window, ActionFactory.PREFERENCES);
		menuBar.add(window);

		MenuManager help = new MenuManager("&Help", IWorkbenchActionConstants.M_HELP); //$NON-NLS-1$
		help.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		help.add(new Separator());
		add(help, ActionFactory.ABOUT);
		menuBar.add(help);
	}

	private void add(IMenuManager menu, ActionFactory... factories) {
		for (ActionFactory factory : factories) {
			menu.add(getAction(factory.getId()));
		}
	}
}

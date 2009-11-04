/*
 This file is part of BORG.

 BORG is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 BORG is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with BORG; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 Copyright 2003 by Mike Berger
 */
package net.sf.borg.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;

import net.sf.borg.common.PrefName;
import net.sf.borg.common.Prefs;
import net.sf.borg.common.Resource;
import net.sf.borg.control.Borg;
import net.sf.borg.ui.address.AddrListView;
import net.sf.borg.ui.calendar.DayPanel;
import net.sf.borg.ui.calendar.MonthPanel;
import net.sf.borg.ui.calendar.SearchView;
import net.sf.borg.ui.calendar.TodoView;
import net.sf.borg.ui.calendar.WeekPanel;
import net.sf.borg.ui.calendar.YearPanel;
import net.sf.borg.ui.memo.MemoPanel;
import net.sf.borg.ui.task.TaskModule;
import net.sf.borg.ui.util.JTabbedPaneWithCloseIcons;

/**
 * This is the main Borg UI class. It provides the the main borg tabbed window.
 */
public class MultiView extends View {

	/**
	 * interface implemented by all UI Modules
	 * 
	 */
	public static interface Module {

		/**
		 * get the module's name
		 * 
		 * @return the name
		 */
		public String getModuleName();

		/**
		 * get the Component for this Module
		 * 
		 * @return the Component or null if none to show
		 */
		public Component getComponent();

		/**
		 * print the Module
		 */
		public void print();

		/**
		 * called by the parent Multiview to allow the Module to initialize its
		 * toolbar items, menu items, and anything else that must be initalized
		 * before its Module methods can be called
		 * 
		 * @param parent
		 *            the parent MultiView
		 */
		public void initialize(MultiView parent);
	}

	/** argument values for setView() */
	public enum ViewType {
		DAY, MONTH, WEEK, YEAR, TASK, MEMO, SEARCH;
	}

	/** The main view singleton */
	static private MultiView mainView = null;

	/**
	 * Get the main view singleton. Make it visible if it is not showing.
	 * 
	 * @return the main view singleton
	 */
	public static MultiView getMainView() {
		if (mainView == null)
			mainView = new MultiView();
		else if (!mainView.isShowing())
			mainView.setVisible(true);
		return (mainView);
	}

	/**
	 * toolbar
	 */
	private JToolBar bar = new JToolBar();
	
	/**
	 * the main menu
	 */
	MainMenu mainMenu = new MainMenu();

	/**
	 * Set of all modules ordered by the Module ordering number
	 */
	private List<Module> moduleSet = new ArrayList<Module>();

	/** The tabs */
	private JTabbedPaneWithCloseIcons tabs_ = null;

	/**
	 * Instantiates a new multi view.
	 */
	private MultiView() {
		super();

		// escape key closes the window
		getLayeredPane().registerKeyboardAction(new ActionListener() {
			public final void actionPerformed(ActionEvent e) {
				if (Borg.getReference().hasTrayIcon())
					closeMainwindow();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		// delete key removes tabs
		getLayeredPane().registerKeyboardAction(new ActionListener() {
			public final void actionPerformed(ActionEvent e) {
				Component c = getTabs().getSelectedComponent();
				getTabs().remove(c);
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		// window close button closes the window
		addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(java.awt.event.WindowEvent evt) {
				closeMainwindow();
			}
		});

		// create the menu bar
		JMenuBar menubar = mainMenu.getMenuBar();
		menubar.setBorder(new BevelBorder(BevelBorder.RAISED));
		setJMenuBar(menubar);
		getContentPane().setLayout(new GridBagLayout());

		loadModules();

		// add the tool bar
		GridBagConstraints cons = new java.awt.GridBagConstraints();
		cons.gridx = 0;
		cons.gridy = 0;
		cons.fill = java.awt.GridBagConstraints.HORIZONTAL;
		cons.weightx = 0.0;
		cons.weighty = 0.0;
		getContentPane().add(getToolBar(), cons);

		// add the tabs
		cons = new java.awt.GridBagConstraints();
		cons.gridx = 0;
		cons.gridy = 1;
		cons.fill = java.awt.GridBagConstraints.BOTH;
		cons.weightx = 1.0;
		cons.weighty = 1.0;
		getContentPane().add(getTabs(), cons);

		setTitle("BORG");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setVisible(true);
		setView(ViewType.MONTH); // start month view
		manageMySize(PrefName.DAYVIEWSIZE);
	}

	/**
	 * add a toolbar button
	 * 
	 * @param icon
	 *            the toolbar icon
	 * @param tooltip
	 *            the tooltip for the button
	 * @param action
	 *            the action listener for the button
	 */
	public void addToolBarItem(Icon icon, String tooltip, ActionListener action) {
		JButton button = new JButton(icon);
		button.setToolTipText(tooltip);
		button.addActionListener(action);
		bar.add(button);
		
		mainMenu.addAction(icon, tooltip, action);
	}
	
	/**
	 * add a help menu item
	 * 
	 * @param icon
	 *            the menu item icon
	 * @param tooltip
	 *            the text for the menu item
	 * @param action
	 *            the action listener for the menu item
	 */
	public void addHelpMenuItem(Icon icon, String tooltip, ActionListener action) {
		mainMenu.addHelpMenuItem(icon, tooltip, action);
	}
	/**
	 * Adds a view as a docked tab or separate window, depending on the user
	 * options.
	 * 
	 * @param dp
	 *            the DockableView
	 */
	public void addView(DockableView dp) {
		String dock = Prefs.getPref(PrefName.DOCKPANELS);
		if (dock.equals("true")) {
			dock(dp);
		} else
			dp.openInFrame();
	}

	/**
	 * close the main view. If the system tray icon is active, the program stays
	 * running. If no system tray icon is active, the program shuts down
	 * entirely
	 */
	private void closeMainwindow() {
		if (!Borg.getReference().hasTrayIcon() && this == mainView) {
			Borg.shutdown();
		} else {
			this.dispose();
		}
	}

	/**
	 * Close the currently selected tab.
	 */
	public void closeSelectedTab() {
		tabs_.closeSelectedTab();
	}

	/**
	 * Close all tabs.
	 */
	public void closeTabs() {
		tabs_.closeClosableTabs();
	}

	/** destroy this window */
	public void destroy() {
		this.dispose();
		mainView = null;
	}

	/**
	 * Dock a view as a tab
	 * 
	 * @param dp
	 *            the DockableView
	 */
	public void dock(DockableView dp) {
		tabs_.addTab(dp.getFrameTitle(), dp);
		tabs_.setSelectedIndex(tabs_.getTabCount() - 1);
		dp.remove();

	}

	/**
	 * get the Module for a given ViewType
	 * 
	 * @param type
	 *            the view type
	 * @return the Module or null
	 */
	private Module getModuleForView(ViewType type) {
		for (Module m : moduleSet) {
			if ((type == ViewType.MONTH && m instanceof MonthPanel)
					|| (type == ViewType.WEEK && m instanceof WeekPanel)
					|| (type == ViewType.DAY && m instanceof DayPanel)
					|| (type == ViewType.YEAR && m instanceof YearPanel)
					|| (type == ViewType.TASK && m instanceof TaskModule)
					|| (type == ViewType.MEMO && m instanceof MemoPanel)
					|| (type == ViewType.SEARCH && m instanceof SearchView)) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Gets the tabs.
	 * 
	 * @return the tabs
	 */
	private JTabbedPane getTabs() {
		if (tabs_ == null) {
			tabs_ = new JTabbedPaneWithCloseIcons();
		}
		return tabs_;
	}

	/**
	 * Gets the tool bar.
	 * 
	 * @return the tool bar
	 */
	private JToolBar getToolBar() {
		bar.setFloatable(false);

		JButton printbut = new JButton(new ImageIcon(getClass().getResource(
				"/resource/Print16.gif")));
		printbut.setToolTipText(Resource.getResourceString("Print"));
		printbut.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				print();
			}
		});
		bar.add(printbut);

		bar.addSeparator();

		JButton clearbut = new JButton(new ImageIcon(getClass().getResource(
				"/resource/Delete16.gif")));
		clearbut.setToolTipText(Resource.getResourceString("close_tabs"));
		clearbut.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				closeTabs();
			}
		});
		bar.add(clearbut);

		return bar;
	}

	/**
	 * have the day week and month panels all show a particular day
	 * 
	 * @param cal
	 *            the day to show
	 */
	public void goTo(Calendar cal) {
		for (Module m : moduleSet) {
			// TODO - get rid of this
			if (m instanceof MonthPanel) {
				((MonthPanel) m).goTo(cal);
			} else if (m instanceof DayPanel) {
				((DayPanel) m).goTo(cal);
			} else if (m instanceof YearPanel) {
				((YearPanel) m).goTo(cal);
			} else if (m instanceof WeekPanel) {
				((WeekPanel) m).goTo(cal);
			}
		}
	}

	/**
	 * load all modules
	 */
	public void loadModules() {
		Calendar cal_ = new GregorianCalendar();
		moduleSet.add(new MonthPanel(cal_.get(Calendar.MONTH), cal_
				.get(Calendar.YEAR)));
		moduleSet.add(new WeekPanel(cal_.get(Calendar.MONTH), cal_
				.get(Calendar.YEAR), cal_.get(Calendar.DATE)));
		moduleSet.add(new DayPanel(cal_.get(Calendar.MONTH), cal_
				.get(Calendar.YEAR), cal_.get(Calendar.DATE)));
		moduleSet.add(new YearPanel(cal_.get(Calendar.YEAR)));
		moduleSet.add(AddrListView.getReference());
		moduleSet.add(TodoView.getReference());
		moduleSet.add(new TaskModule());
		moduleSet.add(new MemoPanel());
		moduleSet.add(new SearchView());
		moduleSet.add(new InfoView("/resource/RELEASE_NOTES.txt", Resource
				.getResourceString("rlsnotes")));
		moduleSet.add(new InfoView("/resource/CHANGES.txt", Resource
				.getResourceString("viewchglog")));
		moduleSet.add(new InfoView("/resource/license.htm", Resource
				.getResourceString("License")));

		for (Module m : moduleSet) {
			m.initialize(this);
		}
	}

	/**
	 * prints the currently selected tab if pritning is supported for that tab
	 */
	public void print() {

		Component c = getTabs().getSelectedComponent();
		for (Module m : moduleSet) {
			if (m.getComponent() == c)
			{
				m.print();
				return;
			}
		}
		
		if( c instanceof InfoView )
		{
			((InfoView)c).print();
		}

	}

	/**
	 * refresh the view based on model changes. currently does nothing for this
	 * view.
	 */
	public void refresh() {
		// nothing to refresh for this view
	}

	/**
	 * set the view to show the given module
	 * 
	 * @param module
	 *            the module
	 */
	public void setView(Module module) {
		Component component = module.getComponent();
		if (component != null) {
			if (!component.isDisplayable()) {
				if (component instanceof DockableView) {
					String dock = Prefs.getPref(PrefName.DOCKPANELS);
					DockableView dp = (DockableView) component;
					if (dock.equals("true")) {
						dock(dp);
					} else {
						dp.openInFrame();
						return;
					}
				} else {
					tabs_.addTab(module.getModuleName(), component);
				}
			}
			getTabs().setSelectedComponent(component);
		}
	}

	/**
	 * Sets the currently selected tab to be a particular view as defined in
	 * ViewType.
	 * 
	 * @param type
	 *            the new view
	 */
	public void setView(ViewType type) {

		Module m = getModuleForView(type);
		if (m != null) {
			setView(m);
		}
	}

}

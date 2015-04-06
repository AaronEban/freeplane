/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Dimitry Polivaev
 *
 *  This file author is Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.view.swing.ui;

import java.awt.Component;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DropTargetListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelListener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.ActionAcceleratorManager;
import org.freeplane.core.ui.IMouseListener;
import org.freeplane.core.ui.IMouseWheelEventHandler;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.ui.components.FreeplaneMenuBar;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.menubuilders.FreeplaneResourceAccessor;
import org.freeplane.core.ui.menubuilders.XmlEntryStructureBuilder;
import org.freeplane.core.ui.menubuilders.action.EntriesForAction;
import org.freeplane.core.ui.menubuilders.generic.BuilderDestroyerPair;
import org.freeplane.core.ui.menubuilders.generic.Entry;
import org.freeplane.core.ui.menubuilders.generic.EntryAccessor;
import org.freeplane.core.ui.menubuilders.generic.EntryVisitor;
import org.freeplane.core.ui.menubuilders.generic.PhaseProcessor;
import org.freeplane.core.ui.menubuilders.generic.PhaseProcessor.Phase;
import org.freeplane.core.ui.menubuilders.generic.SubtreeProcessor;
import org.freeplane.core.ui.menubuilders.menu.MenuAcceleratorChangeListener;
import org.freeplane.core.ui.menubuilders.menu.MenuBuildProcessFactory;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.view.swing.map.MapView;

public class UserInputListenerFactory implements IUserInputListenerFactory {
	public static final String NODE_POPUP = "/node_popup";
// // 	final private Controller controller;
	private IMouseListener mapMouseListener;
	private MouseWheelListener mapMouseWheelListener;
	private JPopupMenu mapsPopupMenu;
	private FreeplaneMenuBar menuBar;
	private final Map<Class<? extends Object>, Object> menuBuilderList = new HashMap<Class<? extends Object>, Object>();
	final private HashSet<IMouseWheelEventHandler> mRegisteredMouseWheelEventHandler = new HashSet<IMouseWheelEventHandler>();
	private DragGestureListener nodeDragListener;
	private DropTargetListener nodeDropTargetListener;
	private KeyListener nodeKeyListener;
	private IMouseListener nodeMotionListener;
	private IMouseListener nodeMouseMotionListener;
	private JPopupMenu nodePopupMenu;
	private final Map<String, JComponent> toolBars;
	private final List<JComponent>[] toolbarLists;
	private ActionAcceleratorManager acceleratorManager;
	private final boolean useRibbonMenu;
	final private List<Map<String, BuilderDestroyerPair>> customBuilders;
	private Entry genericMenuStructure;
	private SubtreeProcessor subtreeBuilder;

	public UserInputListenerFactory(final ModeController modeController, boolean useRibbons) {
		customBuilders = new ArrayList<>(Phase.values().length);
		for (@SuppressWarnings("unused")
		Phase phase : Phase.values()) {
			customBuilders.add(new HashMap<String, BuilderDestroyerPair>());
		}
		useRibbonMenu = useRibbons;
		Controller controller = Controller.getCurrentController();
		menuBuilderList.put(MenuBuilder.class, new MenuBuilder(modeController, getAcceleratorManager()));
		menuBuilderList.put(RibbonBuilder.class, new RibbonBuilder(modeController, getAcceleratorManager()));
		controller.getMapViewManager().addMapSelectionListener(new IMapSelectionListener() {
			public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
				if(modeController.equals(Controller.getCurrentModeController()))
					getMenuBuilder(MenuBuilder.class).afterMapChange(newMap);
			}

			public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
			}
		});

		addUiBuilder(Phase.ACTIONS, "navigate_maps", new BuilderDestroyerPair(new EntryVisitor() {
			@Override
			public void visit(Entry target) {
				createMapActions(target);
			}

			@Override
			public boolean shouldSkipChildren(Entry entry) {
				return true;
			}
		}, EntryVisitor.CHILD_ENTRY_REMOVER));

		addUiBuilder(Phase.ACTIONS, "navigate_modes", new BuilderDestroyerPair(new EntryVisitor() {
			@Override
			public void visit(Entry target) {
				createModeActions(target);
			}

			@Override
			public boolean shouldSkipChildren(Entry entry) {
				return true;
			}
		}, EntryVisitor.CHILD_ENTRY_REMOVER));

		toolBars = new LinkedHashMap<String, JComponent>();
		toolbarLists = newListArray();
		for (int j = 0; j < 4; j++) {
			toolbarLists[j] = new LinkedList<JComponent>();
		}
	}

	public <T> T getMenu(Class<T> clazz) {
		return null;
	}

	@SuppressWarnings("unchecked")
	public <T> T getMenuBuilder(Class<T> clazz) {
		return (T) menuBuilderList.get(clazz);
	}

	public ActionAcceleratorManager getAcceleratorManager() {
		if(acceleratorManager == null) {
			acceleratorManager = new ActionAcceleratorManager();
		}
		return acceleratorManager;
	}

	// isolate unchecked stuff in this method
	@SuppressWarnings("unchecked")
	private List<JComponent>[] newListArray() {
		return new List[4];
	}

	public void addToolBar(final String name, final int position, final JComponent toolBar) {
		toolBars.put(name, toolBar);
		toolbarLists[position].add(toolBar);
	}

	public void addMouseWheelEventHandler(final IMouseWheelEventHandler handler) {
		mRegisteredMouseWheelEventHandler.add(handler);
	}

	public IMouseListener getMapMouseListener() {
		if (mapMouseListener == null) {
			mapMouseListener = new DefaultMapMouseListener();
		}
		return mapMouseListener;
	}

	public MouseWheelListener getMapMouseWheelListener() {
		if (mapMouseWheelListener == null) {
			mapMouseWheelListener = new DefaultMouseWheelListener();
		}
		return mapMouseWheelListener;
	}

	public JPopupMenu getMapPopup() {
		return mapsPopupMenu;
	}

	public FreeplaneMenuBar getMenuBar() {
		if (menuBar == null) {
			menuBar = new FreeplaneMenuBar();
		}
		return menuBar;
	}

//	public MenuBuilder getMenuBuilder() {
//		return menuBuilder;
//	}

	public Set<IMouseWheelEventHandler> getMouseWheelEventHandlers() {
		return Collections.unmodifiableSet(mRegisteredMouseWheelEventHandler);
	}

	public DragGestureListener getNodeDragListener() {
		return nodeDragListener;
	}

	public DropTargetListener getNodeDropTargetListener() {
		return nodeDropTargetListener;
	}

	public KeyListener getNodeKeyListener() {
		if (nodeKeyListener == null) {
			nodeKeyListener = new DefaultNodeKeyListener(null);
		}
		return nodeKeyListener;
	}

	public IMouseListener getNodeMotionListener() {
		return nodeMotionListener;
	}

	public IMouseListener getNodeMouseMotionListener() {
		if (nodeMouseMotionListener == null) {
			nodeMouseMotionListener = new DefaultNodeMouseMotionListener();
		}
		return nodeMouseMotionListener;
	}

	public JPopupMenu getNodePopupMenu() {
		return nodePopupMenu;
	}

	public JComponent getToolBar(final String name) {
		return toolBars.get(name);
	}

	public Iterable<JComponent> getToolBars(final int position) {
		return toolbarLists[position];
	}

	public void removeMouseWheelEventHandler(final IMouseWheelEventHandler handler) {
		mRegisteredMouseWheelEventHandler.remove(handler);
	}

	public void setMapMouseListener(final IMouseListener mapMouseMotionListener) {
		if (mapMouseListener != null) {
			throw new RuntimeException("already set");
		}
		mapMouseListener = mapMouseMotionListener;
	}

	public void setMapMouseWheelListener(final MouseWheelListener mouseWheelListener) {
		if (mapMouseWheelListener != null) {
			throw new RuntimeException("already set");
		}
		mapMouseWheelListener = mouseWheelListener;
	}

	public void setMenuBar(final FreeplaneMenuBar menuBar) {
		if (mapMouseWheelListener != null) {
			throw new RuntimeException("already set");
		}
		this.menuBar = menuBar;
	}


	public void setNodeDragListener(DragGestureListener nodeDragListener) {
		if (this.nodeDragListener != null) {
			throw new RuntimeException("already set");
		}
    	this.nodeDragListener = nodeDragListener;
    }

	public void setNodeDropTargetListener(final DropTargetListener nodeDropTargetListener) {
		if (this.nodeDropTargetListener != null) {
			throw new RuntimeException("already set");
		}
		this.nodeDropTargetListener = nodeDropTargetListener;
	}

	public void setNodeKeyListener(final KeyListener nodeKeyListener) {
		if (this.nodeKeyListener != null) {
			throw new RuntimeException("already set");
		}
		this.nodeKeyListener = nodeKeyListener;
	}

	public void setNodeMotionListener(final IMouseListener nodeMotionListener) {
		if (this.nodeMotionListener != null) {
			throw new RuntimeException("already set");
		}
		this.nodeMotionListener = nodeMotionListener;
	}

	public void setNodeMouseMotionListener(final IMouseListener nodeMouseMotionListener) {
		if (this.nodeMouseMotionListener != null) {
			throw new RuntimeException("already set");
		}
		this.nodeMouseMotionListener = nodeMouseMotionListener;
	}

	public void setNodePopupMenu(final JPopupMenu nodePopupMenu) {
		if (this.nodePopupMenu != null) {
			throw new RuntimeException("already set");
		}
		this.nodePopupMenu = nodePopupMenu;
	}

	public void updateMapList() {
		for (Entry entry : mapMenuEntries.keySet())
			rebuildMenu(entry);
	}

	final private Map<Entry, Entry> mapMenuEntries = new IdentityHashMap<>();

	private void createModeActions(final Entry modesMenuEntry) {
		rebuildMenuOnMapChange(modesMenuEntry);
		Controller controller = Controller.getCurrentController();
		EntryAccessor entryAccessor = new EntryAccessor();
		for (final String key : new LinkedList<String>(controller.getModes())) {
			final AFreeplaneAction modesMenuAction = new ModesMenuAction(key, controller);
			Entry actionEntry = new Entry();
			entryAccessor.setAction(actionEntry, modesMenuAction);
			final ModeController modeController = controller.getModeController();
			if (modeController != null && modeController.getModeName().equals(key)) {
				actionEntry.setAttribute("selected", true);
			}
			modesMenuEntry.addChild(actionEntry);
			ResourceController.getResourceController().getProperty(("keystroke_mode_" + key));
		}
	}

	private void createMapActions(final Entry mapsMenuEntry) {
		rebuildMenuOnMapChange(mapsMenuEntry);
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		final List<? extends Component> mapViewVector = mapViewManager.getMapViewVector();
		if (mapViewVector == null) {
			return;
		}
		EntryAccessor entryAccessor = new EntryAccessor();
		for (final Component mapView : mapViewVector) {
			final String displayName = mapView.getName();
			Entry actionEntry = new Entry();
			entryAccessor.setAction(actionEntry, new MapsMenuAction(displayName));
			final MapView currentMapView = (MapView) mapViewManager.getMapViewComponent();
			if (currentMapView != null) {
				if (mapView == currentMapView) {
					actionEntry.setAttribute("selected", true);
				}
			}
			mapsMenuEntry.addChild(actionEntry);
		}
	}

	private void rebuildMenuOnMapChange(final Entry entry) {
	    Entry menuEntry;
		for (menuEntry = entry.getParent(); //
		menuEntry.getName().isEmpty(); //
		menuEntry = menuEntry.getParent());
		mapMenuEntries.put(menuEntry, null);
    }

	public void rebuildMenu(Entry entry){
		if (subtreeBuilder != null)
			subtreeBuilder.rebuildChildren(entry);
	}

	public void rebuildMenus(String name) {
		final List<Entry> entries = getGenericMenuStructure().findEntries(name);
		for (Entry entry : entries)
			rebuildMenu(entry);
	}

	public void updateMenus(String menuStructureResource, Set<String> plugins) {
		mapsPopupMenu = new JPopupMenu();
		mapsPopupMenu.setName(TextUtils.getText("mindmaps"));
		if(useRibbonMenu()) {
			final URL ribbonStructure = ResourceController.getResourceController().getResource(menuStructureResource.replace("menu.xml", "ribbon.xml"));
			if (ribbonStructure != null) {
				getMenuBuilder(RibbonBuilder.class).updateRibbon(ribbonStructure);
			}
		}

		final URL genericStructure = ResourceController.getResourceController().getResource(
		    menuStructureResource.replace("menu.xml", ".generic.xml"));
		if(genericStructure != null){
			final boolean isUserDefined = genericStructure.getProtocol().equalsIgnoreCase("file");
			try {
				final FreeplaneResourceAccessor resourceAccessor = new FreeplaneResourceAccessor();
				final EntriesForAction entries = new EntriesForAction();
				final ActionAcceleratorManager acceleratorManager = getAcceleratorManager();
				final MenuBuildProcessFactory menuBuildProcessFactory = new MenuBuildProcessFactory();
				final PhaseProcessor buildProcessor = menuBuildProcessFactory.createBuildProcessor(this,
				    Controller.getCurrentModeController(), resourceAccessor, acceleratorManager, entries)
				    .getBuildProcessor();
				subtreeBuilder = menuBuildProcessFactory.getChildProcessor();
				acceleratorManager.addAcceleratorChangeListener(new MenuAcceleratorChangeListener(entries));
				for (final Phase phase : Phase.values())
					for (java.util.Map.Entry<String, BuilderDestroyerPair> entry : customBuilders.get(phase.ordinal())
					    .entrySet())
						buildProcessor.phase(phase).addBuilderPair(entry.getKey(), entry.getValue());
				final InputStream resource = genericStructure.openStream();
				final BufferedReader reader = new BufferedReader(new InputStreamReader(resource));
				genericMenuStructure = XmlEntryStructureBuilder.buildMenuStructure(reader);
				buildProcessor.build(genericMenuStructure);
			} catch (Exception e) {
				if(isUserDefined){
					LogUtils.warn(e);
					String myMessage = TextUtils.format("menu_error", genericStructure.getPath(), e.getMessage());
					UITools.backOtherWindows();
					JOptionPane.showMessageDialog(UITools.getFrame(), myMessage, "Freeplane", JOptionPane.ERROR_MESSAGE);
					System.exit(-1);
				}
				throw new RuntimeException(e);
			}
		}

	}

	public boolean useRibbonMenu() {
		return useRibbonMenu;
	}

	public void addUiBuilder(Phase phase, String name, BuilderDestroyerPair builderDestroyerPair) {
		customBuilders.get(phase.ordinal()).put(name, builderDestroyerPair);
	}

	@Override
	public Entry getGenericMenuStructure() {
		return genericMenuStructure;
	}
}

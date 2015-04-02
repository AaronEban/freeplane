package org.freeplane.core.ui.menubuilders.menu;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.freeplane.core.ui.menubuilders.generic.Entry;
import org.freeplane.core.ui.menubuilders.generic.EntryAccessor;
import org.freeplane.core.ui.menubuilders.generic.EntryVisitor;

public class JToolbarComponentBuilder implements EntryVisitor {

	private final ComponentProvider componentProvider;

	public JToolbarComponentBuilder(ComponentProvider componentProvider) {
		super();
		this.componentProvider = componentProvider;
	}

	public JToolbarComponentBuilder() {
		this(new ToolbarComponentProvider());
	}


	@Override
	public void visit(Entry entry) {
		Component component = componentProvider.createComponent(entry);
		if(component != null){
			new EntryAccessor().setComponent(entry, component);
			final Container container = (Container) new EntryAccessor().getAncestorComponent(entry);
			if (container instanceof JToolBar)
				container.add(component);
			else
				SwingUtilities.getAncestorOfClass(JToolBar.class, container).add(component);
		}
	}

	@Override
	public boolean shouldSkipChildren(Entry entry) {
		return false;
	}
}

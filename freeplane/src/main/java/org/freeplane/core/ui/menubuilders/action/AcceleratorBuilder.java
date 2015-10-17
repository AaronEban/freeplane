package org.freeplane.core.ui.menubuilders.action;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.menubuilders.generic.Entry;
import org.freeplane.core.ui.menubuilders.generic.EntryAccessor;
import org.freeplane.core.ui.menubuilders.generic.EntryVisitor;
import org.freeplane.core.util.Compat;

public class AcceleratorBuilder implements EntryVisitor{

	private final IAcceleratorMap map;
	private final IEntriesForAction entries;

	public AcceleratorBuilder(IAcceleratorMap map, IEntriesForAction entries) {
		this.map = map;
		this.entries = entries;
	}

	public void visit(Entry entry) {
		final AFreeplaneAction action = new EntryAccessor().getAction(entry);
		if (action != null) {
			final EntryAccessor entryAccessor = new EntryAccessor();
			String accelerator = entryAccessor.getAccelerator(entry);
			if(accelerator != null) {
				map.setDefaultAccelerator(action, accelerator);
			}
			else
				map.setDefaultAccelerator(action);
			entries.registerEntry(action, entry);
		}
	}

	protected boolean isMacOsX() {
		return Compat.isMacOsX();
	}

	@Override
	public boolean shouldSkipChildren(Entry entry) {
		return false;
	}

}
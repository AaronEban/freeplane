package org.freeplane.view.swing.map;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

import org.freeplane.features.nodestyle.NodeStyleController;

class ContentPaneLayout implements LayoutManager {
	public void addLayoutComponent(final String name, final Component comp) {
	}

	public void layoutContainer(final Container parent) {
		final int componentCount = parent.getComponentCount();
		final int width = parent.getWidth();
		NodeView view = (NodeView) parent.getParent();
		final MapView map = view.getMap();
		final NodeStyleController ncs = NodeStyleController.getController(map.getModeController());
		final int maxWidth = map.getZoomed(ncs.getMaxWidth(view.getModel()).toBaseUnits());
		final int minWidth = map.getZoomed(ncs.getMinWidth(view.getModel()).toBaseUnits());
		int y = 0;
		for (int i = 0; i < componentCount; i++) {
			final Component component = parent.getComponent(i);
			if (component.isVisible()) {
				component.validate();
				final Dimension preferredCompSize;
				if( width == 0) 
					preferredCompSize = new Dimension();
				else if (component instanceof ZoomableLabel){
					ZoomableLabel r = ((ZoomableLabel)component);
					r.setMinimumWidth(minWidth);
					r.setMaximumWidth(maxWidth);
					preferredCompSize=  r.getPreferredSize();
				}
				else{
					preferredCompSize=  component.getPreferredSize();
				}
				
				if (component instanceof ZoomableLabel) {
					component.setBounds(0, y, width, preferredCompSize.height);
				}
				else {
					if(width > preferredCompSize.width){
						final int x = (int) (component.getAlignmentX() * (width - preferredCompSize.width));
						component.setBounds(x, y, preferredCompSize.width, preferredCompSize.height);
					}
					else{
						component.setBounds(0, y, width, preferredCompSize.height);
					}
				}
				y += preferredCompSize.height;
			}
			else{
				component.setBounds(0, y, 0, 0);
			}
		}
	}

	public Dimension minimumLayoutSize(final Container parent) {
		return preferredLayoutSize(parent);
	}

	public Dimension preferredLayoutSize(final Container parent) {
		NodeView view = (NodeView) parent.getParent();
		final MapView map = view.getMap();
		final NodeStyleController ncs = NodeStyleController.getController(map.getModeController());
		final int minWidth = map.getZoomed(ncs.getMinWidth(view.getModel()).toBaseUnits());
		final int maxWidth = map.getZoomed(ncs.getMaxWidth(view.getModel()).toBaseUnits());
		final Dimension prefSize = new Dimension(0, 0);
		final int componentCount = parent.getComponentCount();
		for (int i = 0; i < componentCount; i++) {
			final Component component = parent.getComponent(i);
			if (component.isVisible()) {
				component.validate();
				final Dimension preferredCompSize;
				if(component instanceof ZoomableLabel) {
					ZoomableLabel r = ((ZoomableLabel)component);
					r.setMinimumWidth(minWidth);
					r.setMaximumWidth(maxWidth);
				} 
				preferredCompSize = component.getPreferredSize();
				
				prefSize.height += preferredCompSize.height;
				prefSize.width = Math.max(prefSize.width, preferredCompSize.width);
			}
		}
		return prefSize;
	}

	public void removeLayoutComponent(final Component comp) {
	}
}
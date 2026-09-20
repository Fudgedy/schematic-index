package com.fudgedy.schematicindex.gui;

import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.GuiContext;

// A ButtonGeneric whose icon paints itself through IGuiIcon.renderAt, at the spot malilib's own sheet blit
// lands so the label offset is unchanged
public final class IconButton extends ButtonGeneric
{
	public IconButton(int x, int y, int width, int height, String text, IGuiIcon icon, String... hoverStrings)
	{
		super(x, y, width, height, text, icon, hoverStrings);
	}

	@Override
	public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
	{
		super.render(ctx, mouseX, mouseY, selected);

		if (!this.visible)
		{
			return;
		}

		int inset = this.renderDefaultBackground ? 4 : 0;
		int iconX = this.alignment.getLeft(this.x, this.x + this.width, this.icon.getWidth(), inset);
		int iconY = this.y + (this.height - this.icon.getHeight()) / 2;
		this.icon.renderAt(ctx, iconX, iconY, this.zLevel, this.enabled, this.hovered);
	}
}

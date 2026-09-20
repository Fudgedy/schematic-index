package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;

// Chrome around the vanilla edit widgets; the boxes themselves stay owned by the screen
public final class Fields
{
	private Fields()
	{
	}

	public static void single(GuiGraphics ctx, EditBox box, int x, int y, int width, int height, int mouseX,
			int mouseY, float partialTick)
	{
		boolean focused = box.isFocused();
		boolean hover = !focused && Theme.inside(mouseX, mouseY, x, y, width, height);
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL,
				focused || hover ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

		if (focused)
		{
			Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.ACCENT);
		}
		else if (hover)
		{
			Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.HAIRLINE);
		}

		box.setX(x + 6);
		box.setY(y + 4);
		box.setWidth(width - 12);
		box.render(ctx, mouseX, mouseY, partialTick);
	}

	public static void multiline(GuiGraphics ctx, MultiLineEditBox box, Rect bounds, int x, int y, int width,
			int height, int mouseX, int mouseY, float partialTick)
	{
		boolean focused = box.isFocused();
		boolean hover = !focused && Theme.inside(mouseX, mouseY, x, y, width, height);
		bounds.set(x, y, width, height);

		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_MODAL,
				focused || hover ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

		if (focused)
		{
			Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_MODAL, Theme.ACCENT);
		}
		else if (hover)
		{
			Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_MODAL, Theme.HAIRLINE);
		}

		box.setX(x);
		box.setY(y);
		box.render(ctx, mouseX, mouseY, partialTick);
	}
}

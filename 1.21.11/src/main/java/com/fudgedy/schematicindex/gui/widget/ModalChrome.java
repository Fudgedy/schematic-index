package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;

public final class ModalChrome
{
	private ModalChrome()
	{
	}

	// The scrim is drawn separately by each modal, before its layout math
	public static void frame(GuiGraphics ctx, int x, int y, int w, int h, boolean redAccent)
	{
		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_MODAL, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, w, h, Theme.RADIUS_MODAL, Theme.HAIRLINE);

		if (redAccent)
		{
			ctx.fill(x + 1, y + 1, x + 5, y + h - 1, 0xFFD64545);
		}
	}
}

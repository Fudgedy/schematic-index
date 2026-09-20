package com.fudgedy.schematicindex.gui.mapart;

import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.GuiGraphics;

// Small drawing and feedback helpers shared by the Mapart panels
public final class MapartUi
{
	private MapartUi()
	{
	}

	public static void press(Rect rect)
	{
		Theme.buttonPress(rect);
		Theme.click();
	}

	public static void scrollbar(GuiGraphics ctx, int x, int top, int height, float value, float max)
	{
		if (max <= 0.0F || height <= 0)
		{
			return;
		}

		int thumb = Math.max(12, Math.round(height * height / (height + max)));
		int thumbY = top + Math.round((height - thumb) * (value / max));
		ctx.fill(x, top, x + 2, top + height, Theme.SURFACE_CARD);
		ctx.fill(x, thumbY, x + 2, thumbY + thumb, Theme.TEXT_ASH);
	}
}

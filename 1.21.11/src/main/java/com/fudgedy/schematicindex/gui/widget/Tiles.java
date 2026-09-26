package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// Cards, stat tiles and progress bars; tiles sit on the SURFACE_CARD modal body, so they take the elevated step
public final class Tiles
{
	public static final int STAT_HEIGHT = 40;

	private Tiles()
	{
	}

	public static void card(GuiGraphics ctx, int x, int y, int width, int height)
	{
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
	}

	public static void stat(GuiGraphics ctx, Font font, int x, int y, int width, String value, String label)
	{
		card(ctx, x, y, width, STAT_HEIGHT);
		int inner = width - Theme.SPACE_S * 2;
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, value, inner)), x + Theme.SPACE_S, y + Theme.SPACE_S, Theme.TEXT);
		Theme.text(ctx, font, Theme.clip(font, label, inner), x + Theme.SPACE_S, y + Theme.SPACE_XL, Theme.TEXT_ASH);
	}

	public static void progress(GuiGraphics ctx, int x, int y, int width, int height, float fraction, boolean complete)
	{
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.SURFACE_HOVER);
		float clamped = Math.max(0.0F, Math.min(1.0F, fraction));

		if (clamped <= 0.0F)
		{
			return;
		}

		int fill = Math.max(Theme.SPACE_2XS, Math.round(width * clamped));
		Theme.roundedRect(ctx, x, y, fill, height, Theme.RADIUS_PILL, complete ? Theme.SUCCESS : Theme.ACCENT_BRIGHT);
	}
}

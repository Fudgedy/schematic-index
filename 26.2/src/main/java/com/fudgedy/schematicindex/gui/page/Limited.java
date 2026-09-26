package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Countdown copy and the small badge for seasonal items, shared by tags, presets and the banner
final class Limited
{
	static final float BADGE_SCALE = 0.75F;
	private static final long HOUR_MS = 3600000L;
	private static final long DAY_MS = 24L * HOUR_MS;
	private static final int FALLBACK_ACCENT = 0xFF7518;

	private Limited()
	{
	}

	static boolean shown()
	{
		return RemoteContent.feature("seasonal");
	}

	static String left(long untilMs)
	{
		String span = span(untilMs);
		return span.isEmpty() ? "Ends soon" : span + " left";
	}

	// Whole days, then whole hours; empty inside the last hour
	static String span(long untilMs)
	{
		long remaining = untilMs - System.currentTimeMillis();

		if (untilMs <= 0L || remaining < HOUR_MS)
		{
			return "";
		}

		return remaining >= DAY_MS ? remaining / DAY_MS + "d" : remaining / HOUR_MS + "h";
	}

	// The live season's colour, so every limited item reads as part of the same drop
	static int accent()
	{
		RemoteContent.Season season = CosmeticColors.season();
		return 0xFF000000 | (season == null ? FALLBACK_ACCENT : season.accent() & 0xFFFFFF);
	}

	static int badgeWidth(Font font, String text)
	{
		return Math.round(font.width(text) * BADGE_SCALE) + 6;
	}

	// Returns the badge width so a caller can lay the next thing after it
	static int badge(GuiGraphicsExtractor ctx, Font font, String text, int x, int y, int color)
	{
		int width = badgeWidth(font, text);
		int height = Math.round(font.lineHeight * BADGE_SCALE) + 3;
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, 0xE0101316);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, color);
		Theme.textScaled(ctx, font, text, x + 3, y + 2, BADGE_SCALE, color);
		return width;
	}
}

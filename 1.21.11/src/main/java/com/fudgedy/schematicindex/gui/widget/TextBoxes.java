package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

// Hand-rolled inputs for modals that take raw key events instead of an EditBox
public final class TextBoxes
{
	private static final long BLINK_MS = 500L;

	private TextBoxes()
	{
	}

	public static void wrapped(GuiGraphics ctx, Font font, int x, int y, int width, int height,
			List<String> lines, String value, String placeholder, boolean caret)
	{
		int line = font.lineHeight;
		int textLines = Math.max(1, lines.size());

		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.BACKDROP);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.ACCENT);

		int textX = x + 6;
		int textY = y + 6;

		if (value.isEmpty())
		{
			Theme.text(ctx, font, placeholder, textX, textY, Theme.TEXT_ASH);
		}
		else
		{
			for (String row : lines)
			{
				Theme.text(ctx, font, row, textX, textY, Theme.TEXT);
				textY += line + 1;
			}
		}

		if (caret && blink())
		{
			String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
			int caretX = value.isEmpty() ? textX : textX + font.width(last);
			int caretY = value.isEmpty() ? y + 6 : y + 6 + (textLines - 1) * (line + 1);
			ctx.fill(caretX, caretY - 1, caretX + 1, caretY + line, Theme.TEXT);
		}
	}

	public static void caretLine(GuiGraphics ctx, Font font, int x, int y, int width, int height,
			String value, String placeholder)
	{
		int line = font.lineHeight;

		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.BACKDROP);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.ACCENT);

		int textX = x + 6;
		int textY = y + (height - line) / 2 + 1;

		if (value.isEmpty())
		{
			Theme.text(ctx, font, placeholder, textX, textY, Theme.TEXT_ASH);
		}
		else
		{
			Theme.text(ctx, font, Theme.clip(font, value, width - 12), textX, textY, Theme.TEXT);
		}

		if (blink())
		{
			int caretX = textX + (value.isEmpty() ? 0 : font.width(Theme.clip(font, value, width - 12)));
			ctx.fill(caretX, textY - 1, caretX + 1, textY + line, Theme.TEXT);
		}
	}

	// The trailing underscore doubles as the caret, so the placeholder only shows on the dark half of the blink
	public static void underscoreLine(GuiGraphics ctx, Font font, int x, int y, int width, int height,
			String value, String placeholder, boolean clip)
	{
		int line = font.lineHeight;

		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.ACCENT);

		boolean caret = blink();
		String display = value.isEmpty() && !caret ? placeholder : value + (caret ? "_" : "");
		int textColor = value.isEmpty() && !caret ? Theme.TEXT_MUTE : Theme.TEXT;
		Theme.text(ctx, font, clip ? Theme.clip(font, display, width - 12) : display, x + 6,
				y + (height - line) / 2 + 1, textColor);
	}

	private static boolean blink()
	{
		return System.currentTimeMillis() / BLINK_MS % 2L == 0L;
	}
}

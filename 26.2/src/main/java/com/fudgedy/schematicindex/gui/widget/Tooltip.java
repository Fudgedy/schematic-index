package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

// Hovered elements call show() while they draw; the owner calls render() once last, so the tip sits above
// everything and a cursor that keeps moving between elements restarts the delay
public final class Tooltip
{
	private static final long DELAY_MS = 300L;
	private static final int MAX_WIDTH = 200;
	private static final int PAD_X = 6;
	private static final int PAD_Y = 4;
	private static final int LINE_PITCH = 10;

	private static String requested;
	private static String shown;
	private static long since;

	private Tooltip()
	{
	}

	public static void show(String text)
	{
		requested = text;
	}

	public static void render(GuiGraphicsExtractor ctx, Font font, int mouseX, int mouseY, int screenWidth, int screenHeight)
	{
		String text = requested;
		requested = null;
		long now = System.currentTimeMillis();

		if (text == null)
		{
			shown = null;
			return;
		}

		if (!text.equals(shown))
		{
			shown = text;
			since = now;
		}

		if (now - since < DELAY_MS)
		{
			return;
		}

		List<String> lines = wrap(font, text);
		int width = 0;

		for (String line : lines)
		{
			width = Math.max(width, font.width(line));
		}

		int boxWidth = width + PAD_X * 2;
		int boxHeight = lines.size() * LINE_PITCH - 1 + PAD_Y * 2;
		int x = mouseX + Theme.SPACE_S;
		int y = mouseY + Theme.SPACE_S;

		if (x + boxWidth > screenWidth - Theme.SPACE_XS)
		{
			x = mouseX - Theme.SPACE_S - boxWidth;
		}

		if (y + boxHeight > screenHeight - Theme.SPACE_XS)
		{
			y = mouseY - Theme.SPACE_S - boxHeight;
		}

		Theme.roundedRect(ctx, x, y, boxWidth, boxHeight, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, boxWidth, boxHeight, Theme.RADIUS_PILL, Theme.HAIRLINE_STRONG);

		for (int i = 0; i < lines.size(); i++)
		{
			Theme.text(ctx, font, lines.get(i), x + PAD_X, y + PAD_Y + i * LINE_PITCH, Theme.TEXT_MUTE);
		}
	}

	private static List<String> wrap(Font font, String text)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();

		for (String word : text.split(" "))
		{
			String candidate = line.isEmpty() ? word : line + " " + word;

			if (font.width(candidate) > MAX_WIDTH - PAD_X * 2 && !line.isEmpty())
			{
				lines.add(line.toString());
				line = new StringBuilder(word);
				continue;
			}

			line = new StringBuilder(candidate);
		}

		if (!line.isEmpty())
		{
			lines.add(line.toString());
		}

		return lines;
	}
}

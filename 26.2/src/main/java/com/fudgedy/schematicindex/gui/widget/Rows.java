package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

// Section headers and list rows; a row sits one surface step above its parent and hovers one more
public final class Rows
{
	public static final String CHEVRON = "\u203a";

	private Rows()
	{
	}

	// Returns where the section's content starts
	public static int header(GuiGraphicsExtractor ctx, Font font, String title, @Nullable String meta, int x, int y, int width)
	{
		Theme.text(ctx, font, Theme.bold(title), x, y, Theme.TEXT);

		if (meta != null && !meta.isEmpty())
		{
			Theme.text(ctx, font, meta, x + width - font.width(meta), y, Theme.TEXT_ASH);
		}

		return y + font.lineHeight + Theme.SPACE_S;
	}

	// Rows here sit on the SURFACE_CARD modal body, so the idle fill is the elevated step; returns hovered
	public static boolean row(GuiGraphicsExtractor ctx, Rect rect, boolean clickable, int mouseX, int mouseY)
	{
		boolean hovered = clickable && rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered, Theme.MOTION_HOVER_MS);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_CARD,
				Theme.mix(Theme.SURFACE_ELEVATED, Theme.SURFACE_HOVER, hover));
		return hovered;
	}

	// Returns the chevron's left edge, so callers can lay trailing content before it
	public static int chevron(GuiGraphicsExtractor ctx, Font font, Rect row, boolean hovered)
	{
		int x = row.x + row.width - Theme.SPACE_S - font.width(CHEVRON);
		Theme.text(ctx, font, CHEVRON, x, row.y + (row.height - font.lineHeight) / 2 + 1, hovered ? Theme.TEXT : Theme.TEXT_MUTE);
		return x;
	}
}

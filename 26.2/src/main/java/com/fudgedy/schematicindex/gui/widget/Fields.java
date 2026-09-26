package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;

// Chrome around the vanilla edit widgets; the boxes themselves stay owned by the screen
public final class Fields
{
	private Fields()
	{
	}

	public static void single(GuiGraphicsExtractor ctx, EditBox box, int x, int y, int width, int height, int mouseX,
			int mouseY, float partialTick)
	{
		frame(ctx, box.isFocused(), x, y, width, height, mouseX, mouseY);
		box.setX(x + 6);
		box.setY(y + 4);
		box.setWidth(width - 12);
		box.extractWidgetRenderState(ctx, mouseX, mouseY, partialTick);
	}

	// Field label with optional right-aligned meta, dropped when the two would touch
	public static void label(GuiGraphicsExtractor ctx, Font font, String label, String meta, int x, int y, int width)
	{
		Theme.text(ctx, font, label, x, y, Theme.TEXT_ASH);

		if (!meta.isEmpty() && font.width(label) + Theme.SPACE_S + font.width(meta) <= width)
		{
			Theme.text(ctx, font, meta, x + width - font.width(meta), y, Theme.TEXT_ASH);
		}
	}

	// The caption sits in the gap under the field, so an error never shifts the form
	public static void error(GuiGraphicsExtractor ctx, Font font, String error, int x, int y, int width, int height)
	{
		if (error.isEmpty())
		{
			return;
		}

		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.DANGER_TEXT);
		Theme.text(ctx, font, Theme.clip(font, error, width), x, y + height + Theme.SPACE_2XS, Theme.DANGER_TEXT);
	}

	public static void multiline(GuiGraphicsExtractor ctx, MultiLineEditBox box, Rect bounds, int x, int y, int width,
			int height, int mouseX, int mouseY, float partialTick)
	{
		bounds.set(x, y, width, height);
		frame(ctx, box.isFocused(), x, y, width, height, mouseX, mouseY);
		// Vanilla pads the text area by 4, so a 2 nudge lines the text up with the single-line fields
		box.setX(x + 2);
		box.setY(y + 2);
		box.extractWidgetRenderState(ctx, mouseX, mouseY, partialTick);
	}

	// An inset well one step darker than the card, so a field reads as a box on any modal or page
	private static void frame(GuiGraphicsExtractor ctx, boolean focused, int x, int y, int width, int height, int mouseX,
			int mouseY)
	{
		boolean hover = Theme.inside(mouseX, mouseY, x, y, width, height);
		int border = focused ? Theme.ACCENT_BRIGHT : hover ? Theme.HAIRLINE_STRONG : Theme.HAIRLINE;
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.SURFACE);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, border);
	}
}

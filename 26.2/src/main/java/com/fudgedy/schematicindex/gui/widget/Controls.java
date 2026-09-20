package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Controls
{
	private Controls()
	{
	}

	public static void toggle(GuiGraphicsExtractor ctx, Font font, Rect rect, String label, boolean on, int mouseX,
			int mouseY)
	{
		toggle(ctx, font, rect, label, on, mouseX, mouseY, false);
	}

	// A disabled toggle greys out and ignores hover; the caller must also ignore clicks
	public static void toggle(GuiGraphicsExtractor ctx, Font font, Rect rect, String label, boolean on, int mouseX,
			int mouseY, boolean disabled)
	{
		boolean hovered = !disabled && rect.contains(mouseX, mouseY);
		float hover = disabled ? 0.0F : Theme.buttonHover(rect, hovered);
		float scale = disabled ? 1.0F : Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				Theme.lighten(Theme.SURFACE_CARD, 0.10F * hover));

		int boxSize = 8;
		int boxX = rect.x + 5;
		int boxY = rect.y + (rect.height - boxSize) / 2;

		if (on)
		{
			Theme.roundedRect(ctx, boxX, boxY, boxSize, boxSize, 1, disabled ? Theme.TEXT_ASH : Theme.ACCENT);
		}
		else
		{
			Theme.roundedOutline(ctx, boxX, boxY, boxSize, boxSize, 1, Theme.TEXT_ASH);
		}

		int labelColor = disabled ? Theme.TEXT_ASH : (on ? Theme.TEXT : Theme.TEXT_MUTE);
		Theme.text(ctx, font, Theme.clip(font, label, rect.width - boxSize - 14),
				boxX + boxSize + 5, rect.y + (rect.height - font.lineHeight) / 2 + 1,
				labelColor);

		Theme.pop(ctx);
	}

	// The Settings page idiom: label and value on one line, a full dark track with the accent fill up
	// to a round knob; returns the y just under the track
	public static int settingSlider(GuiGraphicsExtractor ctx, Font font, Rect track, String label, String value,
			float fraction, boolean dragging, int x, int y, int width, int mouseX, int mouseY)
	{
		Theme.text(ctx, font, label, x, y, Theme.TEXT);
		Theme.text(ctx, font, value, x + width - font.width(value), y, Theme.TEXT_MUTE);
		y += font.lineHeight + 7;

		int trackY = y + 2;
		track.set(x, y - 4, width, 14);
		Theme.roundedRect(ctx, x, trackY, width, 4, 2, Theme.SURFACE_ELEVATED);

		int fillWidth = Math.round(width * Math.max(0.0F, Math.min(1.0F, fraction)));
		Theme.roundedRect(ctx, x, trackY, Math.max(2, fillWidth), 4, 2, Theme.ACCENT);

		int knobX = x + fillWidth;
		int knobRadius = 5;
		boolean active = dragging || track.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, knobX - knobRadius, trackY + 2 - knobRadius, knobRadius * 2, knobRadius * 2, knobRadius,
				active ? Theme.ACCENT_BRIGHT : Theme.ON_ACCENT);

		return y + 14;
	}

	public static void slider(GuiGraphicsExtractor ctx, Rect rect, float value, int mouseX, int mouseY,
			boolean dragging)
	{
		float clamped = Math.max(0.0F, Math.min(1.0F, value));
		int trackY = rect.y + rect.height / 2 - 1;
		Theme.roundedRect(ctx, rect.x, trackY, rect.width, 2, 1, Theme.SURFACE_CARD);

		int fill = Math.round(rect.width * clamped);
		Theme.roundedRect(ctx, rect.x, trackY, fill, 2, 1, Theme.ACCENT);

		boolean hovered = rect.contains(mouseX, mouseY);
		int knobX = rect.x + Math.max(3, Math.min(rect.width - 3, fill));
		Theme.roundedRect(ctx, knobX - 3, rect.y + rect.height / 2 - 4, 6, 8, 1,
				hovered || dragging ? Theme.ACCENT_BRIGHT : Theme.TEXT);
	}
}

package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class Controls
{
	public static final int SWITCH_WIDTH = 20;
	public static final int SWITCH_HEIGHT = 11;
	private static final int KNOB = SWITCH_HEIGHT - Theme.SPACE_2XS * 2;
	private static final int TRACK = 4;
	private static final int KNOB_RADIUS = 5;

	private Controls()
	{
	}

	// The knob eases across over MOTION_TAB, so the key must be an object that lives across frames
	public static void toggleSwitch(GuiGraphics ctx, Object key, int x, int y, boolean on, boolean disabled)
	{
		float t = Theme.buttonHover(key, on, Theme.MOTION_TAB_MS);
		int track = disabled ? Theme.SURFACE_ELEVATED : Theme.mix(Theme.SURFACE_HOVER, Theme.ACCENT, t);
		Theme.roundedRect(ctx, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, Theme.RADIUS_PILL, track);
		int travel = SWITCH_WIDTH - Theme.SPACE_2XS * 2 - KNOB;
		int knobX = x + Theme.SPACE_2XS + Math.round(travel * t);
		int knob = disabled ? Theme.TEXT_ASH : Theme.mix(Theme.TEXT_MUTE, Theme.TEXT, t);
		Theme.roundedRect(ctx, knobX, y + Theme.SPACE_2XS, KNOB, KNOB, Theme.RADIUS_PILL, knob);
	}

	// A settings-row slider: the track spans the rect's width at its vertical centre and the hit area is the whole rect
	public static void rowSlider(GuiGraphics ctx, Rect track, float fraction, boolean dragging, int mouseX, int mouseY)
	{
		int trackY = track.y + (track.height - TRACK) / 2;
		Theme.roundedRect(ctx, track.x, trackY, track.width, TRACK, Theme.RADIUS_PILL, Theme.SURFACE_HOVER);
		int fill = Math.round(track.width * Math.max(0.0F, Math.min(1.0F, fraction)));
		Theme.roundedRect(ctx, track.x, trackY, Math.max(Theme.SPACE_2XS, fill), TRACK, Theme.RADIUS_PILL, Theme.ACCENT);
		boolean active = dragging || track.contains(mouseX, mouseY);
		int knobX = track.x + fill - KNOB_RADIUS;
		int knobY = trackY + TRACK / 2 - KNOB_RADIUS;
		Theme.roundedRect(ctx, knobX, knobY, KNOB_RADIUS * 2, KNOB_RADIUS * 2, KNOB_RADIUS,
				active ? Theme.ACCENT_BRIGHT : Theme.ON_ACCENT);
	}

	public static void toggle(GuiGraphics ctx, Font font, Rect rect, String label, boolean on, int mouseX,
			int mouseY)
	{
		toggle(ctx, font, rect, label, on, mouseX, mouseY, false);
	}

	// A disabled toggle greys out and ignores hover; the caller must also ignore clicks
	public static void toggle(GuiGraphics ctx, Font font, Rect rect, String label, boolean on, int mouseX,
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
	public static int settingSlider(GuiGraphics ctx, Font font, Rect track, String label, String value,
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

	public static void slider(GuiGraphics ctx, Rect rect, float value, int mouseX, int mouseY,
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

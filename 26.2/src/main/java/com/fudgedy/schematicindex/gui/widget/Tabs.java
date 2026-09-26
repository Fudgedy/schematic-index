package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

// Underlined tab bar for peer views and an inset segmented control for alternate views of the same data
public final class Tabs
{
	private static final int UNDERLINE = 2;
	private static final int DOT = 4;
	// Tells the bar a label has no badge
	public static final int NO_BADGE = -1;
	// Draws the badge as a plain dot rather than a count
	public static final int DOT_BADGE = 0;

	private Tabs()
	{
	}

	// badges holds NO_BADGE, DOT_BADGE or a count per label; hidden tabs pass a null label and get a zeroed rect
	public static int bar(GuiGraphicsExtractor ctx, Font font, State state, List<Rect> hits, String[] labels, int[] badges,
			int selected, int x, int y, int width, int mouseX, int mouseY)
	{
		int labelX = x;
		int targetX = x;
		int targetWidth = 0;

		for (int i = 0; i < labels.length; i++)
		{
			Rect hit = Rect.pooled(hits, i);

			if (labels[i] == null)
			{
				hit.set(0, 0, 0, 0);
				continue;
			}

			String label = Theme.bold(labels[i]);
			int labelWidth = font.width(label);
			int badgeWidth = badgeWidth(font, badges[i]);
			hit.set(labelX, y, labelWidth + badgeWidth, Theme.H_TAB);
			boolean hovered = hit.contains(mouseX, mouseY);
			float hover = Theme.buttonHover(hit, hovered && i != selected, Theme.MOTION_HOVER_MS);
			int color = i == selected ? Theme.TEXT : Theme.mix(Theme.TEXT_MUTE, Theme.TEXT, hover);
			int textY = y + (Theme.H_TAB - font.lineHeight) / 2 + 1;
			Theme.text(ctx, font, label, labelX, textY, color);
			badge(ctx, font, badges[i], labelX + labelWidth + Theme.SPACE_XS, y);

			if (i == selected)
			{
				targetX = labelX;
				targetWidth = labelWidth;
			}

			labelX += hit.width + Theme.SPACE_L;
		}

		ctx.fill(x, y + Theme.H_TAB, x + width, y + Theme.H_TAB + 1, Theme.HAIRLINE);
		underline(ctx, state, selected, targetX, targetWidth, y + Theme.H_TAB - UNDERLINE);
		return y + Theme.H_TAB + 1;
	}

	// A vertical category list for pages with more peers than a tab bar holds; returns the bottom edge
	public static int sidebar(GuiGraphicsExtractor ctx, Font font, List<Rect> hits, String[] labels, int selected, int x, int y,
			int width, int mouseX, int mouseY)
	{
		for (int i = 0; i < labels.length; i++)
		{
			Rect hit = Rect.pooled(hits, i);
			hit.set(x, y, width, Theme.H_TAB);
			boolean active = i == selected;
			float hover = Theme.buttonHover(hit, !active && hit.contains(mouseX, mouseY), Theme.MOTION_HOVER_MS);
			int fill = active ? Theme.SURFACE_CARD : Theme.mix(Theme.withAlpha(Theme.SURFACE_CARD, 0.0F), Theme.SURFACE_CARD, hover);
			Theme.roundedRect(ctx, x, y, width, Theme.H_TAB, Theme.RADIUS_CARD, fill);

			if (active)
			{
				ctx.fill(x, y + Theme.SPACE_XS, x + UNDERLINE, y + Theme.H_TAB - Theme.SPACE_XS, Theme.ACCENT_BRIGHT);
			}

			String label = Theme.bold(Theme.clipBold(font, labels[i], width - Theme.SPACE_S * 2));
			int color = active ? Theme.TEXT : Theme.mix(Theme.TEXT_MUTE, Theme.TEXT, hover);
			Theme.text(ctx, font, label, x + Theme.SPACE_S, y + (Theme.H_TAB - font.lineHeight) / 2 + 1, color);
			y += Theme.H_TAB + Theme.SPACE_XS;
		}

		return y - Theme.SPACE_XS;
	}

	public static int segmented(GuiGraphicsExtractor ctx, Font font, List<Rect> hits, String[] labels, int selected, int x,
			int y, int width, int mouseX, int mouseY)
	{
		Theme.roundedRect(ctx, x, y, width, Theme.H_CONTROL, Theme.RADIUS_PILL, Theme.SURFACE);
		int inset = Theme.SPACE_2XS;
		int segment = (width - inset * 2) / labels.length;

		for (int i = 0; i < labels.length; i++)
		{
			Rect hit = Rect.pooled(hits, i);
			int segmentX = x + inset + i * segment;
			int segmentWidth = i == labels.length - 1 ? x + width - inset - segmentX : segment;
			hit.set(segmentX, y + inset, segmentWidth, Theme.H_CONTROL - inset * 2);
			boolean active = i == selected;
			float hover = Theme.buttonHover(hit, !active && hit.contains(mouseX, mouseY), Theme.MOTION_HOVER_MS);

			if (active)
			{
				Theme.roundedRect(ctx, hit.x, hit.y, hit.width, hit.height, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
			}

			String label = Theme.bold(Theme.clipBold(font, labels[i], hit.width - Theme.SPACE_S));
			int color = active ? Theme.TEXT : Theme.mix(Theme.TEXT_MUTE, Theme.TEXT, hover);
			Theme.text(ctx, font, label, hit.x + (hit.width - font.width(label)) / 2,
					y + (Theme.H_CONTROL - font.lineHeight) / 2 + 1, color);
		}

		return y + Theme.H_CONTROL;
	}

	public static int hit(List<Rect> hits, double mouseX, double mouseY)
	{
		for (int i = 0; i < hits.size(); i++)
		{
			if (hits.get(i).contains(mouseX, mouseY))
			{
				return i;
			}
		}

		return -1;
	}

	public static void clear(List<Rect> hits)
	{
		for (Rect hit : hits)
		{
			hit.set(0, 0, 0, 0);
		}
	}

	private static void underline(GuiGraphicsExtractor ctx, State state, int selected, int x, int width, int y)
	{
		long now = System.currentTimeMillis();

		if (state.shown != selected)
		{
			state.from = state.shown;
			state.fromX = state.lastX;
			state.fromWidth = state.lastWidth;
			state.shown = selected;
			state.changedAt = now;
		}

		float t = state.from < 0 ? 1.0F : Theme.easeOut((now - state.changedAt) / (float) Theme.MOTION_TAB_MS);
		int drawX = Math.round(state.fromX + (x - state.fromX) * t);
		int drawWidth = Math.round(state.fromWidth + (width - state.fromWidth) * t);
		state.lastX = drawX;
		state.lastWidth = drawWidth;
		ctx.fill(drawX, y, drawX + drawWidth, y + UNDERLINE, Theme.ACCENT_BRIGHT);
	}

	private static int badgeWidth(Font font, int badge)
	{
		if (badge == NO_BADGE)
		{
			return 0;
		}

		return Theme.SPACE_XS + (badge == DOT_BADGE ? DOT : font.width(countLabel(badge)) + Theme.SPACE_XS * 2);
	}

	private static void badge(GuiGraphicsExtractor ctx, Font font, int badge, int x, int y)
	{
		if (badge == NO_BADGE)
		{
			return;
		}

		if (badge == DOT_BADGE)
		{
			int dotY = y + (Theme.H_TAB - DOT) / 2;
			Theme.roundedRect(ctx, x, dotY, DOT, DOT, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
			return;
		}

		String count = countLabel(badge);
		int badgeY = y + (Theme.H_TAB - Theme.H_BADGE) / 2;
		Theme.roundedRect(ctx, x, badgeY, font.width(count) + Theme.SPACE_XS * 2, Theme.H_BADGE, Theme.RADIUS_PILL, Theme.ACCENT_TINT);
		Theme.text(ctx, font, count, x + Theme.SPACE_XS, badgeY + Theme.SPACE_2XS, Theme.ACCENT_BRIGHT);
	}

	private static String countLabel(int count)
	{
		return count > 9 ? "9+" : Integer.toString(count);
	}

	// Remembers where the underline was so a switch slides it rather than jumping
	public static final class State
	{
		private int from = -1;
		private int shown = -1;
		private long changedAt;
		private int fromX;
		private int fromWidth;
		private int lastX;
		private int lastWidth;
	}
}

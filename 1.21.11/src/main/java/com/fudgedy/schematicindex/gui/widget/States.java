package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// Loading, empty and error stand-ins, so a data-backed view is never a blank area or a lone "Loading..."
public final class States
{
	private static final int BODY_MAX_WIDTH = 240;
	private static final long SHINE_PERIOD_MS = 1400L;
	private static final int SHINE_WIDTH = 14;
	private static final String RETRY = "Retry";

	private States()
	{
	}

	// Returns the bottom edge; the action rect is zeroed when there is no action
	public static int empty(GuiGraphics ctx, Font font, int x, int y, int width, @Nullable ItemStack icon,
			String title, @Nullable String body, Rect action, @Nullable String actionLabel, int mouseX, int mouseY)
	{
		int top = y + Theme.SPACE_XL;

		if (icon != null)
		{
			Theme.itemScaled(ctx, icon, x + (width - Theme.ICON_L) / 2, top, 2.0F);
			top += Theme.ICON_L + Theme.SPACE_S;
		}

		return caption(ctx, font, x, top, width, Theme.bold(title), body, Theme.TEXT_MUTE, action, actionLabel, mouseX, mouseY);
	}

	public static int error(GuiGraphics ctx, Font font, int x, int y, int width, String what, String code, Rect retry,
			int mouseX, int mouseY)
	{
		int top = y + Theme.SPACE_XL;
		String title = Theme.bold("Couldn't load " + what);
		int crossSize = Theme.ICON_S - Theme.SPACE_2XS;
		int lineWidth = crossSize + Theme.SPACE_S + font.width(title);
		int lineX = x + (width - lineWidth) / 2;
		Theme.cross(ctx, lineX, top + 1, crossSize, Theme.DANGER_TEXT);
		Theme.text(ctx, font, title, lineX + crossSize + Theme.SPACE_S, top, Theme.TEXT);
		return caption(ctx, font, x, top, width, null, code, Theme.TEXT_ASH, retry, RETRY, mouseX, mouseY);
	}

	public static void skeleton(GuiGraphics ctx, int x, int y, int width, int height)
	{
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.SKELETON);
		float progress = System.currentTimeMillis() % SHINE_PERIOD_MS / (float) SHINE_PERIOD_MS;
		int shineX = x - SHINE_WIDTH + Math.round(progress * (width + SHINE_WIDTH * 2));
		ctx.enableScissor(x, y, x + width, y + height);
		ctx.fill(shineX, y, shineX + SHINE_WIDTH, y + height, Theme.SKELETON_SHINE);
		ctx.disableScissor();
	}

	public static int skeletonRows(GuiGraphics ctx, int x, int y, int width, int rowHeight, int gap, int count)
	{
		for (int i = 0; i < count; i++)
		{
			skeleton(ctx, x, y + i * (rowHeight + gap), width, rowHeight);
		}

		return y + count * (rowHeight + gap) - gap;
	}

	// A null title leaves the line above to the caller, as the error state draws its own glyph and title
	private static int caption(GuiGraphics ctx, Font font, int x, int top, int width, @Nullable String title,
			@Nullable String body, int bodyColor, Rect action, @Nullable String actionLabel, int mouseX, int mouseY)
	{
		if (title != null)
		{
			Theme.text(ctx, font, title, x + (width - font.width(title)) / 2, top, Theme.TEXT);
		}

		int bottom = top + font.lineHeight;

		if (body != null)
		{
			String line = Theme.clip(font, body, Math.min(width, BODY_MAX_WIDTH));
			bottom += Theme.SPACE_XS;
			Theme.text(ctx, font, line, x + (width - font.width(line)) / 2, bottom, bodyColor);
			bottom += font.lineHeight;
		}

		if (actionLabel == null)
		{
			action.set(0, 0, 0, 0);
			return bottom;
		}

		bottom += Theme.SPACE_M;
		int buttonWidth = Buttons.width(font, actionLabel);
		action.set(x + (width - buttonWidth) / 2, bottom, buttonWidth, Theme.H_CONTROL);
		Buttons.button(ctx, font, action, actionLabel, Buttons.Kind.SECONDARY, true, mouseX, mouseY);
		return bottom + Theme.H_CONTROL;
	}
}

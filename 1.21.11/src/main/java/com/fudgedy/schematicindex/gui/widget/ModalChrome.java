package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ModalChrome
{
	private static final int RISE = 6;
	private static final int HEADER_Y = Theme.SPACE_M;
	private static final int ICON_GAP = Theme.SPACE_XS + Theme.SPACE_2XS;

	private ModalChrome()
	{
	}

	// The scrim is drawn separately by each modal, before its layout math
	public static void frame(GuiGraphics ctx, int x, int y, int w, int h, boolean redAccent)
	{
		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_MODAL, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, w, h, Theme.RADIUS_MODAL, Theme.HAIRLINE);

		if (redAccent)
		{
			ctx.fill(x + 1, y + 1, x + 5, y + h - 1, 0xFFD64545);
		}
	}

	// Fades the scrim in and rises the frame into place, then draws the header row and an inside close button;
	// frame receives where the panel is drawn this frame, so the caller's content rises with it
	public static void open(GuiGraphics ctx, Font font, Rect frame, int screenWidth, int screenHeight, int width,
			int height, String title, @Nullable ItemStack icon, Rect close, long openedAt, int mouseX, int mouseY)
	{
		float t = Theme.easeOut((System.currentTimeMillis() - openedAt) / (float) Theme.MOTION_MODAL_MS);
		ctx.fill(0, 0, screenWidth, screenHeight, Theme.mix(Theme.SCRIM & 0x00FFFFFF, Theme.SCRIM, t));

		int w = Math.min(width, screenWidth - Theme.SPACE_L * 2);
		int h = Math.min(height, screenHeight - Theme.SPACE_L * 2);
		int x = (screenWidth - w) / 2;
		int y = (screenHeight - h) / 2 + Math.round(RISE * (1.0F - t));
		frame.set(x, y, w, h);
		frame(ctx, x, y, w, h, false);

		int titleX = x + Theme.SPACE_L;

		if (icon != null)
		{
			Theme.item(ctx, icon, titleX, y + HEADER_Y);
			titleX += Theme.ICON_M + ICON_GAP;
		}

		Theme.text(ctx, font, Theme.bold(title), titleX, y + HEADER_Y + (Theme.ICON_M - font.lineHeight) / 2 + 1, Theme.TEXT);
		close.set(x + w - Theme.ICON_M - Theme.SPACE_M, y + HEADER_Y, Theme.ICON_M, Theme.ICON_M);
		Buttons.close(ctx, close, mouseX, mouseY);
	}
}

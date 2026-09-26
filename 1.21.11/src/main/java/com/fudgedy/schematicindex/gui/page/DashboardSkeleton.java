package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class DashboardSkeleton
{
	private DashboardSkeleton()
	{
	}

	static void render(IndexScreen screen, GuiGraphics ctx, int x, int y, int w)
	{
		Font font = screen.font();
		int tileGap = 8;
		int tileW = (w - tileGap * 5) / 6;
		int tileH = 42;

		for (int i = 0; i < 6; i++)
		{
			skeletonRect(ctx, x + i * (tileW + tileGap), y, tileW, tileH);
		}

		// Matches the real graph height so the layout does not jump when the stats finish loading
		int gy = y + tileH + 14;
		skeletonRect(ctx, x, gy, w, 150);
		gy += 150 + Theme.SPACE_S;
		skeletonRect(ctx, x, gy, Math.min(w, 256), Theme.H_CONTROL);
		gy += Theme.H_CONTROL + 14;
		skeletonRect(ctx, x, gy, 64, font.lineHeight + 2);
		gy += font.lineHeight + 10;

		int gap = IndexScreen.GUTTER;
		int cols = Math.max(1, (w + gap) / (112 + gap));
		int cw = (w - gap * (cols - 1)) / cols;
		int ch = IndexScreen.imageHeight(cw) + IndexScreen.CAPTION_HEIGHT;
		int bottom = screen.height - 6;

		while (gy + ch <= bottom)
		{
			for (int col = 0; col < cols; col++)
			{
				skeletonRect(ctx, x + col * (cw + gap), gy, cw, ch);
			}

			gy += ch + gap;
		}
	}

	private static void skeletonRect(GuiGraphics ctx, int x, int y, int w, int h)
	{
		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.SKELETON);

		int period = 1400;
		float progress = (System.currentTimeMillis() % period) / (float) period;
		int shineX = x - 20 + Math.round(progress * (w + 40));

		ctx.enableScissor(x, y, x + w, y + h);

		for (int i = 0; i < 14; i++)
		{
			ctx.fill(shineX + i, y, shineX + i + 1, y + h, Theme.SKELETON_SHINE);
		}

		ctx.disableScissor();
	}
}

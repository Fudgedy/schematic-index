package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class Stars
{
	public static final String FULL = "★";

	// Unit star: outer radius 1, inner radius ~0.382, top point up. Filled at native GUI resolution
	// with supersampling, so it stays crisp at any on-screen size
	private static final float[] VX = new float[10];
	private static final float[] VY = new float[10];

	static
	{
		double innerRatio = 0.382D;
		for (int k = 0; k < 5; k++)
		{
			double outerAngle = Math.toRadians(-90 + 72 * k);
			VX[k * 2] = (float) Math.cos(outerAngle);
			VY[k * 2] = (float) Math.sin(outerAngle);
			double innerAngle = Math.toRadians(-90 + 36 + 72 * k);
			VX[k * 2 + 1] = (float) (Math.cos(innerAngle) * innerRatio);
			VY[k * 2 + 1] = (float) (Math.sin(innerAngle) * innerRatio);
		}
	}

	private Stars()
	{
	}

	public static int cellWidth(Font font, float scale)
	{
		return Math.round(font.width(FULL) * scale) + 2;
	}

	public static String format(double value)
	{
		double snapped = Math.round(value * 2.0) / 2.0;
		return snapped == Math.rint(snapped)
				? Integer.toString((int) snapped)
				: String.format(Locale.ROOT, "%.1f", snapped);
	}

	// halfStars is 0..10; a half-filled star is drawn clipped to its left half
	public static void draw(GuiGraphicsExtractor ctx, Font font, int x, int y, int halfStars, float scale,
			boolean interactive, int mouseX, int mouseY, @Nullable Rect[] rects)
			{
		int cell = cellWidth(font, scale);
		int glyphW = Math.round(font.width(FULL) * scale);
		int glyphH = Math.round(font.lineHeight * scale) + 2;

		for (int i = 0; i < 5; i++)
		{
			int sx = x + i * cell;

			if (rects != null)
			{
				rects[i].set(sx, y - 1, cell, glyphH);
			}

			boolean hovered = interactive && rects != null && rects[i].contains(mouseX, mouseY);
			int color = hovered ? 0xFFF6CF63 : Theme.STAT_STARS;
			int fill = Math.max(0, Math.min(2, halfStars - i * 2));

			float cx = sx + glyphW / 2.0F;
			float cy = y + glyphH / 2.0F;
			float outerR = glyphH / 2.0F - 1.0F;

			paint(ctx, cx, cy, outerR, 0xFF60656C);

			if (fill == 2)
			{
				paint(ctx, cx, cy, outerR, color);
			}
			else if (fill == 1)
			{
				ctx.enableScissor((int) Math.floor(cx - outerR - 1), y - 2, Math.round(cx), y + glyphH);
				paint(ctx, cx, cy, outerR, color);
				ctx.disableScissor();
			}
		}
	}

	// Each GUI pixel is supersampled 3x3 so the edges read smooth rather than jagged
	private static void paint(GuiGraphicsExtractor ctx, float cx, float cy, float outerR, int color)
	{
		int minX = (int) Math.floor(cx - outerR) - 1;
		int maxX = (int) Math.ceil(cx + outerR) + 1;
		int minY = (int) Math.floor(cy - outerR) - 1;
		int maxY = (int) Math.ceil(cy + outerR) + 1;

		int baseAlpha = (color >>> 24) & 0xFF;
		if (baseAlpha == 0)
		{
			baseAlpha = 0xFF; // colors supplied without an alpha byte are treated as opaque
		}
		int rgb = color & 0xFFFFFF;

		for (int py = minY; py <= maxY; py++)
		{
			for (int px = minX; px <= maxX; px++)
			{
				int covered = 0;
				for (int sy = 0; sy < 3; sy++)
				{
					for (int sx = 0; sx < 3; sx++)
					{
						float fx = px + (sx + 0.5F) / 3.0F;
						float fy = py + (sy + 0.5F) / 3.0F;
						if (inside((fx - cx) / outerR, (fy - cy) / outerR))
						{
							covered++;
						}
					}
				}

				if (covered == 0)
				{
					continue;
				}

				int alpha = baseAlpha * covered / 9;
				if (alpha <= 0)
				{
					continue;
				}
				ctx.fill(px, py, px + 1, py + 1, (alpha << 24) | rgb);
			}
		}
	}

	// Coordinates are already normalized by radius
	private static boolean inside(float nx, float ny)
	{
		boolean result = false;
		for (int i = 0, j = VX.length - 1; i < VX.length; j = i++)
		{
			float yi = VY[i];
			float yj = VY[j];
			if ((yi > ny) != (yj > ny))
			{
				float xCross = VX[i] + (ny - yi) / (yj - yi) * (VX[j] - VX[i]);
				if (nx < xCross)
				{
					result = !result;
				}
			}
		}
		return result;
	}
}

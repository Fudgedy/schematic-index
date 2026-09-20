package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// A saturation/value square with a hue bar. Drags move the HSV directly and only derive RGB from it,
// never the other way round, so a drag at zero saturation or value cannot snap the hue back to red
public final class ColorPicker
{
	private static final int DRAG_NONE = 0;
	private static final int DRAG_SQUARE = 1;
	private static final int DRAG_HUE = 2;
	private static final int HUE_BAR_SLACK = 4;

	private final Rect square = new Rect();
	private final Rect hueBar = new Rect();
	private float hue;
	private float sat = 1.0F;
	private float value = 1.0F;
	private int rgb = Theme.ACCENT & 0xFFFFFF;
	private int drag = DRAG_NONE;

	public int rgb()
	{
		return this.rgb;
	}

	// Loads the square and bar from an outside colour; a hex field must not call this while syncing
	// its own text from rgb(), or the 8-bit round trip would lose the hue mid-drag
	public void seed(int rgb)
	{
		this.rgb = rgb & 0xFFFFFF;
		float[] hsv = Cosmetics.rgbToHsv(this.rgb);
		this.hue = hsv[0];
		this.sat = hsv[1];
		this.value = hsv[2];
	}

	public void hide()
	{
		this.square.set(0, 0, 0, 0);
		this.hueBar.set(0, 0, 0, 0);
	}

	public boolean isDragging()
	{
		return this.drag != DRAG_NONE;
	}

	// Saturation rises left to right; value falls top to bottom via each strip's fade to black
	public void renderSquare(GuiGraphicsExtractor ctx, int x, int y, int size)
	{
		this.square.set(x, y, size, size);

		for (int sx = 0; sx < size; sx++)
		{
			int topColor = 0xFF000000 | Cosmetics.hsvToRgb(this.hue, (float) sx / (size - 1), 1.0F);
			ctx.fillGradient(x + sx, y, x + sx + 1, y + size, topColor, 0xFF000000);
		}

		Theme.roundedOutline(ctx, x, y, size, size, Theme.RADIUS_PILL, Theme.HAIRLINE);

		int dotX = x + Math.round(this.sat * (size - 1));
		int dotY = y + Math.round((1.0F - this.value) * (size - 1));
		Theme.roundedOutline(ctx, dotX - 4, dotY - 4, 8, 8, 4, 0xFF000000);
		Theme.roundedOutline(ctx, dotX - 3, dotY - 3, 6, 6, 3, 0xFFFFFFFF);
	}

	public void renderHueBar(GuiGraphicsExtractor ctx, int x, int y, int width, int height)
	{
		this.hueBar.set(x, y - HUE_BAR_SLACK, width, height + HUE_BAR_SLACK * 2);

		for (int sx = 0; sx < width; sx++)
		{
			ctx.fill(x + sx, y, x + sx + 1, y + height, 0xFF000000 | Cosmetics.hsvToRgb((float) sx / (width - 1), 1.0F, 1.0F));
		}

		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.HAIRLINE);

		int knobX = x + Math.round(this.hue * (width - 1));
		ctx.fill(knobX - 1, y - 1, knobX + 2, y + height + 1, 0xFFFFFFFF);
		ctx.fill(knobX, y, knobX + 1, y + height, 0xFF000000);
	}

	// A press inside either control starts a drag and already applies its position
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.square.contains(mouseX, mouseY))
		{
			this.drag = DRAG_SQUARE;
		}
		else if (this.hueBar.contains(mouseX, mouseY))
		{
			this.drag = DRAG_HUE;
		}
		else
		{
			return false;
		}

		return this.mouseDragged(mouseX, mouseY);
	}

	public boolean mouseDragged(double mouseX, double mouseY)
	{
		if (this.drag == DRAG_SQUARE)
		{
			this.sat = clamp01((mouseX - this.square.x) / Math.max(1, this.square.width - 1));
			this.value = 1.0F - clamp01((mouseY - this.square.y) / Math.max(1, this.square.height - 1));
		}
		else if (this.drag == DRAG_HUE)
		{
			this.hue = clamp01((mouseX - this.hueBar.x) / Math.max(1, this.hueBar.width - 1));
		}
		else
		{
			return false;
		}

		this.rgb = Cosmetics.hsvToRgb(this.hue, this.sat, this.value) & 0xFFFFFF;
		return true;
	}

	public boolean mouseReleased()
	{
		if (this.drag == DRAG_NONE)
		{
			return false;
		}

		this.drag = DRAG_NONE;
		return true;
	}

	private static float clamp01(double value)
	{
		return (float) Math.max(0.0, Math.min(1.0, value));
	}
}

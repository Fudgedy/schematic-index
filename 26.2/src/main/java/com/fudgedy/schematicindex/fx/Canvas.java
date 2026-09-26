package com.fudgedy.schematicindex.fx;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.Arrays;

// A transparent frame the effects paint into: TOP rows above the text line, BOTTOM rows below it. Paint is
// composited with straight alpha, so the frame laid over any backdrop reads the same as the lab drawn onto it
public final class Canvas
{
	public static final int TOP = 7;
	public static final int BOTTOM = 8;
	public static final int HEIGHT = TOP + NameMask.ROWS + BOTTOM;
	// A name inline in chat or the tab list keeps only the text line and the row its shadow falls on
	public static final int INLINE_HEIGHT = NameMask.ROWS + 1;

	public final int width;
	private final float[] red;
	private final float[] green;
	private final float[] blue;
	private final float[] alpha;
	private final float[] pixel = new float[3];

	public Canvas(int width)
	{
		this.width = width;
		this.red = new float[width * HEIGHT];
		this.green = new float[width * HEIGHT];
		this.blue = new float[width * HEIGHT];
		this.alpha = new float[width * HEIGHT];
	}

	public void set(double x, double y, float[] c)
	{
		this.set(x, y, c, 1.0F);
	}

	public void set(double x, double y, float[] c, float a)
	{
		int px = (int) Math.round(x);
		int py = (int) Math.round(y);

		if (px < 0 || px >= this.width || py < -TOP || py >= NameMask.ROWS + BOTTOM || a <= 0.0F)
		{
			return;
		}

		int i = (py + TOP) * this.width + px;
		float under = this.alpha[i] * (1.0F - a);
		float out = a + under;
		this.red[i] = (c[0] * a + this.red[i] * under) / out;
		this.green[i] = (c[1] * a + this.green[i] * under) / out;
		this.blue[i] = (c[2] * a + this.blue[i] * under) / out;
		this.alpha[i] = out;
	}

	// After every layer has painted, so each kind still draws its own palette and the tint follows its shading
	void tint(NameTint tint)
	{
		for (int i = 0; i < this.alpha.length; i++)
		{
			if (this.alpha[i] <= 0.0F)
			{
				continue;
			}

			this.pixel[0] = this.red[i];
			this.pixel[1] = this.green[i];
			this.pixel[2] = this.blue[i];
			tint.apply(i % this.width, this.pixel);
			this.red[i] = this.pixel[0];
			this.green[i] = this.pixel[1];
			this.blue[i] = this.pixel[2];
		}
	}

	public void clear()
	{
		Arrays.fill(this.alpha, 0.0F);
	}

	public void writeTo(NativeImage image)
	{
		for (int y = 0; y < HEIGHT; y++)
		{
			for (int x = 0; x < this.width; x++)
			{
				image.setPixel(x, y, this.argb((y * this.width) + x));
			}
		}
	}

	// Vanilla text casts a shadow one pixel down and right at a quarter of the ink's brightness; chat and the
	// tab list expect it, so the inline frame bakes it under the name
	public void writeInlineTo(NativeImage image, NameMask mask)
	{
		for (int y = 0; y < INLINE_HEIGHT; y++)
		{
			for (int x = 0; x < this.width; x++)
			{
				int i = (y + TOP) * this.width + x;
				int top = this.argb(i);

				if (!mask.isInk(x - 1, y - 1))
				{
					image.setPixel(x, y, top);
					continue;
				}

				int s = (y - 1 + TOP) * this.width + x - 1;
				float a = this.alpha[i];
				float under = 1.0F - a;
				int r = Math.round(this.red[i] * a + this.red[s] * 0.25F * under);
				int g = Math.round(this.green[i] * a + this.green[s] * 0.25F * under);
				int b = Math.round(this.blue[i] * a + this.blue[s] * 0.25F * under);
				image.setPixel(x, y, 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
			}
		}
	}

	private int argb(int i)
	{
		float a = this.alpha[i];

		if (a <= 0.0F)
		{
			return 0;
		}

		return clamp(Math.round(a * 255.0F)) << 24 | clamp(Math.round(this.red[i])) << 16
				| clamp(Math.round(this.green[i])) << 8 | clamp(Math.round(this.blue[i]));
	}

	private static int clamp(int v)
	{
		return Math.max(0, Math.min(255, v));
	}
}

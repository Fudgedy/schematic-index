package com.fudgedy.schematicindex.fx;

import org.jetbrains.annotations.Nullable;

// The effects that only recolour the name: Shiny, Colour Flow, Rainbow Wave and Pulse
final class ColourKinds
{
	// Light grey rather than white so the sweep still reads on an uncoloured name
	static final int SHINE_BASE = 0xD8DEE3;

	private ColourKinds()
	{
	}

	// A slanted white band crosses the whole name, then rests
	static final class Shine implements Kind
	{
		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			double period = params.num("periodMs", 4000);
			double sweep = params.num("sweepMs", 2500);
			double slant = params.num("slant", 0.6);
			float[] base = params.color("base", SHINE_BASE);
			float[] glint = params.color("glint", 0xFFFFFF);
			float edge = (float) params.num("edge", 0.55);
			double p = Math.min(1.0, (now % (long) period) / sweep);
			double d = -8 + p * (mask.width + 16);

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (!mask.isInk(x, y))
					{
						continue;
					}

					float[] colour = Kind.worn(mask, stops, x, base);
					double v = (x + y * slant) - d;

					if (v > -1.2 && v <= 0.2)
					{
						canvas.set(x, y, glint);
					}
					else if ((v > -2.6 && v <= -1.2) || (v > 0.2 && v <= 1.4))
					{
						canvas.set(x, y, FxMath.lerp(colour, glint, edge));
					}
					else
					{
						canvas.set(x, y, colour);
					}
				}
			}
		}
	}

	// The worn colours as a ring sliding along the name, so the band loops without a seam
	static final class Flow implements Kind
	{
		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			float[][] ring = stops != null ? stops : params.colors("fallback", 0x7FD4FF, 0xB38EF3, 0xFF7EB6);
			double period = params.num("periodMs", 3000);
			double scale = params.num("scale", 0.9);

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (mask.isInk(x, y))
					{
						canvas.set(x, y, FxMath.ringRamp(ring, (float) (x / (mask.width * scale) - now / period)));
					}
				}
			}
		}
	}

	// Its own hue wheel, slanted across the letters and rolling
	static final class Wave implements Kind
	{
		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			double period = params.num("periodMs", 3000);
			double slant = params.num("slant", 0.7);
			double scale = params.num("scale", 0.8);
			float saturation = (float) params.num("saturation", 0.62);
			float value = (float) params.num("value", 1.0);

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (mask.isInk(x, y))
					{
						double hue = (((x + y * slant) / (mask.width * scale) - now / period) % 1 + 1) % 1;
						canvas.set(x, y, FxMath.hsv((float) hue, saturation, value));
					}
				}
			}
		}
	}

	// The whole name sinks dark together, then swells back; the dip is held a little longer than the peak
	static final class Pulse implements Kind
	{
		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			double period = params.num("periodMs", 2000);
			float[] base = params.color("base", SHINE_BASE);
			float darkness = (float) params.num("darkness", 0.28);
			float ring = (float) params.num("ring", 0.28);
			double c = 0.5 - 0.5 * Math.cos(2 * Math.PI * now / period);
			float k = (float) Math.pow(c, params.num("curve", 0.8));

			for (int[] p : mask.ring())
			{
				canvas.set(p[0], p[1], Kind.worn(mask, stops, p[0], base), ring * (1.0F - k));
			}

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (mask.isInk(x, y))
					{
						float[] colour = Kind.worn(mask, stops, x, base);
						canvas.set(x, y, FxMath.lerp(colour, FxMath.scaled(colour, darkness, darkness, darkness + 0.02F), k));
					}
				}
			}
		}
	}
}

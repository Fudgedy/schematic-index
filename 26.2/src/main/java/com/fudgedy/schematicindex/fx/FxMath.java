package com.fudgedy.schematicindex.fx;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

// Colours are {r, g, b} floats in 0..255 so blends match the preview lab exactly before the final round
public final class FxMath
{
	private FxMath()
	{
	}

	public static float[] rgb(int hex)
	{
		return new float[] {(hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF};
	}

	public static float[] lerp(float[] a, float[] b, float t)
	{
		return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
	}

	public static float[] scaled(float[] c, float r, float g, float b)
	{
		return new float[] {c[0] * r, c[1] * g, c[2] * b};
	}

	public static float clamp01(float t)
	{
		return Math.max(0.0F, Math.min(1.0F, t));
	}

	public static float[] ramp(float[][] stops, float t)
	{
		if (stops.length == 1)
		{
			return stops[0];
		}

		t = clamp01(t);
		int n = stops.length - 1;
		int i = Math.min((int) Math.floor(t * n), n - 1);
		return lerp(stops[i], stops[i + 1], t * n - i);
	}

	// The stops as a ring, last blending back into first, so a sliding offset never shows a seam
	public static float[] ringRamp(float[][] stops, float t)
	{
		if (stops.length == 1)
		{
			return stops[0];
		}

		t = ((t % 1.0F) + 1.0F) % 1.0F;
		int n = stops.length;
		float f = t * n;
		int i = (int) Math.floor(f) % n;
		return lerp(stops[i], stops[(i + 1) % n], f - (float) Math.floor(f));
	}

	public static float[] hsv(float h, float s, float v)
	{
		int i = (int) Math.floor(h * 6.0F);
		float f = h * 6.0F - i;
		float p = v * (1.0F - s);
		float q = v * (1.0F - f * s);
		float t = v * (1.0F - (1.0F - f) * s);
		float[] c = switch (((i % 6) + 6) % 6)
		{
			case 0 -> new float[] {v, t, p};
			case 1 -> new float[] {q, v, p};
			case 2 -> new float[] {p, v, t};
			case 3 -> new float[] {p, q, v};
			case 4 -> new float[] {t, p, v};
			default -> new float[] {v, p, q};
		};
		return new float[] {c[0] * 255.0F, c[1] * 255.0F, c[2] * 255.0F};
	}

	// The same layout for a name on every client: a CRC seed stepped by the lab's LCG, in doubles as the lab ran it
	public static Rng rng(String name, String salt)
	{
		CRC32 crc = new CRC32();
		crc.update((name + "|" + salt).getBytes(StandardCharsets.UTF_8));
		return new Rng((int) crc.getValue());
	}

	// One to three gathering spots along [x0, x1], so features clump instead of repeating per letter
	public static double[] clusters(Rng r, int x0, int x1, double perPixel)
	{
		int k = (int) Math.max(1, Math.min(3, Math.round((x1 - x0) / perPixel)));
		double segment = (x1 - x0) / (double) k;
		double[] out = new double[k];

		for (int i = 0; i < k; i++)
		{
			out[i] = x0 + segment * (i + 0.2 + r.next() * 0.6);
		}

		return out;
	}

	public static double weight(double x, double[] centres, double spread)
	{
		double best = 0.0;

		for (double centre : centres)
		{
			double d = (x - centre) / spread;
			best = Math.max(best, Math.exp(-(d * d)));
		}

		return best;
	}

	public static final class Rng
	{
		private int state;

		private Rng(int seed)
		{
			this.state = seed;
		}

		public double next()
		{
			this.state = (this.state * 1103515245 + 12345) & 0x7FFFFFFF;
			return this.state / (double) 0x7FFFFFFF;
		}
	}
}

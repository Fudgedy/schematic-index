package com.fudgedy.schematicindex.fx;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The electric effects: Neon and Glitch
final class SignKinds
{
	private static final int NEON_DEFAULT = 0xFF3FD8;
	private static final int[][] GLITCH_PAIRS = {{0xFF2A6D, 0x05D9E8}, {0xF9F002, 0xB026FF}, {0xFF2A6D, 0xF9F002},
			{0x05D9E8, 0xB026FF}, {0xFF2448, 0x2F6BFF}};
	private static final int[][] STUTTER = {{900, 960}, {1030, 1070}, {1110, 1330}, {1380, 1420}};

	private SignKinds()
	{
	}

	// A lit tube: its own-colour bloom, a current riding along it, and one IGN-picked letter that stutters out
	static final class Neon implements Kind
	{
		private record Layout(int bad, double offset)
		{
		}

		@Override
		public Object prepare(NameMask mask, Params params)
		{
			FxMath.Rng r = FxMath.rng(mask.name, "neon");
			return new Layout((int) Math.floor(r.next() * mask.spans.length), r.next() * params.num("cycleMs", 4000));
		}

		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			Layout layout = (Layout) state;
			double cycle = params.num("cycleMs", 4000);
			double t = (now + layout.offset()) % cycle;
			float[] fallback = params.color("color", NEON_DEFAULT);
			boolean stutter = false;

			for (int[] window : STUTTER)
			{
				stutter |= t >= window[0] && t < window[1];
			}

			int s0 = mask.spans[layout.bad()][0];
			int s1 = mask.spans[layout.bad()][1];
			double hum = 0.9 + 0.1 * Math.sin(now / 37.0) * Math.sin(now / 91.0);
			double current = params.num("currentMs", 1600);
			double head = ((now % (long) current) / current) * (mask.width + 12) - 6;
			float bloom = (float) params.num("bloom", 0.36);

			for (int[] p : mask.band())
			{
				int x = p[0];
				double g = !stutter ? 1 : Math.min(1, Math.max(0, (x < s0 ? s0 - x : x > s1 ? x - s1 : 0) / 3.0));

				if (g <= 0)
				{
					continue;
				}

				double d = p[2] / 2.0;
				double near = Math.max(0, 1 - (d - 1) / 3.5);
				double boost = Math.max(0, 1 - Math.abs(x - head) / 6);
				double alpha = g * Math.min(0.6, (0.05 + bloom * near * near) * hum + 0.12 * boost * near);
				canvas.set(x, p[1], saturated(Kind.worn(mask, stops, x, fallback)), (float) alpha);
			}

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (!mask.isInk(x, y))
					{
						continue;
					}

					float[] tube = Kind.worn(mask, stops, x, fallback);

					if (stutter && x >= s0 && x <= s1)
					{
						canvas.set(x, y, FxMath.scaled(tube, 0.22F, 0.22F, 0.22F));
						continue;
					}

					double surge = Math.max(0, 1 - Math.abs(x - head) / 4);
					canvas.set(x, y, FxMath.lerp(tube, saturated(tube), (float) (0.5 + 0.5 * surge)));
				}
			}
		}

		// The glow is the name's own colour at full strength, never white and never a second hue
		private static float[] saturated(float[] c)
		{
			float m = Math.max(1.0F, Math.max(c[0], Math.max(c[1], c[2])));
			return new float[] {c[0] * 255.0F / m, c[1] * 255.0F / m, c[2] * 255.0F / m};
		}
	}

	// Quiet, then two IGN-timed bursts per cycle where the name tears and neon ghosts split out behind it
	static final class Glitch implements Kind
	{
		private record Layout(double[][] bursts)
		{
		}

		@Override
		public Object prepare(NameMask mask, Params params)
		{
			FxMath.Rng r = FxMath.rng(mask.name, "glitch");
			double at = 300 + r.next() * 1800;
			double big = 200 + r.next() * 120;
			double after = at + big + 700 + r.next() * 1400;
			double small = 60 + r.next() * 80;
			return new Layout(new double[][] {{at, at + big, 1}, {after, after + small, 0.5}});
		}

		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			Layout layout = (Layout) state;
			double t = now % (long) params.num("cycleMs", 5000);
			float[] fallback = params.color("color", 0xF2F4F5);
			double[] hit = null;

			for (double[] burst : layout.bursts())
			{
				if (t >= burst[0] && t < burst[1])
				{
					hit = burst;
					break;
				}
			}

			if (hit == null)
			{
				for (int y = 0; y < NameMask.ROWS; y++)
				{
					for (int x = 0; x < mask.width; x++)
					{
						if (mask.isInk(x, y))
						{
							canvas.set(x, y, Kind.worn(mask, stops, x, fallback));
						}
					}
				}

				return;
			}

			// A fresh pose every 50 ms tick, the same for every viewer of this name
			long step = now / (long) params.num("stepMs", 50);
			FxMath.Rng rr = FxMath.rng(mask.name, "g" + step);
			double strong = hit[2];
			int split = 1 + (rr.next() < 0.4 * strong ? 1 : 0);
			int lift = rr.next() < 0.3 ? (rr.next() < 0.5 ? -1 : 1) : 0;
			List<int[]> bands = new ArrayList<>();
			int count = 1 + (rr.next() < strong ? 1 : 0);

			for (int i = 0; i < count; i++)
			{
				int y0 = (int) Math.floor(rr.next() * 7);
				int h = 1 + (int) Math.floor(rr.next() * 3);
				int sign = rr.next() < 0.5 ? -1 : 1;
				int shift = sign * (1 + (int) Math.floor(rr.next() * (1 + 2 * strong)));
				bands.add(new int[] {y0, y0 + h, shift});
			}

			int drop = rr.next() < 0.35 * strong ? (int) Math.floor(rr.next() * mask.spans.length) : -1;
			float[][][] pairs = params.pairs("pairs", GLITCH_PAIRS);
			float[][] pair = pairs[(int) Math.floor(rr.next() * pairs.length)];
			boolean[] torn = new boolean[(mask.width + 6) * NameMask.ROWS];

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = -3; x < mask.width + 3; x++)
				{
					torn[y * (mask.width + 6) + x + 3] = tornInk(mask, bands, drop, x, y);
				}
			}

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = -3; x < mask.width + 3; x++)
				{
					if (torn[y * (mask.width + 6) + x + 3])
					{
						canvas.set(x - split, y + lift, pair[0], 0.9F);
						canvas.set(x + split, y - lift, pair[1], 0.9F);
					}
				}
			}

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = -3; x < mask.width + 3; x++)
				{
					if (torn[y * (mask.width + 6) + x + 3])
					{
						canvas.set(x, y, Kind.worn(mask, stops, x, fallback));
					}
				}
			}
		}

		private static boolean tornInk(NameMask mask, List<int[]> bands, int drop, int x, int y)
		{
			if (drop >= 0 && x >= mask.spans[drop][0] && x <= mask.spans[drop][1] && y % 2 == 0)
			{
				return false;
			}

			int shift = 0;

			for (int[] band : bands)
			{
				if (y >= band[0] && y < band[1])
				{
					shift = band[2];
					break;
				}
			}

			return mask.isInk(x - shift, y);
		}
	}
}

package com.fudgedy.schematicindex.fx;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The effects that dress the name in a material: Frozen, Molten and Toxic. Each lays its pieces where this IGN's
// letters sit, gathered into name-seeded clusters rather than one per letter
final class DressKinds
{
	private static final int[] ICE_BODY = {0xD6F1FF, 0xA5DCFF, 0x6FC0F5, 0x3F97D6};
	private static final int[] LAVA = {0x4C1908, 0x9A2E06, 0xE0540F, 0xFF9A1F, 0xFFD84A, 0xFFF4B0};
	private static final int[] TOX = {0x1C3A12, 0x2F6A1C, 0x4FA82A, 0x8CE04A, 0xC8FF7A};
	private static final int[][] CROSS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int[][] SMALL_BUBBLE = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final int[][] BIG_BUBBLE = {{0, -1}, {1, -1}, {2, 0}, {2, 1}, {1, 2}, {0, 2}, {-1, 1}, {-1, 0}};
	private static final int[][] POP = {{1, -1}, {-1, -1}, {1, 1}, {-1, 1}};
	private static final int[] TWINKLE = {1, 2, 3, 3, 2, 1};
	private static final int[][] FAR_ARMS = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
	private static final int FRAMES = 32;

	private DressKinds()
	{
	}

	private static int frame(long now, Params params)
	{
		return (int) ((now / (long) params.num("stepMs", 100)) % FRAMES);
	}

	// The ink columns that reach the baseline, where a drip can hang; every column should none reach it
	private static int[] baseline(NameMask mask)
	{
		List<Integer> low = new ArrayList<>();

		for (int x : mask.cols)
		{
			if (mask.bottom[x] >= 6)
			{
				low.add(x);
			}
		}

		if (low.isEmpty())
		{
			return mask.cols;
		}

		int[] out = new int[low.size()];

		for (int i = 0; i < out.length; i++)
		{
			out[i] = low.get(i);
		}

		return out;
	}

	// Letters turned to ice, snow on their flat tops, one ice ledge under the whole name, icicles clumped by the IGN
	static final class Frozen implements Kind
	{
		private static final int SHELF = 8;

		private record Layout(int x0, int x1, int[] sites, int[] lengths, Set<Integer> thick, int drip, int twinkle, int[] sides,
				int[][] stars)
		{
		}

		@Override
		public Object prepare(NameMask mask, Params params)
		{
			FxMath.Rng r = FxMath.rng(mask.name, "ice");
			int[] c = mask.cols;
			int x0 = c[0];
			int x1 = c[c.length - 1];
			double[] centres = FxMath.clusters(r, x0, x1, 26);
			List<Integer> sites = new ArrayList<>();
			int last = -9;

			for (int x = x0 + 1; x < x1; x++)
			{
				double w = FxMath.weight(x, centres, 6);

				if (x - last >= 2 && r.next() < 0.15 + 0.6 * w)
				{
					sites.add(x);
					last = x;
				}
			}

			int[] at = new int[sites.size()];
			int[] lengths = new int[sites.size()];

			for (int i = 0; i < at.length; i++)
			{
				at[i] = sites.get(i);
				lengths[i] = (int) Math.max(1, Math.min(5, Math.round(0.5 + 4 * FxMath.weight(at[i], centres, 5) + r.next() * 1.2)));
			}

			Set<Integer> thick = new HashSet<>();

			for (int x = x0; x <= x1; x++)
			{
				if (r.next() < 0.25)
				{
					thick.add(x);
				}
			}

			int drip = -1;

			for (int i = 0; i < at.length; i++)
			{
				if (drip < 0 || lengths[i] > lengths[drip])
				{
					drip = i;
				}
			}

			int twinkle = c[(int) Math.floor(r.next() * c.length)];
			// Drawn after the layout picks so every icicle keeps its place: which side each thick root leans to
			int[] sides = new int[at.length];

			for (int i = 0; i < sides.length; i++)
			{
				sides[i] = r.next() < 0.5 ? -1 : 1;
			}

			// Stars sit on a snow cap, on the ledge or at an icicle tip, each on its own beat so they never flash together
			int count = Math.max(2, (int) Math.round(c.length / params.num("starEvery", 10)));
			int[][] stars = new int[count][];

			for (int i = 0; i < count; i++)
			{
				int kind = at.length > 0 ? (int) Math.floor(r.next() * 3) : (int) Math.floor(r.next() * 2);
				int x = kind == 2 ? at[(int) Math.floor(r.next() * at.length)] : c[(int) Math.floor(r.next() * c.length)];
				stars[i] = new int[] {kind, x, (int) Math.floor(r.next() * FRAMES), kind == 2 ? indexOf(at, x) : -1};
			}

			return new Layout(x0, x1, at, lengths, thick, drip, twinkle, sides, stars);
		}

		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			Layout layout = (Layout) state;
			int k = frame(now, params);
			float[][] body = params.colors("body", ICE_BODY);
			float[] white = FxMath.rgb(0xFFFFFF);
			int glintEvery = (int) params.num("glintEvery", 5);
			float glint = (float) params.num("glint", 0.45);
			float keep = (float) params.num("keepColours", 0.2);
			int snowRows = (int) params.num("snowRows", 2);

			// The letters are ice themselves, white to a deeper blue down the stroke, with faint diagonal glints so
			// the surface reads frozen rather than flat
			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (mask.isInk(x, y))
					{
						float[] frost = FxMath.ramp(body, y / 7.0F);

						if (glintEvery > 0 && (x + y) % glintEvery == 0)
						{
							frost = FxMath.lerp(frost, white, glint);
						}

						canvas.set(x, y, stops != null ? FxMath.lerp(FxMath.ramp(stops, mask.across(x)), frost, 1.0F - keep) : frost);
					}
				}
			}

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (isSnowCap(mask, x, y, snowRows))
					{
						canvas.set(x, y, white);
					}
				}
			}

			// The ledge wears the letters' palette: pale ice with the same diagonal glints, light blue where it thickens
			for (int x = layout.x0(); x <= layout.x1(); x++)
			{
				canvas.set(x, SHELF, (x + SHELF) % 5 == 0 ? white : body[0]);

				if (layout.thick().contains(x))
				{
					canvas.set(x, SHELF + 1, body[1]);
				}
			}

			// Icicles shade light blue at the root to deep ice at the tip; long ones taper from two pixels wide and
			// catch a white highlight just under the ledge
			float[][] icicle = {body[1], body[2], body[3]};

			for (int i = 0; i < layout.sites().length; i++)
			{
				int x = layout.sites()[i];
				int n = layout.lengths()[i];

				for (int j = 0; j < n; j++)
				{
					canvas.set(x, SHELF + 1 + j, FxMath.ramp(icicle, n == 1 ? 0.5F : j / (float) (n - 1)));
				}

				if (n >= 3)
				{
					canvas.set(x + layout.sides()[i], SHELF + 1, body[1]);
				}

				if (n >= 5)
				{
					canvas.set(x + layout.sides()[i], SHELF + 2, body[2]);
				}

				if (n >= 2)
				{
					canvas.set(x, SHELF + 1, FxMath.lerp(body[1], white, 0.5F));
				}
			}

			if (layout.drip() >= 0)
			{
				int x = layout.sites()[layout.drip()];
				int tip = SHELF + 1 + layout.lengths()[layout.drip()];

				if (k >= 4 && k < 12)
				{
					canvas.set(x, tip, body[2], (k - 4) / 8.0F);
				}
				else if (k >= 12 && k < 17)
				{
					canvas.set(x, tip + (k - 12), body[1]);
				}
			}

			if (k >= 20 && k < 26)
			{
				star(canvas, layout.twinkle(), mask.top[layout.twinkle()] - 2, TWINKLE[k - 20], white, body);
			}

			for (int[] star : layout.stars())
			{
				int ph = (k + star[2]) % FRAMES;

				if (ph >= TWINKLE.length)
				{
					continue;
				}

				int x = star[1];
				int y = star[0] == 0 ? mask.top[x] - 1 : star[0] == 1 ? SHELF : SHELF + 1 + layout.lengths()[star[3]];
				star(canvas, x, y, TWINKLE[ph], white, body);
			}
		}

		private static void star(Canvas canvas, int x, int y, int size, float[] white, float[][] body)
		{
			if (size >= 1)
			{
				canvas.set(x, y, white);
			}

			if (size >= 2)
			{
				for (int[] d : CROSS)
				{
					canvas.set(x + d[0], y + d[1], body[0]);
				}
			}

			if (size >= 3)
			{
				for (int[] d : FAR_ARMS)
				{
					canvas.set(x + d[0], y + d[1], body[1], 0.7F);
				}
			}
		}

		private static int indexOf(int[] values, int value)
		{
			for (int i = 0; i < values.length; i++)
			{
				if (values[i] == value)
				{
					return i;
				}
			}

			return -1;
		}

		// Snow caps only each letter's very top edge, so the ice underneath still shows
		private static boolean isSnowCap(NameMask mask, int x, int y, int snowRows)
		{
			return y == mask.top[x] && y <= snowRows
					&& ((x > 0 && mask.top[x - 1] == y) || (x + 1 < mask.width && mask.top[x + 1] == y));
		}
	}

	// Heat rising through the whole word, lava gathering into name-seeded drips, embers lifting off the tops
	static final class Molten implements Kind
	{
		private record Layout(double[][] drips, double[][] embers)
		{
		}

		@Override
		public Object prepare(NameMask mask, Params params)
		{
			FxMath.Rng r = FxMath.rng(mask.name, "lava");
			int[] base = baseline(mask);
			double[] centres = FxMath.clusters(r, base[0], base[base.length - 1], 30);
			List<double[]> drips = new ArrayList<>();
			int last = -9;

			for (int x : base)
			{
				double w = FxMath.weight(x, centres, 5);

				if (x - last >= 3 && r.next() < 0.08 + 0.55 * w)
				{
					double offset = r.next() * FRAMES;
					drips.add(new double[] {x, offset, 1 + Math.round(3 * w + r.next())});
					last = x;
				}
			}

			int count = Math.max(3, mask.name.length() * 2 / 3);
			double[][] embers = new double[count][];

			for (int i = 0; i < count; i++)
			{
				int x = mask.cols[(int) Math.floor(r.next() * mask.cols.length)];
				double offset = r.next() * FRAMES;
				embers[i] = new double[] {x, offset, r.next()};
			}

			return new Layout(drips.toArray(new double[0][]), embers);
		}

		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			Layout layout = (Layout) state;
			int k = frame(now, params);
			float[][] lava = params.colors("lava", LAVA);
			float[][] heat = {lava[1], lava[2], lava[3], lava[4], lava[5]};
			float keep = (float) params.num("keepColours", 0.25);

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (mask.isInk(x, y))
					{
						double h = y / 7.0 + 0.12 * Math.sin(x * 0.5 + k * 2 * Math.PI / FRAMES);
						float[] c = FxMath.ramp(heat, (float) (1 - h * 0.9));
						canvas.set(x, y, stops != null ? FxMath.lerp(FxMath.ramp(stops, mask.across(x)), c, 1.0F - keep) : c);
					}
				}
			}

			for (double[] drip : layout.drips())
			{
				int x = (int) drip[0];
				int length = (int) drip[2];
				int bottom = mask.bottom[x];
				double ph = (k + drip[1]) % FRAMES;
				int grow = Math.min(length, (int) Math.floor(ph / 4));

				for (int j = 0; j < grow; j++)
				{
					canvas.set(x, bottom + 1 + j, j == 0 ? lava[3] : lava[2]);
				}

				if (ph >= 4 * length)
				{
					int fall = (int) Math.floor(ph - 4 * length);

					if (fall < 6)
					{
						canvas.set(x, bottom + 1 + length + fall, fall < 3 ? lava[3] : lava[1]);
					}
				}
			}

			for (double[] ember : layout.embers())
			{
				int x = (int) ember[0];
				double ph = (k + ember[1]) % FRAMES;

				if (ph < 12)
				{
					double dx = Math.round(Math.sin(ph * 0.5 + ember[2] * 6));
					canvas.set(x + dx, mask.top[x] - 1 - Math.floor(ph * 0.7), ph < 3 ? lava[5] : ph < 7 ? lava[4] : lava[2]);
				}
			}
		}
	}

	// Goo on the letter tops, sagging bumps that narrow to a dripping point, bubbles rising off the name and
	// popping; every goo pixel takes the letters' own shades so the whole effect reads as one colour
	static final class Toxic implements Kind
	{
		private record Layout(double[][] bubbles, int[][] drips, Set<Integer> pool)
		{
		}

		@Override
		public Object prepare(NameMask mask, Params params)
		{
			FxMath.Rng r = FxMath.rng(mask.name, "tox");
			int[] c = mask.cols;
			int count = Math.max(3, mask.name.length() * 2 / 3);
			double[][] bubbles = new double[count][];

			for (int i = 0; i < count; i++)
			{
				int x = c[(int) Math.floor(r.next() * c.length)];
				double offset = r.next() * FRAMES;
				bubbles[i] = new double[] {x, offset, 2 + Math.floor(r.next() * 2)};
			}

			int[] base = baseline(mask);
			double[] centres = FxMath.clusters(r, base[0], base[base.length - 1], 34);
			List<Integer> sites = new ArrayList<>();
			int last = -9;

			for (int x : base)
			{
				if (x - last >= 4 && r.next() < 0.05 + 0.5 * FxMath.weight(x, centres, 4))
				{
					sites.add(x);
					last = x;
				}
			}

			// Drawn after the picks above so bubbles and drip spots keep their places: each drip's reach and beat
			int[][] drips = new int[sites.size()][];

			for (int i = 0; i < drips.length; i++)
			{
				int reach = 1 + (int) Math.floor(r.next() * 2);
				drips[i] = new int[] {sites.get(i), reach, (int) Math.floor(r.next() * FRAMES)};
			}

			Set<Integer> pool = new HashSet<>();

			for (int x : base)
			{
				if (FxMath.weight(x, centres, 5) > params.num("pool", 0.8))
				{
					pool.add(x);
				}
			}

			return new Layout(bubbles, drips, pool);
		}

		@Override
		public void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params)
		{
			Layout layout = (Layout) state;
			int k = frame(now, params);
			float[][] tox = params.colors("goo", TOX);
			float[] light = tox[4];
			float[] mid = tox[3];
			float[] shade = tox[2];
			float[][] body = {mid, mid, shade};
			float keep = (float) params.num("keepColours", 0.3);
			int sag = (int) params.num("sag", 1);
			Tint tint = (x, colour) -> stops != null ? FxMath.lerp(FxMath.ramp(stops, mask.across(x)), colour, 1.0F - keep) : colour;

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (mask.isInk(x, y))
					{
						canvas.set(x, y, tint.of(x, FxMath.ramp(body, y / 7.0F)));
					}
				}
			}

			for (int y = 0; y < NameMask.ROWS; y++)
			{
				for (int x = 0; x < mask.width; x++)
				{
					if (mask.isInk(x, y) && !mask.isInk(x, y - 1))
					{
						canvas.set(x, y, tint.of(x, light));
					}
				}
			}

			// Goo pools under the letters where the drips gather, so they grow out of it rather than hang like pipes
			for (int x : layout.pool())
			{
				canvas.set(x, mask.bottom[x] + 1, tint.of(x, shade));
			}

			// Each drip sags as a small bump narrowing 3 then 1 wide, lit left and shaded right; a droplet swells
			// at the point, drops and falls, and the point pulls back into the bump
			for (int[] drip : layout.drips())
			{
				int x = drip[0];
				int ph = (k + drip[2]) % FRAMES;
				int root = mask.bottom[x] + 1;
				band(canvas, tint, x, root, sag, light, mid, shade);
				band(canvas, tint, x, root + 1, sag - 1, light, mid, shade);

				if (ph >= 8 && ph < 24)
				{
					canvas.set(x, root + 2, tint.of(x, mid));
				}

				if (ph >= 12 && ph < 16)
				{
					canvas.set(x, root + 3, tint.of(x, ph < 14 ? mid : light));
				}

				if (ph >= 16 && ph < 18)
				{
					canvas.set(x, root + 3, tint.of(x, light));
					canvas.set(x, root + 4, tint.of(x, mid));
				}

				if (ph >= 18 && ph < 26)
				{
					int fall = root + 4 + (ph - 18);
					canvas.set(x, fall, tint.of(x, light));
					canvas.set(x, fall + 1, tint.of(x, mid));
				}
			}

			for (double[] bubble : layout.bubbles())
			{
				int x = (int) bubble[0];
				double ph = (k + bubble[1]) % FRAMES;

				if (ph >= 14)
				{
					continue;
				}

				double y = mask.top[x] - 3 - Math.floor(ph * 0.6);

				if (Math.floor(ph) == 13)
				{
					for (int[] d : POP)
					{
						canvas.set(x + d[0], y + d[1], tint.of(x, mid));
					}

					continue;
				}

				int size = (int) Math.min(bubble[2], 1 + Math.floor(ph / 4));

				if (size == 1)
				{
					canvas.set(x, y, tint.of(x, mid));
				}
				else if (size == 2)
				{
					for (int[] d : SMALL_BUBBLE)
					{
						canvas.set(x + d[0], y + d[1], tint.of(x, mid));
					}
				}
				else
				{
					for (int[] d : BIG_BUBBLE)
					{
						canvas.set(x + d[0], y + d[1], tint.of(x, mid));
					}

					canvas.set(x, y - 1, tint.of(x, light));
				}
			}
		}

		private static void band(Canvas canvas, Tint tint, int x, int y, int half, float[] light, float[] mid, float[] shade)
		{
			for (int dx = -half; dx <= half; dx++)
			{
				canvas.set(x + dx, y, tint.of(x + dx, dx < 0 ? light : dx > 0 ? shade : mid));
			}
		}
	}

	// The wearer's colours blended under a material, sampled where the pixel sits along the name
	private interface Tint
	{
		float[] of(int x, float[] colour);
	}
}

package com.fudgedy.schematicindex.mapart;

import java.util.Arrays;

// Nearest-colour matching against the enabled map colours, with error diffusion or ordered
// dithering; height assignment is handed to MapartStaircase afterwards
public final class MapartConverter
{
	// Below this alpha a pixel is a hole: air, or the background colour when a background is on
	private static final int ALPHA_THRESHOLD = 128;
	private static final int ERROR_ROWS = 3;
	private static final float ORDERED_SPREAD = 40.0F;
	// Colour distance charged per block of extra column height, so a run only climbs for a clearly better shade
	private static final float VALLEY_PENALTY_LAB = 6.0F;
	private static final float VALLEY_PENALTY_RGB = 12.0F;
	// The colour space is cut into a 32 cube of buckets, each holding only the palette entries that can be
	// nearest to some point inside it, so a pixel searches a handful of entries instead of the whole palette
	private static final int BUCKETS = 32;
	// Rounding in bucket() can place a query a hair outside its box, so each box is padded by this much
	private static final float BUCKET_MARGIN = 0.001F;
	private static final float[] RGB_MIN = {0.0F, 0.0F, 0.0F};
	private static final float[] RGB_SPAN = {256.0F, 256.0F, 256.0F};
	// Wide enough for every sRGB colour: L 0..100, a -87..99, b -108..95
	private static final float[] LAB_MIN = {-1.0F, -100.0F, -120.0F};
	private static final float[] LAB_SPAN = {102.0F, 210.0F, 230.0F};

	private final int count;
	private final byte[] ids;
	private final byte[] shades;
	private final int[] argb;
	private final float[] c0;
	private final float[] c1;
	private final float[] c2;
	private final boolean lab;
	private final boolean valley;
	private final float valleyPenalty;
	private final int[] columnHeight;
	private final int[] columnMin;
	private final int[] columnMax;
	private final float[] gridMin;
	private final float[] gridSpan;
	private int[] bucketStart;
	private short[] bucketEntries;

	private MapartConverter(MapartOptions options, int width)
	{
		int max = MapPalette.GROUPS * MapPalette.ALL_SHADES;
		this.ids = new byte[max];
		this.shades = new byte[max];
		this.argb = new int[max];
		this.c0 = new float[max];
		this.c1 = new float[max];
		this.c2 = new float[max];
		this.lab = options.betterColor;
		this.valley = options.staircase == MapartOptions.STAIRCASE_VALLEY;
		this.valleyPenalty = this.lab ? VALLEY_PENALTY_LAB : VALLEY_PENALTY_RGB;
		this.gridMin = this.lab ? LAB_MIN : RGB_MIN;
		this.gridSpan = this.lab ? LAB_SPAN : RGB_SPAN;
		this.columnHeight = new int[width];
		this.columnMin = new int[width];
		this.columnMax = new int[width];
		int n = 0;

		for (int id = 1; id < MapPalette.GROUPS; id++)
		{
			if (MapPalette.group(id) == null || !options.enabled[id])
			{
				continue;
			}

			for (int shade = 0; shade < MapPalette.ALL_SHADES; shade++)
			{
				if (!offers(options.staircase, id, shade))
				{
					continue;
				}

				int color = MapPalette.argb(id, shade);
				this.ids[n] = (byte) id;
				this.shades[n] = (byte) shade;
				this.argb[n] = color;
				this.store(n, (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
				n++;
			}
		}

		this.count = n;
	}

	// One-deep water always reads HIGH whatever the staircase does, so it is the only shade offered
	static boolean offers(int staircase, int id, int shade)
	{
		if (id == MapPalette.WATER_ID)
		{
			return shade == MapPalette.SHADE_HIGH && staircase != MapartOptions.STAIRCASE_FULL_DARK;
		}

		return switch (staircase)
		{
			case MapartOptions.STAIRCASE_OFF -> shade == MapPalette.SHADE_NORMAL;
			case MapartOptions.STAIRCASE_FULL_DARK -> shade == MapPalette.SHADE_LOW;
			case MapartOptions.STAIRCASE_FULL_LIGHT -> shade == MapPalette.SHADE_HIGH;
			case MapartOptions.STAIRCASE_FULL_UNOBTAINABLE -> true;
			default -> shade != MapPalette.SHADE_LOWEST;
		};
	}

	public static MapartResult convert(MapartImage source, MapartOptions options)
	{
		int width = options.mapsX * MapartImage.MAP_SIZE;
		int height = options.mapsY * MapartImage.MAP_SIZE;
		MapartImage fitted = source.fit(width, height, options.crop, options.cropX, options.cropY);

		if (options.preprocess)
		{
			fitted = fitted.adjust(options.brightness, options.contrast, options.saturation);
		}

		int pixels = width * height;
		boolean[] hole = new boolean[pixels];

		for (int i = 0; i < pixels; i++)
		{
			hole[i] = (fitted.argb[i] >>> 24) < ALPHA_THRESHOLD;
		}

		boolean backgroundOn = options.background != MapartOptions.BACKGROUND_OFF;
		MapartImage image = backgroundOn ? fitted.composite(options.backgroundColor) : fitted;
		int[] sourcePreview = new int[pixels];

		for (int i = 0; i < pixels; i++)
		{
			sourcePreview[i] = !backgroundOn && hole[i] ? 0 : 0xFF000000 | image.argb[i];
		}

		MapartConverter matcher = new MapartConverter(options, width);
		byte[] colorIds = new byte[pixels];
		byte[] shades = new byte[pixels];

		if (matcher.count > 0)
		{
			// Air holes take no colour; a smooth background takes the flat match and stays out of the dither
			boolean[] skip = backgroundOn ? new boolean[pixels] : hole;
			boolean[] fixed = options.background == MapartOptions.BACKGROUND_SMOOTH ? hole : new boolean[pixels];
			MapartDither.Kernel kernel = MapartDither.kernel(options.dithering);
			MapartDither.Matrix matrix = MapartDither.matrix(options.dithering);

			if (kernel != null)
			{
				matcher.diffuse(image, kernel, skip, fixed, colorIds, shades);
			}
			else if (matrix != null)
			{
				matcher.ordered(image, matrix, skip, fixed, colorIds, shades);
			}
			else
			{
				matcher.plain(image, skip, colorIds, shades);
			}
		}

		return MapartStaircase.layout(options, colorIds, shades, sourcePreview);
	}

	private void plain(MapartImage image, boolean[] skip, byte[] colorIds, byte[] shades)
	{
		for (int y = 0; y < image.height; y++)
		{
			for (int x = 0; x < image.width; x++)
			{
				int i = y * image.width + x;

				if (skip[i])
				{
					continue;
				}

				int pixel = image.argb[i];
				this.pick(x, i, this.nearest((pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF, x), colorIds, shades);
			}
		}
	}

	private void ordered(MapartImage image, MapartDither.Matrix matrix, boolean[] skip, boolean[] fixed, byte[] colorIds,
			byte[] shades)
	{
		int size = matrix.size();
		float cells = size * size;

		for (int y = 0; y < image.height; y++)
		{
			for (int x = 0; x < image.width; x++)
			{
				int i = y * image.width + x;

				if (skip[i])
				{
					continue;
				}

				int pixel = image.argb[i];
				float offset = fixed[i] ? 0.0F
						: ((matrix.thresholds()[(y % size) * size + x % size] + 0.5F) / cells - 0.5F) * ORDERED_SPREAD;
				int best = this.nearest(((pixel >> 16) & 0xFF) + offset, ((pixel >> 8) & 0xFF) + offset,
						(pixel & 0xFF) + offset, x);
				this.pick(x, i, best, colorIds, shades);
			}
		}
	}

	// Clamped to 0..255 before the match and the error handed on, so an unreachable colour cannot pile error up
	private void diffuse(MapartImage image, MapartDither.Kernel kernel, boolean[] skip, boolean[] fixed, byte[] colorIds,
			byte[] shades)
	{
		int width = image.width;
		int height = image.height;
		int[][] taps = kernel.taps();
		float divisor = kernel.divisor();
		// No kernel reaches further than two rows down, so three rows of error ride along the raster
		float[][] error = new float[ERROR_ROWS][width * 3];

		for (int y = 0; y < height; y++)
		{
			float[] row = error[y % ERROR_ROWS];

			for (int x = 0; x < width; x++)
			{
				int i = y * width + x;

				if (skip[i])
				{
					continue;
				}

				int pixel = image.argb[i];

				if (fixed[i])
				{
					this.pick(x, i, this.nearest((pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF, x), colorIds, shades);
					continue;
				}

				float r = clamp(((pixel >> 16) & 0xFF) + row[x * 3]);
				float g = clamp(((pixel >> 8) & 0xFF) + row[x * 3 + 1]);
				float b = clamp((pixel & 0xFF) + row[x * 3 + 2]);
				int best = this.nearest(r, g, b, x);
				this.pick(x, i, best, colorIds, shades);

				int chosen = this.argb[best];
				float er = r - ((chosen >> 16) & 0xFF);
				float eg = g - ((chosen >> 8) & 0xFF);
				float eb = b - (chosen & 0xFF);

				for (int[] tap : taps)
				{
					int tx = x + tap[0];
					int ty = y + tap[1];

					if (tx < 0 || tx >= width || ty >= height)
					{
						continue;
					}

					int target = ty * width + tx;

					if (skip[target] || fixed[target])
					{
						continue;
					}

					float weight = tap[2] / divisor;
					float[] targetRow = error[ty % ERROR_ROWS];
					targetRow[tx * 3] += er * weight;
					targetRow[tx * 3 + 1] += eg * weight;
					targetRow[tx * 3 + 2] += eb * weight;
				}
			}

			Arrays.fill(row, 0.0F);
		}
	}

	private void pick(int x, int i, int best, byte[] colorIds, byte[] shades)
	{
		colorIds[i] = this.ids[best];
		shades[i] = this.shades[best];

		if (!this.valley)
		{
			return;
		}

		int next = this.columnHeight[x] + MapartStaircase.step(this.shades[best]);
		this.columnHeight[x] = next;
		this.columnMin[x] = Math.min(this.columnMin[x], next);
		this.columnMax[x] = Math.max(this.columnMax[x], next);
	}

	private int nearest(float r, float g, float b, int x)
	{
		float cr = clamp(r);
		float cg = clamp(g);
		float cb = clamp(b);
		float p0 = cr;
		float p1 = cg;
		float p2 = cb;

		if (this.lab)
		{
			float[] lab = toLab(cr, cg, cb);
			p0 = lab[0];
			p1 = lab[1];
			p2 = lab[2];
		}

		int best = 0;
		float bestDistance = Float.MAX_VALUE;

		// Valley cost depends on the column, so only that path still walks the whole palette per pixel
		if (this.valley)
		{
			for (int i = 0; i < this.count; i++)
			{
				float d0 = this.c0[i] - p0;
				float d1 = this.c1[i] - p1;
				float d2 = this.c2[i] - p2;
				float distance = d0 * d0 + d1 * d1 + d2 * d2 + this.valleyCost(x, this.shades[i]);

				if (distance < bestDistance)
				{
					bestDistance = distance;
					best = i;
				}
			}

			return best;
		}

		if (this.bucketStart == null)
		{
			this.buildBuckets();
		}

		int bucket = (this.bucket(p0, 0) * BUCKETS + this.bucket(p1, 1)) * BUCKETS + this.bucket(p2, 2);
		int end = this.bucketStart[bucket + 1];

		for (int k = this.bucketStart[bucket]; k < end; k++)
		{
			int i = this.bucketEntries[k];
			float d0 = this.c0[i] - p0;
			float d1 = this.c1[i] - p1;
			float d2 = this.c2[i] - p2;
			float distance = d0 * d0 + d1 * d1 + d2 * d2;

			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = i;
			}
		}

		return best;
	}

	private int bucket(float value, int axis)
	{
		int index = (int) ((value - this.gridMin[axis]) * BUCKETS / this.gridSpan[axis]);
		return Math.max(0, Math.min(BUCKETS - 1, index));
	}

	// Kept while its nearest approach to the box beats the closest entry's farthest corner; palette order keeps ties exact
	private void buildBuckets()
	{
		int[] start = new int[BUCKETS * BUCKETS * BUCKETS + 1];
		short[] entries = new short[BUCKETS * BUCKETS * BUCKETS * 4];
		float[][] near0 = this.axisTable(this.c0, 0, false);
		float[][] near1 = this.axisTable(this.c1, 1, false);
		float[][] near2 = this.axisTable(this.c2, 2, false);
		float[][] far0 = this.axisTable(this.c0, 0, true);
		float[][] far1 = this.axisTable(this.c1, 1, true);
		float[][] far2 = this.axisTable(this.c2, 2, true);
		float[] near = new float[this.count];
		int filled = 0;
		int bucket = 0;

		for (int i0 = 0; i0 < BUCKETS; i0++)
		{
			for (int i1 = 0; i1 < BUCKETS; i1++)
			{
				for (int i2 = 0; i2 < BUCKETS; i2++)
				{
					float threshold = Float.MAX_VALUE;

					for (int i = 0; i < this.count; i++)
					{
						near[i] = near0[i0][i] + near1[i1][i] + near2[i2][i];
						threshold = Math.min(threshold, far0[i0][i] + far1[i1][i] + far2[i2][i]);
					}

					for (int i = 0; i < this.count; i++)
					{
						if (near[i] > threshold)
						{
							continue;
						}

						if (filled == entries.length)
						{
							entries = Arrays.copyOf(entries, entries.length * 2);
						}

						entries[filled++] = (short) i;
					}

					start[++bucket] = filled;
				}
			}
		}

		this.bucketStart = start;
		this.bucketEntries = entries;
	}

	private float[][] axisTable(float[] values, int axis, boolean farthest)
	{
		float[][] table = new float[BUCKETS][this.count];
		float width = this.gridSpan[axis] / BUCKETS;

		for (int index = 0; index < BUCKETS; index++)
		{
			float low = this.gridMin[axis] + index * width - BUCKET_MARGIN;
			float high = low + width + 2.0F * BUCKET_MARGIN;

			for (int i = 0; i < this.count; i++)
			{
				float value = values[i];
				float distance = farthest
						? Math.max(value - low, high - value)
						: Math.max(0.0F, Math.max(low - value, value - high));
				table[index][i] = distance * distance;
			}
		}

		return table;
	}

	private float valleyCost(int x, int shade)
	{
		int next = this.columnHeight[x] + MapartStaircase.step(shade);
		int widened = Math.max(this.columnMax[x], next) - Math.min(this.columnMin[x], next)
				- (this.columnMax[x] - this.columnMin[x]);
		float cost = widened * this.valleyPenalty;
		return cost * cost;
	}

	private void store(int index, int r, int g, int b)
	{
		if (this.lab)
		{
			float[] lab = toLab(r, g, b);
			this.c0[index] = lab[0];
			this.c1[index] = lab[1];
			this.c2[index] = lab[2];
			return;
		}

		this.c0[index] = r;
		this.c1[index] = g;
		this.c2[index] = b;
	}

	private static float clamp(float value)
	{
		return Math.max(0.0F, Math.min(255.0F, value));
	}

	static float[] toLab(float r, float g, float b)
	{
		double lr = linear(r / 255.0);
		double lg = linear(g / 255.0);
		double lb = linear(b / 255.0);

		double x = (lr * 0.4124564 + lg * 0.3575761 + lb * 0.1804375) / 0.95047;
		double y = lr * 0.2126729 + lg * 0.7151522 + lb * 0.0721750;
		double z = (lr * 0.0193339 + lg * 0.1191920 + lb * 0.9503041) / 1.08883;

		double fx = labF(x);
		double fy = labF(y);
		double fz = labF(z);

		return new float[]{(float) (116.0 * fy - 16.0), (float) (500.0 * (fx - fy)), (float) (200.0 * (fy - fz))};
	}

	private static double linear(double channel)
	{
		return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
	}

	private static double labF(double t)
	{
		return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16.0) / 116.0;
	}
}

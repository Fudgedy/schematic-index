package com.fudgedy.schematicindex.fx;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// A name's ink as the vanilla font draws it, two clear columns either side for glows; built from the live
// ascii.png so a resource pack's font shapes the effect too
public final class NameMask
{
	public static final int MARGIN = 2;
	public static final int ROWS = 8;
	// Distances are kept for the rows a glow can reach above and below the text line
	private static final int DIST_TOP = -5;
	private static final int DIST_ROWS = 18;
	private static final float MAX_GLOW = 4.5F;
	private static final Logger LOGGER = LoggerFactory.getLogger("SchematicIndex");
	private static final Identifier ASCII = Identifier.withDefaultNamespace("textures/font/ascii.png");

	private static int[][] glyphs;
	private static boolean failed;

	public final String name;
	public final int width;
	public final int[] top;
	public final int[] bottom;
	public final int[] cols;
	public final int[][] spans;
	private final boolean[] ink;
	private final float[] dist;
	private int[][] ring;
	private int[][] band;

	private NameMask(String name, List<Integer> columnBits, List<int[]> spans)
	{
		this.name = name;
		this.width = columnBits.size() + MARGIN * 2;
		this.ink = new boolean[this.width * ROWS];
		this.top = new int[this.width];
		this.bottom = new int[this.width];
		this.spans = spans.toArray(new int[0][]);

		for (int i = 0; i < columnBits.size(); i++)
		{
			for (int y = 0; y < ROWS; y++)
			{
				if ((columnBits.get(i) >> y & 1) != 0)
				{
					this.ink[y * this.width + i + MARGIN] = true;
				}
			}
		}

		List<Integer> inked = new ArrayList<>();

		for (int x = 0; x < this.width; x++)
		{
			int t = -1;
			int b = -1;

			for (int y = 0; y < ROWS; y++)
			{
				if (this.isInk(x, y))
				{
					if (t < 0)
					{
						t = y;
					}

					b = y;
				}
			}

			this.top[x] = t;
			this.bottom[x] = b;

			if (t >= 0)
			{
				inked.add(x);
			}
		}

		this.cols = new int[inked.size()];

		for (int i = 0; i < this.cols.length; i++)
		{
			this.cols[i] = inked.get(i);
		}

		this.dist = new float[this.width * DIST_ROWS];
		this.fillDistances();
	}

	// Null for a name the font sheet cannot draw (outside printable ascii) or before resources are ready
	public static @Nullable NameMask of(String name)
	{
		int[][] sheet = glyphs();

		if (sheet == null || name.isEmpty())
		{
			return null;
		}

		List<Integer> columns = new ArrayList<>();
		List<int[]> spans = new ArrayList<>();

		for (int i = 0; i < name.length(); i++)
		{
			char c = name.charAt(i);

			if (c < 0x20 || c > 0x7E)
			{
				return null;
			}

			int[] glyph = sheet[c - 0x20];
			spans.add(new int[] {columns.size() + MARGIN, columns.size() + MARGIN - 1 + glyph[0]});

			for (int x = 0; x < glyph[0]; x++)
			{
				int bits = 0;

				for (int y = 0; y < ROWS; y++)
				{
					if ((glyph[1 + y] >> x & 1) != 0)
					{
						bits |= 1 << y;
					}
				}

				columns.add(bits);
			}

			columns.add(0);
		}

		columns.remove(columns.size() - 1);
		return new NameMask(name, columns, spans);
	}

	public boolean isInk(int x, int y)
	{
		return y >= 0 && y < ROWS && x >= 0 && x < this.width && this.ink[y * this.width + x];
	}

	// The name's own advance, what vanilla measures the plain text at
	public int advance()
	{
		return this.width - MARGIN * 2 + 1;
	}

	public float across(int x)
	{
		return (x - MARGIN) / (float) Math.max(1, this.width - 5);
	}

	// Pixels exactly one step from the strokes, the Pulse ring
	public int[][] ring()
	{
		if (this.ring == null)
		{
			this.ring = this.collect(0.0F, 1.0F, true);
		}

		return this.ring;
	}

	// {x, y, distance x2} for every pixel a glow can light, nearest strokes first in scan order
	public int[][] band()
	{
		if (this.band == null)
		{
			this.band = this.collect(0.0F, MAX_GLOW, false);
		}

		return this.band;
	}

	private int[][] collect(float low, float high, boolean exact)
	{
		List<int[]> out = new ArrayList<>();

		for (int row = 0; row < DIST_ROWS; row++)
		{
			for (int x = 0; x < this.width; x++)
			{
				float d = this.dist[row * this.width + x];

				if (exact ? d == high : d > low && d <= high)
				{
					out.add(new int[] {x, row + DIST_TOP, Math.round(d * 2.0F)});
				}
			}
		}

		return out.toArray(new int[0][]);
	}

	private void fillDistances()
	{
		for (int row = 0; row < DIST_ROWS; row++)
		{
			int y = row + DIST_TOP;

			for (int x = 0; x < this.width; x++)
			{
				double best = 99.0;

				if (!this.isInk(x, y))
				{
					for (int dy = -4; dy <= 4; dy++)
					{
						for (int dx = -4; dx <= 4; dx++)
						{
							if (this.isInk(x + dx, y + dy))
							{
								best = Math.min(best, Math.hypot(dx, dy));
							}
						}
					}
				}

				this.dist[row * this.width + x] = best <= MAX_GLOW ? Math.round(best * 2.0) / 2.0F : Float.NaN;
			}
		}
	}

	private static int @Nullable [][] glyphs()
	{
		if (glyphs != null || failed)
		{
			return glyphs;
		}

		Minecraft mc = Minecraft.getInstance();

		if (mc == null || mc.getResourceManager() == null)
		{
			return null;
		}

		Optional<Resource> resource = mc.getResourceManager().getResource(ASCII);

		if (resource.isEmpty())
		{
			failed = true;
			return null;
		}

		try (InputStream in = resource.get().open(); NativeImage sheet = NativeImage.read(in))
		{
			glyphs = readSheet(sheet);
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to read the font sheet for name effects", e);
			failed = true;
		}

		return glyphs;
	}

	// Each glyph is {width, row bits x8}; width is the rightmost inked column plus one, space three, as vanilla's
	// bitmap provider measures it
	private static int[][] readSheet(NativeImage sheet)
	{
		int cell = sheet.getWidth() / 16;
		float step = cell / 8.0F;
		int[][] out = new int[0x7F - 0x20][];

		for (int c = 0x20; c < 0x7F; c++)
		{
			int cx = (c % 16) * cell;
			int cy = (c / 16) * cell;
			int[] glyph = new int[1 + ROWS];
			int width = 0;

			for (int y = 0; y < ROWS; y++)
			{
				for (int x = 0; x < 8; x++)
				{
					int px = (int) (cx + x * step + step / 2.0F);
					int py = (int) (cy + y * step + step / 2.0F);

					if ((sheet.getPixel(px, py) >>> 24) != 0)
					{
						glyph[1 + y] |= 1 << x;
						width = Math.max(width, x + 1);
					}
				}
			}

			glyph[0] = c == ' ' ? 3 : Math.max(1, width);
			out[c - 0x20] = glyph;
		}

		return out;
	}
}

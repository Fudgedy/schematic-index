package com.fudgedy.schematicindex.mapart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Turns per-pixel shades into block heights. A map shades each pixel against the block one row
// north: higher reads HIGH, level NORMAL, lower LOW, so every column is a walk from its noobline
// block southwards stepping one up or down per shade change; columns are independent
public final class MapartStaircase
{
	private MapartStaircase()
	{
	}

	// LOWEST is only ever a flat placement: no built column can produce it
	public static int step(int shade)
	{
		return shade == MapPalette.SHADE_HIGH ? 1 : (shade == MapPalette.SHADE_LOW ? -1 : 0);
	}

	public static MapartResult layout(MapartOptions options, byte[] colorIds, byte[] shades, int[] sourcePreview)
	{
		int width = options.mapsX * MapartImage.MAP_SIZE;
		int height = options.mapsY * MapartImage.MAP_SIZE;
		int[] heights = new int[width * height];
		byte[] supports = new byte[width * height];
		int[] nooblineY = new int[width];
		String[] blocks = new String[MapPalette.GROUPS];
		boolean[] needsSupport = new boolean[MapPalette.GROUPS];
		boolean staircase = options.isStaircase();

		for (int id = 1; id < MapPalette.GROUPS; id++)
		{
			if (MapPalette.group(id) != null && options.enabled[id])
			{
				blocks[id] = options.blockFor(id);
				needsSupport[id] = options.supportFor(id);
			}
		}

		int tiles = options.mapsX * options.mapsY;
		int[][] tileCounts = new int[tiles][MapPalette.GROUPS];
		int[] tileSupports = new int[tiles];
		int[] counts = new int[MapPalette.GROUPS];
		int supportCount = 0;
		int nooblineCount = 0;
		int sizeY = 1;

		for (int x = 0; x < width; x++)
		{
			int relative = 0;
			int lowest = options.noobline ? 0 : Integer.MAX_VALUE;
			boolean any = false;

			for (int z = 0; z < height; z++)
			{
				int i = z * width + x;
				int id = colorIds[i];

				if (id == 0)
				{
					heights[i] = -1;
					continue;
				}

				if (staircase)
				{
					relative += step(shades[i]);
				}

				heights[i] = relative;
				supports[i] = (byte) supportsUnder(options.support, needsSupport[id]);
				lowest = Math.min(lowest, relative - supports[i]);
				any = true;
			}

			if (!any)
			{
				nooblineY[x] = -1;
				continue;
			}

			int shift = -lowest;
			nooblineY[x] = options.noobline ? shift : -1;

			if (options.noobline)
			{
				nooblineCount++;
				sizeY = Math.max(sizeY, shift + 1);
			}

			for (int z = 0; z < height; z++)
			{
				int i = z * width + x;
				int id = colorIds[i];

				if (id == 0)
				{
					continue;
				}

				heights[i] += shift;
				sizeY = Math.max(sizeY, heights[i] + 1);
				counts[id]++;
				supportCount += supports[i];
				int tile = (z / MapartImage.MAP_SIZE) * options.mapsX + x / MapartImage.MAP_SIZE;
				tileCounts[tile][id]++;
				tileSupports[tile] += supports[i];
			}
		}

		// Every split file carries its own noobline row, so each tile is charged one per built column
		if (options.noobline)
		{
			for (int tile = 0; tile < tiles; tile++)
			{
				tileSupports[tile] += builtColumns(colorIds, width, options.mapsX, tile);
			}
		}

		String supportBlock = MapartOptions.SUPPORT_BLOCK;
		Map<String, Integer> totals = new HashMap<>();

		for (int id = 1; id < MapPalette.GROUPS; id++)
		{
			if (counts[id] > 0)
			{
				totals.merge(blocks[id], counts[id], Integer::sum);
			}
		}

		if (supportCount + nooblineCount > 0)
		{
			totals.merge(supportBlock, supportCount + nooblineCount, Integer::sum);
		}

		Map<String, Integer> perMap = new HashMap<>();

		for (int tile = 0; tile < tiles; tile++)
		{
			Map<String, Integer> tileTotals = new HashMap<>();

			for (int id = 1; id < MapPalette.GROUPS; id++)
			{
				if (tileCounts[tile][id] > 0)
				{
					tileTotals.merge(blocks[id], tileCounts[tile][id], Integer::sum);
				}
			}

			if (tileSupports[tile] > 0)
			{
				tileTotals.merge(supportBlock, tileSupports[tile], Integer::sum);
			}

			for (Map.Entry<String, Integer> entry : tileTotals.entrySet())
			{
				perMap.merge(entry.getKey(), entry.getValue(), Math::max);
			}
		}

		int blockCount = 0;
		Map<String, Integer> materials = sorted(totals);

		for (int value : materials.values())
		{
			blockCount += value;
		}

		int[] preview = new int[width * height];

		for (int i = 0; i < preview.length; i++)
		{
			preview[i] = colorIds[i] == 0 ? 0 : MapPalette.argb(colorIds[i], shades[i]);
		}

		return new MapartResult(options.mapsX, options.mapsY, width, height, staircase, colorIds, shades, heights, nooblineY,
				sizeY, blocks, needsSupport, options.support != MapartOptions.SUPPORT_NONE, supportBlock, blockCount,
				materials, preview, supports, options.noobline, sorted(perMap), sourcePreview);
	}

	private static int supportsUnder(int mode, boolean important)
	{
		return switch (mode)
		{
			case MapartOptions.SUPPORT_IMPORTANT -> important ? 1 : 0;
			case MapartOptions.SUPPORT_ALL -> 1;
			case MapartOptions.SUPPORT_ALL_DOUBLE -> 2;
			default -> 0;
		};
	}

	private static int builtColumns(byte[] colorIds, int width, int mapsX, int tile)
	{
		int x0 = (tile % mapsX) * MapartImage.MAP_SIZE;
		int z0 = (tile / mapsX) * MapartImage.MAP_SIZE;
		int built = 0;

		for (int x = x0; x < x0 + MapartImage.MAP_SIZE; x++)
		{
			for (int z = z0; z < z0 + MapartImage.MAP_SIZE; z++)
			{
				if (colorIds[z * width + x] != 0)
				{
					built++;
					break;
				}
			}
		}

		return built;
	}

	private static Map<String, Integer> sorted(Map<String, Integer> totals)
	{
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(totals.entrySet());
		entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		Map<String, Integer> out = new LinkedHashMap<>();

		for (Map.Entry<String, Integer> entry : entries)
		{
			out.put(entry.getKey(), entry.getValue());
		}

		return out;
	}
}

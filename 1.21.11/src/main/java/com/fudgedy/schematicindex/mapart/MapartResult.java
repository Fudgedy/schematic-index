package com.fudgedy.schematicindex.mapart;

import java.util.Map;

// A finished conversion. Pixel index = z * width + x, z = 0 being the north edge; the schematic places
// blocks[colorIds[i]] at (x, heights[i], z + rowOffset), with the noobline row at z = 0 when rowOffset is 1
public record MapartResult(
		int mapsX,
		int mapsY,
		int width,
		int height,
		boolean staircase,
		byte[] colorIds, // MapColor id, 0 = air
		byte[] shades, // Brightness id: 0 LOW, 1 NORMAL, 2 HIGH, 3 LOWEST
		int[] heights, // Y above the floor, -1 for air
		int[] nooblineY, // -1 for an empty column
		int sizeY, // highest Y + 1
		String[] blocks, // spec per MapColor id, "minecraft:name[prop=value]", null where unused
		boolean[] needsSupport,
		boolean supportBlocks,
		String supportBlock, // also builds the noobline
		int blockCount, // includes support and noobline blocks
		Map<String, Integer> materials, // includes support and noobline blocks
		int[] preview,
		byte[] supports, // supportBlock placed directly beneath
		boolean noobline,
		Map<String, Integer> materialsPerMap, // the most any single 1x1 map needs
		int[] sourcePreview) // the fitted, preprocessed picture the colours matched from, 0 where a pixel became air
{
	public int index(int x, int z)
	{
		return z * this.width + x;
	}

	public boolean isAir(int index)
	{
		return this.colorIds[index] == 0;
	}

	public int rowOffset()
	{
		return this.noobline ? 1 : 0;
	}
}

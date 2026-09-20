package com.fudgedy.schematicindex.mapart;

import com.fudgedy.schematicindex.catalogue.Download;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

// Writes a MapartResult through Litematica's own schematic classes so the file round-trips
// through the same reader the preview and load paths use
public final class MapartSchematic
{
	public static final String AUTHOR = "The Schematic Index";
	public static final String REGION = "mapart";
	public static final String EXTENSION = LitematicaSchematic.FILE_EXTENSION;
	public static final int MAP_SIZE = 128;

	private static final int PREVIEW_SIZE = 128;

	private MapartSchematic()
	{
	}

	public static void save(MapartResult result, Path target) throws IOException
	{
		int[] noobline = nooblineFor(result, 0, 0, result.width());
		int sizeY = Math.max(result.sizeY(), tallest(result, 0, 0, result.width(), result.height(), noobline) + 1);
		write(result, 0, 0, result.width(), result.height(), noobline, sizeY, target);
	}

	// The whole mapart as an in-memory schematic, for loading straight into Litematica without a file
	public static LitematicaSchematic build(MapartResult result, String name) throws IOException
	{
		int[] noobline = nooblineFor(result, 0, 0, result.width());
		int sizeY = Math.max(result.sizeY(), tallest(result, 0, 0, result.width(), result.height(), noobline) + 1);
		return build(result, 0, 0, result.width(), result.height(), noobline, sizeY, name);
	}

	// Tiles of splitMaps x splitMaps maps, the last row and column clipped to the mapart; every
	// tile is self-contained with its own noobline row and support blocks
	public static void saveSplit(MapartResult result, Path directory, String baseName, int splitMaps) throws IOException
	{
		int tile = Math.max(1, splitMaps) * MAP_SIZE;

		for (int row = 0; row * tile < result.height(); row++)
		{
			for (int col = 0; col * tile < result.width(); col++)
			{
				int x0 = col * tile;
				int z0 = row * tile;
				int width = Math.min(tile, result.width() - x0);
				int height = Math.min(tile, result.height() - z0);
				int[] noobline = nooblineFor(result, x0, z0, width);
				int sizeY = tallest(result, x0, z0, width, height, noobline) + 1;
				Path target = directory.resolve(baseName + "_" + col + "_" + row + EXTENSION);
				write(result, x0, z0, width, height, noobline, sizeY, target);
			}
		}
	}

	public static int splitCount(MapartResult result, int splitMaps)
	{
		int tile = Math.max(1, splitMaps);
		return Math.ceilDiv(result.mapsX(), tile) * Math.ceilDiv(result.mapsY(), tile);
	}

	private static void write(MapartResult result, int x0, int z0, int width, int height,
			int[] noobline, int sizeY, Path target) throws IOException
	{
		LitematicaSchematic schematic = build(result, x0, z0, width, height, noobline, sizeY, stem(target));
		Path directory = target.toAbsolutePath().getParent();
		Files.createDirectories(directory);

		// writeToFile is the one write entry point every supported Litematica release shares; it re-spells the name
		// and reports failure only through a popup and a false return, so the stem is pre-sanitised and the file checked
		if (!schematic.writeToFile(directory, stem(target), true) || !Files.exists(target))
		{
			throw new IOException("Litematica could not write " + target);
		}
	}

	private static LitematicaSchematic build(MapartResult result, int x0, int z0, int width, int height,
			int[] noobline, int sizeY, String name) throws IOException
	{
		int rowOffset = result.rowOffset();
		LitematicaSchematic schematic = createEmpty(name, width, Math.max(1, sizeY), height + rowOffset);
		LitematicaBlockStateContainer container = schematic.getSubRegionContainer(REGION);
		Map<String, BlockState> states = new HashMap<>();
		BlockState support = state(result.supportBlock(), states);
		int placed = 0;

		for (int x = 0; rowOffset > 0 && x < width; x++)
		{
			if (noobline[x] < 0)
			{
				continue;
			}

			container.set(x, noobline[x], 0, support);
			placed++;
		}

		for (int z = 0; z < height; z++)
		{
			for (int x = 0; x < width; x++)
			{
				int i = result.index(x0 + x, z0 + z);

				if (result.isAir(i))
				{
					continue;
				}

				int id = result.colorIds()[i];
				int y = result.heights()[i];
				container.set(x, y, z + rowOffset, state(result.blocks()[id], states));
				placed++;

				for (int below = 1; below <= result.supports()[i] && y - below >= 0; below++)
				{
					if (!container.get(x, y - below, z + rowOffset).isAir())
					{
						continue;
					}

					container.set(x, y - below, z + rowOffset, support);
					placed++;
				}
			}
		}

		long now = System.currentTimeMillis();
		SchematicMetadata metadata = schematic.getMetadata();
		metadata.setTotalBlocks(placed);
		metadata.setPreviewImagePixelData(thumbnail(result, x0, z0, width, height));
		metadata.setTimeCreated(now);
		metadata.setTimeModified(now);
		return schematic;
	}

	public static String safeStem(String name)
	{
		StringBuilder out = new StringBuilder(name.length());

		for (int i = 0; i < name.length(); i++)
		{
			char c = name.charAt(i);
			boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
			out.append(allowed ? c : '_');
		}

		return out.length() == 0 ? "mapart" : Download.deviceSafe(out.toString());
	}

	private static LitematicaSchematic createEmpty(String name, int sizeX, int sizeY, int sizeZ) throws IOException
	{
		AreaSelection area = new AreaSelection();
		area.setName(name);
		area.addSubRegionBox(new Box(BlockPos.ZERO, new BlockPos(sizeX - 1, sizeY - 1, sizeZ - 1), REGION), true);
		LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, AUTHOR);

		if (schematic == null)
		{
			throw new IOException("Litematica refused a " + sizeX + "x" + sizeY + "x" + sizeZ + " region");
		}

		return schematic;
	}

	// A slice below the first map row shades its top row against whatever the full build has north
	// of it, so that neighbour's height becomes the slice's own noobline; air there needs no block
	private static int[] nooblineFor(MapartResult result, int x0, int z0, int width)
	{
		int[] line = new int[width];

		for (int x = 0; x < width; x++)
		{
			if (!result.noobline())
			{
				line[x] = -1;
				continue;
			}

			if (z0 == 0)
			{
				line[x] = result.nooblineY()[x0 + x];
				continue;
			}

			int north = result.index(x0 + x, z0 - 1);
			line[x] = result.isAir(north) ? -1 : result.heights()[north];
		}

		return line;
	}

	private static int tallest(MapartResult result, int x0, int z0, int width, int height, int[] noobline)
	{
		int top = -1;

		for (int x = 0; x < width; x++)
		{
			top = Math.max(top, noobline[x]);
		}

		for (int z = 0; z < height; z++)
		{
			for (int x = 0; x < width; x++)
			{
				top = Math.max(top, result.heights()[result.index(x0 + x, z0 + z)]);
			}
		}

		return top;
	}

	private static BlockState state(String spec, Map<String, BlockState> cache) throws IOException
	{
		BlockState cached = cache.get(spec);

		if (cached != null)
		{
			return cached;
		}

		if (spec == null)
		{
			throw new IOException("A mapart colour has no block assigned");
		}

		try
		{
			BlockState parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, spec, false).blockState();
			cache.put(spec, parsed);
			return parsed;
		}
		catch (Exception e)
		{
			throw new IOException("Unknown block " + spec, e);
		}
	}

	// Litematica's browser only draws square thumbnails, so the mapart is fitted into one and letterboxed
	private static int[] thumbnail(MapartResult result, int x0, int z0, int width, int height)
	{
		int[] preview = result.preview();

		if (preview == null)
		{
			return null;
		}

		int[] pixels = new int[PREVIEW_SIZE * PREVIEW_SIZE];
		int longest = Math.max(width, height);
		int drawnWidth = Math.max(1, width * PREVIEW_SIZE / longest);
		int drawnHeight = Math.max(1, height * PREVIEW_SIZE / longest);
		int offsetX = (PREVIEW_SIZE - drawnWidth) / 2;
		int offsetY = (PREVIEW_SIZE - drawnHeight) / 2;

		for (int py = 0; py < drawnHeight; py++)
		{
			int z = z0 + py * height / drawnHeight;

			for (int px = 0; px < drawnWidth; px++)
			{
				int x = x0 + px * width / drawnWidth;
				pixels[(offsetY + py) * PREVIEW_SIZE + offsetX + px] = preview[result.index(x, z)];
			}
		}

		return pixels;
	}

	private static String stem(Path target)
	{
		String name = target.getFileName().toString();
		return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
	}
}

package com.fudgedy.schematicindex.mapart;

import com.fudgedy.schematicindex.export.Watermark;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MapartExports
{
	public static final String MATERIALS_SUFFIX = "_materials.txt";
	public static final String PREVIEW_SUFFIX = "_preview.png";
	private static final int PREVIEW_SCALE = 4;
	private static final String PREVIEW_FOOTER = "Made with The Schematic Index · schematicindex.com";

	private MapartExports()
	{
	}

	public static void writeMaterials(MapartResult result, boolean perMap, Path target) throws IOException
	{
		Map<String, Integer> materials = perMap ? result.materialsPerMap() : result.materials();
		List<String> lines = new ArrayList<>(materials.size() + 4);
		lines.add(perMap ? "Materials, most needed by any single map" : "Materials for the whole mapart");
		lines.add(result.mapsX() + "x" + result.mapsY() + " maps, " + result.width() + "x" + result.height() + " blocks");
		lines.add("");
		int total = 0;

		for (Map.Entry<String, Integer> entry : materials.entrySet())
		{
			lines.add(String.format(Locale.ROOT, "%,d x %s", entry.getValue(), MapPalette.displayName(entry.getKey())));
			total += entry.getValue();
		}

		lines.add("");
		lines.add(String.format(Locale.ROOT, "%,d blocks total", total));
		Files.createDirectories(target.toAbsolutePath().getParent());
		Files.write(target, lines, StandardCharsets.UTF_8);
	}

	// Nearest-neighbour, so every map pixel stays a crisp block of colour instead of a blur
	public static BufferedImage writePreview(MapartResult result, Path target) throws IOException
	{
		int width = result.width();
		int height = result.height();
		int[] pixels = result.preview();
		BufferedImage scaled = new BufferedImage(width * PREVIEW_SCALE, height * PREVIEW_SCALE, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < scaled.getHeight(); y++)
		{
			for (int x = 0; x < scaled.getWidth(); x++)
			{
				scaled.setRGB(x, y, pixels[(y / PREVIEW_SCALE) * width + x / PREVIEW_SCALE]);
			}
		}

		BufferedImage image = Watermark.footer(scaled, PREVIEW_FOOTER);
		Files.createDirectories(target.toAbsolutePath().getParent());

		if (!ImageIO.write(image, "png", target.toFile()))
		{
			throw new IOException("No PNG writer");
		}

		return image;
	}
}

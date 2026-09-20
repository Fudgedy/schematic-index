package com.fudgedy.schematicindex.mapart;

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
}

package com.fudgedy.schematicindex;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Schematics opened with "Load" are cached here rather than in the user's schematics folder, and the
// mixin on removeSchematic deletes the cached file, so the cache self-clears. Each file is paired with
// a ".sha" sidecar holding the post's file hash, so an updated post is re-fetched instead of reused.
public final class LoadedCache
{
	private LoadedCache()
	{
	}

	public static Path directory()
	{
		return FabricLoader.getInstance().getGameDir().resolve(SchematicIndexMod.MOD_ID).resolve("loaded");
	}

	public static Path resolve(String fileName)
	{
		return directory().resolve(fileName);
	}

	private static Path hashSidecar(String fileName)
	{
		return directory().resolve(fileName + ".sha");
	}

	// A null or blank hash clears the sidecar, so a post that stopped publishing one cannot keep matching
	public static void store(String fileName, String fileHash)
	{
		try
		{
			Path sidecar = hashSidecar(fileName);

			if (fileHash == null || fileHash.isBlank())
			{
				Files.deleteIfExists(sidecar);
				return;
			}

			Files.createDirectories(directory());
			Files.writeString(sidecar, fileHash.trim(), StandardCharsets.UTF_8);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not record loaded-cache hash", e);
		}
	}

	public static String storedHash(String fileName)
	{
		try
		{
			Path sidecar = hashSidecar(fileName);

			if (!Files.exists(sidecar))
			{
				return null;
			}

			String value = Files.readString(sidecar, StandardCharsets.UTF_8).trim();
			return value.isBlank() ? null : value;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not read loaded-cache hash", e);
			return null;
		}
	}

	// A post with no expected hash is always reusable
	public static boolean isFresh(String fileName, String expectedHash)
	{
		if (expectedHash == null || expectedHash.isBlank())
		{
			return true;
		}

		return expectedHash.trim().equals(storedHash(fileName));
	}

	public static void onUnloaded(LitematicaSchematic schematic)
	{
		try
		{
			Path file = schematic.getFile();

			if (file == null)
			{
				return;
			}

			Path normalized = file.toAbsolutePath().normalize();

			if (normalized.startsWith(directory().toAbsolutePath().normalize()))
			{
				Files.deleteIfExists(normalized);
				Files.deleteIfExists(normalized.resolveSibling(normalized.getFileName().toString() + ".sha"));
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not clear loaded-cache file", e);
		}
	}
}

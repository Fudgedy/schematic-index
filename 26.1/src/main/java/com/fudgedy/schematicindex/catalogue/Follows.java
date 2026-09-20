package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.Toasts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Follows
{
	private static final String FILE_NAME = SchematicIndexMod.MOD_ID + "-follows.properties";
	private static final String KEY = "following";

	// Read on the background catalogue thread while toggle mutates on the GUI thread
	private static final Set<String> FOLLOWING = ConcurrentHashMap.newKeySet();
	private static boolean loaded;

	private Follows()
	{
	}

	public static boolean isFollowing(String poster)
	{
		ensureLoaded();
		return FOLLOWING.contains(key(poster));
	}

	public static boolean toggle(String poster)
	{
		ensureLoaded();
		boolean nowFollowing;

		if (FOLLOWING.remove(key(poster)))
		{
			nowFollowing = false;
		}
		else
		{
			FOLLOWING.add(key(poster));
			nowFollowing = true;
		}

		save();
		return nowFollowing;
	}

	// A union rather than a replace, so a follow made offline survives the sync
	public static void mergeFromServer(Collection<String> posters)
	{
		ensureLoaded();

		if (posters == null || posters.isEmpty())
		{
			return;
		}

		boolean changed = false;

		for (String poster : posters)
		{
			String normalized = key(poster);

			if (!normalized.isBlank() && FOLLOWING.add(normalized))
			{
				changed = true;
			}
		}

		if (changed)
		{
			save();
		}
	}

	public static void notifyForPost(SchematicEntry entry)
	{
		if (!Settings.notifications() || !isFollowing(entry.poster()))
		{
			return;
		}

		Minecraft.getInstance().execute(() ->
				Toasts.push(entry.poster() + " posted", entry.title(), new ItemStack(Items.WRITABLE_BOOK)));
	}

	private static String key(String poster)
	{
		return poster == null ? "" : poster.trim().toLowerCase(Locale.ROOT);
	}

	private static synchronized void ensureLoaded()
	{
		if (loaded)
		{
			return;
		}

		loaded = true;
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

		if (!Files.exists(path))
		{
			return;
		}

		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(path))
		{
			properties.load(reader);
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.warn("Could not read {}", path, e);
			return;
		}

		String stored = properties.getProperty(KEY, "");

		for (String name : stored.split(","))
		{
			if (!name.isBlank())
			{
				// Normalized, not just trimmed, so an entry saved under different casing still matches key()
				FOLLOWING.add(key(name));
			}
		}
	}

	private static void save()
	{
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Properties properties = new Properties();
		properties.setProperty(KEY, String.join(",", FOLLOWING));

		try
		{
			Files.createDirectories(path.getParent());

			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(temporary))
			{
				properties.store(writer, "The Schematic Index - followed creators");
			}

			try
			{
				Files.move(temporary, path,
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException atomicFailed)
			{
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.warn("Could not write {}", path, e);
		}
	}
}

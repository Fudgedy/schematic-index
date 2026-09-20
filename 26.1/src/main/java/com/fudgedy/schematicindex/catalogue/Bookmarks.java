package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.SchematicIndexMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class Bookmarks
{
	private static final String FILE_NAME = SchematicIndexMod.MOD_ID + "-library.properties";
	private static final String KEY_LIKED = "liked";
	private static final String KEY_SAVED = "saved";

	private static final Set<String> LIKED = new LinkedHashSet<>();
	private static final Set<String> SAVED = new LinkedHashSet<>();
	private static boolean loaded;

	private Bookmarks()
	{
	}

	// A LinkedHashSet rather than a concurrent set, so savedOrder() keeps its ordering guarantee. Access
	// is synchronized on the class monitor because reads happen on background threads
	public static synchronized boolean isLiked(String id)
	{
		ensureLoaded();
		return LIKED.contains(id);
	}

	public static synchronized List<String> savedOrder()
	{
		ensureLoaded();
		return new ArrayList<>(SAVED);
	}

	public static synchronized boolean toggleLike(String id)
	{
		ensureLoaded();
		boolean nowLiked = !LIKED.remove(id);

		if (nowLiked)
		{
			LIKED.add(id);
		}

		save();
		return nowLiked;
	}

	// A union rather than a replace, so a like made offline survives the sync
	public static synchronized void mergeLikedFromServer(Collection<String> likedIds)
	{
		ensureLoaded();

		if (likedIds == null || likedIds.isEmpty())
		{
			return;
		}

		boolean changed = false;

		for (String id : likedIds)
		{
			if (id != null && !id.isBlank() && LIKED.add(id.trim()))
			{
				changed = true;
			}
		}

		if (changed)
		{
			save();
		}
	}

	public static synchronized boolean isSaved(String id)
	{
		ensureLoaded();
		return SAVED.contains(id);
	}

	public static synchronized boolean toggleSaved(String id)
	{
		ensureLoaded();
		boolean nowSaved = !SAVED.remove(id);

		if (nowSaved)
		{
			SAVED.add(id);
		}

		save();
		Library.onLocalChange();
		return nowSaved;
	}

	// Union only, so a save made offline survives and a removal here is never undone by a stale server copy
	public static synchronized void mergeSavedFromServer(Collection<String> savedIds)
	{
		ensureLoaded();
		boolean changed = false;

		for (String id : savedIds)
		{
			if (id != null && !id.isBlank() && SAVED.add(id.trim()))
			{
				changed = true;
			}
		}

		if (changed)
		{
			save();
		}
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

		readInto(properties.getProperty(KEY_LIKED, ""), LIKED);
		readInto(properties.getProperty(KEY_SAVED, ""), SAVED);
	}

	private static void readInto(String stored, Set<String> into)
	{
		for (String id : stored.split(","))
		{
			if (!id.isBlank())
			{
				into.add(id.trim());
			}
		}
	}

	private static void save()
	{
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Properties properties = new Properties();
		properties.setProperty(KEY_LIKED, String.join(",", LIKED));
		properties.setProperty(KEY_SAVED, String.join(",", SAVED));

		try
		{
			Files.createDirectories(path.getParent());

			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(temporary))
			{
				properties.store(writer, "The Schematic Index - liked and saved posts");
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

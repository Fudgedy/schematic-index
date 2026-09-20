package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Named, user-created collections of saved posts. Stored locally (like Bookmarks) as a JSON list of
// {id, name, postIds}; the id is generated once and survives renames so the account sync can match it
public final class CollectionStore
{
	public record Row(String id, String name, List<String> postIds)
	{
	}

	private static final String FILE_NAME = SchematicIndexMod.MOD_ID + "-collections.json";
	private static final String KEY_COLLECTIONS = "collections";
	private static final int MAX_NAME = 40;

	private static final Map<String, Entry> DATA = new LinkedHashMap<>();
	private static boolean loaded;

	private CollectionStore()
	{
	}

	public static synchronized List<String> names()
	{
		ensureLoaded();
		return new ArrayList<>(DATA.keySet());
	}

	public static synchronized boolean create(String name)
	{
		ensureLoaded();
		String trimmed = normalize(name);

		if (trimmed.isEmpty() || DATA.containsKey(trimmed))
		{
			return false;
		}

		DATA.put(trimmed, new Entry(UUID.randomUUID().toString()));
		save();
		return true;
	}

	public static synchronized void delete(String name)
	{
		ensureLoaded();

		if (DATA.remove(name) != null)
		{
			save();
		}
	}

	public static synchronized boolean rename(String from, String to)
	{
		ensureLoaded();
		String cleaned = normalize(to);

		if (cleaned.isEmpty() || !DATA.containsKey(from) || (DATA.containsKey(cleaned) && !cleaned.equals(from)))
		{
			return false;
		}

		Map<String, Entry> rebuilt = new LinkedHashMap<>();

		for (Map.Entry<String, Entry> entry : DATA.entrySet())
		{
			rebuilt.put(entry.getKey().equals(from) ? cleaned : entry.getKey(), entry.getValue());
		}

		DATA.clear();
		DATA.putAll(rebuilt);
		DATA.get(cleaned).renamed = true;
		save();
		return true;
	}

	public static synchronized Set<String> postIds(String name)
	{
		ensureLoaded();
		Entry entry = DATA.get(name);
		return entry == null ? Set.of() : new LinkedHashSet<>(entry.postIds);
	}

	public static synchronized int size(String name)
	{
		ensureLoaded();
		Entry entry = DATA.get(name);
		return entry == null ? 0 : entry.postIds.size();
	}

	public static synchronized boolean contains(String name, String id)
	{
		ensureLoaded();
		Entry entry = DATA.get(name);
		return entry != null && entry.postIds.contains(id);
	}

	public static synchronized boolean toggle(String name, String id)
	{
		ensureLoaded();
		Entry entry = DATA.get(name);

		if (entry == null)
		{
			return false;
		}

		boolean nowIn = !entry.postIds.remove(id);

		if (nowIn)
		{
			entry.postIds.add(id);
		}

		save();
		return nowIn;
	}

	public static synchronized List<Row> rows()
	{
		ensureLoaded();
		List<Row> rows = new ArrayList<>();

		for (Map.Entry<String, Entry> entry : DATA.entrySet())
		{
			rows.add(new Row(entry.getValue().id, entry.getKey(), new ArrayList<>(entry.getValue().postIds)));
		}

		return rows;
	}

	// Union only: posts are added, never dropped, and a collection this client has never seen is created.
	// A local rename that has not reached the server yet keeps its name; otherwise the server's name is taken
	public static synchronized void mergeFromServer(List<Row> remote)
	{
		ensureLoaded();
		boolean changed = false;

		for (Row row : remote)
		{
			String name = normalize(row.name());

			if (name.isEmpty())
			{
				continue;
			}

			String localName = nameOf(row.id());

			if (localName == null)
			{
				changed |= adopt(row, name);
				continue;
			}

			Entry entry = DATA.get(localName);
			changed |= entry.postIds.addAll(row.postIds());

			if (!entry.renamed && !localName.equals(name) && !DATA.containsKey(name))
			{
				DATA.put(name, DATA.remove(localName));
				changed = true;
			}
		}

		if (changed)
		{
			write();
		}
	}

	// The same name created on two devices is one collection: their posts join under the smaller id so
	// every client settles on the same one instead of swapping ids on each sync
	private static boolean adopt(Row row, String name)
	{
		Entry existing = DATA.get(name);

		if (existing == null)
		{
			Entry entry = new Entry(row.id());
			entry.postIds.addAll(row.postIds());
			DATA.put(name, entry);
			return true;
		}

		boolean changed = existing.postIds.addAll(row.postIds());

		if (row.id().compareTo(existing.id) < 0)
		{
			existing.id = row.id();
			changed = true;
		}

		return changed;
	}

	// The server now carries every local rename, so the next merge may take its names again
	public static synchronized void markSynced()
	{
		boolean changed = false;

		for (Entry entry : DATA.values())
		{
			changed |= entry.renamed;
			entry.renamed = false;
		}

		if (changed)
		{
			write();
		}
	}

	public static String normalize(String name)
	{
		if (name == null)
		{
			return "";
		}

		String trimmed = name.trim();
		return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
	}

	private static String nameOf(String id)
	{
		for (Map.Entry<String, Entry> entry : DATA.entrySet())
		{
			if (entry.getValue().id.equals(id))
			{
				return entry.getKey();
			}
		}

		return null;
	}

	private static void ensureLoaded()
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

		try (Reader reader = Files.newBufferedReader(path))
		{
			JsonElement root = JsonParser.parseReader(reader);

			if (!root.isJsonObject())
			{
				return;
			}

			if (root.getAsJsonObject().has(KEY_COLLECTIONS))
			{
				readRows(root.getAsJsonObject());
				return;
			}

			readLegacy(root.getAsJsonObject());
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not read collections", e);
			Errors.report(Errors.COLLECTION);
		}
	}

	private static void readRows(JsonObject root)
	{
		for (JsonElement element : Json.arrayOf(root, KEY_COLLECTIONS))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject row = element.getAsJsonObject();
			String name = Json.stringOf(row, "name", "");
			String id = Json.stringOf(row, "id", null);

			if (name.isEmpty() || id == null || DATA.containsKey(name))
			{
				continue;
			}

			Entry entry = new Entry(id);
			entry.postIds.addAll(Json.listOf(row, "postIds"));
			entry.renamed = Json.boolOf(row, "renamed", false);
			DATA.put(name, entry);
		}
	}

	// The pre-sync file was a name -> ids map; ids are minted once and written straight back so they stay put
	private static void readLegacy(JsonObject root)
	{
		for (Map.Entry<String, JsonElement> entry : root.entrySet())
		{
			Entry created = new Entry(UUID.randomUUID().toString());

			if (entry.getValue().isJsonArray())
			{
				for (JsonElement id : entry.getValue().getAsJsonArray())
				{
					String value = Json.asString(id, null);

					if (value != null)
					{
						created.postIds.add(value);
					}
				}
			}

			DATA.put(entry.getKey(), created);
		}

		write();
	}

	private static void save()
	{
		write();
		Library.onLocalChange();
	}

	private static void write()
	{
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		JsonArray collections = new JsonArray();

		for (Map.Entry<String, Entry> entry : DATA.entrySet())
		{
			JsonArray ids = new JsonArray();

			for (String id : entry.getValue().postIds)
			{
				ids.add(id);
			}

			JsonObject row = new JsonObject();
			row.addProperty("id", entry.getValue().id);
			row.addProperty("name", entry.getKey());
			row.add("postIds", ids);

			if (entry.getValue().renamed)
			{
				row.addProperty("renamed", true);
			}

			collections.add(row);
		}

		JsonObject root = new JsonObject();
		root.add(KEY_COLLECTIONS, collections);

		try
		{
			Files.createDirectories(path.getParent());

			// Swapped in from a sibling temp file, so a crash mid-write cannot leave a truncated collections file
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(temporary))
			{
				writer.write(root.toString());
			}

			try
			{
				Files.move(temporary, path,
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException atomicFailed)
			{
				// Some filesystems refuse atomic moves
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.warn("Could not write collections", e);
			Errors.report(Errors.COLLECTION);
		}
	}

	private static final class Entry
	{
		private final LinkedHashSet<String> postIds = new LinkedHashSet<>();
		private String id;
		private boolean renamed;

		private Entry(String id)
		{
			this.id = id;
		}
	}
}

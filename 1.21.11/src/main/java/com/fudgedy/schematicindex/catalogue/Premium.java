package com.fudgedy.schematicindex.catalogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Partner-owned listings the server groups into paid / shard / booster; fetched once per launch, without file urls
public final class Premium
{
	public enum Type
	{
		PAID, SHARD, BOOSTER
	}

	public record Entry(SchematicEntry post, Type type, int shardPrice, String partnerName, String partnerUrl)
	{
	}

	private static final Map<Type, List<Entry>> sections = new EnumMap<>(Type.class);
	private static volatile Set<String> owned = Set.of();
	private static volatile boolean loading;
	private static volatile boolean loaded;

	private Premium()
	{
	}

	public static boolean loaded()
	{
		return loaded;
	}

	public static boolean owned(String postId)
	{
		return owned.contains(postId);
	}

	public static void markOwned(String postId)
	{
		Set<String> next = new HashSet<>(owned);
		next.add(postId);
		owned = Set.copyOf(next);
	}

	public static List<Entry> section(Type type)
	{
		synchronized (sections)
		{
			List<Entry> list = sections.get(type);
			return list == null ? List.of() : List.copyOf(list);
		}
	}

	// Safe to call on every tab open; the fetch runs at most once until refresh()
	public static void ensureLoaded()
	{
		if (loaded || loading)
		{
			return;
		}

		loading = true;
		Net.submit(() -> {
			try
			{
				JsonObject root = Backend.premium();
				Map<Type, List<Entry>> next = new EnumMap<>(Type.class);
				next.put(Type.PAID, parseGroup(root, "paid", Type.PAID));
				next.put(Type.SHARD, parseGroup(root, "shard", Type.SHARD));
				next.put(Type.BOOSTER, parseGroup(root, "booster", Type.BOOSTER));

				synchronized (sections)
				{
					sections.clear();
					sections.putAll(next);
				}

				// Ownership is per-account, so it only means anything once the player is verified
				if (McAuth.verified())
				{
					JsonObject mine = Backend.ownedPremium();
					owned = Set.copyOf(new HashSet<>(Json.listOf(mine, "owned")));
				}

				loaded = true;
			}
			finally
			{
				loading = false;
			}
		});
	}

	public static void refresh()
	{
		loaded = false;
		ensureLoaded();
	}

	private static List<Entry> parseGroup(@Nullable JsonObject root, String key, Type type)
	{
		List<Entry> out = new ArrayList<>();

		if (root == null || !root.has(key) || !root.get(key).isJsonArray())
		{
			return out;
		}

		for (JsonElement element : root.getAsJsonArray(key))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject o = element.getAsJsonObject();
			int price = Json.intOf(o, "shardPrice", 0);
			out.add(new Entry(Backend.parsePost(o), type, price, string(o, "partnerName"), string(o, "partnerUrl")));
		}

		return out;
	}

	private static String string(JsonObject o, String key)
	{
		return Json.stringOf(o, key, "");
	}
}

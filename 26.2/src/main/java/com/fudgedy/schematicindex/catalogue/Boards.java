package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.fx.Effects;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Top ten per leaderboard plus the player's own rank; the server rebuilds its boards every ten minutes, so a
// minute of client cache costs nothing
public final class Boards
{
	private static final long STALE_MS = 60_000L;
	private static final Map<String, Board> CACHE = new ConcurrentHashMap<>();
	private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
	private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

	public record Row(int rank, String ign, int value, int[] stops, @Nullable String effect,
			@Nullable CosmeticTags.Tag tag)
	{
	}

	// rank is null for an unranked or hidden player; mine is null when the board was read anonymously
	public record Mine(@Nullable Integer rank, int value, boolean optedOut)
	{
	}

	public record Board(List<Row> top, @Nullable Mine mine, long fetchedAt)
	{
	}

	private Boards()
	{
	}

	public static boolean enabled()
	{
		return RemoteContent.feature("leaderboards");
	}

	public static @Nullable Board board(String name)
	{
		return CACHE.get(name);
	}

	public static boolean failed(String name)
	{
		return FAILED.contains(name);
	}

	public static void refreshIfStale(String name)
	{
		Board cached = CACHE.get(name);

		if (!enabled() || (cached != null && System.currentTimeMillis() - cached.fetchedAt() < STALE_MS)
				|| !LOADING.add(name))
		{
			return;
		}

		Net.submit(() -> {
			try
			{
				fetch(name);
			}
			finally
			{
				LOADING.remove(name);
			}
		});
	}

	private static void fetch(String name)
	{
		boolean personal = McAuth.verified();
		String path = personal ? "/me/leaderboards/" + name : "/leaderboards/" + name + "?limit=10";
		Backend.ApiResult result = Backend.sendForApiResult("GET", path, null);
		JsonObject body = result.body();

		if (!result.ok() || body == null)
		{
			SchematicIndexMod.LOGGER.debug("GET {} failed with status {}", path, result.status());
			FAILED.add(name);
			return;
		}

		List<Row> rows = new ArrayList<>();
		String list = body.has("entries") ? "entries" : "top";

		for (JsonElement element : Json.arrayOf(body, list))
		{
			if (rows.size() >= 10 || !element.isJsonObject())
			{
				continue;
			}

			JsonObject o = element.getAsJsonObject();
			rows.add(new Row(Json.intOf(o, "rank", rows.size() + 1), Json.stringOf(o, "ign", ""), Json.intOf(o, "value", 0),
					Backend.parseStops(o, "stops"), effectOf(o), CosmeticTags.readTag(o.get("tag"))));
		}

		JsonObject me = Json.objectOf(body, "me");
		Mine mine = null;

		if (me != null)
		{
			JsonElement rank = me.get("rank");
			boolean ranked = rank != null && rank.isJsonPrimitive();
			mine = new Mine(ranked ? Json.asInt(rank, 0) : null, Json.intOf(me, "value", 0), Json.boolOf(me, "optedOut", false));
		}

		CACHE.put(name, new Board(List.copyOf(rows), mine, System.currentTimeMillis()));
		FAILED.remove(name);
	}

	// A board row names at most one animated effect; the first this build knows wins
	private static @Nullable String effectOf(JsonObject o)
	{
		for (String effect : Json.listOf(o, "effects"))
		{
			if (Effects.has(effect))
			{
				return effect;
			}
		}

		return null;
	}
}

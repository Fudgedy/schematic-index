package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

// The 0.8.0 quest fields (reward tag, limited window) that Shards does not parse, read from the same panel
public final class QuestExtras
{
	private static final int GOLD = 0xE8C55E;
	private static final AtomicBoolean LOADING = new AtomicBoolean();

	private static volatile Map<String, Extra> extras = Map.of();

	private record Extra(boolean limited, long untilMs, @Nullable CosmeticTags.Tag rewardTag)
	{
	}

	private QuestExtras()
	{
	}

	public static void refresh()
	{
		if (!McAuth.verified() || !LOADING.compareAndSet(false, true))
		{
			return;
		}

		Net.submit(() -> {
			try
			{
				fetch();
			}
			finally
			{
				LOADING.set(false);
			}
		});
	}

	// "+ {Tag}" for a reward tag and "Ends in 3d" for a limited quest, or null when the quest has neither
	public static @Nullable Component suffix(String questId)
	{
		Extra extra = extras.get(questId);

		if (extra == null || (extra.rewardTag() == null && !extra.limited()))
		{
			return null;
		}

		MutableComponent out = Component.empty();

		if (extra.rewardTag() != null)
		{
			out.append(Component.literal("+ ")).append(Cosmetics.tagOf(extra.rewardTag()));
		}

		if (extra.limited() && extra.untilMs() > 0L)
		{
			String left = "Ends in " + remaining(extra.untilMs() - System.currentTimeMillis());
			out.append(Component.literal((extra.rewardTag() != null ? " " : "") + left).withStyle(Style.EMPTY.withColor(GOLD)));
		}

		return out;
	}

	private static void fetch()
	{
		JsonObject panel = Backend.myShards();

		if (panel == null)
		{
			SchematicIndexMod.LOGGER.debug("GET /me/shards for quest extras failed");
			return;
		}

		Map<String, Extra> parsed = new HashMap<>();

		for (JsonElement element : Json.arrayOf(panel, "quests"))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject quest = element.getAsJsonObject();
			parsed.put(Json.stringOf(quest, "id", ""), new Extra(Json.boolOf(quest, "limited", false),
					Json.longOf(quest, "untilMs", 0L), CosmeticTags.readTag(quest.get("rewardTag"))));
		}

		extras = Map.copyOf(parsed);
	}

	private static String remaining(long ms)
	{
		long hours = Math.max(0L, ms) / 3_600_000L;
		return hours >= 24L ? hours / 24L + "d" : Math.max(1L, hours) + "h";
	}
}

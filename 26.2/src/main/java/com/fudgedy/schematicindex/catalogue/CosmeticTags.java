package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Client mirror of the earned tags an account holds (/me/tags). Tags are authored by staff and either
// bought with shards or handed out; a player never builds one, so nothing here edits a tag's own styling
public final class CosmeticTags
{
	public record Tag(int id, String label, String bracketOpen, String bracketClose, boolean gradient,
			int[] colors, Set<String> styles, int price)
	{
	}

	public static final int NONE = -1;

	private static volatile List<Tag> owned = List.of();
	private static volatile List<Tag> shop = List.of();
	private static volatile int equipped = NONE;
	private static volatile boolean loading;

	private CosmeticTags()
	{
	}

	public static List<Tag> owned()
	{
		return owned;
	}

	public static List<Tag> shop()
	{
		return shop;
	}

	public static int equipped()
	{
		return equipped;
	}

	public static Tag equippedTag()
	{
		for (Tag tag : owned)
		{
			if (tag.id() == equipped)
			{
				return tag;
			}
		}

		return null;
	}

	public static void refresh()
	{
		if (loading)
		{
			return;
		}

		loading = true;
		Net.submit(() ->
		{
			try
			{
				apply(Backend.myTags());
			}
			finally
			{
				loading = false;
			}
		});
	}

	public static void clear()
	{
		owned = List.of();
		shop = List.of();
		equipped = NONE;
	}

	public static void buy(Tag tag, Runnable onBought)
	{
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.buyTag(tag.id());

			if (result.ok())
			{
				apply(result.body());
				Minecraft.getInstance().execute(() ->
				{
					Theme.beaconActivate();
					Toasts.push("Redeemed " + tag.price() + " Shards", "The " + tag.label() + " tag is yours.",
							new ItemStack(Items.AMETHYST_SHARD));
					onBought.run();
				});
			}
			else
			{
				Minecraft.getInstance().execute(() -> Toasts.shopRefusal(result, "this tag", Errors.SHARD_BUY));
			}
		});
	}

	// Wears a tag, or clears the worn one when id is NONE; the server is the record of what is equipped
	public static void equip(int id, Runnable onEquipped)
	{
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.equipTag(id);

			if (result.ok())
			{
				apply(result.body());
				CosmeticColors.publishLoadout();
				Minecraft.getInstance().execute(onEquipped);
			}
		});
	}

	private static void apply(JsonObject o)
	{
		if (o == null)
		{
			return;
		}

		if (o.has("owned") && o.get("owned").isJsonArray())
		{
			owned = readTags(o, "owned");
		}

		if (o.has("shop") && o.get("shop").isJsonArray())
		{
			shop = readTags(o, "shop");
		}

		if (o.has("equipped"))
		{
			equipped = Json.intOf(o, "equipped", NONE);
		}

		if (o.has("balance") && !o.get("balance").isJsonNull())
		{
			McAuth.setShards(Json.intOf(o, "balance", McAuth.shards()));
		}
	}

	private static List<Tag> readTags(JsonObject o, String key)
	{
		List<Tag> tags = new ArrayList<>();

		for (JsonElement element : o.getAsJsonArray(key))
		{
			Tag tag = readTag(element);

			if (tag != null)
			{
				tags.add(tag);
			}
		}

		return List.copyOf(tags);
	}

	// Null for an absent or null tag, so a player wearing none reads as none
	public static @Nullable Tag readTag(@Nullable JsonElement element)
	{
		if (element == null || !element.isJsonObject())
		{
			return null;
		}

		JsonObject row = element.getAsJsonObject();
		return new Tag(Json.intOf(row, "id", NONE), Json.stringOf(row, "label", ""),
				Json.stringOf(row, "bracketOpen", "["), Json.stringOf(row, "bracketClose", "]"),
				Json.boolOf(row, "gradient", false), colors(row), styles(row), Json.intOf(row, "price", NONE));
	}

	// Minecraft's own formatting flags, authored per tag; anything unrecognised is not applied
	private static Set<String> styles(JsonObject row)
	{
		if (!row.has("styles") || !row.get("styles").isJsonArray())
		{
			return Set.of();
		}

		return Set.copyOf(Json.listOf(row, "styles"));
	}

	private static int[] colors(JsonObject row)
	{
		if (!row.has("colors") || !row.get("colors").isJsonArray())
		{
			return new int[] {0xFFFFFF};
		}

		List<JsonElement> raw = row.getAsJsonArray("colors").asList();

		if (raw.isEmpty())
		{
			return new int[] {0xFFFFFF};
		}

		int[] colors = new int[raw.size()];

		for (int i = 0; i < colors.length; i++)
		{
			colors[i] = Json.asInt(raw.get(i), 0xFFFFFF) & 0xFFFFFF;
		}

		return colors;
	}
}

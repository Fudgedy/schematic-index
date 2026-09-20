package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Client mirror of the owned palette (/me/cosmetics); a colour is bought at a picked hex and sold back for less
public final class CosmeticColors
{
	public record Owned(int id, int hex, int paid)
	{
	}

	private static volatile List<Owned> colors = List.of();
	private static volatile Set<String> ownedPresets = Set.of();
	private static volatile Set<String> ownedEffects = Set.of();
	private static volatile Map<String, Integer> effectPrices = Map.of(Cosmetics.SHINE, 350, Cosmetics.FLOW, 400);
	private static volatile int balance;
	private static volatile int price = 175;
	private static volatile int refundValue = 75;
	private static volatile int presetPrice = 300;
	private static volatile int max = 18;
	private static volatile boolean loading;
	private static volatile String publishedLoadout = "";

	private CosmeticColors()
	{
	}

	public static List<Owned> colors()
	{
		return colors;
	}

	public static int count()
	{
		return colors.size();
	}

	public static int max()
	{
		return max;
	}

	public static int price()
	{
		return price;
	}

	public static int refundValue()
	{
		return refundValue;
	}

	public static int presetPrice()
	{
		return presetPrice;
	}

	public static int effectPrice(String id)
	{
		return effectPrices.getOrDefault(id, 0);
	}

	public static boolean ownsEffect(String id)
	{
		return ownedEffects.contains(id);
	}

	public static int balance()
	{
		return balance;
	}

	public static int nextPrice()
	{
		return colors.isEmpty() ? 0 : price;
	}

	public static boolean owns(int id)
	{
		return hexOf(id) >= 0;
	}

	// A bought preset is an owned wearable gradient by name; it never enters the colour palette
	public static boolean ownsPreset(String name)
	{
		return ownedPresets.contains(name);
	}

	public static boolean isRefundable(int id)
	{
		for (Owned owned : colors)
		{
			if (owned.id() == id)
			{
				return owned.paid() != 0;
			}
		}

		return false;
	}

	public static int hexOf(int id)
	{
		for (Owned owned : colors)
		{
			if (owned.id() == id)
			{
				return owned.hex();
			}
		}

		return -1;
	}

	// Restoring a saved loadout calls this too, so an unchanged value is skipped rather than posted on every refresh
	public static void publishLoadout()
	{
		publishLoadout(false);
	}

	// userTriggered surfaces a refusal in the modal; the background callers stay silent
	public static void publishLoadout(boolean userTriggered)
	{
		if (!McAuth.verified())
		{
			return;
		}

		int[] stops = Cosmetics.wornStops();
		Set<String> effects = Cosmetics.effects();
		String signature = Arrays.toString(stops) + effects;

		if (signature.equals(publishedLoadout))
		{
			return;
		}

		publishedLoadout = signature;
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.publishLoadout(stops, effects);
			SchematicIndexMod.LOGGER.debug("Published loadout {} -> {}", signature, result.status());

			if (result.ok())
			{
				Shards.pokeSoon();
				return;
			}

			// A 4xx would answer the same on every poll, so it stays marked as sent until the loadout changes
			if (result.status() == -1 || result.status() >= 500)
			{
				publishedLoadout = "";
			}

			if (userTriggered)
			{
				Errors.report(Errors.COSMETIC_PUBLISH);
			}
		});
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
				apply(Backend.myCosmetics());
			}
			finally
			{
				loading = false;
			}
		});
	}

	// The next verified account must not inherit this one's palette, nor skip publishing its own loadout
	public static void clear()
	{
		colors = List.of();
		ownedPresets = Set.of();
		ownedEffects = Set.of();
		balance = 0;
		publishedLoadout = "";
	}

	public static void buy(int hex, Runnable onBought)
	{
		int cost = nextPrice();
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.buyColor(hex);

			if (result.ok())
			{
				apply(result.body());
				Minecraft.getInstance().execute(() ->
				{
					Theme.beaconActivate();
					Toasts.push(cost > 0 ? "Redeemed " + cost + " Shards" : "Colour Unlocked",
							"Your palette gained #" + String.format("%06X", hex) + ".",
							new ItemStack(Items.AMETHYST_SHARD));
					onBought.run();
				});
			}
			else
			{
				Minecraft.getInstance().execute(() -> Toasts.shopRefusal(result, "this colour", Errors.SHARD_BUY));
			}
		});
	}

	public static void refund(int id, Runnable onRefunded)
	{
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.refundColor(id);

			if (result.ok())
			{
				int refunded = Json.intOf(result.body(), "refunded", 0);
				apply(result.body());
				Minecraft.getInstance().execute(() ->
				{
					Toasts.push("Refunded " + refunded + " Shards", "The colour left your palette.",
							new ItemStack(Items.AMETHYST_SHARD));
					onRefunded.run();
				});
			}
			else if (result.is("not_refundable") || result.is("no_color"))
			{
				Minecraft.getInstance().execute(() -> Toasts.push("Refund Failed",
						"That colour could not be refunded.", new ItemStack(Items.AMETHYST_SHARD)));
			}
			else
			{
				Minecraft.getInstance().execute(() -> Toasts.refusal(result, Errors.SHARD_BUY));
			}
		});
	}

	// Buys a named preset; the server owns the colour list, so the mod only sends which one
	public static void buyPreset(String name, Runnable onBought)
	{
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.buyPreset(name);

			if (result.ok())
			{
				apply(result.body());
				Minecraft.getInstance().execute(() ->
				{
					Theme.beaconActivate();
					Toasts.push("Redeemed " + presetPrice + " Shards", "The " + name + " palette is yours.",
							new ItemStack(Items.AMETHYST_SHARD));
					onBought.run();
				});
			}
			else
			{
				Minecraft.getInstance().execute(() -> Toasts.shopRefusal(result, "this preset", Errors.SHARD_BUY));
			}
		});
	}

	public static void buyEffect(String id, String label, int price, Runnable onBought)
	{
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.buyEffect(id);

			if (result.ok())
			{
				apply(result.body());
				Minecraft.getInstance().execute(() ->
				{
					Theme.beaconActivate();
					Toasts.push("Redeemed " + price + " Shards", "The " + label + " effect is yours.",
							new ItemStack(Items.AMETHYST_SHARD));
					onBought.run();
				});
			}
			else
			{
				Minecraft.getInstance().execute(() -> Toasts.shopRefusal(result, "this effect", Errors.SHARD_BUY));
			}
		});
	}

	private static void apply(JsonObject o)
	{
		if (o == null)
		{
			return;
		}

		// Read before the palette, since restoring the loadout prunes effects against what is owned
		if (o.has("effects") && o.get("effects").isJsonObject())
		{
			applyEffects(o.getAsJsonObject("effects"));
		}

		if (o.has("colors") && o.get("colors").isJsonArray())
		{
			List<Owned> owned = new ArrayList<>();

			for (JsonElement element : o.getAsJsonArray("colors"))
			{
				if (!element.isJsonObject())
				{
					continue;
				}

				JsonObject row = element.getAsJsonObject();
				// An older server does not send paid; unknown stays refundable
				int paid = Json.intOf(row, "paid", -1);
				owned.add(new Owned(Json.intOf(row, "id", -1), Json.intOf(row, "hex", 0xFFFFFF) & 0xFFFFFF, paid));
			}

			colors = List.copyOf(owned);
			// The loadout is plain client-thread state read by the nametag renderer, so it is restored there
			Minecraft.getInstance().execute(Cosmetics::restore);
		}

		if (o.has("ownedPresets") && o.get("ownedPresets").isJsonArray())
		{
			ownedPresets = Set.copyOf(Json.listOf(o, "ownedPresets"));
		}

		if (o.has("balance") && !o.get("balance").isJsonNull())
		{
			balance = Json.intOf(o, "balance", balance);
			McAuth.setShards(balance);
		}

		price = Json.intOf(o, "colorPrice", price);
		refundValue = Json.intOf(o, "refund", refundValue);
		presetPrice = Json.intOf(o, "presetPrice", presetPrice);
		max = Json.intOf(o, "max", max);
	}

	private static void applyEffects(JsonObject o)
	{
		if (o.has("owned") && o.get("owned").isJsonArray())
		{
			ownedEffects = Set.copyOf(Json.listOf(o, "owned"));
		}

		if (o.has("prices") && o.get("prices").isJsonObject())
		{
			effectPrices = prices(o.getAsJsonObject("prices"), effectPrices);
		}
	}

	private static Map<String, Integer> prices(JsonObject o, Map<String, Integer> defaults)
	{
		Map<String, Integer> prices = new HashMap<>(defaults);

		for (Map.Entry<String, JsonElement> entry : o.entrySet())
		{
			int value = Json.asInt(entry.getValue(), -1);

			if (value >= 0)
			{
				prices.put(entry.getKey(), value);
			}
		}

		return Map.copyOf(prices);
	}
}

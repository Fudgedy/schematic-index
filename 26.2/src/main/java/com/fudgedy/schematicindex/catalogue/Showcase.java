package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// The up to three achievement tags a player pins to the top of their public profile
public final class Showcase
{
	public static final int MAX = 3;
	private static final long STALE_MS = 5L * 60L * 1000L;
	private static final AtomicBoolean LOADING = new AtomicBoolean();

	private static volatile List<Integer> pinned = List.of();
	private static volatile long fetchedAt;
	private static volatile boolean saving;

	private Showcase()
	{
	}

	public static List<Integer> pinned()
	{
		return pinned;
	}

	public static boolean isPinned(int tagId)
	{
		return pinned.contains(tagId);
	}

	public static boolean isSaving()
	{
		return saving;
	}

	public static void refreshIfStale()
	{
		if (!McAuth.verified() || System.currentTimeMillis() - fetchedAt < STALE_MS || !LOADING.compareAndSet(false, true))
		{
			return;
		}

		Net.submit(() -> {
			try
			{
				Backend.ApiResult result = Backend.sendForApiResult("GET", "/me/showcase", null);

				if (result.ok() && result.body() != null)
				{
					pinned = read(result.body());
				}
				else
				{
					SchematicIndexMod.LOGGER.debug("GET /me/showcase failed with status {}", result.status());
				}
			}
			finally
			{
				fetchedAt = System.currentTimeMillis();
				LOADING.set(false);
			}
		});
	}

	// Pins or unpins one tag; the list shows the change at once and snaps back if the server refuses it
	public static void toggle(int tagId)
	{
		if (saving)
		{
			return;
		}

		List<Integer> before = pinned;
		List<Integer> next = new ArrayList<>(before);

		if (!next.remove(Integer.valueOf(tagId)))
		{
			if (next.size() >= MAX)
			{
				Toasts.push("Showcase full", "Unpin an achievement to make room.", new ItemStack(Items.NETHER_STAR));
				return;
			}

			next.add(tagId);
		}

		saving = true;
		pinned = List.copyOf(next);
		JsonArray ids = new JsonArray();

		for (int id : next)
		{
			ids.add(id);
		}

		JsonObject body = new JsonObject();
		body.add("pinned", ids);
		Net.submit(() -> {
			try
			{
				Backend.ApiResult result = Backend.sendForApiResult("PUT", "/me/showcase", body.toString());
				SchematicIndexMod.LOGGER.debug("PUT /me/showcase -> {} {}", result.status(), result.error());

				if (result.ok())
				{
					if (result.body() != null && result.body().has("pinned"))
					{
						pinned = read(result.body());
					}

					return;
				}

				pinned = before;
				Minecraft.getInstance().execute(() -> refused(result));
			}
			finally
			{
				saving = false;
			}
		});
	}

	private static void refused(Backend.ApiResult result)
	{
		if (result.unverified() || result.is("not_verified"))
		{
			Toasts.refusal(result, Errors.SHOWCASE_SAVE);
			return;
		}

		String text = result.is("bad_showcase") ? "Only achievements you own can be pinned." : "Try again in a moment.";
		Toasts.push("Couldn't save your showcase", text + " (" + Errors.SHOWCASE_SAVE + ")", new ItemStack(Items.BARRIER));
	}

	private static List<Integer> read(JsonObject body)
	{
		List<Integer> ids = new ArrayList<>();

		for (JsonElement element : Json.arrayOf(body, "pinned"))
		{
			int id = Json.asInt(element, -1);

			if (id >= 0 && ids.size() < MAX)
			{
				ids.add(id);
			}
		}

		return List.copyOf(ids);
	}
}

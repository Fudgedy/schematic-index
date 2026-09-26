package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DashboardStats
{
	static final String[] RANGE_LABELS = {"Today", "7 Days", "30 Days", "All Time"};
	private static final String[] RANGE_QUERIES = {"days=1", "days=7", "days=30", "range=all"};
	private static final int DEFAULT_RANGE = 2;
	private final DashboardPage page;
	boolean loading;
	public boolean loaded;
	// Revisiting the Upload tab reuses the cached stats instead of flashing a skeleton
	long loadedAt;
	boolean ok;
	boolean failed;
	int postsCount;
	int views;
	int downloads;
	int likes;
	int followers;
	double rating;
	int ratingCount;
	int range = storedRange();
	// The range whose data is on screen, restored when a switch fails
	private int shownRange = this.range;
	// Only the newest request applies, so fast switching never lands an older range last
	private int request;
	boolean weekly;
	String[] days = new String[0];
	int[] viewSeries = new int[0];
	int[] downloadSeries = new int[0];
	int[] likeSeries = new int[0];
	int[] starSeries = new int[0];
	public final List<SchematicEntry> posts = new ArrayList<>();
	// Shown with an "In review" badge so an upload never looks like it vanished
	final Set<String> pendingIds = new HashSet<>();
	final Set<String> creditedIds = new HashSet<>();
	// Post id to daily views/downloads/likes series
	final Map<String, int[][]> postSeries = new HashMap<>();

	DashboardStats(DashboardPage page)
	{
		this.page = page;
	}

	public void refresh()
	{
		this.loaded = false;
	}

	boolean isSwitching()
	{
		return this.loading && this.ok && this.range != this.shownRange;
	}

	void selectRange(int range)
	{
		if (range == this.range || range < 0 || range >= RANGE_QUERIES.length)
		{
			return;
		}

		this.range = range;
		Settings.setNumber(SettingsKeys.CREATOR_GRAPH_RANGE, range);
		this.load();
	}

	// A reload keeps the stats already on screen until the new ones land, so nothing flashes or jumps
	void load()
	{
		String code = UploaderAccess.code();

		if (!UploaderAccess.unlocked())
		{
			return;
		}

		int range = this.range;
		int request = ++this.request;
		this.loading = true;
		this.failed = false;
		SchematicIndexMod.LOGGER.debug("Loading creator stats for {}", RANGE_QUERIES[range]);
		new Thread(() -> {
			JsonObject data = Backend.myStats(code, RANGE_QUERIES[range]);
			Minecraft.getInstance().execute(() -> {
				if (request != this.request)
				{
					return;
				}

				this.loading = false;
				this.loaded = true;

				if (data != null)
				{
					this.apply(data);
					this.shownRange = range;
					this.ok = true;
					this.loadedAt = System.currentTimeMillis();
					this.page.graph.animStart = System.currentTimeMillis();
					return;
				}

				SchematicIndexMod.LOGGER.warn("Creator stats for {} failed to load", RANGE_QUERIES[range]);

				// Only a failed first load reaches the Retry state, since ok gates it
				if (!this.ok)
				{
					this.failed = true;
					return;
				}

				if (range != this.shownRange)
				{
					this.range = this.shownRange;
					Toasts.push("Couldn't load your stats", "Try again in a moment. (" + Errors.CREATOR_STATS + ")",
							new ItemStack(Items.BARRIER));
				}
			});
		}, "schematicindex-mystats").start();
	}

	private void apply(JsonObject data)
	{
		try
		{
			// Tiles follow the selected range when the server sends one, else they stay all-time
			JsonObject totals = data.has("rangeTotals") && data.get("rangeTotals").isJsonObject()
					? data.getAsJsonObject("rangeTotals") : Json.objectOf(data, "totals");
			this.postsCount = Json.intOf(totals, "posts", 0);
			this.views = Json.intOf(totals, "views", 0);
			this.downloads = Json.intOf(totals, "downloads", 0);
			this.likes = Json.intOf(totals, "likes", 0);
			this.followers = Json.intOf(totals, "followers", 0);
			this.rating = Json.doubleOf(totals, "rating", 0.0);
			this.ratingCount = Json.intOf(totals, "ratingCount", 0);

			this.days = Json.listOf(data, "days").toArray(new String[0]);
			this.weekly = "week".equals(Json.stringOf(data, "bucket", "day"));

			JsonObject series = Json.objectOf(data, "series");
			this.viewSeries = toIntArray(Json.arrayOf(series, "views"));
			this.downloadSeries = toIntArray(Json.arrayOf(series, "downloads"));
			this.likeSeries = toIntArray(Json.arrayOf(series, "likes"));
			this.starSeries = toIntArray(Json.arrayOf(series, "stars"));

			this.postSeries.clear();

			if (data.has("postSeries") && data.get("postSeries").isJsonObject())
			{
				JsonObject perPost = data.getAsJsonObject("postSeries");

				for (Map.Entry<String, JsonElement> entry : perPost.entrySet())
				{
					if (!entry.getValue().isJsonObject())
					{
						continue;
					}

					JsonObject s = entry.getValue().getAsJsonObject();
					this.postSeries.put(entry.getKey(), new int[][]
					{
							toIntArray(Json.arrayOf(s, "views")),
							toIntArray(Json.arrayOf(s, "downloads")),
							toIntArray(Json.arrayOf(s, "likes")),
							toIntArray(Json.arrayOf(s, "stars")),
					});
				}
			}

			if (this.page.selectedPost != null && !this.postSeries.containsKey(this.page.selectedPost))
			{
				this.page.selectedPost = null;
			}

			this.posts.clear();
			this.pendingIds.clear();
			this.creditedIds.clear();
			Map<String, String> catalogueDesigners = catalogueDesigners();

			for (JsonElement element : Json.arrayOf(data, "posts"))
			{
				if (!element.isJsonObject())
				{
					continue;
				}

				JsonObject p = element.getAsJsonObject();
				String id = Json.stringOf(p, "id", null);

				if (id == null)
				{
					continue;
				}

				if ("pending".equals(Json.stringOf(p, "status", "")))
				{
					this.pendingIds.add(id);
				}

				// Credited as the designer on someone else's upload, so it is theirs to edit
				if ("credited".equals(Json.stringOf(p, "role", "")))
				{
					this.creditedIds.add(id);
				}

				this.posts.add(new SchematicEntry(
						id, Json.stringOf(p, "title", ""), Json.stringOf(p, "thumbnailName", ""),
						Json.stringOf(p, "poster", ""), designerOf(p, id, catalogueDesigners), Category.fromName(Json.stringOf(p, "category", "")),
						0, 0, 0, 0, Json.intOf(p, "downloads", 0), Json.intOf(p, "likes", 0), Json.longOf(p, "postedAt", 0L),
						"", 0, -1, -1, false, Json.stringOf(p, "thumbnailUrl", null),
						List.of(), List.of(), null, null, 0L, false, 0.0, Json.intOf(p, "views", 0),
					0.0, 0, 0, List.of()));
			}

			this.page.cacheDirty = true;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not apply my stats", e);
		}
	}

	private static int storedRange()
	{
		long stored = Settings.number(SettingsKeys.CREATOR_GRAPH_RANGE, DEFAULT_RANGE);
		return stored < 0 || stored >= RANGE_QUERIES.length ? DEFAULT_RANGE : (int) stored;
	}

	// Older servers leave designer out of /me/stats, so the public catalogue fills it in
	private static String designerOf(JsonObject post, String id, Map<String, String> catalogueDesigners)
	{
		String designer = Json.stringOf(post, "designer", "");
		return designer.isBlank() ? catalogueDesigners.getOrDefault(id, "") : designer;
	}

	private static Map<String, String> catalogueDesigners()
	{
		Map<String, String> designers = new HashMap<>();

		for (SchematicEntry entry : Catalogue.posts())
		{
			if (entry.designer() != null && !entry.designer().isBlank())
			{
				designers.put(entry.id(), entry.designer());
			}
		}

		return designers;
	}

	private static int[] toIntArray(JsonArray array)
	{
		int[] out = new int[array.size()];

		for (int i = 0; i < array.size(); i++)
		{
			out[i] = Json.asInt(array.get(i), 0);
		}

		return out;
	}
}

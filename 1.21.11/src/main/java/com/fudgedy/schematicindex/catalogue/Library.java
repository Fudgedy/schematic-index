package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// The local files stay the source of truth and keep working offline; the account copy is only ever unioned in
public final class Library
{
	private static final long DEBOUNCE_MS = 2000L;
	// A separate debounce lock, so a save on the render thread never waits behind an in-flight PUT
	private static final Object PUSH_LOCK = new Object();
	private static final Object DEBOUNCE_LOCK = new Object();

	// Echoed back as the next PUT's base exactly as received, so its type never matters here
	private static volatile JsonElement updatedAt;
	private static ScheduledFuture<?> pending;
	// Once per session: a sync that keeps failing must not stack modals behind every save
	private static volatile boolean reported;

	private Library()
	{
	}

	public static void sync()
	{
		Net.submit(() ->
		{
			Backend.ApiResult result = Backend.myLibrary();
			SchematicIndexMod.LOGGER.debug("GET /me/library -> {}", result.status());

			if (!result.ok() || result.body() == null)
			{
				return;
			}

			merge(result.body());
			push();
		});
	}

	public static void onLocalChange()
	{
		if (!McAuth.verified())
		{
			return;
		}

		synchronized (DEBOUNCE_LOCK)
		{
			if (pending != null)
			{
				pending.cancel(false);
			}

			pending = Net.scheduler().schedule(() -> Net.submit(Library::push), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
	}

	private static void push()
	{
		Backend.ApiResult result;

		synchronized (PUSH_LOCK)
		{
			result = Backend.putLibrary(body());
			SchematicIndexMod.LOGGER.debug("PUT /me/library -> {}", result.status());

			// Another client wrote first; its copy is unioned in and the merged result sent once more
			if (result.status() == 409 && "stale".equals(result.error()))
			{
				merge(Json.objectOf(result.body(), "library"));
				result = Backend.putLibrary(body());
				SchematicIndexMod.LOGGER.debug("PUT /me/library retry -> {}", result.status());
			}

			if (result.ok())
			{
				accept(result.body());
				CollectionStore.markSynced();
				return;
			}
		}

		if (result.status() != -1 && !reported)
		{
			reported = true;
			Errors.report(Errors.LIBRARY_SYNC);
		}
	}

	private static void merge(JsonObject library)
	{
		if (library == null)
		{
			return;
		}

		Bookmarks.mergeSavedFromServer(Json.listOf(library, "saved"));
		List<CollectionStore.Row> rows = new ArrayList<>();

		for (JsonElement element : Json.arrayOf(library, "collections"))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject row = element.getAsJsonObject();
			String id = Json.stringOf(row, "id", null);
			String name = Json.stringOf(row, "name", null);

			if (id != null && name != null)
			{
				rows.add(new CollectionStore.Row(id, name, Json.listOf(row, "postIds")));
			}
		}

		CollectionStore.mergeFromServer(rows);
		accept(library);
	}

	private static void accept(JsonObject library)
	{
		JsonElement stamp = library == null ? null : library.get("updatedAt");
		updatedAt = stamp == null || stamp.isJsonNull() ? null : stamp.deepCopy();
	}

	private static String body()
	{
		JsonArray saved = new JsonArray();

		for (String id : Bookmarks.savedOrder())
		{
			saved.add(id);
		}

		JsonArray collections = new JsonArray();

		for (CollectionStore.Row row : CollectionStore.rows())
		{
			JsonArray postIds = new JsonArray();

			for (String id : row.postIds())
			{
				postIds.add(id);
			}

			JsonObject out = new JsonObject();
			out.addProperty("id", row.id());
			out.addProperty("name", row.name());
			out.add("postIds", postIds);
			collections.add(out);
		}

		JsonObject out = new JsonObject();
		out.add("saved", saved);
		out.add("collections", collections);
		out.add("baseUpdatedAt", updatedAt);
		return out.toString();
	}
}

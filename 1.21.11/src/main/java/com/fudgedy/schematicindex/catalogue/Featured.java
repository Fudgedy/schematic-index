package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

// Today's Build of the Day, pulled anonymously; the payload is a full post, so the banner opens it without a second fetch
public final class Featured
{
	private static final long STALE_MS = 5L * 60L * 1000L;
	private static final AtomicBoolean LOADING = new AtomicBoolean();

	private static volatile @Nullable Today today;
	private static volatile long fetchedAt;

	public record Today(String day, SchematicEntry entry)
	{
	}

	private Featured()
	{
	}

	public static void refreshIfStale()
	{
		if (!RemoteContent.feature("buildOfTheDay") || System.currentTimeMillis() - fetchedAt < STALE_MS
				|| !LOADING.compareAndSet(false, true))
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
				fetchedAt = System.currentTimeMillis();
				LOADING.set(false);
			}
		});
	}

	public static @Nullable Today today()
	{
		return RemoteContent.feature("buildOfTheDay") ? today : null;
	}

	public static boolean isFeatured(String postId)
	{
		Today current = today();
		return current != null && current.entry().id().equals(postId);
	}

	private static void fetch()
	{
		JsonObject body = Backend.getJsonAnon("/featured/today");

		if (body == null)
		{
			SchematicIndexMod.LOGGER.debug("GET /featured/today failed");
			return;
		}

		JsonObject post = Json.objectOf(body, "post");

		if (post == null || !post.has("id"))
		{
			today = null;
			return;
		}

		today = new Today(Json.stringOf(body, "day", ""), Backend.parsePost(post));
	}
}

package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Catalogue
{
	public enum State
	{
		LOADING,
		READY,
		OFFLINE
	}

	public enum Sort
	{
		TRENDING("Trending"),
		NEWEST("Newest"),
		DOWNLOADS("Most downloaded"),
		LIKES("Most liked"),
		RATED("Highest rated");

		private final String label;

		Sort(String label)
		{
			this.label = label;
		}

		public String label()
		{
			return this.label;
		}
	}

	private static final String SNAPSHOT_FILE = SchematicIndexMod.MOD_ID + "-catalogue-cache.json";

	private static volatile State state = State.LOADING;
	private static volatile boolean started;
	private static volatile List<SchematicEntry> posts = List.of();
	private static volatile long revision;
	private static volatile long lastRefresh;

	// The grid is showing the offline snapshot, so the GUI can badge it and keep the rows read-only
	private static volatile boolean stale;

	// The first successful load seeds this silently, so opening the menu never toasts the whole catalogue
	private static final Set<String> KNOWN_POST_IDS = ConcurrentHashMap.newKeySet();
	private static volatile boolean knownInitialized;

	private static final long SOFT_REFRESH_INTERVAL_MS = 60_000L;
	private static final long SNAPSHOT_MIN_INTERVAL_MS = 180_000L;

	private static ScheduledFuture<?> pendingSnapshot;
	private static int snapshotFingerprint;
	private static long snapshotWrittenAt;
	private static final String INDEX_PATH = "/index?limit=60&lite=1";
	private static final int DETAILS_LIMIT = 64;

	// Rows already fetched whole, re-applied after every refresh so a lite page cannot strip them again
	private static final Map<String, SchematicEntry> DETAILS_BY_ID = new LinkedHashMap<>(16, 0.75F, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, SchematicEntry> eldest)
		{
			return this.size() > DETAILS_LIMIT;
		}
	};

	// The last parsed body per index page, so a 304 can be answered from memory without re-parsing
	private static final Map<String, List<SchematicEntry>> PAGE_BY_PATH = new ConcurrentHashMap<>();
	private static final Map<String, String> CURSOR_BY_PATH = new ConcurrentHashMap<>();

	private Catalogue()
	{
	}

	public static State state()
	{
		return state;
	}

	public static List<SchematicEntry> posts()
	{
		return posts;
	}

	public static long revision()
	{
		return revision;
	}

	public static boolean isStale()
	{
		return stale;
	}

	private static synchronized void setPosts(List<SchematicEntry> next)
	{
		posts = next;
		revision++;
	}

	public static void ensureLoaded()
	{
		if (started)
		{
			refresh(true);
			return;
		}

		started = true;
		refresh(false);
	}

	public static void refresh()
	{
		refresh(false);
	}

	private static void refresh(boolean soft)
	{
		if (!Backend.configured())
		{
			// Consent-gated, not a live failure, so a snapshot already on screen stays visible
			if (!stale)
			{
				setPosts(List.of());
			}

			state = State.OFFLINE;
			return;
		}

		if (soft && lastRefresh != 0 && System.currentTimeMillis() - lastRefresh < SOFT_REFRESH_INTERVAL_MS)
		{
			return;
		}

		if (!soft)
		{
			state = State.LOADING;
		}

		Net.submit(() -> {
			try
			{
				if (soft && canSoftMerge())
				{
					softRefresh();
					return;
				}

				commit(fetchAll().entries());
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.info("Catalogue fetch failed: {}", e.toString());
				Errors.report(Errors.NETWORK);

				if (!soft)
				{
					state = State.OFFLINE;
				}
			}
		});
	}

	// Page 0 lands first so a new post shows without waiting on the deeper pages; the full walk then
	// follows, mostly as 304s, so an edit, hide or deletion further down reaches open clients too
	private static void softRefresh()
	{
		Page head = fetchFirstPage();

		if (head == null)
		{
			return;
		}

		boolean headPublished = false;

		if (head.changed())
		{
			List<SchematicEntry> merged = mergeHead(head.entries());

			if (merged != null)
			{
				publish(merged);
				headPublished = true;
			}
		}

		Walk walk = fetchAll();

		if (walk.changed())
		{
			commit(walk.entries());
			return;
		}

		if (headPublished)
		{
			settle();
			return;
		}

		lastRefresh = System.currentTimeMillis();
	}

	private static void commit(List<SchematicEntry> fetched)
	{
		publish(fetched);
		settle();
	}

	private static void publish(List<SchematicEntry> fetched)
	{
		notifyFollowedPosts(fetched);
		setPosts(hydrate(fetched));
		stale = false;
	}

	// onLoaded runs on the main thread with the filled row, or the entry unchanged when the fetch failed
	public static void loadDetails(SchematicEntry entry, Consumer<SchematicEntry> onLoaded)
	{
		if (entry == null || entry.id() == null || entry.materialsLoaded() || !Backend.configured())
		{
			return;
		}

		SchematicEntry held = detailsOf(entry.id());

		if (held != null)
		{
			onLoaded.accept(entry.withDetails(held.description(), held.materials()));
			return;
		}

		Net.submit(() ->
		{
			SchematicEntry fetched = Backend.post(entry.id());
			SchematicIndexMod.LOGGER.debug("post {} details -> {}", entry.id(), fetched != null);
			SchematicEntry filled = fetched == null ? entry : entry.withDetails(fetched.description(), fetched.materials());

			if (fetched != null)
			{
				synchronized (DETAILS_BY_ID)
				{
					DETAILS_BY_ID.put(entry.id(), filled);
				}

				replace(filled);
			}

			Minecraft.getInstance().execute(() -> onLoaded.accept(filled));
		});
	}

	private static @Nullable SchematicEntry detailsOf(String id)
	{
		synchronized (DETAILS_BY_ID)
		{
			return DETAILS_BY_ID.get(id);
		}
	}

	private static List<SchematicEntry> hydrate(List<SchematicEntry> fetched)
	{
		List<SchematicEntry> out = new ArrayList<>(fetched.size());

		for (SchematicEntry entry : fetched)
		{
			SchematicEntry held = entry.materialsLoaded() || entry.id() == null ? null : detailsOf(entry.id());
			out.add(held == null ? entry : entry.withDetails(held.description(), held.materials()));
		}

		return out;
	}

	public static synchronized void updateLikes(String postId, int likes, boolean liked)
	{
		SchematicEntry held = detailsOf(postId);

		if (held != null)
		{
			synchronized (DETAILS_BY_ID)
			{
				DETAILS_BY_ID.put(postId, held.withLikes(likes, liked));
			}
		}

		for (SchematicEntry entry : posts)
		{
			if (postId.equals(entry.id()))
			{
				replace(entry.withLikes(likes, liked));
				return;
			}
		}
	}

	private static synchronized void replace(SchematicEntry filled)
	{
		List<SchematicEntry> current = posts;
		List<SchematicEntry> next = new ArrayList<>(current.size());
		boolean swapped = false;

		for (SchematicEntry entry : current)
		{
			boolean match = filled.id().equals(entry.id());
			swapped |= match;
			next.add(match ? filled : entry);
		}

		if (swapped)
		{
			setPosts(next);
		}
	}

	// The side effects of a commit, run once per refresh even when the head landed early
	private static void settle()
	{
		writeSnapshot(posts);
		NewsFeed.refresh();
		RemoteContent.refresh();
		lastRefresh = System.currentTimeMillis();
		state = State.READY;
	}

	// A delta needs a populated baseline to diff against, so the first load takes the full fetch path
	private static boolean canSoftMerge()
	{
		return knownInitialized && state == State.READY && !posts.isEmpty();
	}

	// The first load only seeds the known-ids set; later refreshes diff against it
	private static void notifyFollowedPosts(List<SchematicEntry> fetched)
	{
		Set<String> current = new HashSet<>();

		for (SchematicEntry entry : fetched)
		{
			if (entry.id() != null)
			{
				current.add(entry.id());
			}
		}

		if (!knownInitialized)
		{
			KNOWN_POST_IDS.addAll(current);
			knownInitialized = true;
			return;
		}

		if (Settings.notifications())
		{
			for (SchematicEntry entry : fetched)
			{
				if (entry.id() == null || KNOWN_POST_IDS.contains(entry.id()))
				{
					continue;
				}

				if (Follows.isFollowing(entry.poster()))
				{
					Follows.notifyForPost(entry);
				}
			}
		}

		KNOWN_POST_IDS.addAll(current);
	}

	private static Page fetchFirstPage()
	{
		return fetchPage(INDEX_PATH);
	}

	// changed is false when the server answered 304, in which case entries is the copy held from last time
	private record Page(List<SchematicEntry> entries, boolean changed, @Nullable String nextCursor)
	{
	}

	private static @Nullable Page fetchPage(String path)
	{
		JsonObject body = Backend.getJsonConditional(path);

		if (body == Backend.NOT_MODIFIED)
		{
			List<SchematicEntry> held = PAGE_BY_PATH.get(path);

			// A 304 with nothing held means the ETag outlived the page cache, so fetch the body plainly
			if (held != null)
			{
				return new Page(held, false, nextCursorOf(path));
			}

			body = Backend.getJson(path);
		}

		if (body == null)
		{
			return null;
		}

		List<SchematicEntry> page = new ArrayList<>();

		for (JsonElement element : Json.arrayOf(body, "posts"))
		{
			if (element.isJsonObject())
			{
				page.add(Backend.parsePost(element.getAsJsonObject()));
			}
		}

		String nextCursor = Json.stringOf(body, "nextCursor", null);
		PAGE_BY_PATH.put(path, List.copyOf(page));
		CURSOR_BY_PATH.put(path, nextCursor == null ? "" : nextCursor);
		return new Page(page, true, nextCursor);
	}

	private static @Nullable String nextCursorOf(String path)
	{
		String cursor = CURSOR_BY_PATH.get(path);
		return cursor == null || cursor.isEmpty() ? null : cursor;
	}

	// The server returns posts newest-first, so an overlap with the held list proves page 0 covers the
	// whole gap. Null means it does not, and the caller must fall back to a full fetch
	private static List<SchematicEntry> mergeHead(List<SchematicEntry> head)
	{
		List<SchematicEntry> current = posts;

		Set<String> headIds = new HashSet<>();

		for (SchematicEntry entry : head)
		{
			if (entry.id() != null)
			{
				headIds.add(entry.id());
			}
		}

		boolean overlap = false;

		for (SchematicEntry entry : current)
		{
			if (entry.id() != null && headIds.contains(entry.id()))
			{
				overlap = true;
				break;
			}
		}

		if (!overlap)
		{
			return null;
		}

		List<SchematicEntry> merged = new ArrayList<>(head.size() + current.size());
		merged.addAll(head);

		for (SchematicEntry entry : current)
		{
			if (entry.id() == null || !headIds.contains(entry.id()))
			{
				merged.add(entry);
			}
		}

		return merged;
	}

	private record Walk(List<SchematicEntry> entries, boolean changed)
	{
	}

	private static Walk fetchAll()
	{
		List<SchematicEntry> all = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		String cursor = null;
		boolean changed = false;

		for (int page = 0; page < 40; page++)
		{
			String path = INDEX_PATH + (cursor == null ? "" : "&cursor=" + Backend.encode(cursor));
			Page fetched = fetchPage(path);

			if (fetched == null)
			{
				if (page == 0)
				{
					throw new IllegalStateException("index unreachable");
				}

				break;
			}

			visited.add(path);
			all.addAll(fetched.entries());
			changed |= fetched.changed();
			cursor = fetched.nextCursor();

			if (cursor == null)
			{
				break;
			}
		}

		// Cursors shift as posts arrive, so pages from earlier walks would otherwise pile up
		PAGE_BY_PATH.keySet().retainAll(visited);
		CURSOR_BY_PATH.keySet().retainAll(visited);
		return new Walk(all, changed);
	}

	// A no-op once live posts are present, and it never downgrades a READY state
	public static synchronized void loadSnapshotIfOffline()
	{
		if (!posts.isEmpty())
		{
			return;
		}

		List<SchematicEntry> snapshot = readSnapshot();

		if (snapshot.isEmpty())
		{
			return;
		}

		setPosts(snapshot);
		stale = true;

		if (state != State.READY)
		{
			state = State.OFFLINE;
		}
	}

	// A soft refresh that only bumped a counter is not worth rewriting megabytes for
	private static synchronized void writeSnapshot(List<SchematicEntry> entries)
	{
		if (entries == null)
		{
			return;
		}

		int fingerprint = entries.hashCode();

		if (snapshotWrittenAt != 0L && fingerprint == snapshotFingerprint)
		{
			return;
		}

		long wait = snapshotWrittenAt + SNAPSHOT_MIN_INTERVAL_MS - System.currentTimeMillis();

		if (snapshotWrittenAt != 0L && wait > 0L)
		{
			if (pendingSnapshot == null || pendingSnapshot.isDone())
			{
				pendingSnapshot = Net.scheduler().schedule(() -> writeSnapshot(posts), wait, TimeUnit.MILLISECONDS);
			}

			return;
		}

		snapshotFingerprint = fingerprint;
		snapshotWrittenAt = System.currentTimeMillis();
		writeSnapshotNow(entries);
	}

	// Written in the server's own wire shape, so readSnapshot can hydrate it back through parsePost.
	// The temp-file swap means a crash mid-write cannot leave a truncated cache
	private static void writeSnapshotNow(List<SchematicEntry> entries)
	{
		JsonArray postsArray = new JsonArray();

		for (SchematicEntry entry : entries)
		{
			postsArray.add(entryToJson(entry));
		}

		JsonObject root = new JsonObject();
		root.add("posts", postsArray);

		Path path = snapshotPath();

		try
		{
			Files.createDirectories(path.getParent());
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
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.warn("Could not write catalogue snapshot {}", path, e);
		}
	}

	private static List<SchematicEntry> readSnapshot()
	{
		Path path = snapshotPath();

		if (!Files.exists(path))
		{
			return List.of();
		}

		try (Reader reader = Files.newBufferedReader(path))
		{
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

			if (!root.has("posts") || !root.get("posts").isJsonArray())
			{
				return List.of();
			}

			List<SchematicEntry> entries = new ArrayList<>();

			for (JsonElement element : root.getAsJsonArray("posts"))
			{
				if (element != null && element.isJsonObject())
				{
					entries.add(Backend.parsePost(element.getAsJsonObject()));
				}
			}

			return entries;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not read catalogue snapshot {}", path, e);
			return List.of();
		}
	}

	// Only the fields parsePost reads: slot, imageCount and downloaded are recomputed on load
	private static JsonObject entryToJson(SchematicEntry entry)
	{
		JsonObject o = new JsonObject();
		o.addProperty("id", entry.id());
		o.addProperty("title", entry.title());
		o.addProperty("thumbnailName", entry.thumbnailName());
		o.addProperty("poster", entry.poster());
		o.addProperty("designer", entry.designer());
		o.addProperty("category", entry.category() == null ? null : entry.category().name());

		JsonObject size = new JsonObject();
		size.addProperty("x", entry.sizeX());
		size.addProperty("y", entry.sizeY());
		size.addProperty("z", entry.sizeZ());
		o.add("size", size);

		o.addProperty("blockCount", entry.blockCount());
		o.addProperty("downloads", entry.downloads());
		o.addProperty("likes", entry.likes());
		o.addProperty("postedAt", entry.postedAt());

		if (entry.description() != null)
		{
			o.addProperty("description", entry.description());
		}

		JsonArray imageUrls = new JsonArray();

		for (String url : entry.imageUrls())
		{
			imageUrls.add(url);
		}

		o.add("imageUrls", imageUrls);

		o.addProperty("thumbnailUrl", entry.thumbnailUrl());
		o.addProperty("fileUrl", entry.fileUrl());
		o.addProperty("fileHash", entry.fileHash());
		o.addProperty("fileSize", entry.fileSize());
		o.addProperty("liked", entry.liked());
		o.addProperty("trendScore", entry.trendScore());
		o.addProperty("views", entry.views());
		o.addProperty("starAvg", entry.starAvg());
		o.addProperty("starCount", entry.starCount());
		o.addProperty("myStars", entry.myStars());

		if (entry.hasPosterStyle())
		{
			JsonArray stops = new JsonArray();

			for (int stop : entry.posterStops())
			{
				stops.add(stop);
			}

			o.add("posterStops", stops);
		}

		// Left out for a lite row, so a reload from the snapshot reads materialsLoaded false rather than empty
		if (entry.materialsLoaded())
		{
			JsonArray materials = new JsonArray();

			for (SchematicEntry.Material material : entry.materials())
			{
				JsonObject row = new JsonObject();
				row.addProperty("name", material.name());
				row.addProperty("count", material.count());
				materials.add(row);
			}

			o.add("materials", materials);
		}

		return o;
	}

	private static Path snapshotPath()
	{
		return FabricLoader.getInstance().getConfigDir().resolve(SNAPSHOT_FILE);
	}
}

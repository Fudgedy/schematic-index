package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

// Account notices (/me/notifications): one poll feeds the bell inbox and the toasts, whether the Index's
// two minute timer or a heartbeat nudge asked for it
public final class Notices
{
	public record Entry(String type, String text, long at, @Nullable Runnable action)
	{
	}

	private static final long POLL_MS = 120000L;
	private static final int TOAST_LIMIT = 5;
	private static final long READY_RETRY_SECONDS = 5;
	// An Index that drew within this window is the one on screen
	private static final long LIVE_MS = 1000L;
	private static final AtomicBoolean POLLING = new AtomicBoolean();

	private static volatile List<Entry> entries = List.of();
	private static volatile long lastPoll;
	// Separate from the inbox seen timestamp so toasts and read-state do not fight; -1 until the first poll
	private static long toastMarker = -1L;
	private static volatile @Nullable IndexScreen liveScreen;
	private static volatile long liveAt;
	private static volatile @Nullable Consumer<IndexScreen> queued;

	private Notices()
	{
	}

	public static List<Entry> entries()
	{
		return entries;
	}

	// Follow, like and claim events reach anyone the server can address, by upload code or by session
	public static boolean hasInbox()
	{
		return UploaderAccess.unlocked() || McAuth.verified();
	}

	// Called every frame by the Index, which also marks it as the screen a toast action should act on
	public static void pollIfDue(IndexScreen screen, long nowMs)
	{
		liveScreen = screen;
		liveAt = System.currentTimeMillis();
		Consumer<IndexScreen> waiting = queued;

		if (waiting != null && Settings.termsAccepted())
		{
			queued = null;
			waiting.accept(screen);
		}

		if (nowMs - lastPoll > POLL_MS)
		{
			pollNow();
		}
	}

	public static void pollNow()
	{
		if (!hasInbox() || !Settings.creatorAlerts())
		{
			return;
		}

		lastPoll = System.currentTimeMillis();

		if (!POLLING.compareAndSet(false, true))
		{
			return;
		}

		String code = UploaderAccess.unlocked() ? UploaderAccess.code() : null;
		Net.submit(() -> {
			try
			{
				poll(code);
			}
			finally
			{
				POLLING.set(false);
			}
		});
	}

	// Runs the action on the Index on screen; with none it waits for the next one, opened here when no world is
	public static void withIndex(Consumer<IndexScreen> action)
	{
		IndexScreen live = liveScreen;

		if (live != null && System.currentTimeMillis() - liveAt < LIVE_MS)
		{
			action.accept(live);
			return;
		}

		queued = action;
		openOrHint();
	}

	public static void openPost(String postId)
	{
		IndexScreen live = liveScreen;

		if (live != null && System.currentTimeMillis() - liveAt < LIVE_MS)
		{
			live.openPostById(postId);
			return;
		}

		IndexScreen.queueOpenPost(postId);
		openOrHint();
	}

	private static void openCreatorPage(IndexScreen screen)
	{
		screen.uploadPage.setOpen(false);
		screen.switchPage(IndexScreen.Page.UPLOAD);
	}

	private static void openOrHint()
	{
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null)
		{
			mc.setScreen(new IndexScreen(null));
			return;
		}

		Toasts.push("The Schematic Index", "Open the Index to see it.", new ItemStack(Items.BOOK));
	}

	private static void poll(@Nullable String code)
	{
		SchematicIndexMod.LOGGER.debug("Polling notifications");
		JsonObject data = Backend.notifications(code);

		if (data == null || !data.has("items") || !data.get("items").isJsonArray())
		{
			SchematicIndexMod.LOGGER.debug("Notifications poll returned nothing");
			return;
		}

		List<Entry> parsed = new ArrayList<>();
		List<JsonObject> raw = new ArrayList<>();

		for (JsonElement element : data.getAsJsonArray("items"))
		{
			if (element == null || !element.isJsonObject())
			{
				continue;
			}

			JsonObject item = element.getAsJsonObject();
			String type = Json.stringOf(item, "type", "");
			parsed.add(new Entry(type, NoticeText.row(item, type), Json.longOf(item, "at", 0L), action(item, type)));
			raw.add(item);
		}

		Minecraft.getInstance().execute(() -> publish(parsed, raw));
	}

	private static void publish(List<Entry> parsed, List<JsonObject> raw)
	{
		List<Integer> order = new ArrayList<>();

		for (int i = 0; i < parsed.size(); i++)
		{
			order.add(i);
		}

		order.sort((a, b) -> Long.compare(parsed.get(b).at(), parsed.get(a).at()));
		List<Entry> sorted = new ArrayList<>();
		long newest = 0L;

		for (int index : order)
		{
			sorted.add(parsed.get(index));
			newest = Math.max(newest, parsed.get(index).at());
		}

		entries = List.copyOf(sorted);

		// The first poll starts from whatever was last toasted or read, so offline events toast once and never again
		long floor = toastMarker < 0L
				? Math.max(Settings.notificationsSeenAt(), Settings.number(SettingsKeys.NOTICES_TOASTED_AT, 0L))
				: toastMarker;
		toastMarker = Math.max(floor, newest);

		if (toastMarker > floor)
		{
			Settings.setNumber(SettingsKeys.NOTICES_TOASTED_AT, toastMarker);
		}

		// A zero floor means nothing was ever seen, so the historical backlog stays quiet
		if (floor == 0L)
		{
			return;
		}

		toastPending(order, parsed, raw, floor);
	}

	// toastMarker is already advanced by the caller, so retrying here cannot double-toast anything
	private static void toastPending(List<Integer> order, List<Entry> parsed, List<JsonObject> raw, long floor)
	{
		if (!ClientReady.ready())
		{
			Net.scheduler().schedule(() -> Minecraft.getInstance().execute(() -> toastPending(order, parsed, raw, floor)),
					READY_RETRY_SECONDS, TimeUnit.SECONDS);
			return;
		}

		int shown = 0;
		boolean accountChanged = false;
		boolean reviewed = false;

		for (int index : order)
		{
			Entry entry = parsed.get(index);

			if (entry.at() <= floor)
			{
				continue;
			}

			accountChanged |= entry.type().startsWith("link_") || entry.type().equals("achievement")
					|| entry.type().equals("perk_removed");
			reviewed |= entry.type().startsWith("post_");

			if (shown < TOAST_LIMIT && Toasts.fromNotice(() -> toast(entry, raw.get(index))))
			{
				shown++;
			}
		}

		// A perk or link that moved server side changes what the Cosmetics page must show
		if (accountChanged)
		{
			CosmeticTags.refresh();
			DiscordLink.status(null);
		}

		// A review moves a post out of "In review", so the creator dashboard reloads on its next frame
		IndexScreen live = liveScreen;

		if (reviewed && live != null)
		{
			live.dashboardPage.stats.refresh();
		}
	}

	private static boolean toast(Entry entry, JsonObject item)
	{
		JsonObject data = Json.objectOf(item, "data");

		switch (entry.type())
		{
			case "follow" -> Toasts.push("New follower", entry.text(), new ItemStack(Items.PLAYER_HEAD));
			case "like" -> Toasts.push("New like", entry.text(), new ItemStack(Items.POPPY));
			case "claim" -> Toasts.push("Claim reviewed", entry.text(), new ItemStack(Items.WRITABLE_BOOK));
			case "link_request" -> {
				return DiscordLink.toastRequest(Json.intOf(data, "requestId", -1), Json.stringOf(data, "username", ""),
						Json.longOf(data, "expiresAt", 0L));
			}
			case "link_done" -> pushWith("Discord linked", NoticeText.linkDoneToast(data), Items.AMETHYST_SHARD, "View",
					entry.action());
			case "link_removed" -> Toasts.push("Discord unlinked", "Your Discord account is no longer linked.",
					new ItemStack(Items.BARRIER));
			case "perk_removed" -> pushWith("Perk removed", entry.text(), Items.BARRIER, "Join Discord", entry.action());
			case "achievement" -> pushAchievement(entry);
			case "featured" -> Toasts.pushCrown("Build of the Day", NoticeText.featuredToast(item, data), "View",
					entry.action());
			case "featured_reward" -> {
				Shards.pokeSoon();
				Toasts.pushCrown("Build of the Day!", entry.text(), "View", entry.action());
			}
			case "post_accepted" -> pushWith("Build accepted", entry.text(), Items.EMERALD, "View", entry.action());
			case "post_denied" -> pushWith("Build not accepted", entry.text(), Items.BARRIER, "View", entry.action());
			default -> Toasts.push("New activity", entry.text(), new ItemStack(Items.NAME_TAG));
		}

		return true;
	}

	// The inbox row icon, matching the toast; null for the Build of the Day notices, which draw the crown
	public static @Nullable ItemStack icon(String type)
	{
		Item item = switch (type)
		{
			case "follow" -> Items.PLAYER_HEAD;
			case "like" -> Items.POPPY;
			case "claim" -> Items.WRITABLE_BOOK;
			case "link_done" -> Items.AMETHYST_SHARD;
			case "link_removed", "perk_removed", "post_denied" -> Items.BARRIER;
			case "achievement" -> Items.NETHER_STAR;
			case "post_accepted" -> Items.EMERALD;
			case "featured", "featured_reward" -> null;
			default -> Items.NAME_TAG;
		};

		return item == null ? null : new ItemStack(item);
	}

	// Share copies the player's public profile, where the new achievement shows
	private static void pushAchievement(Entry entry)
	{
		String ign = McAuth.verifiedName();

		if (ign == null || ign.isBlank() || entry.action() == null)
		{
			pushWith("Achievement unlocked", entry.text(), Items.NETHER_STAR, "Equip", entry.action());
			return;
		}

		String url = Backend.profileUrl(ign);
		Toasts.pushActions("Achievement unlocked", entry.text(), new ItemStack(Items.NETHER_STAR), "Equip", entry.action(),
				"Share", () -> copyProfile(url));
	}

	private static void copyProfile(String url)
	{
		try
		{
			Minecraft.getInstance().keyboardHandler.setClipboard(url);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Copying the profile link failed", e);
			Toasts.push("Couldn't copy the link", "Try again in a moment. (" + Errors.LINK + ")", new ItemStack(Items.BARRIER));
			return;
		}

		Toasts.push("Profile link copied", url, new ItemStack(Items.NAME_TAG));
	}

	private static void pushWith(String title, String text, Item icon, String label, @Nullable Runnable action)
	{
		if (action == null)
		{
			Toasts.push(title, text, new ItemStack(icon));
			return;
		}

		Toasts.pushAction(title, text, new ItemStack(icon), label, action);
	}

	// The same action runs from the toast pill and from the inbox row
	private static @Nullable Runnable action(JsonObject item, String type)
	{
		JsonObject data = Json.objectOf(item, "data");
		String postId = Json.stringOf(item, "postId", Json.stringOf(data, "postId", ""));

		return switch (type)
		{
			case "link_request" -> {
				int requestId = Json.intOf(data, "requestId", -1);
				yield requestId < 0 || !RemoteContent.feature("discordLink") ? null
						: () -> withIndex(screen -> screen.linkRequestModal.open(requestId));
			}
			case "link_done" -> () -> withIndex(screen -> screen.switchPage(IndexScreen.Page.COSMETICS));
			case "perk_removed" -> "left_guild".equals(Json.stringOf(data, "reason", ""))
					? () -> IndexScreen.openExternal(RemoteContent.discord()) : null;
			case "achievement" -> {
				int tagId = Json.intOf(data, "tagId", CosmeticTags.NONE);
				yield tagId == CosmeticTags.NONE ? null : () -> CosmeticTags.equip(tagId, () -> {
				});
			}
			case "featured", "featured_reward", "post_accepted" -> postId.isBlank() ? null
					: () -> openPost(postId);
			case "post_denied" -> () -> withIndex(Notices::openCreatorPage);
			default -> null;
		};
	}
}

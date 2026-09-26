package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// Every toast, kept as a local inbox beside the server notices; an action outlives neither the session nor ACTION_TTL
public final class ToastHistory
{
	public static final String CROWN = "crown";
	public static final String AVATAR_PREFIX = "avatar:";

	private static final String FILE_NAME = SchematicIndexMod.MOD_ID + "-inbox.json";
	private static final int MAX_ENTRIES = 200;
	private static final long ACTION_TTL = 30L * 60L * 1000L;
	private static final long SAVE_DELAY_MS = 1000L;
	private static final Object LOCK = new Object();
	private static final AtomicBoolean SAVE_QUEUED = new AtomicBoolean();

	private static final List<Entry> ENTRIES = new ArrayList<>();
	// Rebuilt under LOCK on every change, so the render thread reads it without locking
	private static volatile List<Entry> view = List.of();
	private static long seenAt;
	private static boolean loaded;

	private ToastHistory()
	{
	}

	public static List<Entry> entries()
	{
		ensureLoaded();
		return view;
	}

	public static void record(String title, String message, String icon, @Nullable String actionLabel,
			@Nullable Runnable action)
	{
		ensureLoaded();
		long now = System.currentTimeMillis();

		synchronized (LOCK)
		{
			Entry newest = ENTRIES.isEmpty() ? null : ENTRIES.get(0);
			int count = 1;

			if (newest != null && newest.matches(title, message, icon))
			{
				count = newest.count + 1;
				ENTRIES.remove(0);
			}

			ENTRIES.add(0, new Entry(title, message, icon, now, count, actionLabel, action));

			while (ENTRIES.size() > MAX_ENTRIES)
			{
				ENTRIES.remove(ENTRIES.size() - 1);
			}

			view = List.copyOf(ENTRIES);
		}

		queueSave();
	}

	public static int unread()
	{
		int count = 0;
		long seen;

		synchronized (LOCK)
		{
			seen = seenAt;
		}

		for (Entry entry : entries())
		{
			if (entry.at > seen)
			{
				count++;
			}
		}

		return count;
	}

	public static void markSeen()
	{
		List<Entry> current = entries();

		if (current.isEmpty())
		{
			return;
		}

		synchronized (LOCK)
		{
			if (current.get(0).at <= seenAt)
			{
				return;
			}

			seenAt = current.get(0).at;
		}

		queueSave();
	}

	public static void clear()
	{
		ensureLoaded();

		synchronized (LOCK)
		{
			ENTRIES.clear();
			view = List.of();
		}

		queueSave();
	}

	public static String iconKey(@Nullable ItemStack stack)
	{
		return stack == null || stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static void ensureLoaded()
	{
		synchronized (LOCK)
		{
			if (loaded)
			{
				return;
			}

			loaded = true;
			load();
			view = List.copyOf(ENTRIES);
		}
	}

	private static void load()
	{
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

		if (!Files.exists(path))
		{
			return;
		}

		JsonObject root;

		try
		{
			root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not read {}", path, e);
			return;
		}

		seenAt = Json.longOf(root, "seenAt", 0L);

		for (JsonElement element : Json.arrayOf(root, "items"))
		{
			if (ENTRIES.size() >= MAX_ENTRIES || element == null || !element.isJsonObject())
			{
				continue;
			}

			JsonObject item = element.getAsJsonObject();
			ENTRIES.add(new Entry(Json.stringOf(item, "title", ""), Json.stringOf(item, "message", ""),
					Json.stringOf(item, "icon", ""), Json.longOf(item, "at", 0L), Math.max(1, Json.intOf(item, "count", 1)),
					null, null));
		}
	}

	// Coalesced onto the network pool, so a burst of toasts writes once and never on the render thread
	private static void queueSave()
	{
		if (SAVE_QUEUED.compareAndSet(false, true))
		{
			Net.scheduler().schedule(ToastHistory::save, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	private static void save()
	{
		SAVE_QUEUED.set(false);
		JsonObject root = new JsonObject();
		JsonArray items = new JsonArray();

		synchronized (LOCK)
		{
			root.addProperty("seenAt", seenAt);

			for (Entry entry : ENTRIES)
			{
				JsonObject item = new JsonObject();
				item.addProperty("title", entry.title);
				item.addProperty("message", entry.message);
				item.addProperty("icon", entry.icon);
				item.addProperty("at", entry.at);
				item.addProperty("count", entry.count);
				items.add(item);
			}
		}

		root.add("items", items);
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

		try
		{
			Files.createDirectories(path.getParent());
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not write {}", path, e);
		}
	}

	public static final class Entry
	{
		private final String title;
		private final String message;
		private final String icon;
		private final long at;
		private final int count;
		private final @Nullable String actionLabel;
		private final @Nullable Runnable action;
		private final @Nullable ItemStack stack;

		Entry(String title, String message, String icon, long at, int count, @Nullable String actionLabel,
				@Nullable Runnable action)
		{
			this.title = title == null ? "" : title;
			this.message = message == null ? "" : message;
			this.icon = icon == null ? "" : icon;
			this.at = at;
			this.count = count;
			this.actionLabel = actionLabel;
			this.action = action;
			this.stack = stackFor(this.icon);
		}

		public String title()
		{
			return this.title;
		}

		public String message()
		{
			return this.message;
		}

		public long at()
		{
			return this.at;
		}

		public int count()
		{
			return this.count;
		}

		public boolean crown()
		{
			return CROWN.equals(this.icon);
		}

		public @Nullable String avatarUrl()
		{
			return this.icon.startsWith(AVATAR_PREFIX) ? this.icon.substring(AVATAR_PREFIX.length()) : null;
		}

		public @Nullable ItemStack stack()
		{
			return this.stack;
		}

		public @Nullable String actionLabel()
		{
			return this.liveAction() == null ? null : this.actionLabel;
		}

		public @Nullable Runnable liveAction()
		{
			return this.action != null && System.currentTimeMillis() - this.at < ACTION_TTL ? this.action : null;
		}

		private boolean matches(String title, String message, String icon)
		{
			return this.title.equals(title) && this.message.equals(message) && this.icon.equals(icon);
		}

		private static @Nullable ItemStack stackFor(String icon)
		{
			if (icon.isEmpty() || icon.equals(CROWN) || icon.startsWith(AVATAR_PREFIX))
			{
				return null;
			}

			Identifier key = Identifier.tryParse(icon);
			Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
			return item == Items.AIR ? null : new ItemStack(item);
		}
	}
}

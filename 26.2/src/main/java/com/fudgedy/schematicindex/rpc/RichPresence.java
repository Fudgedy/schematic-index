package com.fudgedy.schematicindex.rpc;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

// Shown for the whole game session: the mod, the game version and, when allowed, the server name
public final class RichPresence
{
	private static final long TICK_SECONDS = 5;
	private static final long[] BACKOFF_MS = {5_000L, 10_000L, 30_000L, 60_000L};
	private static final long SHUTDOWN_WAIT_MS = 500L;
	private static final int STATE_MAX = 128;
	private static final int NAME_MAX = 40;
	private static final String FEATURE = "richPresence";
	private static final String SINGLEPLAYER = "Singleplayer";
	private static final String REALM = "Realms";
	private static final String MULTIPLAYER = "Multiplayer";
	private static final String DEFAULT_SERVER_NAME = "Minecraft Server";
	private static final String DEFAULT_SERVER_NAME_KEY = "selectServer.defaultName";
	private static final String DOT = " · ";

	private static final ReentrantLock LOCK = new ReentrantLock();
	private static final AtomicBoolean SYNC_QUEUED = new AtomicBoolean();
	private static volatile boolean started;
	private static volatile long sessionStart;
	private static volatile @Nullable String place;

	private static volatile @Nullable DiscordIpc ipc;
	private static @Nullable String connectedAppId;
	private static @Nullable String shown;
	private static int failures;
	private static long retryAt;
	private static boolean connectLogged;
	private static boolean protocolLogged;

	private RichPresence()
	{
	}

	public static synchronized void start()
	{
		if (started)
		{
			return;
		}

		started = true;
		sessionStart = System.currentTimeMillis();
		Net.scheduler().scheduleAtFixedRate(RichPresence::tick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
		Runtime.getRuntime().addShutdownHook(new Thread(RichPresence::shutdown, "schematicindex-rpc-shutdown"));
	}

	// The connection is only safe to read on the render thread, so the place is snapshotted there
	private static void tick()
	{
		Minecraft mc = Minecraft.getInstance();

		if (mc == null)
		{
			return;
		}

		mc.execute(() -> {
			place = placeOf(mc);
			request();
		});
	}

	private static void request()
	{
		if (started && SYNC_QUEUED.compareAndSet(false, true))
		{
			Net.submit(RichPresence::sync);
		}
	}

	// One sync at a time; a request that finds the lock taken is picked up by the next tick instead
	private static void sync()
	{
		if (!LOCK.tryLock())
		{
			SYNC_QUEUED.set(false);
			return;
		}

		try
		{
			SYNC_QUEUED.set(false);
			JsonObject activity = desired();

			if (activity == null)
			{
				hide();
				return;
			}

			show(activity);
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Rich Presence sync failed", e);
		}
		finally
		{
			LOCK.unlock();
		}
	}

	private static void show(JsonObject activity)
	{
		String key = activity.toString();
		String appId = RemoteContent.richPresenceAppId();

		if (ipc != null && !appId.equals(connectedAppId))
		{
			disconnect();
		}

		if (ipc != null && key.equals(shown))
		{
			return;
		}

		if (ipc == null && !connect(appId))
		{
			return;
		}

		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		args.add("activity", activity);

		try
		{
			ipc.request("SET_ACTIVITY", args);
		}
		catch (IOException e)
		{
			fail(e);
			disconnect();
			return;
		}

		SchematicIndexMod.LOGGER.debug("Rich Presence set: {}", activity.get("details"));
		shown = key;
		failures = 0;
		Usage.once("rpc_active");
	}

	private static boolean connect(String appId)
	{
		if (Util.getMillis() < retryAt)
		{
			return false;
		}

		try
		{
			ipc = DiscordIpc.open(appId);
		}
		catch (IOException e)
		{
			fail(e);
			return false;
		}

		SchematicIndexMod.LOGGER.debug("Rich Presence connected as {}", appId);
		connectedAppId = appId;
		shown = null;
		return true;
	}

	// Closing the connection is what clears the activity, so a quiet Discord is left with nothing shown
	private static void hide()
	{
		retryAt = 0L;
		failures = 0;

		if (ipc == null)
		{
			return;
		}

		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());

		try
		{
			ipc.request("SET_ACTIVITY", args);
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.debug("Clearing Rich Presence failed", e);
		}

		disconnect();
	}

	private static void disconnect()
	{
		DiscordIpc current = ipc;
		ipc = null;
		shown = null;
		connectedAppId = null;

		if (current != null)
		{
			current.close();
		}
	}

	private static void fail(IOException e)
	{
		boolean protocol = e instanceof DiscordIpc.ProtocolException;
		failures++;
		retryAt = Util.getMillis() + BACKOFF_MS[Math.min(failures, BACKOFF_MS.length) - 1];

		if (protocol ? protocolLogged : connectLogged)
		{
			return;
		}

		if (protocol)
		{
			protocolLogged = true;
		}
		else
		{
			connectLogged = true;
		}

		SchematicIndexMod.LOGGER.debug("Rich Presence unavailable ({})", protocol ? Errors.RICH_PRESENCE_PROTOCOL
				: Errors.RICH_PRESENCE_CONNECT, e);
	}

	private static void shutdown()
	{
		try
		{
			if (LOCK.tryLock(SHUTDOWN_WAIT_MS, TimeUnit.MILLISECONDS))
			{
				hide();
				return;
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		// A sync stuck on a silent Discord holds the lock; dropping the pipe still clears the activity
		DiscordIpc current = ipc;

		if (current != null)
		{
			current.close();
		}
	}

	private static @Nullable JsonObject desired()
	{
		if (!Settings.termsAccepted() || !RemoteContent.feature(FEATURE) || !Settings.flag(SettingsKeys.DISCORD_PRESENCE, true))
		{
			return null;
		}

		String version = gameVersion();
		String where = place;
		JsonObject activity = new JsonObject();
		activity.addProperty("details", "Using The Schematic Index");
		activity.addProperty("state", clip(where == null ? "Playing Minecraft " + version : "Minecraft " + version + DOT + where, STATE_MAX));
		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", sessionStart);
		activity.add("timestamps", timestamps);
		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", "https://schematicindex.com/web/icon.png");
		assets.addProperty("large_text", "The Schematic Index");
		activity.add("assets", assets);
		JsonArray buttons = new JsonArray();
		buttons.add(button("Get the mod", Backend.siteBase() + "/go/modrinth?src=rpc"));
		buttons.add(button("Visit website", Backend.siteBase() + "/go/site?src=rpc"));
		activity.add("buttons", buttons);
		return activity;
	}

	private static @Nullable String placeOf(Minecraft mc)
	{
		if (mc.hasSingleplayerServer())
		{
			return SINGLEPLAYER;
		}

		ServerData server = mc.getConnection() == null ? null : mc.getCurrentServer();

		if (server == null || !Settings.flag(SettingsKeys.DISCORD_PRESENCE_SERVER, true))
		{
			return null;
		}

		if (server.isRealm())
		{
			return REALM;
		}

		return displayName(server);
	}

	// A blank, still-default or address-shaped name would say nothing useful or leak the address, so it falls back
	private static String displayName(ServerData server)
	{
		String name = server.name == null ? "" : server.name.trim();
		String ip = server.ip == null ? "" : server.ip.trim();

		if (name.isEmpty() || name.equals(DEFAULT_SERVER_NAME) || name.equals(I18n.get(DEFAULT_SERVER_NAME_KEY))
				|| name.equalsIgnoreCase(ip) || looksLikeAddress(name))
		{
			return MULTIPLAYER;
		}

		return clip(name, NAME_MAX);
	}

	// No real server name is a bare host[:port]; a name shaped that way is treated as an address, not a name
	private static boolean looksLikeAddress(String name)
	{
		return name.indexOf(' ') < 0 && (name.contains(".") || name.contains(":"));
	}

	private static String gameVersion()
	{
		return FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	private static String clip(String text, int max)
	{
		return text.length() <= max ? text : text.substring(0, max - 3) + "...";
	}

	private static JsonObject button(String label, String url)
	{
		JsonObject button = new JsonObject();
		button.addProperty("label", label);
		button.addProperty("url", url);
		return button;
	}
}

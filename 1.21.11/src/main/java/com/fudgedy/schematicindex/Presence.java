package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.DiscordLink;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Notices;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// An unverified client never beats, so the first beat lands when the handshake succeeds
public final class Presence
{
	private static final long INTERVAL_MINUTES = 10;
	// A fresh Discord link request asks for this sooner beat, so accepting it in game feels immediate
	private static final long SOON_SECONDS = 120;

	private static volatile boolean started;
	private static final AtomicBoolean SOON_PENDING = new AtomicBoolean();

	private Presence()
	{
	}

	public static synchronized void start()
	{
		if (started)
		{
			return;
		}

		started = true;
		Net.scheduler().scheduleAtFixedRate(() -> Net.submit(Presence::beat), 5, INTERVAL_MINUTES * 60, TimeUnit.SECONDS);
	}

	public static void beatNow()
	{
		if (started)
		{
			Net.submit(Presence::beat);
		}
	}

	private static void beat()
	{
		if (!McAuth.verified())
		{
			return;
		}

		try
		{
			JsonObject reply = Backend.heartbeat(SchematicIndexMod.currentVersion());
			Shards.pokeSoon();
			handle(reply);
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Presence beat failed", e);
		}
	}

	private static void handle(@Nullable JsonObject reply)
	{
		if (reply == null)
		{
			return;
		}

		List<String> nudge = Json.listOf(reply, "nudge");
		SchematicIndexMod.LOGGER.debug("Presence nudge {} beatSoon {}", nudge, Json.boolOf(reply, "beatSoon", false));

		if (nudge.contains("link"))
		{
			DiscordLink.onNudge();
		}

		if (nudge.contains("notices"))
		{
			Notices.pollNow();
		}

		if (Json.boolOf(reply, "beatSoon", false) && SOON_PENDING.compareAndSet(false, true))
		{
			Net.scheduler().schedule(() -> {
				SOON_PENDING.set(false);
				Net.submit(Presence::beat);
			}, SOON_SECONDS, TimeUnit.SECONDS);
		}
	}
}

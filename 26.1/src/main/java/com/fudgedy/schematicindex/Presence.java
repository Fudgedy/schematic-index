package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Shards;

import java.util.concurrent.TimeUnit;

// An unverified client never beats, so the first beat lands when the handshake succeeds
public final class Presence
{
	private static final long INTERVAL_MINUTES = 10;

	private static volatile boolean started;

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
			Backend.heartbeat(SchematicIndexMod.currentVersion());
			Shards.pokeSoon();
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Presence beat failed", e);
		}
	}
}

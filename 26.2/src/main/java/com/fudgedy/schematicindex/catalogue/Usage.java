package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Settings;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Usage
{
	private static final Set<String> SENT = ConcurrentHashMap.newKeySet();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

	private Usage()
	{
	}

	// Sent once per session; the first Index open usually beats the silent verify, so a kind seen before
	// then waits in PENDING for flush() rather than being lost
	public static void once(String kind)
	{
		if (!Settings.usageData() || SENT.contains(kind))
		{
			return;
		}

		if (!McAuth.verified())
		{
			PENDING.add(kind);
			return;
		}

		if (SENT.add(kind))
		{
			Backend.postUsage(kind);
		}
	}

	public static void send(String kind)
	{
		if (!McAuth.verified())
		{
			return;
		}

		Backend.postUsage(kind);
	}

	public static void flush()
	{
		for (String kind : PENDING)
		{
			PENDING.remove(kind);
			once(kind);
		}
	}
}

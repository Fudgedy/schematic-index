package com.fudgedy.schematicindex;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

// Every fallible feature gets a unique code here and reports through report(), never failing silently
public final class Errors
{
	public static final String NETWORK = "SI-NET01";
	public static final String CONTENT = "SI-NET02";
	public static final String DOWNLOAD = "SI-DL01";
	public static final String DOWNLOAD_WRITE = "SI-DL02";
	public static final String LOAD = "SI-LD01";
	public static final String UPLOAD = "SI-UP01";
	public static final String POST_EDIT = "SI-UP02";
	public static final String PARSE = "SI-PR01";
	// Reserved, not surfaced: image loads retry with a backoff instead of opening the modal
	public static final String IMAGE = "SI-IM01";
	public static final String LINK = "SI-IM02";
	public static final String PREVIEW = "SI-PV01";
	public static final String COLLECTION = "SI-CL01";
	public static final String PROFILE = "SI-PF01";
	public static final String FOLLOW = "SI-FL01";
	public static final String LIKE = "SI-LK01";
	public static final String RATE = "SI-RT01";
	public static final String AUTH_CHALLENGE = "SI-AU01";
	public static final String AUTH_SESSION = "SI-AU02";
	public static final String AUTH_VERIFY = "SI-AU03";
	public static final String CLAIM = "SI-CM01";
	public static final String CLAIMS_LIST = "SI-CM02";
	public static final String SHARD_BUY = "SI-SH01";
	public static final String SHARD_WELCOME = "SI-SH02";
	public static final String SHARD_DAILY = "SI-SH03";
	public static final String SHARD_QUEST = "SI-SH04";
	public static final String MAPART_IMAGE = "SI-MA01";
	public static final String MAPART_CONVERT = "SI-MA02";
	public static final String MAPART_SAVE = "SI-MA03";
	public static final String MAPART_PICKER = "SI-MA04";
	public static final String MAPART_EXPORT = "SI-MA05";
	public static final String MAPART_LOAD = "SI-MA06";
	public static final String MAPART_PASTE = "SI-MA07";
	public static final String STAFF_ACTION = "SI-ST01";
	public static final String STAFF_LOAD = "SI-ST02";
	public static final String STAFF_DENIED = "SI-ST03";
	public static final String COSMETIC_PUBLISH = "SI-CS01";
	public static final String LIBRARY_SYNC = "SI-LB01";

	private static final int PENDING_LIMIT = 8;
	private static final ArrayDeque<String> PENDING = new ArrayDeque<>();

	private Errors()
	{
	}

	// Failures surface in order, one modal each; a retry storm of the same code queues it once
	public static void report(String code)
	{
		synchronized (PENDING)
		{
			if (code.equals(PENDING.peekLast()) || PENDING.size() >= PENDING_LIMIT)
			{
				SchematicIndexMod.LOGGER.debug("Dropped repeat error {}", code);
				return;
			}

			PENDING.addLast(code);
		}

		SchematicIndexMod.LOGGER.warn("Surfaced error {}", code);
	}

	public static @Nullable String takePending()
	{
		synchronized (PENDING)
		{
			return PENDING.pollFirst();
		}
	}

	// Repeats of the code on screen are dropped while it shows, so dismissing it cannot reopen the same modal
	public static void dropPending(String code)
	{
		synchronized (PENDING)
		{
			while (code.equals(PENDING.peekFirst()))
			{
				PENDING.pollFirst();
			}
		}
	}
}

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
	// Not surfaced through report(): the early duplicate check is advisory, Post still checks again
	public static final String UPLOAD_CHECK = "SI-UP03";
	public static final String CREATOR_STATS = "SI-UP04";
	public static final String PARSE = "SI-PR01";
	// Reserved, not surfaced: image loads retry with a backoff instead of opening the modal
	public static final String IMAGE = "SI-IM01";
	public static final String LINK = "SI-IM02";
	public static final String PREVIEW = "SI-PV01";
	public static final String PHOTO = "SI-PV02";
	public static final String COLLECTION = "SI-CL01";
	public static final String PROFILE = "SI-PF01";
	public static final String FOLLOW = "SI-FL01";
	public static final String LIKE = "SI-LK01";
	public static final String RATE = "SI-RT01";
	public static final String RATE_PROMPT = "SI-RT02";
	public static final String AUTH_CHALLENGE = "SI-AU01";
	public static final String AUTH_SESSION = "SI-AU02";
	public static final String AUTH_VERIFY = "SI-AU03";
	public static final String CLAIM = "SI-CM01";
	public static final String CLAIMS_LIST = "SI-CM02";
	public static final String SHARD_BUY = "SI-SH01";
	public static final String SHARD_WELCOME = "SI-SH02";
	public static final String SHARD_DAILY = "SI-SH03";
	public static final String SHARD_QUEST = "SI-SH04";
	public static final String SHARD_FREEZE = "SI-SH05";
	// Shown inline by the shard panel with a Retry, never through report(), since the minute poll would repeat it
	public static final String SHARD_LOAD = "SI-SH06";
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
	// SI-ST04 and SI-ST05 are taken by the staff source set's PostModeration, kept out of the community jar
	public static final String COSMETIC_PUBLISH = "SI-CS01";
	public static final String LIBRARY_SYNC = "SI-LB01";
	public static final String DISCORD_CODE = "SI-DC01";
	public static final String DISCORD_RESPOND = "SI-DC02";
	public static final String DISCORD_UNLINK = "SI-DC03";
	public static final String DISCORD_STATUS = "SI-DC04";
	public static final String OPEN_BRIDGE = "SI-OP01";
	public static final String OPEN_NOT_FOUND = "SI-OP02";
	public static final String OPEN_FAILED = "SI-OP03";
	public static final String RICH_PRESENCE_CONNECT = "SI-RP01";
	public static final String RICH_PRESENCE_PROTOCOL = "SI-RP02";
	public static final String BOARDS_LOAD = "SI-BD01";
	public static final String ACHIEVEMENTS_LOAD = "SI-AC01";
	public static final String VISIBILITY_SAVE = "SI-AC02";
	public static final String NEWS_LOAD = "SI-WN01";
	public static final String UPDATE_REQUIRED = "SI-WN02";
	public static final String FEATURED_VOTE = "SI-FV01";
	public static final String SHOWCASE_SAVE = "SI-AC03";

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

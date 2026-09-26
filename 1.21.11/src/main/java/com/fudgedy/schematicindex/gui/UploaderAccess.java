package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public final class UploaderAccess
{
	private static volatile @Nullable String profile;
	private static volatile @Nullable String code;
	private static volatile @Nullable String ign;

	private UploaderAccess()
	{
	}

	// A verified session is enough to post; a legacy code still unlocks the form for an unverified player
	public static boolean unlocked()
	{
		return profile != null || McAuth.verified();
	}

	public static boolean hasCode()
	{
		return profile != null;
	}

	public static @Nullable String profile()
	{
		return profile != null ? profile : McAuth.verifiedName();
	}

	public static @Nullable String code()
	{
		return code;
	}

	public static @Nullable String ign()
	{
		if (profile == null)
		{
			return McAuth.verifiedName();
		}

		return ign != null && !ign.isBlank() ? ign : profile;
	}

	public static @Nullable String redeem(String raw)
	{
		String cleaned = raw.trim();
		JsonObject info = Backend.uploaderInfo(cleaned);

		String displayName = Json.stringOf(info, "displayName", null);

		if (displayName == null)
		{
			return null;
		}

		profile = displayName;
		code = cleaned;
		ign = Json.stringOf(info, "ign", null);
		return profile;
	}

	public static void signOut()
	{
		profile = null;
		code = null;
		ign = null;
	}
}

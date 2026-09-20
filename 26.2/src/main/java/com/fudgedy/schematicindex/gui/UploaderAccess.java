package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
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

	public static boolean unlocked()
	{
		return profile != null;
	}

	public static @Nullable String profile()
	{
		return profile;
	}

	public static @Nullable String code()
	{
		return code;
	}

	public static @Nullable String ign()
	{
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

	public static String betaHint()
	{
		return "Ask an existing uploader for an access code.";
	}
}

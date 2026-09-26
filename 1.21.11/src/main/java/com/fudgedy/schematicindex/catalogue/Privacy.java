package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

// Who can see the account: the public web profile and the leaderboards (/me/privacy)
public final class Privacy
{
	private static final long REFRESH_MS = 60000L;

	private static volatile boolean publicProfile;
	private static volatile boolean showOnLeaderboards = true;
	private static volatile boolean saving;
	private static volatile long lastRefresh;
	// The account the values were read for, so a switched account never shows the last one's choices
	private static volatile @Nullable String owner;

	private Privacy()
	{
	}

	public static boolean loaded()
	{
		return owner != null && owner.equals(McAuth.verifiedUuid());
	}

	public static boolean publicProfile()
	{
		return publicProfile;
	}

	public static boolean showOnLeaderboards()
	{
		return showOnLeaderboards;
	}

	public static boolean saving()
	{
		return saving;
	}

	public static void refreshIfStale()
	{
		long now = System.currentTimeMillis();

		if (!McAuth.verified() || saving || (loaded() && now - lastRefresh < REFRESH_MS) || now - lastRefresh < 5000L)
		{
			return;
		}

		lastRefresh = now;
		Net.submit(() -> {
			Backend.ApiResult result = Backend.privacy();
			SchematicIndexMod.LOGGER.debug("GET /me/privacy -> {}", result.status());

			if (result.ok())
			{
				apply(result.body());
			}
		});
	}

	// Shown at once and put back if the server refuses, so the toggle never waits on the round trip
	public static void save(boolean profile, boolean boards)
	{
		if (saving)
		{
			return;
		}

		boolean oldProfile = publicProfile;
		boolean oldBoards = showOnLeaderboards;
		publicProfile = profile;
		showOnLeaderboards = boards;
		saving = true;
		Net.submit(() -> {
			Backend.ApiResult result = Backend.putPrivacy(profile, boards);
			SchematicIndexMod.LOGGER.debug("PUT /me/privacy -> {} {}", result.status(), result.error());
			saving = false;

			if (result.ok())
			{
				apply(result.body());
				return;
			}

			publicProfile = oldProfile;
			showOnLeaderboards = oldBoards;
			Minecraft.getInstance().execute(() -> Toasts.push("Visibility not saved",
					"Couldn't save your visibility settings. (" + Errors.VISIBILITY_SAVE + ")", new ItemStack(Items.BARRIER)));
		});
	}

	private static void apply(@Nullable JsonObject o)
	{
		if (o == null || !o.has("publicProfile"))
		{
			return;
		}

		publicProfile = Json.boolOf(o, "publicProfile", false);
		showOnLeaderboards = Json.boolOf(o, "showOnLeaderboards", true);
		owner = McAuth.verifiedUuid();
	}
}

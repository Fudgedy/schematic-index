package com.fudgedy.schematicindex.update;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.modal.CoachMark;
import org.jetbrains.annotations.Nullable;

// Decides, per Index frame, whether this client is too old to browse, merely behind, or current
public final class UpdateGate
{
	public static final String PROJECT = "the-schematic-index";
	public static final String PROJECT_URL = "https://modrinth.com/mod/" + PROJECT;

	private static volatile @Nullable ModUpdater.Release modrinth;

	private UpdateGate()
	{
	}

	public static void onIndexOpen(IndexScreen screen)
	{
		CoachMark.migrate();
		tick(screen);
	}

	// Content and news land after the first frame, so the checks repeat until they have something to act on
	public static void tick(IndexScreen screen)
	{
		if (isBlocked())
		{
			screen.updateRequiredModal.open();
			return;
		}

		screen.whatsNewModal.maybeShow();
		screen.coachMark.tick();
	}

	static void setModrinth(@Nullable ModUpdater.Release release)
	{
		modrinth = release;
	}

	public static String currentVersion()
	{
		String version = SchematicIndexMod.currentVersion();
		int plus = version.indexOf('+');
		return plus < 0 ? version : version.substring(0, plus);
	}

	// The newer of what Modrinth lists for this game version and what the server says was released
	public static @Nullable String latestKnown()
	{
		ModUpdater.Release release = modrinth;
		String fromModrinth = release == null ? "" : release.version();
		String fromServer = RemoteContent.latestVersion();

		if (fromModrinth.isBlank())
		{
			return fromServer.isBlank() ? null : fromServer;
		}

		if (fromServer.isBlank())
		{
			return fromModrinth;
		}

		return ModUpdater.isNewer(fromServer, fromModrinth) ? fromServer : fromModrinth;
	}

	public static boolean isOutdated()
	{
		String latest = latestKnown();
		return latest != null && ModUpdater.isNewer(latest, SchematicIndexMod.currentVersion());
	}

	// Independent of the updatePrompt switch: a client below the floor must never browse silently broken
	public static boolean isBlocked()
	{
		String floor = RemoteContent.minSupportedVersion();
		return !floor.isBlank() && ModUpdater.isNewer(floor, SchematicIndexMod.currentVersion());
	}

	public static boolean shouldPrompt()
	{
		return RemoteContent.feature("updatePrompt") && isOutdated();
	}

	// The version page is only right while Modrinth's newest is also the newest known; else the project page
	public static String downloadUrl()
	{
		ModUpdater.Release release = modrinth;
		String latest = latestKnown();

		if (release == null || release.id() == null || release.id().isBlank() || latest == null
				|| ModUpdater.isNewer(latest, release.version()))
		{
			return PROJECT_URL;
		}

		return PROJECT_URL + "/version/" + release.id();
	}
}

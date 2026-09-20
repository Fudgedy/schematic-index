package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.catalogue.Net;
import fi.dy.masa.litematica.data.DataManager;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Settings
{
	private static final String FILE_NAME = SchematicIndexMod.MOD_ID + ".properties";

	// Bumped when a key changes meaning; migrate() maps an older file forward before it is read
	private static final int CONFIG_VERSION = 1;
	private static final String KEY_CONFIG_VERSION = "config_version";

	private static final String KEY_SOUNDS = "sound_effects";
	private static final String KEY_MOD_TAGS = "mod_tags";
	private static final String KEY_OWN_NAMETAG = "own_nametag";
	private static final String KEY_MAP_CORNERS = "map_corners";
	private static final String KEY_CORNER_HEIGHT = "corner_height";
	private static final String KEY_MAPART_PRESETS = "mapart_presets";
	private static final String KEY_UI_VOLUME = "ui_volume";
	private static final String KEY_PREVIEW_FOV = "preview_fov";
	private static final String KEY_CONFIRM_OVERWRITE = "confirm_overwrite";
	private static final String KEY_DOWNLOAD_DIR = "download_directory";
	private static final String KEY_GRID_DENSITY = "grid_density";
	private static final String KEY_TUTORIAL_SEEN = "tutorial_seen";
	private static final String KEY_SHARD_WELCOME_SEEN = "shard_welcome_seen";
	private static final String KEY_TOASTS = "toasts";
	private static final String KEY_NOTIFICATIONS = "notifications";
	private static final String KEY_CREATOR_ALERTS = "creator_alerts";
	private static final String KEY_NOTIFICATIONS_SEEN = "notifications_seen_at";
	private static final String KEY_LAST_VISIT = "last_visit_at";
	private static final String KEY_SESSION_TOKEN = "session_token";
	private static final String KEY_TERMS = "terms_accepted";
	private static final String KEY_SKIP_DESIGNER_WARNING = "skip_designer_warning";
	private static final String KEY_TERMS_BODY = "terms_body";
	private static final String KEY_TERMS_VERSION = "terms_version";
	private static final String KEY_DISMISSED_ANNOUNCEMENT = "dismissed_announcement";
	private static final String KEY_COSMETIC_LOADOUT = "cosmetic_loadout";
	private static final String KEY_USAGE_DATA = "usage_data";
	private static final String KEY_DEBUG_UPDATE_TOAST = "debug_update_toast";

	private static final String OFFICIAL_API = "https://api.schematicindex.com";

	private static boolean sounds = true;
	private static boolean modTags = true;
	private static boolean ownNametag = true;
	private static boolean mapCorners;
	// UNSET until the player chooses a height, so the first use can take their current Y instead
	private static final int UNSET_HEIGHT = Integer.MIN_VALUE;
	private static final int DEFAULT_HEIGHT = 64;
	private static int cornerHeight = UNSET_HEIGHT;
	// name=palette code pairs joined by ';', names never contain either separator
	private static final Map<String, String> mapartPresets = new LinkedHashMap<>();
	// The worn loadout as the mode name plus its owned colour ids, so a restart keeps the nametag
	private static volatile String cosmeticLoadout = "";
	private static int uiVolume = 100;
	private static int previewFov = 70;
	private static boolean confirmOverwrite = true;

	private static volatile @Nullable String sessionToken;

	private static boolean termsAccepted;
	private static boolean skipDesignerWarning;
	private static boolean usageData = true;

	private static volatile @Nullable String cachedTermsBody;
	private static volatile int cachedTermsVersion;
	private static @Nullable String dismissedAnnouncement;

	private static boolean toasts = true;
	private static boolean debugUpdateToast;

	private static boolean notifications = true;
	private static boolean creatorAlerts = true;
	private static long notificationsSeenAt;
	private static long lastVisitAt;
	private static @Nullable Path customDownloadDirectory;

	private static int gridDensity;
	private static boolean tutorialSeen;
	private static volatile boolean shardWelcomeSeen;
	private static boolean loaded;

	// Setters schedule one debounced write instead of a full-file write per call, on a daemon thread
	private static final long SAVE_DEBOUNCE_MS = 500L;
	private static ScheduledFuture<?> pendingSave;

	private Settings()
	{
	}

	public static boolean sounds()
	{
		return sounds;
	}

	public static boolean confirmOverwrite()
	{
		return confirmOverwrite;
	}

	public static void toggleSounds()
	{
		sounds = !sounds;
		save();
	}

	public static boolean modTags()
	{
		return modTags;
	}

	public static String cosmeticLoadout()
	{
		return cosmeticLoadout == null ? "" : cosmeticLoadout;
	}

	public static void setCosmeticLoadout(String value)
	{
		String next = value == null ? "" : value;

		if (next.equals(cosmeticLoadout))
		{
			return;
		}

		cosmeticLoadout = next;
		save();
	}

	public static void toggleModTags()
	{
		modTags = !modTags;
		save();
	}

	public static boolean ownNametag()
	{
		return ownNametag;
	}

	public static void toggleOwnNametag()
	{
		ownNametag = !ownNametag;
		save();
	}

	public static boolean mapCorners()
	{
		return mapCorners;
	}

	public static void setMapCorners(boolean value)
	{
		mapCorners = value;
		save();
	}

	public static int cornerHeight()
	{
		return cornerHeight == UNSET_HEIGHT ? DEFAULT_HEIGHT : cornerHeight;
	}

	public static boolean hasCornerHeight()
	{
		return cornerHeight != UNSET_HEIGHT;
	}

	public static void setCornerHeight(int y)
	{
		if (y == cornerHeight)
		{
			return;
		}

		cornerHeight = y;
		save();
	}

	public static Map<String, String> mapartPresets()
	{
		return Collections.unmodifiableMap(mapartPresets);
	}

	public static void putMapartPreset(String name, String code)
	{
		mapartPresets.put(name.replace('=', ' ').replace(';', ' ').trim(), code);
		save();
	}

	public static void removeMapartPreset(String name)
	{
		if (mapartPresets.remove(name) != null)
		{
			save();
		}
	}

	public static int uiVolume()
	{
		return uiVolume;
	}

	public static float uiVolumeFraction()
	{
		return uiVolume / 100.0F;
	}

	public static void setUiVolume(int percent)
	{
		int clamped = Math.max(0, Math.min(100, percent));

		if (clamped != uiVolume)
		{
			uiVolume = clamped;
			save();
		}
	}

	public static int previewFov()
	{
		return previewFov;
	}

	public static void setPreviewFov(int degrees)
	{
		int clamped = Math.max(30, Math.min(110, degrees));

		if (clamped != previewFov)
		{
			previewFov = clamped;
			save();
		}
	}

	public static void toggleConfirmOverwrite()
	{
		confirmOverwrite = !confirmOverwrite;
		save();
	}

	public static int gridDensity()
	{
		return gridDensity;
	}

	public static String gridDensityLabel()
	{
		return gridDensity < 0 ? "Large" : gridDensity > 0 ? "Compact" : "Comfortable";
	}

	public static void cycleGridDensity()
	{
		gridDensity = gridDensity >= 1 ? -1 : gridDensity + 1;
		save();
	}

	public static boolean toasts()
	{
		return toasts;
	}

	public static void toggleToasts()
	{
		toasts = !toasts;
		save();
	}

	public static boolean notifications()
	{
		return notifications;
	}

	public static void toggleNotifications()
	{
		notifications = !notifications;
		save();
	}

	public static boolean creatorAlerts()
	{
		return creatorAlerts;
	}

	public static void toggleCreatorAlerts()
	{
		creatorAlerts = !creatorAlerts;
		save();
	}

	public static long lastVisitAt()
	{
		return lastVisitAt;
	}

	public static void setLastVisitAt(long at)
	{
		lastVisitAt = at;
		save();
	}

	public static long notificationsSeenAt()
	{
		return notificationsSeenAt;
	}

	public static void setNotificationsSeenAt(long at)
	{
		if (at > notificationsSeenAt)
		{
			notificationsSeenAt = at;
			save();
		}
	}

	public static String sessionToken()
	{
		return sessionToken == null ? "" : sessionToken;
	}

	public static void setSessionToken(@Nullable String value)
	{
		String next = value == null ? "" : value;

		if (next.equals(sessionToken()))
		{
			return;
		}

		sessionToken = next;
		save();
	}

	public static String apiBaseUrl()
	{
		return System.getProperty("schematicindex.index", OFFICIAL_API);
	}

	public static boolean hasApiBaseUrl()
	{
		return apiBaseUrl() != null && !apiBaseUrl().isBlank();
	}

	public static boolean termsAccepted()
	{
		return termsAccepted;
	}

	public static void acceptTerms()
	{
		termsAccepted = true;
		save();
	}

	public static boolean skipDesignerWarning()
	{
		return skipDesignerWarning;
	}

	// A one-shot test hook: true once, on the launch whose file carried the key; it is never written back,
	// so the next save drops it
	public static boolean takeDebugUpdateToast()
	{
		boolean set = debugUpdateToast;
		debugUpdateToast = false;
		return set;
	}

	public static boolean usageData()
	{
		return usageData;
	}

	public static void toggleUsageData()
	{
		usageData = !usageData;
		save();
	}

	public static void setSkipDesignerWarning(boolean value)
	{
		skipDesignerWarning = value;
		save();
	}

	public static @Nullable String cachedTermsBody()
	{
		return cachedTermsBody != null && !cachedTermsBody.isBlank() ? cachedTermsBody : null;
	}

	public static int cachedTermsVersion()
	{
		return cachedTermsVersion;
	}

	public static void cacheTerms(int version, String body)
	{
		if (body == null || body.isBlank())
		{
			return;
		}

		if (version == cachedTermsVersion && body.equals(cachedTermsBody))
		{
			return;
		}

		cachedTermsVersion = version;
		cachedTermsBody = body;
		save();
	}

	public static @Nullable String dismissedAnnouncement()
	{
		return dismissedAnnouncement;
	}

	public static void dismissAnnouncement(String id)
	{
		if (id == null || id.equals(dismissedAnnouncement))
		{
			return;
		}

		dismissedAnnouncement = id;
		save();
	}

	public static void revokeTerms()
	{
		termsAccepted = false;
		save();
	}

	public static boolean tutorialSeen()
	{
		return tutorialSeen;
	}

	public static void markTutorialSeen()
	{
		if (!tutorialSeen)
		{
			tutorialSeen = true;
			save();
		}
	}

	public static boolean shardWelcomeSeen()
	{
		return shardWelcomeSeen;
	}

	public static void markShardWelcomeSeen()
	{
		if (!shardWelcomeSeen)
		{
			shardWelcomeSeen = true;
			save();
		}
	}

	public static Path downloadDirectory()
	{
		return customDownloadDirectory != null ? customDownloadDirectory : defaultDownloadDirectory();
	}

	public static Path defaultDownloadDirectory()
	{
		try
		{
			return DataManager.getSchematicsBaseDirectory();
		}
		catch (Exception e)
		{
			return FabricLoader.getInstance().getGameDir().resolve("schematics");
		}
	}

	public static boolean hasCustomDownloadDirectory()
	{
		return customDownloadDirectory != null;
	}

	public static void setDownloadDirectory(Path directory)
	{
		customDownloadDirectory = directory;
		save();
	}

	public static void clearDownloadDirectory()
	{
		customDownloadDirectory = null;
		save();
	}

	public static void load()
	{
		if (loaded)
		{
			return;
		}

		loaded = true;

		// Flushed synchronously at exit, so a change made moments before quitting is not lost
		Runtime.getRuntime().addShutdownHook(new Thread(Settings::writeToDisk, "schematicindex-settings-flush"));

		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

		if (!Files.exists(path))
		{
			writeToDisk();
			return;
		}

		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(path))
		{
			properties.load(reader);
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.warn("Could not read {}", path, e);
			writeToDisk();
			return;
		}

		migrate(properties, parseInt(properties.getProperty(KEY_CONFIG_VERSION), 0));

		sounds = parse(properties.getProperty(KEY_SOUNDS), true);
		modTags = parse(properties.getProperty(KEY_MOD_TAGS), true);
		ownNametag = parse(properties.getProperty(KEY_OWN_NAMETAG), true);
		mapCorners = parse(properties.getProperty(KEY_MAP_CORNERS), false);
		cornerHeight = parseInt(properties.getProperty(KEY_CORNER_HEIGHT), UNSET_HEIGHT);
		loadPresets(properties.getProperty(KEY_MAPART_PRESETS, ""));
		uiVolume = Math.max(0, Math.min(100, parseInt(properties.getProperty(KEY_UI_VOLUME), 100)));
		previewFov = Math.max(30, Math.min(110, parseInt(properties.getProperty(KEY_PREVIEW_FOV), 70)));
		confirmOverwrite = parse(properties.getProperty(KEY_CONFIRM_OVERWRITE), true);
		gridDensity = Math.max(-1, Math.min(1, parseInt(properties.getProperty(KEY_GRID_DENSITY), 0)));
		tutorialSeen = parse(properties.getProperty(KEY_TUTORIAL_SEEN), false);
		shardWelcomeSeen = parse(properties.getProperty(KEY_SHARD_WELCOME_SEEN), false);
		toasts = parse(properties.getProperty(KEY_TOASTS), true);
		notifications = parse(properties.getProperty(KEY_NOTIFICATIONS), true);
		creatorAlerts = parse(properties.getProperty(KEY_CREATOR_ALERTS), true);
		notificationsSeenAt = parseLong(properties.getProperty(KEY_NOTIFICATIONS_SEEN), 0L);
		lastVisitAt = parseLong(properties.getProperty(KEY_LAST_VISIT), 0L);
		cosmeticLoadout = properties.getProperty(KEY_COSMETIC_LOADOUT, "");
		sessionToken = properties.getProperty(KEY_SESSION_TOKEN, "");
		termsAccepted = parse(properties.getProperty(KEY_TERMS), false);
		skipDesignerWarning = parse(properties.getProperty(KEY_SKIP_DESIGNER_WARNING), false);
		usageData = parse(properties.getProperty(KEY_USAGE_DATA), true);
		debugUpdateToast = parse(properties.getProperty(KEY_DEBUG_UPDATE_TOAST), false);
		cachedTermsBody = properties.getProperty(KEY_TERMS_BODY, "");
		cachedTermsVersion = parseInt(properties.getProperty(KEY_TERMS_VERSION), 0);
		dismissedAnnouncement = properties.getProperty(KEY_DISMISSED_ANNOUNCEMENT, "");

		String stored = properties.getProperty(KEY_DOWNLOAD_DIR);
		customDownloadDirectory = stored == null || stored.isBlank() ? null : Path.of(stored.trim());
	}

	// Rewrites keys in place before they are read; nothing has changed meaning yet
	private static void migrate(Properties properties, int from)
	{
		if (from >= CONFIG_VERSION)
		{
			return;
		}
	}

	private static synchronized void save()
	{
		if (pendingSave != null)
		{
			pendingSave.cancel(false);
		}

		pendingSave = Net.scheduler().schedule(Settings::writeToDisk, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	// Synchronized so a debounced write racing the shutdown flush cannot interleave into a corrupt file
	private static synchronized void writeToDisk()
	{
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Properties properties = new Properties();
		properties.setProperty(KEY_CONFIG_VERSION, Integer.toString(CONFIG_VERSION));
		properties.setProperty(KEY_SOUNDS, Boolean.toString(sounds));
		properties.setProperty(KEY_MOD_TAGS, Boolean.toString(modTags));
		properties.setProperty(KEY_OWN_NAMETAG, Boolean.toString(ownNametag));
		properties.setProperty(KEY_MAP_CORNERS, Boolean.toString(mapCorners));
		properties.setProperty(KEY_MAPART_PRESETS, joinPresets());
		properties.setProperty(KEY_UI_VOLUME, Integer.toString(uiVolume));
		properties.setProperty(KEY_PREVIEW_FOV, Integer.toString(previewFov));
		properties.setProperty(KEY_CONFIRM_OVERWRITE, Boolean.toString(confirmOverwrite));
		properties.setProperty(KEY_GRID_DENSITY, Integer.toString(gridDensity));
		properties.setProperty(KEY_TUTORIAL_SEEN, Boolean.toString(tutorialSeen));
		properties.setProperty(KEY_SHARD_WELCOME_SEEN, Boolean.toString(shardWelcomeSeen));
		properties.setProperty(KEY_TOASTS, Boolean.toString(toasts));
		properties.setProperty(KEY_NOTIFICATIONS, Boolean.toString(notifications));
		properties.setProperty(KEY_CREATOR_ALERTS, Boolean.toString(creatorAlerts));
		properties.setProperty(KEY_NOTIFICATIONS_SEEN, Long.toString(notificationsSeenAt));
		properties.setProperty(KEY_LAST_VISIT, Long.toString(lastVisitAt));
		properties.setProperty(KEY_COSMETIC_LOADOUT, cosmeticLoadout == null ? "" : cosmeticLoadout);
		properties.setProperty(KEY_TERMS, Boolean.toString(termsAccepted));
		properties.setProperty(KEY_SKIP_DESIGNER_WARNING, Boolean.toString(skipDesignerWarning));
		properties.setProperty(KEY_USAGE_DATA, Boolean.toString(usageData));
		properties.setProperty(KEY_TERMS_VERSION, Integer.toString(cachedTermsVersion));

		if (cachedTermsBody != null && !cachedTermsBody.isBlank())
		{
			properties.setProperty(KEY_TERMS_BODY, cachedTermsBody);
		}

		if (dismissedAnnouncement != null && !dismissedAnnouncement.isBlank())
		{
			properties.setProperty(KEY_DISMISSED_ANNOUNCEMENT, dismissedAnnouncement);
		}

		if (sessionToken != null && !sessionToken.isBlank())
		{
			properties.setProperty(KEY_SESSION_TOKEN, sessionToken);
		}

		if (customDownloadDirectory != null)
		{
			properties.setProperty(KEY_DOWNLOAD_DIR, customDownloadDirectory.toString());
		}

		if (cornerHeight != UNSET_HEIGHT)
		{
			properties.setProperty(KEY_CORNER_HEIGHT, Integer.toString(cornerHeight));
		}

		try
		{
			Files.createDirectories(path.getParent());

			// A crash mid-write must not leave a truncated file, which would also lose the device identity
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(temporary))
			{
				properties.store(writer, "The Schematic Index");
			}

			try
			{
				Files.move(temporary, path,
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException atomicFailed)
			{
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private static void loadPresets(String stored)
	{
		mapartPresets.clear();

		for (String pair : stored.split(";"))
		{
			int equals = pair.indexOf('=');

			if (equals > 0)
			{
				mapartPresets.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
			}
		}
	}

	private static String joinPresets()
	{
		StringBuilder out = new StringBuilder();

		for (Map.Entry<String, String> entry : mapartPresets.entrySet())
		{
			if (out.length() > 0)
			{
				out.append(';');
			}

			out.append(entry.getKey()).append('=').append(entry.getValue());
		}

		return out.toString();
	}

	private static boolean parse(String value, boolean fallback)
	{
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}

	private static int parseInt(String value, int fallback)
	{
		if (value == null)
		{
			return fallback;
		}

		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	private static long parseLong(String value, long fallback)
	{
		if (value == null)
		{
			return fallback;
		}

		try
		{
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
}

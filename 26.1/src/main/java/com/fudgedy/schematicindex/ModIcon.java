package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

// The font glyph is baked from a bundled PNG at resource-load time, so an icon downloaded from the admin site
// can only be served through the resource system; ResourceOverrideMixin returns this cache file for the texture id
public final class ModIcon
{
	private static final Identifier FONT_TEXTURE = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/font/modtag.png");
	private static final int RECONCILE_DELAY_SECONDS = 6;
	// Bounded so an outage is not polled all session; a missed icon costs nothing until next launch
	private static final int MAX_ATTEMPTS = 5;

	private static volatile boolean available;
	private static int attempts;
	private static ScheduledFuture<?> ticker;
	private static final AtomicBoolean RECONCILING = new AtomicBoolean();

	private ModIcon()
	{
	}

	public static boolean isOverride(Identifier id)
	{
		return available && FONT_TEXTURE.equals(id);
	}

	public static InputStream overrideStream() throws IOException
	{
		return Files.newInputStream(cacheFile());
	}

	public static synchronized void start()
	{
		dropSheetCache();
		available = Files.exists(cacheFile());

		if (ticker != null)
		{
			return;
		}

		// Retries because the catalogue is only reachable once the player has accepted the terms
		ticker = Net.scheduler().scheduleAtFixedRate(ModIcon::tick, RECONCILE_DELAY_SECONDS, RECONCILE_DELAY_SECONDS,
				TimeUnit.SECONDS);
	}

	private static void tick()
	{
		if (!RECONCILING.compareAndSet(false, true))
		{
			return;
		}

		Net.submit(() -> {
			try
			{
				reconcile();
			}
			finally
			{
				RECONCILING.set(false);
			}
		});
	}

	// scheduleAtFixedRate silently cancels the task forever if a run throws, so nothing here may escape
	private static void reconcile()
	{
		try
		{
			if (!Backend.configured())
			{
				return;
			}

			JsonObject content = Backend.getJsonAnon("/content");

			if (content == null)
			{
				attempts++;

				if (attempts >= MAX_ATTEMPTS)
				{
					SchematicIndexMod.LOGGER.info("[modicon] giving up after {} attempts", attempts);
					stop();
				}

				return;
			}

			attempts = 0;
			String url = Json.stringOf(content, "modIconUrl", "");

			if (url.isEmpty())
			{
				Files.deleteIfExists(cacheFile());
				Files.deleteIfExists(markerFile());
				SchematicIndexMod.LOGGER.info("[modicon] no custom icon set; using bundled");
				stop();
				return;
			}

			String marker = Files.exists(markerFile()) ? Files.readString(markerFile()).trim() : "";

			if (url.equals(marker) && Files.exists(cacheFile()))
			{
				SchematicIndexMod.LOGGER.info("[modicon] custom icon already cached");
				stop();
				return;
			}

			Files.createDirectories(cacheFile().getParent());

			if (!Backend.download(url, cacheFile()))
			{
				SchematicIndexMod.LOGGER.warn("[modicon] download failed, will retry: {}", url);
				return;
			}

			Files.writeString(markerFile(), url);
			available = true;
			Minecraft mc = Minecraft.getInstance();
			mc.execute(mc::reloadResourcePacks);
			SchematicIndexMod.LOGGER.info("[modicon] downloaded custom icon and reloaded resources");
			stop();
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("[modicon] reconcile error", e);
		}
	}

	// Stale glyph-sheet cache files only waste disk
	private static void dropSheetCache()
	{
		try
		{
			Files.deleteIfExists(cacheDirectory().resolve("modtag-sheet.png"));
			Files.deleteIfExists(cacheDirectory().resolve("modtag-sheet.json"));
			Path icons = cacheDirectory().resolve("badges");

			if (!Files.isDirectory(icons))
			{
				return;
			}

			List<Path> files;

			try (Stream<Path> walk = Files.walk(icons))
			{
				files = walk.sorted(Comparator.reverseOrder()).toList();
			}

			for (Path file : files)
			{
				Files.deleteIfExists(file);
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("[modicon] stale sheet cache not removed", e);
		}
	}

	private static synchronized void stop()
	{
		if (ticker != null)
		{
			ticker.cancel(false);
			ticker = null;
		}
	}

	private static Path cacheDirectory()
	{
		return FabricLoader.getInstance().getGameDir().resolve(SchematicIndexMod.MOD_ID);
	}

	private static Path cacheFile()
	{
		return cacheDirectory().resolve("modtag-override.png");
	}

	private static Path markerFile()
	{
		return cacheDirectory().resolve("modtag-override.url");
	}
}

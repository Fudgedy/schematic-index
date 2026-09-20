package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.mapart.MapCorners;
import com.fudgedy.schematicindex.update.UpdateNotice;
import fi.dy.masa.malilib.event.RenderEventHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchematicIndexMod implements ClientModInitializer
{
	public static final String MOD_ID = "schematicindex";
	public static final Logger LOGGER = LoggerFactory.getLogger("The Schematic Index");

	@Override
	public void onInitializeClient()
	{
		Settings.load();
		Keybinds.register();
		RenderEventHandler.getInstance().registerWorldLastRenderer(MapCorners.getInstance());
		Presence.start();
		UpdateNotice.start();
		ModTags.start();
		ModIcon.start();
		LOGGER.info("The Schematic Index loaded (catalogue: {})",
				Settings.hasApiBaseUrl() ? Settings.apiBaseUrl() : "not configured");
	}

	public static String currentVersion()
	{
		try
		{
			return FabricLoader.getInstance().getModContainer(MOD_ID)
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("0.0.0");
		}
		catch (Exception e)
		{
			return "0.0.0";
		}
	}
}

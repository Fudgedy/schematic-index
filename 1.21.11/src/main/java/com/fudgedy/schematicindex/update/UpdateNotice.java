package com.fudgedy.schematicindex.update;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// One toast per launch when Modrinth carries a newer release; never a lock, never a modal
public final class UpdateNotice
{
	private static final String PROJECT = "the-schematic-index";
	private static final String PAGE_URL = "https://modrinth.com/mod/" + PROJECT;
	private static final long LIFETIME_MS = Toasts.defaultLifetime() * 3L;
	private static final String DEBUG_LATEST = "0.7.10";

	private static volatile boolean started;

	private UpdateNotice()
	{
	}

	// Called at client init and again on terms acceptance; whichever comes first with terms in place runs
	public static synchronized void start()
	{
		if (started || !Settings.termsAccepted())
		{
			return;
		}

		started = true;

		if (Settings.takeDebugUpdateToast())
		{
			announce(DEBUG_LATEST, SchematicIndexMod.currentVersion());
			return;
		}

		Net.submit(UpdateNotice::check);
	}

	private static void check()
	{
		try
		{
			ModUpdater.Release latest = ModUpdater.resolveLatest(PROJECT);
			String current = SchematicIndexMod.currentVersion();

			if (latest == null || !ModUpdater.isNewer(latest.version(), current))
			{
				return;
			}

			SchematicIndexMod.LOGGER.info("Schematic Index {} is on Modrinth (running {})", latest.version(), current);
			announce(latest.version(), current);
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Update check failed", e);
		}
	}

	private static void announce(String latest, String current)
	{
		Minecraft.getInstance().execute(() -> Toasts.pushAction("Update available",
				"Schematic Index " + latest + " is out. You're on " + current + ".",
				new ItemStack(Items.WRITABLE_BOOK), "Open Modrinth", () -> Util.getPlatform().openUri(PAGE_URL),
				LIFETIME_MS));
	}
}

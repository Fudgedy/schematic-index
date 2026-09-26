package com.fudgedy.schematicindex.update;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.ClientReady;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.NewsFeed;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.TimeUnit;

// One toast per launch when a newer release is known; the Index header keeps a pill up after it fades
public final class UpdateNotice
{
	private static final long LIFETIME_MS = Toasts.defaultLifetime() * 3L;
	private static final long READY_RETRY_SECONDS = 5;
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
			// The switch and the server's latest come from /content, which nothing else has fetched this early
			if (!RemoteContent.loaded())
			{
				RemoteContent.refresh();
			}

			UpdateGate.setModrinth(ModUpdater.resolveLatest(UpdateGate.PROJECT));
			String latest = UpdateGate.latestKnown();
			String current = SchematicIndexMod.currentVersion();

			if (latest == null || !ModUpdater.isNewer(latest, current))
			{
				return;
			}

			SchematicIndexMod.LOGGER.info("Schematic Index {} is out (running {})", latest, current);

			if (!RemoteContent.feature("updatePrompt"))
			{
				return;
			}

			if (!NewsFeed.loaded())
			{
				NewsFeed.refresh();
			}

			announce(latest, current);
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Update check failed", e);
		}
	}

	private static void announce(String latest, String current)
	{
		String highlight = NewsFeed.highlightFor(latest);
		String text = highlight == null
				? "Schematic Index " + latest + " is out. You're on " + UpdateGate.currentVersion() + "."
				: latest + " is out: " + highlight;
		Minecraft.getInstance().execute(() -> showToast(text));
	}

	// The client init entrypoint that starts the check can run before item holders are bound, so wait it out
	private static void showToast(String text)
	{
		if (!Settings.flag(SettingsKeys.UPDATE_NOTICES, true))
		{
			return;
		}

		if (!ClientReady.ready())
		{
			Net.scheduler().schedule(() -> Minecraft.getInstance().execute(() -> showToast(text)), READY_RETRY_SECONDS,
					TimeUnit.SECONDS);
			return;
		}

		Toasts.pushAction("Update available", text, new ItemStack(Items.WRITABLE_BOOK), "Open Modrinth",
				() -> IndexScreen.openExternal(UpdateGate.downloadUrl()), LIFETIME_MS);
	}
}

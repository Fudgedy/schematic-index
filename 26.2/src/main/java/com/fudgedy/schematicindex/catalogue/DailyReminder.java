package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// A launch-time nudge that today's Shards are waiting, claimable from the toast without opening the Index.
// The title screen only starts a poll rather than a single attempt: content, verify and the shards fetch are
// three sequential network round trips that can easily outlive the title screen, and a player who is already
// in a world by the time they finish must still get the reminder
public final class DailyReminder
{
	private static final long POLL_DELAY_SECONDS = 5;
	private static final long POLL_INTERVAL_SECONDS = 20;

	private static final AtomicBoolean POLLING = new AtomicBoolean();
	private static final AtomicBoolean ATTEMPT_RUNNING = new AtomicBoolean();
	private static volatile boolean decided;
	private static volatile @Nullable ScheduledFuture<?> pollTask;

	private DailyReminder()
	{
	}

	public static void onTitleScreen()
	{
		if (!POLLING.compareAndSet(false, true))
		{
			return;
		}

		SchematicIndexMod.LOGGER.debug("Daily reminder: polling started at title screen");
		pollTask = Net.scheduler().scheduleAtFixedRate(DailyReminder::poll, POLL_DELAY_SECONDS, POLL_INTERVAL_SECONDS,
				TimeUnit.SECONDS);
	}

	private static void poll()
	{
		try
		{
			attempt();
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder poll failed", e);
			ATTEMPT_RUNNING.set(false);
		}
	}

	private static void attempt()
	{
		if (decided || !Settings.termsAccepted() || !Settings.flag(SettingsKeys.DAILY_REMINDER, true)
				|| !ATTEMPT_RUNNING.compareAndSet(false, true))
		{
			return;
		}

		Net.submit(DailyReminder::prepare);
	}

	private static void prepare()
	{
		try
		{
			// Nothing has fetched /content this early, and the switch lives there
			if (!RemoteContent.loaded())
			{
				RemoteContent.refresh();
			}

			if (!RemoteContent.feature("dailyToast"))
			{
				SchematicIndexMod.LOGGER.debug("Daily reminder: dailyToast feature is off");
				finishAttempt(false);
				return;
			}

			Minecraft.getInstance().execute(() -> McAuth.ensureVerified(DailyReminder::afterVerify));
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder: content check failed, will retry", e);
			finishAttempt(false);
		}
	}

	private static void afterVerify()
	{
		if (!McAuth.verified())
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder: verify failed ({}), will retry", McAuth.failureCode());
			finishAttempt(false);
			return;
		}

		Net.submit(() -> {
			try
			{
				JsonObject panel = Backend.myShards();

				if (panel == null)
				{
					SchematicIndexMod.LOGGER.debug("Daily reminder: GET /me/shards failed, will retry");
					finishAttempt(false);
					return;
				}

				Shards.applyPanel(panel);
				Minecraft.getInstance().execute(DailyReminder::announce);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.debug("Daily reminder: shards fetch failed, will retry", e);
				finishAttempt(false);
			}
		});
	}

	private static void announce()
	{
		String today = LocalDate.now(ZoneOffset.UTC).toString();

		if (Shards.claimedToday())
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder: already claimed today, nothing to show");
			finishAttempt(true);
			return;
		}

		Shards.Day card = todayCard();

		if (card == null)
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder: no 'today' day card in the panel yet, will retry");
			finishAttempt(false);
			return;
		}

		if (today.equals(Settings.text(SettingsKeys.LAST_DAILY_TOAST_DAY, "")))
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder: already shown today ({})", today);
			finishAttempt(true);
			return;
		}

		if (!ClientReady.ready())
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder: client still loading, will retry");
			finishAttempt(false);
			return;
		}

		String text = "Day " + card.day() + " · +" + card.reward() + " Shards";

		// The reward table steps up on days 8 and 15
		if (card.day() == 7 || card.day() == 14)
		{
			text += " · Rewards go up from tomorrow";
		}

		ItemStack icon = new ItemStack(Items.AMETHYST_SHARD);

		// Action pills only take clicks on a screen, so in a world the toast points at the Index instead
		if (Minecraft.getInstance().level != null)
		{
			Toasts.push("Daily reward ready", text + ". Open the Index to claim.", icon);
			SchematicIndexMod.LOGGER.debug("Daily reminder: shown in-world, day {}", card.day());
		}
		else
		{
			Toasts.pushAction("Daily reward ready", text, icon, "Claim",
					() -> Shards.claimDaily(earned -> Usage.once("daily_toast_claim")));
			SchematicIndexMod.LOGGER.debug("Daily reminder: shown at title screen, day {}", card.day());
		}

		Settings.setText(SettingsKeys.LAST_DAILY_TOAST_DAY, today);
		finishAttempt(true);
	}

	// done stops the poll for the rest of the session; a false leaves it running so the next tick can retry
	// whatever step failed, and also catches a player who reached a world before the chain finished
	private static void finishAttempt(boolean done)
	{
		ATTEMPT_RUNNING.set(false);

		if (!done)
		{
			return;
		}

		decided = true;
		ScheduledFuture<?> task = pollTask;

		if (task != null)
		{
			task.cancel(false);
		}
	}

	private static Shards.@Nullable Day todayCard()
	{
		for (Shards.Day day : Shards.days())
		{
			if ("today".equals(day.state()))
			{
				return day;
			}
		}

		return null;
	}
}

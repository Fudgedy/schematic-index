package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

// Client mirror of the shard wallet panel (/me/shards). Every claim POST returns the fresh panel,
// so the holder replaces its state from whatever the server sends back
public final class Shards
{
	public record Day(int day, int reward, String state)
	{
	}

	// page is the rail page the quest is done on, or null when there is nowhere to send the player
	public record Quest(String id, String icon, String title, String description, int progress, int target, int reward, String state,
			boolean weekly, String page)
	{
	}

	public record Referral(String code, int invited, int rewarded, int pending, boolean redeemed, int reward,
			int streak, int streakRequired)
	{
		public boolean canRedeem()
		{
			return this.streak >= this.streakRequired;
		}
	}

	public static final int DEFAULT_REFERRAL_REWARD = 200;
	public static final int DEFAULT_STREAK_REQUIRED = 3;

	private static final long BALANCE_TWEEN_MS = 600L;
	private static final long STREAK_RISK_MS = 6L * 3_600_000L;
	private static final AtomicBoolean STREAK_RISK_TOASTED = new AtomicBoolean();
	// Long enough for the server to have counted the write the poke follows
	private static final long POKE_DELAY_MS = 1500L;
	private static final Executor POKE_DELAY = CompletableFuture.delayedExecutor(POKE_DELAY_MS, TimeUnit.MILLISECONDS, Net::submit);
	private static final AtomicBoolean POKE_PENDING = new AtomicBoolean();
	private static volatile int balance;
	private static volatile int streak;
	private static volatile int bestStreak;
	private static volatile int focusDay;
	private static volatile boolean claimedToday;
	private static volatile long weekResetsAt;
	private static volatile long streakBreaksAt;
	private static volatile List<Day> days = List.of();
	private static volatile List<Quest> quests = List.of();
	private static volatile Referral referral;
	private static volatile boolean loading;
	private static volatile boolean loadingReferral;
	private static volatile boolean redeeming;
	private static int shownFrom;
	private static int shownTarget;
	private static long shownSince;

	private Shards()
	{
	}

	public static int balance()
	{
		return balance;
	}

	public static int streak()
	{
		return streak;
	}

	public static int bestStreak()
	{
		return bestStreak;
	}

	public static int focusDay()
	{
		return focusDay;
	}

	public static boolean claimedToday()
	{
		return claimedToday;
	}

	public static List<Day> days()
	{
		return days;
	}

	public static List<Quest> quests()
	{
		return quests;
	}

	public static long weekResetsAt()
	{
		return weekResetsAt;
	}

	public static boolean streakAtRisk()
	{
		return !claimedToday && streak > 0 && streakBreaksAt > 0L
				&& streakBreaksAt - System.currentTimeMillis() < STREAK_RISK_MS;
	}

	public static String streakBreakText()
	{
		if (streakBreaksAt <= 0L || streak <= 0)
		{
			return null;
		}

		long remaining = Math.max(0L, streakBreaksAt - System.currentTimeMillis());
		return "Streak breaks in " + remaining / 3_600_000L + "h " + remaining % 3_600_000L / 60_000L + "m";
	}

	public static Referral referral()
	{
		return referral;
	}

	public static boolean isRedeeming()
	{
		return redeeming;
	}

	// The balance as the pill draws it this frame, counting toward the real one so a claim or purchase reads as motion
	public static int displayedBalance()
	{
		int target = McAuth.shards();
		long now = Util.getMillis();

		if (target != shownTarget)
		{
			shownFrom = displayedAt(now);
			shownTarget = target;
			shownSince = now;
		}

		return displayedAt(now);
	}

	public static int balanceTrend()
	{
		if (Util.getMillis() - shownSince >= BALANCE_TWEEN_MS)
		{
			return 0;
		}

		return Integer.signum(shownTarget - shownFrom);
	}

	private static int displayedAt(long now)
	{
		float t = (now - shownSince) / (float) BALANCE_TWEEN_MS;
		return shownFrom + Math.round((shownTarget - shownFrom) * Theme.easeOut(t));
	}

	public static void refresh()
	{
		if (loading)
		{
			return;
		}

		loading = true;
		Net.submit(() -> {
			try
			{
				apply(Backend.myShards());
			}
			finally
			{
				loading = false;
			}
		});
	}

	// One deferred refresh after an action that can move a quest; pokes inside the window fold into the pending one
	public static void pokeSoon()
	{
		if (!McAuth.verified() || !POKE_PENDING.compareAndSet(false, true))
		{
			return;
		}

		POKE_DELAY.execute(() -> {
			POKE_PENDING.set(false);
			refresh();
		});
	}

	// onClaimed receives the shards earned, on the client thread, only once the server has credited them
	public static void claimDaily(IntConsumer onClaimed)
	{
		int before = balance;
		Net.submit(() -> {
			JsonObject panel = Backend.claimDaily();

			if (panel == null)
			{
				fail(Errors.SHARD_DAILY, "Could not claim today's reward, try again in a moment.");
				return;
			}

			apply(panel);
			int earned = balance - before;
			Minecraft.getInstance().execute(() -> {
				onClaimed.accept(earned);

				if (earned > 0)
				{
					Toasts.push("Daily Login", "You earned " + earned + " shards, come back tomorrow to claim more shards.",
							new ItemStack(Items.AMETHYST_SHARD));
				}
			});
		});
	}

	// Toasts must be pushed on the client thread, not the request worker
	private static void fail(String code, String body)
	{
		Errors.report(code);
		Minecraft.getInstance().execute(() -> Toasts.push("Shards", body, new ItemStack(Items.AMETHYST_SHARD)));
	}

	// Dismissing the primer is the acknowledgement the server waits for before crediting the welcome shards;
	// the seen flag only lands once they are on the balance, so a failed claim gets another try next launch
	public static void claimWelcome()
	{
		Net.submit(() -> {
			Backend.ApiResult result = Backend.claimWelcome();
			SchematicIndexMod.LOGGER.debug("welcome/claim -> {} {}", result.status(), result.error());

			if (!result.ok() || result.body() == null)
			{
				Errors.report(Errors.SHARD_WELCOME);
				return;
			}

			McAuth.setShards(intOf(result.body(), "balance"));
			McAuth.setWelcomePending(false);
			Settings.markShardWelcomeSeen();
		});
	}

	public static void refreshReferral()
	{
		if (loadingReferral)
		{
			return;
		}

		loadingReferral = true;
		Net.submit(() -> {
			try
			{
				applyReferral(Backend.referral());
			}
			finally
			{
				loadingReferral = false;
			}
		});
	}

	// Enters a friend's code; every refusal has its own toast since each asks the player to do something different
	public static void redeemReferral(String code, Runnable onRedeemed)
	{
		if (redeeming)
		{
			return;
		}

		redeeming = true;
		Net.submit(() -> {
			try
			{
				Backend.ApiResult result = Backend.redeemReferral(code);

				if (result.ok())
				{
					applyReferral(result.body());
					refreshReferral();
					refresh();
					Minecraft.getInstance().execute(() -> {
						Toasts.push("Code Redeemed", "You and your friend both earned " + rewardOrDefault() + " Shards.",
								new ItemStack(Items.AMETHYST_SHARD));
						onRedeemed.run();
					});
					return;
				}

				String body = redeemFailure(result);
				Minecraft.getInstance().execute(() -> Toasts.push("Code Not Redeemed", body, new ItemStack(Items.AMETHYST_SHARD)));
			}
			finally
			{
				redeeming = false;
			}
		});
	}

	private static String redeemFailure(Backend.ApiResult result)
	{
		String error = result.error();

		if (result.status() == 429)
		{
			return "Too many attempts, try again in a minute.";
		}

		if (error == null)
		{
			return "The code could not be redeemed right now.";
		}

		return switch (error)
		{
			case "unknown_code" -> "That code doesn't exist.";
			case "own_code" -> "You can't redeem your own code.";
			case "already_redeemed" -> "You've already redeemed a code.";
			case "too_old" -> "Only accounts newer than 7 days can redeem a code.";
			case "streak_required" -> "You need a " + streakRequiredOrDefault() + " Day Streak to redeem a code.";
			case "inviter_streak" -> "Your friend needs a " + streakRequiredOrDefault() + " Day Streak first.";
			default -> "The code could not be redeemed right now.";
		};
	}

	public static int rewardOrDefault()
	{
		return referral == null ? DEFAULT_REFERRAL_REWARD : referral.reward();
	}

	public static int streakRequiredOrDefault()
	{
		return referral == null ? DEFAULT_STREAK_REQUIRED : referral.streakRequired();
	}

	public static void startQuest(String questId)
	{
		Net.submit(() -> {
			JsonObject panel = Backend.startQuest(questId);

			if (panel == null)
			{
				fail(Errors.SHARD_QUEST, "Could not start the quest, try again in a moment.");
				return;
			}

			apply(panel);
		});
	}

	public static void claimQuest(String questId, IntConsumer onClaimed)
	{
		int before = balance;
		Net.submit(() -> {
			JsonObject panel = Backend.claimQuest(questId);

			if (panel == null)
			{
				fail(Errors.SHARD_QUEST, "Could not claim the quest reward, try again in a moment.");
				return;
			}

			apply(panel);
			int earned = balance - before;
			Minecraft.getInstance().execute(() -> onClaimed.accept(earned));
		});
	}

	// A shard purchase echoes the fresh wallet panel; routing it here keeps both balance mirrors in sync
	public static void applyPanel(JsonObject panel)
	{
		apply(panel);
	}

	private static void apply(JsonObject o)
	{
		if (o == null)
		{
			return;
		}

		balance = intOf(o, "balance");
		streak = intOf(o, "streak");
		bestStreak = intOf(o, "bestStreak");
		focusDay = intOf(o, "focusDay");
		claimedToday = boolOf(o, "claimedToday");
		weekResetsAt = Json.longOf(o, "weekResetsAt", 0L);
		streakBreaksAt = Json.longOf(o, "streakBreaksAt", 0L);
		days = parseDays(o);
		List<Quest> before = quests;
		quests = parseQuests(o);
		toastFinishedQuests(before, quests);
		McAuth.setShards(balance);
		toastReferralNews(o);
		toastStreakRisk();
	}

	// Once per launch, so the minute poll does not nag; the pill keeps pulsing until the claim lands
	private static void toastStreakRisk()
	{
		if (!streakAtRisk() || !STREAK_RISK_TOASTED.compareAndSet(false, true))
		{
			return;
		}

		Minecraft.getInstance().execute(() -> Toasts.pushAction("Streak At Risk",
				"Claim today's Shards before your streak breaks", new ItemStack(Items.AMETHYST_SHARD), "Open panel",
				IndexScreen::requestShardPanel));
	}

	// Rewards that landed since the panel was last opened; the server sends each one exactly once
	private static void toastReferralNews(JsonObject o)
	{
		if (!o.has("referralNews") || !o.get("referralNews").isJsonArray())
		{
			return;
		}

		for (JsonElement element : o.getAsJsonArray("referralNews"))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject news = element.getAsJsonObject();
			String name = stringOf(news, "name");
			int amount = intOf(news, "amount");
			Minecraft.getInstance().execute(() -> Toasts.push("Referral Reward",
					"You earned " + amount + " Shards thanks to " + (name.isEmpty() ? "a friend" : name) + ".",
					new ItemStack(Items.AMETHYST_SHARD)));
		}
	}

	// Only a quest the player had running counts as finished; a first load has nothing to compare against
	private static void toastFinishedQuests(List<Quest> before, List<Quest> after)
	{
		if (before.isEmpty())
		{
			return;
		}

		for (Quest quest : after)
		{
			if (!"claimable".equals(quest.state()))
			{
				continue;
			}

			for (Quest old : before)
			{
				if (old.id().equals(quest.id()) && "in_progress".equals(old.state()))
				{
					Minecraft.getInstance().execute(() ->
					{
						Toasts.push("Quest Complete",
								quest.title() + " is done. Claim " + quest.reward() + " Shards in the shard panel.",
								new ItemStack(Items.AMETHYST_SHARD));
						Theme.success();
					});
				}
			}
		}
	}

	private static void applyReferral(JsonObject o)
	{
		if (o == null || !o.has("code"))
		{
			return;
		}

		referral = new Referral(stringOf(o, "code"), intOf(o, "invited"), intOf(o, "rewarded"), intOf(o, "pending"),
				boolOf(o, "redeemed"), intOr(o, "reward", DEFAULT_REFERRAL_REWARD), intOf(o, "streak"),
				intOr(o, "streakRequired", DEFAULT_STREAK_REQUIRED));
	}

	private static List<Day> parseDays(JsonObject o)
	{
		List<Day> out = new ArrayList<>();

		if (o.has("days") && o.get("days").isJsonArray())
		{
			for (JsonElement element : o.getAsJsonArray("days"))
			{
				if (!element.isJsonObject())
				{
					continue;
				}

				JsonObject d = element.getAsJsonObject();
				out.add(new Day(intOf(d, "day"), intOf(d, "reward"), stringOf(d, "state")));
			}
		}

		return out;
	}

	private static List<Quest> parseQuests(JsonObject o)
	{
		List<Quest> out = new ArrayList<>();

		if (o.has("quests") && o.get("quests").isJsonArray())
		{
			for (JsonElement element : o.getAsJsonArray("quests"))
			{
				if (!element.isJsonObject())
				{
					continue;
				}

				JsonObject q = element.getAsJsonObject();
				String page = stringOf(q, "page");
				out.add(new Quest(stringOf(q, "id"), stringOf(q, "icon"), stringOf(q, "title"), stringOf(q, "desc"),
						intOf(q, "progress"), intOf(q, "target"), intOf(q, "reward"), stringOf(q, "state"),
						boolOf(q, "weekly"), page.isEmpty() ? null : page));
			}
		}

		return out;
	}

	private static int intOf(JsonObject o, String key)
	{
		return intOr(o, key, 0);
	}

	private static int intOr(JsonObject o, String key, int fallback)
	{
		return Json.intOf(o, key, fallback);
	}

	private static String stringOf(JsonObject o, String key)
	{
		return Json.stringOf(o, key, "");
	}

	private static boolean boolOf(JsonObject o, String key)
	{
		return Json.boolOf(o, key, false);
	}
}

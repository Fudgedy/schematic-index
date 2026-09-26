package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Glyphs;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Rows;
import com.fudgedy.schematicindex.gui.widget.States;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// The Streak tab: streak hero with the one Claim, a seven-day window of the strip and the freeze row
final class ShardDaily
{
	private static final int VISIBLE_DAYS = 7;
	private static final int TILE_GAP = Theme.SPACE_S;
	private static final int TILE_HEIGHT = 72;
	private static final int SECTION_GAP = Theme.SPACE_XL;
	private static final int BANNER_HEIGHT = 20;
	private static final int HERO_HEIGHT = 31;
	private static final int CLAIM_MIN_WIDTH = 96;
	private static final String CLAIMED = "Claimed today";
	private static final String CLAIMING = "Claiming…";
	private static final int FREEZE_SLOTS_MAX = 4;
	private static final String DOT = " · ";
	private static final String RISK_HINT = ". Claim today to keep it.";
	private static final String RISK_PREFIX = "Streak ends in ";
	private static final Identifier LOCK = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/lock.png");
	private static final Identifier FLAME = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/streak_flame.png");
	private static final int FLAME_SIZE = 18;
	private static final int FLAME_FRAMES = 3;
	private static final Identifier FREEZE = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/freeze.png");
	private static final Identifier FREEZE_LIVE = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/freeze_live.png");
	private static final int FREEZE_LIVE_FRAMES = 6;
	private static final Identifier FROST_LEFT = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/frozen_frost_left.png");
	private static final Identifier FROST_RIGHT = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/frozen_frost_right.png");
	private static final int FROST_LEFT_WIDTH = 25;
	private static final int FROST_RIGHT_WIDTH = 26;
	private static final int FROST_HEIGHT = 72;
	// The fire and the ice cube share one beat
	private static final long TILE_FRAME_MS = 160L;
	private static final Identifier FREEZE_EMPTY = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/freeze_empty.png");
	private static final ItemStack CHEST = new ItemStack(Items.CHEST);

	private final ShardPanel panel;
	private final Rect claimDaily = new Rect();
	private final Rect buyFreeze = new Rect();
	private final Rect retry = new Rect();
	private final Rect strip = new Rect();
	private final Rect freezeRow = new Rect();
	private int windowStart;
	private boolean windowInit = true;
	// The window eases from where it was to the new start, so a wheel step reads as a slide
	private float windowFrom;
	private long windowMovedAt;

	ShardDaily(ShardPanel panel)
	{
		this.panel = panel;
	}

	void onOpen()
	{
		this.windowInit = true;
	}

	void clearRects()
	{
		this.claimDaily.set(0, 0, 0, 0);
		this.buyFreeze.set(0, 0, 0, 0);
		this.retry.set(0, 0, 0, 0);
		this.strip.set(0, 0, 0, 0);
	}

	int render(GuiGraphics ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		List<Shards.Day> days = Shards.days();

		if (days.isEmpty())
		{
			this.clearRects();
			return this.renderUnloaded(ctx, font, x, y, width, mouseX, mouseY);
		}

		if (Shards.streakAtRisk())
		{
			this.renderBanner(ctx, font, x, y, width);
			y += BANNER_HEIGHT + Theme.SPACE_S;
		}

		this.renderHero(ctx, font, days, x, y, width, mouseX, mouseY);
		y += HERO_HEIGHT + SECTION_GAP;
		this.renderStrip(ctx, font, days, x, y, width, mouseX, mouseY);
		y += TILE_HEIGHT + SECTION_GAP;
		return this.renderFreezes(ctx, font, x, y, width, mouseX, mouseY);
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.retry.contains(mouseX, mouseY))
		{
			Theme.click();
			Shards.refresh();
			return true;
		}

		if (this.claimDaily.contains(mouseX, mouseY))
		{
			Theme.buttonPress(this.claimDaily);
			Theme.beaconPowerSelect();
			int x = this.claimDaily.x + this.claimDaily.width / 2;
			int y = this.claimDaily.y;
			Shards.claimDaily(earned -> this.panel.spawnFloater(x, y, earned));
			return true;
		}

		if (this.buyFreeze.contains(mouseX, mouseY) && canBuyFreeze())
		{
			Theme.buttonPress(this.buyFreeze);
			int x = this.buyFreeze.x + this.buyFreeze.width / 2;
			int y = this.buyFreeze.y;
			Shards.buyFreeze(spent -> {
				Theme.beaconActivate();
				this.panel.spawnFloater(x, y, -spent);
			});
			return true;
		}

		return false;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		int count = Shards.days().size();

		if (!this.strip.contains(mouseX, mouseY) || count <= VISIBLE_DAYS || scrollY == 0.0D)
		{
			return false;
		}

		int next = Math.max(0, Math.min(this.windowStart - (int) Math.signum(scrollY), count - VISIBLE_DAYS));

		if (next != this.windowStart)
		{
			this.windowFrom = this.shownStart();
			this.windowMovedAt = System.currentTimeMillis();
			this.windowStart = next;
		}

		return true;
	}

	private int renderUnloaded(GuiGraphics ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		if (Shards.loadFailed() && !Shards.isLoading())
		{
			return States.error(ctx, font, x, y, width, "your streak", Errors.SHARD_LOAD, this.retry, mouseX, mouseY);
		}

		int tileWidth = (width - TILE_GAP * (VISIBLE_DAYS - 1)) / VISIBLE_DAYS;
		States.skeleton(ctx, x, y, Theme.SPACE_XL * 2 + Theme.SPACE_M, font.lineHeight * 2);
		States.skeleton(ctx, x, y + Theme.SPACE_XL - Theme.SPACE_2XS, Theme.SPACE_2XL * 2 + Theme.SPACE_L, font.lineHeight);
		int tilesY = y + HERO_HEIGHT + SECTION_GAP;

		for (int i = 0; i < VISIBLE_DAYS; i++)
		{
			States.skeleton(ctx, x + i * (tileWidth + TILE_GAP), tilesY, tileWidth, TILE_HEIGHT);
		}

		int rowY = tilesY + TILE_HEIGHT + SECTION_GAP;
		States.skeleton(ctx, x, rowY, width, Theme.H_ROW_2);
		return rowY + Theme.H_ROW_2;
	}

	// The server words the break time; the duration after its prefix is the part that carries the warning
	private void renderBanner(GuiGraphics ctx, Font font, int x, int y, int width)
	{
		Theme.roundedRect(ctx, x, y, width, BANNER_HEIGHT, Theme.RADIUS_CARD, Theme.WARNING_TINT);
		Glyphs.draw(ctx, Glyphs.CLOCK, x + Theme.SPACE_S, y + (BANNER_HEIGHT - Theme.ICON_S) / 2, Theme.WARNING);
		String text = Shards.streakBreakText();

		if (text == null)
		{
			return;
		}

		int textX = x + Theme.SPACE_S + Theme.ICON_S + Theme.SPACE_XS + Theme.SPACE_2XS;
		int textY = y + (BANNER_HEIGHT - font.lineHeight) / 2 + 1;
		int right = x + width - Theme.SPACE_S;

		String prefix = text.startsWith(RISK_PREFIX) ? RISK_PREFIX : "";
		String time = text.substring(prefix.length());
		Theme.text(ctx, font, prefix, textX, textY, Theme.TEXT);
		textX += font.width(prefix);
		Theme.text(ctx, font, time, textX, textY, Theme.WARNING);
		textX += font.width(time);
		Theme.text(ctx, font, Theme.clip(font, RISK_HINT, right - textX), textX, textY, Theme.TEXT_MUTE);
	}

	private void renderHero(GuiGraphics ctx, Font font, List<Shards.Day> days, int x, int y, int width,
			int mouseX, int mouseY)
	{
		int reward = todayReward(days);
		boolean claimed = Shards.claimedToday();
		boolean claiming = !claimed && Shards.isClaimingDaily();
		int buttonWidth = claimed ? Buttons.doneWidth(font, CLAIMED)
				: Math.max(CLAIM_MIN_WIDTH, claiming ? Buttons.width(font, CLAIMING) : Buttons.priceWidth(font, "Claim", reward));
		Rect slot = this.claimDaily;
		slot.set(x + width - buttonWidth, y + (HERO_HEIGHT - Theme.H_CONTROL_L) / 2, buttonWidth, Theme.H_CONTROL_L);
		int room = slot.x - Theme.SPACE_M - x;
		int streak = Shards.streak();
		int best = Shards.bestStreak();
		String current = "Current streak: " + streak + (streak == 1 ? " day" : " days");
		String highest = "Highest streak: " + best + (best == 1 ? " day" : " days");
		int textY = y + (HERO_HEIGHT - Theme.H_CONTROL_L) / 2;
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, current, room)), x, textY, Theme.TEXT);
		textY += font.lineHeight + Theme.SPACE_2XS;
		Theme.text(ctx, font, Theme.clip(font, highest, room), x, textY, Theme.TEXT_MUTE);

		if (claimed)
		{
			Buttons.done(ctx, font, slot, CLAIMED);
			slot.set(0, 0, 0, 0);
		}
		else if (claiming)
		{
			Buttons.button(ctx, font, slot, CLAIMING, Buttons.Kind.PRIMARY, false, mouseX, mouseY);
			slot.set(0, 0, 0, 0);
		}
		else
		{
			Buttons.price(ctx, font, slot, "Claim", reward, Buttons.Kind.PRIMARY, true, mouseX, mouseY);
		}
	}

	private void renderStrip(GuiGraphics ctx, Font font, List<Shards.Day> days, int x, int y, int width,
			int mouseX, int mouseY)
	{
		if (this.windowInit)
		{
			this.windowStart = Math.max(0, Math.min(focusIndex(days) - VISIBLE_DAYS / 2, Math.max(0, days.size() - VISIBLE_DAYS)));
			this.windowFrom = this.windowStart;
			this.windowMovedAt = 0L;
			this.windowInit = false;
		}

		this.windowStart = Math.min(this.windowStart, Math.max(0, days.size() - VISIBLE_DAYS));
		int tileWidth = (width - TILE_GAP * (VISIBLE_DAYS - 1)) / VISIBLE_DAYS;
		int step = tileWidth + TILE_GAP;
		int shown = Math.min(VISIBLE_DAYS, days.size());
		int stripWidth = shown * step - TILE_GAP;
		int baseX = x + (width - stripWidth) / 2;
		this.strip.set(baseX, y, stripWidth, TILE_HEIGHT);
		int offset = Math.round(this.shownStart() * step);
		ctx.enableScissor(baseX, y, baseX + stripWidth, y + TILE_HEIGHT);

		for (int i = 0; i < days.size(); i++)
		{
			int tileX = baseX + i * step - offset;

			if (tileX + tileWidth <= baseX || tileX >= baseX + stripWidth)
			{
				continue;
			}

			Shards.Day day = days.get(i);
			this.renderTile(ctx, font, day, tileX, y, tileWidth);

			if (this.strip.contains(mouseX, mouseY) && Theme.inside(mouseX, mouseY, tileX, y, tileWidth, TILE_HEIGHT))
			{
				Tooltip.show("Day " + day.day() + DOT + stateWords(day));
			}
		}

		ctx.disableScissor();
	}

	private void renderTile(GuiGraphics ctx, Font font, Shards.Day day, int x, int y, int width)
	{
		boolean claimed = day.state().equals("claimed");
		boolean today = day.state().equals("today");
		boolean todayClaimed = today && Shards.claimedToday();
		boolean locked = !claimed && !today && !day.frozen();
		boolean frozen = day.frozen();

		int fill = frozen ? Theme.FROZEN_TINT : (claimed || todayClaimed ? Theme.EMBER_TINT
				: (today ? Theme.SURFACE_ELEVATED : Theme.SURFACE));
		Theme.roundedRect(ctx, x, y, width, TILE_HEIGHT, Theme.RADIUS_CARD, fill);

		if (frozen)
		{
			Theme.roundedOutline(ctx, x, y, width, TILE_HEIGHT, Theme.RADIUS_CARD, Theme.FROZEN_BORDER);
			// Halves pinned to each edge since the tile width follows the panel; clipped so a narrow tile keeps them inside
			ctx.enableScissor(x, y, x + width, y + TILE_HEIGHT);
			Theme.image(ctx, FROST_LEFT, x, y, FROST_LEFT_WIDTH, FROST_HEIGHT, FROST_LEFT_WIDTH, FROST_HEIGHT);
			Theme.image(ctx, FROST_RIGHT, x + width - FROST_RIGHT_WIDTH, y, FROST_RIGHT_WIDTH, FROST_HEIGHT, FROST_RIGHT_WIDTH,
					FROST_HEIGHT);
			ctx.disableScissor();
		}
		else if (today)
		{
			Theme.roundedOutline(ctx, x, y, width, TILE_HEIGHT, Theme.RADIUS_CARD, todayClaimed ? Theme.HAIRLINE_STRONG : Theme.ACCENT_BRIGHT);
		}
		else if (locked)
		{
			Theme.roundedOutline(ctx, x, y, width, TILE_HEIGHT, Theme.RADIUS_CARD, Theme.HAIRLINE);
		}

		int iconX = x + (width - Theme.ICON_M) / 2;
		int iconY = y + (TILE_HEIGHT - Theme.ICON_M) / 2;

		// A frozen day paused the streak rather than earning it, so it wears the ice block, not the claim tick
		if (frozen)
		{
			Theme.frame(ctx, FREEZE_LIVE, iconX, iconY, Theme.ICON_M, Theme.ICON_M, tileFrame(FREEZE_LIVE_FRAMES), FREEZE_LIVE_FRAMES);
		}
		else if (claimed || todayClaimed)
		{
			Theme.frame(ctx, FLAME, iconX - 1, iconY - 1, FLAME_SIZE, FLAME_SIZE, tileFrame(FLAME_FRAMES), FLAME_FRAMES);
		}
		else if (today)
		{
			Theme.item(ctx, CHEST, iconX, iconY);
		}
		else
		{
			Theme.image(ctx, LOCK, iconX, iconY, Theme.ICON_M, Theme.ICON_M);
		}

		String label = Theme.clip(font, "Day " + day.day(), width - Theme.SPACE_XS);
		label = today ? Theme.bold(label) : label;
		int labelColor = today ? Theme.TEXT : Theme.TEXT_ASH;
		int labelX = x + (width - font.width(label)) / 2;
		int labelY = y + Theme.SPACE_S;
		Theme.text(ctx, font, label, labelX, labelY, labelColor);

		int amountColor = today && !todayClaimed ? Theme.SHARD_TEXT : (claimed || todayClaimed || frozen ? Theme.TEXT_MUTE : Theme.TEXT_ASH);
		String amount = Theme.count(day.reward());
		amount = today ? Theme.bold(amount) : amount;
		int pairWidth = Theme.ICON_S + Theme.SPACE_2XS + font.width(amount);
		int pairX = x + (width - pairWidth) / 2;
		int amountY = y + TILE_HEIGHT - Theme.SPACE_S - font.lineHeight + 1;
		Glyphs.shard(ctx, pairX, amountY);
		Theme.text(ctx, font, amount, pairX + Theme.ICON_S + Theme.SPACE_2XS, amountY, amountColor);
	}

	private int renderFreezes(GuiGraphics ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		this.freezeRow.set(x, y, width, Theme.H_ROW_2);
		Rows.row(ctx, this.freezeRow, false, mouseX, mouseY);

		int max = Shards.freezeMax();
		int have = Shards.freezes();
		int slotX = x + Theme.SPACE_S;
		int slotY = y + (Theme.H_ROW_2 - Theme.ICON_M) / 2;
		int slots;

		if (max <= FREEZE_SLOTS_MAX)
		{
			for (int i = 0; i < max; i++)
			{
				// The empty slot is the block's own dark silhouette, so a bought freeze fills exactly its shape
				Theme.image(ctx, i < have ? FREEZE : FREEZE_EMPTY, slotX + i * (Theme.ICON_M + Theme.SPACE_XS), slotY,
						Theme.ICON_M, Theme.ICON_M);
			}

			slots = max == 0 ? 0 : max * (Theme.ICON_M + Theme.SPACE_XS) - Theme.SPACE_XS;
		}
		else
		{
			Theme.image(ctx, FREEZE, slotX, slotY, Theme.ICON_M, Theme.ICON_M);
			String times = "×" + have;
			Theme.text(ctx, font, times, slotX + Theme.ICON_M + Theme.SPACE_XS, y + (Theme.H_ROW_2 - font.lineHeight) / 2 + 1, Theme.TEXT_MUTE);
			slots = Theme.ICON_M + Theme.SPACE_XS + font.width(times);
		}

		int price = Shards.freezePrice();
		int buttonWidth = Buttons.priceWidth(font, "Buy", price);
		this.buyFreeze.set(x + width - Theme.SPACE_S - buttonWidth, y + (Theme.H_ROW_2 - Theme.H_CONTROL) / 2, buttonWidth, Theme.H_CONTROL);
		boolean enabled = canBuyFreeze();
		Buttons.price(ctx, font, this.buyFreeze, "Buy", price, Buttons.Kind.SECONDARY, enabled, mouseX, mouseY);

		if (!enabled && !Shards.isBuyingFreeze() && this.buyFreeze.contains(mouseX, mouseY))
		{
			Tooltip.show(have >= max ? "You have the maximum of " + max
					: "You need " + Theme.count(price - Shards.balance()) + " more shards");
		}

		int textX = slotX + slots + (slots == 0 ? 0 : Theme.SPACE_M);
		int room = this.buyFreeze.x - Theme.SPACE_M - textX;
		String title = Theme.bold(Theme.clipBold(font, "Streak freezes", room));
		int titleY = y + Theme.SPACE_S;
		Theme.text(ctx, font, title, textX, titleY, Theme.TEXT);
		Theme.text(ctx, font, have + "/" + max, textX + font.width(title) + Theme.SPACE_XS + Theme.SPACE_2XS, titleY, Theme.TEXT_ASH);
		Theme.text(ctx, font, Theme.clip(font, "Saves your streak when you miss a day", room), textX,
				titleY + font.lineHeight + Theme.SPACE_2XS, Theme.TEXT_MUTE);
		return y + Theme.H_ROW_2;
	}

	private float shownStart()
	{
		float t = Theme.easeOut((System.currentTimeMillis() - this.windowMovedAt) / (float) Theme.MOTION_TAB_MS);
		return this.windowFrom + (this.windowStart - this.windowFrom) * t;
	}

	private static boolean canBuyFreeze()
	{
		return Shards.freezes() < Shards.freezeMax() && Shards.balance() >= Shards.freezePrice() && !Shards.isBuyingFreeze();
	}

	private static int focusIndex(List<Shards.Day> days)
	{
		for (int i = 0; i < days.size(); i++)
		{
			if (days.get(i).day() == Shards.focusDay())
			{
				return i;
			}
		}

		return 0;
	}

	private static int todayReward(List<Shards.Day> days)
	{
		for (Shards.Day day : days)
		{
			if (day.state().equals("today"))
			{
				return day.reward();
			}
		}

		return 0;
	}

	private static String stateWords(Shards.Day day)
	{
		int ahead = day.day() - Shards.focusDay();
		boolean today = day.state().equals("today");

		if (day.frozen() || day.state().equals("claimed") || today)
		{
			return day.frozen() ? "Frozen" : (today && !Shards.claimedToday() ? "Today" : "Claimed");
		}

		return ahead <= 1 ? "Unlocks tomorrow" : "Unlocks in " + ahead + " days";
	}

	private static int tileFrame(int frames)
	{
		return (int) (System.currentTimeMillis() / TILE_FRAME_MS % frames);
	}
}

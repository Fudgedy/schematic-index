package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.mapart.MapartUi;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.TextInput;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// The daily-login streak, invite card and quests behind the top-bar shard pill. Every claim re-pulls the
// panel from the server, so the view is always whatever /me/shards last returned
public class ShardPanel
{
	private static final int PAD = 16;
	private static final int CARD_GAP = 6;
	private static final int CARD_HEIGHT = 86;
	private static final int VISIBLE_DAYS = 7;
	private static final int QUEST_HEIGHT = 42;
	private static final String GO_GLYPH = "\u203a";
	private static final int GO_CELL = 10;
	private static final int REFERRAL_PAD = 8;
	private static final int KEY_ENTER = 257;
	private static final int KEY_KEYPAD_ENTER = 335;
	private static final long COPIED_FLASH_MS = 1400L;
	private static final long FLOAT_MS = 800L;
	private static final int FLOAT_RISE = 14;
	private static final long PULSE_MS = 1200L;
	// One full sine of t / 160 spans ~1005 ms; the negative half is clamped away so the heart rests between beats
	private static final long HEARTBEAT_MS = 1005L;
	private static final float HEARTBEAT_SCALE = 0.08F;
	private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-";
	// Brand green marks a claimed day; a reward icon shrinks to this to sit level with its number
	private static final int GREEN = 0xFF2A7B5B;
	private static final int LOCKED = 0xFF6E767C;
	private static final float REWARD_ICON = 0.6F;
	private static final Identifier LOCK = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/lock.png");
	private static final Identifier CALENDAR = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/calendar.png");
	private static final Identifier CHECK = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/check.png");
	private static final ItemStack SHARD = new ItemStack(Items.AMETHYST_SHARD);
	private static final ItemStack CHEST = new ItemStack(Items.CHEST);

	private final IndexScreen screen;
	private boolean open;
	// Horizontal scroll over the day strip; re-centred on the focus day whenever the panel opens
	private double dayScroll;
	private double dayScrollMax;
	private boolean dayScrollInit;
	private final Rect daysViewport = new Rect();
	private final Rect bounds = new Rect();
	// The header and balance footer stay put; everything between them scrolls inside this clip
	private final Rect viewport = new Rect();
	private final Rect closeButton = new Rect();
	private float scroll;
	private float maxScroll;
	private final Rect claimDaily = new Rect();
	private final Rect copyCode = new Rect();
	private final Rect redeemButton = new Rect();
	private final Rect codeField = new Rect();
	private final TextInput codeInput = new TextInput(12, CODE_CHARS);
	private long codeCopiedAt;
	// Parallel lists: each actionable quest button and the "start:<id>" / "claim:<id>" it triggers
	private final List<Rect> questButtons = new ArrayList<>();
	private final List<String> questActions = new ArrayList<>();
	private final List<Rect> questRects = new ArrayList<>();
	// Parallel lists: each quest row that leads somewhere and the rail page it opens
	private final List<Rect> questRows = new ArrayList<>();
	private final List<IndexScreen.Page> questPages = new ArrayList<>();
	private final List<Rect> questRowRects = new ArrayList<>();
	// A quest claimed while the panel is open stays on screen to show its payout; reopening retires it
	private final Set<String> justClaimed = new HashSet<>();
	private final List<Floater> floaters = new ArrayList<>();
	private List<Shards.Quest> pendingCache = List.of();
	private List<Shards.Quest> pendingSource;
	private final Map<String, QuestText> questText = new HashMap<>();
	private final Map<String, ItemStack> questIcons = new HashMap<>();

	public ShardPanel(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open()
	{
		this.open = true;
		this.dayScrollInit = true;
		this.scroll = 0.0F;
		this.justClaimed.clear();
		this.pendingSource = null;
		this.floaters.clear();
		this.codeInput.set("");
		Shards.refresh();
		Shards.refreshReferral();
	}

	public void close()
	{
		this.open = false;
		this.codeInput.blur();
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		List<Shards.Quest> quests = this.pendingQuests();
		int line = font.lineHeight;
		int width = Math.min(this.screen.width - 24, 540);
		int questBlock = quests.isEmpty() ? line + 6 : quests.size() * QUEST_HEIGHT;
		List<String> invite = this.screen.wrap(inviteText(), width - PAD * 2 - REFERRAL_PAD * 2, 3);
		int referralBlock = this.referralHeight(font, invite, Shards.referral());
		int header = PAD + 22;
		int footer = 12 + 20 + PAD;
		int streakBlock = Shards.streakBreakText() == null ? 0 : line + 4;
		int content = CARD_HEIGHT + 32 + streakBlock + 8 + line + 14 + referralBlock + 14 + line + 8 + questBlock;
		int height = Math.min(header + content + footer, this.screen.height - 24);
		int x = (this.screen.width - width) / 2;
		int y = (this.screen.height - height) / 2;
		this.bounds.set(x, y, width, height);
		this.viewport.set(x, y + header, width, height - header - footer);

		ModalChrome.frame(ctx, x, y, width, height, false);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_MODAL, Theme.SHARD);

		int inner = width - PAD * 2;
		Theme.image(ctx, CALENDAR, x + PAD, y + PAD, 16, 16);
		Theme.text(ctx, font, Theme.bold("Daily Login Streak"), x + PAD + 22, y + PAD + 4, Theme.TEXT);
		this.closeButton.set(x + width - 7, y - 7, 14, 14);
		Buttons.cornerClose(ctx, this.closeButton, mouseX, mouseY);

		// Clipped rows must not light up under a cursor that is really over the header or footer
		if (!this.viewport.contains(mouseX, mouseY))
		{
			mouseX = -1;
			mouseY = -1;
		}

		ctx.enableScissor(this.viewport.x, this.viewport.y, this.viewport.x + this.viewport.width,
				this.viewport.y + this.viewport.height);
		int textY = this.viewport.y - Math.round(this.scroll);

		textY = this.renderDays(ctx, font, x + PAD, textY, inner, mouseX, mouseY);
		textY += 8;

		Theme.text(ctx, font, Theme.bold("Current streak: " + Shards.streak() + streakUnit(Shards.streak())), x + PAD, textY, Theme.TEXT);
		String best = "Highest streak: " + Shards.bestStreak() + streakUnit(Shards.bestStreak());
		Theme.goldGradientText(ctx, font, best, x + width - PAD - font.width(Theme.bold(best)), textY, true);
		textY += line + 14;

		textY = this.renderReferral(ctx, font, invite, x + PAD, textY, inner, mouseX, mouseY) + 14;

		Theme.text(ctx, font, Theme.bold("Quests"), x + PAD, textY, Theme.TEXT);
		String resets = weekResetText(Shards.weekResetsAt());

		if (resets != null)
		{
			Theme.text(ctx, font, resets, x + width - PAD - font.width(resets), textY, Theme.TEXT_ASH);
		}

		textY += line + 8;
		textY = this.renderQuests(ctx, font, quests, x + PAD, textY, inner, mouseX, mouseY);
		ctx.disableScissor();

		// The estimate above only sizes the card; the scroll range comes from what was really drawn
		int drawn = textY + Math.round(this.scroll) - this.viewport.y;
		this.maxScroll = Math.max(0.0F, drawn - this.viewport.height);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		MapartUi.scrollbar(ctx, x + width - 5, this.viewport.y, this.viewport.height, this.scroll, this.maxScroll);
		this.renderBalance(ctx, font, x + PAD, y + height - PAD - 20, inner);
		this.renderFloaters(ctx, font);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.closeButton.contains(mouseX, mouseY))
		{
			this.close();
			Theme.click(0.9F);
			return true;
		}

		if (!this.viewport.contains(mouseX, mouseY))
		{
			this.codeInput.blur();
			return this.clickedOutside(mouseX, mouseY);
		}

		if (this.claimDaily.contains(mouseX, mouseY))
		{
			Theme.beaconPowerSelect();
			int x = this.claimDaily.x + this.claimDaily.width / 2;
			int y = this.claimDaily.y;
			Shards.claimDaily(earned -> this.spawnFloater(x, y, earned));
			return true;
		}

		if (this.copyCode.contains(mouseX, mouseY) && Shards.referral() != null)
		{
			this.screen.copyToClipboard(Shards.referral().code());
			this.codeCopiedAt = System.currentTimeMillis();
			Theme.click(1.2F);
			return true;
		}

		if (this.redeemButton.contains(mouseX, mouseY))
		{
			this.redeem();
			return true;
		}

		if (this.codeInput.click(mouseX, mouseY))
		{
			return true;
		}

		for (int i = 0; i < this.questButtons.size() && i < this.questActions.size(); i++)
		{
			if (this.questButtons.get(i).contains(mouseX, mouseY))
			{
				String action = this.questActions.get(i);

				if (action.startsWith("start:"))
				{
					Theme.beaconActivate();
					Shards.startQuest(action.substring(6));
				}
				else
				{
					Theme.raidVictory();
					this.justClaimed.add(action.substring(6));
					this.pendingSource = null;
					Rect button = this.questButtons.get(i);
					int x = button.x + button.width / 2;
					int y = button.y;
					Shards.claimQuest(action.substring(6), earned -> this.spawnFloater(x, y, earned));
				}

				return true;
			}
		}

		for (int i = 0; i < this.questRows.size() && i < this.questPages.size(); i++)
		{
			if (this.questRows.get(i).contains(mouseX, mouseY))
			{
				Theme.click();
				this.close();
				this.screen.switchPage(this.questPages.get(i));
				return true;
			}
		}

		return this.clickedOutside(mouseX, mouseY);
	}

	private static IndexScreen.Page pageFor(String page)
	{
		if (page == null)
		{
			return null;
		}

		return switch (page)
		{
			case "browse" -> IndexScreen.Page.BROWSE;
			case "premium" -> IndexScreen.Page.PREMIUM;
			case "saved" -> IndexScreen.Page.SAVED;
			case "mapart" -> IndexScreen.Page.MAPART;
			case "upload" -> IndexScreen.Page.UPLOAD;
			case "cosmetics" -> IndexScreen.Page.COSMETICS;
			case "settings" -> IndexScreen.Page.SETTINGS;
			default -> null;
		};
	}

	private boolean clickedOutside(double mouseX, double mouseY)
	{
		if (!this.bounds.contains(mouseX, mouseY))
		{
			this.close();
			Theme.click(0.9F);
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.codeInput.isFocused())
		{
			boolean enter = event.key() == KEY_ENTER || event.key() == KEY_KEYPAD_ENTER;
			this.codeInput.keyPressed(event);

			if (enter)
			{
				this.redeem();
			}

			return true;
		}

		if (event.key() == 256)
		{
			this.close();
		}

		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		return this.open && this.codeInput.charTyped(event);
	}

	private void redeem()
	{
		String code = this.codeInput.value().trim().toUpperCase();

		if (code.isEmpty() || Shards.isRedeeming())
		{
			return;
		}

		Theme.click();
		Shards.redeemReferral(code, () -> this.codeInput.set(""));
	}

	private int renderReferral(GuiGraphicsExtractor ctx, Font font, List<String> invite, int x, int y, int width,
			int mouseX, int mouseY)
	{
		Shards.Referral referral = Shards.referral();
		int height = this.referralHeight(font, invite, referral);
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.HAIRLINE);

		int cx = x + REFERRAL_PAD;
		int cy = y + REFERRAL_PAD;
		float beat = (float) Math.max(0.0, Math.sin(Util.getMillis() % HEARTBEAT_MS / 160.0));
		Theme.pushScale(ctx, cx + 4, cy + 4, 9, 9, 1.0F + HEARTBEAT_SCALE * beat);
		Theme.heart(ctx, cx + 4, cy + 4, true);
		Theme.pop(ctx);
		Theme.text(ctx, font, Theme.bold("Invite a friend"), cx + 20, cy + 4, Theme.TEXT);

		int copyWidth = font.width(Theme.bold("Copied!")) + 16;
		this.copyCode.set(x + width - REFERRAL_PAD - copyWidth, cy, copyWidth, 16);
		boolean flash = System.currentTimeMillis() - this.codeCopiedAt < COPIED_FLASH_MS;
		Buttons.pill(ctx, font, this.copyCode, flash ? "Copied!" : "Copy", mouseX, mouseY, false);
		int codeRoom = this.copyCode.x - 8 - (cx + 20 + font.width(Theme.bold("Invite a friend")) + 8);
		String code = Theme.clipBold(font, referral == null ? "..." : referral.code(), codeRoom);
		Theme.text(ctx, font, Theme.bold(code), this.copyCode.x - 8 - font.width(Theme.bold(code)), cy + 4, Theme.SHARD);
		cy += 16 + 6;

		for (String row : invite)
		{
			Theme.text(ctx, font, row, cx, cy, Theme.TEXT_MUTE);
			cy += font.lineHeight + 1;
		}

		cy += 4;
		String counts = referral == null ? "Loading..." : referral.invited() + " invited \u00b7 " + referral.rewarded()
				+ " rewarded \u00b7 " + referral.pending() + " pending";
		Theme.text(ctx, font, counts, cx, cy, Theme.TEXT_ASH);
		cy += font.lineHeight;

		if (referral == null || referral.redeemed())
		{
			this.redeemButton.set(0, 0, 0, 0);
			return y + height;
		}

		cy += 6;
		int redeemWidth = font.width(Theme.bold("Redeem")) + 20;
		this.redeemButton.set(x + width - REFERRAL_PAD - redeemWidth, cy, redeemWidth, 16);
		this.codeField.set(cx, cy, width - REFERRAL_PAD * 2 - redeemWidth - 6, 16);
		this.codeInput.render(ctx, font, this.codeField, "Friend's code", mouseX, mouseY);

		if (this.codeInput.value().trim().isEmpty() || Shards.isRedeeming())
		{
			Buttons.disabled(ctx, font, this.redeemButton, "Redeem");
		}
		else
		{
			Buttons.pill(ctx, font, this.redeemButton, "Redeem", mouseX, mouseY, true);
		}

		if (!referral.canRedeem())
		{
			cy += 16 + 4;
			String hint = "Redeeming unlocks at a " + referral.streakRequired() + " Day Streak";
			Theme.text(ctx, font, hint, cx, cy, Theme.TEXT_ASH);
		}

		return y + height;
	}

	// The origin is captured at click time, since the button rects are rebuilt every frame and the
	// claimed row may have moved or gone by the time the server answers
	private void spawnFloater(int x, int y, int reward)
	{
		if (reward <= 0)
		{
			return;
		}

		this.floaters.add(new Floater(Theme.bold("+" + reward), x, y - 2, Util.getMillis()));
	}

	private void renderFloaters(GuiGraphicsExtractor ctx, Font font)
	{
		long now = Util.getMillis();

		for (int i = this.floaters.size() - 1; i >= 0; i--)
		{
			Floater floater = this.floaters.get(i);
			float t = (now - floater.bornAt()) / (float) FLOAT_MS;

			if (t >= 1.0F)
			{
				this.floaters.remove(i);
				continue;
			}

			int y = floater.y() - Math.round(FLOAT_RISE * Theme.easeOut(t));
			Theme.text(ctx, font, floater.text(), floater.x() - font.width(floater.text()) / 2, y,
					Theme.withAlpha(Theme.SHARD, 1.0F - t));
		}
	}

	private static String inviteText()
	{
		return "Give it to a friend. Once you both have a " + Shards.streakRequiredOrDefault()
				+ " Day Streak, they can enter it and you both get " + Shards.rewardOrDefault() + " Shards.";
	}

	private int referralHeight(Font font, List<String> invite, Shards.Referral referral)
	{
		int height = REFERRAL_PAD + 16 + 6 + invite.size() * (font.lineHeight + 1) + 4 + font.lineHeight + REFERRAL_PAD;

		if (referral == null || referral.redeemed())
		{
			return height;
		}

		return referral.canRedeem() ? height + 6 + 16 : height + 6 + 16 + 4 + font.lineHeight;
	}

	private static String weekResetText(long resetsAt)
	{
		if (resetsAt <= 0L)
		{
			return null;
		}

		long remaining = Math.max(0L, resetsAt - System.currentTimeMillis());
		long days = remaining / 86_400_000L;
		long hours = remaining % 86_400_000L / 3_600_000L;

		if (days == 0 && hours == 0)
		{
			return "Resets in <1h";
		}

		return "Resets in " + (days > 0 ? days + "d " : "") + hours + "h";
	}

	private int renderDays(GuiGraphicsExtractor ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		List<Shards.Day> days = Shards.days();
		int cardWidth = (width - CARD_GAP * (VISIBLE_DAYS - 1)) / VISIBLE_DAYS;
		int step = cardWidth + CARD_GAP;
		int strip = days.isEmpty() ? 0 : days.size() * step - CARD_GAP;
		this.dayScrollMax = Math.max(0, strip - width);
		this.daysViewport.set(x, y, width, CARD_HEIGHT);

		if (this.dayScrollInit)
		{
			int focusIndex = 0;

			for (int i = 0; i < days.size(); i++)
			{
				if (days.get(i).day() == Shards.focusDay())
				{
					focusIndex = i;
					break;
				}
			}

			this.dayScroll = Math.max(0, Math.min(focusIndex * (double) step, this.dayScrollMax));
			this.dayScrollInit = false;
		}

		ctx.enableScissor(x, y, x + width, y + CARD_HEIGHT);

		for (int i = 0; i < days.size(); i++)
		{
			int cx = x + i * step - (int) Math.round(this.dayScroll);

			if (cx + cardWidth >= x && cx <= x + width)
			{
				this.renderDayCard(ctx, font, days.get(i), cx, y, cardWidth);
			}
		}

		ctx.disableScissor();

		// A thin track + thumb under the strip, sized to the visible fraction, hinting it scrolls
		if (this.dayScrollMax > 0)
		{
			int trackY = y + CARD_HEIGHT + 5;
			Theme.roundedRect(ctx, x, trackY, width, 3, 1, Theme.SURFACE_ELEVATED);
			int thumbW = Math.max(24, (int) ((long) width * width / strip));
			int thumbX = x + (int) Math.round((width - thumbW) * (this.dayScroll / this.dayScrollMax));
			Theme.roundedRect(ctx, thumbX, trackY, thumbW, 3, 1, Theme.SHARD);
		}

		int belowCards = y + CARD_HEIGHT + 14;

		if (Shards.claimedToday())
		{
			this.claimDaily.set(0, 0, 0, 0);
			Theme.text(ctx, font, "Claimed today - come back tomorrow!", x, belowCards + 4, Theme.TEXT_MUTE);
		}
		else
		{
			this.claimDaily.set(x, belowCards, width, 18);
			Buttons.pill(ctx, font, this.claimDaily, "Claim Daily Reward", mouseX, mouseY, true);
		}

		String breaks = Shards.streakBreakText();

		if (breaks == null)
		{
			return belowCards + 18;
		}

		int color = Shards.streakAtRisk() ? pulse(Theme.GOLD) : Theme.TEXT_ASH;
		Theme.text(ctx, font, breaks, x + (width - font.width(breaks)) / 2, belowCards + 18 + 4, color);
		return belowCards + 18 + 4 + font.lineHeight;
	}

	// Alpha breathing shared with the top-bar pill, so both nudge in the same rhythm
	public static int pulse(int color)
	{
		float phase = Util.getMillis() % PULSE_MS / (float) PULSE_MS;
		return Theme.withAlpha(color, 0.7F + 0.3F * (float) Math.sin(phase * Math.PI * 2.0D));
	}

	private void renderDayCard(GuiGraphicsExtractor ctx, Font font, Shards.Day day, int cx, int y, int cardWidth)
	{
		boolean claimed = day.state().equals("claimed");
		boolean today = day.state().equals("today");
		int accent = claimed ? GREEN : (today ? Theme.GOLD : Theme.HAIRLINE);

		if (today && !Shards.claimedToday())
		{
			accent = pulse(accent);
		}

		Theme.roundedRect(ctx, cx, y, cardWidth, CARD_HEIGHT, Theme.RADIUS_CARD,
				today ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, cx, y, cardWidth, CARD_HEIGHT, Theme.RADIUS_CARD, accent);

		String label = Theme.clip(font, "Day " + day.day(), cardWidth - 4);
		Theme.text(ctx, font, Theme.bold(label), cx + (cardWidth - font.width(Theme.bold(label))) / 2, y + 8, Theme.TEXT);

		int iconX = cx + (cardWidth - 16) / 2;

		if (claimed)
		{
			Theme.image(ctx, CHECK, iconX, y + 24, 16, 16);
		}
		else if (today)
		{
			ctx.item(CHEST, iconX, y + 24);
		}
		else
		{
			Theme.image(ctx, LOCK, iconX, y + 24, 16, 16);
		}

		String state = claimed ? "Claimed" : (today ? "Today" : "Locked");
		int stateColor = claimed ? GREEN : (today ? Theme.GOLD : LOCKED);
		Theme.text(ctx, font, state, cx + (cardWidth - font.width(state)) / 2, y + 48, stateColor);

		String reward = Theme.bold(Integer.toString(day.reward()));
		int rewardWidth = 12 + font.width(reward);
		int rewardX = cx + (cardWidth - rewardWidth) / 2;
		Theme.itemScaled(ctx, SHARD, rewardX, y + CARD_HEIGHT - 17, REWARD_ICON);
		Theme.text(ctx, font, reward, rewardX + 12, y + CARD_HEIGHT - 16, today || claimed ? Theme.SHARD : LOCKED);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.dayScrollMax > 0 && this.viewport.contains(mouseX, mouseY) && this.daysViewport.contains(mouseX, mouseY))
		{
			this.dayScroll = Math.max(0, Math.min(this.dayScroll - scrollY * 24, this.dayScrollMax));
			return true;
		}

		this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}

	// Everything still worth showing: unclaimed quests, plus any just claimed in front of the player.
	// Shards swaps the list whole on every refresh, so identity says whether the cache still holds
	private List<Shards.Quest> pendingQuests()
	{
		List<Shards.Quest> source = Shards.quests();

		if (source == this.pendingSource)
		{
			return this.pendingCache;
		}

		List<Shards.Quest> pending = new ArrayList<>();

		for (Shards.Quest quest : source)
		{
			if (!quest.state().equals("claimed") || this.justClaimed.contains(quest.id()))
			{
				pending.add(quest);
			}
		}

		this.pendingCache = pending;
		this.pendingSource = source;
		return pending;
	}

	private QuestText questText(Font font, Shards.Quest quest, int titleWidth, int descriptionWidth)
	{
		QuestText cached = this.questText.get(quest.id());

		if (cached != null && cached.fits(quest, titleWidth, descriptionWidth))
		{
			return cached;
		}

		QuestText computed = new QuestText(quest.title(), quest.description(), titleWidth, descriptionWidth,
				Theme.bold(Theme.clipBold(font, quest.title(), titleWidth)), Theme.clip(font, quest.description(), descriptionWidth));
		this.questText.put(quest.id(), computed);
		return computed;
	}

	private int renderQuests(GuiGraphicsExtractor ctx, Font font, List<Shards.Quest> quests, int x, int y,
			int width, int mouseX, int mouseY)
	{
		this.questButtons.clear();
		this.questActions.clear();
		this.questRows.clear();
		this.questPages.clear();

		if (quests.isEmpty())
		{
			Theme.text(ctx, font, "No quests right now.", x, y, Theme.TEXT_MUTE);
			return y + font.lineHeight + 6;
		}

		int buttonWidth = font.width(Theme.bold("In Progress")) + 12;
		int buttonX = x + width - buttonWidth - 8 - GO_CELL;
		int rewardX = buttonX - 66;
		int barWidth = 92;
		int barX = rewardX - barWidth - 14;
		int row = 0;

		for (Shards.Quest quest : quests)
		{
			boolean available = quest.state().equals("available");
			boolean claimable = quest.state().equals("claimable");
			boolean claimed = quest.state().equals("claimed");
			int rowH = QUEST_HEIGHT - 4;
			int rowMid = y + rowH / 2;
			boolean rowHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + rowH;
			IndexScreen.Page page = pageFor(quest.page());

			Theme.roundedRect(ctx, x, y, width, rowH, Theme.RADIUS_CARD,
					rowHover && page != null ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
			Theme.roundedOutline(ctx, x, y, width, rowH, Theme.RADIUS_CARD, rowHover ? Theme.SHARD : Theme.HAIRLINE);
			ctx.item(this.questIcon(quest.icon()), x + 8, rowMid - 8);

			if (page != null)
			{
				Rect rowRect = Rect.pooled(this.questRowRects, this.questRows.size());
				rowRect.set(x, y, width, rowH);
				this.questRows.add(rowRect);
				this.questPages.add(page);
				Theme.text(ctx, font, GO_GLYPH, x + width - 8 - font.width(GO_GLYPH), rowMid - font.lineHeight / 2 + 1,
						rowHover ? Theme.SHARD : Theme.TEXT_MUTE);
			}

			int textLeft = x + 30;
			int textBlock = font.lineHeight * 2 + 1;
			int titleY = y + (rowH - textBlock) / 2;
			String prog = quest.progress() + " / " + quest.target();
			int chipWidth = quest.weekly() ? font.width("Weekly") + 6 : 0;
			int trailing = quest.weekly() ? chipWidth + 4 + font.width(prog) + 4 : 0;
			QuestText text = this.questText(font, quest, barX - textLeft - 6 - trailing, barX - textLeft - 6);
			String title = text.clippedTitle();
			Theme.text(ctx, font, title, textLeft, titleY, Theme.TEXT);

			if (quest.weekly())
			{
				int chipX = textLeft + font.width(title) + 4;
				Theme.roundedRect(ctx, chipX, titleY - 1, chipWidth, font.lineHeight + 1, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
				Theme.roundedOutline(ctx, chipX, titleY - 1, chipWidth, font.lineHeight + 1, Theme.RADIUS_PILL, Theme.ACCENT);
				Theme.text(ctx, font, "Weekly", chipX + 3, titleY, Theme.ACCENT_BRIGHT);
				Theme.text(ctx, font, prog, chipX + chipWidth + 4, titleY, Theme.TEXT_MUTE);
			}

			Theme.text(ctx, font, text.clippedDescription(), textLeft, titleY + font.lineHeight + 1, Theme.TEXT_MUTE);

			float frac = quest.target() == 0 ? 1.0F : Math.min(1.0F, (float) quest.progress() / quest.target());
			Theme.roundedRect(ctx, barX, rowMid - 2, barWidth, 5, 2, Theme.SURFACE_ELEVATED);
			Theme.roundedRect(ctx, barX, rowMid - 2, Math.max(2, Math.round(barWidth * frac)), 5, 2, claimed ? GREEN : Theme.ACCENT);

			if (!quest.weekly())
			{
				Theme.text(ctx, font, prog, barX + (barWidth - font.width(prog)) / 2, rowMid + 6, Theme.TEXT_MUTE);
			}

			Theme.text(ctx, font, "Reward", rewardX, rowMid - 9, Theme.TEXT_ASH);
			Theme.itemScaled(ctx, SHARD, rewardX, rowMid + 3, REWARD_ICON);
			Theme.text(ctx, font, Theme.bold(Integer.toString(quest.reward())), rewardX + 12, rowMid + 4, Theme.SHARD);

			Rect button = Rect.pooled(this.questRects, row++);
			button.set(buttonX, rowMid - 8, buttonWidth, 16);

			// The server starts weekly quests itself, so their row only ever offers Claim
			if (quest.weekly() && !claimable && !claimed)
			{
				y += QUEST_HEIGHT;
				continue;
			}

			if (available)
			{
				Buttons.pill(ctx, font, button, "Start", mouseX, mouseY, true);
				this.questButtons.add(button);
				this.questActions.add("start:" + quest.id());
			}
			else if (claimable)
			{
				Buttons.pill(ctx, font, button, "Claim", mouseX, mouseY, true);
				this.questButtons.add(button);
				this.questActions.add("claim:" + quest.id());
			}
			else
			{
				Buttons.disabled(ctx, font, button, claimed ? "Claimed" : "In Progress");
			}

			y += QUEST_HEIGHT;
		}

		return y;
	}

	private void renderBalance(GuiGraphicsExtractor ctx, Font font, int x, int y, int width)
	{
		Theme.roundedRect(ctx, x, y, width, 20, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, 20, Theme.RADIUS_PILL, Theme.HAIRLINE);
		Theme.itemScaled(ctx, SHARD, x + 8, y + 5, REWARD_ICON);
		Theme.text(ctx, font, Theme.bold("Your balance:"), x + 25, y + 6, Theme.TEXT);
		Theme.text(ctx, font, Theme.bold(Integer.toString(Shards.displayedBalance())),
				x + 25 + font.width(Theme.bold("Your balance: ")), y + 6, Theme.shardTint(Shards.balanceTrend()));
	}

	private static String streakUnit(int value)
	{
		return value == 1 ? " day" : " days";
	}

	// Paper for an item id this build does not know
	private ItemStack questIcon(String itemId)
	{
		String id = itemId == null ? "" : itemId;
		ItemStack cached = this.questIcons.get(id);

		if (cached != null)
		{
			return cached;
		}

		Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
		Item item = key == null ? Items.PAPER : BuiltInRegistries.ITEM.getValue(key);
		ItemStack stack = new ItemStack(item == Items.AIR ? Items.PAPER : item);
		this.questIcons.put(id, stack);
		return stack;
	}

	private record Floater(String text, int x, int y, long bornAt)
	{
	}

	private record QuestText(String title, String description, int titleWidth, int descriptionWidth, String clippedTitle,
			String clippedDescription)
	{
		boolean fits(Shards.Quest quest, int titleWidth, int descriptionWidth)
		{
			return this.titleWidth == titleWidth && this.descriptionWidth == descriptionWidth
					&& Objects.equals(this.title, quest.title()) && Objects.equals(this.description, quest.description());
		}
	}
}

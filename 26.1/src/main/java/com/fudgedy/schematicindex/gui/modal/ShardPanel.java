package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.Boards;
import com.fudgedy.schematicindex.catalogue.QuestExtras;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.mapart.MapartUi;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Glyphs;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Tabs;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// The shard wallet behind the top-bar pill: a fixed modal with Streak, Quests, Leaderboards and Referral tabs.
// Every claim re-pulls the panel from the server, so each tab shows whatever /me/shards last returned
public class ShardPanel
{
	public static final int DAILY = 0;
	public static final int QUESTS = 1;
	public static final int BOARDS = 2;
	public static final int INVITE = 3;
	private static final String[] LABELS = {"Streak", "Quests", "Leaderboards", "Referral"};
	private static final int HEIGHT = 300;
	private static final int TABS_Y = Theme.SPACE_2XL + Theme.SPACE_XS;
	private static final int BODY_Y = TABS_Y + Theme.H_TAB + Theme.SPACE_M;
	private static final int FLOAT_RISE = 14;
	private static final int KEY_ESCAPE = 256;
	private static final int KEY_TAB = 258;
	private static final ItemStack SHARD = new ItemStack(Items.AMETHYST_SHARD);
	private static final String BUY_LABEL = "Buy shards";

	// Remembered for the game session, so reopening lands where the player left off once nothing needs claiming
	private static int lastTab = DAILY;

	private final IndexScreen screen;
	private final ShardDaily daily = new ShardDaily(this);
	private final ShardQuests quests = new ShardQuests(this);
	private final ShardBoards boards = new ShardBoards(this);
	private final ShardInvite invite = new ShardInvite(this);
	private final Rect frame = new Rect();
	private final Rect body = new Rect();
	private final Rect closeButton = new Rect();
	private final Rect buyShards = new Rect();
	private final List<Rect> tabHits = new ArrayList<>();
	private final Tabs.State tabState = new Tabs.State();
	private final float[] scroll = new float[LABELS.length];
	private final List<Floater> floaters = new ArrayList<>();
	private boolean open;
	private long openedAt;
	private int selected = DAILY;
	private float maxScroll;

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
		this.openedAt = System.currentTimeMillis();
		this.floaters.clear();
		this.quests.resetSession();
		this.daily.onOpen();
		this.invite.onOpen();

		for (int i = 0; i < this.scroll.length; i++)
		{
			this.scroll[i] = 0.0F;
		}

		boolean loaded = !Shards.days().isEmpty();
		this.selected = loaded && !Shards.claimedToday() ? DAILY : (ShardQuests.claimableCount() > 0 ? QUESTS : lastTab);
		this.selected = this.selected == BOARDS && !Boards.enabled() ? DAILY : this.selected;
		Shards.refresh();
		Shards.refreshReferral();
		QuestExtras.refresh();
		this.boards.onOpen();
	}

	// For entry points that exist to show quests, such as the link modal's quests button
	public void showQuests()
	{
		this.select(QUESTS);
	}

	public void close()
	{
		this.open = false;
		this.invite.blur();
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ModalChrome.open(ctx, font, this.frame, this.screen.width, this.screen.height, Theme.MODAL_L, HEIGHT, "Shards", SHARD,
				this.closeButton, this.openedAt, mouseX, mouseY);
		this.renderBalance(ctx, font, mouseX, mouseY);

		if (this.selected == BOARDS && !Boards.enabled())
		{
			this.selected = DAILY;
		}

		int innerX = this.frame.x + Theme.SPACE_L;
		int innerWidth = this.frame.width - Theme.SPACE_L * 2;
		String[] labels = LABELS.clone();
		labels[BOARDS] = Boards.enabled() ? LABELS[BOARDS] : null;
		int claimable = ShardQuests.claimableCount();
		boolean dailyWaiting = !Shards.days().isEmpty() && !Shards.claimedToday();
		int[] badges = {dailyWaiting ? Tabs.DOT_BADGE : Tabs.NO_BADGE, claimable > 0 ? claimable : Tabs.NO_BADGE, Tabs.NO_BADGE,
				Tabs.NO_BADGE};
		Tabs.bar(ctx, font, this.tabState, this.tabHits, labels, badges, this.selected, innerX, this.frame.y + TABS_Y, innerWidth,
				mouseX, mouseY);

		this.body.set(innerX, this.frame.y + BODY_Y, innerWidth, this.frame.height - BODY_Y - Theme.SPACE_L);
		this.clearHiddenTabs();

		// Clipped rows must not light up under a cursor that is really over the header or tabs
		int bodyMouseX = this.body.contains(mouseX, mouseY) ? mouseX : -1;
		int bodyMouseY = this.body.contains(mouseX, mouseY) ? mouseY : -1;
		float offset = this.scroll[this.selected];
		int top = this.body.y - Math.round(offset);
		ctx.enableScissor(this.body.x, this.body.y, this.body.x + this.body.width, this.body.y + this.body.height);
		int bottom = switch (this.selected)
		{
			case QUESTS -> this.quests.render(ctx, font, this.body.x, top, this.body.width, bodyMouseX, bodyMouseY);
			case BOARDS -> this.boards.render(ctx, font, this.body.x, top, this.body.width, bodyMouseX, bodyMouseY);
			case INVITE -> this.invite.render(ctx, font, this.body.x, top, this.body.width, bodyMouseX, bodyMouseY);
			default -> this.daily.render(ctx, font, this.body.x, top, this.body.width, bodyMouseX, bodyMouseY);
		};
		ctx.disableScissor();

		this.maxScroll = Math.max(0.0F, bottom - top - this.body.height);
		this.scroll[this.selected] = Math.min(offset, this.maxScroll);
		MapartUi.scrollbar(ctx, this.frame.x + this.frame.width - Theme.SPACE_S, this.body.y, this.body.height,
				this.scroll[this.selected], this.maxScroll);
		this.renderFloaters(ctx, font);
		Tooltip.render(ctx, font, mouseX, mouseY, this.screen.width, this.screen.height);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.closeButton.contains(mouseX, mouseY) || !this.frame.contains(mouseX, mouseY))
		{
			this.close();
			Theme.click(0.9F);
			return true;
		}

		if (this.buyShards.contains(mouseX, mouseY))
		{
			Toasts.openStore();
			return true;
		}

		int tab = Tabs.hit(this.tabHits, mouseX, mouseY);

		if (tab >= 0)
		{
			if (tab != this.selected)
			{
				this.select(tab);
				Theme.tab();
			}

			return true;
		}

		if (!this.body.contains(mouseX, mouseY))
		{
			this.invite.blur();
			return true;
		}

		switch (this.selected)
		{
			case QUESTS -> this.quests.mouseClicked(mouseX, mouseY);
			case BOARDS -> this.boards.mouseClicked(mouseX, mouseY);
			case INVITE -> this.invite.mouseClicked(mouseX, mouseY);
			default -> this.daily.mouseClicked(mouseX, mouseY);
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.selected == INVITE && this.invite.isFocused())
		{
			return this.invite.keyPressed(event);
		}

		if (event.key() == KEY_ESCAPE)
		{
			this.close();
			Theme.click(0.9F);
		}
		else if (event.key() == KEY_TAB)
		{
			this.cycle(event.hasShiftDown() ? -1 : 1);
		}

		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		return this.open && this.selected == INVITE && this.invite.charTyped(event);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.selected == DAILY && this.body.contains(mouseX, mouseY) && this.daily.mouseScrolled(mouseX, mouseY, scrollY))
		{
			return true;
		}

		float next = this.scroll[this.selected] - (float) scrollY * IndexScreen.SCROLL_STEP;
		this.scroll[this.selected] = Math.max(0.0F, Math.min(this.maxScroll, next));
		return true;
	}

	IndexScreen screen()
	{
		return this.screen;
	}

	// The origin is captured at click time, since the button rects are rebuilt every frame and the
	// claimed row may have moved or gone by the time the server answers
	void spawnFloater(int x, int y, int delta)
	{
		if (delta == 0)
		{
			return;
		}

		String text = Theme.bold((delta > 0 ? "+" : "-") + Math.abs(delta));
		this.floaters.add(new Floater(text, x, y - Theme.SPACE_2XS, Util.getMillis(), delta > 0 ? Theme.SHARD_TEXT : Theme.SHARD_SPEND));
	}

	private void select(int tab)
	{
		this.selected = tab;
		lastTab = tab;
		this.invite.blur();
	}

	private void cycle(int direction)
	{
		int next = this.selected;

		for (int i = 0; i < LABELS.length; i++)
		{
			next = Math.floorMod(next + direction, LABELS.length);

			if (next != BOARDS || Boards.enabled())
			{
				break;
			}
		}

		this.select(next);
		Theme.tab();
	}

	// Hidden tabs keep last frame's rects otherwise, and an invisible button would still take clicks
	private void clearHiddenTabs()
	{
		if (this.selected != DAILY)
		{
			this.daily.clearRects();
		}

		if (this.selected != QUESTS)
		{
			this.quests.clearRects();
		}

		if (this.selected != BOARDS)
		{
			this.boards.clearRects();
		}

		if (this.selected != INVITE)
		{
			this.invite.clearRects();
		}
	}

	private void renderBalance(GuiGraphicsExtractor ctx, Font font, int mouseX, int mouseY)
	{
		String amount = Theme.bold(Theme.count(Shards.displayedBalance()));
		int trend = Shards.balanceTrend();
		int color = trend > 0 ? Theme.SUCCESS : (trend < 0 ? Theme.SHARD_SPEND : Theme.SHARD_TEXT);
		int right = this.closeButton.x - Theme.SPACE_M;
		int textY = this.closeButton.y + (Theme.ICON_M - font.lineHeight) / 2 + 1;
		int textX = right - font.width(amount);
		Theme.text(ctx, font, amount, textX, textY, color);
		int glyphX = textX - Theme.SPACE_2XS - Theme.ICON_S;
		Glyphs.shard(ctx, glyphX, textY);
		int buttonWidth = Buttons.width(font, BUY_LABEL);
		this.buyShards.set(glyphX - Theme.SPACE_M - buttonWidth, this.closeButton.y, buttonWidth, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.buyShards, BUY_LABEL, Buttons.Kind.SECONDARY, true, mouseX, mouseY);
	}

	private void renderFloaters(GuiGraphicsExtractor ctx, Font font)
	{
		long now = Util.getMillis();

		for (int i = this.floaters.size() - 1; i >= 0; i--)
		{
			Floater floater = this.floaters.get(i);
			float t = (now - floater.bornAt()) / (float) Theme.MOTION_REWARD_MS;

			if (t >= 1.0F)
			{
				this.floaters.remove(i);
				continue;
			}

			int y = floater.y() - Math.round(FLOAT_RISE * Theme.easeOut(t));
			Theme.text(ctx, font, floater.text(), floater.x() - font.width(floater.text()) / 2, y,
					Theme.withAlpha(floater.color(), 1.0F - t));
		}
	}

	private record Floater(String text, int x, int y, long bornAt, int color)
	{
	}
}

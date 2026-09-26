package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.QuestExtras;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Glyphs;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Rows;
import com.fudgedy.schematicindex.gui.widget.States;
import com.fudgedy.schematicindex.gui.widget.Tiles;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The Quests tab: daily and weekly groups of rows, each with its reward and at most one action
final class ShardQuests
{
	private static final int BAR_WIDTH = 48;
	private static final int SKELETON_ROWS = 3;
	private static final String DOT = " · ";
	private static final ItemStack SHARD = new ItemStack(Items.AMETHYST_SHARD);

	private final ShardPanel panel;
	private final Rect retry = new Rect();
	private final Rect emptyAction = new Rect();
	// Parallel lists: each actionable quest button and the "start:<id>" / "claim:<id>" it triggers
	private final List<Rect> buttons = new ArrayList<>();
	private final List<String> actions = new ArrayList<>();
	private final List<Rect> buttonPool = new ArrayList<>();
	// Parallel lists: each quest row that leads somewhere and the rail page it opens
	private final List<Rect> rows = new ArrayList<>();
	private final List<IndexScreen.Page> pages = new ArrayList<>();
	private final List<Rect> rowPool = new ArrayList<>();
	private int rowCount;
	// A quest claimed while the panel is open stays on screen to show its payout; reopening retires it
	private final Set<String> justClaimed = new HashSet<>();
	private final Map<String, ItemStack> icons = new HashMap<>();
	private List<Shards.Quest> pendingCache = List.of();
	private List<Shards.Quest> pendingSource;

	ShardQuests(ShardPanel panel)
	{
		this.panel = panel;
	}

	void resetSession()
	{
		this.justClaimed.clear();
		this.pendingSource = null;
	}

	void clearRects()
	{
		this.retry.set(0, 0, 0, 0);
		this.emptyAction.set(0, 0, 0, 0);
		this.buttons.clear();
		this.actions.clear();
		this.rows.clear();
		this.pages.clear();
	}

	static int claimableCount()
	{
		int count = 0;

		for (Shards.Quest quest : Shards.quests())
		{
			if (quest.state().equals("claimable"))
			{
				count++;
			}
		}

		return count;
	}

	int render(GuiGraphics ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		this.clearRects();
		this.rowCount = 0;

		if (Shards.days().isEmpty())
		{
			if (Shards.loadFailed() && !Shards.isLoading())
			{
				return States.error(ctx, font, x, y, width, "your streak", Errors.SHARD_LOAD, this.retry, mouseX, mouseY);
			}

			States.skeleton(ctx, x, y, Theme.SPACE_2XL + Theme.SPACE_S, font.lineHeight);
			return States.skeletonRows(ctx, x, y + font.lineHeight + Theme.SPACE_S, width, Theme.H_ROW_2, Theme.SPACE_XS, SKELETON_ROWS);
		}

		List<Shards.Quest> pending = this.pendingQuests();
		long weekLeft = Shards.weekResetsAt() - System.currentTimeMillis();

		if (pending.isEmpty())
		{
			String body = Shards.weekResetsAt() > 0L ? "New weekly quests in " + Theme.duration(weekLeft) : "Check back tomorrow";
			return States.empty(ctx, font, x, y, width, SHARD, "All caught up", body, this.emptyAction, null, mouseX, mouseY);
		}

		int bottom = this.renderGroup(ctx, font, pending, false, "Daily", null, x, y, width, mouseX, mouseY);
		int weeklyTop = bottom == y ? y : bottom + Theme.SPACE_L;
		String meta = Shards.weekResetsAt() > 0L ? "Resets in " + Theme.duration(weekLeft) : null;
		int weeklyBottom = this.renderGroup(ctx, font, pending, true, "Weekly", meta, x, weeklyTop, width, mouseX, mouseY);
		return weeklyBottom == weeklyTop ? bottom : weeklyBottom;
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.retry.contains(mouseX, mouseY))
		{
			Theme.click();
			Shards.refresh();
			return true;
		}

		for (int i = 0; i < this.buttons.size(); i++)
		{
			Rect button = this.buttons.get(i);

			if (!button.contains(mouseX, mouseY))
			{
				continue;
			}

			Theme.buttonPress(button);
			String action = this.actions.get(i);
			String id = action.substring(action.indexOf(':') + 1);

			if (action.startsWith("start:"))
			{
				Theme.beaconActivate();
				Shards.startQuest(id);
				return true;
			}

			Theme.raidVictory();
			this.justClaimed.add(id);
			this.pendingSource = null;
			int x = button.x + button.width / 2;
			int y = button.y;
			Shards.claimQuest(id, earned -> this.panel.spawnFloater(x, y, earned));
			return true;
		}

		for (int i = 0; i < this.rows.size(); i++)
		{
			if (this.rows.get(i).contains(mouseX, mouseY))
			{
				Theme.click();
				this.panel.close();
				this.panel.screen().switchPage(this.pages.get(i));
				return true;
			}
		}

		return false;
	}

	// Returns y unchanged when the group has no quests, so its header is left out too
	private int renderGroup(GuiGraphics ctx, Font font, List<Shards.Quest> pending, boolean weekly, String title,
			String meta, int x, int y, int width, int mouseX, int mouseY)
	{
		List<Shards.Quest> group = new ArrayList<>();

		for (int rank = 0; rank < 4; rank++)
		{
			for (Shards.Quest quest : pending)
			{
				if (quest.weekly() == weekly && this.orderOf(quest) == rank)
				{
					group.add(quest);
				}
			}
		}

		if (group.isEmpty())
		{
			return y;
		}

		int rowY = Rows.header(ctx, font, title, meta, x, y, width);

		for (int i = 0; i < group.size(); i++)
		{
			this.renderRow(ctx, font, group.get(i), x, rowY, width, mouseX, mouseY);
			rowY += Theme.H_ROW_2 + (i == group.size() - 1 ? 0 : Theme.SPACE_XS);
		}

		return rowY;
	}

	private void renderRow(GuiGraphics ctx, Font font, Shards.Quest quest, int x, int y, int width, int mouseX,
			int mouseY)
	{
		boolean claimable = quest.state().equals("claimable");
		boolean claimed = quest.state().equals("claimed");
		boolean startable = quest.state().equals("available") && !quest.weekly();
		IndexScreen.Page page = pageFor(quest.page());

		Rect row = Rect.pooled(this.rowPool, this.rowCount++);
		row.set(x, y, width, Theme.H_ROW_2);
		boolean hovered = Rows.row(ctx, row, page != null, mouseX, mouseY);
		Theme.item(ctx, this.icon(quest.icon()), x + Theme.SPACE_S, y + (Theme.H_ROW_2 - Theme.ICON_M) / 2);

		int right = x + width - Theme.SPACE_S;
		int centreY = y + (Theme.H_ROW_2 - font.lineHeight) / 2 + 1;

		if (claimable || startable)
		{
			String label = claimable ? "Claim" : "Start";
			Rect button = Rect.pooled(this.buttonPool, this.buttons.size());
			int buttonWidth = Buttons.width(font, label);
			button.set(right - buttonWidth, y + (Theme.H_ROW_2 - Theme.H_CONTROL) / 2, buttonWidth, Theme.H_CONTROL);
			Buttons.button(ctx, font, button, label, claimable ? Buttons.Kind.PRIMARY : Buttons.Kind.SECONDARY, true, mouseX, mouseY);
			this.buttons.add(button);
			this.actions.add((claimable ? "claim:" : "start:") + quest.id());
			right = button.x - Theme.SPACE_M;
		}
		else if (claimed)
		{
			String done = Theme.bold("Claimed");
			int doneX = right - font.width(done);
			Theme.text(ctx, font, done, doneX, centreY, Theme.SUCCESS);
			Glyphs.draw(ctx, Glyphs.CHECK, doneX - Theme.SPACE_XS - Theme.ICON_S, centreY, Theme.SUCCESS);
			right = doneX - Theme.SPACE_XS - Theme.ICON_S - Theme.SPACE_M;
		}
		else if (page != null)
		{
			right = Rows.chevron(ctx, font, row, hovered) - Theme.SPACE_M;
		}

		if (page != null)
		{
			this.rows.add(row);
			this.pages.add(page);
		}

		String reward = Theme.bold(Theme.count(quest.reward()));
		int rewardX = right - font.width(reward);
		Theme.text(ctx, font, reward, rewardX, centreY, Theme.SHARD_TEXT);
		int glyphX = rewardX - Theme.SPACE_2XS - Theme.ICON_S;
		Glyphs.shard(ctx, glyphX, centreY);

		this.renderText(ctx, font, quest, x + Theme.SPACE_S + Theme.ICON_M + Theme.SPACE_S, y, glyphX - Theme.SPACE_M, claimed, row, mouseX, mouseY);
	}

	private void renderText(GuiGraphics ctx, Font font, Shards.Quest quest, int textX, int y, int right,
			boolean claimed, Rect row, int mouseX, int mouseY)
	{
		int room = right - textX;
		Component suffix = QuestExtras.suffix(quest.id());
		int suffixWidth = suffix == null ? 0 : font.width(suffix) + Theme.SPACE_XS + Theme.SPACE_2XS;
		String title = Theme.clipBold(font, quest.title(), room - suffixWidth);
		int titleY = y + Theme.SPACE_S;
		Theme.text(ctx, font, Theme.bold(title), textX, titleY, Theme.TEXT);

		if (suffix != null && room - suffixWidth > 0)
		{
			Theme.text(ctx, font, suffix, textX + font.width(Theme.bold(title)) + Theme.SPACE_XS + Theme.SPACE_2XS, titleY, Theme.TEXT);
		}

		int lineY = titleY + font.lineHeight + Theme.SPACE_XS;
		float fraction = quest.target() == 0 ? 1.0F : (float) quest.progress() / quest.target();
		Tiles.progress(ctx, textX, lineY + Theme.SPACE_2XS, BAR_WIDTH, Theme.SPACE_XS, fraction, claimed || fraction >= 1.0F);
		String progress = quest.progress() + "/" + quest.target() + DOT;
		int progressX = textX + BAR_WIDTH + Theme.SPACE_XS + Theme.SPACE_2XS;
		Theme.text(ctx, font, progress, progressX, lineY, Theme.TEXT_ASH);
		int descriptionX = progressX + font.width(progress);
		String description = Theme.clip(font, quest.description(), right - descriptionX);
		Theme.text(ctx, font, description, descriptionX, lineY, Theme.TEXT_MUTE);

		boolean clipped = !title.equals(quest.title()) || !description.equals(quest.description());

		if (clipped && row.contains(mouseX, mouseY))
		{
			Tooltip.show(quest.title() + DOT + quest.description());
		}
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

	// Claimable first so the payout is never below the fold, then what is under way, then new, then done
	private int orderOf(Shards.Quest quest)
	{
		return switch (quest.state())
		{
			case "claimable" -> 0;
			case "available" -> 2;
			case "claimed" -> 3;
			default -> 1;
		};
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

	// Paper for an item id this build does not know
	private ItemStack icon(String itemId)
	{
		String id = itemId == null ? "" : itemId;
		ItemStack cached = this.icons.get(id);

		if (cached != null)
		{
			return cached;
		}

		Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
		Item item = key == null ? Items.PAPER : BuiltInRegistries.ITEM.getValue(key);
		ItemStack stack = new ItemStack(item == Items.AIR ? Items.PAPER : item);
		this.icons.put(id, stack);
		return stack;
	}
}

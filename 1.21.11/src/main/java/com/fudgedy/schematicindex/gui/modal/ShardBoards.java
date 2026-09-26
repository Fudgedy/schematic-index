package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Boards;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.page.SettingsPage;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Glyphs;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.States;
import com.fudgedy.schematicindex.gui.widget.Tabs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

// The Leaderboards tab: a board switcher, the top ten and the player's own standing, which is always shown
final class ShardBoards
{
	private static final String[] BOARDS = {"shards", "streak", "best_streak"};
	private static final String[] LABELS = {"Most Shards", "Streaks", "Highest Streaks"};
	private static final int[] PODIUM = {Theme.PODIUM_GOLD, Theme.PODIUM_SILVER, Theme.PODIUM_BRONZE};
	private static final int ROWS = 10;
	private static final int ROW_PITCH = 15;
	private static final int RANK_WIDTH = Theme.SPACE_XL;
	private static final int OWN_HEIGHT = 20;

	private final ShardPanel panel;
	private final List<Rect> segments = new ArrayList<>();
	private final Rect ownAction = new Rect();
	private final Rect retry = new Rect();
	private final Rect empty = new Rect();
	private final Rect hover = new Rect();
	private int selected;
	// Tallest the tab has drawn since the panel opened; a board still loading keeps that room so the
	// body never shrinks under the player on a board switch
	private int reserved;

	ShardBoards(ShardPanel panel)
	{
		this.panel = panel;
	}

	void onOpen()
	{
		if (!Boards.enabled())
		{
			return;
		}

		Usage.once("boards_open");
		this.reserved = 0;

		for (String board : BOARDS)
		{
			Boards.refreshIfStale(board);
		}
	}

	void clearRects()
	{
		Tabs.clear(this.segments);
		this.ownAction.set(0, 0, 0, 0);
		this.retry.set(0, 0, 0, 0);
	}

	int render(GuiGraphics ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		int top = y;
		y = Tabs.segmented(ctx, font, this.segments, LABELS, this.selected, x, y, width, mouseX, mouseY) + Theme.SPACE_M;
		String name = BOARDS[this.selected];
		Boards.refreshIfStale(name);
		Boards.Board board = Boards.board(name);
		this.ownAction.set(0, 0, 0, 0);

		if (board == null)
		{
			if (Boards.failed(name))
			{
				return this.hold(top, States.error(ctx, font, x, y, width, "leaderboards", Errors.BOARDS_LOAD, this.retry, mouseX, mouseY));
			}

			this.retry.set(0, 0, 0, 0);
			int rows = States.skeletonRows(ctx, x, y, width, ROW_PITCH - Theme.SPACE_XS, Theme.SPACE_XS, ROWS);
			return this.hold(top, rows + Theme.SPACE_S + OWN_HEIGHT);
		}

		this.retry.set(0, 0, 0, 0);

		if (board.top().isEmpty())
		{
			States.empty(ctx, font, x, y, width, null, "Nobody here yet", "Claim a daily reward to be first", this.empty, null,
					mouseX, mouseY);
		}

		for (int i = 0; i < board.top().size() && i < ROWS; i++)
		{
			this.renderRow(ctx, font, board.top().get(i), x, y + i * ROW_PITCH, width, mouseX, mouseY);
		}

		int ownY = y + ROWS * ROW_PITCH + Theme.SPACE_S;
		this.renderOwn(ctx, font, board.mine(), x, ownY, width, mouseX, mouseY);
		return this.hold(top, ownY + OWN_HEIGHT);
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		int hit = Tabs.hit(this.segments, mouseX, mouseY);

		if (hit >= 0)
		{
			if (hit != this.selected)
			{
				Theme.click(1.1F);
				this.selected = hit;
				Boards.refreshIfStale(BOARDS[hit]);
			}

			return true;
		}

		if (this.retry.contains(mouseX, mouseY))
		{
			Theme.click();
			Boards.refreshIfStale(BOARDS[this.selected]);
			return true;
		}

		if (!this.ownAction.contains(mouseX, mouseY))
		{
			return false;
		}

		Theme.click();

		if (!McAuth.verified())
		{
			this.panel.screen().startVerify(null);
			return true;
		}

		this.panel.close();
		SettingsPage.showProfile();
		this.panel.screen().switchPage(IndexScreen.Page.SETTINGS);
		return true;
	}

	private void renderRow(GuiGraphics ctx, Font font, Boards.Row row, int x, int y, int width, int mouseX, int mouseY)
	{
		this.hover.set(x, y, width, ROW_PITCH);

		if (this.hover.contains(mouseX, mouseY))
		{
			Theme.roundedRect(ctx, x, y, width, ROW_PITCH, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
		}

		int textY = y + (ROW_PITCH - font.lineHeight) / 2 + 1;
		boolean podium = row.rank() >= 1 && row.rank() <= PODIUM.length;
		String rank = Integer.toString(row.rank());
		Theme.text(ctx, font, podium ? Theme.bold(rank) : rank, x + Theme.SPACE_S, textY, podium ? PODIUM[row.rank() - 1] : Theme.TEXT_ASH);

		MutableComponent line = Component.empty();

		if (row.tag() != null)
		{
			line.append(Cosmetics.tagOf(row.tag())).append(Component.literal(" "));
		}

		line.append(row.stops().length == 0 ? Component.literal(row.ign())
				: Cosmetics.preview(row.ign(), row.stops(), Style.EMPTY, row.effect()));
		Theme.text(ctx, font, line, x + Theme.SPACE_S + RANK_WIDTH + Theme.SPACE_XS, textY, Theme.TEXT);
		this.renderValue(ctx, font, row.value(), x + width - Theme.SPACE_S, textY);
	}

	// The own row covers every standing the server can report, so the player always learns where they are
	private void renderOwn(GuiGraphics ctx, Font font, Boards.Mine mine, int x, int y, int width, int mouseX, int mouseY)
	{
		Theme.roundedRect(ctx, x, y, width, OWN_HEIGHT, Theme.RADIUS_CARD, Theme.ACCENT_TINT);
		int textY = y + (OWN_HEIGHT - font.lineHeight) / 2 + 1;
		int textX = x + Theme.SPACE_S;

		if (mine != null && mine.rank() != null)
		{
			String rank = Theme.bold("Your rank: #" + mine.rank());
			Theme.text(ctx, font, rank, textX, textY, Theme.ACCENT_BRIGHT);
			this.renderValue(ctx, font, mine.value(), x + width - Theme.SPACE_S, textY);
			return;
		}

		String message;
		String action = null;

		if (mine == null && !McAuth.verified())
		{
			message = "Verify your account to get ranked";
			action = "Verify";
		}
		else if (mine != null && mine.optedOut())
		{
			message = "You're hidden from leaderboards";
			action = "Settings";
		}
		else
		{
			message = "Not ranked yet. Claim a daily reward to join.";
		}

		int right = x + width - Theme.SPACE_XS;

		if (action != null)
		{
			int buttonWidth = Buttons.width(font, action);
			this.ownAction.set(right - buttonWidth, y + (OWN_HEIGHT - Theme.H_CONTROL) / 2, buttonWidth, Theme.H_CONTROL);
			Buttons.button(ctx, font, this.ownAction, action, Buttons.Kind.GHOST, true, mouseX, mouseY);
			right = this.ownAction.x - Theme.SPACE_S;
		}

		Theme.text(ctx, font, Theme.clip(font, message, right - textX), textX, textY, Theme.TEXT_MUTE);
	}

	private void renderValue(GuiGraphics ctx, Font font, int value, int right, int textY)
	{
		if (this.selected >= 1)
		{
			String days = Theme.count(value) + (value == 1 ? " day" : " days");
			Theme.text(ctx, font, days, right - font.width(days), textY, Theme.TEXT_MUTE);
			return;
		}

		String amount = Theme.count(value);
		int glyphX = right - Theme.ICON_S;
		Glyphs.shard(ctx, glyphX, textY);
		Theme.text(ctx, font, amount, glyphX - Theme.SPACE_2XS - font.width(amount), textY, Theme.TEXT_MUTE);
	}

	private int hold(int top, int bottom)
	{
		this.reserved = Math.max(this.reserved, bottom - top);
		return top + this.reserved;
	}
}

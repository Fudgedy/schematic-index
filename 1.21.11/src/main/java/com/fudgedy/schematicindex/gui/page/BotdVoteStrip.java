package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.FeaturedVote;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

// "Vote for tomorrow's Build of the Day" under the banner: the three shortlisted builds, each opening on click
class BotdVoteStrip
{
	static final int HEIGHT = 68;
	private static final int PAD = 8;
	private static final int TILE_GAP = 6;
	private static final int TILE_HEIGHT = 40;
	private static final int BUTTON_HEIGHT = 13;
	// Below this a tile keeps its text and drops the thumbnail
	private static final int THUMB_MIN_TILE = 150;
	private static final String PICKED = "Your vote ✔";

	private final IndexScreen screen;
	private final List<Rect> tiles = new ArrayList<>();
	private final List<Rect> buttons = new ArrayList<>();
	private final List<String> postIds = new ArrayList<>();

	BotdVoteStrip(IndexScreen screen)
	{
		this.screen = screen;
	}

	boolean shown()
	{
		FeaturedVote.refreshIfStale();
		FeaturedVote.Ballot ballot = FeaturedVote.ballot();
		return ballot != null && ballot.open();
	}

	void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY)
	{
		this.clear();
		FeaturedVote.Ballot ballot = FeaturedVote.ballot();
		IndexScreen screen = this.screen;

		if (ballot == null || y + HEIGHT <= screen.gridTop || y >= screen.gridBottom)
		{
			return;
		}

		if (mouseY < screen.gridTop)
		{
			mouseX = -1;
			mouseY = -1;
		}

		Font font = screen.font();
		ctx.enableScissor(x, screen.gridTop, x + width, screen.gridBottom);
		Theme.roundedRect(ctx, x, y, width, HEIGHT, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, HEIGHT, Theme.RADIUS_CARD, Theme.HAIRLINE);
		Theme.goldGradientText(ctx, font, "Vote for tomorrow's Build of the Day", x + PAD + 2, y + 7, true);
		String left = closesText(ballot.closesAt());
		Theme.text(ctx, font, left, x + width - PAD - 2 - font.width(left), y + 7, Theme.TEXT_ASH);

		List<FeaturedVote.Candidate> candidates = ballot.candidates();
		int count = Math.min(3, candidates.size());
		int tileWidth = (width - PAD * 2 - TILE_GAP * 2) / 3;

		for (int i = 0; i < count; i++)
		{
			int tileX = x + PAD + i * (tileWidth + TILE_GAP);
			this.renderTile(ctx, font, ballot, candidates.get(i), tileX, y + 20, tileWidth, mouseX, mouseY);
		}

		ctx.disableScissor();
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (mouseY < this.screen.gridTop)
		{
			return false;
		}

		for (int i = 0; i < this.buttons.size() && i < this.postIds.size(); i++)
		{
			if (this.buttons.get(i).contains(mouseX, mouseY))
			{
				Theme.click(1.1F);
				FeaturedVote.vote(this.postIds.get(i));
				return true;
			}
		}

		for (int i = 0; i < this.tiles.size() && i < this.postIds.size(); i++)
		{
			if (this.tiles.get(i).contains(mouseX, mouseY))
			{
				// openPostById's show() plays the click; a second one here would double it
				this.screen.openPostById(this.postIds.get(i));
				return true;
			}
		}

		return false;
	}

	private void renderTile(GuiGraphics ctx, Font font, FeaturedVote.Ballot ballot, FeaturedVote.Candidate candidate,
			int x, int y, int width, int mouseX, int mouseY)
	{
		int index = this.postIds.size();
		this.postIds.add(candidate.postId());
		Rect tile = Rect.pooled(this.tiles, index);
		tile.set(x, y, width, TILE_HEIGHT);
		boolean mine = candidate.postId().equals(ballot.myVote());
		boolean hovered = tile.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, x, y, width, TILE_HEIGHT, Theme.RADIUS_CARD, hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE);
		Theme.roundedOutline(ctx, x, y, width, TILE_HEIGHT, Theme.RADIUS_CARD, mine ? Theme.GOLD : Theme.HAIRLINE);

		int textX = x + 6;

		if (width >= THUMB_MIN_TILE)
		{
			int thumbHeight = TILE_HEIGHT - 8;
			int thumbWidth = Math.round(thumbHeight * 16.0F / 9.0F);
			Identifier thumbnail = candidate.thumbnailUrl() == null || candidate.thumbnailUrl().isBlank() ? null
					: ImageStore.thumbnail(candidate.thumbnailUrl());

			if (thumbnail != null)
			{
				Theme.image(ctx, thumbnail, x + 4, y + 4, thumbWidth, thumbHeight);
			}
			else
			{
				Theme.loadingPlaceholder(ctx, x + 4, y + 4, thumbWidth, thumbHeight);
			}

			textX = x + 4 + thumbWidth + 6;
		}

		int room = x + width - 6 - textX;
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, candidate.title(), room)), textX, y + 5, Theme.TEXT);
		String designer = candidate.designer().isBlank() ? "" : "by " + candidate.designer();
		Theme.text(ctx, font, Theme.clip(font, designer, room), textX, y + 15, Theme.TEXT_MUTE);

		String votes = candidate.votes() < 0 ? "" : candidate.votes() + (candidate.votes() == 1 ? " vote" : " votes");
		Rect button = Rect.pooled(this.buttons, index);

		if (mine)
		{
			button.set(0, 0, 0, 0);
			int pickX = x + width - 6 - font.width(Theme.bold(PICKED));
			Theme.text(ctx, font, Theme.bold(PICKED), pickX, y + 27, Theme.GOLD);
			Theme.text(ctx, font, Theme.clip(font, votes, pickX - textX - 4), textX, y + 27, Theme.TEXT_ASH);
			return;
		}

		String label = ballot.myVote() == null ? "Vote" : "Switch";
		int buttonWidth = font.width(Theme.bold(label)) + 14;
		button.set(x + width - 5 - buttonWidth, y + TILE_HEIGHT - 5 - BUTTON_HEIGHT, buttonWidth, BUTTON_HEIGHT);

		if (FeaturedVote.isVoting())
		{
			Buttons.disabled(ctx, font, button, label);
		}
		else
		{
			Buttons.pill(ctx, font, button, label, mouseX, mouseY, ballot.myVote() == null);
		}

		Theme.text(ctx, font, Theme.clip(font, votes, button.x - textX - 4), textX, y + 27, Theme.TEXT_ASH);
	}

	private static String closesText(long closesAt)
	{
		long remaining = Math.max(0L, closesAt - System.currentTimeMillis());
		long hours = remaining / 3_600_000L;
		long minutes = remaining % 3_600_000L / 60_000L;

		if (hours == 0 && minutes == 0)
		{
			return "Closes in <1m";
		}

		return "Closes in " + (hours > 0 ? hours + "h " : "") + minutes + "m";
	}

	private void clear()
	{
		this.postIds.clear();

		for (Rect rect : this.tiles)
		{
			rect.set(0, 0, 0, 0);
		}

		for (Rect rect : this.buttons)
		{
			rect.set(0, 0, 0, 0);
		}
	}
}

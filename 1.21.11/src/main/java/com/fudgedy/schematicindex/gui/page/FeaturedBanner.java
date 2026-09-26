package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Featured;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.Crown;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

// Build of the Day across the top of an unfiltered Browse feed; it scrolls away with the first row of cards
public class FeaturedBanner
{
	private static final int HEIGHT = 44;

	private final BrowsePage browse;
	private final IndexScreen screen;
	private final Rect bounds = new Rect();
	private final BotdVoteStrip vote;

	FeaturedBanner(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
		this.vote = new BotdVoteStrip(screen);
	}

	// The badge CardGrid draws on today's card; a dark pill so the crown reads over any thumbnail
	public static void crown(GuiGraphics ctx, int x, int y)
	{
		int width = Crown.WIDTH + Theme.SPACE_2XS * 2;
		int height = Crown.HEIGHT + Theme.SPACE_2XS * 2;
		Theme.roundedRect(ctx, x - 1, y - 1, width + 2, height + 2, Theme.RADIUS_PILL, 0xFF000000);
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.ON_GOLD);
		Crown.badge(ctx, x + Theme.SPACE_2XS, y + Theme.SPACE_2XS);
	}

	// Space the grid leaves above its first row; zero whenever the banner is not shown
	int offset()
	{
		return this.bannerHeight() + (this.voteShown() ? BotdVoteStrip.HEIGHT + IndexScreen.GUTTER : 0);
	}

	void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (this.voteShown())
		{
			int voteY = this.screen.gridTop - Math.round(this.screen.scroll) + this.bannerHeight();
			this.vote.render(ctx, this.screen.contentX, voteY, this.screen.contentWidth, mouseX, mouseY);
		}

		Featured.Today today = Featured.today();

		if (today == null || !this.shown())
		{
			this.bounds.set(0, 0, 0, 0);
			return;
		}

		IndexScreen screen = this.screen;
		Font font = screen.font();
		int x = screen.contentX;
		int y = screen.gridTop - Math.round(screen.scroll);
		int width = screen.contentWidth;
		this.bounds.set(x, y, width, HEIGHT);

		if (y + HEIGHT <= screen.gridTop)
		{
			return;
		}

		boolean hovered = this.bounds.contains(mouseX, mouseY) && mouseY >= screen.gridTop;
		ctx.enableScissor(x, screen.gridTop, x + width, screen.gridBottom);
		Theme.roundedRect(ctx, x, y, width, HEIGHT, Theme.RADIUS_CARD, hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, HEIGHT, Theme.RADIUS_CARD, hovered ? Theme.GOLD_BRIGHT : Theme.GOLD);
		Crown.draw(ctx, x + Theme.SPACE_S, y + (HEIGHT - Crown.HEIGHT) / 2);

		SchematicEntry entry = today.entry();
		int thumbWidth = Math.round((HEIGHT - 8) * 16.0F / 9.0F);
		int thumbX = x + width - 4 - thumbWidth;
		Identifier thumbnail = screen.thumbnailTexture(entry);

		if (thumbnail != null)
		{
			Theme.image(ctx, thumbnail, thumbX, y + 4, thumbWidth, HEIGHT - 8);
		}
		else
		{
			Theme.loadingPlaceholder(ctx, thumbX, y + 4, thumbWidth, HEIGHT - 8);
		}

		int textX = x + Theme.SPACE_S + Crown.WIDTH + Theme.SPACE_S;
		int room = thumbX - textX - 8;
		String dayLabel = SchematicEntry.formatDay(today.day());
		String heading = dayLabel.isEmpty() ? "Build of the Day" : "Build of the Day · " + dayLabel;
		Theme.goldGradientText(ctx, font, heading, textX, y + 7, true);
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, entry.title(), room)), textX, y + 18, Theme.TEXT);
		String designer = "Designed by: " + entry.credit();
		Theme.text(ctx, font, Theme.clip(font, designer, room), textX, y + 29, Theme.TEXT_MUTE);
		ctx.disableScissor();
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.voteShown() && this.vote.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		Featured.Today today = Featured.today();

		if (today == null || !this.shown() || mouseY < this.screen.gridTop || !this.bounds.contains(mouseX, mouseY))
		{
			return false;
		}

		// openDetail's show() plays the click; a second one here would double it
		Usage.once("botd_open");
		this.screen.openDetail(today.entry());
		return true;
	}

	private int bannerHeight()
	{
		return this.shown() ? HEIGHT + IndexScreen.GUTTER : 0;
	}

	private boolean shown()
	{
		return Featured.today() != null && this.defaultView();
	}

	private boolean voteShown()
	{
		return this.defaultView() && this.vote.shown();
	}

	// Default view only: page one of Browse with no search, filter, profile or history in play
	private boolean defaultView()
	{
		BrowsePage browse = this.browse;
		return Catalogue.state() == Catalogue.State.READY
				&& this.screen.page == IndexScreen.Page.BROWSE && browse.profile.poster == null
				&& browse.activeTags.isEmpty() && browse.query.isBlank() && browse.downloadFilter == 0
				&& !browse.followingFilter && !browse.historyView;
	}
}

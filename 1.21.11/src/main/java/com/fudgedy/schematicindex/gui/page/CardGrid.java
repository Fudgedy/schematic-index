package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.Featured;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.CardMenu;
import com.fudgedy.schematicindex.gui.Crown;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CardGrid
{
	private final BrowsePage browse;
	private final IndexScreen screen;
	public final Rect retryButton = new Rect();
	private final Map<String, CardText> cardTextCache = new HashMap<>();
	private final Rect heartRectPool = new Rect();
	private final Rect menuGlyphRectPool = new Rect();

	CardGrid(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
	}

	public void clearTextCache()
	{
		this.cardTextCache.clear();
	}

	public void renderCard(GuiGraphics ctx, SchematicEntry entry, int x, int y, int mouseX, int mouseY)
	{
		IndexScreen screen = this.screen;
		Font font = screen.font();
		boolean hovered = Theme.inside(mouseX, mouseY, x, y, screen.cardWidth, screen.cardHeight)
				&& mouseY >= screen.gridTop && mouseY < screen.gridBottom;

		Theme.roundedRect(ctx, x, y, screen.cardWidth, screen.cardHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);

		int imageHeight = IndexScreen.imageHeight(screen.cardWidth);
		Identifier texture = screen.thumbnailTexture(entry);

		if (texture != null)
		{
			Theme.image(ctx, texture, x, y, screen.cardWidth, imageHeight);
		}
		else
		{
			Theme.loadingPlaceholder(ctx, x, y, screen.cardWidth, imageHeight);
		}

		CardText text = this.cardText(entry);
		String tag = text.tag();
		int tagWidth = font.width(tag) + 8;
		Theme.roundedRect(ctx, x + 4, y + imageHeight - 15, tagWidth, 11, Theme.RADIUS_PILL, 0xCC0F1114);
		Theme.text(ctx, font, tag, x + 8, y + imageHeight - 13, Theme.TEXT);

		Rect heart = this.heartRect(x, y, imageHeight);
		Theme.roundedRect(ctx, heart.x - 2, heart.y - 2, IndexScreen.HEART_SIZE + 4, IndexScreen.HEART_SIZE + 4,
				Theme.RADIUS_PILL, 0xCC0F1114);
		Theme.heartPopped(ctx, heart.x, heart.y, IndexScreen.isLikedBy(entry), IndexScreen.popAge(entry));

		int rightBadgeY = y + 4;
		// The corner cell stays free for the menu glyph, so the badges never jump when it appears
		int badgeRight = x + screen.cardWidth - 4 - CardMenu.GLYPH_CELL - 3;

		if (IndexScreen.isSaved(entry))
		{
			int badge = font.width("Saved") + 8;
			int badgeX = badgeRight - badge;
			Theme.roundedRect(ctx, badgeX - 1, rightBadgeY - 1, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, badgeX, rightBadgeY, badge, 11, Theme.RADIUS_PILL, Theme.ACCENT);
			Theme.text(ctx, font, "Saved", badgeX + 4, rightBadgeY + 2, Theme.ON_ACCENT);
			rightBadgeY += 15;
		}

		if (this.browse.viewedFrontRow.contains(entry.id()))
		{
			int badge = font.width("Viewed") + 8;
			int badgeX = badgeRight - badge;
			Theme.roundedRect(ctx, badgeX - 1, rightBadgeY - 1, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, badgeX, rightBadgeY, badge, 11, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
			Theme.text(ctx, font, "Viewed", badgeX + 4, rightBadgeY + 2, Theme.TEXT);
			rightBadgeY += 15;
		}

		// isUpdateAvailable is cached per entry id so this does not re-hash the file every frame
		if (screen.isUpdateAvailable(entry))
		{
			int badge = font.width("Update") + 8;
			int badgeX = badgeRight - badge;
			Theme.roundedRect(ctx, badgeX - 1, rightBadgeY - 1, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, badgeX, rightBadgeY, badge, 11, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
			Theme.text(ctx, font, "Update", badgeX + 4, rightBadgeY + 2, Theme.ON_ACCENT);
		}

		boolean isNew = BrowsePage.newSinceCutoff > 0L && entry.postedAt() > BrowsePage.newSinceCutoff
				&& !entry.id().equals("preview");

		if (Featured.isFeatured(entry.id()))
		{
			int crownX = x + 4;
			int crownY = y + (isNew ? 19 : 4);
			FeaturedBanner.crown(ctx, crownX, crownY);
			int crownW = Crown.WIDTH + Theme.SPACE_2XS * 2 + 2;
			int crownH = Crown.HEIGHT + Theme.SPACE_2XS * 2 + 2;

			if (Theme.inside(mouseX, mouseY, crownX - 1, crownY - 1, crownW, crownH))
			{
				Tooltip.show("Build of the Day");
			}
		}

		if (isNew)
		{
			int badge = font.width(Theme.bold("New")) + 8;
			Theme.roundedRect(ctx, x + 3, y + 3, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, x + 4, y + 4, badge, 11, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
			Theme.text(ctx, font, Theme.bold("New"), x + 8, y + 6, Theme.ON_ACCENT);
		}

		int textX = x + 5;
		Theme.text(ctx, font, text.name(), textX, y + imageHeight + 6, Theme.TEXT);

		int metaY = y + imageHeight + 6 + font.lineHeight + 2;
		int countX = x + screen.cardWidth - 5 - text.downloadsWidth();
		Theme.text(ctx, font, text.downloads(), countX, metaY, Theme.TEXT_MUTE);
		Theme.downloadGlyph(ctx, countX - Theme.DOWNLOAD_GLYPH_WIDTH - 3, metaY + 2, Theme.TEXT_MUTE);
		Theme.text(ctx, font, text.credit(), textX, metaY, Theme.TEXT_MUTE);

		if (hovered || screen.cardMenu.isFor(entry))
		{
			Theme.roundedOutline(ctx, x, y, screen.cardWidth, screen.cardHeight, Theme.RADIUS_CARD, Theme.ACCENT_BRIGHT);
			int glyphX = x + screen.cardWidth - 4 - CardMenu.GLYPH_CELL;
			CardMenu.glyph(ctx, glyphX, y + 4, Theme.inside(mouseX, mouseY, glyphX, y + 4, CardMenu.GLYPH_CELL, 11));
		}
	}

	void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		IndexScreen screen = this.screen;
		Font font = screen.font();
		Catalogue.State state = Catalogue.state();

		if (state != Catalogue.State.READY)
		{
			this.renderSkeleton(ctx, state == Catalogue.State.OFFLINE, mouseX, mouseY);
			return;
		}

		this.retryButton.set(0, 0, 0, 0);

		if (this.browse.visible.isEmpty())
		{
			String message = screen.page == IndexScreen.Page.SAVED && this.browse.downloadFilter == 0
					? "Nothing saved yet - open a post and Save for later"
					: "Nothing matches that filter";
			int centreX = screen.contentX + screen.contentWidth / 2;
			Theme.text(ctx, font, message,
					centreX - font.width(message) / 2, screen.gridTop + 40, Theme.TEXT_ASH);

			if (this.browse.chips.filtersActive())
			{
				String active = Theme.clip(font, "Active: " + this.browse.chips.activeFilterSummary(),
						screen.contentWidth - 16);
				Theme.text(ctx, font, active, centreX - font.width(active) / 2,
						screen.gridTop + 40 + font.lineHeight + 6, Theme.TEXT_MUTE);
				String hint = "Use \"Clear filters\" above to reset.";
				Theme.text(ctx, font, hint, centreX - font.width(hint) / 2,
						screen.gridTop + 40 + (font.lineHeight + 6) * 2, Theme.TEXT_MUTE);
			}

			return;
		}

		this.browse.maybeLoadMore();
		int shown = this.browse.shownCap();

		ctx.enableScissor(screen.contentX, screen.gridTop, screen.contentX + screen.contentWidth, screen.gridBottom);

		int rowHeight = screen.cardHeight + IndexScreen.GUTTER;
		int top = screen.gridTop + this.browse.featured.offset();
		int firstRow = Math.max(0, (int) ((screen.scroll - (top - screen.gridTop)) / rowHeight));
		int lastRow = Math.min((shown - 1) / screen.columns,
				(int) ((screen.scroll + (screen.gridBottom - top)) / rowHeight));
		String addingTo = this.browse.collections.addingTo;

		for (int row = firstRow; row <= lastRow; row++)
		{
			for (int column = 0; column < screen.columns; column++)
			{
				int index = row * screen.columns + column;

				if (index >= shown)
				{
					break;
				}

				int x = screen.contentX + column * (screen.cardWidth + IndexScreen.GUTTER);
				int y = top + row * rowHeight - Math.round(screen.scroll);
				SchematicEntry card = this.browse.visible.get(index);
				this.renderCard(ctx, card, x, y, mouseX, mouseY);

				if (index == this.browse.focusedCard)
				{
					// Flush to the card, so the grid scissor cannot clip the top edge on the top row
					Theme.roundedOutline(ctx, x, y, screen.cardWidth, screen.cardHeight,
							Theme.RADIUS_CARD, Theme.ACCENT_BRIGHT);
				}

				if (addingTo != null && CollectionStore.contains(addingTo, card.id()))
				{
					Theme.roundedOutline(ctx, x - 1, y - 1, screen.cardWidth + 2, screen.cardHeight + 2,
							Theme.RADIUS_CARD, Theme.ACCENT);
					int badge = font.width(Theme.bold("Added")) + 10;
					Theme.roundedRect(ctx, x + screen.cardWidth - badge - 4, y + 4, badge, 12, Theme.RADIUS_PILL,
							Theme.ACCENT);
					Theme.text(ctx, font, Theme.bold("Added"), x + screen.cardWidth - badge, y + 6, Theme.ON_ACCENT);
				}
			}
		}

		if (shown < this.browse.visible.size())
		{
			int rows = (shown + screen.columns - 1) / screen.columns;
			int y = top + rows * rowHeight - Math.round(screen.scroll) + 4;
			String more = "Loading more...";
			Theme.text(ctx, font, more, screen.contentX + (screen.contentWidth - font.width(more)) / 2, y,
					Theme.TEXT_ASH);
		}

		ctx.disableScissor();
	}

	boolean mouseClicked(MouseButtonEvent event, double mouseX, double mouseY)
	{
		SchematicEntry hit = this.entryAt(mouseX, mouseY);

		if (hit == null)
		{
			return false;
		}

		IndexScreen screen = this.screen;
		String addingTo = this.browse.collections.addingTo;

		if (event.button() == 1)
		{
			screen.cardMenu.open(hit, (int) mouseX, (int) mouseY);
		}
		else if (this.menuGlyphRect(hit).contains(mouseX, mouseY))
		{
			Rect glyph = this.menuGlyphRect(hit);
			screen.cardMenu.open(hit, glyph.x + glyph.width - CardMenu.WIDTH, glyph.y + glyph.height + 2);
		}
		else if (addingTo != null)
		{
			boolean nowIn = CollectionStore.toggle(addingTo, hit.id());
			Theme.click(nowIn ? 1.2F : 0.9F);
		}
		else if (this.heartAt(hit, mouseX, mouseY))
		{
			screen.requireVerified(() -> {
				screen.toggleLike(hit);

				if (screen.page == IndexScreen.Page.SAVED)
				{
					this.browse.refilter();
				}
			});
		}
		else
		{
			screen.openDetail(hit);
		}

		return true;
	}

	private void renderSkeleton(GuiGraphics ctx, boolean offline, int mouseX, int mouseY)
	{
		IndexScreen screen = this.screen;
		Font font = screen.font();
		ctx.enableScissor(screen.contentX, screen.gridTop, screen.contentX + screen.contentWidth, screen.gridBottom);

		int rowHeight = screen.cardHeight + IndexScreen.GUTTER;
		int rows = (screen.gridBottom - screen.gridTop) / rowHeight + 1;
		int imageHeight = IndexScreen.imageHeight(screen.cardWidth);
		long now = System.currentTimeMillis();

		for (int row = 0; row < rows; row++)
		{
			for (int column = 0; column < screen.columns; column++)
			{
				int x = screen.contentX + column * (screen.cardWidth + IndexScreen.GUTTER);
				int y = screen.gridTop + row * rowHeight;

				Theme.roundedRect(ctx, x, y, screen.cardWidth, screen.cardHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
				Theme.roundedRect(ctx, x, y, screen.cardWidth, imageHeight, Theme.RADIUS_CARD, Theme.SKELETON);
				Theme.roundedRect(ctx, x + 5, y + imageHeight + 6, screen.cardWidth * 2 / 3, 5, 1, Theme.SKELETON);
				Theme.roundedRect(ctx, x + 5, y + imageHeight + 15, screen.cardWidth / 3, 5, 1, Theme.SKELETON);

				if (!offline)
				{
					int span = screen.cardWidth + 40;
					int offset = (int) ((now / 4L + (long) (row + column) * 40L) % span) - 20;
					int shimmerX = x + offset;
					ctx.enableScissor(Math.max(screen.contentX, x), y,
							Math.min(screen.contentX + screen.contentWidth, x + screen.cardWidth), y + imageHeight);
					Theme.roundedRect(ctx, shimmerX, y, 18, imageHeight, 0, Theme.SKELETON_SHINE);
					ctx.disableScissor();
				}
			}
		}

		ctx.disableScissor();

		if (!offline)
		{
			this.retryButton.set(0, 0, 0, 0);
			return;
		}

		ctx.fill(screen.contentX, screen.gridTop, screen.contentX + screen.contentWidth, screen.gridBottom, 0xCC0F1114);

		String headline = "Can't reach the index";
		String detail = "Check your connection and try again.";
		int centreX = screen.contentX + screen.contentWidth / 2;
		int centreY = screen.gridTop + (screen.gridBottom - screen.gridTop) / 2;

		Theme.textScaled(ctx, font, Theme.bold(headline),
				centreX - font.width(Theme.bold(headline)), centreY - 24, 2.0F, Theme.TEXT);
		Theme.text(ctx, font, detail, centreX - font.width(detail) / 2, centreY - 2, Theme.TEXT_MUTE);

		int retryWidth = font.width(Theme.bold("Try again")) + 20;
		this.retryButton.set(centreX - retryWidth / 2, centreY + 12, retryWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.retryButton, "Try again", mouseX, mouseY, true);
	}

	private CardText cardText(SchematicEntry entry)
	{
		int cardWidth = this.screen.cardWidth;
		Font font = this.screen.font();
		CardText cached = this.cardTextCache.get(entry.id());

		if (cached != null && cached.entry() == entry && cached.width() == cardWidth)
		{
			return cached;
		}

		if (this.cardTextCache.size() >= IndexScreen.CARD_TEXT_CACHE_MAX)
		{
			this.cardTextCache.clear();
		}

		String tag = Theme.clip(font, entry.category().label(), cardWidth - 34);
		String name = Theme.bold(Theme.clipBold(font, entry.cardName(), cardWidth - 10));
		String downloads = entry.downloadsLabel();
		int downloadsWidth = font.width(downloads);
		// Mirrors renderCard's geometry: the credit runs from textX to the download glyph
		int posterRoom = cardWidth - 5 - downloadsWidth - Theme.DOWNLOAD_GLYPH_WIDTH - 6 - 5;
		Component credit = this.creditLine(entry, posterRoom);

		CardText computed = new CardText(entry, cardWidth, tag, name, downloads, downloadsWidth, credit);
		this.cardTextCache.put(entry.id(), computed);
		return computed;
	}

	// The poster's gradient only when the credit names them; a designer credit stays plain
	private Component creditLine(SchematicEntry entry, int room)
	{
		Font font = this.screen.font();

		if (!entry.isCreditPoster() || !entry.hasPosterStyle())
		{
			return Component.literal(Theme.clip(font, entry.credit(), room));
		}

		String name = Theme.clip(font, entry.poster(), room);
		return ModTags.creator(name, entry.posterStops(), Style.EMPTY);
	}

	private Rect heartRect(int cardX, int cardY, int imageHeight)
	{
		this.heartRectPool.set(cardX + this.screen.cardWidth - IndexScreen.HEART_SIZE - 6,
				cardY + imageHeight - IndexScreen.HEART_SIZE - 5, IndexScreen.HEART_SIZE, IndexScreen.HEART_SIZE);
		return this.heartRectPool;
	}

	private boolean heartAt(SchematicEntry entry, double mouseX, double mouseY)
	{
		IndexScreen screen = this.screen;
		int index = this.browse.visible.indexOf(entry);

		if (index < 0)
		{
			return false;
		}

		int row = index / screen.columns;
		int column = index % screen.columns;
		int x = screen.contentX + column * (screen.cardWidth + IndexScreen.GUTTER);
		int y = screen.gridTop + this.browse.featured.offset() + row * (screen.cardHeight + IndexScreen.GUTTER)
				- Math.round(screen.scroll);
		Rect heart = this.heartRect(x, y, IndexScreen.imageHeight(screen.cardWidth));
		return Theme.inside(mouseX, mouseY, heart.x - 2, heart.y - 2, IndexScreen.HEART_SIZE + 4,
				IndexScreen.HEART_SIZE + 4);
	}

	// Shared instance, consumed before the next call, like heartRect
	private Rect menuGlyphRect(SchematicEntry entry)
	{
		IndexScreen screen = this.screen;
		int index = this.browse.visible.indexOf(entry);
		int row = index / screen.columns;
		int column = index % screen.columns;
		int x = screen.contentX + column * (screen.cardWidth + IndexScreen.GUTTER);
		int y = screen.gridTop + this.browse.featured.offset() + row * (screen.cardHeight + IndexScreen.GUTTER)
				- Math.round(screen.scroll);
		this.menuGlyphRectPool.set(x + screen.cardWidth - 4 - CardMenu.GLYPH_CELL, y + 4, CardMenu.GLYPH_CELL, 11);
		return this.menuGlyphRectPool;
	}

	private @Nullable SchematicEntry entryAt(double mouseX, double mouseY)
	{
		IndexScreen screen = this.screen;

		if (mouseY < screen.gridTop || mouseY >= screen.gridBottom || mouseX < screen.contentX)
		{
			return null;
		}

		int rowHeight = screen.cardHeight + IndexScreen.GUTTER;
		int relativeY = (int) (mouseY - screen.gridTop - this.browse.featured.offset() + screen.scroll);
		int row = relativeY / rowHeight;

		if (relativeY < 0 || relativeY % rowHeight > screen.cardHeight)
		{
			return null;
		}

		int relativeX = (int) (mouseX - screen.contentX);
		int columnWidth = screen.cardWidth + IndexScreen.GUTTER;
		int column = relativeX / columnWidth;

		if (column >= screen.columns || relativeX % columnWidth > screen.cardWidth)
		{
			return null;
		}

		int index = row * screen.columns + column;
		return index >= 0 && index < this.browse.shownCap() ? this.browse.visible.get(index) : null;
	}

	// Clipped and formatted card strings by entry id, valid for one entry instance and card width, so a
	// visible card does not re-clip its name and re-format its counts every frame
	private record CardText(SchematicEntry entry, int width, String tag, String name, String downloads,
			int downloadsWidth, Component credit)
	{
	}
}

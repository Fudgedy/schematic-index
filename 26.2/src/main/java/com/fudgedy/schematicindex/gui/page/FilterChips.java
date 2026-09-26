package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.catalogue.Follows;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.BatchDownload;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class FilterChips
{
	private static final int OFFLINE_BANNER_HEIGHT = 15;

	private final BrowsePage browse;
	private final IndexScreen screen;
	private final Rect clearFiltersButton = new Rect();
	public final Rect followingFilterButton = new Rect();
	public final Rect historyButton = new Rect();
	final Rect offlineRefresh = new Rect();
	private final List<Rect> chipRects = new ArrayList<>();
	private final List<Category> chipOrder = new ArrayList<>();

	FilterChips(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
	}

	void layout()
	{
		IndexScreen screen = this.screen;
		FilterDropdowns dropdowns = this.browse.dropdowns;
		Font font = screen.font();
		this.chipRects.clear();
		this.chipOrder.clear();

		if (this.browse.profile.poster != null)
		{
			screen.chipRowHeight = 128;
			dropdowns.sortButton.set(0, 0, 0, 0);
			return;
		}

		if (screen.page != IndexScreen.Page.BROWSE && screen.page != IndexScreen.Page.SAVED)
		{
			screen.chipRowHeight = 26;
			return;
		}

		if (screen.page == IndexScreen.Page.SAVED)
		{
			this.browse.collections.layout();
			return;
		}

		int sortWidth = Dropdown.width(font, FilterDropdowns.SORT_CAPTION, FilterDropdowns.SORT_LABELS);
		int filterWidth = Dropdown.width(font, FilterDropdowns.DOWNLOAD_FILTER_CAPTION,
				FilterDropdowns.DOWNLOAD_FILTER_LABELS);
		int followWidth = font.width(Theme.bold(this.followingChipLabel())) + 12;
		int historyWidth = font.width(Theme.bold("History")) + 12;

		boolean showClear = this.filtersActive();
		int clearWidth = showClear ? font.width(Theme.bold("Clear filters")) + 12 : 0;
		int firstRowLimit = screen.contentX + screen.contentWidth - sortWidth - 6 - filterWidth
				- 6 - followWidth - 6 - historyWidth - 18;
		int limit = screen.contentX + screen.contentWidth;

		int x = screen.contentX;
		int row = 0;

		for (Category value : Category.values())
		{
			int width = font.width(Theme.bold(value.label())) + 12;
			int rowLimit = row == 0 ? firstRowLimit : limit;

			if (x + width > rowLimit && x > screen.contentX)
			{
				row++;
				x = screen.contentX;
			}

			Rect rect = new Rect();
			rect.set(x, IndexScreen.TOP_BAR_HEIGHT + 6 + row * (IndexScreen.CHIP_HEIGHT + IndexScreen.CHIP_GAP), width,
					IndexScreen.CHIP_HEIGHT);
			this.chipRects.add(rect);
			this.chipOrder.add(value);
			x += width + IndexScreen.CHIP_GAP;
		}

		// Clear filters sits on its own second line, so an active filter forces two rows
		int lastRow = showClear ? Math.max(row, 1) : row;
		screen.chipRowHeight = 6 + (lastRow + 1) * IndexScreen.CHIP_HEIGHT + lastRow * IndexScreen.CHIP_GAP + 6;

		if (screen.batchDownload.isActive())
		{
			screen.chipRowHeight += BatchDownload.STRIP_HEIGHT;
		}

		if (Catalogue.isStale())
		{
			screen.chipRowHeight += OFFLINE_BANNER_HEIGHT;
		}

		int top = IndexScreen.TOP_BAR_HEIGHT + 6;
		int height = IndexScreen.CHIP_HEIGHT;
		dropdowns.sortButton.set(screen.contentX + screen.contentWidth - sortWidth, top, sortWidth, height);
		dropdowns.downloadFilterButton.set(dropdowns.sortButton.x - 6 - filterWidth, top, filterWidth, height);
		this.followingFilterButton.set(dropdowns.downloadFilterButton.x - 6 - followWidth, top, followWidth, height);
		this.historyButton.set(this.followingFilterButton.x - 6 - historyWidth, top, historyWidth, height);

		if (showClear)
		{
			int clearY = IndexScreen.TOP_BAR_HEIGHT + 6 + (IndexScreen.CHIP_HEIGHT + IndexScreen.CHIP_GAP);
			this.clearFiltersButton.set(dropdowns.sortButton.x + dropdowns.sortButton.width - clearWidth, clearY,
					clearWidth, IndexScreen.CHIP_HEIGHT);
		}
		else
		{
			this.clearFiltersButton.set(0, 0, 0, 0);
		}
	}

	void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		IndexScreen screen = this.screen;
		Font font = screen.font();
		ctx.fill(IndexScreen.RAIL_WIDTH, IndexScreen.TOP_BAR_HEIGHT, screen.width,
				IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight, Theme.BACKDROP);

		if (this.browse.profile.poster != null)
		{
			this.browse.profile.renderHeader(ctx, mouseX, mouseY);
			return;
		}

		if (screen.page == IndexScreen.Page.SAVED)
		{
			this.browse.collections.render(ctx, mouseX, mouseY);
			this.browse.dropdowns.render(ctx, mouseX, mouseY);
			this.renderBatchStrip(ctx, mouseX, mouseY);
			return;
		}

		for (int i = 0; i < this.chipRects.size(); i++)
		{
			Rect rect = this.chipRects.get(i);
			Category value = this.chipOrder.get(i);
			boolean active = value == Category.ALL ? this.browse.activeTags.isEmpty()
					: this.browse.activeTags.contains(value);
			boolean hovered = rect.contains(mouseX, mouseY);
			int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

			float hover = Theme.buttonHover(rect, hovered);
			float scale = Theme.popScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
			Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

			Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);
			Theme.text(ctx, font, Theme.bold(value.label()), rect.x + 6,
					rect.y + (IndexScreen.CHIP_HEIGHT - font.lineHeight) / 2 + 1,
					active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);

			Theme.pop(ctx);
		}

		Buttons.pill(ctx, font, this.historyButton, "History", mouseX, mouseY, this.browse.historyView);
		Buttons.pill(ctx, font, this.followingFilterButton, this.followingChipLabel(), mouseX, mouseY,
				this.browse.followingFilter);
		this.browse.dropdowns.render(ctx, mouseX, mouseY);

		if (this.clearFiltersButton.width > 0)
		{
			this.chipPill(ctx, this.clearFiltersButton, "Clear filters", true, mouseX, mouseY);
		}

		this.renderBatchStrip(ctx, mouseX, mouseY);
	}

	// Sits in the strip layoutChips reserves; clears itself once a live refresh drops staleness
	void renderOfflineBanner(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		IndexScreen screen = this.screen;

		if (this.browse.profile.poster != null || screen.page != IndexScreen.Page.BROWSE || !Catalogue.isStale())
		{
			this.offlineRefresh.set(0, 0, 0, 0);
			return;
		}

		Font font = screen.font();
		int y = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight - OFFLINE_BANNER_HEIGHT;
		ctx.fill(IndexScreen.RAIL_WIDTH, y, screen.width, y + OFFLINE_BANNER_HEIGHT, 0xF0332A12);
		ctx.fill(IndexScreen.RAIL_WIDTH, y + OFFLINE_BANNER_HEIGHT - 1, screen.width, y + OFFLINE_BANNER_HEIGHT,
				Theme.HAIRLINE);

		int refreshWidth = font.width(Theme.bold("Refresh")) + 12;
		this.offlineRefresh.set(screen.contentX + screen.contentWidth - refreshWidth, y + 1, refreshWidth,
				OFFLINE_BANNER_HEIGHT - 3);

		String text = "Couldn't reach the server. Showing the last catalogue you loaded.";
		Theme.text(ctx, font, Theme.clip(font, text, screen.contentWidth - refreshWidth - 8),
				screen.contentX, y + (OFFLINE_BANNER_HEIGHT - font.lineHeight) / 2, Theme.TEXT);
		Buttons.pill(ctx, font, this.offlineRefresh, "Refresh", mouseX, mouseY, false);
	}

	// Every chip rect is sized from its own label at layout time, so the label needs no clipping here
	void chipPill(GuiGraphicsExtractor ctx, Rect rect, String label, boolean active, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		boolean hovered = rect.contains(mouseX, mouseY);
		int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.popScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);
		Theme.text(ctx, font, Theme.bold(label), rect.x + 6,
				rect.y + (IndexScreen.CHIP_HEIGHT - font.lineHeight) / 2 + 1, active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);
		Theme.pop(ctx);
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		IndexScreen screen = this.screen;
		BrowsePage browse = this.browse;
		FilterDropdowns dropdowns = browse.dropdowns;

		if (browse.profile.poster == null && screen.batchDownload.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (screen.gridPage() && browse.profile.poster == null
				&& dropdowns.sortDropdown.click(mouseX, mouseY) != Dropdown.NOT_HANDLED)
		{
			return true;
		}

		if (screen.page == IndexScreen.Page.BROWSE && this.clearFiltersButton.width > 0
				&& this.clearFiltersButton.contains(mouseX, mouseY))
		{
			this.clearFilters();
			return true;
		}

		if (screen.gridPage() && browse.profile.poster == null
				&& dropdowns.downloadFilterDropdown.click(mouseX, mouseY) != Dropdown.NOT_HANDLED)
		{
			return true;
		}

		if (screen.page == IndexScreen.Page.BROWSE && browse.profile.poster == null
				&& this.followingFilterButton.contains(mouseX, mouseY))
		{
			browse.followingFilter = !browse.followingFilter;
			Theme.click(browse.followingFilter ? 1.1F : 0.9F);
			browse.layoutChips();
			screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
			screen.scroll = 0.0F;
			browse.refilter();
			return true;
		}

		if (screen.page == IndexScreen.Page.BROWSE && browse.profile.poster == null
				&& this.historyButton.contains(mouseX, mouseY))
		{
			browse.historyView = !browse.historyView;
			Theme.click(browse.historyView ? 1.1F : 0.9F);
			browse.layoutChips();
			screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
			screen.scroll = 0.0F;
			browse.refilter();
			return true;
		}

		if (screen.page == IndexScreen.Page.SAVED && browse.collections.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		for (int i = 0; i < this.chipRects.size(); i++)
		{
			if (this.chipRects.get(i).contains(mouseX, mouseY))
			{
				Theme.buttonPop(this.chipRects.get(i));
				Category clicked = this.chipOrder.get(i);

				if (clicked == Category.ALL)
				{
					browse.activeTags.clear();
				}
				else if (browse.activeTags.contains(clicked))
				{
					browse.activeTags.remove(clicked);
				}
				else
				{
					browse.activeTags.add(clicked);
				}

				Theme.click();
				screen.scroll = 0.0F;
				// A tag toggle can add or drop the Clear-filters chip, so re-lay the row first
				browse.layoutChips();
				screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
				browse.refilter();
				return true;
			}
		}

		return false;
	}

	// A creator profile reuses BROWSE but is not a filter the Clear chip should reset
	boolean filtersActive()
	{
		BrowsePage browse = this.browse;
		return this.screen.page == IndexScreen.Page.BROWSE && browse.profile.poster == null
				&& (!browse.activeTags.isEmpty() || !browse.query.trim().isEmpty() || browse.downloadFilter != 0
						|| browse.followingFilter || browse.historyView);
	}

	String activeFilterSummary()
	{
		BrowsePage browse = this.browse;
		List<String> parts = new ArrayList<>();

		// EnumSet iterates in natural enum order, so the summary is stable
		if (!browse.activeTags.isEmpty())
		{
			List<String> tagLabels = new ArrayList<>();

			for (Category tag : browse.activeTags)
			{
				tagLabels.add(tag.label());
			}

			parts.add(String.join(", ", tagLabels));
		}

		String q = browse.query.trim();

		if (!q.isEmpty())
		{
			parts.add("\"" + q + "\"");
		}

		if (browse.downloadFilter != 0)
		{
			parts.add(browse.dropdowns.downloadFilterLabel());
		}

		if (browse.followingFilter)
		{
			parts.add("Following");
		}

		if (browse.historyView)
		{
			parts.add("History");
		}

		return String.join(", ", parts);
	}

	private void clearFilters()
	{
		BrowsePage browse = this.browse;
		IndexScreen screen = this.screen;
		browse.activeTags.clear();
		browse.setSearch("");
		browse.downloadFilter = 0;
		browse.followingFilter = false;
		browse.historyView = false;
		screen.refreshDownloadedNames();
		screen.setFocused(null);
		Theme.click(0.9F);
		browse.layoutChips();
		screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
		screen.scroll = 0.0F;
		browse.refilter();
	}

	// Sits above the offline banner's slot, since layoutChips stacks the two strips in that order
	private void renderBatchStrip(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		IndexScreen screen = this.screen;

		if (!screen.batchDownload.isActive())
		{
			return;
		}

		int y = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight - BatchDownload.STRIP_HEIGHT;

		if (screen.page == IndexScreen.Page.BROWSE && Catalogue.isStale())
		{
			y -= OFFLINE_BANNER_HEIGHT;
		}

		screen.batchDownload.render(ctx, screen.font(), screen.contentX, y, screen.contentWidth, mouseX, mouseY);
	}

	// The "N new" suffix is dropped once the filter is on, to keep the label terse
	private String followingChipLabel()
	{
		if (!this.browse.followingFilter)
		{
			int fresh = this.newFollowedPostCount();

			if (fresh > 0)
			{
				return "Following (" + fresh + " new)";
			}
		}

		return "Following";
	}

	// Linear scan, called only while laying out the chip row
	private int newFollowedPostCount()
	{
		if (BrowsePage.newSinceCutoff <= 0L)
		{
			return 0;
		}

		int count = 0;

		for (SchematicEntry entry : Catalogue.posts())
		{
			if (entry.postedAt() > BrowsePage.newSinceCutoff && Follows.isFollowing(entry.poster()))
			{
				count++;
			}
		}

		return count;
	}
}

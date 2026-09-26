package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Bookmarks;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.Follows;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BrowsePage
{
	static final int PAGE_SIZE = 12;
	// Below this many ratings a star average says little, so those posts sort behind the rest
	private static final int RATED_MIN_RATINGS = 3;
	private static final int LOADING_ROW = 20;
	// A keystroke schedules the heavy refilter instead of running it; render() fires it once typing
	// pauses, and any other refilter cancels the pending one
	static final long SEARCH_DEBOUNCE_MS = 120L;

	// Most recently opened first, in-memory
	private static final List<String> RECENT_VIEWED = new ArrayList<>();
	private static final int RECENT_VIEWED_MAX = 24;

	// Captured once per launch from the stored last-visit time, which is then advanced to now
	static long newSinceCutoff = -1L;

	private final IndexScreen screen;
	public final CardGrid grid;
	public final FilterChips chips;
	public final FilterDropdowns dropdowns;
	public final SavedCollections collections;
	public final CreatorProfile profile;
	public final BrowseSearch search;
	public final BrowseNav nav;
	public final FeaturedBanner featured;

	final List<SchematicEntry> visible = new ArrayList<>();
	// Empty means All; never contains Category.ALL. EnumSet gives a stable order for summaries and keys
	final Set<Category> activeTags = EnumSet.noneOf(Category.class);
	Catalogue.Sort sort = Catalogue.Sort.TRENDING;
	String query = "";
	boolean searchRefilterPending;
	long searchRefilterAt;
	int shownCount = PAGE_SIZE;
	// An unchanged signature means a background refresh, so scroll and page depth survive the refilter
	private String lastFilterKey = "";
	int focusedCard = -1;
	// The banner arrives after the feed, so a change in its height re-derives the scroll range
	private int laidBannerOffset;
	// 0 = all, 1 = only downloaded, 2 = not yet downloaded
	int downloadFilter;
	boolean followingFilter;
	// In-memory only, so the history view resets on a Minecraft restart
	boolean historyView;
	// Cards in this set show a "Viewed" badge explaining why they sit above the sorted order
	final Set<String> viewedFrontRow = new HashSet<>();
	// Local, user-made groups of posts shown in the Saved tab
	public @Nullable String activeCollection;

	public BrowsePage(IndexScreen screen)
	{
		this.screen = screen;
		this.grid = new CardGrid(this, screen);
		this.chips = new FilterChips(this, screen);
		this.dropdowns = new FilterDropdowns(this, screen);
		this.collections = new SavedCollections(this, screen);
		this.profile = new CreatorProfile(this, screen);
		this.search = new BrowseSearch(this, screen);
		this.nav = new BrowseNav(this, screen);
		this.featured = new FeaturedBanner(this, screen);
	}

	public static void captureNewSince()
	{
		if (newSinceCutoff < 0L)
		{
			newSinceCutoff = Settings.lastVisitAt();
			Settings.setLastVisitAt(System.currentTimeMillis());
		}
	}

	public void layoutGrid()
	{
		IndexScreen screen = this.screen;
		screen.columns = Math.max(2, Math.min(7, columnsFor(screen.contentWidth) + Settings.gridDensity()));
		screen.cardWidth = (screen.contentWidth - IndexScreen.GUTTER * (screen.columns - 1)) / screen.columns;
		screen.cardHeight = IndexScreen.imageHeight(screen.cardWidth) + IndexScreen.CAPTION_HEIGHT;
	}

	public void cycleGridDensity()
	{
		Settings.cycleGridDensity();

		this.layoutGrid();
		this.refilter();
	}

	public void layoutChips()
	{
		this.chips.layout();
	}

	// Chip-row geometry feeds gridTop, so anything that adds or drops a row re-runs both
	public void refreshChipRow()
	{
		this.layoutChips();
		this.screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight;
		this.recomputeScrollBounds();
	}

	public void refilterIfDue()
	{
		if (this.searchRefilterPending && System.currentTimeMillis() >= this.searchRefilterAt)
		{
			this.refilter();
		}
	}

	public void flushSearchDebounce()
	{
		if (this.searchRefilterPending)
		{
			this.refilter();
		}
	}

	public void refilter()
	{
		// Any refilter satisfies a pending debounce
		this.searchRefilterPending = false;
		this.visible.clear();
		String needle = this.query.trim().toLowerCase(Locale.ROOT);
		IndexScreen.Page page = this.screen.page;
		String poster = this.profile.poster;

		String filterKey = page + "|" + this.activeTags + "|" + this.sort + "|" + needle + "|"
				+ this.downloadFilter + "|" + poster + "|" + this.activeCollection
				+ "|" + this.followingFilter + "|" + this.historyView;
		boolean sameFilter = filterKey.equals(this.lastFilterKey);
		int prevShown = this.shownCount;

		for (SchematicEntry entry : Catalogue.posts())
		{
			if (poster != null)
			{
				if (!entry.poster().equals(poster))
				{
					continue;
				}
			}
			else if (page == IndexScreen.Page.SAVED)
			{
				if (this.activeCollection != null)
				{
					if (!CollectionStore.contains(this.activeCollection, entry.id()))
					{
						continue;
					}
				}
				else if (!IndexScreen.isSaved(entry))
				{
					continue;
				}
			}

			if (page != IndexScreen.Page.SAVED && !this.activeTags.isEmpty() && !this.activeTags.contains(entry.category()))
			{
				continue;
			}

			// The poster guard keeps a creator's own profile grid from being hidden
			if (this.followingFilter && page == IndexScreen.Page.BROWSE && poster == null
					&& !Follows.isFollowing(entry.poster()))
			{
				continue;
			}

			if (this.historyView && page == IndexScreen.Page.BROWSE && poster == null
					&& !RECENT_VIEWED.contains(entry.id()))
			{
				continue;
			}

			if (!needle.isEmpty()
					&& !entry.title().toLowerCase(Locale.ROOT).contains(needle)
					&& !entry.poster().toLowerCase(Locale.ROOT).contains(needle)
					&& !entry.designer().toLowerCase(Locale.ROOT).contains(needle))
			{
				continue;
			}

			// A profile grid is also page BROWSE, so the poster guard keeps its posts visible
			if (this.downloadFilter != 0 && poster == null)
			{
				boolean owned = this.screen.isDownloaded(entry);

				if (this.downloadFilter == 1 && !owned)
				{
					continue;
				}

				if (this.downloadFilter == 2 && owned)
				{
					continue;
				}
			}

			this.visible.add(entry);
		}

		Comparator<SchematicEntry> comparator;

		// On Saved, "Newest" means most recently added to Saved, not the post's upload date
		if (page == IndexScreen.Page.SAVED && this.sort == Catalogue.Sort.NEWEST)
		{
			List<String> order = this.activeCollection != null
					? new ArrayList<>(CollectionStore.postIds(this.activeCollection))
					: Bookmarks.savedOrder();
			Map<String, Integer> rank = new HashMap<>();

			for (int i = 0; i < order.size(); i++)
			{
				rank.put(order.get(i), i);
			}

			comparator = Comparator.comparingInt((SchematicEntry e) -> rank.getOrDefault(e.id(), -1)).reversed();
		}
		else
		{
			comparator = switch (this.sort)
			{
				case TRENDING -> Comparator.comparingDouble(SchematicEntry::trendScore).reversed()
						.thenComparing(Comparator.comparingLong(SchematicEntry::postedAt).reversed());
				case NEWEST -> Comparator.comparingLong(SchematicEntry::postedAt).reversed();
				case DOWNLOADS -> Comparator.comparingInt(SchematicEntry::downloads).reversed();
				case LIKES -> Comparator.comparingInt(IndexScreen::likesOf).reversed();
				case RATED -> Comparator.comparingInt((SchematicEntry e) -> e.starCount() >= RATED_MIN_RATINGS ? 0 : 1)
						.thenComparing(Comparator.comparingDouble(SchematicEntry::starAvg).reversed())
						.thenComparing(Comparator.comparingInt(SchematicEntry::starCount).reversed());
			};
		}

		this.visible.sort(comparator);
		this.viewedFrontRow.clear();

		// Overrides the sort above so the grid reads as a view-history timeline
		if (this.historyView && page == IndexScreen.Page.BROWSE && poster == null)
		{
			Map<String, Integer> viewedRank = new HashMap<>();

			for (int i = 0; i < RECENT_VIEWED.size(); i++)
			{
				viewedRank.put(RECENT_VIEWED.get(i), i);
			}

			this.visible.sort(Comparator.comparingInt(e -> viewedRank.getOrDefault(e.id(), Integer.MAX_VALUE)));
		}

		// Only on an unfiltered browse view: the top row becomes the most recently viewed posts
		if (page == IndexScreen.Page.BROWSE && poster == null && this.activeTags.isEmpty()
				&& needle.isEmpty() && this.downloadFilter == 0 && !this.historyView && !this.followingFilter
				&& !RECENT_VIEWED.isEmpty())
		{
			int topRow = Math.max(1, this.screen.columns);
			List<SchematicEntry> front = new ArrayList<>();

			for (String id : RECENT_VIEWED)
			{
				if (front.size() >= topRow)
				{
					break;
				}

				for (int i = 0; i < this.visible.size(); i++)
				{
					if (this.visible.get(i).id().equals(id))
					{
						front.add(this.visible.remove(i));
						this.viewedFrontRow.add(id);
						break;
					}
				}
			}

			this.visible.addAll(0, front);
		}

		if (sameFilter)
		{
			// A background refresh keeps the card count, and the clamp in recomputeScrollBounds keeps
			// the scroll offset, so a scrolled user is not yanked upward
			this.shownCount = Math.max(PAGE_SIZE, Math.min(prevShown, Math.max(PAGE_SIZE, this.visible.size())));
		}
		else
		{
			this.lastFilterKey = filterKey;
			this.shownCount = PAGE_SIZE;
		}

		this.focusedCard = -1;
		this.recomputeScrollBounds();
	}

	public boolean hasVisiblePosts()
	{
		return !this.visible.isEmpty();
	}

	// Feeds the Recently viewed row; only the detail card appends to it
	public void recordViewed(String id)
	{
		RECENT_VIEWED.remove(id);
		RECENT_VIEWED.add(0, id);

		while (RECENT_VIEWED.size() > RECENT_VIEWED_MAX)
		{
			RECENT_VIEWED.remove(RECENT_VIEWED.size() - 1);
		}
	}

	public void resetView()
	{
		this.collections.addingTo = null;
		this.activeCollection = null;
		this.profile.poster = null;
		this.nav.clear();
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (this.featured.offset() != this.laidBannerOffset)
		{
			this.recomputeScrollBounds();
		}

		this.grid.render(ctx, mouseX, mouseY);
		this.featured.render(ctx, mouseX, mouseY);
		this.renderScrollbar(ctx);
		this.chips.render(ctx, mouseX, mouseY);
		this.chips.renderOfflineBanner(ctx, mouseX, mouseY);
		this.collections.renderAddModeBanner(ctx, mouseX, mouseY);
		Tooltip.render(ctx, this.screen.font(), mouseX, mouseY, this.screen.width, this.screen.height);
	}

	public boolean mouseClicked(MouseButtonEvent event, double mouseX, double mouseY)
	{
		if (this.grid.retryButton.contains(mouseX, mouseY) || this.chips.offlineRefresh.contains(mouseX, mouseY))
		{
			Theme.click();
			Catalogue.refresh();
			return true;
		}

		if (this.profile.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.collections.addModeClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.chips.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.featured.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		return this.grid.mouseClicked(event, mouseX, mouseY);
	}

	void setSearch(String value)
	{
		this.query = value;

		if (this.search.box != null)
		{
			this.search.box.setValue(value);
		}
	}

	int shownCap()
	{
		return Math.min(this.shownCount, this.visible.size());
	}

	void recomputeScrollBounds()
	{
		IndexScreen screen = this.screen;
		int shown = this.shownCap();
		int rows = (shown + screen.columns - 1) / screen.columns;
		this.laidBannerOffset = this.featured.offset();
		int contentHeight = this.laidBannerOffset + rows * screen.cardHeight + Math.max(0, rows - 1) * IndexScreen.GUTTER;

		if (shown < this.visible.size())
		{
			contentHeight += LOADING_ROW;
		}

		screen.maxScroll = Math.max(0.0F, contentHeight - (screen.gridBottom - screen.gridTop));
		screen.scroll = Math.min(screen.scroll, screen.maxScroll);
	}

	void maybeLoadMore()
	{
		if (this.shownCount >= this.visible.size())
		{
			return;
		}

		if (this.screen.scroll >= this.screen.maxScroll - (this.screen.cardHeight + IndexScreen.GUTTER))
		{
			this.shownCount = Math.min(this.visible.size(), this.shownCount + PAGE_SIZE);
			this.recomputeScrollBounds();
		}
	}

	private static int columnsFor(int width)
	{
		if (width < 340)
		{
			return 2;
		}

		if (width < 500)
		{
			return 3;
		}

		if (width < 660)
		{
			return 4;
		}

		return 5;
	}

	private void renderScrollbar(GuiGraphics ctx)
	{
		IndexScreen screen = this.screen;

		if (screen.maxScroll <= 0.0F)
		{
			return;
		}

		screen.verticalScrollbar(ctx, IndexScreen.SCROLLBAR_MAIN, screen.scroll, screen.maxScroll, screen.gridTop,
				screen.gridBottom - screen.gridTop, screen.contentX + screen.contentWidth);
	}
}

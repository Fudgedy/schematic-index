package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.gui.IndexScreen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Set;

public class BrowseNav
{
	// Static, so a menu reopen lands on the same view and a Minecraft restart resets it
	private static Catalogue.Sort sessionSort = Catalogue.Sort.TRENDING;
	private static Set<Category> sessionTags = EnumSet.noneOf(Category.class);
	private static float sessionScroll;
	private static int sessionShown = BrowsePage.PAGE_SIZE;
	private static int sessionDownloadFilter;

	private final BrowsePage browse;
	private final IndexScreen screen;
	// Bounded so deep navigation cannot grow it forever
	private final ArrayDeque<Snapshot> stack = new ArrayDeque<>();

	BrowseNav(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
	}

	public void restoreSession()
	{
		this.browse.sort = sessionSort;
		this.browse.activeTags.clear();
		this.browse.activeTags.addAll(sessionTags);
		this.browse.downloadFilter = sessionDownloadFilter;
	}

	public void restoreScroll()
	{
		BrowsePage browse = this.browse;
		browse.shownCount = Math.max(BrowsePage.PAGE_SIZE, Math.min(sessionShown, browse.visible.size()));
		browse.recomputeScrollBounds();
		this.screen.scroll = Math.min(sessionScroll, this.screen.maxScroll);
	}

	public void saveSession()
	{
		sessionSort = this.browse.sort;
		sessionTags.clear();
		sessionTags.addAll(this.browse.activeTags);
		sessionScroll = this.screen.scroll;
		sessionShown = this.browse.shownCount;
		sessionDownloadFilter = this.browse.downloadFilter;
	}

	public boolean hasHistory()
	{
		return !this.stack.isEmpty();
	}

	public void back()
	{
		this.restore(this.stack.pop());
	}

	public boolean handleGridKey(int key)
	{
		BrowsePage browse = this.browse;
		int shown = browse.shownCap();

		if (shown <= 0)
		{
			return false;
		}

		if (key == 257 || key == 335)
		{
			if (browse.focusedCard >= 0 && browse.focusedCard < browse.visible.size())
			{
				this.screen.openDetail(browse.visible.get(browse.focusedCard));
			}
			else
			{
				browse.focusedCard = 0;
				this.scrollToFocused();
			}

			return true;
		}

		if (key != 262 && key != 263 && key != 264 && key != 265)
		{
			return false;
		}

		int columns = this.screen.columns;

		if (browse.focusedCard < 0)
		{
			browse.focusedCard = 0;
		}
		else if (key == 262)
		{
			browse.focusedCard = Math.min(shown - 1, browse.focusedCard + 1);
		}
		else if (key == 263)
		{
			browse.focusedCard = Math.max(0, browse.focusedCard - 1);
		}
		else if (key == 264)
		{
			browse.focusedCard = Math.min(shown - 1, browse.focusedCard + columns);
		}
		else
		{
			browse.focusedCard = Math.max(0, browse.focusedCard - columns);
		}

		browse.maybeLoadMore();
		this.scrollToFocused();
		return true;
	}

	void push()
	{
		BrowsePage browse = this.browse;
		// A copy, not the live set, so later edits do not mutate history
		this.stack.push(new Snapshot(this.screen.page, EnumSet.copyOf(browse.activeTags), browse.sort, browse.query,
				browse.downloadFilter, browse.profile.poster, browse.activeCollection, this.screen.scroll,
				browse.shownCount));

		while (this.stack.size() > 16)
		{
			this.stack.removeLast();
		}
	}

	void clear()
	{
		this.stack.clear();
	}

	// Scroll is applied after the list is rebuilt, so the clamp runs against the real content height
	private void restore(Snapshot snap)
	{
		BrowsePage browse = this.browse;
		IndexScreen screen = this.screen;
		screen.page = snap.page();
		browse.activeTags.clear();
		browse.activeTags.addAll(snap.activeTags());
		browse.sort = snap.sort();
		browse.setSearch(snap.query());
		browse.downloadFilter = snap.downloadFilter();
		browse.profile.poster = snap.profilePoster();
		browse.activeCollection = snap.activeCollection();
		screen.setFocused(null);

		// Both must run before the grid is rebuilt
		if (browse.profile.poster != null)
		{
			browse.profile.fetch(browse.profile.poster);
		}

		screen.refreshDownloadedNames();
		browse.layoutChips();
		screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
		browse.refilter();

		// Card count, then bounds, then the scroll offset last
		browse.shownCount = Math.max(BrowsePage.PAGE_SIZE,
				Math.min(snap.shownCount(), Math.max(BrowsePage.PAGE_SIZE, browse.visible.size())));
		browse.recomputeScrollBounds();
		screen.scroll = Math.max(0.0F, Math.min(snap.scroll(), screen.maxScroll));
	}

	private void scrollToFocused()
	{
		BrowsePage browse = this.browse;

		if (browse.focusedCard < 0)
		{
			return;
		}

		IndexScreen screen = this.screen;
		int rowHeight = screen.cardHeight + IndexScreen.GUTTER;
		int cardTop = (browse.focusedCard / screen.columns) * rowHeight;
		int cardBottom = cardTop + screen.cardHeight;
		int viewTop = Math.round(screen.scroll);
		int viewHeight = screen.gridBottom - screen.gridTop;

		if (cardTop < viewTop)
		{
			screen.scroll = cardTop;
		}
		else if (cardBottom > viewTop + viewHeight)
		{
			screen.scroll = cardBottom - viewHeight;
		}

		screen.scroll = Math.max(0.0F, Math.min(screen.scroll, screen.maxScroll));
	}

	// Captured on diving into a sub-view so back lands where the user left off, not on a default BROWSE
	private record Snapshot(IndexScreen.Page page, Set<Category> activeTags, Catalogue.Sort sort, String query,
			int downloadFilter, @Nullable String profilePoster, @Nullable String activeCollection,
			float scroll, int shownCount)
	{
	}
}

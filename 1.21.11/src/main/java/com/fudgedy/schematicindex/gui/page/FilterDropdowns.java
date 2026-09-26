package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public class FilterDropdowns
{
	static final String SORT_CAPTION = "Sort: ";
	static final String[] SORT_LABELS = sortLabels();
	static final String DOWNLOAD_FILTER_CAPTION = "Show: ";
	static final String[] DOWNLOAD_FILTER_LABELS = {"All", "Downloaded", "Not downloaded"};

	private final BrowsePage browse;
	private final IndexScreen screen;
	final Rect sortButton = new Rect();
	final Rect downloadFilterButton = new Rect();
	final Dropdown sortDropdown = new Dropdown();
	final Dropdown downloadFilterDropdown = new Dropdown();

	FilterDropdowns(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
	}

	// Drawn after the rail and top bar, so an open list sits above the grid rather than under the next card row
	public void renderLists(GuiGraphics ctx, int mouseX, int mouseY, boolean overlayOpen)
	{
		if (overlayOpen || !this.screen.gridPage() || this.browse.profile.poster != null)
		{
			this.closeDropdowns();
			return;
		}

		Font font = this.screen.font();
		this.sortDropdown.renderOpen(ctx, font, mouseX, mouseY, this.screen.height);
		this.downloadFilterDropdown.renderOpen(ctx, font, mouseX, mouseY, this.screen.height);
	}

	public @Nullable Dropdown openDropdown()
	{
		if (this.sortDropdown.isOpen())
		{
			return this.sortDropdown;
		}

		return this.downloadFilterDropdown.isOpen() ? this.downloadFilterDropdown : null;
	}

	public boolean closeDropdowns()
	{
		boolean wasOpen = this.openDropdown() != null;
		this.sortDropdown.close();
		this.downloadFilterDropdown.close();
		return wasOpen;
	}

	// An open chip-row list takes the click whole, so a click-away dismisses it without reaching the grid
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		Dropdown openDropdown = this.openDropdown();

		if (openDropdown != null)
		{
			this.choseDropdown(openDropdown, openDropdown.click(mouseX, mouseY));
			return true;
		}

		return false;
	}

	void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		this.downloadFilterDropdown.render(ctx, font, this.downloadFilterButton, DOWNLOAD_FILTER_LABELS,
				this.browse.downloadFilter, "", DOWNLOAD_FILTER_CAPTION, mouseX, mouseY);
		this.sortDropdown.render(ctx, font, this.sortButton, SORT_LABELS, this.browse.sort.ordinal(), "", SORT_CAPTION,
				mouseX, mouseY);
	}

	String downloadFilterLabel()
	{
		return DOWNLOAD_FILTER_CAPTION + DOWNLOAD_FILTER_LABELS[this.browse.downloadFilter];
	}

	private void choseDropdown(Dropdown dropdown, int choice)
	{
		if (choice < 0)
		{
			return;
		}

		if (dropdown == this.sortDropdown)
		{
			this.browse.sort = Catalogue.Sort.values()[choice];
			this.browse.refilter();
			return;
		}

		this.browse.downloadFilter = choice;
		this.screen.refreshDownloadedNames();
		this.screen.scroll = 0.0F;
		this.browse.refilter();
	}

	private static String[] sortLabels()
	{
		Catalogue.Sort[] all = Catalogue.Sort.values();
		String[] labels = new String[all.length];

		for (int i = 0; i < all.length; i++)
		{
			labels[i] = all[i].label();
		}

		return labels;
	}
}

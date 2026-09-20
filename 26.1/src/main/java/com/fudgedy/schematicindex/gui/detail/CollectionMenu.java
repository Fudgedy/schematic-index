package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

// The dropdown behind the detail card's Collections button
public class CollectionMenu
{
	private static final int DOWNLOAD_CELL = 13;

	private final IndexScreen screen;
	private boolean open;
	private final List<Rect> rects = new ArrayList<>();
	// One per row, on the right edge, so a collection can be pulled down whole from here
	private final List<Rect> downloadRects = new ArrayList<>();
	private final Rect newChip = new Rect();
	// Rows scroll inside the card once the collections outgrow the window
	private final Rect bounds = new Rect();
	private float scroll;
	private float maxScroll;

	public CollectionMenu(IndexScreen screen)
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
	}

	public void close()
	{
		this.open = false;
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, SchematicEntry entry, Rect anchor)
	{
		Font font = this.screen.font();
		this.rects.clear();
		this.downloadRects.clear();
		List<String> names = CollectionStore.names();

		int rowH = 15;
		int menuWidth = 160;
		int rowsHeight = (names.size() + 1) * rowH;
		int listHeight = Math.min(rowsHeight, this.screen.height - 16);
		int menuHeight = listHeight + 8;
		this.maxScroll = Math.max(0, rowsHeight - listHeight);
		this.scroll = Math.max(0.0F, Math.min(this.scroll, this.maxScroll));
		int mx = anchor.x;
		int my = anchor.y - menuHeight - 4;

		if (my < 4)
		{
			my = Math.min(anchor.y + 18, this.screen.height - menuHeight - 4);
		}

		if (mx + menuWidth > this.screen.width - 4)
		{
			mx = this.screen.width - menuWidth - 4;
		}

		this.bounds.set(mx, my, menuWidth, menuHeight);
		Theme.roundedRect(ctx, mx, my, menuWidth, menuHeight, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, mx, my, menuWidth, menuHeight, Theme.RADIUS_CARD, Theme.HAIRLINE);

		ctx.enableScissor(mx, my + 4, mx + menuWidth, my + 4 + listHeight);
		int cy = my + 4 - Math.round(this.scroll);

		for (String name : names)
		{
			Rect rect = new Rect();
			rect.set(mx + 4, cy, menuWidth - 8, rowH);
			this.rects.add(rect);

			boolean in = CollectionStore.contains(name, entry.id());
			boolean hovered = rect.contains(mouseX, mouseY);

			if (in || hovered)
			{
				Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
			}

			if (in)
			{
				ctx.fill(rect.x, rect.y + 2, rect.x + 3, rect.y + rect.height - 2, Theme.ACCENT);
			}

			Rect download = new Rect();
			download.set(rect.x + rect.width - DOWNLOAD_CELL, rect.y, DOWNLOAD_CELL, rowH);
			this.downloadRects.add(download);
			boolean downloadHovered = download.contains(mouseX, mouseY);

			if (hovered)
			{
				Theme.downloadGlyph(ctx, download.x + (DOWNLOAD_CELL - Theme.DOWNLOAD_GLYPH_WIDTH) / 2,
						download.y + (rowH - 5) / 2, downloadHovered ? Theme.ACCENT_BRIGHT : Theme.TEXT_MUTE);
			}

			Theme.text(ctx, font, Theme.clip(font, name, menuWidth - 16 - DOWNLOAD_CELL), mx + (in ? 12 : 8), cy + 3,
					in ? Theme.ACCENT_BRIGHT : Theme.TEXT);
			cy += rowH;
		}

		this.newChip.set(mx + 4, cy, menuWidth - 8, rowH);

		if (this.newChip.contains(mouseX, mouseY))
		{
			Theme.roundedRect(ctx, this.newChip.x, cy, menuWidth - 8, rowH, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
		}

		Theme.text(ctx, font, Theme.bold("+ New collection"), mx + 8, cy + 3, Theme.ACCENT);
		ctx.disableScissor();
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (!this.open || this.maxScroll <= 0.0F || !this.bounds.contains(mouseX, mouseY))
		{
			return false;
		}

		this.scroll = Math.max(0.0F,
				Math.min(this.maxScroll, this.scroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}

	public boolean mouseClicked(double mouseX, double mouseY, SchematicEntry entry)
	{
		List<String> names = CollectionStore.names();
		// Rows scrolled out of the card are clipped, so they must not answer to a click either
		boolean inside = this.bounds.contains(mouseX, mouseY);

		for (int i = 0; inside && i < this.rects.size() && i < names.size(); i++)
		{
			if (i < this.downloadRects.size() && this.downloadRects.get(i).contains(mouseX, mouseY))
			{
				this.open = false;
				this.screen.openDownloadAll(names.get(i));
				return true;
			}

			if (this.rects.get(i).contains(mouseX, mouseY))
			{
				boolean nowIn = CollectionStore.toggle(names.get(i), entry.id());
				Theme.click(nowIn ? 1.2F : 0.9F);

				if (this.screen.page == IndexScreen.Page.SAVED)
				{
					this.screen.refilter();
				}

				return true;
			}
		}

		if (inside && this.newChip.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.open = false;
			this.screen.nameInputModal.open(entry.id());
			return true;
		}

		this.open = false;
		return true;
	}
}

package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Right-click menu on a grid card; every entry reuses an action the detail card already offers
public class CardMenu
{
	public static final int GLYPH_CELL = 14;
	public static final int WIDTH = 150;
	private static final int ROW = 15;
	private static final int PAD = 4;
	private static final long ARROW_TURN_MS = 120L;
	private static final float QUARTER_TURN = (float) (Math.PI / 2.0);

	private final IndexScreen screen;
	private @Nullable SchematicEntry entry;
	private int anchorX;
	private int anchorY;
	private boolean collectionsOpen;
	private long openedAt;
	private long closedAt;
	private final Rect bounds = new Rect();
	private final Rect copyLink = new Rect();
	private final Rect addTo = new Rect();
	private final Rect download = new Rect();
	private final Rect creator = new Rect();
	private final Rect newCollection = new Rect();
	private final List<Rect> collectionRects = new ArrayList<>();
	// The sub-list scrolls inside this band once the collections outgrow the window
	private final Rect listBand = new Rect();
	private float listScroll;
	private float listMaxScroll;

	CardMenu(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.entry != null;
	}

	public boolean isFor(SchematicEntry entry)
	{
		return this.entry != null && this.entry.id().equals(entry.id());
	}

	public void open(SchematicEntry entry, int anchorX, int anchorY)
	{
		Usage.once("card_menu");
		this.entry = entry;
		this.anchorX = anchorX;
		this.anchorY = anchorY;
		this.collectionsOpen = false;
		this.closedAt = 0L;
		Theme.click(1.0F);
	}

	public void close()
	{
		this.entry = null;
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		SchematicEntry entry = this.entry;

		if (entry == null)
		{
			return;
		}

		Font font = this.screen.font();
		List<String> names = this.collectionsOpen ? CollectionStore.names() : List.of();
		int fixedRows = PAD * 2 + ROW * 4;
		int listHeight = this.collectionsOpen ? (names.size() + 1) * ROW : 0;
		int listView = Math.max(0, Math.min(listHeight, this.screen.height - 8 - fixedRows));
		this.listMaxScroll = Math.max(0, listHeight - listView);
		this.listScroll = Math.max(0.0F, Math.min(this.listScroll, this.listMaxScroll));
		int height = fixedRows + listView;
		int x = Math.max(4, Math.min(this.anchorX, this.screen.width - WIDTH - 4));
		int y = Math.max(4, Math.min(this.anchorY, this.screen.height - height - 4));
		this.bounds.set(x, y, WIDTH, height);

		Theme.roundedRect(ctx, x, y, WIDTH, height, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, WIDTH, height, Theme.RADIUS_CARD, Theme.HAIRLINE);

		int rowY = y + PAD;
		rowY = this.row(ctx, font, this.copyLink, x, rowY, "Copy share link", mouseX, mouseY);
		rowY = this.row(ctx, font, this.addTo, x, rowY, "Add to collection", mouseX, mouseY);
		int arrowX = x + WIDTH - 12;
		int arrowY = this.addTo.y + (ROW - 7) / 2;
		Theme.pushRotate(ctx, arrowX, arrowY, 4, 7, this.arrowTurn());
		Theme.arrow(ctx, arrowX, arrowY, false, this.collectionsOpen ? Theme.ACCENT_BRIGHT : Theme.TEXT_MUTE);
		Theme.pop(ctx);

		this.collectionRects.clear();

		if (this.collectionsOpen)
		{
			this.listBand.set(x, rowY, WIDTH, listView);
			ctx.enableScissor(x, rowY, x + WIDTH, rowY + listView);
			int bandBottom = rowY + listView;
			rowY -= Math.round(this.listScroll);

			for (String name : names)
			{
				Rect rect = new Rect();
				rect.set(x + PAD, rowY, WIDTH - PAD * 2, ROW);
				this.collectionRects.add(rect);

				boolean in = CollectionStore.contains(name, entry.id());
				boolean hovered = rect.contains(mouseX, mouseY);

				if (in || hovered)
				{
					Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
				}

				if (in)
				{
					ctx.fill(rect.x + 8, rect.y + 2, rect.x + 11, rect.y + rect.height - 2, Theme.ACCENT);
				}

				Theme.text(ctx, font, Theme.clip(font, name, WIDTH - 28), x + (in ? 20 : 16), rowY + 3,
						in ? Theme.ACCENT_BRIGHT : Theme.TEXT);
				rowY += ROW;
			}

			this.newCollection.set(x + PAD, rowY, WIDTH - PAD * 2, ROW);

			if (this.newCollection.contains(mouseX, mouseY))
			{
				Theme.roundedRect(ctx, this.newCollection.x, rowY, WIDTH - PAD * 2, ROW, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
			}

			Theme.text(ctx, font, Theme.bold("+ New collection"), x + 16, rowY + 3, Theme.ACCENT);
			ctx.disableScissor();
			rowY = bandBottom;
		}
		else
		{
			this.newCollection.set(0, 0, 0, 0);
			this.listBand.set(0, 0, 0, 0);
		}

		String downloadLabel = this.screen.isDownloaded(entry) ? "Download again" : "Download";
		rowY = this.row(ctx, font, this.download, x, rowY, downloadLabel, mouseX, mouseY);
		this.row(ctx, font, this.creator, x, rowY, "Open creator", mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		SchematicEntry entry = this.entry;

		if (entry == null)
		{
			return false;
		}

		if (this.copyLink.contains(mouseX, mouseY))
		{
			this.close();
			Theme.click(1.1F);
			this.screen.copyShareLink(entry);
		}
		else if (this.addTo.contains(mouseX, mouseY))
		{
			this.collectionsOpen = !this.collectionsOpen;

			if (this.collectionsOpen)
			{
				this.openedAt = Util.getMillis();
			}
			else
			{
				this.closedAt = Util.getMillis();
			}

			Theme.click(this.collectionsOpen ? 1.1F : 0.9F);
		}
		else if (this.collectionsOpen && this.listBand.contains(mouseX, mouseY)
				&& this.clickCollection(mouseX, mouseY, entry))
		{
			return true;
		}
		else if (this.collectionsOpen && this.listBand.contains(mouseX, mouseY)
				&& this.newCollection.contains(mouseX, mouseY))
		{
			this.close();
			Theme.click(1.1F);
			this.screen.nameInputModal.open(entry.id());
		}
		else if (this.download.contains(mouseX, mouseY))
		{
			this.close();
			this.screen.requestDownload(entry);
		}
		else if (this.creator.contains(mouseX, mouseY))
		{
			this.close();
			Theme.click(1.0F);
			this.screen.openProfile(entry.poster());
		}
		else
		{
			this.close();
			Theme.click(0.9F);
		}

		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (this.entry == null || this.listMaxScroll <= 0.0F || !this.bounds.contains(mouseX, mouseY))
		{
			return false;
		}

		this.listScroll = Math.max(0.0F,
				Math.min(this.listMaxScroll, this.listScroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (this.entry == null || event.key() != 256)
		{
			return false;
		}

		this.close();
		return true;
	}

	// Three dots in a dark pill, the same treatment as the card's category tag
	public static void glyph(GuiGraphicsExtractor ctx, int x, int y, boolean lit)
	{
		Theme.roundedRect(ctx, x, y, GLYPH_CELL, 11, Theme.RADIUS_PILL, 0xCC0F1114);
		int color = lit ? Theme.ACCENT_BRIGHT : Theme.TEXT;

		for (int i = 0; i < 3; i++)
		{
			int dotX = x + 2 + i * 4;
			ctx.fill(dotX, y + 4, dotX + 2, y + 6, color);
		}
	}

	private int row(GuiGraphicsExtractor ctx, Font font, Rect rect, int x, int y, String label, int mouseX, int mouseY)
	{
		rect.set(x + PAD, y, WIDTH - PAD * 2, ROW);

		if (rect.contains(mouseX, mouseY))
		{
			Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
		}

		Theme.text(ctx, font, label, x + 8, y + 3, Theme.TEXT);
		return y + ROW;
	}

	// The chevron swings from right to down as the sub-list opens and back as it closes
	private float arrowTurn()
	{
		long since = this.collectionsOpen ? this.openedAt : this.closedAt;
		float fraction = Theme.easeOut((Util.getMillis() - since) / (float) ARROW_TURN_MS);
		return (this.collectionsOpen ? fraction : 1.0F - fraction) * QUARTER_TURN;
	}

	private boolean clickCollection(double mouseX, double mouseY, SchematicEntry entry)
	{
		List<String> names = CollectionStore.names();

		for (int i = 0; i < this.collectionRects.size() && i < names.size(); i++)
		{
			if (!this.collectionRects.get(i).contains(mouseX, mouseY))
			{
				continue;
			}

			boolean nowIn = CollectionStore.toggle(names.get(i), entry.id());
			Theme.click(nowIn ? 1.2F : 0.9F);

			if (this.screen.page == IndexScreen.Page.SAVED)
			{
				this.screen.refilter();
			}

			return true;
		}

		return false;
	}
}

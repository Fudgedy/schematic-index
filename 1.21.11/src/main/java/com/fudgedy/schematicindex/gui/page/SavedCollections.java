package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.BatchDownload;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.modal.CoachMark;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SavedCollections
{
	private static final int TRASH_CELL_WIDTH = 13; // trash glyph + padding reserved in named chips

	private final BrowsePage browse;
	private final IndexScreen screen;
	@Nullable String addingTo;
	private final List<Rect> chips = new ArrayList<>();
	private final Rect newChip = new Rect();
	private final Rect addPostsChip = new Rect();
	private final Rect loadCodeChip = new Rect();
	private final Rect generateCodeChip = new Rect();
	private final Rect downloadAllChip = new Rect();
	public @Nullable String sharedCode;   // generated share code for the active collection
	private boolean sharingCode;
	private long codeCopiedAt;             // shows "Copied!" on the chip briefly after copying
	private final Rect addModeDone = new Rect();
	// Shared instance: trashRect()'s value is always consumed immediately
	private final Rect trashRectPool = new Rect();

	SavedCollections(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
	}

	public boolean optionsClicked(MouseButtonEvent event, double mouseX, double mouseY)
	{
		IndexScreen screen = this.screen;

		if (event.button() == 1 && screen.page == IndexScreen.Page.SAVED && !screen.detailView.isOpen()
				&& !screen.termsModal.isOpen() && !screen.tutorialModal.isOpen()
				&& !screen.designerWarnModal.isOpen())
		{
			for (int i = 1; i < this.chips.size(); i++)
			{
				if (this.chips.get(i).contains(mouseX, mouseY))
				{
					List<String> names = CollectionStore.names();

					if (i - 1 < names.size())
					{
						screen.collectionOptionsModal.open(names.get(i - 1));
						Theme.click(0.9F);
					}

					return true;
				}
			}
		}

		return false;
	}

	void layout()
	{
		IndexScreen screen = this.screen;
		FilterDropdowns dropdowns = this.browse.dropdowns;
		Font font = screen.font();
		this.chips.clear();

		int sortWidth = Dropdown.width(font, FilterDropdowns.SORT_CAPTION, FilterDropdowns.SORT_LABELS);
		int filterWidth = Dropdown.width(font, FilterDropdowns.DOWNLOAD_FILTER_CAPTION,
				FilterDropdowns.DOWNLOAD_FILTER_LABELS);
		int firstRowLimit = screen.contentX + screen.contentWidth - sortWidth - 6 - filterWidth - 8;
		int limit = screen.contentX + screen.contentWidth;
		int x = screen.contentX;
		int[] row = {0};
		int rowStep = IndexScreen.CHIP_HEIGHT + IndexScreen.CHIP_GAP;
		int top = IndexScreen.TOP_BAR_HEIGHT + 6;

		List<String> labels = new ArrayList<>();
		labels.add("All saved");
		labels.addAll(CollectionStore.names());

		for (int idx = 0; idx < labels.size(); idx++)
		{
			int width = font.width(Theme.bold(labels.get(idx))) + 12;

			// Named collections carry a trash icon on the right
			if (idx > 0)
			{
				width += TRASH_CELL_WIDTH;
			}

			x = this.wrapChip(x, width, firstRowLimit, limit, row);
			Rect rect = new Rect();
			rect.set(x, top + row[0] * rowStep, width, IndexScreen.CHIP_HEIGHT);
			this.chips.add(rect);
			x += width + IndexScreen.CHIP_GAP;
		}

		int newWidth = font.width(Theme.bold("+ New")) + 12;
		x = this.wrapChip(x, newWidth, firstRowLimit, limit, row);
		this.newChip.set(x, top + row[0] * rowStep, newWidth, IndexScreen.CHIP_HEIGHT);
		x += newWidth + IndexScreen.CHIP_GAP;

		if (this.browse.activeCollection != null)
		{
			int addWidth = font.width(Theme.bold("+ Add posts")) + 12;
			x = this.wrapChip(x, addWidth, firstRowLimit, limit, row);
			this.addPostsChip.set(x, top + row[0] * rowStep, addWidth, IndexScreen.CHIP_HEIGHT);
			x += addWidth + IndexScreen.CHIP_GAP;

			String genLabel = this.generateCodeLabel();
			int genTextWidth = font.width(Theme.bold(genLabel));

			// Size to the wider of "Copied!" and the code so the chip does not resize when one lapses
			if (this.sharedCode != null)
			{
				genTextWidth = Math.max(genTextWidth,
						Math.max(font.width(Theme.bold("Copied!")), font.width(Theme.bold(this.sharedCode))));
			}

			int genWidth = genTextWidth + 12;
			x = this.wrapChip(x, genWidth, firstRowLimit, limit, row);
			this.generateCodeChip.set(x, top + row[0] * rowStep, genWidth, IndexScreen.CHIP_HEIGHT);
			x += genWidth + IndexScreen.CHIP_GAP;
		}
		else
		{
			this.addPostsChip.set(0, 0, 0, 0);
			this.generateCodeChip.set(0, 0, 0, 0);
		}

		int dlWidth = font.width(Theme.bold("Download all")) + 12;
		x = this.wrapChip(x, dlWidth, firstRowLimit, limit, row);
		this.downloadAllChip.set(x, top + row[0] * rowStep, dlWidth, IndexScreen.CHIP_HEIGHT);
		x += dlWidth + IndexScreen.CHIP_GAP;

		int loadWidth = font.width(Theme.bold("Load code")) + 12;
		x = this.wrapChip(x, loadWidth, firstRowLimit, limit, row);
		this.loadCodeChip.set(x, top + row[0] * rowStep, loadWidth, IndexScreen.CHIP_HEIGHT);
		x += loadWidth + IndexScreen.CHIP_GAP;

		screen.chipRowHeight = 6 + (row[0] + 1) * IndexScreen.CHIP_HEIGHT + row[0] * IndexScreen.CHIP_GAP + 6;

		if (screen.batchDownload.isActive())
		{
			screen.chipRowHeight += BatchDownload.STRIP_HEIGHT;
		}

		dropdowns.sortButton.set(screen.contentX + screen.contentWidth - sortWidth, top, sortWidth,
				IndexScreen.CHIP_HEIGHT);
		dropdowns.downloadFilterButton.set(dropdowns.sortButton.x - 6 - filterWidth, top, filterWidth,
				IndexScreen.CHIP_HEIGHT);
	}

	void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		FilterChips pills = this.browse.chips;
		List<String> names = CollectionStore.names();

		for (int i = 0; i < this.chips.size(); i++)
		{
			Rect rect = this.chips.get(i);

			if (i == 0)
			{
				pills.chipPill(ctx, rect, "All saved", this.browse.activeCollection == null, mouseX, mouseY);
				continue;
			}

			String label = i - 1 < names.size() ? names.get(i - 1) : "";
			this.namedChip(ctx, rect, label, label.equals(this.browse.activeCollection), mouseX, mouseY);
		}

		pills.chipPill(ctx, this.newChip, "+ New", false, mouseX, mouseY);

		if (this.browse.activeCollection != null)
		{
			pills.chipPill(ctx, this.addPostsChip, "+ Add posts", false, mouseX, mouseY);
			// The only chip whose label changes between layouts, so it alone is still clipped on draw
			pills.chipPill(ctx, this.generateCodeChip,
					Theme.clipBold(this.screen.font(), this.generateCodeLabel(), this.generateCodeChip.width - 8),
					this.sharedCode != null, mouseX, mouseY);
		}

		// Highlighted while a batch runs so the chip reads as a state, not a fresh action
		pills.chipPill(ctx, this.downloadAllChip, "Download all", this.screen.batchDownload.isActive(), mouseX, mouseY);
		pills.chipPill(ctx, this.loadCodeChip, "Load code", false, mouseX, mouseY);
	}

	void renderAddModeBanner(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (this.addingTo == null)
		{
			return;
		}

		IndexScreen screen = this.screen;
		Font font = screen.font();
		int height = 20;
		int y = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
		ctx.fill(IndexScreen.RAIL_WIDTH, y, screen.width, y + height, Theme.ACCENT);

		String text = "Tap posts to add them to \"" + this.addingTo + "\"";
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, text, screen.contentWidth - 90)),
				screen.contentX, y + (height - font.lineHeight) / 2, Theme.ON_ACCENT);

		int doneWidth = font.width(Theme.bold("Done")) + 16;
		this.addModeDone.set(screen.contentX + screen.contentWidth - doneWidth, y + 2, doneWidth, height - 4);
		boolean hovered = this.addModeDone.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, this.addModeDone.x, this.addModeDone.y, doneWidth, height - 4, Theme.RADIUS_PILL,
				hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.text(ctx, font, Theme.bold("Done"), this.addModeDone.x + 8, this.addModeDone.y + 2, Theme.TEXT);
	}

	boolean addModeClicked(double mouseX, double mouseY)
	{
		if (this.addingTo != null && this.addModeDone.contains(mouseX, mouseY))
		{
			IndexScreen screen = this.screen;
			String collection = this.addingTo;
			this.addingTo = null;
			screen.page = IndexScreen.Page.SAVED;
			this.browse.activeCollection = collection;
			this.browse.setSearch("");
			Theme.click(0.9F);
			this.browse.layoutChips();
			screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
			screen.scroll = 0.0F;
			this.browse.refilter();
			return true;
		}

		return false;
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		IndexScreen screen = this.screen;
		BrowsePage browse = this.browse;
		List<String> names = CollectionStore.names();

		for (int i = 1; i < this.chips.size(); i++)
		{
			if (this.trashRect(this.chips.get(i)).contains(mouseX, mouseY))
			{
				if (i - 1 < names.size())
				{
					screen.deleteCollectionModal.open(names.get(i - 1));
					Theme.click(0.9F);
				}

				return true;
			}
		}

		for (int i = 0; i < this.chips.size(); i++)
		{
			if (this.chips.get(i).contains(mouseX, mouseY))
			{
				browse.activeCollection = i == 0 ? null : (i - 1 < names.size() ? names.get(i - 1) : null);
				this.sharedCode = null;
				Theme.click();
				screen.scroll = 0.0F;
				browse.layoutChips();
				screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
				browse.refilter();
				return true;
			}
		}

		if (this.newChip.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			screen.nameInputModal.open(null);
			CoachMark.maybe(screen, CoachMark.Kind.COLLECTIONS);
			return true;
		}

		if (browse.activeCollection != null && this.addPostsChip.contains(mouseX, mouseY))
		{
			this.addingTo = browse.activeCollection;
			screen.page = IndexScreen.Page.BROWSE;
			browse.activeTags.clear();
			browse.setSearch("");
			Theme.click(1.1F);
			browse.layoutChips();
			screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight + 20;
			screen.scroll = 0.0F;
			browse.refilter();
			return true;
		}

		if (browse.activeCollection != null && this.generateCodeChip.contains(mouseX, mouseY))
		{
			if (this.sharedCode != null)
			{
				screen.copyToClipboard(this.sharedCode);
				this.codeCopiedAt = System.currentTimeMillis();
				browse.layoutChips();
				Theme.click(1.2F);
			}
			else if (!this.sharingCode)
			{
				this.generateShareCode();
			}

			return true;
		}

		if (this.downloadAllChip.contains(mouseX, mouseY))
		{
			screen.openDownloadAll(browse.activeCollection);
			return true;
		}

		if (this.loadCodeChip.contains(mouseX, mouseY))
		{
			screen.loadCodeModal.open();
			Theme.click(1.1F);
			return true;
		}

		return false;
	}

	private void namedChip(GuiGraphics ctx, Rect rect, String label, boolean active, int mouseX, int mouseY)
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

		Rect trash = this.trashRect(rect);
		boolean trashHovered = trash.contains(mouseX, mouseY);
		int trashColor = trashHovered ? 0xFFE05555 : (active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);
		Theme.trashGlyph(ctx, trash.x + (trash.width - Theme.TRASH_GLYPH_WIDTH) / 2,
				trash.y + (trash.height - 8) / 2, trashColor);
		Theme.pop(ctx);
	}

	private Rect trashRect(Rect chip)
	{
		// Shared instance: the caller consumes it before the next trashRect() call
		this.trashRectPool.set(chip.x + chip.width - TRASH_CELL_WIDTH, chip.y, TRASH_CELL_WIDTH, chip.height);
		return this.trashRectPool;
	}

	private String generateCodeLabel()
	{
		if (this.sharedCode != null)
		{
			return System.currentTimeMillis() - this.codeCopiedAt < 1400L ? "Copied!" : this.sharedCode;
		}

		return this.sharingCode ? "..." : "Generate code";
	}

	private int wrapChip(int x, int width, int firstRowLimit, int limit, int[] row)
	{
		int rowLimit = row[0] == 0 ? firstRowLimit : limit;

		if (x + width > rowLimit && x > this.screen.contentX)
		{
			row[0]++;
			return this.screen.contentX;
		}

		return x;
	}

	private void generateShareCode()
	{
		String collection = this.browse.activeCollection;

		if (collection == null)
		{
			return;
		}

		List<String> ids = new ArrayList<>(CollectionStore.postIds(collection));

		if (ids.isEmpty())
		{
			this.screen.status = "Add some posts before sharing this collection.";
			return;
		}

		Theme.click(1.1F);
		this.screen.requireVerified(() -> {
			this.sharingCode = true;
			Thread worker = new Thread(() -> {
				String code = Backend.shareCollection(collection, ids);

				if (code != null)
				{
					Shards.pokeSoon();
				}

				Minecraft.getInstance().execute(() -> {
					this.sharingCode = false;

					if (code != null && collection.equals(this.browse.activeCollection))
					{
						this.sharedCode = code;
						this.browse.layoutChips();
					}
					else if (code == null)
					{
						this.screen.status = "Could not create a code, try again.";
					}
				});
			}, "schematicindex-share");
			worker.setDaemon(true);
			worker.start();
		});
	}
}

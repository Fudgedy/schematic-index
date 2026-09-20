package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Confirms a whole-collection download with its size before anything is queued
public class DownloadAllModal
{
	private final IndexScreen screen;
	private boolean open;
	private String label = "";
	private final List<SchematicEntry> pending = new ArrayList<>();
	private int total;
	private int present;
	private long bytes;
	private final Rect downloadButton = new Rect();
	private final Rect cancelButton = new Rect();
	private final Rect bounds = new Rect();

	public DownloadAllModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(String label, List<SchematicEntry> entries)
	{
		// The folder listing feeding isDownloaded may predate files dropped in by hand
		this.screen.refreshDownloadedNames();
		this.pending.clear();
		this.total = entries.size();
		this.present = 0;
		this.bytes = 0L;

		for (SchematicEntry entry : entries)
		{
			if (this.screen.isDownloaded(entry))
			{
				this.present++;
				continue;
			}

			this.pending.add(entry);
			this.bytes += Math.max(0L, entry.fileSize());
		}

		if (this.pending.isEmpty())
		{
			Theme.click(0.9F);
			Toasts.push("Nothing to download", "Everything here is already in your folder.",
					new ItemStack(Items.BOOKSHELF));
			return;
		}

		this.label = label;
		this.open = true;
		this.screen.modalFocus = -1;
		Theme.click(1.0F);
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 16;
		int cardWidth = Math.min(this.screen.width - 40, 300);
		int line = font.lineHeight;
		List<String> lines = this.screen.wrap(this.summary(), cardWidth - pad * 2, 2);
		int cardHeight = pad + line + 4 + line + 8 + lines.size() * (line + 2) + 12 + IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.text(ctx, font, Theme.bold("Download all"), x + pad, y + pad, Theme.TEXT);
		Theme.text(ctx, font, Theme.clip(font, this.label, cardWidth - pad * 2), x + pad, y + pad + line + 4,
				Theme.ACCENT_BRIGHT);

		int lineY = y + pad + line + 4 + line + 8;

		for (String text : lines)
		{
			Theme.text(ctx, font, text, x + pad, lineY, Theme.TEXT_MUTE);
			lineY += line + 2;
		}

		int buttonY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		String downloadLabel = "Download " + this.pending.size();
		int downloadWidth = font.width(Theme.bold(downloadLabel)) + 20;
		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		this.cancelButton.set(x + pad, buttonY, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.downloadButton.set(x + cardWidth - pad - downloadWidth, buttonY, downloadWidth, IndexScreen.FIELD_HEIGHT);

		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.downloadButton, downloadLabel, mouseX, mouseY, true);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.downloadButton.contains(mouseX, mouseY))
		{
			this.confirm();
		}
		else if (this.cancelButton.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (event.key() == 256)
		{
			this.open = false;
		}

		return true;
	}

	public Rect[] focusButtons()
	{
		return new Rect[]{this.downloadButton, this.cancelButton};
	}

	public void activateFocus(Rect button, int index)
	{
		if (index == 0)
		{
			this.confirm();
			return;
		}

		this.open = false;
	}

	public Rect[] pressButtons()
	{
		return new Rect[]{this.downloadButton, this.cancelButton};
	}

	private void confirm()
	{
		this.open = false;
		Theme.click(1.2F);
		this.screen.batchDownload.start(this.label, new ArrayList<>(this.pending));
	}

	private String summary()
	{
		return this.total + (this.total == 1 ? " schematic, " : " schematics, ") + this.present
				+ " already downloaded, " + sizeLabel(this.bytes);
	}

	// Posts from the offline snapshot carry no size, so a zero total says so instead of claiming 0 KB
	private static String sizeLabel(long bytes)
	{
		if (bytes <= 0L)
		{
			return "size unknown";
		}

		if (bytes < 1_048_576L)
		{
			return Math.max(1L, (bytes + 1023L) / 1024L) + " KB";
		}

		return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0D);
	}
}

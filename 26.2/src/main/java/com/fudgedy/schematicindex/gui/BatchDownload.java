package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// Downloads a collection one file at a time and draws its progress strip under the chip row
public final class BatchDownload
{
	public static final int STRIP_HEIGHT = 18;
	private static final int BAR_WIDTH = 70;
	private static final int PILL_HEIGHT = 12;

	private final IndexScreen screen;
	private final List<SchematicEntry> queue = new ArrayList<>();
	private String label = "";
	// Position of the file in flight; -1 while idle, and it stays put on a failure so Retry resumes there
	private int index = -1;
	private boolean failed;
	private boolean stopping;
	private final Rect retryButton = new Rect();
	private final Rect stopButton = new Rect();

	BatchDownload(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isActive()
	{
		return this.index >= 0;
	}

	public boolean isRunning()
	{
		return this.index >= 0 && !this.failed;
	}

	public void start(String label, List<SchematicEntry> entries)
	{
		if (this.isActive() || entries.isEmpty())
		{
			return;
		}

		this.label = label;
		this.queue.clear();
		this.queue.addAll(entries);
		this.index = 0;
		this.failed = false;
		this.stopping = false;
		Usage.send("download_all");
		this.screen.beginDownload(this.queue.get(0), false);
		this.screen.refreshChipRow();
	}

	// Polled each frame: Download.progress is the only signal the transfer thread leaves behind
	public void tick()
	{
		if (!this.isRunning())
		{
			return;
		}

		Download.Progress progress = Download.progress(this.queue.get(this.index).id());
		Download.State state = progress == null ? Download.State.FAILED : progress.state();

		if (state == Download.State.RUNNING)
		{
			return;
		}

		if (state == Download.State.FAILED)
		{
			this.failed = true;
			this.screen.refreshChipRow();
			return;
		}

		this.index++;

		if (this.stopping || this.index >= this.queue.size())
		{
			this.finish();
			return;
		}

		this.screen.beginDownload(this.queue.get(this.index), false);
	}

	public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		if (!this.isActive())
		{
			return;
		}

		SchematicEntry current = this.queue.get(this.index);
		int pillY = y + (STRIP_HEIGHT - PILL_HEIGHT) / 2;
		int right = x + width;

		if (this.failed)
		{
			int retryWidth = font.width(Theme.bold("Retry failed")) + 14;
			int dismissWidth = font.width(Theme.bold("Dismiss")) + 14;
			this.stopButton.set(right - dismissWidth, pillY, dismissWidth, PILL_HEIGHT);
			this.retryButton.set(this.stopButton.x - 6 - retryWidth, pillY, retryWidth, PILL_HEIGHT);
			Buttons.pill(ctx, font, this.stopButton, "Dismiss", mouseX, mouseY, false);
			Buttons.pill(ctx, font, this.retryButton, "Retry failed", mouseX, mouseY, true);
		}
		else
		{
			String stopLabel = this.stopping ? "Stopping" : "Stop";
			int stopWidth = font.width(Theme.bold("Stopping")) + 14;
			this.stopButton.set(right - stopWidth, pillY, stopWidth, PILL_HEIGHT);
			this.retryButton.set(0, 0, 0, 0);

			if (this.stopping)
			{
				Buttons.disabled(ctx, font, this.stopButton, stopLabel);
			}
			else
			{
				Buttons.pill(ctx, font, this.stopButton, stopLabel, mouseX, mouseY, false);
			}
		}

		int barX = (this.failed ? this.retryButton.x : this.stopButton.x) - 8 - BAR_WIDTH;
		int barY = y + STRIP_HEIGHT / 2 - 2;
		Theme.roundedRect(ctx, barX, barY, BAR_WIDTH, 4, Theme.RADIUS_PILL, Theme.HAIRLINE);
		int filled = Math.round(BAR_WIDTH * this.fraction());

		if (filled > 0)
		{
			Theme.roundedRect(ctx, barX, barY, filled, 4, Theme.RADIUS_PILL,
					this.failed ? 0xFFD64545 : Theme.ACCENT_BRIGHT);
		}

		String counter = (this.index + 1) + " / " + this.queue.size() + " \u00b7 ";
		String text = (this.failed ? "Failed at " : "") + counter + current.title();
		Theme.text(ctx, font, Theme.clip(font, text, barX - 8 - x), x, y + (STRIP_HEIGHT - font.lineHeight) / 2 + 1,
				this.failed ? Theme.TEXT : Theme.TEXT_MUTE);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.isActive())
		{
			return false;
		}

		if (this.failed && this.retryButton.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.failed = false;
			this.screen.beginDownload(this.queue.get(this.index), false);
			this.screen.refreshChipRow();
			return true;
		}

		if (!this.stopButton.contains(mouseX, mouseY))
		{
			return false;
		}

		Theme.click(0.9F);

		if (this.failed)
		{
			this.reset();
		}
		else
		{
			// The file in flight cannot be interrupted, so the queue drains once it lands
			this.stopping = true;
		}

		return true;
	}

	private float fraction()
	{
		float current = 0.0F;

		if (!this.failed)
		{
			Download.Progress progress = Download.progress(this.queue.get(this.index).id());
			current = progress == null ? 0.0F : progress.fraction();
		}

		return Math.min(1.0F, (this.index + current) / this.queue.size());
	}

	private void finish()
	{
		int saved = this.index;
		boolean complete = saved >= this.queue.size();
		String detail = complete
				? saved + (saved == 1 ? " file saved to your schematics folder." : " files saved to your schematics folder.")
				: saved + " of " + this.queue.size() + " files saved.";
		Toasts.push(complete ? this.label + " downloaded" : "Download stopped", detail,
				new ItemStack(Items.BOOKSHELF));
		this.reset();
	}

	private void reset()
	{
		this.index = -1;
		this.failed = false;
		this.stopping = false;
		this.queue.clear();
		this.screen.refreshChipRow();
	}
}

package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.TextBoxes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// Two stages sharing one card: the reason picker, then the free-text context
public class ReportModal
{
	private static final String[] REASONS = {
			"NSFW / Explicit Content", "Stealing Credit", "Spam / Misleading", "Other"};

	private static final String[] CODES = {"NSFW", "STOLEN", "SPAM", "OTHER"};
	private static final int CONTEXT_MAX = 99;

	private final IndexScreen screen;
	private boolean pickerOpen;
	private final Rect[] reasonRects = new Rect[REASONS.length];
	private final Rect cancelButton = new Rect();

	private boolean contextOpen;
	private int reasonIndex = -1;
	private String context = "";
	private final Rect submitButton = new Rect();
	private final Rect bounds = new Rect();

	public ReportModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isPickerOpen()
	{
		return this.pickerOpen;
	}

	public boolean isContextOpen()
	{
		return this.contextOpen;
	}

	public void openPicker()
	{
		this.pickerOpen = true;
	}

	public void closePicker()
	{
		this.pickerOpen = false;
	}

	public void close()
	{
		this.pickerOpen = false;
		this.contextOpen = false;
	}

	public void renderPicker(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 14;
		int rowHeight = 16;
		int reasonGap = 5;
		int cardWidth = 232;
		int line = font.lineHeight;
		List<String> warning = this.screen.wrap(
				"Only report posts that break the rules. False reports are taken seriously and can get "
						+ "your account banned from the Index.", cardWidth - pad * 2, 4);

		int titleToWarning = 12;
		int warningToReasons = 16;
		int reasonsToDivider = 10;
		int dividerToCancel = 10;
		int warnHeight = warning.size() * (line + 1);
		int reasonsHeight = REASONS.length * rowHeight + (REASONS.length - 1) * reasonGap;

		int cardHeight = pad + line + titleToWarning + warnHeight + warningToReasons + reasonsHeight
				+ reasonsToDivider + 1 + dividerToCancel + rowHeight + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.text(ctx, font, Theme.bold("Report this post"), x + pad, y + pad, Theme.TEXT);

		int warnY = y + pad + line + titleToWarning;

		for (String row : warning)
		{
			Theme.text(ctx, font, row, x + pad, warnY, Theme.ACCENT_BRIGHT);
			warnY += line + 1;
		}

		int rowY = warnY - 1 + warningToReasons;

		for (int i = 0; i < REASONS.length; i++)
		{
			if (this.reasonRects[i] == null)
			{
				this.reasonRects[i] = new Rect();
			}

			this.reasonRects[i].set(x + pad, rowY, cardWidth - pad * 2, rowHeight);
			Buttons.pill(ctx, font, this.reasonRects[i], REASONS[i], mouseX, mouseY, false);
			rowY += rowHeight + reasonGap;
		}

		rowY += reasonsToDivider - reasonGap;
		ctx.fill(x + pad, rowY, x + cardWidth - pad, rowY + 1, Theme.HAIRLINE);
		rowY += 1 + dividerToCancel;

		this.cancelButton.set(x + pad, rowY, cardWidth - pad * 2, rowHeight);
		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
	}

	public void renderContext(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 14;
		int line = font.lineHeight;
		int cardWidth = 236;
		int boxInner = cardWidth - pad * 2 - 12;

		List<String> lines = this.screen.wrapContext(this.context, boxInner);
		int textLines = Math.max(1, lines.size());
		int boxHeight = 8 + textLines * (line + 1) + 6;

		int cardHeight = pad + line + 4 + line + 8 + boxHeight + 6 + line + 10 + 16 + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);

		int ty = y + pad;
		Theme.text(ctx, font, Theme.bold("Context"), x + pad, ty, Theme.TEXT);
		ty += line + 4;
		Theme.text(ctx, font, "Explain the report - under 100 characters.", x + pad, ty, Theme.TEXT_ASH);
		ty += line + 8;

		int boxX = x + pad;
		int boxWidth = cardWidth - pad * 2;
		TextBoxes.wrapped(ctx, font, boxX, ty, boxWidth, boxHeight, lines, this.context, "Type here...",
				true);

		ty += boxHeight + 6;
		String counter = this.context.length() + "/" + CONTEXT_MAX;
		Theme.text(ctx, font, counter, x + cardWidth - pad - font.width(counter), ty, Theme.TEXT_ASH);
		ty += line + 10;

		this.submitButton.set(x + pad, ty, cardWidth - pad * 2, 16);
		Buttons.pill(ctx, font, this.submitButton, "Submit report", mouseX, mouseY, true);
	}

	public void clickPicker(double mouseX, double mouseY)
	{
		for (int i = 0; i < this.reasonRects.length; i++)
		{
			Rect rect = this.reasonRects[i];

			if (rect != null && rect.contains(mouseX, mouseY))
			{
				this.pickerOpen = false;
				this.contextOpen = true;
				this.reasonIndex = i;
				this.context = "";
				Theme.click(1.1F);
				return;
			}
		}

		if (this.cancelButton.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			this.pickerOpen = false;
			Theme.click(0.9F);
		}
	}

	public void clickContext(double mouseX, double mouseY)
	{
		if (this.submitButton.contains(mouseX, mouseY))
		{
			this.submit();
		}
		else if (!this.bounds.contains(mouseX, mouseY))
		{
			this.contextOpen = false;
			this.reasonIndex = -1;
			this.context = "";
			Theme.click(0.9F);
		}
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.contextOpen)
		{
			return false;
		}

		if (IndexScreen.isPasteChord(event))
		{
			this.context = this.screen.pasteInto(this.context, CONTEXT_MAX);
			return true;
		}

		switch (event.key())
		{
			case 259 -> {
				if (!this.context.isEmpty())
				{
					this.context = this.context.substring(0, this.context.length() - 1);
				}
			}
			case 257, 335 -> this.submit();
			case 256 -> this.contextOpen = false;
			default -> {
			}
		}

		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		if (!this.contextOpen)
		{
			return false;
		}

		if (event.codepoint() >= ' ' && this.context.length() < CONTEXT_MAX)
		{
			this.context += event.codepointAsString();
		}

		return true;
	}

	public Rect[] pickerPressButtons()
	{
		List<Rect> picker = new ArrayList<>();

		for (Rect rect : this.reasonRects)
		{
			if (rect != null)
			{
				picker.add(rect);
			}
		}

		picker.add(this.cancelButton);
		return picker.toArray(new Rect[0]);
	}

	public Rect[] contextPressButtons()
	{
		return new Rect[]{this.submitButton};
	}

	// Only an accepted report shows the success toast
	private void submit()
	{
		SchematicEntry entry = this.screen.detailView.entry();
		String code = this.reasonIndex >= 0 && this.reasonIndex < CODES.length
				? CODES[this.reasonIndex] : "OTHER";

		if (entry != null)
		{
			String postId = entry.id();
			String note = this.context;
			Thread worker = new Thread(() -> {
				boolean ok = Backend.report(postId, code, note);
				Minecraft.getInstance().execute(() -> {
					if (ok)
					{
						Toasts.push("Report submitted", "Thanks - we'll take a look.", new ItemStack(Items.PAPER));
					}
					else if (Backend.configured())
					{
						Toasts.push("Report failed", "Couldn't send your report. Please try again.", new ItemStack(Items.BARRIER));
					}
				});
			}, "schematicindex-report");
			worker.setDaemon(true);
			worker.start();
		}

		this.contextOpen = false;
		this.reasonIndex = -1;
		this.context = "";
		Theme.click(1.1F);
	}
}

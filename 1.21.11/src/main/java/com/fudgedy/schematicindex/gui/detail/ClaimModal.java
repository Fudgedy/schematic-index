package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.McAuth;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// Submitted to POST /post/:id/claim
public class ClaimModal
{
	private static final int NOTE_MAX = 199;

	private final IndexScreen screen;
	private boolean open;
	private boolean busy;
	private String note = "";
	private final Rect bounds = new Rect();
	private final Rect submitButton = new Rect();
	private final Rect cancelButton = new Rect();
	// A response landing after a close or reopen must not clobber a newer attempt's state
	private int requestSeq;

	public ClaimModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public boolean isBusy()
	{
		return this.busy;
	}

	public void open()
	{
		this.open = true;
		this.note = "";
		this.busy = false;
		this.screen.modalFocus = -1;
	}

	public void close()
	{
		this.open = false;
		this.note = "";
		this.screen.modalFocus = -1;
	}

	// Cleared without touching modalFocus, so reopening the detail card starts from a blank note
	public void reset()
	{
		this.open = false;
		this.note = "";
		this.busy = false;
	}

	// Unlike the report modal this stays open until the server answers, so a 4xx reacts in place
	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 14;
		int line = font.lineHeight;
		int cardWidth = 236;
		int boxInner = cardWidth - pad * 2 - 12;

		SchematicEntry entry = this.screen.detailView.entry();
		String designer = entry == null || entry.designer() == null || entry.designer().isBlank()
				? "Unknown" : entry.designer();
		String current = Theme.clip(font, "Current designer: " + designer, cardWidth - pad * 2);
		List<String> blurb = this.screen.wrapContext(
				"A moderator will review your claim. Add proof or context below (optional).",
				cardWidth - pad * 2);
		int blurbHeight = blurb.size() * (line + 1);

		List<String> lines = this.screen.wrapContext(this.note, boxInner);
		int textLines = Math.max(1, lines.size());
		int boxHeight = 8 + textLines * (line + 1) + 6;

		int cardHeight = pad + line + 4 + line + 6 + blurbHeight + 8 + boxHeight + 6 + line + 10
				+ 16 + 6 + 16 + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);

		int ty = y + pad;
		Theme.text(ctx, font, Theme.bold("Claim designer credit"), x + pad, ty, Theme.TEXT);
		ty += line + 4;
		Theme.text(ctx, font, current, x + pad, ty, Theme.TEXT_ASH);
		ty += line + 6;

		for (String row : blurb)
		{
			Theme.text(ctx, font, row, x + pad, ty, Theme.TEXT_MUTE);
			ty += line + 1;
		}

		ty += 8;

		int boxX = x + pad;
		int boxWidth = cardWidth - pad * 2;
		TextBoxes.wrapped(ctx, font, boxX, ty, boxWidth, boxHeight, lines, this.note, "Type here...",
				!this.busy);

		ty += boxHeight + 6;
		String counter = this.note.length() + "/" + NOTE_MAX;
		Theme.text(ctx, font, counter, x + cardWidth - pad - font.width(counter), ty, Theme.TEXT_ASH);
		ty += line + 10;

		this.submitButton.set(x + pad, ty, cardWidth - pad * 2, 16);

		if (this.busy)
		{
			Buttons.mock(ctx, font, this.submitButton, "Submitting...");
		}
		else
		{
			Buttons.pill(ctx, font, this.submitButton, "Submit claim", mouseX, mouseY, true);
		}

		ty += 16 + 6;
		this.cancelButton.set(x + pad, ty, cardWidth - pad * 2, 16);
		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
	}

	public void mouseClicked(double mouseX, double mouseY)
	{
		if (this.busy)
		{
			return;
		}

		if (this.submitButton.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.submit();
		}
		else if (this.cancelButton.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.close();
		}
	}

	// The modal-focus handler normally consumes Enter; this keeps the behaviour otherwise
	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.busy)
		{
			return true;
		}

		if (IndexScreen.isPasteChord(event))
		{
			this.note = this.screen.pasteInto(this.note, NOTE_MAX);
			return true;
		}

		switch (event.key())
		{
			case 259 -> {
				if (!this.note.isEmpty())
				{
					this.note = this.note.substring(0, this.note.length() - 1);
				}
			}
			case 257, 335 -> this.submit();
			case 256 -> this.close();
			default -> {
			}
		}

		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (!this.busy && event.codepoint() >= ' ' && this.note.length() < NOTE_MAX)
		{
			this.note += event.codepointAsString();
		}

		return true;
	}

	public Rect[] focusButtons()
	{
		return new Rect[]{this.submitButton, this.cancelButton};
	}

	public void activateFocus(Rect button, int index)
	{
		if (index == 0)
		{
			this.submit();
		}
		else
		{
			this.close();
		}
	}

	public Rect[] pressButtons()
	{
		return this.busy ? new Rect[0] : new Rect[]{this.submitButton, this.cancelButton};
	}

	// A superseded response leaves the modal state alone but still reports the outcome, so closing
	// the card cannot silently swallow it
	private void submit()
	{
		SchematicEntry entry = this.screen.detailView.entry();

		if (entry == null || entry.id() == null || this.busy)
		{
			return;
		}

		this.busy = true;
		int seq = ++this.requestSeq;
		String postId = entry.id();
		String note = this.note;
		Thread worker = new Thread(() -> {
			Backend.ApiResult result = Backend.submitClaim(postId, note);
			SchematicIndexMod.LOGGER.debug("claim {} -> {} {}", postId, result.status(), result.error());
			Minecraft.getInstance().execute(() -> {
				if (seq == this.requestSeq)
				{
					this.busy = false;

					if (this.open)
					{
						this.close();
					}
				}

				if (result.ok())
				{
					Toasts.push("Claim submitted", "A moderator will review your claim.",
							new ItemStack(Items.WRITABLE_BOOK));
				}
				else if (result.status() == 401 || result.status() == 403)
				{
					// The server no longer honours the session; re-verify and let the user resubmit
					McAuth.invalidate();
					this.screen.startVerify(() -> {
						if (this.screen.detailView.isOpen())
						{
							this.screen.claimModal.open();
						}
					});
				}
				else if (result.status() == 409 && "already_credited_to_you".equals(result.error()))
				{
					Toasts.push("Already credited", "This post already lists you as the designer.",
							new ItemStack(Items.EMERALD));
				}
				else if (result.status() == 409)
				{
					Toasts.push("Claim already pending", "You already have a claim in review for this post.",
							new ItemStack(Items.CLOCK));
				}
				else if (result.status() == 429)
				{
					Toasts.push("Slow down", "Too many claims recently - try again later.",
							new ItemStack(Items.CLOCK));
				}
				else
				{
					this.screen.showError(Errors.CLAIM);
				}
			});
		}, "schematicindex-claim");
		worker.setDaemon(true);
		worker.start();
	}
}

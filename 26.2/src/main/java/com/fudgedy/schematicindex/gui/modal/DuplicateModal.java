package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DuplicateModal
{
	private final IndexScreen screen;
	private boolean open;
	private boolean pending;
	private @Nullable String postId;
	private @Nullable String title;
	private final Rect close = new Rect();
	private final Rect viewPost = new Rect();
	// Captured during render so a click on the scrim outside it dismisses the modal
	private final Rect bounds = new Rect();

	public DuplicateModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	// Submit-time fallback: the server already refused the post, with no postId to offer
	public void open()
	{
		this.open(false, null, null);
	}

	// The early, pre-Post check: postId lets the player jump to the existing post, pending flags one
	// still awaiting review, where a title may not exist yet
	public void open(boolean pending, @Nullable String postId, @Nullable String title)
	{
		this.open = true;
		this.pending = pending;
		this.postId = postId;
		this.title = title != null && !title.isBlank() ? title : null;
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 16;
		int cardWidth = Math.min(this.screen.width - 40, 320);
		int line = font.lineHeight;
		String message = this.pending
				? "This schematic is already waiting for review."
				: "This schematic has already been uploaded. You cannot post it again.";
		List<String> body = this.screen.wrap(message, cardWidth - pad * 2 - 4, 4);
		int titleHeight = this.title != null ? line + 4 : 0;
		int cardHeight = pad + line + 8 + body.size() * (line + 2) + titleHeight + 14 + IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, true);

		int tx = x + pad + 4;
		int ty = y + pad;
		Theme.text(ctx, font, Theme.bold("Already uploaded"), tx, ty, Theme.TEXT);
		ty += line + 8;

		for (String row : body)
		{
			Theme.text(ctx, font, row, tx, ty, Theme.TEXT_ASH);
			ty += line + 2;
		}

		if (this.title != null)
		{
			Theme.text(ctx, font, Theme.clip(font, "\"" + this.title + "\"", cardWidth - pad * 2 - 4), tx, ty, Theme.TEXT_MUTE);
			ty += titleHeight;
		}

		int btnY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		int closeWidth = font.width(Theme.bold("Close")) + 20;
		this.close.set(x + cardWidth - pad - closeWidth, btnY, closeWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.close, "Close", mouseX, mouseY, true);

		if (this.postId != null)
		{
			int viewWidth = Buttons.width(font, "View post");
			this.viewPost.set(this.close.x - Theme.SPACE_S - viewWidth, btnY, viewWidth, IndexScreen.FIELD_HEIGHT);
			Buttons.button(ctx, font, this.viewPost, "View post", Buttons.Kind.SECONDARY, true, mouseX, mouseY);
		}
		else
		{
			this.viewPost.set(0, 0, 0, 0);
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.viewPost.contains(mouseX, mouseY) && this.postId != null)
		{
			// openPostById's show() plays the click; a second one here would double it
			String postId = this.postId;
			this.open = false;
			this.screen.openPostById(postId);
			return true;
		}

		if (this.close.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
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

		if (event.key() == 256 || event.key() == 257 || event.key() == 335)
		{
			this.open = false;
		}

		return true;
	}
}

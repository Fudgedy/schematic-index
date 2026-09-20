package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

public class ErrorModal
{
	private final IndexScreen screen;
	private boolean open;
	private String code = "";
	private final Rect close = new Rect();
	private final Rect report = new Rect();
	// Captured during render so a click on the scrim outside it dismisses the modal
	private final Rect bounds = new Rect();

	public ErrorModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(String code)
	{
		this.code = code;
		this.open = true;
		this.screen.modalFocus = -1;
	}

	// A failure raised off the render thread waits in Errors until a frame can surface it
	public void takePending()
	{
		if (this.open)
		{
			Errors.dropPending(this.code);
			return;
		}

		String next = Errors.takePending();

		if (next != null)
		{
			this.code = next;
			this.open = true;
		}
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
		int cardWidth = Math.min(this.screen.width - 40, 330);
		int line = font.lineHeight;
		int innerWidth = cardWidth - pad * 2 - 4;

		String discord = RemoteContent.discord();
		boolean hasDiscord = discord != null && !discord.isBlank();

		List<String> desc = this.screen.wrap(
				"Report this bug using the code you were given in the discord server below.", innerWidth, 3);

		int cardHeight = pad + line + 8 + line + 10 + desc.size() * (line + 2) + 8 + line + 16
				+ IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, true);

		int tx = x + pad + 4;
		int ty = y + pad;

		Theme.text(ctx, font, Theme.bold("Something went wrong"), tx, ty, Theme.TEXT);
		ty += line + 8;

		Theme.text(ctx, font, "Error code:", tx, ty, Theme.TEXT_ASH);
		Theme.text(ctx, font, Theme.bold(this.code), tx + font.width("Error code: "), ty, Theme.TEXT);
		ty += line + 10;

		for (String row : desc)
		{
			Theme.text(ctx, font, row, tx, ty, Theme.TEXT_ASH);
			ty += line + 2;
		}

		ty += 8;

		if (hasDiscord)
		{
			String linkText = Theme.clip(font, discord, innerWidth);
			int linkWidth = font.width(linkText);
			this.report.set(tx, ty, linkWidth, line);
			boolean hover = this.report.contains(mouseX, mouseY);
			int color = hover ? Theme.ACCENT_BRIGHT : Theme.ACCENT;
			Theme.text(ctx, font, linkText, tx, ty, color);
			ctx.fill(tx, ty + line - 1, tx + linkWidth, ty + line, color);
		}
		else
		{
			this.report.set(0, 0, 0, 0);
			Theme.text(ctx, font, "Discord link coming soon.", tx, ty, Theme.TEXT_MUTE);
		}

		int closeWidth = font.width(Theme.bold("Close")) + 20;
		int closeY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		this.close.set(x + cardWidth - pad - closeWidth, closeY, closeWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.close, "Close", mouseX, mouseY, false);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.report.contains(mouseX, mouseY))
		{
			this.screen.openLink(RemoteContent.discord());
		}
		else if (this.close.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
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
		String discord = RemoteContent.discord();
		return discord != null && !discord.isBlank()
				? new Rect[]{this.report, this.close}
				: new Rect[]{this.close};
	}

	public void activateFocus(Rect button, int index)
	{
		// The focus list may or may not include the report link, so dispatch by identity
		if (button == this.report)
		{
			this.screen.openLink(RemoteContent.discord());
		}
		else
		{
			this.open = false;
		}
	}
}

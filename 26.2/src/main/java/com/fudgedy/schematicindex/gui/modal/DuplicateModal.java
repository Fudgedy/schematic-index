package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

public class DuplicateModal
{
	private final IndexScreen screen;
	private boolean open;
	private final Rect close = new Rect();
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

	public void open()
	{
		this.open = true;
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
		List<String> body = this.screen.wrap(
				"This schematic has already been uploaded. You cannot post it again.", cardWidth - pad * 2 - 4, 4);
		int cardHeight = pad + line + 8 + body.size() * (line + 2) + 14 + IndexScreen.FIELD_HEIGHT + pad;
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

		int btnY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		int closeWidth = font.width(Theme.bold("Close")) + 20;
		this.close.set(x + cardWidth - pad - closeWidth, btnY, closeWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.close, "Close", mouseX, mouseY, true);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
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

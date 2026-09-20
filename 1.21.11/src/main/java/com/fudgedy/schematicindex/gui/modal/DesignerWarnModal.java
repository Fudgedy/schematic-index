package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

public class DesignerWarnModal
{
	private final IndexScreen screen;
	private boolean open;
	private boolean dontShow;
	private final Rect cancel = new Rect();
	private final Rect upload = new Rect();
	private final Rect toggle = new Rect();
	// Captured during render so a click on the scrim outside it dismisses the modal
	private final Rect bounds = new Rect();

	public DesignerWarnModal(IndexScreen screen)
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
		this.dontShow = false;
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 14;
		int line = font.lineHeight;
		int cardWidth = 252;
		List<String> body = this.screen.wrap("You haven't set a designer. This post will be marked as Unknown.",
				cardWidth - pad * 2, 3);

		int boxSize = 9;
		int toggleRowHeight = boxSize + 8;
		int titleToBody = 12;
		int bodyToToggle = 16;
		int toggleToDivider = 12;
		int dividerToButtons = 12;
		int buttonHeight = 16;
		int bodyHeight = body.size() * (line + 1);

		int cardHeight = pad + line + titleToBody + bodyHeight + bodyToToggle + toggleRowHeight
				+ toggleToDivider + 1 + dividerToButtons + buttonHeight + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.text(ctx, font, Theme.bold("No designer set"), x + pad, y + pad, Theme.TEXT);

		int bodyY = y + pad + line + titleToBody;

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + pad, bodyY, Theme.ACCENT_BRIGHT);
			bodyY += line + 1;
		}

		int toggleY = bodyY - 1 + bodyToToggle;
		String toggleLabel = "Don't show this again";
		int toggleRowWidth = boxSize + 8 + font.width(toggleLabel) + 8;
		this.toggle.set(x + pad, toggleY, toggleRowWidth, toggleRowHeight);

		Theme.roundedRect(ctx, x + pad, toggleY, toggleRowWidth, toggleRowHeight, Theme.RADIUS_PILL,
				Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x + pad, toggleY, toggleRowWidth, toggleRowHeight, Theme.RADIUS_PILL,
				Theme.ACCENT_BRIGHT);

		int boxX = x + pad + 5;
		int boxY = toggleY + (toggleRowHeight - boxSize) / 2;
		Theme.roundedRect(ctx, boxX, boxY, boxSize, boxSize, Theme.RADIUS_PILL,
				this.dontShow ? Theme.ACCENT : Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, boxX, boxY, boxSize, boxSize, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
		Theme.text(ctx, font, toggleLabel, boxX + boxSize + 6, toggleY + (toggleRowHeight - line) / 2, Theme.TEXT);

		int dividerY = toggleY + toggleRowHeight + toggleToDivider;
		ctx.fill(x + pad, dividerY, x + cardWidth - pad, dividerY + 1, Theme.HAIRLINE);

		int buttonY = dividerY + 1 + dividerToButtons;
		int bw = (cardWidth - pad * 2 - 8) / 2;
		this.cancel.set(x + pad, buttonY, bw, buttonHeight);
		this.upload.set(x + pad + bw + 8, buttonY, bw, buttonHeight);
		Buttons.pill(ctx, font, this.cancel, "Cancel", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.upload, "Upload anyway", mouseX, mouseY, true);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.toggle.contains(mouseX, mouseY))
		{
			this.dontShow = !this.dontShow;
			Theme.click(0.9F);
			return true;
		}

		if (this.cancel.contains(mouseX, mouseY))
		{
			this.open = false;
			Theme.click(0.9F);
			return true;
		}

		if (this.upload.contains(mouseX, mouseY))
		{
			if (this.dontShow)
			{
				Settings.setSkipDesignerWarning(true);
			}

			this.open = false;
			Theme.click(1.1F);
			this.screen.beginUpload();
			return true;
		}

		// A warning defaults to cancel
		if (!this.bounds.contains(mouseX, mouseY))
		{
			this.open = false;
			Theme.click(0.9F);
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open || event.key() != 256)
		{
			return false;
		}

		this.open = false;
		Theme.click(0.9F);
		return true;
	}
}

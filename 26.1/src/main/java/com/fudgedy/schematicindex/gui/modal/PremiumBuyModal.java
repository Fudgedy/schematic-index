package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.Premium;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

// Buying a paid or booster listing: the schematic lives with the partner, so this points the buyer
// to that partner's Discord to complete the purchase or boost there
public class PremiumBuyModal
{
	private final IndexScreen screen;
	private boolean open;
	private Premium.Entry entry;
	private final Rect openDiscord = new Rect();
	private final Rect close = new Rect();
	private final Rect bounds = new Rect();

	public PremiumBuyModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(Premium.Entry entry)
	{
		this.entry = entry;
		this.open = true;
	}

	public void close()
	{
		this.open = false;
		this.entry = null;
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (!this.open || this.entry == null)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 14;
		int line = font.lineHeight;
		int cardWidth = 268;

		String kind = this.entry.type() == Premium.Type.BOOSTER ? "boost-unlocked" : "paid";
		List<String> body = this.screen.wrap("This is a " + kind + " schematic handled by our partner - "
				+ this.entry.partnerName() + ". Head to their Discord to unlock or purchase it there.",
				cardWidth - pad * 2, 4);

		int titleToBody = 12;
		int bodyToButtons = 16;
		int buttonHeight = 16;
		int bodyHeight = body.size() * (line + 1);
		int cardHeight = pad + line + titleToBody + bodyHeight + bodyToButtons + buttonHeight + pad;

		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.goldGradientText(ctx, font,
				Theme.clipBold(font, "Handled by " + this.entry.partnerName(), cardWidth - pad * 2), x + pad, y + pad, true);

		int bodyY = y + pad + line + titleToBody;

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + pad, bodyY, Theme.TEXT);
			bodyY += line + 1;
		}

		int buttonY = bodyY - 1 + bodyToButtons;
		int bw = (cardWidth - pad * 2 - 8) / 2;
		this.close.set(x + pad, buttonY, bw, buttonHeight);
		this.openDiscord.set(x + pad + bw + 8, buttonY, bw, buttonHeight);
		Buttons.pill(ctx, font, this.close, "Close", mouseX, mouseY, false);
		Buttons.gold(ctx, font, this.openDiscord, "Open Discord", mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.close.contains(mouseX, mouseY))
		{
			this.close();
			Theme.click(0.9F);
			return true;
		}

		if (this.openDiscord.contains(mouseX, mouseY) && this.entry != null)
		{
			String url = this.entry.partnerUrl();
			this.close();
			this.screen.openLink(url);
			return true;
		}

		if (!this.bounds.contains(mouseX, mouseY))
		{
			this.close();
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

		this.close();
		Theme.click(0.9F);
		return true;
	}
}

package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OverwriteConfirm
{
	private final IndexScreen screen;
	private @Nullable SchematicEntry pending;
	private final Rect replaceButton = new Rect();
	private final Rect cancelButton = new Rect();
	private final Rect bounds = new Rect();

	public OverwriteConfirm(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.pending != null;
	}

	public void open(SchematicEntry entry)
	{
		this.pending = entry;
		this.screen.modalFocus = -1;
		Theme.click(0.9F);
	}

	public void close()
	{
		this.pending = null;
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		SchematicEntry entry = this.pending;

		if (entry == null)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int cardWidth = 240;
		int pad = 12;
		List<String> lines = this.screen.wrap("A schematic with this name is already in your download folder.",
				cardWidth - pad * 2, 3);
		int cardHeight = pad + font.lineHeight + 4 + font.lineHeight + 4 + lines.size() * (font.lineHeight + 1)
				+ 10 + 16 + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);

		Theme.text(ctx, font, Theme.bold("Replace existing file?"), x + pad, y + pad, Theme.TEXT);

		int textY = y + pad + font.lineHeight + 4;

		String name = Theme.clip(font, entry.title() + ".litematic", cardWidth - pad * 2);
		Theme.text(ctx, font, name, x + pad, textY, Theme.ACCENT_BRIGHT);

		int lineY = textY + font.lineHeight + 4;

		for (String line : lines)
		{
			Theme.text(ctx, font, line, x + pad, lineY, Theme.TEXT_MUTE);
			lineY += font.lineHeight + 1;
		}

		int buttonY = y + cardHeight - pad - 16;
		int buttonWidth = (cardWidth - pad * 2 - 6) / 2;
		this.cancelButton.set(x + pad, buttonY, buttonWidth, 16);
		this.replaceButton.set(x + pad + buttonWidth + 6, buttonY, buttonWidth, 16);

		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.replaceButton, "Replace", mouseX, mouseY, true);
	}

	public void mouseClicked(double mouseX, double mouseY)
	{
		if (this.replaceButton.contains(mouseX, mouseY))
		{
			this.confirm();
		}
		else if (this.cancelButton.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.cancel();
		}
	}

	public void confirm()
	{
		SchematicEntry entry = this.pending;
		this.pending = null;

		if (entry != null)
		{
			this.screen.beginDownload(entry);
		}
	}

	public void cancel()
	{
		this.pending = null;
		this.screen.status = "";
	}

	public Rect[] focusButtons()
	{
		return new Rect[]{this.replaceButton, this.cancelButton};
	}

	public void activateFocus(Rect button, int index)
	{
		if (index == 0)
		{
			this.confirm();
		}
		else
		{
			this.cancel();
		}
	}

	public Rect[] pressButtons()
	{
		return new Rect[]{this.replaceButton, this.cancelButton};
	}
}

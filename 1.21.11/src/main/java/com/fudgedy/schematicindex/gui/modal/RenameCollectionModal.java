package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.TextBoxes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

public class RenameCollectionModal
{
	private final IndexScreen screen;
	private boolean open;
	private @Nullable String from;
	private String input = "";
	private final Rect confirmButton = new Rect();
	private final Rect cancelButton = new Rect();
	private final Rect bounds = new Rect();

	public RenameCollectionModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(String from)
	{
		this.open = true;
		this.from = from;
		this.input = from;
		this.screen.modalFocus = -1;
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
		int cardHeight = pad + line + 10 + IndexScreen.FIELD_HEIGHT + 14 + IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.text(ctx, font, Theme.bold("Rename collection"), x + pad, y + pad, Theme.TEXT);

		String renameCount = this.input.length() + " / 40";
		Theme.text(ctx, font, renameCount, x + cardWidth - pad - font.width(renameCount), y + pad, Theme.TEXT_ASH);

		int fieldY = y + pad + line + 10;
		int fieldWidth = cardWidth - pad * 2;
		TextBoxes.underscoreLine(ctx, font, x + pad, fieldY, fieldWidth, IndexScreen.FIELD_HEIGHT, this.input, "Name",
				true);

		int btnY = fieldY + IndexScreen.FIELD_HEIGHT + 14;
		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		int renameWidth = font.width(Theme.bold("Rename")) + 20;
		this.cancelButton.set(x + pad, btnY, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.confirmButton.set(x + cardWidth - pad - renameWidth, btnY, renameWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.confirmButton, "Rename", mouseX, mouseY, true);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.confirmButton.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.confirm();
		}
		else if (this.cancelButton.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
			this.from = null;
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (IndexScreen.isPasteChord(event))
		{
			this.input = this.screen.pasteInto(this.input, 40);
			return true;
		}

		switch (event.key())
		{
			case 259 -> {
				if (!this.input.isEmpty())
				{
					this.input = this.input.substring(0, this.input.length() - 1);
				}
			}
			case 257, 335 -> this.confirm();
			case 256 -> {
				this.open = false;
				this.from = null;
			}
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

		if (event.codepoint() >= ' ' && this.input.length() < 40)
		{
			this.input += event.codepointAsString();
		}

		return true;
	}

	public Rect[] focusButtons()
	{
		return new Rect[]{this.confirmButton, this.cancelButton};
	}

	public void activateFocus(Rect button, int index)
	{
		if (index == 0)
		{
			this.confirm();
		}
		else
		{
			this.open = false;
			this.from = null;
		}
	}

	private void confirm()
	{
		if (this.from == null)
		{
			return;
		}

		String cleaned = CollectionStore.normalize(this.input);

		if (!cleaned.isEmpty() && CollectionStore.rename(this.from, cleaned))
		{
			if (this.from.equals(this.screen.activeCollection))
			{
				this.screen.activeCollection = cleaned;
			}

			this.screen.layoutChips();
			this.screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight;
			this.screen.refilter();
		}

		this.open = false;
		this.from = null;
	}
}

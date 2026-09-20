package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DeleteCollectionModal
{
	private final IndexScreen screen;
	private boolean open;
	private @Nullable String name;
	private final Rect confirmButton = new Rect();
	private final Rect cancelButton = new Rect();
	// Captured during render so a click on the scrim outside it dismisses the modal
	private final Rect bounds = new Rect();

	public DeleteCollectionModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(@Nullable String name)
	{
		this.name = name;
		this.open = true;
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
		int cardWidth = Math.min(this.screen.width - 40, 340);
		int line = font.lineHeight;
		String message = "Are you sure you want to delete \"" + this.name
				+ "\"? It will be lost forever - a very long time.";
		List<String> lines = this.screen.wrap(message, cardWidth - pad * 2 - 4, 4);

		int cardHeight = pad + line + 8 + lines.size() * (line + 2) + 14 + IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, true);

		int tx = x + pad + 4;
		int ty = y + pad;
		Theme.text(ctx, font, Theme.bold("Delete collection"), tx, ty, Theme.TEXT);
		ty += line + 8;

		for (String row : lines)
		{
			Theme.text(ctx, font, row, tx, ty, Theme.TEXT_ASH);
			ty += line + 2;
		}

		int btnY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		int deleteWidth = font.width(Theme.bold("Delete")) + 20;
		this.cancelButton.set(x + pad, btnY, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.confirmButton.set(x + cardWidth - pad - deleteWidth, btnY, deleteWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
		Buttons.danger(ctx, font, this.confirmButton, "Delete", mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.confirmButton.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.confirm();
		}
		else if (this.cancelButton.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.cancel();
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
			this.name = null;
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
			this.cancel();
		}
	}

	// Shared by activateFocus and mouseClicked so the two paths cannot drift apart
	private void confirm()
	{
		if (this.name != null)
		{
			CollectionStore.delete(this.name);

			if (this.name.equals(this.screen.activeCollection))
			{
				this.screen.activeCollection = null;
			}
		}

		this.open = false;
		this.name = null;
		this.screen.layoutChips();
		this.screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight;
		this.screen.refilter();
	}

	private void cancel()
	{
		this.open = false;
		this.name = null;
	}
}

package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

public class CollectionOptionsModal
{
	private final IndexScreen screen;
	private boolean open;
	private @Nullable String name;
	private final Rect rename = new Rect();
	private final Rect delete = new Rect();
	private final Rect cancel = new Rect();
	private final Rect bounds = new Rect();

	public CollectionOptionsModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(String name)
	{
		this.open = true;
		this.name = name;
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
		int cardHeight = pad + line + 14 + IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.text(ctx, font, Theme.bold(Theme.clip(font, this.name, cardWidth - pad * 2)), x + pad, y + pad,
				Theme.TEXT);

		int btnY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		int renameWidth = font.width(Theme.bold("Rename")) + 20;
		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		int deleteWidth = font.width(Theme.bold("Delete")) + 20;
		this.rename.set(x + pad, btnY, renameWidth, IndexScreen.FIELD_HEIGHT);
		this.cancel.set(x + pad + renameWidth + 8, btnY, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.delete.set(x + cardWidth - pad - deleteWidth, btnY, deleteWidth, IndexScreen.FIELD_HEIGHT);

		Buttons.pill(ctx, font, this.rename, "Rename", mouseX, mouseY, true);
		Buttons.pill(ctx, font, this.cancel, "Cancel", mouseX, mouseY, false);
		Buttons.danger(ctx, font, this.delete, "Delete", mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.rename.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			String name = this.name;
			this.open = false;

			if (name != null)
			{
				this.screen.renameCollectionModal.open(name);
			}
		}
		else if (this.delete.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
			this.screen.deleteCollectionModal.open(this.name);
		}
		else if (this.cancel.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
			this.name = null;
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
		return new Rect[]{this.rename, this.cancel, this.delete};
	}

	public void activateFocus(Rect button, int index)
	{
		String name = this.name;

		if (index == 0 && name != null)
		{
			this.open = false;
			this.screen.renameCollectionModal.open(name);
		}
		else if (index == 2)
		{
			this.open = false;
			this.screen.deleteCollectionModal.open(name);
		}
		else
		{
			this.open = false;
			this.name = null;
		}
	}
}

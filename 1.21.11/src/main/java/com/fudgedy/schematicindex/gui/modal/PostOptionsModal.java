package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

public class PostOptionsModal
{
	private final IndexScreen screen;
	private boolean open;
	private @Nullable String id;
	private @Nullable String title;
	private final Rect edit = new Rect();
	private final Rect unpublish = new Rect();
	private final Rect cancel = new Rect();
	private final Rect bounds = new Rect();

	public PostOptionsModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(String id)
	{
		SchematicEntry entry = null;

		for (SchematicEntry e : this.screen.myPosts)
		{
			if (e.id().equals(id))
			{
				entry = e;
				break;
			}
		}

		if (entry == null)
		{
			return;
		}

		this.open = true;
		this.id = id;
		this.title = entry.title();
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
		Theme.text(ctx, font, Theme.bold(Theme.clip(font, this.title, cardWidth - pad * 2)), x + pad, y + pad,
				Theme.TEXT);

		int btnY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		int editWidth = font.width(Theme.bold("Edit")) + 20;
		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		int unpubWidth = font.width(Theme.bold("Unpublish")) + 20;
		this.cancel.set(x + pad, btnY, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.edit.set(x + (cardWidth - editWidth) / 2, btnY, editWidth, IndexScreen.FIELD_HEIGHT);
		this.unpublish.set(x + cardWidth - pad - unpubWidth, btnY, unpubWidth, IndexScreen.FIELD_HEIGHT);

		Buttons.pill(ctx, font, this.edit, "Edit", mouseX, mouseY, true);
		Buttons.pill(ctx, font, this.cancel, "Cancel", mouseX, mouseY, false);
		Buttons.danger(ctx, font, this.unpublish, "Unpublish", mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.edit.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			String id = this.id;
			this.open = false;

			if (id != null)
			{
				this.screen.openEditPost(id);
			}
		}
		else if (this.unpublish.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
			this.screen.unpublishModal.open(this.id, this.title);
		}
		else if (this.cancel.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
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
}

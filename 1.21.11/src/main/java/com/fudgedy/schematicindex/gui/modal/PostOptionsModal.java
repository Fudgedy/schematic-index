package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

public class PostOptionsModal
{
	private static final int WIDTH = 208;
	private static final int KEY_ESCAPE = 256;

	private final IndexScreen screen;
	private boolean open;
	private long openedAt;
	private @Nullable String id;
	private @Nullable String title;
	private String subtitle = "";
	private final Rect edit = new Rect();
	private final Rect unpublish = new Rect();
	private final Rect cancel = new Rect();
	private final Rect close = new Rect();
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

		for (SchematicEntry e : this.screen.dashboardPage.stats.posts)
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
		this.openedAt = System.currentTimeMillis();
		this.id = id;
		this.title = entry.title();
		this.subtitle = entry.category().label() + " · Published " + entry.agoLabel();
		this.screen.modalFocus = -1;
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		int line = font.lineHeight;
		int buttonsHeight = Theme.H_CONTROL * 3 + Theme.SPACE_S * 2;
		int height = Theme.SPACE_M + Theme.ICON_M + Theme.SPACE_XS + line + Theme.SPACE_L + buttonsHeight
				+ Theme.SPACE_L;
		int titleWidth = Math.min(WIDTH, this.screen.width - Theme.SPACE_L * 2) - Theme.SPACE_L * 2 - Theme.ICON_M
				- Theme.SPACE_S;
		String name = this.title == null ? "" : this.title;
		String shown = Theme.clipBold(font, name, titleWidth);

		ModalChrome.open(ctx, font, this.bounds, this.screen.width, this.screen.height, WIDTH, height, shown, null,
				this.close, this.openedAt, mouseX, mouseY);
		int x = this.bounds.x;
		int y = this.bounds.y;
		int w = this.bounds.width;
		int headerY = y + Theme.SPACE_M;

		if (!shown.equals(name) && Theme.inside(mouseX, mouseY, x + Theme.SPACE_L, headerY, titleWidth, Theme.ICON_M))
		{
			Tooltip.show(name);
		}

		Theme.text(ctx, font, Theme.clip(font, this.subtitle, w - Theme.SPACE_L * 2), x + Theme.SPACE_L,
				headerY + Theme.ICON_M + Theme.SPACE_XS, Theme.TEXT_ASH);

		int buttonW = w - Theme.SPACE_L * 2;
		int buttonX = x + Theme.SPACE_L;
		int cancelY = y + this.bounds.height - Theme.SPACE_L - Theme.H_CONTROL;
		int unpublishY = cancelY - Theme.SPACE_S - Theme.H_CONTROL;
		int editY = unpublishY - Theme.SPACE_S - Theme.H_CONTROL;
		this.edit.set(buttonX, editY, buttonW, Theme.H_CONTROL);
		this.unpublish.set(buttonX, unpublishY, buttonW, Theme.H_CONTROL);
		this.cancel.set(buttonX, cancelY, buttonW, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.edit, "Edit", Buttons.Kind.PRIMARY, true, mouseX, mouseY);
		Buttons.button(ctx, font, this.unpublish, "Unpublish", Buttons.Kind.DANGER, true, mouseX, mouseY);
		Buttons.button(ctx, font, this.cancel, "Cancel", Buttons.Kind.SECONDARY, true, mouseX, mouseY);
		Tooltip.render(ctx, font, mouseX, mouseY, this.screen.width, this.screen.height);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.edit.contains(mouseX, mouseY))
		{
			this.doEdit();
		}
		else if (this.unpublish.contains(mouseX, mouseY))
		{
			this.doUnpublish();
		}
		else if (this.cancel.contains(mouseX, mouseY) || this.close.contains(mouseX, mouseY)
				|| !this.bounds.contains(mouseX, mouseY))
		{
			this.doCancel();
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (event.key() == KEY_ESCAPE)
		{
			this.open = false;
		}

		return true;
	}

	public Rect[] focusButtons()
	{
		return new Rect[]{this.edit, this.unpublish, this.cancel};
	}

	public void activateFocus(Rect button, int index)
	{
		if (index == 0)
		{
			this.doEdit();
		}
		else if (index == 1)
		{
			this.doUnpublish();
		}
		else
		{
			this.doCancel();
		}
	}

	// Shared by activateFocus and mouseClicked so the two paths cannot drift apart
	private void doEdit()
	{
		Theme.click(1.1F);
		String id = this.id;
		this.open = false;

		if (id != null)
		{
			this.screen.openEditPost(id);
		}
	}

	private void doUnpublish()
	{
		Theme.click(0.9F);
		this.open = false;
		this.screen.unpublishModal.open(this.id, this.title);
	}

	private void doCancel()
	{
		Theme.click(0.9F);
		this.open = false;
	}
}

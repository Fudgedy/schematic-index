package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UnpublishModal
{
	private final IndexScreen screen;
	private boolean open;
	private @Nullable String id;
	private @Nullable String title;
	private final Rect confirmButton = new Rect();
	private final Rect cancelButton = new Rect();
	private final Rect bounds = new Rect();

	public UnpublishModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open(@Nullable String id, @Nullable String title)
	{
		this.id = id;
		this.title = title;
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
		List<String> lines = this.screen.wrap("Unpublish \"" + this.title
				+ "\"? It will be removed from the index. You cannot undo this action.", cardWidth - pad * 2 - 4, 4);
		int cardHeight = pad + line + 8 + lines.size() * (line + 2) + 14 + IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, true);

		int tx = x + pad + 4;
		int ty = y + pad;
		Theme.text(ctx, font, Theme.bold("Unpublish post"), tx, ty, Theme.TEXT);
		ty += line + 8;

		for (String row : lines)
		{
			Theme.text(ctx, font, row, tx, ty, Theme.TEXT_ASH);
			ty += line + 2;
		}

		int btnY = y + cardHeight - pad - IndexScreen.FIELD_HEIGHT;
		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		int confirmWidth = font.width(Theme.bold("Unpublish")) + 20;
		this.cancelButton.set(x + pad, btnY, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.confirmButton.set(x + cardWidth - pad - confirmWidth, btnY, confirmWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
		Buttons.danger(ctx, font, this.confirmButton, "Unpublish", mouseX, mouseY);
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
		else if (this.cancelButton.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
			this.id = null;
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
			this.id = null;
		}

		return true;
	}

	private void confirm()
	{
		String code = UploaderAccess.code();
		String id = this.id;
		this.open = false;

		if (code == null || id == null)
		{
			return;
		}

		Thread worker = new Thread(() -> {
			Backend.ApiResult result = Backend.unpublishPost(code, id);
			Minecraft.getInstance().execute(() -> {
				if (result.ok())
				{
					this.screen.myStatsLoaded = false;
					Catalogue.refresh();
					Toasts.push("Post unpublished", "No one can view it any longer.", new ItemStack(Items.BARRIER));
				}
				else
				{
					this.screen.showError(Errors.POST_EDIT);
				}
			});
		}, "schematicindex-unpublish");
		worker.setDaemon(true);
		worker.start();
	}
}

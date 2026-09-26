package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.update.UpdateGate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// Stays up for as long as the Index is open: the only ways out are updating or leaving the menu
public class UpdateRequiredModal implements Overlay
{
	private static final int PAD = 18;
	private static final String BODY = "This version no longer works with the catalogue. Update to keep browsing.";

	private final IndexScreen screen;
	private boolean open;
	private final Rect update = new Rect();
	private final Rect close = new Rect();

	public UpdateRequiredModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	@Override
	public boolean isOpen()
	{
		return this.open;
	}

	public void open()
	{
		if (this.open)
		{
			return;
		}

		this.open = true;
		this.screen.detailView.close();
		Usage.once("update_required");
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int width = 300;
		int inner = width - PAD * 2;
		int line = font.lineHeight;
		List<String> body = this.screen.wrap(BODY, inner, 4);
		String footer = "Version " + UpdateGate.currentVersion() + " no longer works with the catalogue. ("
				+ Errors.UPDATE_REQUIRED + ")";
		List<String> footerLines = this.screen.wrap(footer, inner, 3);
		int height = PAD + line + 12 + body.size() * (line + 2) + 14 + 18 + 12 + footerLines.size() * (line + 1) + PAD;
		int x = (this.screen.width - width) / 2;
		int y = (this.screen.height - height) / 2;

		ModalChrome.frame(ctx, x, y, width, height, true);

		int ty = y + PAD;
		Theme.itemScaled(ctx, new ItemStack(Items.WRITABLE_BOOK), x + PAD, ty - 1, 0.65F);
		Theme.text(ctx, font, Theme.bold("Update required"), x + PAD + 16, ty, Theme.TEXT);
		ty += line + 12;

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + PAD, ty, Theme.TEXT_MUTE);
			ty += line + 2;
		}

		ty += 14;
		int half = (inner - 6) / 2;
		this.close.set(x + PAD, ty, half, 18);
		this.update.set(x + width - PAD - half, ty, half, 18);
		Buttons.pill(ctx, font, this.close, "Close menu", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.update, "Open Modrinth", mouseX, mouseY, true);
		ty += 18 + 12;

		for (String row : footerLines)
		{
			Theme.text(ctx, font, row, x + PAD, ty, Theme.TEXT_ASH);
			ty += line + 1;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.update.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.screen.openLink(UpdateGate.downloadUrl());
		}
		else if (this.close.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.screen.onClose();
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == 256)
		{
			this.screen.onClose();
		}

		return true;
	}
}

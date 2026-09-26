package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// Players who download but never place a build are the quietest churn, so the steps are spelled out
public class LoadGuideModal implements Overlay
{
	private static final int PAD = 16;
	private static final int WIDTH = 300;
	private static final String[][] STEPS = {
			{"Open Litematica", "In game, press M to open the Litematica menu."},
			{"Load Schematics", "Choose Load Schematics and pick the file you just downloaded."},
			{"Place it", "Press Load. The build appears as a hologram you can move into place and build over."}};
	private static final ItemStack[] ICONS = {new ItemStack(Items.BOOK), new ItemStack(Items.FILLED_MAP),
			new ItemStack(Items.STRUCTURE_BLOCK)};

	private final IndexScreen screen;
	private boolean open;
	private final Rect done = new Rect();

	public LoadGuideModal(IndexScreen screen)
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
		this.open = true;
		Usage.once("coach_download_guide");
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int inner = WIDTH - PAD * 2;
		int line = font.lineHeight;
		int rowsHeight = 0;

		for (String[] step : STEPS)
		{
			rowsHeight += rowHeight(this.screen.wrap(step[1], inner - 30, 3).size(), line) + 8;
		}

		int height = PAD + line + 12 + rowsHeight + 6 + 18 + PAD;
		int x = (this.screen.width - WIDTH) / 2;
		int y = (this.screen.height - height) / 2;
		ModalChrome.frame(ctx, x, y, WIDTH, height, false);

		int ty = y + PAD;
		Theme.text(ctx, font, Theme.bold("Loading a download"), x + PAD, ty, Theme.TEXT);
		ty += line + 12;

		for (int i = 0; i < STEPS.length; i++)
		{
			List<String> body = this.screen.wrap(STEPS[i][1], inner - 30, 3);
			int rowHeight = rowHeight(body.size(), line);
			Theme.roundedRect(ctx, x + PAD, ty, inner, rowHeight, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
			Theme.itemScaled(ctx, ICONS[i], x + PAD + 5, ty + (rowHeight - 16) / 2, 1.0F);
			Theme.text(ctx, font, Theme.bold((i + 1) + ". " + STEPS[i][0]), x + PAD + 28, ty + 5, Theme.TEXT);
			int textY = ty + 5 + line + 2;

			for (String row : body)
			{
				Theme.text(ctx, font, row, x + PAD + 28, textY, Theme.TEXT_MUTE);
				textY += line + 1;
			}

			ty += rowHeight + 8;
		}

		int doneWidth = font.width(Theme.bold("Got it")) + 20;
		this.done.set(x + WIDTH - PAD - doneWidth, ty + 6, doneWidth, 18);
		Buttons.pill(ctx, font, this.done, "Got it", mouseX, mouseY, true);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.done.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.open = false;
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == 256 || event.key() == 257 || event.key() == 335)
		{
			this.open = false;
		}

		return true;
	}

	private static int rowHeight(int lines, int lineHeight)
	{
		return Math.max(26, 5 + lineHeight + 2 + lines * (lineHeight + 1) + 4);
	}
}

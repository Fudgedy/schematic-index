package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.update.UpdateGate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Header reminder while a newer release exists; the launch toast is easy to miss, this is not
public class UpdatePill
{
	private static final String LABEL = "Update available";

	private final IndexScreen screen;
	private final Rect rect = new Rect();

	public UpdatePill(IndexScreen screen)
	{
		this.screen = screen;
	}

	// Sits left of the given header button and gives way rather than overlap the search box
	public void layout(Rect rightNeighbour)
	{
		if (!UpdateGate.shouldPrompt() || UpdateGate.isBlocked())
		{
			this.rect.set(0, 0, 0, 0);
			return;
		}

		Font font = this.screen.font();
		int width = font.width(Theme.bold(LABEL)) + 14;
		int x = rightNeighbour.x - 6 - width;
		int searchRight = this.screen.contentX + (this.screen.contentWidth + this.screen.searchWidth()) / 2;

		if (x < searchRight + 6)
		{
			this.rect.set(0, 0, 0, 0);
			return;
		}

		this.rect.set(x, rightNeighbour.y, width, 16);
	}

	public Rect rect()
	{
		return this.rect;
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (this.rect.width == 0)
		{
			return;
		}

		Buttons.pill(ctx, this.screen.font(), this.rect, LABEL, mouseX, mouseY, true);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.rect.contains(mouseX, mouseY))
		{
			return false;
		}

		Usage.once("update_pill_click");
		this.screen.openLink(UpdateGate.downloadUrl());
		return true;
	}
}

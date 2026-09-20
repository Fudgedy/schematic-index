package com.fudgedy.schematicindex.gui.mapart;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.mapart.MapPalette;
import com.fudgedy.schematicindex.mapart.MapartResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;
import java.util.Map;

// The materials column: the block totals of the current conversion, or the most any one map needs
public final class MapartMaterialsPanel
{
	private static final int ROW_GAP = 6;
	private static final int ROW_HEIGHT = 18;
	private static final int ICON = 16;
	private static final float NAME_SCALE = 0.85F;

	private Font font;
	public final Rect bounds = new Rect();
	private final Rect listBounds = new Rect();
	private final Rect maxToggle = new Rect();
	private final Rect saveButton = new Rect();
	private float scroll;
	private float maxScroll;

	public void render(GuiGraphicsExtractor ctx, Font font, int x, int top, int width, int bottom, int mouseX, int mouseY)
	{
		this.font = font;
		this.bounds.set(x, top, width, bottom - top);
		int y = top;

		Theme.text(ctx, font, Theme.bold("Materials"), x, y, Theme.TEXT);
		y += font.lineHeight + ROW_GAP;

		this.maxToggle.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, font, this.maxToggle, "Only show max mats per split", MapartSession.maxPerSplit, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + 2;

		for (String row : MapartSettingsPanel.wrap(font, "For material reuse with locked maps", width))
		{
			Theme.text(ctx, font, row, x, y, Theme.TEXT_ASH);
			y += font.lineHeight + 1;
		}

		y += ROW_GAP;
		this.saveButton.set(x, y, width, IndexScreen.FIELD_HEIGHT);

		if (MapartSession.ready())
		{
			Buttons.pill(ctx, font, this.saveButton, "Save materials list", mouseX, mouseY, false);
		}
		else
		{
			Buttons.disabled(ctx, font, this.saveButton, "Save materials list");
		}

		y += IndexScreen.FIELD_HEIGHT + ROW_GAP + 2;
		this.renderList(ctx, x, y, width, bottom);
	}

	private void renderList(GuiGraphicsExtractor ctx, int x, int top, int width, int bottom)
	{
		this.listBounds.set(x, top, width, Math.max(0, bottom - top));
		MapartResult result = MapartSession.result;

		if (result == null || bottom <= top)
		{
			this.maxScroll = 0.0F;
			return;
		}

		Map<String, Integer> materials = MapartSession.maxPerSplit ? result.materialsPerMap() : result.materials();
		int rowHeight = ROW_HEIGHT;
		ctx.enableScissor(x, top, x + width, bottom);
		int y = top - Math.round(this.scroll);

		for (Map.Entry<String, Integer> entry : materials.entrySet())
		{
			if (y + rowHeight >= top && y <= bottom)
			{
				String count = String.format(Locale.ROOT, "%,d", entry.getValue());
				int countWidth = this.font.width(count);
				int textY = y + (rowHeight - this.font.lineHeight) / 2 + 1;
				Theme.itemScaled(ctx, MapartBlockPicker.icon(entry.getKey()), x, y + (rowHeight - ICON) / 2, 1.0F);
				// The name is clipped in its own scale so the shrunken text still stops short of the count
				int nameRoom = Math.round((width - ICON - 4 - countWidth - 6) / NAME_SCALE);
				String name = Theme.clip(this.font, MapPalette.displayName(entry.getKey()), nameRoom);
				Theme.textScaled(ctx, this.font, name, x + ICON + 4, textY + 1, NAME_SCALE, Theme.TEXT_MUTE);
				Theme.text(ctx, this.font, count, x + width - countWidth, textY, Theme.TEXT);
			}

			y += rowHeight;
		}

		int total = 0;

		for (int value : materials.values())
		{
			total += value;
		}

		if (y + rowHeight >= top && y <= bottom)
		{
			String count = String.format(Locale.ROOT, "%,d", total);
			Theme.text(ctx, this.font, Theme.bold("Total"), x, y + 2, Theme.TEXT);
			Theme.text(ctx, this.font, Theme.bold(count), x + width - this.font.width(Theme.bold(count)), y + 2, Theme.TEXT);
		}

		ctx.disableScissor();
		this.maxScroll = Math.max(0.0F, (materials.size() + 1) * rowHeight + 2 - (bottom - top));
		this.scroll = Math.min(this.scroll, this.maxScroll);
		MapartUi.scrollbar(ctx, x + width + 3, top, bottom - top, this.scroll, this.maxScroll);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.bounds.contains(mouseX, mouseY))
		{
			return false;
		}

		if (this.maxToggle.contains(mouseX, mouseY))
		{
			MapartUi.press(this.maxToggle);
			MapartSession.maxPerSplit = !MapartSession.maxPerSplit;
			this.scroll = 0.0F;
			return true;
		}

		if (this.saveButton.contains(mouseX, mouseY) && MapartSession.ready())
		{
			MapartUi.press(this.saveButton);
			MapartSession.saveMaterials();
			return true;
		}

		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (!this.listBounds.contains(mouseX, mouseY))
		{
			return false;
		}

		this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}
}

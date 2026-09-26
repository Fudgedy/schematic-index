package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

// The upload form's picture list: a row of thumbnails with a cover badge on the first. Click a tile to
// select it, then set it as the cover or remove it
final class UploadPictures
{
	private final UploadPage page;
	private final List<Rect> tiles = new ArrayList<>();
	private final Rect coverButton = new Rect();
	private final Rect removeButton = new Rect();
	private int selected = -1;

	UploadPictures(UploadPage page)
	{
		this.page = page;
	}

	// Returns the y under the row, or under the action row when a picture is selected
	int render(GuiGraphicsExtractor ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		if (this.selected >= this.page.pictures.size())
		{
			this.selected = -1;
		}

		int tileWidth = (width - Theme.SPACE_XS * 4) / 5;
		int tileHeight = tileWidth * 9 / 16;

		for (int i = 0; i < UploadFiles.MAX_PICTURES; i++)
		{
			Rect tile = Rect.pooled(this.tiles, i);
			int tileX = x + i * (tileWidth + Theme.SPACE_XS);
			tile.set(0, 0, 0, 0);

			if (i >= this.page.pictures.size())
			{
				Theme.roundedRect(ctx, tileX, y, tileWidth, tileHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
				Theme.roundedOutline(ctx, tileX, y, tileWidth, tileHeight, Theme.RADIUS_CARD, Theme.HAIRLINE);
				continue;
			}

			tile.set(tileX, y, tileWidth, tileHeight);
			Identifier texture = ImageStore.thumbnail(this.page.pictureStart + i);

			if (texture != null)
			{
				Theme.image(ctx, texture, tileX, y, tileWidth, tileHeight);
			}
			else
			{
				Theme.loadingPlaceholder(ctx, tileX, y, tileWidth, tileHeight);
			}

			boolean hovered = tile.contains(mouseX, mouseY);
			int outline = i == this.selected ? Theme.ACCENT_BRIGHT : (hovered ? Theme.HAIRLINE_STRONG : Theme.HAIRLINE);
			Theme.roundedOutline(ctx, tileX, y, tileWidth, tileHeight, Theme.RADIUS_CARD, outline);

			if (i == 0)
			{
				String cover = "Cover";
				int badgeWidth = font.width(cover) + Theme.SPACE_XS * 2;
				Theme.roundedRect(ctx, tileX + Theme.SPACE_2XS, y + Theme.SPACE_2XS, badgeWidth, Theme.H_BADGE,
						Theme.RADIUS_PILL, Theme.ACCENT_TINT);
				Theme.text(ctx, font, cover, tileX + Theme.SPACE_2XS + Theme.SPACE_XS, y + Theme.SPACE_2XS + 2,
						Theme.ACCENT_BRIGHT);
			}
		}

		int rowY = y + tileHeight + Theme.SPACE_S;

		if (this.selected < 0)
		{
			this.coverButton.set(0, 0, 0, 0);
			this.removeButton.set(0, 0, 0, 0);
			return rowY;
		}

		boolean isCover = this.selected == 0;
		int coverWidth = Buttons.width(font, "Set as cover");
		int removeWidth = Buttons.width(font, "Remove");
		this.coverButton.set(x, rowY, coverWidth, Theme.H_CONTROL);
		this.removeButton.set(x + coverWidth + Theme.SPACE_S, rowY, removeWidth, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.coverButton, isCover ? "Cover" : "Set as cover", Buttons.Kind.SECONDARY,
				!isCover, mouseX, mouseY);
		Buttons.button(ctx, font, this.removeButton, "Remove", Buttons.Kind.GHOST, true, mouseX, mouseY);
		return rowY + Theme.H_CONTROL;
	}

	void reset()
	{
		this.selected = -1;
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		for (int i = 0; i < this.page.pictures.size() && i < this.tiles.size(); i++)
		{
			if (this.tiles.get(i).contains(mouseX, mouseY))
			{
				this.selected = this.selected == i ? -1 : i;
				Theme.click();
				return true;
			}
		}

		if (this.selected < 0)
		{
			return false;
		}

		if (this.coverButton.contains(mouseX, mouseY))
		{
			this.page.files.setCover(this.selected);
			this.selected = 0;
			Theme.click(1.1F);
			return true;
		}

		if (this.removeButton.contains(mouseX, mouseY))
		{
			this.page.files.removePicture(this.selected);
			this.selected = -1;
			Theme.click(0.9F);
			return true;
		}

		return false;
	}
}

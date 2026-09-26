package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

// Photo Mode's unsaved shots: the full PNG for the upload plus a small texture for the strip, freed on removal
final class PhotoStrip
{
	static final int PREVIEW_WIDTH = 320;
	static final int PREVIEW_HEIGHT = 180;
	static final long NO_PHOTO = -1L;
	private static final long CONFIRM_MS = 3000L;

	private static int nextTextureId;

	private final List<Photo> photos = new ArrayList<>();
	private final List<Rect> tiles = new ArrayList<>();
	private final Rect coverButton = new Rect();
	private final Rect retakeButton = new Rect();
	private final Rect deleteButton = new Rect();
	private int selected = -1;
	private long deleteArmedAt;
	private long nextPhotoId;
	boolean coverChosen;
	// The photo a retake in flight will overwrite; its actions stay disabled until the capture lands
	long retaking = NO_PHOTO;

	int size()
	{
		return this.photos.size();
	}

	List<byte[]> pngs()
	{
		List<byte[]> pngs = new ArrayList<>();

		for (Photo photo : this.photos)
		{
			pngs.add(photo.png());
		}

		return pngs;
	}

	// Takes ownership of preview
	void add(byte[] png, NativeImage preview)
	{
		this.photos.add(new Photo(this.nextPhotoId++, png, register(preview)));
	}

	// Takes ownership of preview only when the photo still exists
	boolean replace(long id, byte[] png, NativeImage preview)
	{
		for (int i = 0; i < this.photos.size(); i++)
		{
			Photo photo = this.photos.get(i);

			if (photo.id() == id)
			{
				release(photo.texture());
				this.photos.set(i, new Photo(id, png, register(preview)));
				return true;
			}
		}

		return false;
	}

	void clear()
	{
		for (Photo photo : this.photos)
		{
			release(photo.texture());
		}

		this.photos.clear();
		this.selected = -1;
		this.deleteArmedAt = 0L;
		this.coverChosen = false;
		this.retaking = NO_PHOTO;
	}

	// Tiles for every free slot, then the selected photo's actions; returns the y under the action row
	int render(GuiGraphicsExtractor ctx, Font font, int x, int y, int tileWidth, int capacity, boolean showCover,
			int mouseX, int mouseY)
	{
		int tileHeight = tileWidth * 9 / 16;

		for (int i = 0; i < capacity; i++)
		{
			Rect tile = Rect.pooled(this.tiles, i);
			int tileX = x + i * (tileWidth + Theme.SPACE_S);

			if (i >= this.photos.size())
			{
				tile.set(0, 0, 0, 0);
				Theme.roundedRect(ctx, tileX, y, tileWidth, tileHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
				Theme.roundedOutline(ctx, tileX, y, tileWidth, tileHeight, Theme.RADIUS_CARD, Theme.HAIRLINE);
				String empty = Integer.toString(i + 1);
				Theme.text(ctx, font, empty, tileX + (tileWidth - font.width(empty)) / 2,
						y + (tileHeight - font.lineHeight) / 2 + 1, Theme.TEXT_ASH);
				continue;
			}

			tile.set(tileX, y, tileWidth, tileHeight);

			Theme.image(ctx, this.photos.get(i).texture(), tile.x, tile.y, tileWidth, tileHeight,
					PREVIEW_WIDTH, PREVIEW_HEIGHT);
			boolean hovered = tile.contains(mouseX, mouseY);
			int outline = i == this.selected ? Theme.ACCENT_BRIGHT : (hovered ? Theme.HAIRLINE_STRONG : Theme.HAIRLINE);
			Theme.roundedOutline(ctx, tile.x, tile.y, tileWidth, tileHeight, Theme.RADIUS_CARD, outline);

			if (i == 0 && showCover)
			{
				String cover = "Cover";
				int badgeWidth = font.width(cover) + Theme.SPACE_XS * 2;
				int badgeX = tile.x + Theme.SPACE_XS;
				int badgeY = tile.y + Theme.SPACE_XS;
				Theme.roundedRect(ctx, badgeX, badgeY, badgeWidth, Theme.H_BADGE, Theme.RADIUS_PILL, Theme.ACCENT_TINT);
				Theme.text(ctx, font, cover, badgeX + Theme.SPACE_XS, badgeY + 2, Theme.ACCENT_BRIGHT);
			}
		}

		int rowY = y + tileHeight + Theme.SPACE_S;

		if (this.selected < 0 || this.selected >= this.photos.size())
		{
			this.coverButton.set(0, 0, 0, 0);
			this.retakeButton.set(0, 0, 0, 0);
			this.deleteButton.set(0, 0, 0, 0);
			String hint = this.photos.isEmpty()
					? "Photos you take land here, up to " + capacity + "."
					: "Click a photo to make it the cover, retake or delete it.";
			Theme.text(ctx, font, hint, x, rowY + (Theme.H_CONTROL - font.lineHeight) / 2 + 1, Theme.TEXT_ASH);
			return rowY + Theme.H_CONTROL;
		}

		boolean isCover = this.selected == 0 && showCover;
		boolean locked = this.isLocked();
		boolean armed = this.deleteArmed();
		int coverWidth = Buttons.width(font, "Set as cover");
		int retakeWidth = Buttons.width(font, "Retake");
		int deleteWidth = Math.max(Buttons.width(font, "Delete"), Buttons.width(font, "Confirm?"));
		this.coverButton.set(x, rowY, coverWidth, Theme.H_CONTROL);
		this.retakeButton.set(x + coverWidth + Theme.SPACE_S, rowY, retakeWidth, Theme.H_CONTROL);
		this.deleteButton.set(this.retakeButton.x + retakeWidth + Theme.SPACE_S, rowY, deleteWidth, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.coverButton, isCover ? "Cover" : "Set as cover", Buttons.Kind.SECONDARY,
				!isCover && !locked, mouseX, mouseY);
		Buttons.button(ctx, font, this.retakeButton, locked ? "..." : "Retake", Buttons.Kind.SECONDARY, !locked,
				mouseX, mouseY);

		if (armed && !locked)
		{
			Buttons.danger(ctx, font, this.deleteButton, "Confirm?", mouseX, mouseY);
		}
		else
		{
			Buttons.button(ctx, font, this.deleteButton, "Delete", Buttons.Kind.GHOST, !locked, mouseX, mouseY);
		}

		return rowY + Theme.H_CONTROL;
	}

	// retake receives the id of the photo to reshoot; the owner applies its own rate limit
	boolean mouseClicked(double mouseX, double mouseY, boolean showCover, LongConsumer retake)
	{
		for (int i = 0; i < this.photos.size() && i < this.tiles.size(); i++)
		{
			if (this.tiles.get(i).contains(mouseX, mouseY))
			{
				this.selected = this.selected == i ? -1 : i;
				this.deleteArmedAt = 0L;
				Theme.click();
				return true;
			}
		}

		boolean onAction = this.coverButton.contains(mouseX, mouseY) || this.retakeButton.contains(mouseX, mouseY)
				|| this.deleteButton.contains(mouseX, mouseY);

		if (onAction && this.isLocked())
		{
			return true;
		}

		if (this.coverButton.contains(mouseX, mouseY))
		{
			if (!(this.selected == 0 && showCover))
			{
				this.photos.add(0, this.photos.remove(this.selected));
				this.selected = 0;
				this.coverChosen = true;
				Theme.buttonPress(this.coverButton);
				Theme.click(1.1F);
			}

			return true;
		}

		if (this.retakeButton.contains(mouseX, mouseY))
		{
			Theme.buttonPress(this.retakeButton);
			retake.accept(this.photos.get(this.selected).id());
			return true;
		}

		if (!this.deleteButton.contains(mouseX, mouseY))
		{
			return false;
		}

		if (!this.deleteArmed())
		{
			this.deleteArmedAt = System.currentTimeMillis();
			Theme.click();
			return true;
		}

		release(this.photos.remove(this.selected).texture());
		this.selected = -1;
		this.deleteArmedAt = 0L;
		Theme.click(0.9F);
		return true;
	}

	private boolean isLocked()
	{
		return this.retaking != NO_PHOTO && this.selected >= 0 && this.selected < this.photos.size()
				&& this.photos.get(this.selected).id() == this.retaking;
	}

	private boolean deleteArmed()
	{
		return this.deleteArmedAt != 0L && System.currentTimeMillis() - this.deleteArmedAt <= CONFIRM_MS;
	}

	private static Identifier register(NativeImage preview)
	{
		Identifier id = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "photo/" + nextTextureId++);
		Minecraft.getInstance().getTextureManager().register(id,
				new DynamicTexture(() -> "schematicindex-photo", preview));
		return id;
	}

	// Releasing closes the DynamicTexture, which frees both the GPU texture and its NativeImage
	private static void release(Identifier id)
	{
		Minecraft.getInstance().getTextureManager().release(id);
	}

	private record Photo(long id, byte[] png, Identifier texture)
	{
	}
}

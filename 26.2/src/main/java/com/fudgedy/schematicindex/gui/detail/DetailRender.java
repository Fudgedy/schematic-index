package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.catalogue.Follows;
import com.fudgedy.schematicindex.catalogue.Premium;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Stars;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

// The detail card's picture panel and the meta column beside it
class DetailRender
{
	private final DetailView view;
	private final DetailBody body;
	private SchematicEntry postedByFor;
	private int postedByRoom;
	private Component postedBy = Component.empty();

	DetailRender(DetailView view)
	{
		this.view = view;
		this.body = new DetailBody(view);
	}

	void render(GuiGraphicsExtractor ctx, SchematicEntry entry, int mouseX, int mouseY)
	{
		IndexScreen screen = this.view.screen;
		long open = System.currentTimeMillis() - this.view.openedAt;
		float fade = Math.min(1.0F, Math.max(0.0F, open / 140.0F));
		ctx.fill(0, 0, screen.width, screen.height, ((int) (0x99 * fade) << 24));

		int modalWidth = Math.min(screen.width - 24, this.view.model ? 860 : 688);
		int modalHeight = Math.min(screen.height - 24, this.view.model ? 453 : 363);
		int x = (screen.width - modalWidth) / 2;
		int y = (screen.height - modalHeight) / 2;
		int pad = 12;
		this.view.bounds.set(x, y, modalWidth, modalHeight);

		Theme.roundedRect(ctx, x, y, modalWidth, modalHeight, Theme.RADIUS_MODAL, Theme.SURFACE_ELEVATED);

		int imageWidth = Math.round((modalWidth - pad * 3) * (this.view.model ? 0.64F : 0.56F));
		int imageHeight = IndexScreen.imageHeight(imageWidth);
		int infoX = x + pad + imageWidth + pad;
		int infoWidth = modalWidth - (infoX - x) - pad;
		DetailView.Layout layout = new DetailView.Layout(x, y, modalWidth, modalHeight, pad, imageWidth,
				imageHeight, infoX, infoWidth);

		this.renderImage(ctx, entry, mouseX, mouseY, layout);
		this.renderInfo(ctx, entry, mouseX, mouseY, layout);
		this.body.render(ctx, entry, mouseX, mouseY, layout);
	}

	private void renderImage(GuiGraphicsExtractor ctx, SchematicEntry entry, int mouseX, int mouseY,
			DetailView.Layout layout)
	{
		IndexScreen screen = this.view.screen;
		Font font = screen.font();
		DetailCamera camera = this.view.camera;
		int x = layout.x();
		int y = layout.y();
		int pad = layout.pad();
		int imageWidth = layout.imageWidth();
		int imageHeight = layout.imageHeight();

		if (this.view.model)
		{
			// The picture-mode Copy PNG hotspot must not linger from a prior frame
			this.view.copyPngButton.set(0, 0, 0, 0);
			SchematicPreview.request(entry.schematicSlot(), camera.yaw, camera.pitch, camera.zoom,
					camera.cutaway, camera.freeLook, camera.freeEye, camera.layer);
			Identifier model = SchematicPreview.texture(entry.schematicSlot());

			if (model != null)
			{
				ctx.fill(x + pad, y + pad, x + pad + imageWidth, y + pad + imageHeight, 0xFF10151A);

				// The GPU path hands back a bottom-left-origin framebuffer texture; the CPU path is top-left
				if (SchematicPreview.textureFlippedV())
				{
					Theme.imageFlippedV(ctx, model, x + pad, y + pad, imageWidth, imageHeight,
							SchematicPreview.renderWidth(), SchematicPreview.renderHeight());
				}
				else
				{
					Theme.image(ctx, model, x + pad, y + pad, imageWidth, imageHeight,
							SchematicPreview.renderWidth(), SchematicPreview.renderHeight());
				}

				// Sits inside imageRect, so its click is handled ahead of the orbit-drag start
				String pngLabel = "Save PNG";
				int pngW = font.width(Theme.bold(pngLabel)) + 12;
				int pngX = x + pad + 4;
				int pngY = y + pad + imageHeight - 16;
				this.view.savePngButton.set(pngX, pngY, pngW, 12);
				boolean pngHover = this.view.savePngButton.contains(mouseX, mouseY);
				Theme.roundedRect(ctx, pngX, pngY, pngW, 12, Theme.RADIUS_PILL, pngHover ? 0xEE000000 : 0xCC0F1114);
				Theme.text(ctx, font, Theme.bold(pngLabel), pngX + 6, pngY + 2, Theme.TEXT);
			}
			else
			{
				this.view.savePngButton.set(0, 0, 0, 0);
				Theme.blueprintPlaceholder(ctx, x + pad, y + pad, imageWidth, imageHeight);
				int slot = entry.schematicSlot();
				String message;

				if (!SchematicPreview.hasSchematic(slot))
				{
					message = "No schematic files found";
				}
				else if (SchematicPreview.failed(slot))
				{
					message = (SchematicPreview.hosted(slot)
							? "Can't connect. Click to retry."
							: "Couldn't load preview. Click to retry.") + " (" + Errors.PREVIEW + ")";
				}
				else
				{
					String stage = SchematicPreview.loadingStage();
					String base = stage.isEmpty() ? "Rendering" : stage;
					message = base + ".".repeat((int) (System.currentTimeMillis() / 400 % 3) + 1);
				}

				Theme.text(ctx, font, message,
						x + pad + (imageWidth - font.width(message)) / 2,
						y + pad + imageHeight / 2 - 4, Theme.TEXT_MUTE);
			}
		}
		else
		{
			this.view.savePngButton.set(0, 0, 0, 0);
			Identifier texture = screen.imageTexture(entry, this.view.image);

			if (texture != null)
			{
				Theme.image(ctx, texture, x + pad, y + pad, imageWidth, imageHeight);

				// Only for a post image with a ref readable back from the cache, never the 3D preview
				String copyRef = screen.currentImageRef(entry);

				if (copyRef != null)
				{
					String copyLabel = "Copy PNG";
					int copyW = font.width(Theme.bold(copyLabel)) + 12;
					int copyX = x + pad + 4;
					int copyY = y + pad + imageHeight - 16;
					this.view.copyPngButton.set(copyX, copyY, copyW, 12);
					boolean copyHover = this.view.copyPngButton.contains(mouseX, mouseY);
					Theme.roundedRect(ctx, copyX, copyY, copyW, 12, Theme.RADIUS_PILL,
							copyHover ? 0xEE000000 : 0xCC0F1114);
					Theme.text(ctx, font, Theme.bold(copyLabel), copyX + 6, copyY + 2, Theme.TEXT);

					// Premium listings are not posts, so they have no share page
					if (this.view.isPremium())
					{
						this.view.copyLinkButton.set(0, 0, 0, 0);
					}
					else
					{
						String linkLabel = "Share";
						int linkW = font.width(Theme.bold(linkLabel)) + 12;
						int linkX = copyX + copyW + 4;
						this.view.copyLinkButton.set(linkX, copyY, linkW, 12);
						boolean linkHover = this.view.copyLinkButton.contains(mouseX, mouseY);
						Theme.roundedRect(ctx, linkX, copyY, linkW, 12, Theme.RADIUS_PILL,
								linkHover ? 0xEE000000 : 0xCC0F1114);
						Theme.text(ctx, font, Theme.bold(linkLabel), linkX + 6, copyY + 2, Theme.TEXT);
					}
				}
				else
				{
					this.view.copyPngButton.set(0, 0, 0, 0);
					this.view.copyLinkButton.set(0, 0, 0, 0);
				}
			}
			else
			{
				this.view.copyPngButton.set(0, 0, 0, 0);
				this.view.copyLinkButton.set(0, 0, 0, 0);
				Theme.loadingPlaceholder(ctx, x + pad, y + pad, imageWidth, imageHeight);
			}
		}

		this.view.imageRect.set(x + pad, y + pad, imageWidth, imageHeight);

		if (!this.view.model && entry.imageCount() > 1)
		{
			this.view.prev.set(x + pad + 2, y + pad + imageHeight / 2 - 8, 12, 16);
			this.view.next.set(x + pad + imageWidth - 14, y + pad + imageHeight / 2 - 8, 12, 16);
			Buttons.arrow(ctx, this.view.prev, true, mouseX, mouseY);
			Buttons.arrow(ctx, this.view.next, false, mouseX, mouseY);
		}
		else
		{
			this.view.prev.set(0, 0, 0, 0);
			this.view.next.set(0, 0, 0, 0);
		}

		String caption = this.view.model
				? ""
				: (entry.imageCount() > 1 ? (this.view.image + 1) + "/" + entry.imageCount() : "");

		if (!caption.isEmpty())
		{
			int captionWidth = font.width(caption) + 8;
			Theme.roundedRect(ctx, x + pad + imageWidth - captionWidth - 3, y + pad + imageHeight - 14,
					captionWidth, 11, Theme.RADIUS_PILL, 0xCC0F1114);
			Theme.text(ctx, font, caption, x + pad + imageWidth - captionWidth + 1,
					y + pad + imageHeight - 12, Theme.TEXT);
		}
	}

	// The prefix keeps the line's colour; only the name carries the poster's gradient
	private Component postedBy(Font font, SchematicEntry entry, int room)
	{
		if (this.postedByFor == entry && this.postedByRoom == room)
		{
			return this.postedBy;
		}

		String prefix = "Posted by ";
		Component line;

		if (!entry.hasPosterStyle())
		{
			line = Component.literal(Theme.clip(font, prefix + entry.poster(), room));
		}
		else
		{
			String name = Theme.clip(font, entry.poster(), room - font.width(prefix));
			line = Component.literal(prefix).append(ModTags.creator(name, entry.posterStops(), Style.EMPTY));
		}

		this.postedByFor = entry;
		this.postedByRoom = room;
		this.postedBy = line;
		return line;
	}

	private void renderInfo(GuiGraphicsExtractor ctx, SchematicEntry entry, int mouseX, int mouseY,
			DetailView.Layout layout)
	{
		IndexScreen screen = this.view.screen;
		Font font = screen.font();
		int x = layout.x();
		int y = layout.y();
		int pad = layout.pad();
		int modalWidth = layout.width();
		int infoX = layout.infoX();
		int infoWidth = layout.infoWidth();
		int line = y + pad;

		String age = entry.agoLabel();
		int ageWidth = font.width(age);
		Theme.text(ctx, font, age, x + modalWidth - pad - ageWidth, y + pad, Theme.TEXT_ASH);

		boolean updateAvailable = screen.isUpdateAvailable(entry);

		if (updateAvailable)
		{
			int badge = font.width("Update") + 8;
			int badgeX = x + modalWidth - pad - badge;
			int badgeY = y + pad + font.lineHeight + 2;
			Theme.roundedRect(ctx, badgeX - 1, badgeY - 1, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, badgeX, badgeY, badge, 11, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
			Theme.text(ctx, font, "Update", badgeX + 4, badgeY + 2, Theme.ON_ACCENT);
		}

		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, entry.title(), infoWidth - ageWidth - 6)),
				infoX, line, Theme.TEXT);
		line += font.lineHeight + 11;

		// A premium listing belongs to a partner, not a poster, so it drops follow / profile / posted-by
		if (this.view.isPremium())
		{
			this.view.follow.set(0, 0, 0, 0);
			this.view.viewProfile.set(0, 0, 0, 0);
			this.view.posterRect.set(0, 0, 0, 0);
		}
		else
		{
			if (this.view.followConfirm && System.currentTimeMillis() - this.view.followConfirmAt > 3000L)
			{
				this.view.followConfirm = false;
			}

			boolean following = Follows.isFollowing(entry.poster());
			String followLabel = !following ? "Follow" : (this.view.followConfirm ? "Unfollow?" : "Following");
			int followWidth = font.width(Theme.bold(followLabel)) + 12;
			int viewWidth = font.width(Theme.bold("View Profile")) + 12;
			this.view.follow.set(infoX + infoWidth - followWidth, line - 1, followWidth, 12);
			this.view.viewProfile.set(this.view.follow.x - 6 - viewWidth, line - 1, viewWidth, 12);
			Buttons.pill(ctx, font, this.view.follow, followLabel, mouseX, mouseY,
					following && !this.view.followConfirm);
			Buttons.pill(ctx, font, this.view.viewProfile, "View Profile", mouseX, mouseY, false);

			int postedRoom = Math.max(20, this.view.viewProfile.x - infoX - 6);
			Component postedBy = this.postedBy(font, entry, postedRoom);
			this.view.posterRect.set(infoX, line, Math.min(font.width(postedBy), postedRoom), font.lineHeight);
			boolean posterHover = this.view.posterRect.contains(mouseX, mouseY);
			Theme.text(ctx, font, postedBy, infoX, line, posterHover ? Theme.ACCENT_BRIGHT : Theme.TEXT_MUTE);
			line += font.lineHeight + 8;

			if (screen.staff != null)
			{
				line += screen.staff.renderDetailControls(ctx, entry, infoX, line, infoWidth, mouseX, mouseY);
			}
		}

		// The chip reserves its width first, so the designer name is clipped to what is left
		boolean claimable = !this.view.isPremium() && this.view.canShowClaim(entry);
		String claimLabel = "Claim credit";
		int claimChipWidth = font.width(Theme.bold(claimLabel)) + 12;
		int designerRoom = claimable ? Math.max(24, infoWidth - claimChipWidth - 6) : infoWidth;
		String designerText = Theme.clip(font, "Designed by " + entry.designer(), designerRoom);
		Theme.text(ctx, font, designerText, infoX, line, Theme.TEXT_MUTE);

		if (claimable)
		{
			int chipX = Math.min(infoX + font.width(designerText) + 6, infoX + infoWidth - claimChipWidth);
			this.view.claim.set(chipX, line - 2, claimChipWidth, font.lineHeight + 4);
			Buttons.pill(ctx, font, this.view.claim, claimLabel, mouseX, mouseY, false);
		}
		else
		{
			this.view.claim.set(0, 0, 0, 0);
		}

		// Paid and boost listings credit their partner and link the Discord; a shard listing does neither
		if (this.view.isPremium() && this.view.premium.type() != Premium.Type.SHARD)
		{
			line += font.lineHeight + 4;
			int joinWidth = font.width(Theme.bold("Join Discord")) + 14;
			this.view.joinDiscord.set(infoX + infoWidth - joinWidth, line - 3, joinWidth, font.lineHeight + 5);
			Buttons.gold(ctx, font, this.view.joinDiscord, "Join Discord", mouseX, mouseY);

			String featured = Theme.clipBold(font, "Featured by " + this.view.premium.partnerName(),
					infoWidth - joinWidth - 6);
			Theme.goldGradientText(ctx, font, featured, infoX, line, true);
		}
		else if (this.view.isPremium())
		{
			this.view.joinDiscord.set(0, 0, 0, 0);
			line += font.lineHeight + 4;
			String price = Integer.toString(this.view.premium.shardPrice());
			Theme.itemScaled(ctx, new ItemStack(Items.AMETHYST_SHARD), infoX, line - 3, 0.65F);
			Theme.text(ctx, font, Theme.bold(price), infoX + 14, line, Theme.SHARD);
		}
		else
		{
			this.view.joinDiscord.set(0, 0, 0, 0);
		}

		line += font.lineHeight + 16;

		// Premium keeps the size and view stats but drops downloads and likes; owner-entered zero size hides
		boolean premium = this.view.isPremium();
		boolean hasSize = entry.sizeX() * entry.sizeY() * entry.sizeZ() > 0;

		if (!premium || hasSize)
		{
			line = screen.metaRow(ctx, "Dimensions", entry.dimensionsLabel(), infoX, line, infoWidth) + 8;
		}
		if (!premium || entry.blockCount() > 0)
		{
			line = screen.metaRow(ctx, "Total Blocks", entry.blockCountLabel(), infoX, line, infoWidth) + 8;
		}
		if (!premium || hasSize)
		{
			line = screen.metaRow(ctx, "Volume", entry.volumeLabel(), infoX, line, infoWidth) + 8;
		}

		Theme.text(ctx, font, "Views", infoX, line, Theme.TEXT_ASH);
		String views = SchematicEntry.compact(entry.views());
		int viewsWidth = font.width(views);
		Theme.text(ctx, font, views, infoX + infoWidth - viewsWidth, line, Theme.TEXT);
		Theme.eyeGlyph(ctx, infoX + infoWidth - viewsWidth - Theme.EYE_GLYPH_WIDTH - 3, line + 2, Theme.TEXT_MUTE);
		line += font.lineHeight + 9;

		if (!premium)
		{
			Theme.text(ctx, font, "Downloads", infoX, line, Theme.TEXT_ASH);
			String downloads = entry.downloadsLabel();
			int downloadsWidth = font.width(downloads);
			Theme.text(ctx, font, downloads, infoX + infoWidth - downloadsWidth, line, Theme.TEXT);
			Theme.downloadGlyph(ctx, infoX + infoWidth - downloadsWidth - Theme.DOWNLOAD_GLYPH_WIDTH - 3,
					line + 2, Theme.TEXT_MUTE);
			line += font.lineHeight + 9;
		}

		if (this.view.isPremium())
		{
			this.view.heart.set(0, 0, 0, 0);
		}
		else
		{
			boolean liked = IndexScreen.isLikedBy(entry);
			String likeCount = SchematicEntry.compact(IndexScreen.likesOf(entry));
			Theme.text(ctx, font, "Likes", infoX, line, Theme.TEXT_ASH);
			int likeWidth = font.width(likeCount);
			Theme.text(ctx, font, likeCount, infoX + infoWidth - likeWidth, line,
					liked ? Theme.ACCENT_BRIGHT : Theme.TEXT);
			this.view.heart.set(infoX + infoWidth - likeWidth - IndexScreen.HEART_SIZE - 4, line - 1,
					IndexScreen.HEART_SIZE, IndexScreen.HEART_SIZE);
			Theme.heartPopped(ctx, this.view.heart.x, this.view.heart.y, liked, IndexScreen.popAge(entry));
			line += font.lineHeight + 9;
		}

		if (!this.view.isPremium())
		{
			Theme.text(ctx, font, "Avg stars", infoX, line, Theme.TEXT_ASH);
			String avg = this.view.starCount > 0 ? String.format(Locale.ROOT, "%.1f", this.view.starAvg) : "-";
			int avgWidth = font.width(avg);
			Theme.text(ctx, font, avg, infoX + infoWidth - avgWidth, line, Theme.TEXT);
			Theme.textScaled(ctx, font, Stars.FULL, infoX + infoWidth - avgWidth - 9, line, 0.9F, Theme.STAT_STARS);
			line += font.lineHeight + 9;

			Theme.text(ctx, font, "Total stars", infoX, line, Theme.TEXT_ASH);
			String total = this.view.starCount > 0
					? Stars.format(this.view.starAvg * this.view.starCount) : "0";
			Theme.text(ctx, font, total, infoX + infoWidth - font.width(total), line, Theme.TEXT);
			line += font.lineHeight + 9;

			float starScale = 2.0F;
			int starRowWidth = 5 * Stars.cellWidth(font, starScale);
			int starRowHeight = Math.round(font.lineHeight * starScale);
			Theme.text(ctx, font, "Your rating", infoX, line + (starRowHeight - font.lineHeight) / 2,
					Theme.TEXT_ASH);
			Stars.draw(ctx, font, infoX + infoWidth - starRowWidth, line, this.view.myStars, starScale, true,
					mouseX, mouseY, this.view.starRects);
			line += starRowHeight + 6;
		}
		else
		{
			for (int s = 0; s < this.view.starRects.length; s++)
			{
				this.view.starRects[s].set(0, 0, 0, 0);
			}
		}

		if (this.view.model)
		{
			this.view.camera.renderControls(ctx, font, infoX, line, infoWidth, mouseX, mouseY);
		}
		else
		{
			this.view.camera.clearControls();
		}
	}
}

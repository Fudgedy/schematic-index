package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Fields;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

class UploadView
{
	private final UploadPage page;
	private final IndexScreen screen;
	// Rebuilt only when a form field feeding it changes
	private SchematicEntry cachedPreview;
	private String cachedPreviewKey;
	// Shared instances: render runs every frame and Buttons.mock only reads them
	private final Rect previewFollowRect = new Rect();
	private final Rect previewCloseRect = new Rect();
	private final Rect previewSaveRect = new Rect();
	private final Rect previewDownloadRect = new Rect();
	private final Rect previewPreview3dRect = new Rect();

	UploadView(UploadPage page, IndexScreen screen)
	{
		this.page = page;
		this.screen = screen;
	}

	void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick)
	{
		Font font = this.screen.font();
		int formWidth = Math.min(this.screen.contentWidth, 300);
		int formX = this.screen.contentX + (this.screen.contentWidth - formWidth) / 2;
		int y = IndexScreen.TOP_BAR_HEIGHT + 18;
		// Only the open form draws it, and a stale rect over the dashboard would wipe the form unseen
		this.page.clearButton.set(0, 0, 0, 0);

		if (!UploaderAccess.unlocked())
		{
			Theme.textScaled(ctx, font, "Share your builds", formX, y, 1.0F, Theme.TEXT);
			y += 14;

			for (String line : this.screen.wrap("Verify your Minecraft account to post. Every upload is reviewed "
					+ "by staff before it goes live.", formWidth, 3))
			{
				Theme.text(ctx, font, line, formX, y, Theme.TEXT_MUTE);
				y += font.lineHeight + 1;
			}

			y += 6;
			this.page.verifyButton.set(formX, y, font.width(Theme.bold("Verify")) + 24, IndexScreen.FIELD_HEIGHT);
			Buttons.pill(ctx, font, this.page.verifyButton, "Verify", mouseX, mouseY, true);
			y += IndexScreen.FIELD_HEIGHT + 14;

			Theme.text(ctx, font, "Have an access code?", formX, y, Theme.TEXT_ASH);
			y += font.lineHeight + 4;
			Fields.single(ctx, this.page.codeBox, formX, y, formWidth, IndexScreen.FIELD_HEIGHT,
					mouseX, mouseY, partialTick);
			y += IndexScreen.FIELD_HEIGHT + 6;

			this.page.unlockButton.set(formX, y, 70, IndexScreen.FIELD_HEIGHT);
			Buttons.pill(ctx, font, this.page.unlockButton, "Unlock", mouseX, mouseY, false);
			y += IndexScreen.FIELD_HEIGHT + 8;

			if (!this.page.status.isEmpty())
			{
				Theme.text(ctx, font, Theme.clip(font, this.page.status, formWidth), formX, y,
						this.page.statusError ? Theme.DANGER_TEXT : Theme.ACCENT_BRIGHT);
				y += font.lineHeight + 6;
			}

			return;
		}

		if (!this.page.open)
		{
			this.screen.dashboardPage.render(ctx, mouseX, mouseY, partialTick);
			return;
		}

		int margin = 18;
		int areaX = this.screen.contentX + margin;
		int areaW = this.screen.contentWidth - margin * 2;
		int top = IndexScreen.TOP_BAR_HEIGHT + 16;

		this.page.backButton.set(areaX, top - 4, 52, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.page.backButton, "< Back", mouseX, mouseY, false);
		// Sized for the confirm label, so arming it never moves the edge under the cursor
		int clearWidth = Math.max(Buttons.width(font, UploadPage.CLEAR_LABEL), Buttons.width(font, UploadPage.CLEAR_CONFIRM_LABEL));
		this.page.clearButton.set(this.page.backButton.x + this.page.backButton.width + Theme.SPACE_S, top - 4, clearWidth,
				IndexScreen.FIELD_HEIGHT);

		if (this.page.clearArmed() && this.page.canClear())
		{
			Buttons.danger(ctx, font, this.page.clearButton, UploadPage.CLEAR_CONFIRM_LABEL, mouseX, mouseY);
		}
		else
		{
			Buttons.button(ctx, font, this.page.clearButton, UploadPage.CLEAR_LABEL, Buttons.Kind.DANGER, this.page.canClear(),
					mouseX, mouseY);
		}
		this.page.signOutButton.set(0, 0, 0, 0);

		if (UploaderAccess.hasCode())
		{
			this.page.signOutButton.set(areaX + areaW - 58, top - 4, 58, IndexScreen.FIELD_HEIGHT);
			// "Confirm?" fits the existing pill width, so the adjacent "+ New post" button does not shift
			Buttons.pill(ctx, font, this.page.signOutButton, (this.page.signOutConfirmAt != 0L
					&& System.currentTimeMillis() - this.page.signOutConfirmAt <= UploadPage.SIGN_OUT_CONFIRM_MS)
					? "Confirm?" : "Sign out", mouseX, mouseY, false);
		}

		int gap = 24;
		int leftW = Math.min(258, (areaW - gap) * 44 / 100);
		int leftX = areaX;
		int rightX = areaX + leftW + gap;
		int rightW = areaX + areaW - rightX;

		// Pinned under the header rather than in the scrolling body, so a message can never grow off
		// screen; the slot is reserved whether or not there is a message, so the form beneath does not jump
		int statusY = top + IndexScreen.FIELD_HEIGHT + 2;

		if (!this.page.status.isEmpty())
		{
			Theme.text(ctx, font, Theme.clip(font, this.page.status, areaW), areaX, statusY,
					this.page.statusError ? Theme.DANGER_TEXT : Theme.ACCENT_BRIGHT);
		}

		// The header row stays put; both columns scroll beneath it on a window too short for the form
		int viewTop = statusY + font.lineHeight + Theme.SPACE_S;
		int viewBottom = this.screen.height - IndexScreen.OUTER_MARGIN;
		this.page.bodyTop = viewTop;
		this.page.bodyBottom = viewBottom;
		ctx.enableScissor(this.screen.contentX, viewTop, this.screen.contentX + this.screen.contentWidth, viewBottom);

		int startY = viewTop - Math.round(this.screen.scroll);
		y = startY;

		Theme.text(ctx, font, "Schematic name", leftX, y, Theme.TEXT_ASH);
		Theme.text(ctx, font, "on the post page",
				leftX + leftW - font.width("on the post page"), y, Theme.TEXT_ASH);
		y += font.lineHeight + 3;
		Fields.single(ctx, this.page.titleBox, leftX, y, leftW, IndexScreen.FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += IndexScreen.FIELD_HEIGHT + 12;

		Theme.text(ctx, font, "Thumbnail name", leftX, y, Theme.TEXT_ASH);
		Theme.text(ctx, font, "on the card", leftX + leftW - font.width("on the card"), y, Theme.TEXT_ASH);
		y += font.lineHeight + 3;
		Fields.single(ctx, this.page.thumbnailBox, leftX, y, leftW, IndexScreen.FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += IndexScreen.FIELD_HEIGHT + 12;

		Theme.text(ctx, font, "Designed by", leftX, y, Theme.TEXT_ASH);
		y += font.lineHeight + 3;
		Fields.single(ctx, this.page.designerBox, leftX, y, leftW, IndexScreen.FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += IndexScreen.FIELD_HEIGHT + 12;

		this.page.descriptionBox = FormFields.ensureMultiline(this.screen, this.page.descriptionBox, leftW,
				IndexScreen.DESC_FIELD_HEIGHT);
		Theme.text(ctx, font, "Description", leftX, y, Theme.TEXT_ASH);
		String uploadCount = this.page.descriptionBox.getValue().length() + " / " + IndexScreen.DESC_CHAR_LIMIT;
		Theme.text(ctx, font, uploadCount, leftX + leftW - font.width(uploadCount), y, Theme.TEXT_ASH);
		y += font.lineHeight + 3;
		Fields.multiline(ctx, this.page.descriptionBox, this.page.descriptionBounds, leftX, y, leftW,
				IndexScreen.DESC_FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += IndexScreen.DESC_FIELD_HEIGHT + 14;

		this.page.categoryButton.set(leftX, y, leftW, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.page.categoryButton, "Category: " + this.page.category.label(), mouseX, mouseY, false);
		y += IndexScreen.FIELD_HEIGHT + 14;

		Theme.text(ctx, font, "Schematic file", leftX, y, Theme.TEXT_ASH);
		y += font.lineHeight + 3;
		this.page.schematicButton.set(leftX, y, leftW, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.page.schematicButton,
				this.page.schematic == null ? "Choose .litematic" : "Change file", mouseX, mouseY, false);
		y += IndexScreen.FIELD_HEIGHT + 4;

		String chosen = this.page.schematic == null ? "No file chosen" : this.page.schematic.getFileName().toString();
		int chosenColor = this.page.schematic == null ? Theme.TEXT_ASH
				: (this.page.schematicDuplicate ? Theme.DANGER_TEXT : Theme.ACCENT_BRIGHT);
		Theme.text(ctx, font, Theme.clip(font, chosen, leftW), leftX, y, chosenColor);
		y += font.lineHeight + 6;

		if (this.page.duplicateChecking)
		{
			Theme.text(ctx, font, "Checking for duplicates...", leftX, y, Theme.TEXT_ASH);
			y += font.lineHeight + 6;
		}
		else if (this.page.schematicDuplicate)
		{
			Theme.text(ctx, font, "Already uploaded - pick a different file.", leftX, y, Theme.DANGER_TEXT);
			y += font.lineHeight + 6;
		}
		else if (!this.page.duplicateCheckNote.isEmpty())
		{
			Theme.text(ctx, font, Theme.clip(font, this.page.duplicateCheckNote, leftW), leftX, y, Theme.TEXT_ASH);
			y += font.lineHeight + 6;
		}

		this.page.photoModeButton.set(leftX, y, leftW, IndexScreen.FIELD_HEIGHT);
		Buttons.button(ctx, font, this.page.photoModeButton, "3D Image Selector", Buttons.Kind.SECONDARY,
				this.page.schematic != null, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + Theme.SPACE_XS;
		Theme.text(ctx, font, "Take pictures of your build in 3D.", leftX, y, Theme.TEXT_MUTE);
		y += font.lineHeight + 14;

		String pictureCount = this.page.pictures.isEmpty() ? "1-5 images" : this.page.pictures.size() + " selected";
		Theme.text(ctx, font, "Pictures", leftX, y, Theme.TEXT_ASH);
		Theme.text(ctx, font, pictureCount, leftX + leftW - font.width(pictureCount), y, Theme.TEXT_ASH);
		y += font.lineHeight + 3;
		this.page.picturesButton.set(leftX, y, leftW, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.page.picturesButton, "Upload Pictures", mouseX, mouseY, false);
		y += IndexScreen.FIELD_HEIGHT + Theme.SPACE_S;
		y = this.page.pictureList.render(ctx, font, leftX, y, leftW, mouseX, mouseY);
		y += Theme.SPACE_L;

		this.page.postButton.set(leftX, y, leftW, IndexScreen.FIELD_HEIGHT);

		if (this.page.uploading)
		{
			Buttons.uploading(ctx, font, this.page.postButton, this.page.startedAt);
		}
		else if (this.page.schematicDuplicate)
		{
			Buttons.disabled(ctx, font, this.page.postButton, "Post");
		}
		else
		{
			Buttons.pill(ctx, font, this.page.postButton, "Post", mouseX, mouseY, true);
		}

		y += IndexScreen.FIELD_HEIGHT + 8;

		for (String line : this.screen.wrap("Ctrl+V to paste an image or discord link to a schematic", leftW, 2))
		{
			Theme.text(ctx, font, line, leftX, y, Theme.TEXT_MUTE);
			y += font.lineHeight + 2;
		}

		y += 5;

		if (this.page.uploading)
		{
			long elapsed = System.currentTimeMillis() - this.page.startedAt;

			if (this.page.fileSize > 8L * 1024 * 1024 || elapsed > 10_000L)
			{
				for (String line : this.screen.wrap("This is a large schematic file, uploading may take a while.", leftW, 2))
				{
					Theme.text(ctx, font, line, leftX, y, Theme.TEXT_ASH);
					y += font.lineHeight + 1;
				}
			}
		}

		SchematicEntry preview = this.previewEntry();
		int ry = startY;

		Theme.text(ctx, font, Theme.bold("Post Thumbnail"), rightX, ry, Theme.TEXT_MUTE);
		ry += font.lineHeight + 6;

		int cardPreviewWidth = Math.min(rightW, 138);
		int savedCardWidth = this.screen.cardWidth;
		int savedCardHeight = this.screen.cardHeight;
		this.screen.cardWidth = cardPreviewWidth;
		this.screen.cardHeight = IndexScreen.imageHeight(cardPreviewWidth) + IndexScreen.CAPTION_HEIGHT;

		// An exception mid-render must not leave the shared grid card size corrupted for later frames
		try
		{
			this.screen.browsePage.grid.renderCard(ctx, preview, rightX, ry, -999, -999);
			ry += this.screen.cardHeight + 16;
		}
		finally
		{
			this.screen.cardWidth = savedCardWidth;
			this.screen.cardHeight = savedCardHeight;
		}

		Theme.text(ctx, font, Theme.bold("In the post"), rightX, ry, Theme.TEXT_MUTE);
		ry += font.lineHeight + 6;
		ry = this.renderDetailPreview(ctx, preview, rightX, ry, rightW, mouseX, mouseY);

		ctx.disableScissor();

		int contentHeight = Math.max(y, ry) + IndexScreen.OUTER_MARGIN - startY;
		this.screen.maxScroll = Math.max(0.0F, contentHeight - (viewBottom - viewTop));
		this.screen.scroll = Math.min(this.screen.scroll, this.screen.maxScroll);
		this.screen.verticalScrollbar(ctx, IndexScreen.SCROLLBAR_MAIN, this.screen.scroll, this.screen.maxScroll, viewTop,
				viewBottom - viewTop, this.screen.contentX + this.screen.contentWidth);
	}

	private SchematicEntry previewEntry()
	{
		String title = this.page.titleBox.getValue().trim();

		if (title.isEmpty())
		{
			title = "Your build";
		}

		String poster = UploaderAccess.profile() == null ? "you" : UploaderAccess.profile();
		String designer = this.page.designerBox.getValue().trim();
		String thumbnail = this.page.thumbnailBox.getValue().trim();
		String description = this.page.descriptionBox.getValue().trim();

		String key = title + '\0' + poster + '\0' + designer + '\0' + thumbnail + '\0'
				+ this.page.category + '\0' + this.page.sizeX + '\0' + this.page.sizeY + '\0'
				+ this.page.sizeZ + '\0' + this.page.blockCount + '\0' + description + '\0'
				+ this.page.pictures.size() + '\0' + this.page.pictureStart;

		if (this.cachedPreview != null && key.equals(this.cachedPreviewKey))
		{
			return this.cachedPreview;
		}

		this.cachedPreviewKey = key;
		this.cachedPreview = SchematicEntry.local("preview", title, thumbnail, poster,
				designer.isEmpty() ? "Unknown" : designer, this.page.category,
				this.page.sizeX, this.page.sizeY, this.page.sizeZ, this.page.blockCount, 0, 0,
				System.currentTimeMillis(), description,
				this.page.pictures.size(), this.page.pictureStart, -1, false);
		return this.cachedPreview;
	}

	private int renderDetailPreview(GuiGraphicsExtractor ctx, SchematicEntry entry, int x, int y, int modalWidth,
			int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		int pad = 12;
		int imageWidth = Math.round((modalWidth - pad * 3) * 0.56F);
		int imageHeight = IndexScreen.imageHeight(imageWidth);

		List<String> descLines = this.screen.wrap(entry.description(), modalWidth - pad * 2, 2);
		int modalHeight = pad + imageHeight + 6 + descLines.size() * (font.lineHeight + 1) + 10 + 16 + pad;

		Theme.roundedRect(ctx, x, y, modalWidth, modalHeight, Theme.RADIUS_MODAL, Theme.SURFACE_ELEVATED);

		Identifier texture = entry.imageCount() > 0 ? this.screen.imageTexture(entry, this.page.picturePreview) : null;

		if (texture != null)
		{
			Theme.image(ctx, texture, x + pad, y + pad, imageWidth, imageHeight);
		}
		else
		{
			Theme.blueprintPlaceholder(ctx, x + pad, y + pad, imageWidth, imageHeight);
			String empty = "No pictures yet";
			Theme.text(ctx, font, empty, x + pad + (imageWidth - font.width(empty)) / 2,
					y + pad + imageHeight / 2 - 4, Theme.TEXT_ASH);
		}

		if (entry.imageCount() > 1)
		{
			this.page.imagePrev.set(x + pad + 2, y + pad + imageHeight / 2 - 8, 12, 16);
			this.page.imageNext.set(x + pad + imageWidth - 14, y + pad + imageHeight / 2 - 8, 12, 16);
			Buttons.arrow(ctx, this.page.imagePrev, true, mouseX, mouseY);
			Buttons.arrow(ctx, this.page.imageNext, false, mouseX, mouseY);

			String counter = (this.page.picturePreview + 1) + "/" + entry.imageCount();
			int cw = font.width(counter) + 8;
			Theme.roundedRect(ctx, x + pad + imageWidth - cw - 3, y + pad + imageHeight - 14, cw, 11,
					Theme.RADIUS_PILL, 0xCC0F1114);
			Theme.text(ctx, font, counter, x + pad + imageWidth - cw + 1, y + pad + imageHeight - 12, Theme.TEXT);
		}
		else
		{
			this.page.imagePrev.set(0, 0, 0, 0);
			this.page.imageNext.set(0, 0, 0, 0);
		}

		// Cover and remove now live in the picture list below the form; this preview only browses
		if (this.page.picturePreview == 0 && entry.imageCount() > 0)
		{
			String cover = "Cover";
			int coverWidth = font.width(cover) + 10;
			int tx = x + pad + 4;
			int ty = y + pad + imageHeight - 15;
			Theme.roundedRect(ctx, tx, ty, coverWidth, 12, Theme.RADIUS_PILL, Theme.ACCENT);
			Theme.text(ctx, font, cover, tx + 5, ty + 2, Theme.ON_ACCENT);
		}

		int infoX = x + pad + imageWidth + pad;
		int infoWidth = modalWidth - (infoX - x) - pad;
		int line = y + pad;

		String age = entry.agoLabel();
		int ageWidth = font.width(age);
		Theme.text(ctx, font, age, x + modalWidth - pad - ageWidth, y + pad, Theme.TEXT_ASH);

		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, entry.title(), infoWidth - ageWidth - 6)),
				infoX, line, Theme.TEXT);
		line += font.lineHeight + 4;

		String followLabel = "Follow";
		int followWidth = font.width(Theme.bold(followLabel)) + 12;
		Rect follow = this.previewFollowRect;
		follow.set(infoX + infoWidth - followWidth, line - 1, followWidth, 12);
		Buttons.mock(ctx, font, follow, followLabel);

		Theme.text(ctx, font, Theme.clip(font, "Posted by " + entry.poster(), infoWidth - followWidth - 6),
				infoX, line, Theme.TEXT_MUTE);
		line += font.lineHeight + 3;

		String designer = entry.designer().isBlank() ? "Unknown" : entry.designer();
		Theme.text(ctx, font, Theme.clip(font, "Designed by " + designer, infoWidth), infoX, line, Theme.TEXT_MUTE);
		line += font.lineHeight + 6;

		line = this.screen.metaRow(ctx, "Dimensions", entry.dimensionsLabel(), infoX, line, infoWidth);
		line = this.screen.metaRow(ctx, "Total Blocks", entry.blockCountLabel(), infoX, line, infoWidth);
		line = this.screen.metaRow(ctx, "Volume", entry.volumeLabel(), infoX, line, infoWidth);

		Theme.text(ctx, font, "Downloads", infoX, line, Theme.TEXT_ASH);
		String downloads = entry.downloadsLabel();
		int downloadsWidth = font.width(downloads);
		Theme.text(ctx, font, downloads, infoX + infoWidth - downloadsWidth, line, Theme.TEXT);
		Theme.downloadGlyph(ctx, infoX + infoWidth - downloadsWidth - Theme.DOWNLOAD_GLYPH_WIDTH - 3,
				line + 2, Theme.TEXT_MUTE);
		line += font.lineHeight + 2;

		Theme.text(ctx, font, "Likes", infoX, line, Theme.TEXT_ASH);
		String likeCount = SchematicEntry.compact(entry.likes());
		int likeWidth = font.width(likeCount);
		Theme.text(ctx, font, likeCount, infoX + infoWidth - likeWidth, line, Theme.TEXT);
		Theme.heart(ctx, infoX + infoWidth - likeWidth - IndexScreen.HEART_SIZE - 4, line - 1, false);

		int descriptionY = y + pad + imageHeight + 6;

		for (String row : descLines)
		{
			Theme.text(ctx, font, row, x + pad, descriptionY, Theme.TEXT_MUTE);
			descriptionY += font.lineHeight + 1;
		}

		int buttonY = y + modalHeight - pad - 16;
		int downloadW = font.width(Theme.bold("Download")) + 18;
		int previewW = font.width(Theme.bold("3D preview")) + 18;
		int closeW = font.width(Theme.bold("Close")) + 18;
		int saveW = font.width(Theme.bold("Save for later")) + 18;

		Rect close = this.previewCloseRect;
		close.set(x + pad, buttonY, closeW, 16);
		Rect save = this.previewSaveRect;
		save.set(close.x + closeW + 6, buttonY, saveW, 16);
		Rect download = this.previewDownloadRect;
		download.set(x + modalWidth - pad - downloadW, buttonY, downloadW, 16);
		Rect preview3d = this.previewPreview3dRect;
		preview3d.set(download.x - 6 - previewW, buttonY, previewW, 16);

		Buttons.mock(ctx, font, close, "Close");
		Buttons.mock(ctx, font, save, "Save for later");
		Buttons.mock(ctx, font, preview3d, "3D preview");
		Buttons.mock(ctx, font, download, "Download");
		return y + modalHeight;
	}
}

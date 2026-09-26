package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class UploadSubmit
{
	private final UploadPage page;
	private final IndexScreen screen;

	UploadSubmit(UploadPage page, IndexScreen screen)
	{
		this.page = page;
		this.screen = screen;
	}

	void submitPost()
	{
		if (this.page.uploading)
		{
			return;
		}

		String title = this.page.titleBox.getValue().trim();

		if (title.isEmpty())
		{
			this.page.statusError("Give it a schematic name first.");
			this.page.focusTitle();
			return;
		}

		if (this.page.schematic == null)
		{
			this.page.statusError("Choose the .litematic file first.");
			return;
		}

		// The early check already flagged this file; Post stays disabled until a different one is picked
		if (this.page.schematicDuplicate)
		{
			this.screen.duplicateModal.open();
			return;
		}

		if (this.page.pictures.isEmpty())
		{
			this.page.statusError("Select at least one picture.");
			return;
		}

		if (!UploaderAccess.unlocked())
		{
			this.page.statusError("Verify your account to post.");
			return;
		}

		if (this.page.designerBox.getValue().trim().isEmpty() && !Settings.skipDesignerWarning())
		{
			this.screen.designerWarnModal.open();
			return;
		}

		this.beginUpload();
	}

	void beginUpload()
	{
		if (this.page.uploading || this.page.schematic == null || !UploaderAccess.unlocked())
		{
			return;
		}

		Usage.once("first_upload_attempt");
		String designer = this.page.designerBox.getValue().trim();

		JsonObject meta = new JsonObject();
		meta.addProperty("title", this.page.titleBox.getValue().trim());
		meta.addProperty("thumbnailName", this.page.thumbnailBox.getValue().trim());
		meta.addProperty("designer", designer.isEmpty() ? "Unknown" : designer);
		meta.addProperty("category", this.page.category.name());
		meta.addProperty("description", this.page.descriptionBox.getValue().trim());
		// The cover is always pictures[0]; the field stays for the server's existing contract
		meta.addProperty("thumbnailIndex", 0);

		String code = UploaderAccess.code();
		Path schematic = this.page.schematic;
		List<Path> pictures = new ArrayList<>(this.page.pictures);
		this.page.clearStatus();
		this.page.uploading = true;
		this.page.startedAt = System.currentTimeMillis();

		try
		{
			this.page.fileSize = Files.size(schematic);
		}
		catch (Exception e)
		{
			this.page.fileSize = 0L;
		}

		Thread worker = new Thread(() -> {
			Backend.UploadResult result = Backend.upload(code, meta.toString(), schematic, pictures);
			Minecraft.getInstance().execute(() -> {
				this.page.uploading = false;

				if (result.status() == 201)
				{
					this.clearForm();
					// The form clears and the page switches before the refresh lands, so the toast is the
					// only success signal that survives
					Toasts.push("Submitted for review", "Your post will go live once a moderator approves it.",
							new ItemStack(Items.WRITABLE_BOOK));
					this.screen.switchPage(IndexScreen.Page.BROWSE);
				}
				else if (result.status() == 409 && "duplicate".equals(result.error()))
				{
					this.page.clearStatus();
					this.page.schematicDuplicate = true;
					this.screen.duplicateModal.open();
				}
				else if (result.message() != null && !result.message().isBlank())
				{
					this.page.statusError(result.message());
				}
				else
				{
					this.page.statusError("Upload failed.");
					this.screen.showError(Errors.UPLOAD);
				}
			});
		}, "schematicindex-upload");
		worker.setDaemon(true);
		worker.start();
	}

	private void clearForm()
	{
		this.page.resetForm();
		this.page.open = false;
		Catalogue.refresh();
	}
}

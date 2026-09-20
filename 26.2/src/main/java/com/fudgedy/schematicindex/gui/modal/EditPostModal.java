package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Fields;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public class EditPostModal
{
	private final IndexScreen screen;
	private EditBox titleBox;
	private EditBox thumbnailBox;
	private EditBox designerBox;
	private MultiLineEditBox descriptionBox;
	private boolean open;
	private @Nullable String postId;
	// Set only by the staff hook: any post, submitted through the session rather than an upload code
	private boolean staffEdit;
	private Category category = Category.FARMS;
	private String status = "";
	private final Rect categoryButton = new Rect();
	private final Rect save = new Rect();
	private final Rect cancel = new Rect();
	private final Rect bounds = new Rect();
	private final Rect descriptionBounds = new Rect();

	public EditPostModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	// Screen.init clears the widget list, so the boxes are rebuilt and re-registered on every resize
	public void buildFields()
	{
		this.titleBox = this.screen.textField(0, 0, 100, "Schematic name",
				this.titleBox == null ? "" : this.titleBox.getValue());
		this.thumbnailBox = this.screen.textField(0, 0, 100, "Thumbnail name",
				this.thumbnailBox == null ? "" : this.thumbnailBox.getValue());
		this.designerBox = this.screen.textField(0, 0, 100, "Designed by",
				this.designerBox == null ? "" : this.designerBox.getValue());

		String description = this.descriptionBox == null ? "" : this.descriptionBox.getValue();
		this.descriptionBox = MultiLineEditBox.builder()
				.setPlaceholder(Component.literal("Description"))
				.setTextColor(Theme.TEXT)
				.setTextShadow(false)
				.setShowBackground(false)
				.setShowDecorations(false)
				.build(this.screen.font(), 100, 60, Component.literal("Description"));
		this.descriptionBox.setCharacterLimit(IndexScreen.DESC_CHAR_LIMIT);
		this.descriptionBox.setValue(description);
		this.screen.addModalWidget(this.descriptionBox);
	}

	public void open(String id)
	{
		SchematicEntry entry = null;

		for (SchematicEntry e : this.screen.myPosts)
		{
			if (e.id().equals(id))
			{
				entry = e;
				break;
			}
		}

		if (entry == null)
		{
			return;
		}

		this.staffEdit = false;
		this.fill(entry);
	}

	// Reached only through the staff hook, so the community jar has no caller for it
	public void open(SchematicEntry entry)
	{
		if (this.screen.staff == null || !this.screen.staff.available())
		{
			return;
		}

		this.staffEdit = true;
		this.fill(entry);
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 16;
		int cardWidth = Math.min(this.screen.width - 40, 420);
		int line = font.lineHeight;
		int fieldGap = 8;
		int descHeight = IndexScreen.DESC_FIELD_HEIGHT;
		int cardHeight = pad + line + 10
				+ (IndexScreen.FIELD_HEIGHT + fieldGap) * 3
				+ line + 2 + descHeight + fieldGap
				+ IndexScreen.FIELD_HEIGHT + 6
				+ IndexScreen.FIELD_HEIGHT + 6 + line + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.text(ctx, font, Theme.bold("Edit post"), x + pad, y + pad, Theme.TEXT);

		int fx = x + pad;
		int fw = cardWidth - pad * 2;
		int fy = y + pad + line + 10;

		Fields.single(ctx, this.titleBox, fx, fy, fw, IndexScreen.FIELD_HEIGHT, mouseX, mouseY, partialTick);
		fy += IndexScreen.FIELD_HEIGHT + fieldGap;
		Fields.single(ctx, this.thumbnailBox, fx, fy, fw, IndexScreen.FIELD_HEIGHT, mouseX, mouseY, partialTick);
		fy += IndexScreen.FIELD_HEIGHT + fieldGap;
		Fields.single(ctx, this.designerBox, fx, fy, fw, IndexScreen.FIELD_HEIGHT, mouseX, mouseY, partialTick);
		fy += IndexScreen.FIELD_HEIGHT + fieldGap;

		this.descriptionBox = this.screen.ensureMultiline(this.descriptionBox, fw, descHeight);
		Theme.text(ctx, font, "Description", fx, fy, Theme.TEXT_ASH);
		String editCount = this.descriptionBox.getValue().length() + " / " + IndexScreen.DESC_CHAR_LIMIT;
		Theme.text(ctx, font, editCount, fx + fw - font.width(editCount), fy, Theme.TEXT_ASH);
		fy += line + 2;
		Fields.multiline(ctx, this.descriptionBox, this.descriptionBounds, fx, fy, fw, descHeight,
				mouseX, mouseY, partialTick);
		fy += descHeight + fieldGap;

		this.categoryButton.set(fx, fy, fw, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.categoryButton, "Category: " + this.category.label(), mouseX, mouseY, false);
		fy += IndexScreen.FIELD_HEIGHT + 6;

		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		int saveWidth = font.width(Theme.bold("Save")) + 20;
		this.cancel.set(fx, fy, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.save.set(x + cardWidth - pad - saveWidth, fy, saveWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.cancel, "Cancel", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.save, "Save", mouseX, mouseY, true);
		fy += IndexScreen.FIELD_HEIGHT + 6;

		if (!this.status.isEmpty())
		{
			Theme.text(ctx, font, Theme.clip(font, this.status, fw), fx, fy, Theme.ACCENT_BRIGHT);
		}
	}

	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.save.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.screen.setFocused(null);
			this.confirm();
		}
		else if (this.cancel.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
			this.postId = null;
			this.screen.setFocused(null);
		}
		else if (this.categoryButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.category = Category.next(this.category.name());
		}
		else if (this.descriptionBounds.contains(mouseX, mouseY))
		{
			this.screen.setFocused(this.descriptionBox);
			this.descriptionBox.setFocused(true);
			this.descriptionBox.mouseClicked(event, doubleClick);
		}
		else
		{
			this.screen.focusField(this.titleBox, event, doubleClick, mouseX, mouseY);
			this.screen.focusField(this.thumbnailBox, event, doubleClick, mouseX, mouseY);
			this.screen.focusField(this.designerBox, event, doubleClick, mouseX, mouseY);
		}

		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.descriptionBounds.contains(mouseX, mouseY))
		{
			this.descriptionBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open || event.key() != 256)
		{
			return false;
		}

		this.open = false;
		this.postId = null;
		this.screen.setFocused(null);
		return true;
	}

	private void fill(SchematicEntry entry)
	{
		String id = entry.id();
		this.open = true;
		this.postId = id;
		this.status = "";
		this.category = entry.category();
		this.screen.fillEditField(this.titleBox, entry.title());
		this.screen.fillEditField(this.thumbnailBox, entry.thumbnailName() == null ? "" : entry.thumbnailName());
		this.screen.fillEditField(this.designerBox, entry.designer() == null ? "" : entry.designer());
		this.descriptionBox.setValue(entry.description() == null ? "" : entry.description());
		this.screen.setFocused(null);
		this.loadDetails(id);
	}

	// The my-posts payload is a dashboard summary and omits designer and description
	private void loadDetails(String id)
	{
		if (!Backend.configured())
		{
			return;
		}

		// A field that stopped matching its baseline was edited while the fetch was in flight, so the
		// fetched value must not overwrite it
		String designerBaseline = this.designerBox.getValue();
		String descriptionBaseline = this.descriptionBox.getValue();

		Thread worker = new Thread(() -> {
			JsonObject body = Backend.getJson("/post/" + id);

			if (body == null)
			{
				return;
			}

			String designer = Json.stringOf(body, "designer", "");
			String description = Json.stringOf(body, "description", "");

			Minecraft.getInstance().execute(() -> {
				if (!this.open || !id.equals(this.postId))
				{
					return;
				}

				if (this.designerBox.getValue().equals(designerBaseline))
				{
					this.screen.fillEditField(this.designerBox, designer);
				}

				if (this.descriptionBox.getValue().equals(descriptionBaseline))
				{
					this.descriptionBox.setValue(description);
				}
			});
		}, "schematicindex-editload");
		worker.setDaemon(true);
		worker.start();
	}

	private void confirm()
	{
		String code = UploaderAccess.code();
		String id = this.postId;

		if (id == null)
		{
			return;
		}

		if (code == null && !this.staffEdit)
		{
			this.status = "Your upload code was signed out. Unlock it again to edit.";
			return;
		}

		String title = this.titleBox.getValue().trim();

		if (title.isEmpty())
		{
			this.status = "A title is required.";
			return;
		}

		this.status = "Saving...";
		String thumbnail = this.thumbnailBox.getValue().trim();
		String designer = this.designerBox.getValue().trim();
		String description = this.descriptionBox.getValue().trim();
		String category = this.category.name();

		boolean viaStaff = this.staffEdit;
		Thread worker = new Thread(() -> {
			Backend.ApiResult result = viaStaff
					? this.staffEdit(id, title, thumbnail, designer, description, category)
					: Backend.editPost(code, id, title, thumbnail, designer, description, category);
			Minecraft.getInstance().execute(() -> {
				if (result.ok())
				{
					this.open = false;
					this.postId = null;
					this.screen.myStatsLoaded = false;
					Catalogue.refresh();
					Toasts.push("Post updated", title, new ItemStack(Items.WRITABLE_BOOK));
					return;
				}

				this.status = refusal(result, viaStaff);
			});
		}, "schematicindex-edit");
		worker.setDaemon(true);
		worker.start();
	}

	// The server's own message for a field it rejected; every other refusal gets a reason and a code
	private static String refusal(Backend.ApiResult result, boolean viaStaff)
	{
		String message = result.message();

		if (result.status() == 400 && message != null)
		{
			return message;
		}

		if (result.unverified())
		{
			return "Verify your account first, then save again.";
		}

		if (result.is("bad_code"))
		{
			return "Your upload code was refused. Unlock it again to edit.";
		}

		if (result.is("not_staff") || result.is("not_owner"))
		{
			return "Could not save: this account is no longer staff. (" + Errors.STAFF_DENIED + ")";
		}

		if (result.status() == 404)
		{
			return "Could not save: this post is not yours to edit.";
		}

		String code = viaStaff ? Errors.STAFF_ACTION : Errors.POST_EDIT;
		Errors.report(code);
		return "Could not save. Try again. (" + code + ")";
	}

	private Backend.ApiResult staffEdit(String id, String title, String thumbnail, String designer, String description,
			String category)
	{
		if (this.screen.staff == null)
		{
			return new Backend.ApiResult(-1, null);
		}

		return this.screen.staff.editPost(id, title, thumbnail, designer, description, category);
	}
}

package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.page.FormFields;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Fields;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

// Mirrors the upload form's text fields; the server's edit route takes text and category only, so pictures stay put
public class EditPostModal
{
	private static final int WIDTH = 440;
	private static final int PREVIEW_WIDTH = 120;
	private static final int MIN_DESCRIPTION_HEIGHT = 28;
	private static final String PREVIEW_ID = "edit-preview";
	private final IndexScreen screen;
	private EditBox titleBox;
	private EditBox thumbnailBox;
	private EditBox designerBox;
	private MultiLineEditBox descriptionBox;
	private boolean open;
	private long openedAt;
	private @Nullable String postId;
	private @Nullable SchematicEntry entry;
	private @Nullable SchematicEntry preview;
	// Set only by the staff hook: any post, submitted through the session rather than an upload code
	private boolean staffEdit;
	private boolean saving;
	private Category category = Category.FARMS;
	private final EditPostMessages messages = new EditPostMessages();
	private final Dropdown categoryDropdown = new Dropdown();
	private final Rect categoryButton = new Rect();
	private final Rect save = new Rect();
	private final Rect cancel = new Rect();
	private final Rect close = new Rect();
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
		this.titleBox = FormFields.textField(this.screen, 0, 0, 100, "Schematic name",
				this.titleBox == null ? "" : this.titleBox.getValue());
		this.thumbnailBox = FormFields.textField(this.screen, 0, 0, 100, "Thumbnail name",
				this.thumbnailBox == null ? "" : this.thumbnailBox.getValue());
		this.designerBox = FormFields.textField(this.screen, 0, 0, 100, "Designed by",
				this.designerBox == null ? "" : this.designerBox.getValue());
		this.titleBox.setMaxLength(EditPostMessages.TITLE_LIMIT);
		this.thumbnailBox.setMaxLength(EditPostMessages.THUMBNAIL_LIMIT);
		this.designerBox.setMaxLength(EditPostMessages.DESIGNER_LIMIT);
		this.descriptionBox = FormFields.multiline(this.screen, 100, IndexScreen.DESC_FIELD_HEIGHT,
				this.descriptionBox == null ? "" : this.descriptionBox.getValue());
	}

	public void open(String id)
	{
		for (SchematicEntry candidate : this.screen.dashboardPage.stats.posts)
		{
			if (candidate.id().equals(id))
			{
				this.staffEdit = false;
				this.fill(candidate);
				return;
			}
		}
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

	public void render(GuiGraphics ctx, int mouseX, int mouseY, float partialTick)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		int line = font.lineHeight;
		int labelH = line + Theme.SPACE_XS;
		int group = labelH + Theme.H_CONTROL + Theme.SPACE_L;
		int chrome = Theme.SPACE_M + Theme.ICON_M + Theme.SPACE_S + group * 3 + labelH + Theme.SPACE_L
				+ Theme.H_CONTROL + Theme.SPACE_L;
		int descriptionH = Math.max(MIN_DESCRIPTION_HEIGHT,
				Math.min(IndexScreen.DESC_FIELD_HEIGHT, this.screen.height - Theme.SPACE_L * 2 - chrome));

		ModalChrome.open(ctx, font, this.bounds, this.screen.width, this.screen.height, WIDTH, chrome + descriptionH,
				"Edit post", null, this.close, this.openedAt, mouseX, mouseY);
		int x = this.bounds.x;
		int y = this.bounds.y;
		int w = this.bounds.width;

		int rightW = Math.min(PREVIEW_WIDTH, (w - Theme.SPACE_L * 3) / 3);
		int leftX = x + Theme.SPACE_L;
		int leftW = w - Theme.SPACE_L * 3 - rightW;
		int rightX = leftX + leftW + Theme.SPACE_L;
		int top = y + Theme.SPACE_M + Theme.ICON_M + Theme.SPACE_S;
		int fy = top;

		Fields.label(ctx, font, "Schematic name", "on the post page", leftX, fy, leftW);
		Fields.single(ctx, this.titleBox, leftX, fy + labelH, leftW, Theme.H_CONTROL, mouseX, mouseY, partialTick);
		Fields.error(ctx, font, this.messages.title, leftX, fy + labelH, leftW, Theme.H_CONTROL);
		fy += group;
		Fields.label(ctx, font, "Thumbnail name", "on the card", leftX, fy, leftW);
		Fields.single(ctx, this.thumbnailBox, leftX, fy + labelH, leftW, Theme.H_CONTROL, mouseX, mouseY, partialTick);
		Fields.error(ctx, font, this.messages.thumbnail, leftX, fy + labelH, leftW, Theme.H_CONTROL);
		fy += group;
		Fields.label(ctx, font, "Designed by", "", leftX, fy, leftW);
		Fields.single(ctx, this.designerBox, leftX, fy + labelH, leftW, Theme.H_CONTROL, mouseX, mouseY, partialTick);
		Fields.error(ctx, font, this.messages.designer, leftX, fy + labelH, leftW, Theme.H_CONTROL);
		fy += group;

		this.descriptionBox = FormFields.ensureMultiline(this.screen, this.descriptionBox, leftW, descriptionH);
		Fields.label(ctx, font, "Description", this.descriptionBox.getValue().length() + " / " + IndexScreen.DESC_CHAR_LIMIT,
				leftX, fy, leftW);
		fy += labelH;
		Fields.multiline(ctx, this.descriptionBox, this.descriptionBounds, leftX, fy, leftW, descriptionH,
				mouseX, mouseY, partialTick);
		Fields.error(ctx, font, this.messages.description, leftX, fy, leftW, descriptionH);

		int ry = top;
		Fields.label(ctx, font, "Preview", "", rightX, ry, rightW);
		ry += labelH;
		ry = this.renderPreview(ctx, rightX, ry, rightW) + Theme.SPACE_L;
		Fields.label(ctx, font, "Category", "", rightX, ry, rightW);
		this.categoryButton.set(rightX, ry + labelH, rightW, Theme.H_CONTROL);
		this.categoryDropdown.render(ctx, font, this.categoryButton, Category.tagLabels(), Category.tagIndex(this.category),
				mouseX, mouseY);

		int buttonY = y + this.bounds.height - Theme.SPACE_L - Theme.H_CONTROL;
		int buttonW = Math.max(Buttons.width(font, "Cancel"), Buttons.width(font, "Save"));
		this.save.set(x + w - Theme.SPACE_L - buttonW, buttonY, buttonW, Theme.H_CONTROL);
		this.cancel.set(this.save.x - Theme.SPACE_S - buttonW, buttonY, buttonW, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.cancel, "Cancel", Buttons.Kind.SECONDARY, true, mouseX, mouseY);
		Buttons.button(ctx, font, this.save, this.saving ? "..." : "Save", Buttons.Kind.PRIMARY, !this.saving,
				mouseX, mouseY);

		if (!this.messages.status.isEmpty())
		{
			Theme.text(ctx, font, Theme.clip(font, this.messages.status, this.cancel.x - Theme.SPACE_S - leftX), leftX,
					buttonY + (Theme.H_CONTROL - line) / 2 + 1, this.messages.isError ? Theme.DANGER_TEXT : Theme.TEXT_ASH);
		}

		this.categoryDropdown.renderOpen(ctx, font, mouseX, mouseY, this.screen.height);
	}

	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		int picked = this.categoryDropdown.click(mouseX, mouseY);

		if (picked >= 0)
		{
			this.category = Category.tags()[picked];
			return true;
		}

		if (picked == Dropdown.TOGGLED)
		{
			return true;
		}

		if (this.save.contains(mouseX, mouseY))
		{
			if (!this.saving)
			{
				Theme.click(1.1F);
				this.screen.setFocused(null);
				this.confirm();
			}
		}
		else if (this.cancel.contains(mouseX, mouseY) || this.close.contains(mouseX, mouseY)
				|| !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.dismiss();
		}
		else if (this.descriptionBounds.contains(mouseX, mouseY))
		{
			this.screen.setFocused(this.descriptionBox);
			this.descriptionBox.setFocused(true);
			this.descriptionBox.mouseClicked(event, doubleClick);
		}
		else
		{
			FormFields.focusField(this.screen, this.titleBox, event, doubleClick, mouseX, mouseY);
			FormFields.focusField(this.screen, this.thumbnailBox, event, doubleClick, mouseX, mouseY);
			FormFields.focusField(this.screen, this.designerBox, event, doubleClick, mouseX, mouseY);
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
		if (!this.open)
		{
			return false;
		}

		if (event.key() == 256)
		{
			if (this.categoryDropdown.isOpen())
			{
				this.categoryDropdown.close();
				return true;
			}

			this.dismiss();
			return true;
		}

		boolean enter = event.key() == 257 || event.key() == 335;
		boolean singleLine = this.titleBox.isFocused() || this.thumbnailBox.isFocused() || this.designerBox.isFocused();

		if (enter && singleLine && !this.saving)
		{
			this.screen.setFocused(null);
			this.confirm();
			return true;
		}

		return false;
	}

	private int renderPreview(GuiGraphics ctx, int x, int y, int width)
	{
		SchematicEntry shown = this.previewEntry();

		if (shown == null)
		{
			return y;
		}

		int savedWidth = this.screen.cardWidth;
		int savedHeight = this.screen.cardHeight;
		this.screen.cardWidth = width;
		this.screen.cardHeight = IndexScreen.imageHeight(width) + IndexScreen.CAPTION_HEIGHT;

		// An exception mid-render must not leave the shared grid card size corrupted for later frames
		try
		{
			this.screen.browsePage.grid.renderCard(ctx, shown, x, y, -999, -999);
			return y + this.screen.cardHeight;
		}
		finally
		{
			this.screen.cardWidth = savedWidth;
			this.screen.cardHeight = savedHeight;
		}
	}

	private @Nullable SchematicEntry previewEntry()
	{
		if (this.entry == null)
		{
			return null;
		}

		String title = this.titleBox.getValue().trim();
		String thumbnail = this.thumbnailBox.getValue().trim();
		String designer = this.designerBox.getValue().trim();
		SchematicEntry last = this.preview;

		if (last != null && last.title().equals(title) && last.thumbnailName().equals(thumbnail)
				&& last.designer().equals(designer) && last.category() == this.category)
		{
			return last;
		}

		this.preview = this.entry.asPreview(PREVIEW_ID, title, thumbnail, designer, this.category);
		return this.preview;
	}

	private void dismiss()
	{
		this.open = false;
		this.postId = null;
		this.entry = null;
		this.preview = null;
		this.categoryDropdown.close();
		this.screen.setFocused(null);
	}

	private void fill(SchematicEntry entry)
	{
		String id = entry.id();
		this.open = true;
		this.openedAt = System.currentTimeMillis();
		this.postId = id;
		this.entry = entry;
		this.preview = null;
		this.saving = false;
		this.messages.clear();
		this.category = entry.category() == Category.ALL ? Category.tags()[0] : entry.category();
		this.categoryDropdown.close();
		FormFields.fillEditField(this.titleBox, entry.title());
		FormFields.fillEditField(this.thumbnailBox, entry.thumbnailName() == null ? "" : entry.thumbnailName());
		FormFields.fillEditField(this.designerBox, entry.designer() == null ? "" : entry.designer());
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
					FormFields.fillEditField(this.designerBox, designer);
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

		if (!UploaderAccess.unlocked() && !this.staffEdit)
		{
			this.messages.fail("Verify your account first, then save again.");
			return;
		}

		String title = this.titleBox.getValue().trim();
		String thumbnail = this.thumbnailBox.getValue().trim();
		String designer = this.designerBox.getValue().trim();
		String description = this.descriptionBox.getValue().trim();
		Category chosen = this.category;
		String category = chosen.name();

		if (!this.messages.validate(title, thumbnail, designer, description))
		{
			return;
		}

		this.saving = true;
		this.messages.status = "Saving...";
		boolean viaStaff = this.staffEdit;
		SchematicIndexMod.LOGGER.debug("Saving edit to post {}", id);
		Thread worker = new Thread(() -> {
			Backend.ApiResult result = viaStaff
					? this.staffEdit(id, title, thumbnail, designer, description, category)
					: Backend.editPost(code, id, title, thumbnail, designer, description, category);
			Minecraft.getInstance().execute(() -> {
				// Closing the modal mid-save does not undo the save, so success still refreshes and toasts
				boolean current = id.equals(this.postId);

				if (result.ok())
				{
					if (current)
					{
						this.dismiss();
					}

					this.screen.dashboardPage.stats.refresh();
					Catalogue.applyEdit(id, title, thumbnail, designer, description, chosen);
					this.screen.detailView.applyEdit(id, title, thumbnail, designer, description, chosen);
					Catalogue.revalidate();
					Toasts.push("Post updated", title, new ItemStack(Items.WRITABLE_BOOK));
					return;
				}

				if (!current)
				{
					return;
				}

				this.saving = false;
				SchematicIndexMod.LOGGER.debug("Edit to post {} refused with {}", id, result.status());
				this.messages.refuse(result, viaStaff);
			});
		}, "schematicindex-edit");
		worker.setDaemon(true);
		worker.start();
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

package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UploadPage
{
	// Independent of the field's pixel width so a long draft is stored in full, not truncated to fit
	private static final int TITLE_CHAR_LIMIT = 120;
	// The server's per-field caps for the card name and the designer credit
	private static final int THUMBNAIL_CHAR_LIMIT = 64;
	private static final int DESIGNER_CHAR_LIMIT = 64;
	public static final long SIGN_OUT_CONFIRM_MS = 3000L;
	static final long CLEAR_CONFIRM_MS = 3000L;
	static final String CLEAR_LABEL = "Clear";
	static final String CLEAR_CONFIRM_LABEL = "Click again to clear";
	private static final Category DEFAULT_CATEGORY = Category.FARMS;

	// Kept across a menu close so a half-filled post survives; cleared on restart or a successful upload
	private static boolean draftSaved;
	private static boolean draftFormOpen;
	private static String draftTitle = "";
	private static String draftThumbnail = "";
	private static String draftDesigner = "";
	private static String draftDescription = "";
	private static @Nullable Category draftCategory;
	private static @Nullable Path draftSchematic;
	private static final List<Path> draftPictures = new ArrayList<>();

	private final IndexScreen screen;
	final UploadFiles files;
	private final UploadPaste paste;
	private final UploadSubmit submit;
	private final UploadView view;
	final UploadPictures pictureList;
	EditBox codeBox;
	EditBox titleBox;
	EditBox thumbnailBox;
	EditBox designerBox;
	MultiLineEditBox descriptionBox;
	final Rect descriptionBounds = new Rect();
	public final Rect unlockButton = new Rect();
	final Rect verifyButton = new Rect();
	public final Rect signOutButton = new Rect();
	// Two-step confirm, because Sign out sits right next to "+ New post". 0 means not armed
	public long signOutConfirmAt = 0L;
	public final Rect clearButton = new Rect();
	// Wipes the whole form, so it takes a second click inside CLEAR_CONFIRM_MS. 0 means not armed
	private long clearArmedAt;
	public final Rect categoryButton = new Rect();
	final Rect imagePrev = new Rect();
	final Rect imageNext = new Rect();
	public final Rect picturesButton = new Rect();
	public final Rect schematicButton = new Rect();
	public final Rect photoModeButton = new Rect();
	@Nullable Path schematic;
	final List<Path> pictures = new ArrayList<>();
	int pictureStart = -1;
	int picturePreview;
	public final Rect postButton = new Rect();
	Category category = DEFAULT_CATEGORY;
	String status = "";
	boolean statusError;
	boolean uploading;
	long startedAt;
	long fileSize;
	int sizeX;
	int sizeY;
	int sizeZ;
	int blockCount;
	// A background parse checks this before applying, in case the file was swapped or the form left
	long schematicParseSeq;
	// Same guard as schematicParseSeq, for the duplicate check that starts alongside it
	long duplicateCheckSeq;
	boolean duplicateChecking;
	boolean schematicDuplicate;
	String duplicateCheckNote = "";
	boolean open;
	final Rect backButton = new Rect();
	// The scissored form body; anything laid out outside it is scrolled out of sight and must not take clicks
	int bodyTop;
	int bodyBottom;
	// init runs again on every resize; restoring past the first would overwrite edits made since
	private boolean draftRestored;

	public UploadPage(IndexScreen screen)
	{
		this.screen = screen;
		this.files = new UploadFiles(this, screen);
		this.paste = new UploadPaste(this);
		this.submit = new UploadSubmit(this, screen);
		this.view = new UploadView(this, screen);
		this.pictureList = new UploadPictures(this);
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void setOpen(boolean open)
	{
		this.open = open;
	}

	public void clearStatus()
	{
		this.status = "";
		this.statusError = false;
	}

	// Neutral or success feedback: "Photo added", "Cover updated", a busy label mid-flight
	void status(String message)
	{
		this.status = message;
		this.statusError = false;
	}

	// A failure the player needs to act on: shown in DANGER_TEXT instead of the neutral colour
	void statusError(String message)
	{
		this.status = message;
		this.statusError = true;
	}

	public void buildFields()
	{
		int formWidth = Math.min(this.screen.contentWidth, 300);
		int formX = this.screen.contentX + (this.screen.contentWidth - formWidth) / 2;
		int y = IndexScreen.TOP_BAR_HEIGHT + 60;

		this.codeBox = FormFields.textField(this.screen, formX + 6, y + 4, formWidth - 12, "Access code",
				this.codeBox == null ? "" : this.codeBox.getValue());
		this.titleBox = FormFields.textField(this.screen, formX + 6, y + 4, formWidth - 12, "Schematic name",
				this.titleBox == null ? "" : this.titleBox.getValue());
		this.thumbnailBox = FormFields.textField(this.screen, formX + 6, y + 4, formWidth - 12, "Thumbnail name",
				this.thumbnailBox == null ? "" : this.thumbnailBox.getValue());
		this.designerBox = FormFields.textField(this.screen, formX + 6, y + 4, formWidth - 12, "Designed by",
				this.designerBox == null ? "" : this.designerBox.getValue());
		String uploadDescription = this.descriptionBox == null ? "" : this.descriptionBox.getValue();
		this.descriptionBox = FormFields.multiline(this.screen, 100, IndexScreen.DESC_FIELD_HEIGHT, uploadDescription);

		// Screen.init clears every widget, so the edit modal must re-register its boxes in this same pass
		this.screen.editPostModal.buildFields();
	}

	public void limitFields()
	{
		int avgBoldChar = Math.max(4, this.screen.font().width(Theme.bold("abcdefghijklmnopqrstuvwxyz")) / 26 + 1);
		this.thumbnailBox.setMaxLength(Math.min(THUMBNAIL_CHAR_LIMIT, Math.max(10, (this.screen.cardWidth - 12) / avgBoldChar)));
		this.designerBox.setMaxLength(DESIGNER_CHAR_LIMIT);
		// Deliberately not width-derived like the thumbnail box: a narrow layout must not truncate a draft
		this.titleBox.setMaxLength(TITLE_CHAR_LIMIT);
	}

	public void restoreDraft()
	{
		if (!draftSaved || this.draftRestored)
		{
			return;
		}

		this.draftRestored = true;

		FormFields.fillEditField(this.titleBox, draftTitle);
		FormFields.fillEditField(this.thumbnailBox, draftThumbnail);
		FormFields.fillEditField(this.designerBox, draftDesigner);
		this.descriptionBox.setValue(draftDescription);

		if (draftCategory != null)
		{
			this.category = draftCategory;
		}

		this.schematic = draftSchematic;
		this.pictures.clear();
		this.pictures.addAll(draftPictures);
		// The image store was released on close, so the restored paths need fresh indices
		this.pictureStart = this.pictures.isEmpty() ? -1 : ImageStore.register(this.pictures);
		this.picturePreview = 0;
		this.open = draftFormOpen;

		if (this.schematic != null)
		{
			this.files.parseSchematicStats(this.schematic);
			this.files.checkDuplicate(this.schematic);
		}
	}

	public void saveDraft()
	{
		if (!UploaderAccess.unlocked() || this.titleBox == null)
		{
			return;
		}

		draftTitle = this.titleBox.getValue();
		draftThumbnail = this.thumbnailBox.getValue();
		draftDesigner = this.designerBox.getValue();
		draftDescription = this.descriptionBox.getValue();
		draftCategory = this.category;
		draftSchematic = this.schematic;
		draftPictures.clear();
		draftPictures.addAll(this.pictures);
		draftFormOpen = this.open;
		draftSaved = true;
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY, float partialTick)
	{
		this.view.render(ctx, mouseX, mouseY, partialTick);
	}

	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY)
	{
		if (!UploaderAccess.unlocked())
		{
			if (this.verifyButton.contains(mouseX, mouseY))
			{
				Theme.click();
				this.screen.startVerify(null);
				return true;
			}

			if (this.unlockButton.contains(mouseX, mouseY))
			{
				String value = this.codeBox.getValue();
				this.status("Checking code...");
				this.screen.setFocused(null);
				Thread worker = new Thread(() -> {
					String owner = UploaderAccess.redeem(value);
					// Probed only on failure, so the message can name the real reason
					boolean reachable = owner != null || backendReachable();
					Minecraft.getInstance().execute(() -> {
						if (owner != null)
						{
							this.status("Unlocked as " + owner + ".");
						}
						else if (!reachable)
						{
							this.statusError("Couldn't reach the server - try again.");
						}
						else
						{
							this.statusError("That code is not valid.");
						}
					});
				}, "schematicindex-code");
				worker.setDaemon(true);
				worker.start();
				return true;
			}

			return FormFields.focusField(this.screen, this.codeBox, event, doubleClick, mouseX, mouseY);
		}

		if (this.signOutButton.contains(mouseX, mouseY))
		{
			long now = System.currentTimeMillis();

			// The first click only arms the confirm, guarding the button beside "+ New post"
			if (this.signOutConfirmAt != 0L && now - this.signOutConfirmAt <= SIGN_OUT_CONFIRM_MS)
			{
				this.signOutConfirmAt = 0L;
				UploaderAccess.signOut();
				this.clearStatus();
				this.screen.dashboardPage.stats.loaded = false;
				this.screen.dashboardPage.selectedPost = null;
				this.screen.setFocused(null);
			}
			else
			{
				this.signOutConfirmAt = now;
				Theme.click();
			}

			return true;
		}

		this.signOutConfirmAt = 0L;

		if (this.clearButton.contains(mouseX, mouseY))
		{
			this.clickClear();
			return true;
		}

		this.clearArmedAt = 0L;

		if (this.screen.dashboardPage.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.backButton.contains(mouseX, mouseY))
		{
			this.open = false;
			this.screen.dashboardPage.stats.loaded = false;
			Theme.click(0.9F);
			return true;
		}

		if (mouseY < this.bodyTop || mouseY >= this.bodyBottom)
		{
			this.clearFocus();
			return true;
		}

		if (this.categoryButton.contains(mouseX, mouseY))
		{
			this.category = Category.next(this.category.name());
			return true;
		}

		if (this.pictureList.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.imagePrev.contains(mouseX, mouseY))
		{
			this.picturePreview = Math.floorMod(this.picturePreview - 1, Math.max(1, this.pictures.size()));
			return true;
		}

		if (this.imageNext.contains(mouseX, mouseY))
		{
			this.picturePreview = Math.floorMod(this.picturePreview + 1, Math.max(1, this.pictures.size()));
			return true;
		}

		if (this.picturesButton.contains(mouseX, mouseY))
		{
			this.files.openPicturePicker();
			return true;
		}

		if (this.schematicButton.contains(mouseX, mouseY))
		{
			this.files.openSchematicPicker();
			return true;
		}

		if (this.photoModeButton.contains(mouseX, mouseY))
		{
			this.files.openPhotoMode();
			return true;
		}

		if (this.postButton.contains(mouseX, mouseY))
		{
			this.submit.submitPost();
			return true;
		}

		if (FormFields.focusField(this.screen, this.titleBox, event, doubleClick, mouseX, mouseY)
				|| FormFields.focusField(this.screen, this.thumbnailBox, event, doubleClick, mouseX, mouseY)
				|| FormFields.focusField(this.screen, this.designerBox, event, doubleClick, mouseX, mouseY))
		{
			return true;
		}

		if (this.descriptionBox != null && this.descriptionBounds.contains(mouseX, mouseY))
		{
			this.screen.setFocused(this.descriptionBox);
			this.descriptionBox.setFocused(true);
			this.descriptionBox.mouseClicked(event, doubleClick);
			return true;
		}

		// Steps out of any text box, so Ctrl+V pastes a schematic instead of typing into a field
		this.clearFocus();
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
	{
		if (this.descriptionBox != null && this.descriptionBounds.contains(mouseX, mouseY))
		{
			this.descriptionBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
			return true;
		}

		this.screen.scroll = Math.max(0.0F, Math.min(this.screen.maxScroll,
				this.screen.scroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}

	public boolean anyFieldFocused()
	{
		return (this.titleBox != null && this.titleBox.isFocused())
				|| (this.thumbnailBox != null && this.thumbnailBox.isFocused())
				|| (this.designerBox != null && this.designerBox.isFocused())
				|| (this.descriptionBox != null && this.descriptionBox.isFocused())
				|| (this.codeBox != null && this.codeBox.isFocused());
	}

	public void pasteFromClipboard()
	{
		this.paste.pasteFromClipboard();
	}

	public void dropFiles(List<Path> paths)
	{
		this.files.dropFiles(paths);
	}

	public void beginUpload()
	{
		this.submit.beginUpload();
	}

	public void addPhotos(List<byte[]> photos, boolean coverFirst)
	{
		this.files.addPhotos(photos, coverFirst);
	}

	// Scrolls back to the top so the field is on screen, then puts the cursor in it
	void focusTitle()
	{
		this.clearFocus();
		this.screen.scroll = 0.0F;
		this.screen.setFocused(this.titleBox);
		this.titleBox.setFocused(true);
	}

	boolean clearArmed()
	{
		return this.clearArmedAt != 0L && System.currentTimeMillis() - this.clearArmedAt <= CLEAR_CONFIRM_MS;
	}

	boolean canClear()
	{
		return !this.uploading && !this.isBlank();
	}

	// Everything a post carries goes, including the saved draft; the category returns to its default
	// only on Clear, since a finished upload keeps it for the next post
	void resetForm()
	{
		ImageStore.releasePicked(this.pictureStart, this.pictures.size());
		this.pictures.clear();
		this.pictureStart = -1;
		this.picturePreview = 0;
		this.pictureList.reset();
		this.schematic = null;
		this.schematicParseSeq++;
		this.duplicateCheckSeq++;
		this.sizeX = 0;
		this.sizeY = 0;
		this.sizeZ = 0;
		this.blockCount = 0;
		this.titleBox.setValue("");
		this.thumbnailBox.setValue("");
		this.designerBox.setValue("");
		this.descriptionBox.setValue("");
		this.clearStatus();
		this.schematicDuplicate = false;
		this.duplicateChecking = false;
		this.duplicateCheckNote = "";
		clearDraft();
	}

	static void clearDraft()
	{
		draftSaved = false;
		draftFormOpen = false;
		draftTitle = "";
		draftThumbnail = "";
		draftDesigner = "";
		draftDescription = "";
		draftCategory = null;
		draftSchematic = null;
		draftPictures.clear();
	}

	private boolean isBlank()
	{
		return this.titleBox.getValue().isEmpty() && this.thumbnailBox.getValue().isEmpty()
				&& this.designerBox.getValue().isEmpty() && this.descriptionBox.getValue().isEmpty()
				&& this.category == DEFAULT_CATEGORY && this.schematic == null && this.pictures.isEmpty();
	}

	private void clickClear()
	{
		if (!this.canClear())
		{
			this.clearArmedAt = 0L;
			return;
		}

		if (!this.clearArmed())
		{
			this.clearArmedAt = System.currentTimeMillis();
			Theme.click();
			return;
		}

		this.clearArmedAt = 0L;
		List<Path> owned = new ArrayList<>(this.pictures);

		if (this.schematic != null)
		{
			owned.add(this.schematic);
		}

		this.clearFocus();
		this.resetForm();
		this.category = DEFAULT_CATEGORY;
		this.files.deletePasted(owned);
		this.screen.scroll = 0.0F;
		Theme.click(0.9F);
	}

	private void clearFocus()
	{
		this.screen.setFocused(null);

		for (EditBox box : new EditBox[]{this.codeBox, this.titleBox, this.thumbnailBox, this.designerBox})
		{
			if (box != null)
			{
				box.setFocused(false);
			}
		}

		if (this.descriptionBox != null)
		{
			this.descriptionBox.setFocused(false);
		}
	}

	// redeem() collapses an unreachable server and an invalid code into the same null result. Runs
	// off the render thread and never throws
	private static boolean backendReachable()
	{
		String host = Backend.baseHost();

		if (host == null || host.isBlank())
		{
			return false;
		}

		// A short TCP connect to 443 separates an answering server from an unreachable one
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress(host, 443), 4000);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}
}

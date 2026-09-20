package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Premium;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

public class DetailView
{
	// Sent only once clicking stops, so racing responses cannot flash the locally-updated stars
	private static final long RATE_DEBOUNCE_MS = 550L;

	final IndexScreen screen;
	final DetailCamera camera;
	final CollectionMenu collectionMenu;
	private final DetailRender renderer;
	private final DetailActions actions;

	private @Nullable SchematicEntry entry;
	// Non-null when the open post is a premium listing: hides the 3D preview and swaps Download for Buy
	public @Nullable Premium.Entry premium;
	long openedAt;
	int image;
	boolean model;
	float descScroll; // px
	float descMaxScroll;
	final Rect descBounds = new Rect();
	// false = Description, true = Materials; both share descScroll since only one is visible
	boolean materialsTab;
	final Rect descTab = new Rect();
	final Rect materialsTabRect = new Rect();
	final Rect imageRect = new Rect();
	final Rect prev = new Rect();
	final Rect next = new Rect();
	final Rect download = new Rect();
	final Rect joinDiscord = new Rect();
	final Rect load = new Rect();
	final Rect preview3d = new Rect();
	final Rect save = new Rect();
	final Rect follow = new Rect();
	final Rect closeButton = new Rect();
	final Rect cornerClose = new Rect();
	final Rect heart = new Rect();
	final Rect claim = new Rect();
	final Rect report = new Rect();
	final Rect collection = new Rect();
	final Rect posterRect = new Rect();
	final Rect viewProfile = new Rect();
	final Rect savePngButton = new Rect();
	final Rect copyPngButton = new Rect();
	final Rect copyLinkButton = new Rect();
	// Captured during render so a click on the scrim outside it dismisses the card
	final Rect bounds = new Rect();

	int myStars;      // this device's rating in half-stars (0..10)
	double starAvg;   // average rating 0..5
	int starCount;    // number of ratings
	final Rect[] starRects = {new Rect(), new Rect(), new Rect(), new Rect(), new Rect()};
	private int pendingRateValue = -1;
	private long pendingRateAt;
	private @Nullable String pendingRateId;
	// The server-confirmed rating the optimistic stars roll back to when the send fails
	private int committedStars;

	boolean followConfirm;
	long followConfirmAt;

	public DetailView(IndexScreen screen)
	{
		this.screen = screen;
		this.camera = new DetailCamera(this);
		this.collectionMenu = new CollectionMenu(screen);
		this.renderer = new DetailRender(this);
		this.actions = new DetailActions(this);
	}

	public boolean isOpen()
	{
		return this.entry != null;
	}

	public @Nullable SchematicEntry entry()
	{
		return this.entry;
	}

	public int image()
	{
		return this.image;
	}

	public void applyLikes(String postId, int likes, boolean liked)
	{
		SchematicEntry open = this.entry;

		if (open != null && postId.equals(open.id()))
		{
			this.entry = open.withLikes(likes, liked);
		}
	}

	public boolean isPremium()
	{
		return this.premium != null;
	}

	public void openPremium(SchematicEntry entry, Premium.Entry premium)
	{
		this.open(entry);
		this.premium = premium;
		this.model = false;
		Backend.premiumViewAsync(entry.id());
	}

	public void open(SchematicEntry entry)
	{
		this.entry = entry;
		this.premium = null;
		this.collectionMenu.close();
		Backend.viewAsync(entry.id());

		// The lite catalogue omits materials and description; they arrive here, unless the view has moved on
		Catalogue.loadDetails(entry, filled ->
		{
			if (this.entry != null && this.entry.id().equals(filled.id()))
			{
				this.entry = filled;
			}
		});

		this.myStars = entry.myStars();
		this.committedStars = entry.myStars();
		this.starAvg = entry.starAvg();
		this.starCount = entry.starCount();

		this.screen.recordViewed(entry.id());

		Theme.click(1.2F);
		this.openedAt = System.currentTimeMillis();
		this.image = 0;
		this.preloadGallery();
		this.model = false;
		this.camera.yaw = 35.0F;
		this.camera.pitch = 28.0F;
		this.camera.zoom = 1.0F;
		this.camera.layer = 1.0F;
		this.screen.detailDownloadLocked = false;
		this.resetState();
	}

	public void close()
	{
		this.entry = null;
		this.premium = null;
		// Every post opens fresh in Orbit mode with the camera at its default framing
		this.camera.cutaway = true;
		this.camera.reset();
		this.resetState();
	}

	public void dismiss()
	{
		this.entry = null;
		this.premium = null;
		this.collectionMenu.close();
	}

	// Shared by open, the close click and Escape, so the three paths cannot drift apart
	public void resetState()
	{
		this.collectionMenu.close();
		this.followConfirm = false;
		this.screen.overwriteConfirm.close();
		this.screen.reportModal.close();
		this.screen.claimModal.reset();
		this.descScroll = 0.0F;
		this.materialsTab = false;
		this.camera.clearHeldKeys();
		this.screen.status = "";
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		SchematicEntry entry = this.entry;

		if (entry == null)
		{
			return;
		}

		// A modal stacked on the card owns the pointer, so nothing beneath it lights up
		boolean covered = this.overlayOpen();
		this.renderer.render(ctx, entry, covered ? -1 : mouseX, covered ? -1 : mouseY);
	}

	public boolean overlayOpen()
	{
		return this.screen.overwriteConfirm.isOpen() || this.screen.reportModal.isPickerOpen()
				|| this.screen.reportModal.isContextOpen() || this.screen.claimModal.isOpen();
	}

	public void tickSpectator()
	{
		this.camera.tick();
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		return this.actions.mouseClicked(mouseX, mouseY);
	}

	public boolean mouseDragged(double mouseX, double dragX, double dragY)
	{
		if (this.camera.draggingLayer && this.entry != null && this.model)
		{
			this.camera.setLayerFromMouse(mouseX);
			return true;
		}

		if (this.camera.orbiting && this.entry != null && this.model)
		{
			// Negated to match the GPU preview's Minecraft screen orientation
			this.camera.yaw = (this.camera.yaw - (float) dragX * 0.8F) % 360.0F;
			this.camera.pitch = Math.max(-88.0F, Math.min(88.0F, this.camera.pitch + (float) dragY * 0.8F));
			return true;
		}

		return false;
	}

	public void releaseDrags()
	{
		this.camera.orbiting = false;
		this.camera.draggingLayer = false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (this.entry == null)
		{
			return false;
		}

		if (this.collectionMenu.mouseScrolled(mouseX, mouseY, scrollY))
		{
			return true;
		}

		if (this.collectionMenu.isOpen())
		{
			this.collectionMenu.close();
			return true;
		}

		if (this.overlayOpen())
		{
			return true;
		}

		if (this.model && this.camera.freeLook && this.camera.freeEye != null
				&& this.imageRect.contains(mouseX, mouseY))
		{
			this.camera.adjustSpeed(scrollY);
		}
		else if (this.model && !this.camera.freeLook && this.imageRect.contains(mouseX, mouseY))
		{
			float factor = scrollY > 0 ? 1.18F : 1.0F / 1.18F;
			this.camera.zoom = SchematicPreview.clampZoom(this.entry.schematicSlot(), this.camera.zoom * factor);
		}
		else if (this.descMaxScroll > 0.0F)
		{
			this.descScroll = Math.max(0.0F,
					Math.min(this.descMaxScroll, this.descScroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (this.entry != null && this.model && this.camera.freeLook && this.camera.freeEye != null
				&& DetailCamera.isSpectatorKey(event.key()))
		{
			this.camera.pressKey(event.key());
			return true;
		}

		if (event.key() == 256 && this.entry != null)
		{
			if (this.collectionMenu.isOpen())
			{
				this.collectionMenu.close();
				return true;
			}

			if (this.screen.overwriteConfirm.isOpen())
			{
				this.screen.overwriteConfirm.cancel();
				return true;
			}

			if (this.screen.reportModal.isPickerOpen())
			{
				this.screen.reportModal.closePicker();
				return true;
			}

			this.close();
			return true;
		}

		return false;
	}

	public boolean imageKeyPressed(KeyEvent event)
	{
		if (this.entry != null && !this.model && this.entry.imageCount() > 1
				&& (event.key() == 262 || event.key() == 263))
		{
			Theme.click();
			int delta = event.key() == 262 ? 1 : -1;
			this.image = Math.floorMod(this.image + delta, this.entry.imageCount());
			this.preloadGallery();
			return true;
		}

		return false;
	}

	public void keyReleased(KeyEvent event)
	{
		if (DetailCamera.isSpectatorKey(event.key()))
		{
			this.camera.releaseKey(event.key());
		}
	}

	// Only the visible frame and the next: a whole gallery at 2.4 MB a frame evicted every grid thumbnail
	void preloadGallery()
	{
		SchematicEntry entry = this.entry;

		if (entry == null || entry.imageCount() <= 0)
		{
			return;
		}

		for (int offset = 0; offset < Math.min(2, entry.imageCount()); offset++)
		{
			this.screen.imageTexture(entry, Math.floorMod(this.image + offset, entry.imageCount()));
		}
	}

	public Rect[] pressButtons()
	{
		return new Rect[]{this.closeButton, this.cornerClose, this.report, this.claim,
				this.save,
				this.follow, this.load, this.download, this.preview3d, this.camera.resetViewButton,
				this.camera.cutawayToggle, this.camera.spectatorButton, this.camera.orbitButton, this.savePngButton,
				this.copyPngButton};
	}

	// The server's average is folded back in only when no newer click is queued
	public void flushPendingRate(boolean force)
	{
		if (this.pendingRateValue < 0 || this.pendingRateId == null)
		{
			return;
		}

		if (!force && System.currentTimeMillis() - this.pendingRateAt < RATE_DEBOUNCE_MS)
		{
			return;
		}

		String id = this.pendingRateId;
		int value = this.pendingRateValue;
		this.pendingRateValue = -1;
		this.pendingRateId = null;

		Thread worker = new Thread(() -> {
			Backend.ApiResult result = Backend.rate(id, value);

			if (result.ok())
			{
				Shards.pokeSoon();
				Minecraft.getInstance().execute(() -> {
					if (this.pendingRateValue < 0 && this.entry != null && this.entry.id().equals(id))
					{
						this.starAvg = Json.doubleOf(result.body(), "starAvg", this.starAvg);
						this.starCount = Json.intOf(result.body(), "starCount", this.starCount);
						this.myStars = Json.intOf(result.body(), "myStars", value);
						this.committedStars = this.myStars;
					}
				});
			}
			else if (Backend.configured())
			{
				// A real send failure, not an unconfigured backend, so a lost rating is not swallowed
				Minecraft.getInstance().execute(() -> {
					if (this.pendingRateValue < 0 && this.entry != null && this.entry.id().equals(id))
					{
						this.myStars = this.committedStars;
					}

					Toasts.refusal(result, Errors.RATE);
				});
			}
		}, "schematicindex-rate");
		worker.setDaemon(true);
		worker.start();
	}

	// A second click on the same star drops to a half star, a third clears the rating
	void rateStar(SchematicEntry entry, int starIndex)
	{
		int full = (starIndex + 1) * 2;
		int value = this.myStars == full ? full - 1 : (this.myStars == full - 1 ? 0 : full);
		this.queueRating(entry, value);
	}

	// An unverified user still sees the chip; clicking runs the silent verification first
	boolean canShowClaim(SchematicEntry entry)
	{
		if (entry == null || entry.id() == null || entry.id().isBlank() || !Backend.configured())
		{
			return false;
		}

		if (UploaderAccess.unlocked() && UploaderAccess.profile() != null
				&& UploaderAccess.profile().equalsIgnoreCase(entry.poster()))
		{
			return false;
		}

		String me = McAuth.verifiedName();
		return me == null || entry.designer() == null || !me.equalsIgnoreCase(entry.designer().trim());
	}

	private void queueRating(SchematicEntry entry, int value)
	{
		this.myStars = value;
		Theme.rate();
		this.pendingRateId = entry.id();
		this.pendingRateValue = value;
		this.pendingRateAt = System.currentTimeMillis();
	}

	record Layout(int x, int y, int width, int height, int pad, int imageWidth, int imageHeight,
			int infoX, int infoWidth)
	{
	}
}

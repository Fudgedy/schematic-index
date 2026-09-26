package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.SpectatorCamera;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Tabs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// A full-screen studio over the upload form: frame the local schematic, shoot 1920x1080 stills, keep the best.
// Nothing leaves the client until the form itself is posted
public class PhotoMode implements Overlay
{
	private static final long HINT_MS = 1800L;
	private static final int FOV_MIN = 30;
	private static final int FOV_RANGE = 80;
	private static final float FRAME_PITCH = 30.0F;
	private static final int SLIDER_HEIGHT = 10;
	private static final int MAX_TILE_WIDTH = 112;
	private static final String[] BACKGROUNDS = {"Sky", "Dark", "Light"};
	private static final int[] BACKGROUND_COLORS = {SchematicPreview.BACKGROUND_SKY, SchematicPreview.BACKGROUND,
			SchematicPreview.BACKGROUND_LIGHT};

	private final IndexScreen screen;
	private final PhotoStrip strip = new PhotoStrip();
	private final PhotoFrame frame;
	private final PhotoCapture capture = new PhotoCapture(this, this.strip);
	private @Nullable Path registered;
	private int slot = -1;
	private int capacity;
	private boolean coverLeads;
	private boolean open;
	private boolean framed;
	private String hint = "";
	private boolean hintIsError;
	private long hintAt;
	private float yaw;
	private float pitch;
	private float zoom;
	private float layer;
	private int fov;
	private int background;
	boolean guide;
	private boolean orbiting;
	private boolean draggingLayer;
	private boolean draggingFov;
	private boolean freeLook;
	private double @Nullable [] freeEye;
	private final SpectatorCamera fly = new SpectatorCamera();
	final Rect viewport = new Rect();
	private final Rect autoFrameButton = new Rect();
	private final Rect guideToggle = new Rect();
	private final Rect spectatorToggle = new Rect();
	private final List<Rect> backgroundHits = new ArrayList<>();
	private final Rect cancelButton = new Rect();
	private final Rect doneButton = new Rect();
	private final Rect shutterButton = new Rect();
	private final Rect layerSlider = new Rect();
	private final Rect fovSlider = new Rect();

	public PhotoMode(IndexScreen screen)
	{
		this.screen = screen;
		this.frame = new PhotoFrame(this);
	}

	@Override
	public boolean isOpen()
	{
		return this.open;
	}

	// capacity is the pictures the form can still take; coverLeads when the first photo will be its thumbnail
	public void open(Path schematic, int capacity, boolean coverLeads)
	{
		if (!schematic.equals(this.registered))
		{
			this.slot = SchematicPreview.register(schematic);
			this.registered = schematic;
		}

		this.strip.clear();
		this.capacity = capacity;
		this.coverLeads = coverLeads;
		this.framed = false;
		this.yaw = 45.0F;
		this.pitch = FRAME_PITCH;
		this.zoom = 1.0F;
		this.layer = 1.0F;
		this.fov = Settings.previewFov();
		this.background = 1;
		this.guide = true;
		this.freeLook = false;
		this.freeEye = null;
		this.fly.clearKeys();
		this.fly.resetSpeed();
		this.hint = "";
		this.open = true;
		SchematicIndexMod.LOGGER.debug("Photo Mode opened for {} with room for {}", schematic.getFileName(), capacity);
	}

	// Idempotent; frees every photo texture and drops in-flight captures
	public void close()
	{
		this.capture.cancel();
		this.strip.clear();
		this.open = false;
		this.releaseDrags();
		this.fly.clearKeys();
		SchematicPreview.clearOverrides();
	}

	public void releaseDrags()
	{
		if (this.draggingLayer || this.draggingFov)
		{
			Theme.click(1.0F);
		}

		this.orbiting = false;
		this.draggingLayer = false;
		this.draggingFov = false;
	}

	// Called every frame regardless of which overlay is on top, matching the detail view's own free-fly tick
	public void tickSpectator()
	{
		if (!this.open || !this.freeLook || this.freeEye == null)
		{
			return;
		}

		double[] size = SchematicPreview.modelSize(this.slot);

		if (size == null)
		{
			return;
		}

		double span = Math.max(size[0], Math.max(size[1], size[2]));
		double step = Math.max(4.0D, span) * 0.06D;
		this.fly.tick(this.freeEye, this.yaw, this.pitch, step, size[0], size[1], size[2]);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		int width = this.screen.width;
		int height = this.screen.height;
		ctx.fill(0, 0, width, height, Theme.BACKDROP);

		SchematicPreview.overrideView(this.fov, BACKGROUND_COLORS[this.background]);
		SchematicPreview.request(this.slot, this.yaw, this.pitch, this.zoom, true, this.freeLook, this.freeEye,
				this.layer);

		if (!this.framed && SchematicPreview.layerHeight(this.slot) > 0)
		{
			this.framed = true;
			this.autoFrame();
		}

		int toolbarBottom = this.renderToolbar(ctx, font, width, mouseX, mouseY);
		int panelTop = this.renderPanel(ctx, font, width, height, mouseX, mouseY);

		int areaWidth = width - Theme.SPACE_L * 2;
		int areaHeight = panelTop - toolbarBottom - Theme.SPACE_S * 2;
		int viewWidth = Math.max(0, Math.min(areaWidth, areaHeight * 16 / 9));
		int viewHeight = viewWidth * 9 / 16;
		this.viewport.set((width - viewWidth) / 2, toolbarBottom + Theme.SPACE_S + (areaHeight - viewHeight) / 2,
				viewWidth, viewHeight);
		this.frame.render(ctx, font, this.slot, this.capture.flashAt());
		this.renderHint(ctx, font);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.doneButton.contains(mouseX, mouseY))
		{
			Theme.buttonPress(this.doneButton);
			Theme.click();
			this.done();
			return true;
		}

		if (this.cancelButton.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.close();
			return true;
		}

		if (this.autoFrameButton.contains(mouseX, mouseY))
		{
			Theme.buttonPress(this.autoFrameButton);
			Theme.click();
			this.autoFrame();
			return true;
		}

		if (this.guideToggle.contains(mouseX, mouseY))
		{
			this.guide = !this.guide;
			Theme.click();
			return true;
		}

		if (this.spectatorToggle.contains(mouseX, mouseY))
		{
			this.setSpectator(!this.freeLook);
			return true;
		}

		int picked = Tabs.hit(this.backgroundHits, mouseX, mouseY);

		if (picked >= 0)
		{
			this.background = picked;
			Theme.tab();
			return true;
		}

		if (this.shutterButton.contains(mouseX, mouseY))
		{
			Theme.buttonPress(this.shutterButton);
			this.capture(PhotoStrip.NO_PHOTO);
			return true;
		}

		if (this.layerSlider.contains(mouseX, mouseY))
		{
			this.draggingLayer = true;
			this.setLayer(mouseX);
			return true;
		}

		if (this.fovSlider.contains(mouseX, mouseY))
		{
			this.draggingFov = true;
			this.setFov(mouseX);
			return true;
		}

		if (this.strip.mouseClicked(mouseX, mouseY, this.coverLeads || this.strip.coverChosen, this::capture))
		{
			return true;
		}

		if (!this.viewport.contains(mouseX, mouseY))
		{
			return true;
		}

		if (SchematicPreview.failed(this.slot))
		{
			SchematicPreview.retry(this.slot);
			Theme.click();
			return true;
		}

		this.orbiting = true;
		return true;
	}

	public boolean mouseDragged(double mouseX, double dragX, double dragY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.draggingLayer)
		{
			this.setLayer(mouseX);
		}
		else if (this.draggingFov)
		{
			this.setFov(mouseX);
		}
		else if (this.orbiting)
		{
			// Same handedness as the detail view's orbit, which matches the GPU preview's screen orientation
			this.yaw = (this.yaw - (float) dragX * 0.8F) % 360.0F;
			this.pitch = Math.max(-88.0F, Math.min(88.0F, this.pitch + (float) dragY * 0.8F));
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (!this.viewport.contains(mouseX, mouseY))
		{
			return true;
		}

		if (this.freeLook)
		{
			this.fly.adjustSpeed(scrollY);
		}
		else
		{
			float factor = scrollY > 0 ? 1.18F : 1.0F / 1.18F;
			this.zoom = SchematicPreview.clampZoom(this.slot, this.zoom * factor);
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (this.freeLook && SpectatorCamera.isKey(event.key()))
		{
			this.fly.pressKey(event.key());
			return true;
		}

		if (event.key() == 256) // escape
		{
			Theme.click(0.9F);
			this.close();
		}
		else if (event.key() == 32) // space
		{
			this.capture(PhotoStrip.NO_PHOTO);
		}

		return true;
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		if (SpectatorCamera.isKey(event.key()))
		{
			this.fly.releaseKey(event.key());
		}
	}

	private void done()
	{
		List<byte[]> photos = this.strip.pngs();
		boolean coverFirst = this.coverLeads || this.strip.coverChosen;
		this.close();

		if (!photos.isEmpty())
		{
			this.screen.uploadPage.addPhotos(photos, coverFirst);
		}
	}

	// Snaps to the nearest three-quarter diagonal and pulls back until every corner of the build is in frame
	private void autoFrame()
	{
		this.yaw = Math.round((this.yaw - 45.0F) / 90.0F) * 90.0F + 45.0F;
		this.pitch = FRAME_PITCH;
		this.layer = 1.0F;
		this.zoom = SchematicPreview.fitZoom(this.slot, this.yaw, this.pitch, this.fov);
	}

	private void capture(long replaceId)
	{
		this.capture.shoot(this.slot, this.capacity, replaceId);
	}

	private void setSpectator(boolean spectator)
	{
		this.freeLook = spectator;
		Theme.click(spectator ? 1.3F : 0.9F);
		this.freeEye = spectator ? SchematicPreview.eye(this.slot, this.yaw, this.pitch, this.zoom, true) : null;
		this.fly.clearKeys();
		this.fly.resetSpeed();
	}

	void hint(String text, boolean error)
	{
		this.hint = text;
		this.hintIsError = error;
		this.hintAt = System.currentTimeMillis();
	}

	private void setLayer(double mouseX)
	{
		int total = Math.max(1, SchematicPreview.layerHeight(this.slot));
		float fraction = (float) Math.max(0.0D, Math.min(1.0D, (mouseX - this.layerSlider.x) / this.layerSlider.width));
		this.layer = (float) Math.max(1, Math.round(fraction * total)) / total;
	}

	private void setFov(double mouseX)
	{
		float fraction = (float) Math.max(0.0D, Math.min(1.0D, (mouseX - this.fovSlider.x) / this.fovSlider.width));
		this.fov = FOV_MIN + Math.round(fraction * FOV_RANGE);
	}

	private int renderToolbar(GuiGraphics ctx, Font font, int width, int mouseX, int mouseY)
	{
		int barHeight = Theme.H_CONTROL + Theme.SPACE_S * 2;
		ctx.fill(0, 0, width, barHeight, Theme.SURFACE);
		ctx.fill(0, barHeight - 1, width, barHeight, Theme.HAIRLINE);

		int y = Theme.SPACE_S;
		int autoWidth = Buttons.width(font, "Auto-frame");
		int guideWidth = font.width("Guide") + Theme.SPACE_L + Theme.SPACE_S;
		int spectatorWidth = font.width("Spectator") + Theme.SPACE_L + Theme.SPACE_S;
		int segmentedWidth = Theme.BUTTON_MIN_WIDTH * 2 + Theme.SPACE_L;
		int doneWidth = Buttons.width(font, "Cancel");

		// Everything past the title has a fixed width; the title takes what's left and clips at it,
		// so a small window can never push the segmented control past the right-anchored Cancel/Done
		int reserved = Theme.SPACE_L + Theme.SPACE_XL + autoWidth + Theme.SPACE_S + guideWidth + Theme.SPACE_S
				+ spectatorWidth + Theme.SPACE_S + segmentedWidth + doneWidth * 2 + Theme.SPACE_S + Theme.SPACE_L;
		String title = Theme.bold(Theme.clip(font, "3D Image Selector", Math.max(20, width - reserved)));

		int x = Theme.SPACE_L;
		Theme.text(ctx, font, title, x, y + (Theme.H_CONTROL - font.lineHeight) / 2 + 1, Theme.TEXT);
		x += font.width(title) + Theme.SPACE_XL;

		this.autoFrameButton.set(x, y, autoWidth, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.autoFrameButton, "Auto-frame", Buttons.Kind.SECONDARY, true, mouseX, mouseY);
		x += autoWidth + Theme.SPACE_S;

		this.guideToggle.set(x, y, guideWidth, Theme.H_CONTROL);
		Controls.toggle(ctx, font, this.guideToggle, "Guide", this.guide, mouseX, mouseY);
		x += guideWidth + Theme.SPACE_S;

		this.spectatorToggle.set(x, y, spectatorWidth, Theme.H_CONTROL);
		Controls.toggle(ctx, font, this.spectatorToggle, "Spectator", this.freeLook, mouseX, mouseY);
		x += spectatorWidth + Theme.SPACE_S;

		Tabs.segmented(ctx, font, this.backgroundHits, BACKGROUNDS, this.background, x, y, segmentedWidth, mouseX, mouseY);

		this.doneButton.set(width - Theme.SPACE_L - doneWidth, y, doneWidth, Theme.H_CONTROL);
		this.cancelButton.set(this.doneButton.x - Theme.SPACE_S - doneWidth, y, doneWidth, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.cancelButton, "Cancel", Buttons.Kind.SECONDARY, true, mouseX, mouseY);
		Buttons.button(ctx, font, this.doneButton, "Done", Buttons.Kind.PRIMARY, true, mouseX, mouseY);
		return barHeight;
	}

	// Strip on the left, lens sliders and the shutter on the right; returns the panel's top edge
	private int renderPanel(GuiGraphics ctx, Font font, int width, int height, int mouseX, int mouseY)
	{
		int shutterWidth = Buttons.width(font, "Take photo");
		int sliderWidth = Math.min(160, width / 5);
		int shutterX = width - Theme.SPACE_L - shutterWidth;
		int sliderX = shutterX - Theme.SPACE_L - sliderWidth;
		int stripWidth = sliderX - Theme.SPACE_XL - Theme.SPACE_L;
		int tileWidth = Math.min(MAX_TILE_WIDTH, (stripWidth - Theme.SPACE_S * 4) / 5);
		int tileHeight = tileWidth * 9 / 16;
		int panelHeight = Theme.SPACE_S * 3 + font.lineHeight + Theme.SPACE_XS + tileHeight + Theme.H_CONTROL;
		int top = height - panelHeight;
		ctx.fill(0, top, width, height, Theme.SURFACE);
		ctx.fill(0, top, width, top + 1, Theme.HAIRLINE);

		int x = Theme.SPACE_L;
		int y = top + Theme.SPACE_S;
		Theme.text(ctx, font, Theme.bold("Photos"), x, y, Theme.TEXT);
		String count = this.strip.size() + " / " + this.capacity;
		int countRight = x + this.capacity * (tileWidth + Theme.SPACE_S) - Theme.SPACE_S;
		Theme.text(ctx, font, count, countRight - font.width(count), y, Theme.TEXT_ASH);

		int tilesY = y + font.lineHeight + Theme.SPACE_XS;
		this.strip.render(ctx, font, x, tilesY, tileWidth, this.capacity, this.coverLeads || this.strip.coverChosen,
				mouseX, mouseY);

		int total = Math.max(1, SchematicPreview.layerHeight(this.slot));
		int shown = Math.max(1, Math.min(total, Math.round(this.layer * total)));
		int sliderY = this.slider(ctx, font, this.layerSlider, "Layers", shown + " / " + total, this.layer,
				this.draggingLayer, sliderX, tilesY, sliderWidth, mouseX, mouseY);
		this.slider(ctx, font, this.fovSlider, "FOV", Integer.toString(this.fov),
				(this.fov - FOV_MIN) / (float) FOV_RANGE, this.draggingFov, sliderX, sliderY + Theme.SPACE_S,
				sliderWidth, mouseX, mouseY);

		boolean ready = SchematicPreview.texture(this.slot) != null && this.strip.size() < this.capacity;
		this.shutterButton.set(shutterX, tilesY + (tileHeight - Theme.H_CONTROL_L) / 2, shutterWidth,
				Theme.H_CONTROL_L);
		String shutterLabel = this.capture.isBusy() ? "..." : "Take photo";
		Buttons.button(ctx, font, this.shutterButton, shutterLabel, Buttons.Kind.PRIMARY, ready, mouseX, mouseY);
		return top;
	}

	private int slider(GuiGraphics ctx, Font font, Rect track, String label, String value, float fraction,
			boolean dragging, int x, int y, int width, int mouseX, int mouseY)
	{
		Theme.text(ctx, font, label, x, y, Theme.TEXT_ASH);
		Theme.text(ctx, font, value, x + width - font.width(value), y, Theme.TEXT);
		y += font.lineHeight + Theme.SPACE_2XS;
		track.set(x, y, width, SLIDER_HEIGHT);
		Controls.slider(ctx, track, fraction, mouseX, mouseY, dragging);
		return y + SLIDER_HEIGHT;
	}

	// Errors stay until the next action replaces them; plain hints fade out. While flying, the fly
	// controls hint takes over once nothing else needs the chip
	private void renderHint(GuiGraphics ctx, Font font)
	{
		long age = System.currentTimeMillis() - this.hintAt;
		boolean expired = this.hint.isEmpty() || (!this.hintIsError && age > HINT_MS);
		String text = expired ? (this.freeLook ? SpectatorCamera.HINT : "") : this.hint;
		boolean error = !expired && this.hintIsError;

		if (text.isEmpty() || this.viewport.width <= 0)
		{
			return;
		}

		text = Theme.clip(font, text, this.viewport.width - Theme.SPACE_L * 2);
		int chipWidth = font.width(text) + Theme.SPACE_S * 2;
		int chipX = this.viewport.x + (this.viewport.width - chipWidth) / 2;
		int chipY = this.viewport.y + Theme.SPACE_S;
		Theme.roundedRect(ctx, chipX, chipY, chipWidth, Theme.H_CONTROL, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, chipX, chipY, chipWidth, Theme.H_CONTROL, Theme.RADIUS_PILL, Theme.HAIRLINE_STRONG);
		Theme.text(ctx, font, text, chipX + Theme.SPACE_S, chipY + (Theme.H_CONTROL - font.lineHeight) / 2 + 1,
				error ? Theme.DANGER_TEXT : Theme.TEXT_MUTE);
	}
}

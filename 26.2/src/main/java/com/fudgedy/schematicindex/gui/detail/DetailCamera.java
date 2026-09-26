package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.SpectatorCamera;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

// The 3D preview's orbit and free-fly camera, plus the layer slider that trims it
public class DetailCamera
{
	// Posts already counted as flown this launch, so toggling the mode on one build is one quest event
	private static final Set<String> FLOWN = new HashSet<>();

	private final DetailView view;
	float yaw = 35.0F;
	float pitch = 28.0F;
	float zoom = 1.0F;
	float layer = 1.0F;
	boolean orbiting;
	boolean cutaway = true;
	boolean freeLook;
	double @Nullable [] freeEye;
	private final SpectatorCamera fly = new SpectatorCamera();
	boolean draggingLayer;
	final Rect cutawayToggle = new Rect();
	final Rect spectatorButton = new Rect();
	final Rect orbitButton = new Rect();
	final Rect resetViewButton = new Rect();
	final Rect layerSlider = new Rect();

	public DetailCamera(DetailView view)
	{
		this.view = view;
	}

	public static boolean isSpectatorKey(int key)
	{
		return SpectatorCamera.isKey(key);
	}

	public void tick()
	{
		double[] eye = this.freeEye;
		SchematicEntry entry = this.view.entry();

		if (!this.view.model || !this.freeLook || eye == null || entry == null)
		{
			return;
		}

		this.fly.tick(eye, this.yaw, this.pitch, this.freeLookStep(), entry.sizeX(), entry.sizeY(), entry.sizeZ());
	}

	public void setMode(boolean spectator, SchematicEntry entry)
	{
		this.freeLook = spectator;
		Theme.click(spectator ? 1.3F : 0.9F);

		if (spectator)
		{
			Usage.once("preview_fly");
		}

		if (spectator && FLOWN.add(entry.id()))
		{
			Backend.postEvent("fly");
			Shards.pokeSoon();
		}

		this.freeEye = spectator
				? SchematicPreview.eye(entry.schematicSlot(), this.yaw, this.pitch,
						this.zoom, this.cutaway)
				: null;
		this.fly.clearKeys();
		this.fly.resetSpeed();
		this.view.screen.status = spectator ? SpectatorCamera.HINT : "";
	}

	public void reset()
	{
		this.yaw = 35.0F;
		this.pitch = 28.0F;
		this.zoom = 1.0F;
		this.layer = 1.0F;
		this.freeLook = false;
		this.freeEye = null;
		this.fly.clearKeys();
		this.view.screen.status = "";
	}

	public void clearHeldKeys()
	{
		this.fly.clearKeys();
	}

	public void pressKey(int key)
	{
		this.fly.pressKey(key);
	}

	public void releaseKey(int key)
	{
		this.fly.releaseKey(key);
	}

	public void setLayerFromMouse(double mouseX)
	{
		if (this.layerSlider.width <= 0 || this.view.entry() == null)
		{
			return;
		}

		float fraction = (float) ((mouseX - this.layerSlider.x) / this.layerSlider.width);
		fraction = Math.max(0.0F, Math.min(1.0F, fraction));

		int total = this.layerHeight();
		int layers = Math.max(1, Math.min(total, Math.round(fraction * total)));
		this.layer = (float) layers / total;
	}

	void renderControls(GuiGraphicsExtractor ctx, Font font, int infoX, int line, int infoWidth,
			int mouseX, int mouseY)
	{
		line += 6;
		Theme.text(ctx, font, "View", infoX, line, Theme.TEXT_ASH);
		line += font.lineHeight + 3;

		this.cutawayToggle.set(infoX, line, infoWidth, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, font, this.cutawayToggle, "Hide Close Blocks when Orbiting", this.cutaway,
				mouseX, mouseY, this.freeLook);
		line += IndexScreen.FIELD_HEIGHT + 4;

		int modeGap = 4;
		int modeHalf = (infoWidth - modeGap) / 2;
		this.spectatorButton.set(infoX, line, modeHalf, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.spectatorButton, "Spectator", mouseX, mouseY, this.freeLook);
		this.orbitButton.set(infoX + modeHalf + modeGap, line, infoWidth - modeHalf - modeGap,
				IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.orbitButton, "Orbit", mouseX, mouseY, !this.freeLook);
		line += IndexScreen.FIELD_HEIGHT + 6;

		int totalLayers = this.layerHeight();
		int shownLayers = Math.max(1, Math.min(totalLayers, Math.round(this.layer * totalLayers)));
		String layerLabel = shownLayers + " / " + totalLayers;
		Theme.text(ctx, font, "Layers", infoX, line, Theme.TEXT_ASH);
		Theme.text(ctx, font, layerLabel, infoX + infoWidth - font.width(layerLabel), line, Theme.TEXT);
		line += font.lineHeight + 3;
		this.layerSlider.set(infoX, line, infoWidth, 10);
		Controls.slider(ctx, this.layerSlider, this.layer, mouseX, mouseY, this.draggingLayer);
		line += 10 + 6;

		this.resetViewButton.set(infoX, line, infoWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.resetViewButton, "Reset view", mouseX, mouseY, false);
	}

	void clearControls()
	{
		this.cutawayToggle.set(0, 0, 0, 0);
		this.spectatorButton.set(0, 0, 0, 0);
		this.orbitButton.set(0, 0, 0, 0);
		this.layerSlider.set(0, 0, 0, 0);
		this.resetViewButton.set(0, 0, 0, 0);
	}

	int layerHeight()
	{
		SchematicEntry entry = this.view.entry();

		if (entry == null)
		{
			return 1;
		}

		// The real Y size, so the slider reads in block layers rather than downsampled model layers
		int real = entry.sizeY();

		if (real > 0)
		{
			return real;
		}

		int height = SchematicPreview.layerHeight(entry.schematicSlot());
		return height > 0 ? height : 1;
	}

	private double freeLookStep()
	{
		SchematicEntry entry = this.view.entry();

		if (entry == null)
		{
			return 1.0D;
		}

		double span = Math.max(entry.sizeX(), Math.max(entry.sizeY(), entry.sizeZ()));
		return Math.max(4.0D, span) * 0.06D;
	}

	void adjustSpeed(double scrollY)
	{
		// In free-look the wheel adjusts fly speed, not zoom; tick caps the absolute value
		this.fly.adjustSpeed(scrollY);
	}
}

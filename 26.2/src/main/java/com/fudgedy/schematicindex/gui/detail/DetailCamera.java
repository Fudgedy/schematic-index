package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.SchematicPreview;
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
	private static final double MAX_SPECTATOR_SPEED = 120.0D; // blocks/sec
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
	private final Set<Integer> spectatorKeys = new HashSet<>(); // GLFW key codes
	private long spectatorMoveNanos; // nanoTime of the previous move tick, 0 when not moving
	private float spectatorSpeed = 1.0F;
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
		return key == 87 || key == 83 || key == 65 || key == 68 || key == 32 || key == 340 || key == 341;
	}

	// Wall-clock dt, so held keys glide instead of stepping once per key-repeat
	public void tick()
	{
		double[] eye = this.freeEye;
		SchematicEntry entry = this.view.entry();

		if (!this.view.model || !this.freeLook || eye == null || entry == null
				|| this.spectatorKeys.isEmpty())
		{
			this.spectatorMoveNanos = 0L;
			return;
		}

		long now = System.nanoTime();

		if (this.spectatorMoveNanos == 0L)
		{
			this.spectatorMoveNanos = now;
			return;
		}

		// Clamped so a hitch cannot fling the camera across the scene in one jump
		double dt = Math.min((now - this.spectatorMoveNanos) / 1_000_000_000.0D, 0.1D);
		this.spectatorMoveNanos = now;

		if (dt <= 0.0D)
		{
			return;
		}

		double yaw = Math.toRadians(this.yaw);
		double pitch = Math.toRadians(this.pitch);
		double cosPitch = Math.cos(pitch);
		double forwardX = Math.sin(yaw) * cosPitch;
		double forwardY = -Math.sin(pitch);
		double forwardZ = Math.cos(yaw) * cosPitch;
		// Must equal the renderer's screen-right, forward x worldUp, or A and D invert against the view
		double rightX = -Math.cos(yaw);
		double rightZ = Math.sin(yaw);

		double speed = Math.min(this.freeLookStep() * 15.0D * this.spectatorSpeed, MAX_SPECTATOR_SPEED);
		double dist = speed * dt;

		if (this.spectatorKeys.contains(87))
		{ // W: forward
			eye[0] += forwardX * dist;
			eye[1] += forwardY * dist;
			eye[2] += forwardZ * dist;
		}

		if (this.spectatorKeys.contains(83))
		{ // S: back
			eye[0] -= forwardX * dist;
			eye[1] -= forwardY * dist;
			eye[2] -= forwardZ * dist;
		}

		if (this.spectatorKeys.contains(65))
		{ // A: strafe left
			eye[0] -= rightX * dist;
			eye[2] -= rightZ * dist;
		}

		if (this.spectatorKeys.contains(68))
		{ // D: strafe right
			eye[0] += rightX * dist;
			eye[2] += rightZ * dist;
		}

		if (this.spectatorKeys.contains(32))
		{ // Space: up
			eye[1] += dist;
		}

		if (this.spectatorKeys.contains(340) || this.spectatorKeys.contains(341))
		{ // Shift: down
			eye[1] -= dist;
		}

		// The eye may pull back a size-scaled margin beyond each face, so an overview never shrinks the build to a speck
		double span = Math.max(entry.sizeX(), Math.max(entry.sizeY(), entry.sizeZ()));
		double margin = Math.max(span * 1.5D, 24.0D);
		eye[0] = Math.max(-margin, Math.min(entry.sizeX() + margin, eye[0]));
		eye[1] = Math.max(-margin, Math.min(entry.sizeY() + margin, eye[1]));
		eye[2] = Math.max(-margin, Math.min(entry.sizeZ() + margin, eye[2]));
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
		this.spectatorKeys.clear();
		this.spectatorMoveNanos = 0L;
		this.spectatorSpeed = 1.0F;
		this.view.screen.status = spectator
				? "Spectator: drag to look, WASD to fly, Space/Shift up/down, scroll to change speed."
				: "";
	}

	public void reset()
	{
		this.yaw = 35.0F;
		this.pitch = 28.0F;
		this.zoom = 1.0F;
		this.layer = 1.0F;
		this.freeLook = false;
		this.freeEye = null;
		this.spectatorKeys.clear();
		this.spectatorMoveNanos = 0L;
		this.view.screen.status = "";
	}

	public void clearHeldKeys()
	{
		this.spectatorKeys.clear();
		this.spectatorMoveNanos = 0L;
	}

	public void pressKey(int key)
	{
		this.spectatorKeys.add(key);
	}

	public void releaseKey(int key)
	{
		this.spectatorKeys.remove(key);
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
		float factor = scrollY > 0 ? 1.3F : 1.0F / 1.3F;
		this.spectatorSpeed = Math.max(0.005F, Math.min(12.0F, this.spectatorSpeed * factor));
	}
}

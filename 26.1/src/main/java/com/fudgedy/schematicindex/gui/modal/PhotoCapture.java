package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.Theme;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

// Photo Mode's shutter: rate-limited full-size captures of the shown preview, developed off-thread into the strip
final class PhotoCapture
{
	private static final long COOLDOWN_MS = 1000L;

	private final PhotoMode mode;
	private final PhotoStrip strip;
	// Bumped on cancel, so a capture still reading back from the GPU is dropped on arrival
	private volatile long generation;
	private boolean busy;
	private long lastAt;
	private long flashAt;

	PhotoCapture(PhotoMode mode, PhotoStrip strip)
	{
		this.mode = mode;
		this.strip = strip;
	}

	boolean isBusy()
	{
		return this.busy;
	}

	long flashAt()
	{
		return this.flashAt;
	}

	void cancel()
	{
		this.generation++;
		this.busy = false;
		this.strip.retaking = PhotoStrip.NO_PHOTO;
	}

	// replaceId is the photo a retake overwrites, or NO_PHOTO for a new one
	void shoot(int slot, int capacity, long replaceId)
	{
		long now = System.currentTimeMillis();

		if (this.busy || now - this.lastAt < COOLDOWN_MS)
		{
			this.mode.hint("One photo per second.", false);
			return;
		}

		if (replaceId == PhotoStrip.NO_PHOTO && this.strip.size() >= capacity)
		{
			this.mode.hint("The strip is full. Delete a photo to take another.", false);
			return;
		}

		if (SchematicPreview.texture(slot) == null)
		{
			this.mode.hint("Wait for the preview to finish loading.", false);
			return;
		}

		this.busy = true;
		this.strip.retaking = replaceId;
		this.lastAt = now;
		this.flashAt = now;
		Theme.shutter();
		long ticket = this.generation;
		SchematicPreview.capturePng(slot, png -> this.develop(ticket, capacity, replaceId, png));
	}

	// Called on the Net pool with the encoded frame, or on the client thread with null when nothing rendered
	private void develop(long ticket, int capacity, long replaceId, byte @Nullable [] png)
	{
		NativeImage preview = null;

		if (png != null && ticket == this.generation)
		{
			try (NativeImage full = NativeImage.read(png))
			{
				preview = SchematicPreview.downscale(full, PhotoStrip.PREVIEW_WIDTH, PhotoStrip.PREVIEW_HEIGHT);
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.warn("Could not decode photo", e);
			}
		}

		NativeImage developed = preview;
		Minecraft.getInstance().execute(() -> this.keep(ticket, capacity, replaceId, png, developed));
	}

	private void keep(long ticket, int capacity, long replaceId, byte @Nullable [] png, @Nullable NativeImage preview)
	{
		if (ticket != this.generation)
		{
			if (preview != null)
			{
				preview.close();
			}

			return;
		}

		this.busy = false;
		this.strip.retaking = PhotoStrip.NO_PHOTO;

		if (png == null || preview == null)
		{
			SchematicIndexMod.LOGGER.warn("Photo capture failed at {}", png == null ? "render" : "decode");
			this.mode.hint("Couldn't take the photo. (" + Errors.PHOTO + ")", true);
			return;
		}

		if (replaceId == PhotoStrip.NO_PHOTO && this.strip.size() < capacity)
		{
			this.strip.add(png, preview);
		}
		else if (replaceId == PhotoStrip.NO_PHOTO || !this.strip.replace(replaceId, png, preview))
		{
			// A retake whose photo is gone is dropped rather than landing in another photo's slot
			preview.close();
			return;
		}

		SchematicIndexMod.LOGGER.debug("Kept photo at {} KB", png.length / 1024);
	}
}

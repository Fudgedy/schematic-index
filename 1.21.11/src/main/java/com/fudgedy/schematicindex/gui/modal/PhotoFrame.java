package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

// Photo Mode's viewport: the live preview at the capture's 16:9, its loading and error states, the
// rule-of-thirds guide and the shutter flash
final class PhotoFrame
{
	static final long FLASH_MS = 220L;
	private static final float GUIDE_LINE_ALPHA = 0.3F;
	private static final float FLASH_ALPHA = 0.85F;

	private final PhotoMode mode;

	PhotoFrame(PhotoMode mode)
	{
		this.mode = mode;
	}

	void render(GuiGraphics ctx, Font font, int slot, long flashAt)
	{
		Rect view = this.mode.viewport;

		if (view.width <= 0 || view.height <= 0)
		{
			return;
		}

		Identifier texture = SchematicPreview.texture(slot);

		if (texture == null)
		{
			this.renderPending(ctx, font, slot, view);
			return;
		}

		// The GPU path hands back a bottom-left-origin framebuffer texture; the CPU path is top-left
		if (SchematicPreview.textureFlippedV())
		{
			Theme.imageFlippedV(ctx, texture, view.x, view.y, view.width, view.height,
					SchematicPreview.renderWidth(), SchematicPreview.renderHeight());
		}
		else
		{
			Theme.image(ctx, texture, view.x, view.y, view.width, view.height,
					SchematicPreview.renderWidth(), SchematicPreview.renderHeight());
		}

		if (this.mode.guide)
		{
			this.renderGuide(ctx, view);
		}

		float flash = (System.currentTimeMillis() - flashAt) / (float) FLASH_MS;

		if (flash >= 0.0F && flash < 1.0F)
		{
			ctx.fill(view.x, view.y, view.x + view.width, view.y + view.height,
					Theme.withAlpha(Theme.TEXT, FLASH_ALPHA * (1.0F - Theme.easeOut(flash))));
		}
	}

	private void renderPending(GuiGraphics ctx, Font font, int slot, Rect view)
	{
		Theme.blueprintPlaceholder(ctx, view.x, view.y, view.width, view.height);
		String message;

		if (SchematicPreview.failed(slot))
		{
			message = "Couldn't load the preview. Click to retry. (" + Errors.PREVIEW + ")";
		}
		else
		{
			String stage = SchematicPreview.loadingStage();
			int dots = (int) (System.currentTimeMillis() / 400 % 3) + 1;
			message = (stage.isEmpty() ? "Rendering" : stage) + ".".repeat(dots);
		}

		message = Theme.clip(font, message, view.width - Theme.SPACE_L * 2);
		Theme.text(ctx, font, message, view.x + (view.width - font.width(message)) / 2,
				view.y + (view.height - font.lineHeight) / 2, Theme.TEXT_MUTE);
	}

	// Pure 2D overlay drawn straight onto the already-rendered preview texture: no re-render, no re-mesh
	private void renderGuide(GuiGraphics ctx, Rect view)
	{
		int line = Theme.withAlpha(Theme.TEXT, GUIDE_LINE_ALPHA);

		for (int i = 1; i < 3; i++)
		{
			int x = view.x + view.width * i / 3;
			int y = view.y + view.height * i / 3;
			ctx.fill(x, view.y, x + 1, view.y + view.height, line);
			ctx.fill(view.x, y, view.x + view.width, y + 1, line);
		}
	}
}

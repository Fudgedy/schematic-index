package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class Buttons
{
	private Buttons()
	{
	}

	public static void pill(GuiGraphics ctx, Font font, Rect rect, String label, int mouseX, int mouseY,
			boolean primary)
	{
		pill(ctx, font, rect, label, mouseX, mouseY, primary, true);
	}

	// Single glyphs such as a stepper's minus and plus read as blobs in bold, so they can opt out
	public static void pill(GuiGraphics ctx, Font font, Rect rect, String label, int mouseX, int mouseY,
			boolean primary, boolean bold)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);

		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

		int fill = Theme.lighten(primary ? Theme.ACCENT : Theme.SURFACE_CARD, 0.12F * hover);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);

		if (hovered && !primary)
		{
			Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
		}

		String clipped = Theme.clip(font, label, rect.width - 8);
		String text = bold ? Theme.bold(clipped) : clipped;
		Theme.text(ctx, font, text,
				rect.x + (rect.width - font.width(text)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1,
				primary ? Theme.ON_ACCENT : Theme.TEXT);

		Theme.pop(ctx);
	}

	// Glossy gold pill for the premium tab: gold base plus a fading top sheen for a metallic look
	public static void gold(GuiGraphics ctx, Font font, Rect rect, String label, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);

		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);
		Theme.goldGloss(ctx, rect.x, rect.y, rect.width, rect.height, hover);

		String text = Theme.bold(Theme.clip(font, label, rect.width - 8));
		Theme.text(ctx, font, text,
				rect.x + (rect.width - font.width(text)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1,
				Theme.ON_GOLD);

		Theme.pop(ctx);
	}

	// Decorative stand-in for the upload live-preview: no hover, no outline, wired to no action
	public static void mock(GuiGraphics ctx, Font font, Rect rect, String label)
	{
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.SURFACE_CARD);

		String text = Theme.bold(Theme.clip(font, label, rect.width - 8));
		Theme.text(ctx, font, text,
				rect.x + (rect.width - font.width(text)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1,
				Theme.TEXT_MUTE);
	}

	public static void disabled(GuiGraphics ctx, Font font, Rect rect, String label)
	{
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
		String text = Theme.bold(label);
		Theme.text(ctx, font, text, rect.x + (rect.width - font.width(text)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1, Theme.TEXT_ASH);
	}

	public static void danger(GuiGraphics ctx, Font font, Rect rect, String label, int mouseX, int mouseY)
	{
		boolean hover = rect.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				hover ? 0xFFE05555 : 0xFFD64545);
		String text = Theme.bold(label);
		Theme.text(ctx, font, text, rect.x + (rect.width - font.width(text)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1, Theme.ON_ACCENT);
	}

	public static void save(GuiGraphics ctx, Font font, Rect rect, boolean saved, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		String label = saved ? "Saved" : "Save for later";

		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

		if (saved)
		{
			Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
					Theme.lighten(Theme.ACCENT, 0.12F * hover));
			Theme.text(ctx, font, Theme.bold(label),
					rect.x + (rect.width - font.width(Theme.bold(label))) / 2,
					rect.y + (rect.height - font.lineHeight) / 2 + 1, Theme.ON_ACCENT);
			Theme.pop(ctx);
			return;
		}

		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				Theme.lighten(Theme.SURFACE_CARD, 0.12F * hover));

		if (hovered)
		{
			Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
		}

		Theme.text(ctx, font, Theme.bold(label),
				rect.x + (rect.width - font.width(Theme.bold(label))) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1, Theme.TEXT);

		Theme.pop(ctx);
	}

	public static void download(GuiGraphics ctx, Font font, Rect rect, SchematicEntry entry, int mouseX,
			int mouseY, boolean locked)
	{
		boolean hovered = !locked && rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				Theme.lighten(Theme.ACCENT, 0.12F * hover));

		Download.Progress progress = Download.progress(entry.id());
		String label = "Download";

		// Display only: tickDownloads drives the terminal state so it fires with the button unrendered
		if (progress != null)
		{
			float fraction = switch (progress.state())
			{
				case DONE -> 1.0F;
				case RUNNING -> progress.fraction();
				case FAILED -> 0.0F;
			};

			int fillWidth = Math.round((rect.width - 2) * Math.max(0.0F, Math.min(1.0F, fraction)));

			if (fillWidth > 0)
			{
				Theme.roundedRect(ctx, rect.x + 1, rect.y + 1, fillWidth, rect.height - 2,
						Theme.RADIUS_PILL, Theme.DOWNLOAD_FILL);
			}

			label = switch (progress.state())
			{
				case RUNNING -> Math.round(fraction * 100) + "%";
				case DONE -> "Downloaded";
				case FAILED -> "Retry";
			};
		}

		String text = Theme.bold(label);
		Theme.text(ctx, font, text, rect.x + (rect.width - font.width(text)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1, Theme.ON_ACCENT);

		Theme.pop(ctx);
	}

	public static void uploading(GuiGraphics ctx, Font font, Rect rect, long startedAt)
	{
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.ACCENT);

		float fraction = Math.max(0.0F, Math.min(1.0F, (float) Backend.uploadFraction()));
		int fillWidth = Math.round((rect.width - 2) * fraction);

		if (fillWidth > 0)
		{
			Theme.roundedRect(ctx, rect.x + 1, rect.y + 1, fillWidth, rect.height - 2, Theme.RADIUS_PILL, Theme.DOWNLOAD_FILL);
		}

		int percent = Math.round(fraction * 100.0F);
		long elapsed = System.currentTimeMillis() - startedAt;
		String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 3) + 1);
		String text = "Uploading " + percent + "%";

		if (fraction > 0.05F && fraction < 0.95F)
		{
			long remaining = Math.max(1, (long) (elapsed / 1000.0 * (1.0 - fraction) / fraction));
			text += "  ~" + remaining + "s";
		}

		String label = Theme.bold(text + dots);
		Theme.text(ctx, font, label, rect.x + (rect.width - font.width(label)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1, Theme.ON_ACCENT);
	}

	// The 14px corner close used on detail cards: a card-coloured square with a cross that turns accent on hover
	public static void cornerClose(GuiGraphics ctx, Rect rect, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * Theme.buttonHover(rect, hovered));
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_CARD,
				hovered ? Theme.ACCENT : Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_CARD, Theme.HAIRLINE);
		Theme.cross(ctx, rect.x + (rect.width - 6) / 2, rect.y + (rect.height - 6) / 2, 6,
				hovered ? Theme.ON_ACCENT : Theme.TEXT_MUTE);
		Theme.pop(ctx);
	}

	// A minus / value / plus group; the value sits on the pills' text baseline
	public static int stepper(GuiGraphics ctx, Font font, Rect minus, Rect plus, String value, int x, int y,
			int mouseX, int mouseY)
	{
		int size = 16;
		minus.set(x, y, size, size);
		stepperButton(ctx, minus, false, mouseX, mouseY);
		int valueWidth = Math.max(18, font.width(value) + 8);
		Theme.text(ctx, font, value, x + size + (valueWidth - font.width(value)) / 2,
				y + (size - font.lineHeight) / 2 + 1, Theme.TEXT);
		plus.set(x + size + valueWidth, y, size, size);
		stepperButton(ctx, plus, true, mouseX, mouseY);
		return x + size * 2 + valueWidth;
	}

	// The font's - and + sit on a text baseline and never centre in a square, so the glyphs are drawn
	private static void stepperButton(GuiGraphics ctx, Rect rect, boolean plus, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);

		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				Theme.lighten(Theme.SURFACE_CARD, 0.12F * hover));

		if (hovered)
		{
			Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
		}

		int centerX = rect.x + rect.width / 2;
		int centerY = rect.y + rect.height / 2;
		ctx.fill(centerX - 3, centerY, centerX + 4, centerY + 1, Theme.TEXT);

		if (plus)
		{
			ctx.fill(centerX, centerY - 3, centerX + 1, centerY + 4, Theme.TEXT);
		}

		Theme.pop(ctx);
	}

	public static void arrow(GuiGraphics ctx, Rect rect, boolean left, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				hovered ? Theme.SURFACE_ELEVATED : 0xCC0F1114);
		Theme.arrow(ctx, rect.x + (rect.width - 4) / 2, rect.y + (rect.height - 7) / 2, left,
				hovered ? Theme.ACCENT_BRIGHT : Theme.TEXT);
	}
}

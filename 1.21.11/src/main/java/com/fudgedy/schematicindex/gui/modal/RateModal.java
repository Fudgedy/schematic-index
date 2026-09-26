package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.detail.RatingNudge;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Stars;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

// Asks for a star rating right after a single download, since few players go back to the detail stars
public class RateModal implements Overlay
{
	private static final int WIDTH = 280;
	private static final int THUMB_WIDTH = 96;
	private static final int THUMB_HEIGHT = 54;
	private static final float STAR_SCALE = 2.0F;
	private static final long THANKS_MS = 900L;
	private static final int KEY_ESCAPE = 256;
	private static final String[] LABELS = {"Poor", "Fair", "Good", "Great", "Loved it"};

	private final IndexScreen screen;
	private final Rect bounds = new Rect();
	private final Rect close = new Rect();
	private final Rect notNow = new Rect();
	private final Rect[] stars = {new Rect(), new Rect(), new Rect(), new Rect(), new Rect()};
	private @Nullable SchematicEntry entry;
	private boolean open;
	private long openedAt;
	private long thanksAt;
	private int chosen;

	public RateModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	@Override
	public boolean isOpen()
	{
		return this.open;
	}

	public void open(SchematicEntry entry)
	{
		this.entry = entry;
		this.open = true;
		this.openedAt = System.currentTimeMillis();
		this.thanksAt = 0L;
		this.chosen = 0;

		for (Rect star : this.stars)
		{
			star.set(0, 0, 0, 0);
		}
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		SchematicEntry entry = this.entry;

		if (entry == null)
		{
			this.open = false;
			return;
		}

		if (this.thanksAt > 0L && System.currentTimeMillis() - this.thanksAt > THANKS_MS)
		{
			this.open = false;
			return;
		}

		Font font = this.screen.font();
		int line = font.lineHeight;
		int glyphHeight = Math.round(line * STAR_SCALE) + 2;
		int textHeight = line + Theme.SPACE_XS + line;
		int tail = Theme.SPACE_L + glyphHeight + Theme.SPACE_XS + line + Theme.SPACE_L + Theme.H_CONTROL + Theme.SPACE_L;
		int head = Theme.SPACE_M + Theme.ICON_M + Theme.SPACE_S;
		// A short window drops the picture before anything else, so the stars and buttons always fit
		boolean thumb = hasPicture(entry) && head + THUMB_HEIGHT + tail <= this.screen.height - Theme.SPACE_L * 2;
		int rowHeight = thumb ? THUMB_HEIGHT : textHeight;

		ModalChrome.open(ctx, font, this.bounds, this.screen.width, this.screen.height, WIDTH, head + rowHeight + tail,
				"Rate this build", null, this.close, this.openedAt, mouseX, mouseY);
		int x = this.bounds.x;
		int y = this.bounds.y;
		int w = this.bounds.width;
		int innerX = x + Theme.SPACE_L;
		int innerWidth = w - Theme.SPACE_L * 2;
		int rowY = y + head;
		int textX = innerX;

		if (thumb)
		{
			this.drawPicture(ctx, entry, innerX, rowY);
			textX += THUMB_WIDTH + Theme.SPACE_S;
		}

		int textWidth = innerX + innerWidth - textX;
		int textY = rowY + (rowHeight - textHeight) / 2;
		String shown = Theme.clipBold(font, entry.title(), textWidth);
		Theme.text(ctx, font, Theme.bold(shown), textX, textY, Theme.TEXT);

		if (!shown.equals(entry.title()) && Theme.inside(mouseX, mouseY, textX, textY, textWidth, line))
		{
			Tooltip.show(entry.title());
		}

		String creator = entry.designer().isBlank() ? entry.poster() : entry.designer();

		if (!creator.isBlank())
		{
			Theme.text(ctx, font, Theme.clip(font, "by " + creator, textWidth), textX, textY + line + Theme.SPACE_XS,
					Theme.TEXT_MUTE);
		}

		int starsY = rowY + rowHeight + Theme.SPACE_L;
		int caption = this.renderStars(ctx, font, x + w / 2, starsY, glyphHeight, mouseX, mouseY);
		String label = caption < 0 ? "Pick a score" : LABELS[caption];
		int labelColor = caption < 0 ? Theme.TEXT_ASH : Theme.TEXT_MUTE;

		if (this.thanksAt > 0L)
		{
			label = "Thanks for rating";
			labelColor = Theme.SUCCESS;
		}

		Theme.text(ctx, font, label, x + (w - font.width(label)) / 2, starsY + glyphHeight + Theme.SPACE_XS, labelColor);

		int buttonWidth = Buttons.width(font, "Not now");
		this.notNow.set(x + w - Theme.SPACE_L - buttonWidth, y + this.bounds.height - Theme.SPACE_L - Theme.H_CONTROL,
				buttonWidth, Theme.H_CONTROL);
		Buttons.button(ctx, font, this.notNow, "Not now", Buttons.Kind.SECONDARY, this.thanksAt == 0L, mouseX, mouseY);
		Tooltip.render(ctx, font, mouseX, mouseY, this.screen.width, this.screen.height);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.thanksAt > 0L)
		{
			return true;
		}

		for (int i = 0; i < this.stars.length; i++)
		{
			if (this.stars[i].contains(mouseX, mouseY))
			{
				this.rate(i);
				return true;
			}
		}

		if (this.notNow.contains(mouseX, mouseY) || this.close.contains(mouseX, mouseY)
				|| !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == KEY_ESCAPE)
		{
			this.open = false;
		}

		return true;
	}

	// Returns the star under the cursor, or -1; hovering fills every star up to it as a preview
	private int renderStars(GuiGraphics ctx, Font font, int centreX, int y, int glyphHeight, int mouseX,
			int mouseY)
	{
		int cell = Stars.cellWidth(font, STAR_SCALE);
		int left = centreX - (cell * this.stars.length - 2) / 2;
		int hovered = -1;

		for (int i = 0; i < this.stars.length && this.thanksAt == 0L; i++)
		{
			if (Theme.inside(mouseX, mouseY, left + i * cell, y - 1, cell, glyphHeight))
			{
				hovered = i;
			}
		}

		int halfStars = this.thanksAt > 0L ? this.chosen : (hovered + 1) * 2;
		Stars.draw(ctx, font, left, y, halfStars, STAR_SCALE, this.thanksAt == 0L, mouseX, mouseY, this.stars);
		return hovered;
	}

	private void drawPicture(GuiGraphics ctx, SchematicEntry entry, int x, int y)
	{
		Identifier texture = this.screen.thumbnailTexture(entry);

		if (texture == null)
		{
			Theme.loadingPlaceholder(ctx, x, y, THUMB_WIDTH, THUMB_HEIGHT);
			return;
		}

		Theme.image(ctx, texture, x, y, THUMB_WIDTH, THUMB_HEIGHT);
	}

	private void rate(int star)
	{
		SchematicEntry entry = this.entry;

		if (entry == null)
		{
			return;
		}

		this.chosen = (star + 1) * 2;
		this.thanksAt = System.currentTimeMillis();
		Theme.rate();
		RatingNudge.submit(this.screen, entry, this.chosen);
	}

	// The cover is already cached from the card that was clicked; a post with no pictures has nothing to load
	private static boolean hasPicture(SchematicEntry entry)
	{
		return entry.thumbnailUrl() != null && !entry.thumbnailUrl().isBlank() || !entry.imageUrls().isEmpty()
				|| entry.imageCount() > 0;
	}
}

package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

// A closed pill showing the current choice; the open list is drawn by whoever owns the late overlay
// pass so it sits above everything laid out after the pill
public final class Dropdown
{
	public static final int NOT_HANDLED = -2;
	public static final int TOGGLED = -1;
	private static final int ITEM_HEIGHT = 14;
	private static final int PAD = 6;
	private static final long ARROW_TURN_MS = 120L;
	private static final float QUARTER_TURN = (float) (Math.PI / 2.0D);

	public final Rect button = new Rect();
	private final Rect list = new Rect();
	private String[] labels;
	private int selected;
	private boolean open;
	private long openedAt;
	private long closedAt;

	// Wide enough for the longest choice, so swapping the selection never reflows the row around it
	public static int width(Font font, String caption, String[] labels)
	{
		int widest = 0;

		for (String label : labels)
		{
			widest = Math.max(widest, font.width(caption + label));
		}

		return widest + PAD * 2 + 10;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void close()
	{
		this.setOpen(false);
	}

	private void setOpen(boolean value)
	{
		if (this.open == value)
		{
			return;
		}

		this.open = value;

		if (value)
		{
			this.openedAt = Util.getMillis();
		}
		else
		{
			this.closedAt = Util.getMillis();
		}
	}

	private float arrowTurn()
	{
		long since = this.open ? this.openedAt : this.closedAt;
		float fraction = Theme.easeOut((Util.getMillis() - since) / (float) ARROW_TURN_MS);
		return (this.open ? fraction : 1.0F - fraction) * QUARTER_TURN;
	}

	public void render(GuiGraphicsExtractor ctx, Font font, Rect rect, String[] labels, int selected, int mouseX, int mouseY)
	{
		this.render(ctx, font, rect, labels, selected, "", mouseX, mouseY);
	}

	public void render(GuiGraphicsExtractor ctx, Font font, Rect rect, String[] labels, int selected, String placeholder,
			int mouseX, int mouseY)
	{
		this.render(ctx, font, rect, labels, selected, placeholder, "", mouseX, mouseY);
	}

	// The caption ("Sort: ") prefixes the closed pill only, so the open list keeps the bare labels
	public void render(GuiGraphicsExtractor ctx, Font font, Rect rect, String[] labels, int selected, String placeholder,
			String caption, int mouseX, int mouseY)
	{
		this.labels = labels;
		this.selected = selected;
		this.button.set(rect.x, rect.y, rect.width, rect.height);

		boolean hovered = rect.contains(mouseX, mouseY) || this.open;
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

		if (hovered)
		{
			Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
		}

		String shown = selected >= 0 && selected < labels.length ? caption + labels[selected] : placeholder;
		Theme.text(ctx, font, Theme.clip(font, shown, rect.width - PAD * 2 - 8), rect.x + PAD,
				rect.y + (rect.height - font.lineHeight) / 2 + 1, Theme.TEXT);
		int arrowX = rect.x + rect.width - PAD - 4;
		int arrowY = rect.y + (rect.height - 7) / 2;
		Theme.pushRotate(ctx, arrowX, arrowY, 4, 7, this.arrowTurn());
		Theme.arrow(ctx, arrowX, arrowY, false, Theme.TEXT_MUTE);
		Theme.pop(ctx);
	}

	public void renderOpen(GuiGraphicsExtractor ctx, Font font, int mouseX, int mouseY, int screenHeight)
	{
		if (!this.open || this.labels == null)
		{
			return;
		}

		int height = this.labels.length * ITEM_HEIGHT + 4;
		int y = this.button.y + this.button.height + 2;

		if (y + height > screenHeight)
		{
			y = this.button.y - 2 - height;
		}

		this.list.set(this.button.x, y, this.button.width, height);
		Theme.roundedRect(ctx, this.list.x, this.list.y, this.list.width, this.list.height, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, this.list.x, this.list.y, this.list.width, this.list.height, Theme.RADIUS_CARD, Theme.HAIRLINE);

		for (int i = 0; i < this.labels.length; i++)
		{
			int itemY = this.list.y + 2 + i * ITEM_HEIGHT;
			boolean hovered = Theme.inside(mouseX, mouseY, this.list.x, itemY, this.list.width, ITEM_HEIGHT);

			if (hovered || i == this.selected)
			{
				Theme.roundedRect(ctx, this.list.x + 2, itemY, this.list.width - 4, ITEM_HEIGHT, Theme.RADIUS_PILL,
						hovered ? Theme.ACCENT : Theme.SURFACE_CARD);
			}

			Theme.text(ctx, font, Theme.clip(font, this.labels[i], this.list.width - PAD * 2), this.list.x + PAD,
					itemY + (ITEM_HEIGHT - font.lineHeight) / 2 + 1, hovered ? Theme.ON_ACCENT : Theme.TEXT);
		}
	}

	// Runs before any other click handling while open, so a choice or a dismissal consumes the click
	public int click(double mouseX, double mouseY)
	{
		if (this.open)
		{
			this.setOpen(false);

			if (this.list.contains(mouseX, mouseY))
			{
				int index = (int) ((mouseY - this.list.y - 2) / ITEM_HEIGHT);
				Theme.click();
				return Math.max(0, Math.min(this.labels.length - 1, index));
			}

			return TOGGLED;
		}

		if (this.button.contains(mouseX, mouseY))
		{
			this.setOpen(true);
			Theme.click();
			return TOGGLED;
		}

		return NOT_HANDLED;
	}
}

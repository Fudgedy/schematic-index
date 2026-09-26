package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Rows;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Lays a Settings tab out as uniform rows and records what each drawn control does, so a click can only
// reach something that is on screen this frame
final class SettingsRows
{
	private static final int ROW_HEIGHT = Theme.H_ROW_2;
	private static final int SLIDER_WIDTH = 96;
	private static final String VALUE_SAMPLE = "100%";

	private final IndexScreen screen;
	private final List<Rect> rowPool = new ArrayList<>();
	private final List<Rect> switchPool = new ArrayList<>();
	private final List<Rect> buttonPool = new ArrayList<>();
	private final List<Rect> hits = new ArrayList<>();
	private final List<Runnable> actions = new ArrayList<>();
	private final List<Rect> pressable = new ArrayList<>();
	private int rowIndex;
	private int switchIndex;
	private int buttonIndex;
	private int x;
	private int width;
	private int mouseX;
	private int mouseY;
	private boolean firstSection;

	SettingsRows(IndexScreen screen)
	{
		this.screen = screen;
	}

	void begin(int x, int width, int mouseX, int mouseY)
	{
		this.x = x;
		this.width = width;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.rowIndex = 0;
		this.switchIndex = 0;
		this.buttonIndex = 0;
		this.firstSection = true;
		this.hits.clear();
		this.actions.clear();
		this.pressable.clear();
	}

	int header(GuiGraphics ctx, String title, int y)
	{
		y += this.firstSection ? 0 : Theme.SPACE_XL - Theme.SPACE_XS;
		this.firstSection = false;
		return Rows.header(ctx, this.screen.font(), title, null, this.x, y, this.width);
	}

	int toggle(GuiGraphics ctx, String title, String description, boolean on, Runnable action, int y)
	{
		return this.toggle(ctx, title, description, on, false, action, y);
	}

	// The whole row is the hit target; a disabled row neither hovers nor takes clicks
	int toggle(GuiGraphics ctx, String title, String description, boolean on, boolean disabled, Runnable action, int y)
	{
		Rect row = this.row(y);
		boolean hovered = !disabled && row.contains(this.mouseX, this.mouseY);
		this.fill(ctx, row, hovered);
		int switchX = row.x + row.width - Theme.SPACE_M - Controls.SWITCH_WIDTH;
		Rect knob = Rect.pooled(this.switchPool, this.switchIndex++);
		knob.set(switchX, row.y + (ROW_HEIGHT - Controls.SWITCH_HEIGHT) / 2, Controls.SWITCH_WIDTH, Controls.SWITCH_HEIGHT);
		Controls.toggleSwitch(ctx, knob, knob.x, knob.y, on, disabled);
		this.label(ctx, row, title, description, switchX - Theme.SPACE_S - row.x - Theme.SPACE_M, disabled);

		if (!disabled)
		{
			this.hits.add(row);
			this.actions.add(() -> {
				action.run();
				Theme.click(1.1F);
			});
		}

		return this.next(y);
	}

	int flag(GuiGraphics ctx, String title, String description, String key, int y)
	{
		return this.toggle(ctx, title, description, Settings.flag(key, true),
				() -> Settings.setFlag(key, !Settings.flag(key, true)), y);
	}

	// Only lays out the track; the page owns dragging because a drag outlives the frame the rows are rebuilt in
	int slider(GuiGraphics ctx, Rect track, String title, String description, String value, float fraction,
			boolean dragging, int y)
	{
		Font font = this.screen.font();
		Rect row = this.row(y);
		this.fill(ctx, row, false);
		int valueWidth = font.width(VALUE_SAMPLE);
		int right = row.x + row.width - Theme.SPACE_M;
		Theme.text(ctx, font, value, right - font.width(value), row.y + (ROW_HEIGHT - font.lineHeight) / 2 + 1, Theme.TEXT_MUTE);
		int trackX = right - valueWidth - Theme.SPACE_M - SLIDER_WIDTH;
		track.set(trackX, row.y + Theme.SPACE_S, SLIDER_WIDTH, ROW_HEIGHT - Theme.SPACE_S * 2);
		Controls.rowSlider(ctx, track, fraction, dragging, this.mouseX, this.mouseY);
		this.label(ctx, row, title, description, trackX - Theme.SPACE_M - row.x - Theme.SPACE_M, false);
		return this.next(y);
	}

	// Buttons sit right-aligned in the order given, so the last one lands on the row's right edge
	int buttons(GuiGraphics ctx, String title, String description, int y, Action... buttons)
	{
		Font font = this.screen.font();
		Rect row = this.row(y);
		this.fill(ctx, row, false);
		int right = row.x + row.width - Theme.SPACE_M;
		int buttonX = right;

		for (int i = buttons.length - 1; i >= 0; i--)
		{
			Action button = buttons[i];
			int buttonWidth = Math.max(Buttons.width(font, button.label()), Buttons.width(font, button.sizeLabel()));
			buttonX -= buttonWidth;
			Rect rect = Rect.pooled(this.buttonPool, this.buttonIndex++);
			rect.set(buttonX, row.y + (ROW_HEIGHT - Theme.H_CONTROL) / 2, buttonWidth, Theme.H_CONTROL);

			if (button.armed())
			{
				Buttons.danger(ctx, font, rect, button.label(), this.mouseX, this.mouseY);
			}
			else
			{
				Buttons.button(ctx, font, rect, button.label(), button.kind(), true, this.mouseX, this.mouseY);
			}

			this.hits.add(rect);
			this.actions.add(button.quiet() ? button.run() : () -> {
				Theme.click();
				button.run().run();
			});
			this.pressable.add(rect);
			buttonX -= Theme.SPACE_S;
		}

		this.label(ctx, row, title, description, buttonX - row.x - Theme.SPACE_M, false);
		return this.next(y);
	}

	int note(GuiGraphics ctx, String title, String description, int y)
	{
		Rect row = this.row(y);
		this.fill(ctx, row, false);
		this.label(ctx, row, title, description, row.width - Theme.SPACE_M * 2, false);
		return this.next(y);
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		for (int i = this.hits.size() - 1; i >= 0; i--)
		{
			if (this.hits.get(i).contains(mouseX, mouseY))
			{
				this.actions.get(i).run();
				return true;
			}
		}

		return false;
	}

	List<Rect> pressable()
	{
		return this.pressable;
	}

	int x()
	{
		return this.x;
	}

	int width()
	{
		return this.width;
	}

	private Rect row(int y)
	{
		Rect row = Rect.pooled(this.rowPool, this.rowIndex++);
		row.set(this.x, y, this.width, ROW_HEIGHT);
		return row;
	}

	private void fill(GuiGraphics ctx, Rect row, boolean hovered)
	{
		float hover = Theme.buttonHover(row, hovered, Theme.MOTION_HOVER_MS);
		Theme.roundedRect(ctx, row.x, row.y, row.width, row.height, Theme.RADIUS_CARD,
				Theme.mix(Theme.SURFACE_CARD, Theme.SURFACE_ELEVATED, hover));
	}

	// One line each; a clipped description shows in full as a tooltip on the row
	private void label(GuiGraphics ctx, Rect row, String title, @Nullable String description, int room, boolean disabled)
	{
		Font font = this.screen.font();
		int textX = row.x + Theme.SPACE_M;
		int titleY = row.y + Theme.SPACE_S;
		Theme.text(ctx, font, Theme.clip(font, title, room), textX, titleY, disabled ? Theme.TEXT_ASH : Theme.TEXT);

		if (description == null || description.isEmpty())
		{
			return;
		}

		String clipped = Theme.clip(font, description, room);
		Theme.text(ctx, font, clipped, textX, titleY + font.lineHeight + Theme.SPACE_2XS, Theme.TEXT_MUTE);

		if (!clipped.equals(description) && row.contains(this.mouseX, this.mouseY))
		{
			Tooltip.show(description);
		}
	}

	private int next(int y)
	{
		return y + ROW_HEIGHT + Theme.SPACE_XS;
	}

	// sizeLabel reserves the width of the widest label a button cycles through, so it never jumps
	record Action(String label, String sizeLabel, Buttons.Kind kind, boolean armed, boolean quiet, Runnable run)
	{
		static Action primary(String label, Runnable run)
		{
			return new Action(label, label, Buttons.Kind.PRIMARY, false, false, run);
		}

		static Action secondary(String label, Runnable run)
		{
			return new Action(label, label, Buttons.Kind.SECONDARY, false, false, run);
		}

		static Action cycling(String label, String sizeLabel, Runnable run)
		{
			return new Action(label, sizeLabel, Buttons.Kind.SECONDARY, false, false, run);
		}

		// Plays no click itself, for actions such as opening a link that already make their own sound
		static Action quietSecondary(String label, Runnable run)
		{
			return new Action(label, label, Buttons.Kind.SECONDARY, false, true, run);
		}

		static Action danger(String label, String sizeLabel, boolean armed, Runnable run)
		{
			return new Action(label, sizeLabel, Buttons.Kind.DANGER, armed, false, run);
		}
	}
}

package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

// A single-line field fed raw key events by its owner; commit fires on Enter or focus loss
public final class TextInput
{
	private static final int KEY_BACKSPACE = 259;
	private static final int KEY_ENTER = 257;
	private static final int KEY_KEYPAD_ENTER = 335;
	private static final int KEY_ESCAPE = 256;
	// A shade above the hairline so the box reads as a field on the card it sits on
	private static final int BORDER = Theme.lighten(Theme.HAIRLINE, 0.18F);

	public final Rect bounds = new Rect();
	private final int maxLength;
	private final String allowed;
	private String value = "";
	private boolean focused;
	private boolean dirty;

	// allowed lists every typeable character, or is null to accept anything printable
	public TextInput(int maxLength, String allowed)
	{
		this.maxLength = maxLength;
		this.allowed = allowed;
	}

	public String value()
	{
		return this.value;
	}

	public void set(String value)
	{
		this.value = value == null ? "" : value;
	}

	public boolean isFocused()
	{
		return this.focused;
	}

	public void render(GuiGraphicsExtractor ctx, Font font, Rect rect, String placeholder, int mouseX, int mouseY)
	{
		this.bounds.set(rect.x, rect.y, rect.width, rect.height);

		if (this.focused)
		{
			TextBoxes.underscoreLine(ctx, font, rect.x, rect.y, rect.width, rect.height, this.value, placeholder, true);
			return;
		}

		boolean hover = rect.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				hover ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				hover ? Theme.lighten(BORDER, 0.15F) : BORDER);

		String shown = this.value.isEmpty() ? placeholder : this.value;
		Theme.text(ctx, font, Theme.clip(font, shown, rect.width - 12), rect.x + 6, rect.y + (rect.height - font.lineHeight) / 2 + 1,
				this.value.isEmpty() ? Theme.TEXT_ASH : Theme.TEXT);
	}

	// Returns true when the click landed in the field; a click elsewhere drops focus and commits
	public boolean click(double mouseX, double mouseY)
	{
		boolean inside = this.bounds.contains(mouseX, mouseY);

		if (inside && !this.focused)
		{
			this.focused = true;
			Theme.click();
		}
		else if (!inside && this.focused)
		{
			this.blur();
		}

		return inside;
	}

	public void blur()
	{
		this.focused = false;
		this.dirty = true;
	}

	// True once per edit session after a commit, so the owner reads the value exactly when it settles
	public boolean takeCommitted()
	{
		if (!this.dirty)
		{
			return false;
		}

		this.dirty = false;
		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.focused)
		{
			return false;
		}

		if (IndexScreen.isPasteChord(event))
		{
			this.paste();
			return true;
		}

		switch (event.key())
		{
			case KEY_BACKSPACE -> {
				if (!this.value.isEmpty())
				{
					this.value = this.value.substring(0, this.value.length() - 1);
				}
			}
			case KEY_ENTER, KEY_KEYPAD_ENTER, KEY_ESCAPE -> this.blur();
			default -> {
			}
		}

		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		if (!this.focused)
		{
			return false;
		}

		String typed = event.codepointAsString();

		if (this.accepts(typed.charAt(0)) && this.value.length() < this.maxLength)
		{
			this.value += typed;
		}

		return true;
	}

	private void paste()
	{
		String clip;

		try
		{
			clip = Minecraft.getInstance().keyboardHandler.getClipboard();
		}
		catch (Exception e)
		{
			return;
		}

		StringBuilder out = new StringBuilder(this.value);

		for (int i = 0; i < clip.length() && out.length() < this.maxLength; i++)
		{
			if (this.accepts(clip.charAt(i)))
			{
				out.append(clip.charAt(i));
			}
		}

		this.value = out.toString();
	}

	private boolean accepts(char c)
	{
		return c >= ' ' && (this.allowed == null || this.allowed.indexOf(c) >= 0);
	}
}

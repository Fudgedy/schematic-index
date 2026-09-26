package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class FormFields
{
	private FormFields()
	{
	}

	public static EditBox textField(IndexScreen screen, int x, int y, int width, String hint, String value)
	{
		EditBox box = new EditBox(screen.font(), x, y, width, 10, Component.literal(hint));
		box.setBordered(false);
		box.setMaxLength(120);
		box.setTextColor(Theme.TEXT);
		box.setHint(hint(hint));
		box.setValue(value);
		screen.addModalWidget(box);
		return box;
	}

	// A MultiLineEditBox wraps to the width it was built with, so a resize needs a fresh box
	public static MultiLineEditBox ensureMultiline(IndexScreen screen, MultiLineEditBox box, int width, int height)
	{
		if (box != null && box.getWidth() == width && box.getHeight() == height)
		{
			return box;
		}

		String value = box == null ? "" : box.getValue();

		if (box != null)
		{
			screen.removeModalWidget(box);
		}

		return multiline(screen, width, height, value);
	}

	public static MultiLineEditBox multiline(IndexScreen screen, int width, int height, String value)
	{
		MultiLineEditBox box = MultiLineEditBox.builder()
				.setPlaceholder(hint("Description"))
				.setTextColor(Theme.TEXT)
				.setTextShadow(false)
				.setShowBackground(false)
				.setShowDecorations(false)
				.build(screen.font(), width, height, Component.literal("Description"));
		box.setCharacterLimit(IndexScreen.DESC_CHAR_LIMIT);
		box.setValue(value);
		screen.addModalWidget(box);
		return box;
	}

	// Vanilla draws single-line hints dark grey but multiline placeholders near white, so both get the label tier
	public static Component hint(String text)
	{
		return Component.literal(text).withColor(Theme.TEXT_ASH & 0xFFFFFF);
	}

	// Parks the cursor at the start so a long value shows from the beginning, not its end
	public static void fillEditField(EditBox box, String value)
	{
		box.setValue(value);
		box.setCursorPosition(0);
		box.setHighlightPos(0);
	}

	public static boolean focusField(IndexScreen screen, EditBox box, MouseButtonEvent event, boolean doubleClick,
			double mouseX, double mouseY)
	{
		if (!Theme.inside(mouseX, mouseY, box.getX() - 6, box.getY() - 4, box.getWidth() + 12, IndexScreen.FIELD_HEIGHT))
		{
			return false;
		}

		screen.setFocused(box);
		box.setFocused(true);
		box.mouseClicked(event, doubleClick);
		return true;
	}
}

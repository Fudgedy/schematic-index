package com.fudgedy.schematicindex.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

// A modal registered through IndexScreen.overlay(): while open it owns every click, key, scroll and character
public interface Overlay
{
	boolean isOpen();

	void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY);

	boolean mouseClicked(double mouseX, double mouseY);

	boolean keyPressed(KeyEvent event);

	default void keyReleased(KeyEvent event)
	{
	}

	default boolean charTyped(CharacterEvent event)
	{
		return false;
	}

	default boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		return false;
	}
}

package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

// The Build of the Day mark, one texel per GUI pixel so the pixel art never resamples
public final class Crown
{
	public static final int WIDTH = 12;
	public static final int HEIGHT = 8;

	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/crown_badge.png");

	private Crown()
	{
	}

	public static void draw(GuiGraphicsExtractor ctx, int x, int y)
	{
		Theme.image(ctx, TEXTURE, x, y, WIDTH, HEIGHT, WIDTH, HEIGHT);
	}

	public static void badge(GuiGraphicsExtractor ctx, int x, int y)
	{
		Theme.image(ctx, TEXTURE, x, y, WIDTH, HEIGHT, WIDTH, HEIGHT);
	}
}

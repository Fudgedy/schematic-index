package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.resources.Identifier;

// ButtonGeneric blits getTexture at 1:1 from a 256 px sheet and never calls renderAt, so the cell it is
// pointed at is a blank one and IconButton paints the high-resolution logo over it through renderAt
public final class IndexIcon implements IGuiIcon
{
	public static final IndexIcon INSTANCE = new IndexIcon();

	private static final int SIZE = 12;
	private static final int LOGO_SIZE = 64;

	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/icons.png");
	private static final Identifier LOGO =
			Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/menu_icon.png");

	private IndexIcon()
	{
	}

	@Override
	public int getWidth()
	{
		return SIZE;
	}

	@Override
	public int getHeight()
	{
		return SIZE;
	}

	@Override
	public int getU()
	{
		return 0;
	}

	@Override
	public int getV()
	{
		return SIZE;
	}

	@Override
	public Identifier getTexture()
	{
		return TEXTURE;
	}

	@Override
	public void renderAt(GuiContext ctx, int x, int y, float zLevel, boolean enabled, boolean selected)
	{
		Theme.image(ctx, LOGO, x, y, SIZE, SIZE, LOGO_SIZE, LOGO_SIZE);
	}
}

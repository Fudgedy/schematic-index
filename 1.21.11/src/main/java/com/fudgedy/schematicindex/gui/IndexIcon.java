package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// ButtonGeneric blits getTexture at 1:1 from a 256 px sheet and never calls renderAt, so the cell it is
// pointed at is a blank one and IconButton paints the high-resolution logo over it through renderAt
public final class IndexIcon implements IGuiIcon
{
	public static final IndexIcon INSTANCE = new IndexIcon();

	// The 9 px logo at 1.5x: whole screen pixels at GUI scale 2 and 4, where 2x crowded the label
	private static final float SCALE = 1.5F;
	private static final int LOGO = 9;
	private static final int SIZE = 14;
	// The logo sits 7x in the top-left 63 px of a 64 px slot, so each drawn pixel lands inside one logo pixel
	private static final int LOGO_SOURCE = 63;
	private static final int LOGO_TEXTURE = 64;

	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/icons.png");
	private static final Identifier LOGO_ID =
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
		// Anchored on whole GUI pixels, so at 1.5x every logo edge lands on a screen pixel; the spare half is unseen
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x, (float) y);
		ctx.pose().scale(SCALE, SCALE);
		ctx.blit(RenderPipelines.GUI_TEXTURED, LOGO_ID, 0, 0, 0.0F, 0.0F, LOGO, LOGO, LOGO_SOURCE, LOGO_SOURCE,
				LOGO_TEXTURE, LOGO_TEXTURE);
		ctx.pose().popMatrix();
	}
}

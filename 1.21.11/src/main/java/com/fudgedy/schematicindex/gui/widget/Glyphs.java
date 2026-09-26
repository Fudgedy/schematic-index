package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// 8px monochrome glyphs drawn as pixel runs, so any role colour tints them and they stay crisp at every scale
public final class Glyphs
{
	public static final String[] CLOCK = {
			"  XXXX  ",
			" X    X ",
			"X   X  X",
			"X   X  X",
			"X   XX X",
			"X      X",
			" X    X ",
			"  XXXX  "
	};

	public static final String[] CHECK = {
			"        ",
			"       X",
			"      XX",
			"X    XX ",
			"XX  XX  ",
			" XXXX   ",
			"  XX    ",
			"        "
	};

	private static final ItemStack SHARD = new ItemStack(Items.AMETHYST_SHARD);

	private Glyphs()
	{
	}

	// The currency mark is the real amethyst shard at half size, one texel per screen pixel at GUI scale 2
	public static void shard(GuiGraphics ctx, int x, int y)
	{
		Theme.itemScaled(ctx, SHARD, x, y, 0.5F);
	}

	public static void draw(GuiGraphics ctx, String[] glyph, int x, int y, int color)
	{
		for (int row = 0; row < glyph.length; row++)
		{
			String line = glyph[row];
			int runStart = -1;

			for (int column = 0; column <= line.length(); column++)
			{
				boolean filled = column < line.length() && line.charAt(column) == 'X';

				if (filled && runStart < 0)
				{
					runStart = column;
				}
				else if (!filled && runStart >= 0)
				{
					ctx.fill(x + runStart, y + row, x + column, y + row + 1, color);
					runStart = -1;
				}
			}
		}
	}
}

package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// The strip over Cosmetics while a seasonal drop is live for this account
class SeasonBanner
{
	private static final int HEIGHT = 24;

	private final IndexScreen screen;

	SeasonBanner(IndexScreen screen)
	{
		this.screen = screen;
	}

	int render(GuiGraphicsExtractor ctx, int formX, int y, int formWidth)
	{
		RemoteContent.Season season = CosmeticColors.season();

		if (season == null || !Limited.shown())
		{
			return y;
		}

		Font font = this.screen.font();
		int accent = Limited.accent();
		Theme.roundedRect(ctx, formX, y, formWidth, HEIGHT, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, formX, y, formWidth, HEIGHT, Theme.RADIUS_CARD, accent);
		ctx.fill(formX + 1, y + 1, formX + 4, y + HEIGHT - 1, accent);
		Theme.itemScaled(ctx, new ItemStack(Items.CLOCK), formX + 9, y + (HEIGHT - 10) / 2, 0.65F);

		String span = Limited.span(season.endsAt());
		String text = season.name() + " drop · " + (span.isEmpty() ? "ends soon" : "ends in " + span);
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, text, formWidth - 30)), formX + 24,
				y + (HEIGHT - font.lineHeight) / 2 + 1, accent);
		return y + HEIGHT + IndexScreen.SETTINGS_ROW_GAP;
	}
}

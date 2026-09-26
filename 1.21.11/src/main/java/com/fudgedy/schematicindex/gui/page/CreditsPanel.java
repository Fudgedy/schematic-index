package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.States;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.List;

final class CreditsPanel
{
	private static final int AVATAR = 24;
	private static final int SKELETON_ROWS = 2;

	private CreditsPanel()
	{
	}

	// Flows inline in the Settings column; returns the bottom edge
	static int render(IndexScreen screen, GuiGraphics ctx, int x, int y, int width)
	{
		Font font = screen.font();
		List<RemoteContent.Credit> credits = RemoteContent.credits();

		if (credits.isEmpty() && !RemoteContent.loaded())
		{
			return States.skeletonRows(ctx, x, y, width, AVATAR, Theme.SPACE_S, SKELETON_ROWS);
		}

		if (credits.isEmpty())
		{
			Theme.text(ctx, font, "No credits yet.", x, y, Theme.TEXT_MUTE);
			return y + font.lineHeight;
		}

		int textWidth = Math.min(width, Theme.TEXT_MAX_WIDTH);

		for (RemoteContent.Credit credit : credits)
		{
			Identifier avatar = ImageStore.avatar(credit.avatarUrl());

			if (avatar != null)
			{
				Theme.image(ctx, avatar, x, y, AVATAR, AVATAR, 64, 64);
			}
			else
			{
				Theme.roundedRect(ctx, x, y, AVATAR, AVATAR, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
			}

			int nameX = x + AVATAR + Theme.SPACE_S;
			int nameWidth = x + width - nameX;
			Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, credit.displayName(), nameWidth)), nameX, y + Theme.SPACE_2XS,
					Theme.TEXT);

			if (!credit.role().isBlank())
			{
				Theme.text(ctx, font, Theme.clip(font, credit.role(), nameWidth), nameX,
						y + Theme.SPACE_2XS + font.lineHeight + Theme.SPACE_2XS, Theme.ACCENT_BRIGHT);
			}

			y += AVATAR + Theme.SPACE_XS;

			for (String row : screen.wrap(credit.description(), textWidth, 4))
			{
				Theme.text(ctx, font, row, x, y, Theme.TEXT_MUTE);
				y += font.lineHeight + Theme.SPACE_2XS;
			}

			y += Theme.SPACE_M;
		}

		return y - Theme.SPACE_M;
	}
}

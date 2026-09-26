package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.catalogue.DiscordLink;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Achievements under Cosmetics, Tags: the owned ones to pin, then what unlocks each locked one and how far along the player is
class AchievementShelf
{
	private static final int CARD_HEIGHT = 58;
	private static final int CARD_GAP = 6;
	private static final int BAR_HEIGHT = 4;
	private static final String JOIN_DISCORD = "join_discord";
	private static final String LINK_CODE = "link_code";
	private static final float QUARTER_TURN = (float) (Math.PI / 2.0);

	// Static so the choice lasts the game session across menu reopens, but is never saved
	private static boolean expanded;
	private static long toggledAt;

	private final IndexScreen screen;
	private final List<Rect> joinButtons = new ArrayList<>();
	private final List<Rect> codeButtons = new ArrayList<>();
	private final ShowcaseShelf showcase;
	private final Rect toggle = new Rect();
	private int contentHeight;

	AchievementShelf(IndexScreen screen)
	{
		this.screen = screen;
		this.showcase = new ShowcaseShelf(screen);
	}

	static String rarityText(CosmeticTags.Tag tag)
	{
		return "Owned by " + String.format(Locale.ROOT, "%.1f", tag.rarity()) + "% of players";
	}

	int render(GuiGraphicsExtractor ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		this.clear();

		if (!RemoteContent.feature("achievements"))
		{
			return y;
		}

		if (CosmeticTags.loadFailed())
		{
			y = this.screen.settingsSectionHeader(ctx, "Achievements", formX, y);
			return this.screen.settingsDescription(ctx, "Couldn't load achievements. (" + Errors.ACHIEVEMENTS_LOAD + ")",
					1, formX, y, formWidth);
		}

		List<CosmeticTags.Tag> shown = visible();
		List<CosmeticTags.Tag> owned = ShowcaseShelf.owned();

		if (shown.isEmpty() && owned.isEmpty())
		{
			return y;
		}

		y = this.renderToggle(ctx, owned.size(), shown.size(), formX, y + Theme.SPACE_S, formWidth, mouseX, mouseY);
		float open = openFraction();

		if (open <= 0.0F)
		{
			return y + IndexScreen.SETTINGS_ROW_GAP;
		}

		int top = y + Theme.SPACE_S;

		if (open >= 1.0F)
		{
			int bottom = this.renderList(ctx, owned, shown, formX, top, formWidth, mouseX, mouseY);
			this.contentHeight = bottom - top;
			return bottom;
		}

		// The list is measured on the frame before, so the clip grows toward its real height
		int visible = Math.round(this.contentHeight * open);
		ctx.enableScissor(formX, top, formX + formWidth, top + visible);
		int bottom = this.renderList(ctx, owned, shown, formX, top, formWidth, -1, -1);
		ctx.disableScissor();
		this.contentHeight = bottom - top;
		this.clear();
		return y + Math.max(IndexScreen.SETTINGS_ROW_GAP, Theme.SPACE_S + Math.min(visible, this.contentHeight));
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.toggle.contains(mouseX, mouseY))
		{
			Theme.click(expanded ? 0.9F : 1.1F);
			expanded = !expanded;
			toggledAt = System.currentTimeMillis();
			return true;
		}

		if (this.showcase.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		for (Rect rect : this.joinButtons)
		{
			if (rect.contains(mouseX, mouseY))
			{
				this.screen.openLink(RemoteContent.discord());
				return true;
			}
		}

		for (Rect rect : this.codeButtons)
		{
			if (rect.contains(mouseX, mouseY))
			{
				this.screen.linkModal.open();
				return true;
			}
		}

		return false;
	}

	private static float openFraction()
	{
		float t = Theme.easeOut((System.currentTimeMillis() - toggledAt) / (float) Theme.MOTION_TAB_MS);
		return expanded ? t : 1.0F - t;
	}

	private int renderToggle(GuiGraphicsExtractor ctx, int earned, int locked, int x, int y, int width, int mouseX,
			int mouseY)
	{
		Font font = this.screen.font();
		this.toggle.set(x, y, width, Theme.H_ROW);
		boolean hovered = this.toggle.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(this.toggle, hovered, Theme.MOTION_HOVER_MS);
		Theme.roundedRect(ctx, x, y, width, Theme.H_ROW, Theme.RADIUS_CARD,
				Theme.mix(Theme.SURFACE_CARD, Theme.SURFACE_ELEVATED, hover));
		int textY = y + (Theme.H_ROW - font.lineHeight) / 2 + 1;
		Theme.text(ctx, font, Theme.bold("Achievements (" + (earned + locked) + ")"), x + Theme.SPACE_M, textY, Theme.TEXT);

		int arrowX = x + width - Theme.SPACE_M - Theme.SPACE_XS;
		int arrowY = y + (Theme.H_ROW - 7) / 2;
		String meta = earned + " earned";
		Theme.text(ctx, font, meta, arrowX - Theme.SPACE_S - font.width(meta), textY, Theme.TEXT_ASH);
		Theme.pushRotate(ctx, arrowX, arrowY, 4, 7, openFraction() * QUARTER_TURN);
		Theme.arrow(ctx, arrowX, arrowY, false, hovered ? Theme.TEXT : Theme.TEXT_MUTE);
		Theme.pop(ctx);
		return y + Theme.H_ROW;
	}

	private int renderList(GuiGraphicsExtractor ctx, List<CosmeticTags.Tag> owned, List<CosmeticTags.Tag> shown,
			int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		y = this.showcase.render(ctx, owned, formX, y, formWidth, mouseX, mouseY);

		if (shown.isEmpty())
		{
			return y;
		}

		y = this.screen.settingsDescription(ctx, "Earned, never bought. Each card says how to unlock it.", 1, formX, y,
				formWidth);

		for (int i = 0; i < shown.size(); i++)
		{
			this.renderCard(ctx, shown.get(i), i, formX, y, formWidth, mouseX, mouseY);
			y += CARD_HEIGHT + CARD_GAP;
		}

		return y - CARD_GAP + IndexScreen.SETTINGS_ROW_GAP;
	}

	// The Discord card only makes sense while linking is switched on
	private static List<CosmeticTags.Tag> visible()
	{
		List<CosmeticTags.Tag> shown = new ArrayList<>();

		for (CosmeticTags.Tag tag : CosmeticTags.locked())
		{
			if (tag.actions().contains(LINK_CODE) && !DiscordLink.enabled())
			{
				continue;
			}

			shown.add(tag);
		}

		return shown;
	}

	private void renderCard(GuiGraphicsExtractor ctx, CosmeticTags.Tag tag, int index, int x, int y, int width,
			int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		Theme.roundedRect(ctx, x, y, width, CARD_HEIGHT, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, CARD_HEIGHT, Theme.RADIUS_CARD, Theme.HAIRLINE);
		Theme.text(ctx, font, Cosmetics.tagOf(tag), x + 8, y + 7, Theme.TEXT);

		if (tag.hasRarity())
		{
			String rarity = rarityText(tag);
			Theme.text(ctx, font, rarity, x + width - 8 - font.width(rarity), y + 7, Theme.TEXT_ASH);
		}

		String hint = sentence(tag.hint().isBlank() ? tag.description() : tag.hint());
		int hintY = y + 7 + font.lineHeight + 4;
		Theme.text(ctx, font, Theme.clip(font, hint, width - 16), x + 8, hintY, Theme.TEXT_MUTE);
		int rowY = hintY + font.lineHeight + 6;

		if (tag.pendingUnlock())
		{
			Theme.text(ctx, font, "Unlocks at 1.0.0 \u2714", x + 8, rowY + 5, Theme.ACCENT_BRIGHT);
		}
		else if (!tag.actions().isEmpty())
		{
			this.renderActions(ctx, tag, index, x + 8, rowY, mouseX, mouseY);
		}
		else if (tag.hasProgress())
		{
			this.renderProgress(ctx, tag, x + 8, rowY + 6, width - 16);
		}
	}

	private static String sentence(String text)
	{
		String trimmed = text.strip();

		if (trimmed.isEmpty() || trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?"))
		{
			return trimmed;
		}

		return trimmed + ".";
	}

	private void renderProgress(GuiGraphicsExtractor ctx, CosmeticTags.Tag tag, int x, int y, int width)
	{
		Font font = this.screen.font();
		int have = Math.min(tag.have(), tag.need());
		String count = have + "/" + tag.need();
		int barWidth = width - font.width(count) - 8;
		int filled = Math.round(barWidth * (have / (float) tag.need()));
		Theme.roundedRect(ctx, x, y, barWidth, BAR_HEIGHT, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);

		if (filled > 0)
		{
			Theme.roundedRect(ctx, x, y, filled, BAR_HEIGHT, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
		}

		Theme.text(ctx, font, count, x + barWidth + 8, y - 2, Theme.TEXT_MUTE);
	}

	private void renderActions(GuiGraphicsExtractor ctx, CosmeticTags.Tag tag, int index, int x, int y, int mouseX,
			int mouseY)
	{
		Font font = this.screen.font();

		if (tag.actions().contains(JOIN_DISCORD))
		{
			Rect join = Rect.pooled(this.joinButtons, index);
			join.set(x, y, font.width(Theme.bold("Join Discord")) + 24, IndexScreen.FIELD_HEIGHT);
			Buttons.pill(ctx, font, join, "Join Discord", mouseX, mouseY, false);
			x += join.width + 6;
		}

		if (tag.actions().contains(LINK_CODE))
		{
			Rect code = Rect.pooled(this.codeButtons, index);
			code.set(x, y, font.width(Theme.bold("Get link code")) + 24, IndexScreen.FIELD_HEIGHT);
			Buttons.pill(ctx, font, code, "Get link code", mouseX, mouseY, true);
		}
	}

	private void clear()
	{
		this.showcase.clear();

		for (Rect rect : this.joinButtons)
		{
			rect.set(0, 0, 0, 0);
		}

		for (Rect rect : this.codeButtons)
		{
			rect.set(0, 0, 0, 0);
		}
	}
}

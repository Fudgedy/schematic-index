package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

class TagShelf
{
	private final IndexScreen screen;
	private final CosmeticsPage page;
	final Rect buyButton = new Rect();
	final AchievementShelf achievements;
	// One chip per owned or shop tag, laid out fresh each frame from whatever the server sent
	final List<Rect> rects = new ArrayList<>();
	final List<Integer> ids = new ArrayList<>();
	int previewTag = CosmeticTags.NONE;
	private int lastRowY;

	TagShelf(IndexScreen screen, CosmeticsPage page)
	{
		this.screen = screen;
		this.page = page;
		this.achievements = new AchievementShelf(screen);
	}

	// Earned tags: staff author them, a player only wears one. Owned tags toggle on click; a shop tag is
	// previewed first and only then offers its buy, matching how presets are sold
	int render(GuiGraphics ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		y = this.screen.settingsSectionHeader(ctx, "Tags", formX, y);
		y = this.screen.settingsDescription(ctx, "Show off your nametag with a cool tag alongside it.",
				1, formX, y, formWidth);

		this.ids.clear();

		if (CosmeticTags.owned().isEmpty() && CosmeticTags.shop().isEmpty())
		{
			this.buyButton.set(0, 0, 0, 0);
			y = this.screen.settingsDescription(ctx, "You have no tags yet.", 1, formX, y, formWidth);
			return this.achievements.render(ctx, formX, y, formWidth, mouseX, mouseY);
		}

		int chip = 0;

		if (!CosmeticTags.owned().isEmpty())
		{
			y = this.screen.settingsSectionHeader(ctx, "Owned", formX, y);
			chip = this.renderRow(ctx, CosmeticTags.owned(), chip, formX, y, formWidth, mouseX, mouseY, true);
			y = this.rowBottom(chip, y);
		}

		if (!CosmeticTags.shop().isEmpty())
		{
			y = this.screen.settingsSectionHeader(ctx, "Unowned", formX, y);
			int before = chip;
			chip = this.renderRow(ctx, CosmeticTags.shop(), chip, formX, y, formWidth, mouseX, mouseY, false);
			y = this.rowBottom(chip - before, y);
		}

		y = this.renderDescription(ctx, formX, y, formWidth, mouseX, mouseY);
		CosmeticTags.Tag previewed = this.previewedShopTag();

		if (previewed == null)
		{
			this.buyButton.set(0, 0, 0, 0);
			return this.achievements.render(ctx, formX, y, formWidth, mouseX, mouseY);
		}

		int width = this.page.shardButtonWidth("Buy", previewed.price());
		this.buyButton.set(formX, y, width, IndexScreen.FIELD_HEIGHT);
		this.page.renderShardBuyButton(ctx, this.buyButton, "Buy", previewed.price(), mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + IndexScreen.SETTINGS_ROW_GAP;

		return this.achievements.render(ctx, formX, y, formWidth, mouseX, mouseY);
	}

	// An owned tag toggles on and off; a shop tag only arms the preview, so nothing is bought by one click
	boolean click(int id)
	{
		this.screen.setFocused(null);

		for (CosmeticTags.Tag tag : CosmeticTags.owned())
		{
			if (tag.id() == id)
			{
				this.previewTag = CosmeticTags.NONE;
				CosmeticTags.equip(id == CosmeticTags.equipped() ? CosmeticTags.NONE : id,
						this.page::rebuild);
				Theme.click();
				return true;
			}
		}

		this.previewTag = this.previewTag == id ? CosmeticTags.NONE : id;
		this.page.presets.clearPreview();
		this.page.revealBuy = this.previewTag != CosmeticTags.NONE;
		Theme.click();
		return true;
	}

	CosmeticTags.Tag previewedShopTag()
	{
		for (CosmeticTags.Tag tag : CosmeticTags.shop())
		{
			if (tag.id() == this.previewTag)
			{
				return tag;
			}
		}

		return null;
	}

	// Lays one wrapped run of tag chips; returns the next free chip slot so the two sections share the map
	private int renderRow(GuiGraphics ctx, List<CosmeticTags.Tag> tags, int chip, int formX, int y,
			int formWidth, int mouseX, int mouseY, boolean owned)
	{
		Font font = this.screen.font();
		int x = formX;
		int row = y;

		for (CosmeticTags.Tag tag : tags)
		{
			Component drawn = Cosmetics.tagOf(tag);
			String badge = badgeOf(tag, owned);
			int badgeWidth = badge == null ? 0 : Limited.badgeWidth(font, badge) + 4;
			int width = Math.min(font.width(drawn) + 14 + badgeWidth, formWidth);

			if (x + width > formX + formWidth)
			{
				x = formX;
				row += IndexScreen.FIELD_HEIGHT + 6;
			}

			boolean marked = owned ? tag.id() == CosmeticTags.equipped() : tag.id() == this.previewTag;
			Rect.pooled(this.rects, chip).set(x, row, width, IndexScreen.FIELD_HEIGHT);
			this.ids.add(tag.id());
			this.renderChip(ctx, drawn, x, row, width, marked, mouseX, mouseY);

			if (badge != null)
			{
				Limited.badge(ctx, font, badge, x + width - 3 - badgeWidth + 4, row + 3,
						tag.vaulted() ? Theme.TEXT_MUTE : Limited.accent());
			}

			x += width + 6;
			chip++;
		}

		this.lastRowY = row;
		return chip;
	}

	// A fixed two-line slot, so hovering from chip to chip never shifts the buy button below it
	private int renderDescription(GuiGraphics ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		if (!hasDescriptions())
		{
			return y;
		}

		CosmeticTags.Tag focused = this.focusedTag(mouseX, mouseY);
		String text = focused == null || focused.description().isBlank()
				? "Hover a tag to see what it is for."
				: focused.description();

		if (focused != null && focused.hasRarity())
		{
			text += " " + AchievementShelf.rarityText(focused) + ".";
		}
		this.screen.settingsDescription(ctx, text, 2, formX, y, formWidth);

		return y + 2 * (this.screen.font().lineHeight + 2) + IndexScreen.SETTINGS_HINT_GAP;
	}

	private CosmeticTags.Tag focusedTag(int mouseX, int mouseY)
	{
		for (int i = 0; i < this.ids.size() && i < this.rects.size(); i++)
		{
			if (this.rects.get(i).contains(mouseX, mouseY))
			{
				return byId(this.ids.get(i));
			}
		}

		CosmeticTags.Tag previewed = this.previewedShopTag();
		return previewed != null ? previewed : byId(CosmeticTags.equipped());
	}

	// A seasonal shop tag counts down; an owned one past its window is kept but marked as no longer sold
	private static String badgeOf(CosmeticTags.Tag tag, boolean owned)
	{
		if (owned)
		{
			return tag.vaulted() ? "Vaulted" : null;
		}

		return tag.limited() && Limited.shown() ? "Limited · " + Limited.left(tag.untilMs()) : null;
	}

	private static CosmeticTags.Tag byId(int id)
	{
		for (CosmeticTags.Tag tag : CosmeticTags.owned())
		{
			if (tag.id() == id)
			{
				return tag;
			}
		}

		for (CosmeticTags.Tag tag : CosmeticTags.shop())
		{
			if (tag.id() == id)
			{
				return tag;
			}
		}

		return null;
	}

	private static boolean hasDescriptions()
	{
		for (CosmeticTags.Tag tag : CosmeticTags.owned())
		{
			if (!tag.description().isBlank())
			{
				return true;
			}
		}

		for (CosmeticTags.Tag tag : CosmeticTags.shop())
		{
			if (!tag.description().isBlank())
			{
				return true;
			}
		}

		return false;
	}

	private int rowBottom(int drawn, int y)
	{
		return drawn == 0 ? y : this.lastRowY + IndexScreen.FIELD_HEIGHT + IndexScreen.SETTINGS_ROW_GAP;
	}

	private void renderChip(GuiGraphics ctx, Component drawn, int x, int y, int width, boolean marked,
			int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		boolean hovered = Theme.inside(mouseX, mouseY, x, y, width, IndexScreen.FIELD_HEIGHT);
		Theme.roundedRect(ctx, x, y, width, IndexScreen.FIELD_HEIGHT, Theme.RADIUS_PILL,
				Theme.lighten(Theme.SURFACE_CARD, hovered ? 0.12F : 0.0F));
		Theme.roundedOutline(ctx, x, y, width, IndexScreen.FIELD_HEIGHT, Theme.RADIUS_PILL,
				marked ? Theme.ACCENT_BRIGHT : Theme.HAIRLINE);
		// A styled component has no ellipsis form, so an over-long tag is cut at the chip edge instead
		ctx.enableScissor(x, y, x + width - 4, y + IndexScreen.FIELD_HEIGHT);
		Theme.text(ctx, font, drawn, x + 7, y + (IndexScreen.FIELD_HEIGHT - font.lineHeight) / 2 + 1, Theme.TEXT);
		ctx.disableScissor();
	}
}

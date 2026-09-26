package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Showcase;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

// Owned achievements above the locked cards, pinned ones first, each with a pin toggle for the profile showcase
class ShowcaseShelf
{
	private static final int ROW_HEIGHT = 22;
	private static final int ROW_GAP = 4;

	private final IndexScreen screen;
	private final List<Rect> pins = new ArrayList<>();
	private final List<Integer> pinIds = new ArrayList<>();

	ShowcaseShelf(IndexScreen screen)
	{
		this.screen = screen;
	}

	static List<CosmeticTags.Tag> owned()
	{
		List<CosmeticTags.Tag> pinned = new ArrayList<>();
		List<CosmeticTags.Tag> rest = new ArrayList<>();

		for (int id : Showcase.pinned())
		{
			for (CosmeticTags.Tag tag : CosmeticTags.owned())
			{
				if (tag.id() == id && isAchievement(tag))
				{
					pinned.add(tag);
				}
			}
		}

		for (CosmeticTags.Tag tag : CosmeticTags.owned())
		{
			if (isAchievement(tag) && !Showcase.isPinned(tag.id()))
			{
				rest.add(tag);
			}
		}

		pinned.addAll(rest);
		return pinned;
	}

	int render(GuiGraphicsExtractor ctx, List<CosmeticTags.Tag> owned, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		this.clear();

		if (owned.isEmpty())
		{
			return y;
		}

		Showcase.refreshIfStale();
		boolean canPin = McAuth.verified();
		String count = Showcase.pinned().size() + "/" + Showcase.MAX + " pinned";
		String intro = canPin ? "Pin up to " + Showcase.MAX + " to show them first on your profile (" + count + ")."
				: "Verify your account to pin achievements to your profile.";
		y = this.screen.settingsDescription(ctx, intro, 2, formX, y, formWidth);

		for (int i = 0; i < owned.size(); i++)
		{
			this.renderRow(ctx, owned.get(i), i, canPin, formX, y, formWidth, mouseX, mouseY);
			y += ROW_HEIGHT + ROW_GAP;
		}

		return y - ROW_GAP + IndexScreen.SETTINGS_ROW_GAP;
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		for (int i = 0; i < this.pins.size() && i < this.pinIds.size(); i++)
		{
			if (this.pins.get(i).contains(mouseX, mouseY))
			{
				Theme.click(Showcase.isPinned(this.pinIds.get(i)) ? 0.9F : 1.1F);
				Showcase.toggle(this.pinIds.get(i));
				return true;
			}
		}

		return false;
	}

	private void renderRow(GuiGraphicsExtractor ctx, CosmeticTags.Tag tag, int index, boolean canPin, int x, int y, int width,
			int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		boolean pinned = Showcase.isPinned(tag.id());
		Theme.roundedRect(ctx, x, y, width, ROW_HEIGHT, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, ROW_HEIGHT, Theme.RADIUS_CARD, pinned ? Theme.GOLD : Theme.HAIRLINE);
		int textY = y + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
		Theme.text(ctx, font, Cosmetics.tagOf(tag), x + 8, textY, Theme.TEXT);

		Rect pin = Rect.pooled(this.pins, index);
		this.pinIds.add(tag.id());
		int right = x + width - 5;

		if (canPin)
		{
			String label = pinned ? "Unpin" : "Pin";
			int pinWidth = font.width(Theme.bold(label)) + 16;
			pin.set(right - pinWidth, y + 4, pinWidth, ROW_HEIGHT - 8);
			boolean full = !pinned && Showcase.pinned().size() >= Showcase.MAX;

			if (full || Showcase.isSaving())
			{
				Buttons.disabled(ctx, font, pin, label);
				pin.set(0, 0, 0, 0);
			}
			else
			{
				Buttons.pill(ctx, font, pin, label, mouseX, mouseY, !pinned);
			}

			right -= pinWidth + 8;
		}

		if (tag.hasRarity())
		{
			String rarity = AchievementShelf.rarityText(tag);
			Theme.text(ctx, font, rarity, right - font.width(rarity), textY, Theme.TEXT_ASH);
		}
	}

	private static boolean isAchievement(CosmeticTags.Tag tag)
	{
		return tag.kind().equals("achievement");
	}

	void clear()
	{
		this.pinIds.clear();

		for (Rect rect : this.pins)
		{
			rect.set(0, 0, 0, 0);
		}
	}
}

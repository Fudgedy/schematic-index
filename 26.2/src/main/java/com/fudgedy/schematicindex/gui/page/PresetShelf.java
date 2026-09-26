package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class PresetShelf
{
	private static final int PER_ROW = 5;

	private final IndexScreen screen;
	private final CosmeticsPage page;
	final Rect buyButton = new Rect();
	private final Rect wearButton = new Rect();
	private final List<Rect> rects = new ArrayList<>();
	// Kept by name, so a server list that reorders between frames never moves the preview to a neighbour
	private @Nullable String preview;

	PresetShelf(IndexScreen screen, CosmeticsPage page)
	{
		this.screen = screen;
		this.page = page;
	}

	@Nullable Cosmetics.Preset previewed()
	{
		if (this.preview == null)
		{
			return null;
		}

		for (Cosmetics.Preset preset : Cosmetics.presets())
		{
			if (preset.name().equals(this.preview))
			{
				return preset;
			}
		}

		return null;
	}

	void clearPreview()
	{
		this.preview = null;
	}

	// Presets show the name first and only then offer the buy, so nobody pays for a palette sight unseen
	int render(GuiGraphicsExtractor ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		List<Cosmetics.Preset> presets = Cosmetics.presets();
		y = this.screen.settingsSectionHeader(ctx, "Presets", formX, y);
		y = this.screen.settingsDescription(ctx, "A preset is a ready made gradient which you can buy for a discount "
				+ "compared to buying colours separately. Click on a preset to preview it above. Owned presets "
				+ "have a dot in the top right corner.", 4, formX, y, formWidth);

		int presetWidth = (formWidth - (PER_ROW - 1) * 6) / PER_ROW;
		int rows = 0;

		for (int i = 0; i < presets.size(); i++)
		{
			rows = i / PER_ROW;
			int px = formX + i % PER_ROW * (presetWidth + 6);
			int py = y + rows * (IndexScreen.FIELD_HEIGHT + 6);
			Rect.pooled(this.rects, i).set(px, py, presetWidth, IndexScreen.FIELD_HEIGHT);
			Cosmetics.Preset preset = presets.get(i);
			this.renderSwatch(ctx, preset, px, py, presetWidth, mouseX, mouseY);
		}

		for (int i = presets.size(); i < this.rects.size(); i++)
		{
			this.rects.get(i).set(0, 0, 0, 0);
		}

		y += (rows + 1) * (IndexScreen.FIELD_HEIGHT + 6) - 6 + IndexScreen.SETTINGS_ROW_GAP;
		this.buyButton.set(0, 0, 0, 0);
		this.wearButton.set(0, 0, 0, 0);
		Cosmetics.Preset preset = this.previewed();

		if (preset == null)
		{
			return y;
		}

		Rect button;

		if (CosmeticColors.ownsPreset(preset.name()))
		{
			String wear = "Wear (" + preset.name() + ")";
			button = this.wearButton;
			button.set(formX, y, font.width(Theme.bold(wear)) + 24, IndexScreen.FIELD_HEIGHT);
			Buttons.pill(ctx, font, button, wear, mouseX, mouseY, true);
		}
		else
		{
			int price = CosmeticColors.priceOf(preset);
			button = this.buyButton;

			if (price > 0)
			{
				button.set(formX, y, this.page.shardButtonWidth("Buy", price), IndexScreen.FIELD_HEIGHT);
				this.page.renderShardBuyButton(ctx, button, "Buy", price, mouseX, mouseY);
			}
			else
			{
				button.set(formX, y, font.width(Theme.bold("Free")) + 24, IndexScreen.FIELD_HEIGHT);
				Buttons.pill(ctx, font, button, "Free", mouseX, mouseY, true);
			}
		}

		String note = note(preset);

		if (note != null)
		{
			Theme.text(ctx, font, note, button.x + button.width + 8, y + (IndexScreen.FIELD_HEIGHT - font.lineHeight) / 2 + 1,
					preset.vaulted() ? Theme.TEXT_ASH : Limited.accent());
		}

		return y + IndexScreen.FIELD_HEIGHT + IndexScreen.SETTINGS_ROW_GAP;
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		Cosmetics.Preset previewed = this.previewed();

		if (previewed != null && this.wearButton.contains(mouseX, mouseY))
		{
			Cosmetics.wearPreset(previewed);
			this.preview = null;
			this.page.afterChange();
			return true;
		}

		if (previewed != null && this.buyButton.contains(mouseX, mouseY))
		{
			// The preview survives the purchase so Wear is waiting where the Buy button just was
			CosmeticColors.buyPreset(previewed.name(), CosmeticColors.priceOf(previewed), this.page::rebuild);
			return true;
		}

		List<Cosmetics.Preset> presets = Cosmetics.presets();

		for (int i = 0; i < presets.size() && i < this.rects.size(); i++)
		{
			if (!this.rects.get(i).contains(mouseX, mouseY))
			{
				continue;
			}

			Cosmetics.Preset preset = presets.get(i);
			this.preview = preset.name().equals(this.preview) ? null : preset.name();
			this.page.tags.previewTag = CosmeticTags.NONE;
			this.page.revealBuy = this.preview != null && !CosmeticColors.ownsPreset(preset.name());
			this.screen.setFocused(null);
			Theme.click();
			return true;
		}

		return false;
	}

	private static @Nullable String note(Cosmetics.Preset preset)
	{
		if (preset.vaulted())
		{
			return "Vaulted: no longer sold";
		}

		return preset.limited() && Limited.shown() ? "Limited · " + Limited.left(preset.untilMs()) : null;
	}

	private void renderSwatch(GuiGraphicsExtractor ctx, Cosmetics.Preset preset, int x, int y, int width, int mouseX,
			int mouseY)
	{
		int[] stops = preset.stops();
		int height = IndexScreen.FIELD_HEIGHT;

		for (int sx = 0; sx < width; sx++)
		{
			float t = width <= 1 ? 0.0F : (float) sx / (width - 1);
			ctx.fill(x + sx, y, x + sx + 1, y + height, 0xFF000000 | Cosmetics.sample(stops, t));
		}

		boolean hovered = Theme.inside(mouseX, mouseY, x, y, width, height);
		boolean previewing = preset.name().equals(this.preview);
		boolean limited = preset.limited() && Limited.shown();
		int edge = hovered || previewing ? Theme.ACCENT_BRIGHT : limited ? Limited.accent() : 0xFFFFFFFF;
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, edge);

		if (CosmeticColors.ownsPreset(preset.name()))
		{
			ctx.fill(x + width - 6, y + 2, x + width - 2, y + 6, Theme.ACCENT_BRIGHT);
		}

		Font font = this.screen.font();

		if (limited)
		{
			Limited.badge(ctx, font, Limited.left(preset.untilMs()), x + 2, y + 2, Limited.accent());
		}
		else if (preset.vaulted())
		{
			Limited.badge(ctx, font, "Vaulted", x + 2, y + 2, Theme.TEXT_MUTE);
		}
	}
}

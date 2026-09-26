package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.fx.Effect;
import com.fudgedy.schematicindex.fx.Effects;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ColorPicker;
import com.fudgedy.schematicindex.gui.widget.Fields;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CosmeticsPage
{
	// Palette geometry: three rows of six, dropping to five columns once the gradient strip claims a column
	private static final int PALETTE_SLOTS = 18;
	private static final int PALETTE_ROWS = 3;
	private static final int PALETTE_SWATCH = 26;
	private static final int PALETTE_GAP = 6;
	private static final int GRADIENT_STRIP_GAP = 24;
	private static final long REVEAL_MS = 220L;
	private static final int EFFECT_CARD_HEIGHT = IndexScreen.FIELD_HEIGHT + 16;
	private static final int EFFECT_CARD_GAP = 6;
	// Vanilla's nametag backing at the default text background opacity
	private static final int NAMETAG_BACKDROP = 0x40000000;

	private final IndexScreen screen;
	final TagShelf tags;
	final PresetShelf presets;
	private final SeasonBanner season;
	private final Rect offButton = new Rect();
	private final Rect gradientButton = new Rect();
	private final Rect buyButton = new Rect();
	private final Rect refundButton = new Rect();
	private final ColorPicker picker = new ColorPicker();
	private final Rect[] slotRects = newRects(PALETTE_SLOTS);
	// Bundled icon per effect id, null for a server-added effect without one; checked once, not per frame
	private final Map<String, Identifier> effectIcons = new HashMap<>();
	// The owned colour drawn in each palette slot, so a click maps back to the id the server knows
	private final int[] slotIds = new int[PALETTE_SLOTS];
	private final Rect[] gradientRects = newRects(Cosmetics.GRADIENT_SLOTS);
	// The effects on sale, as last laid out; a click maps back through this rather than a fixed list
	private List<Effect> shop = List.of();
	private Rect[] effectCards = newRects(0);
	private Rect[] effectButtons = newRects(0);
	// The effect card under the pointer, played on the preview before it is bought
	private int effectHover = -1;
	private int previewEffect = -1;
	// The picker keeps its own HSV so hue survives dragging saturation or value to zero
	private int pickColor = Theme.ACCENT & 0xFFFFFF;
	// The gradient slot the pointer picked up; a release on the same slot is a click, not a reorder
	private int gradientDrag = -1;
	private int dragX;
	private int dragY;
	// Buy only offers itself once the player has chosen a colour
	private boolean picked = true;
	private int selected = Cosmetics.EMPTY;
	// Armed by a shop tag or preset click; the frame after lays out its Buy button and glides down to it
	boolean revealBuy;
	private float scrollFrom;
	private float scrollTarget = -1.0F;
	private long scrollStartedAt;
	// Set while the field is rewritten from the picker, so the responder ignores its own write
	private boolean syncingHex;
	private EditBox hexBox;

	public CosmeticsPage(IndexScreen screen)
	{
		this.screen = screen;
		this.tags = new TagShelf(screen, this);
		this.presets = new PresetShelf(screen, this);
		this.season = new SeasonBanner(screen);
	}

	// Screen init also follows a resource reload, so the icon lookups start over here
	public void buildFields()
	{
		this.effectIcons.clear();
		this.hexBox = FormFields.textField(this.screen, 0, 0, 70, "RRGGBB", String.format("%06X", this.pickColor));
		this.hexBox.setMaxLength(6);
		this.hexBox.setResponder(this::applyHex);
		this.seedPicker();
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY, float partialTick)
	{
		Font font = this.screen.font();
		int top = IndexScreen.TOP_BAR_HEIGHT + 12;
		int bottom = this.screen.height - IndexScreen.OUTER_MARGIN;
		int formWidth = Math.min(this.screen.contentWidth - 48, 420);
		int formX = this.screen.contentX + 24;

		ctx.enableScissor(this.screen.contentX, top, this.screen.contentX + this.screen.contentWidth, bottom);

		int startY = top - Math.round(this.screen.scroll);
		int y = startY;

		// A refunded colour must not keep styling the name, so the loadout is reconciled before it is drawn
		Cosmetics.pruneUnowned();
		this.easeScroll();
		this.effectHover = this.hoveredEffect(mouseX, mouseY);

		Theme.textScaled(ctx, font, Theme.bold("Nametag Cosmetics"), formX, y, 1.5F, Theme.TEXT);
		y += 36;
		y = this.season.render(ctx, formX, y, formWidth);

		y = this.screen.settingsDescription(ctx, "Use Colours & Tags, to customize your name exactly how you want "
				+ "to, your nametag is visible to all who are playing with the mod, including your cosmetic "
				+ "changes.", 4, formX, y, formWidth);

		y = this.renderPreview(ctx, formX, y, formWidth);

		y = this.screen.settingsSectionHeader(ctx, "Style", formX, y);

		int segment = (formWidth - 6) / 2;
		this.offButton.set(formX, y, segment, IndexScreen.FIELD_HEIGHT);
		this.gradientButton.set(formX + segment + 6, y, formWidth - segment - 6, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.offButton, "Off", mouseX, mouseY,
				Cosmetics.mode() == Cosmetics.Mode.NONE);
		Buttons.pill(ctx, font, this.gradientButton, "Gradient", mouseX, mouseY,
				Cosmetics.mode() == Cosmetics.Mode.GRADIENT);
		y += IndexScreen.FIELD_HEIGHT + IndexScreen.SETTINGS_ROW_GAP;

		y = this.renderPalette(ctx, formX, y, formWidth, mouseX, mouseY);
		y = this.renderColorPicker(ctx, formX, y, formWidth, mouseX, mouseY, partialTick);
		y = this.presets.render(ctx, formX, y, formWidth, mouseX, mouseY);
		y = this.renderEffects(ctx, formX, y, formWidth, mouseX, mouseY);
		y = this.tags.render(ctx, formX, y, formWidth, mouseX, mouseY);
		this.renderCarriedSlot(ctx);

		ctx.disableScissor();

		int contentHeight = y - startY;
		this.screen.maxScroll = Math.max(0.0F, contentHeight - (bottom - top));
		this.screen.scroll = Math.min(this.screen.scroll, this.screen.maxScroll);

		if (this.revealBuy)
		{
			this.revealBuy = false;
			this.revealButton(this.tags.previewTag != CosmeticTags.NONE
					? this.tags.buyButton : this.presets.buyButton, bottom);
		}

		this.screen.verticalScrollbar(ctx, IndexScreen.SCROLLBAR_MAIN, this.screen.scroll, this.screen.maxScroll, top,
				bottom - top, this.screen.contentX + this.screen.contentWidth);
	}

	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY)
	{
		if (this.tags.achievements.mouseClicked(mouseX, mouseY))
		{
			return true;
		}
		if (this.offButton.contains(mouseX, mouseY))
		{
			return this.switchMode(Cosmetics.Mode.NONE);
		}
		if (this.gradientButton.contains(mouseX, mouseY))
		{
			return this.switchMode(Cosmetics.Mode.GRADIENT);
		}
		if (this.buyButton.contains(mouseX, mouseY))
		{
			int hex = this.pickColor;
			CosmeticColors.buy(hex, () ->
			{
				this.picked = false;
				this.wearNewest();
				this.rebuild();
			});
			return true;
		}
		if (this.refundButton.contains(mouseX, mouseY))
		{
			int id = this.selected;
			CosmeticColors.refund(id, () ->
			{
				this.selected = Cosmetics.EMPTY;
				this.afterChange();
			});
			return true;
		}
		if (this.tags.buyButton.contains(mouseX, mouseY))
		{
			CosmeticTags.Tag tag = this.tags.previewedShopTag();

			if (tag != null)
			{
				CosmeticTags.buy(tag, () ->
				{
					this.tags.previewTag = CosmeticTags.NONE;
					CosmeticTags.equip(tag.id(), this::rebuild);
				});
			}

			return true;
		}
		for (int i = 0; i < this.effectButtons.length; i++)
		{
			if (this.effectButtons[i].contains(mouseX, mouseY))
			{
				return this.clickEffect(i);
			}
		}
		for (int i = 0; i < this.effectCards.length; i++)
		{
			if (this.effectCards[i].contains(mouseX, mouseY))
			{
				this.previewEffect = this.previewEffect == i ? -1 : i;
				this.screen.setFocused(null);
				Theme.click();
				return true;
			}
		}
		if (this.presets.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		// The strip is picked up rather than acted on, so the release can tell a reorder from a click
		for (int i = 0; i < this.gradientRects.length; i++)
		{
			if (this.gradientRects[i].contains(mouseX, mouseY))
			{
				this.gradientDrag = i;
				this.dragX = (int) mouseX;
				this.dragY = (int) mouseY;
				this.screen.setFocused(null);
				return true;
			}
		}

		for (int i = 0; i < this.slotRects.length; i++)
		{
			if (this.slotRects[i].contains(mouseX, mouseY) && this.slotIds[i] != Cosmetics.EMPTY)
			{
				return this.clickPaletteSlot(this.slotIds[i]);
			}
		}

		for (int i = 0; i < this.tags.ids.size(); i++)
		{
			if (this.tags.rects.get(i).contains(mouseX, mouseY))
			{
				return this.tags.click(this.tags.ids.get(i));
			}
		}

		if (this.picker.mouseClicked(mouseX, mouseY))
		{
			this.screen.setFocused(null);
			this.afterPickerDrag();
			return true;
		}

		if (this.hexBox != null && FormFields.focusField(this.screen, this.hexBox, event, doubleClick, mouseX, mouseY))
		{
			return true;
		}

		this.screen.setFocused(null);
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY)
	{
		if (this.picker.mouseDragged(mouseX, mouseY))
		{
			this.afterPickerDrag();
			return true;
		}

		if (this.gradientDrag >= 0)
		{
			this.dragX = (int) mouseX;
			this.dragY = (int) mouseY;
			return true;
		}

		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY)
	{
		if (this.picker.mouseReleased())
		{
			return true;
		}

		if (this.gradientDrag >= 0)
		{
			return this.releaseGradientDrag(mouseX, mouseY);
		}

		return false;
	}

	public void resetPreviewEffect()
	{
		this.previewEffect = -1;
	}

	public void cancelReveal()
	{
		this.scrollTarget = -1.0F;
	}

	// A half-typed hex is not an error; the last valid colour stays until six digits parse
	private void applyHex(String value)
	{
		if (this.syncingHex)
		{
			return;
		}

		try
		{
			this.pickColor = Integer.parseInt(value.trim(), 16) & 0xFFFFFF;
			this.picked = true;
			this.seedPicker();
		}
		catch (NumberFormatException ignored)
		{
		}
	}

	// Loads the square and bar from the picked colour; kept separate so a drag never re-derives its own hue
	private void seedPicker()
	{
		this.picker.seed(this.pickColor);
	}

	private void refreshHexBox()
	{
		if (this.hexBox == null)
		{
			return;
		}

		// Re-seeding from this write would round the hue back through 8-bit RGB and lose it at zero
		// saturation or value, snapping the bar to red mid-drag
		this.syncingHex = true;
		this.hexBox.setValue(String.format("%06X", this.pickColor));
		this.syncingHex = false;
	}

	private static Rect[] newRects(int count)
	{
		Rect[] rects = new Rect[count];

		for (int i = 0; i < count; i++)
		{
			rects[i] = new Rect();
		}

		return rects;
	}

	// Scrolls down only, and only as far as the button's bottom needs to clear the viewport edge
	private void revealButton(Rect button, int bottom)
	{
		float overflow = button.y + button.height + IndexScreen.SETTINGS_ROW_GAP - bottom;

		if (button.height <= 0 || overflow <= 0.0F)
		{
			return;
		}

		this.scrollFrom = this.screen.scroll;
		this.scrollTarget = Math.min(this.screen.maxScroll, this.screen.scroll + overflow);
		this.scrollStartedAt = Util.getMillis();
	}

	private void easeScroll()
	{
		if (this.scrollTarget < 0.0F)
		{
			return;
		}

		float fraction = Theme.easeOut((Util.getMillis() - this.scrollStartedAt) / (float) REVEAL_MS);
		float target = Math.min(this.scrollTarget, this.screen.maxScroll);
		this.screen.scroll = this.scrollFrom + (target - this.scrollFrom) * fraction;

		if (fraction >= 1.0F)
		{
			this.scrollTarget = -1.0F;
		}
	}

	// Stands in for the in-world nametag, which the game never draws for the local player. A previewed preset
	// wins over the worn loadout, and the current team wraps the name exactly as the nametag mixin does
	// The nametag as it floats over the player, badge and backing included, doubled so the plate reads
	private int renderPreview(GuiGraphics ctx, int formX, int y, int formWidth)
	{
		Minecraft minecraft = Minecraft.getInstance();
		Font font = this.screen.font();
		int previewHeight = 46;
		Theme.roundedRect(ctx, formX, y, formWidth, previewHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, formX, y, formWidth, previewHeight, Theme.RADIUS_CARD, Theme.HAIRLINE);

		String name = minecraft != null && minecraft.getUser() != null
				? minecraft.getUser().getName() : "Fudgedy";
		String effect = this.previewedEffect();
		Team team = minecraft == null || minecraft.player == null ? null : minecraft.player.getTeam();
		Cosmetics.Preset previewed = this.presets.previewed();
		Component bare = previewed != null
				? Cosmetics.preview(name, previewed.stops(), Style.EMPTY, effect)
				: Cosmetics.apply(name, Style.EMPTY, effect);
		CosmeticTags.Tag previewTag = this.tags.previewedShopTag();
		Component worn = previewTag != null ? Cosmetics.tagOf(previewTag) : Cosmetics.tag();
		Component nametag = ModTags.compose(bare, team, worn);
		int width = Math.max(1, font.width(nametag));
		float scale = Math.min(2.0F, (formWidth - 12) / (float) width);
		this.renderNametagAt(ctx, nametag, formX + formWidth / 2, y + (previewHeight - font.lineHeight) / 2, scale);

		return y + previewHeight + IndexScreen.SETTINGS_ROW_GAP;
	}

	private void renderNametagAt(GuiGraphics ctx, Component nametag, int centreX, int top, float scale)
	{
		Font font = this.screen.font();
		int width = Math.max(1, font.width(nametag));
		int line = font.lineHeight;
		int x = centreX - width / 2;
		// pushScale grows the text about its centre, so the top edge is pulled down by half the growth
		int y = top + Math.round(line * (scale - 1.0F) / 2.0F);
		Theme.pushScale(ctx, x, y, width, line, scale);
		ctx.fill(x - 1, y - 1, x + width + 1, y + line, NAMETAG_BACKDROP);
		Theme.text(ctx, font, nametag, x, y, Theme.TEXT);
		Theme.pop(ctx);
	}

	// The owned palette: every slot carries a white border, an unowned one stays blank. In gradient mode the
	// three gradient slots leave the grid and sit apart as their own horizontal strip
	private int renderPalette(GuiGraphics ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		boolean gradient = Cosmetics.mode() == Cosmetics.Mode.GRADIENT;
		int max = Math.min(CosmeticColors.max(), PALETTE_SLOTS);
		int columns = PALETTE_SLOTS / PALETTE_ROWS;

		y = this.screen.settingsSectionHeader(ctx, "Your palette", formX, y);
		y = this.screen.settingsDescription(ctx, "You own " + CosmeticColors.count() + " of " + max + " colours. A "
				+ "colour is fixed once bought" + (gradient
						? ". Click one to send it to the gradient; drag the gradient slots to reorder, click one to empty it."
						: ". Click one to wear it."), 3, formX, y, formWidth);

		this.layOutPalette(max);

		int slotIndex = 0;

		for (int row = 0; row < PALETTE_ROWS; row++)
		{
			for (int column = 0; column < columns && slotIndex < max; column++, slotIndex++)
			{
				int sx = formX + column * (PALETTE_SWATCH + PALETTE_GAP);
				int sy = y + row * (PALETTE_SWATCH + PALETTE_GAP);
				this.slotRects[slotIndex].set(sx, sy, PALETTE_SWATCH, PALETTE_SWATCH);
				int id = this.slotIds[slotIndex];
				boolean inGradient = id != Cosmetics.EMPTY && Cosmetics.isInGradient(id);
				this.renderSlot(ctx, sx, sy, CosmeticColors.hexOf(id), inGradient, 255);

				if (id != Cosmetics.EMPTY && id == this.selected)
				{
					Theme.roundedOutline(ctx, sx - 3, sy - 3, PALETTE_SWATCH + 6, PALETTE_SWATCH + 6,
							Theme.RADIUS_CARD, Theme.SHARD);
				}
			}
		}

		for (int i = slotIndex; i < this.slotRects.length; i++)
		{
			this.slotRects[i].set(0, 0, 0, 0);
		}

		int gridHeight = PALETTE_ROWS * PALETTE_SWATCH + (PALETTE_ROWS - 1) * PALETTE_GAP;

		if (gradient)
		{
			this.renderGradientStrip(ctx, formX + columns * (PALETTE_SWATCH + PALETTE_GAP) + GRADIENT_STRIP_GAP,
					y + (gridHeight - PALETTE_SWATCH) / 2, mouseX, mouseY);
		}
		else
		{
			for (Rect slot : this.gradientRects)
			{
				slot.set(0, 0, 0, 0);
			}
		}

		y += gridHeight + IndexScreen.SETTINGS_ROW_GAP;

		if (this.selected != Cosmetics.EMPTY && CosmeticColors.isRefundable(this.selected))
		{
			// the swatch and the ringed slot answer the same question from both ends of the page
			int hex = CosmeticColors.hexOf(this.selected);
			Theme.roundedRect(ctx, formX, y + 2, 12, 12, Theme.RADIUS_CARD, 0xFF000000 | hex);
			Theme.roundedOutline(ctx, formX, y + 2, 12, 12, Theme.RADIUS_CARD, 0xFFFFFFFF);
			String label = "Refund #" + String.format("%06X", hex);
			int width = this.shardButtonWidth(label, CosmeticColors.refundValue());
			this.refundButton.set(formX + 18, y, width, IndexScreen.FIELD_HEIGHT);
			this.renderShardBuyButton(ctx, this.refundButton, label, CosmeticColors.refundValue(),
					mouseX, mouseY);
			y += IndexScreen.FIELD_HEIGHT + IndexScreen.SETTINGS_ROW_GAP;
		}
		else
		{
			this.refundButton.set(0, 0, 0, 0);
		}

		return y;
	}

	// The three gradient stops, left to right; an emptied one stays half-there so the strip keeps its shape
	private void renderGradientStrip(GuiGraphics ctx, int stripX, int stripY, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		Theme.text(ctx, font, Theme.bold("Gradient"), stripX, stripY - font.lineHeight - 5, Theme.TEXT_ASH);

		for (int i = 0; i < Cosmetics.GRADIENT_SLOTS; i++)
		{
			int sx = stripX + i * (PALETTE_SWATCH + PALETTE_GAP);
			this.gradientRects[i].set(sx, stripY, PALETTE_SWATCH, PALETTE_SWATCH);
			int hex = CosmeticColors.hexOf(Cosmetics.gradientAt(i));
			boolean target = this.gradientDrag >= 0 && this.gradientDrag != i
					&& this.gradientRects[i].contains(mouseX, mouseY);

			// the lifted slot leaves a hollow behind it and is redrawn under the pointer once the page is done
			if (i == this.gradientDrag)
			{
				this.renderSlot(ctx, sx, stripY, -1, false, 128);
				continue;
			}

			this.renderSlot(ctx, sx, stripY, hex, target, hex < 0 ? 128 : 255);
		}
	}

	// Fills the slot-to-colour map the click handler reads back; a gradient slot only points at a colour
	// here, it never takes one out of the palette
	private void layOutPalette(int max)
	{
		Arrays.fill(this.slotIds, Cosmetics.EMPTY);
		int slot = 0;

		for (CosmeticColors.Owned owned : CosmeticColors.colors())
		{
			if (slot >= max)
			{
				break;
			}

			this.slotIds[slot++] = owned.id();
		}
	}

	private void renderCarriedSlot(GuiGraphics ctx)
	{
		if (this.gradientDrag < 0)
		{
			return;
		}

		int hex = CosmeticColors.hexOf(Cosmetics.gradientAt(this.gradientDrag));
		this.renderSlot(ctx, this.dragX - PALETTE_SWATCH / 2, this.dragY - PALETTE_SWATCH / 2,
				hex, true, 220);
	}

	// A white edge one pixel wider says this colour is in the gradient; purple is the refund pick
	private void renderSlot(GuiGraphics ctx, int x, int y, int hex, boolean marked, int alpha)
	{
		int fill = hex < 0 ? Theme.SURFACE_CARD : 0xFF000000 | hex;
		Theme.roundedRect(ctx, x, y, PALETTE_SWATCH, PALETTE_SWATCH, Theme.RADIUS_CARD, withAlpha(fill, alpha));
		Theme.roundedOutline(ctx, x, y, PALETTE_SWATCH, PALETTE_SWATCH, Theme.RADIUS_CARD,
				withAlpha(0xFFFFFFFF, alpha));

		if (!marked)
		{
			return;
		}

		Theme.roundedOutline(ctx, x - 1, y - 1, PALETTE_SWATCH + 2, PALETTE_SWATCH + 2, Theme.RADIUS_CARD,
				withAlpha(0xFFFFFFFF, alpha));
	}

	private static int withAlpha(int argb, int alpha)
	{
		return (argb & 0x00FFFFFF) | (Math.min(255, alpha) << 24);
	}

	// The picker plus its Buy button; the button only appears once a colour has been chosen
	private int renderColorPicker(GuiGraphics ctx, int formX, int y, int formWidth, int mouseX, int mouseY,
			float partialTick)
	{
		Font font = this.screen.font();
		y = this.screen.settingsSectionHeader(ctx, "Buy a colour", formX, y);

		if (CosmeticColors.count() >= Math.min(CosmeticColors.max(), PALETTE_SLOTS))
		{
			this.picker.hide();
			this.buyButton.set(0, 0, 0, 0);
			return this.screen.settingsDescription(ctx, "Your palette is full. Refund a colour to make room.",
					1, formX, y, formWidth);
		}

		int squareSize = 92;
		this.picker.renderSquare(ctx, formX, y, squareSize);
		Fields.single(ctx, this.hexBox, formX + squareSize + 12, y, 74, IndexScreen.FIELD_HEIGHT,
				mouseX, mouseY, partialTick);

		int sideX = formX + squareSize + 12;
		int swatchY = y + IndexScreen.FIELD_HEIGHT + 8;
		this.renderSlot(ctx, sideX, swatchY, this.pickColor, false, 255);

		if (this.picked)
		{
			int price = CosmeticColors.nextPrice();
			int buttonY = swatchY + PALETTE_SWATCH + 8;

			if (price > 0)
			{
				this.buyButton.set(sideX, buttonY, this.shardButtonWidth("Buy", price), IndexScreen.FIELD_HEIGHT);
				this.renderShardBuyButton(ctx, this.buyButton, "Buy", price, mouseX, mouseY);
			}
			else
			{
				this.buyButton.set(sideX, buttonY, font.width(Theme.bold("Free")) + 24, IndexScreen.FIELD_HEIGHT);
				Buttons.pill(ctx, font, this.buyButton, "Free", mouseX, mouseY, true);
			}
		}
		else
		{
			this.buyButton.set(0, 0, 0, 0);
		}

		y += squareSize + 6;

		int hueHeight = 10;
		this.picker.renderHueBar(ctx, formX, y, squareSize, hueHeight);

		return y + hueHeight + IndexScreen.SETTINGS_ROW_GAP;
	}

	// One card per effect; hovering or selecting it plays the effect on the preview, so it is seen before it is bought
	// Two columns once a card can hold its label beside its button, otherwise a single column the page scrolls
	private int renderEffects(GuiGraphics ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		y = this.screen.settingsSectionHeader(ctx, "Effects", formX, y);
		y = this.screen.settingsDescription(ctx, "Effects play over whatever colours you wear, one at a time.", 1,
				formX, y, formWidth);

		this.shop = shopEffects();

		if (this.effectCards.length != this.shop.size())
		{
			this.effectCards = newRects(this.shop.size());
			this.effectButtons = newRects(this.shop.size());
		}

		int columns = formWidth >= this.effectCardMinWidth() * 2 + EFFECT_CARD_GAP ? 2 : 1;
		int cardWidth = (formWidth - (columns - 1) * EFFECT_CARD_GAP) / columns;
		int rows = (this.shop.size() + columns - 1) / columns;

		for (int i = 0; i < this.shop.size(); i++)
		{
			int cx = formX + i % columns * (cardWidth + EFFECT_CARD_GAP);
			int cy = y + i / columns * (EFFECT_CARD_HEIGHT + EFFECT_CARD_GAP);
			this.renderEffectCard(ctx, i, cx, cy, cardWidth, mouseX, mouseY);
		}

		return y + rows * (EFFECT_CARD_HEIGHT + EFFECT_CARD_GAP) - EFFECT_CARD_GAP + IndexScreen.SETTINGS_ROW_GAP;
	}

	private int effectCardMinWidth()
	{
		Font font = this.screen.font();
		int text = 0;
		int button = 0;

		for (Effect effect : this.shop)
		{
			text = Math.max(text, font.width(Theme.bold(effect.label())));
			text = Math.max(text, font.width(effect.blurb()));
			button = Math.max(button, this.shardButtonWidth("Buy", CosmeticColors.effectPrice(effect.id())));
		}

		return 28 + text + 6 + button + 6;
	}

	private void renderEffectCard(GuiGraphics ctx, int index, int x, int y, int width, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		Effect effect = this.shop.get(index);
		String id = effect.id();
		boolean worn = Cosmetics.effects().contains(id);
		Rect card = this.effectCards[index];
		Rect button = this.effectButtons[index];
		card.set(x, y, width, EFFECT_CARD_HEIGHT);
		boolean hovered = card.contains(mouseX, mouseY);
		boolean selected = index == this.previewEffect;
		Theme.roundedRect(ctx, x, y, width, EFFECT_CARD_HEIGHT, Theme.RADIUS_CARD,
				Theme.lighten(Theme.SURFACE_CARD, hovered ? 0.06F : 0.0F));
		Theme.roundedOutline(ctx, x, y, width, EFFECT_CARD_HEIGHT, Theme.RADIUS_CARD,
				worn || selected ? Theme.ACCENT_BRIGHT : hovered ? Theme.SHARD : Theme.HAIRLINE);

		int buttonY = y + (EFFECT_CARD_HEIGHT - IndexScreen.FIELD_HEIGHT) / 2;
		boolean owns = CosmeticColors.ownsEffect(id);
		String buyLabel = owns ? (worn ? "Remove" : "Wear") : "Buy";
		int price = owns ? 0 : CosmeticColors.effectPrice(id);
		int buttonWidth = owns ? font.width(Theme.bold(buyLabel)) + 24 : this.shardButtonWidth(buyLabel, price);
		button.set(x + width - 6 - buttonWidth, buttonY, buttonWidth, IndexScreen.FIELD_HEIGHT);

		int titleY = y + (EFFECT_CARD_HEIGHT - font.lineHeight * 2 - 2) / 2 + 1;
		int textX = x + 28;
		int textWidth = Math.max(0, button.x - textX - 6);
		Identifier icon = this.effectIcon(id);
		int iconY = y + (EFFECT_CARD_HEIGHT - 16) / 2;

		if (icon != null)
		{
			Theme.image(ctx, icon, x + 6, iconY, 16, 16, 16, 16);
		}
		else
		{
			Theme.itemScaled(ctx, new ItemStack(iconOf(effect)), x + 6, iconY, 1.0F);
		}

		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, effect.label(), textWidth)), textX, titleY, Theme.TEXT);
		Theme.text(ctx, font, Theme.clip(font, effect.blurb(), textWidth), textX, titleY + font.lineHeight + 2,
				Theme.TEXT_ASH);

		if (owns)
		{
			Buttons.pill(ctx, font, button, buyLabel, mouseX, mouseY, !worn);
			return;
		}

		this.renderShardBuyButton(ctx, button, buyLabel, price, mouseX, mouseY);
	}

	// A served effect is on sale once the server prices it; one already owned stays listed either way
	private static List<Effect> shopEffects()
	{
		List<Effect> out = new ArrayList<>();

		for (Effect effect : Effects.all())
		{
			if (CosmeticColors.effectPrice(effect.id()) > 0 || CosmeticColors.ownsEffect(effect.id()))
			{
				out.add(effect);
			}
		}

		return out;
	}

	private @Nullable Identifier effectIcon(String id)
	{
		if (this.effectIcons.containsKey(id))
		{
			return this.effectIcons.get(id);
		}

		Identifier texture = Identifier.tryBuild(SchematicIndexMod.MOD_ID, "textures/gui/effect/" + id + ".png");

		if (texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty())
		{
			texture = null;
		}

		if (texture == null)
		{
			SchematicIndexMod.LOGGER.debug("No bundled icon for effect {}, showing its item", id);
		}

		this.effectIcons.put(id, texture);
		return texture;
	}

	private static Item iconOf(Effect effect)
	{
		Identifier key = Identifier.tryParse(effect.icon());
		return key == null ? Items.NAME_TAG : BuiltInRegistries.ITEM.getValue(key);
	}

	private int hoveredEffect(int mouseX, int mouseY)
	{
		for (int i = 0; i < this.effectCards.length; i++)
		{
			if (this.effectCards[i].contains(mouseX, mouseY))
			{
				return i;
			}
		}

		return -1;
	}

	// The hovered card wins, then the selected one, then whatever is worn
	private String previewedEffect()
	{
		if (this.effectHover >= 0 && this.effectHover < this.shop.size())
		{
			return this.shop.get(this.effectHover).id();
		}

		return this.previewEffect >= 0 && this.previewEffect < this.shop.size() ? this.shop.get(this.previewEffect).id()
				: Cosmetics.effect();
	}

	int shardButtonWidth(String label, int price)
	{
		Font font = this.screen.font();
		return 8 + font.width(Theme.bold(label)) + 6 + 13 + font.width(Theme.bold(Integer.toString(price))) + 8;
	}

	// A buy button that spells out the shard cost the way the rest of the mod does: amethyst icon plus purple price
	void renderShardBuyButton(GuiGraphics ctx, Rect rect, String label, int price, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		boolean hovered = rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				Theme.lighten(Theme.SURFACE_CARD, 0.12F * hover));
		Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				hovered ? Theme.SHARD : Theme.HAIRLINE);

		int textY = rect.y + (rect.height - font.lineHeight) / 2 + 1;
		int tx = rect.x + 8;
		String bold = Theme.bold(label);
		Theme.text(ctx, font, bold, tx, textY, Theme.TEXT);
		tx += font.width(bold) + 6;
		Theme.itemScaled(ctx, new ItemStack(Items.AMETHYST_SHARD), tx, rect.y + (rect.height - 10) / 2, 0.6F);
		tx += 13;
		Theme.text(ctx, font, Theme.bold(Integer.toString(price)), tx, textY, Theme.SHARD);

		Theme.pop(ctx);
	}

	// A palette colour is always selected for refund; in gradient mode it also joins the gradient
	private boolean clickPaletteSlot(int id)
	{
		this.selected = id;
		this.presets.clearPreview();

		Cosmetics.pushGradient(id);

		if (Cosmetics.mode() != Cosmetics.Mode.GRADIENT)
		{
			Cosmetics.setMode(Cosmetics.Mode.GRADIENT);
		}

		this.screen.setFocused(null);
		Theme.click();
		return true;
	}

	private boolean clickEffect(int index)
	{
		Effect effect = this.shop.get(index);
		String id = effect.id();

		if (CosmeticColors.ownsEffect(id))
		{
			Cosmetics.setEffect(id, !Cosmetics.effects().contains(id));
			this.afterChange();
			return true;
		}

		CosmeticColors.buyEffect(id, effect.label(), CosmeticColors.effectPrice(id), () ->
		{
			Cosmetics.setEffect(id, true);
			this.rebuild();
		});
		return true;
	}

	// A freshly bought colour goes straight on, so buying one visibly does something
	private void wearNewest()
	{
		if (CosmeticColors.colors().isEmpty())
		{
			return;
		}

		int id = CosmeticColors.colors().get(CosmeticColors.count() - 1).id();
		this.selected = id;

		// a gradient the player arranged is theirs to change, so only an empty loadout takes the new colour
		if (Cosmetics.mode() != Cosmetics.Mode.NONE)
		{
			return;
		}

		Cosmetics.pushGradient(id);
		Cosmetics.setMode(Cosmetics.Mode.GRADIENT);
	}

	// A release on the slot that was picked up is a click, which empties it; anywhere else reorders the strip
	private boolean releaseGradientDrag(double mouseX, double mouseY)
	{
		int from = this.gradientDrag;
		this.gradientDrag = -1;

		for (int i = 0; i < this.gradientRects.length; i++)
		{
			if (this.gradientRects[i].contains(mouseX, mouseY))
			{
				if (i == from)
				{
					Cosmetics.clearGradientSlot(from);
				}
				else
				{
					Cosmetics.swapGradient(from, i);
				}

				Theme.click();
				return true;
			}
		}

		return true;
	}

	private void afterPickerDrag()
	{
		this.pickColor = this.picker.rgb();
		this.picked = true;
		this.refreshHexBox();
	}

	private boolean switchMode(Cosmetics.Mode mode)
	{
		Cosmetics.setMode(mode);
		this.afterChange();
		return true;
	}

	// After any change to the palette or loadout, rebuild the hex field and re-seed the picker
	void afterChange()
	{
		this.rebuild();
		Theme.click();
	}

	// The silent half, for callbacks that land after the click or beacon their action already played
	void rebuild()
	{
		this.screen.setFocused(null);
		this.buildFields();
	}
}

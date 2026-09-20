package com.fudgedy.schematicindex.gui.mapart;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ColorPicker;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.TextInput;
import com.fudgedy.schematicindex.mapart.MapCorners;
import com.fudgedy.schematicindex.mapart.MapartImage;
import com.fudgedy.schematicindex.mapart.MapartOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The settings column: every conversion option, image first and output last, plus the map corner overlay,
// on one rhythm of FIELD_HEIGHT rows separated by ROW_GAP
public final class MapartSettingsPanel
{
	// One rhythm for the column, the same class of gap the Settings page keeps between its rows
	private static final int ROW_GAP = 10;
	private static final int LABEL_HEIGHT = 13;
	private static final int HINT_HEIGHT = 11;
	private static final int CARD_PAD = 8;
	// Label line plus the shared slider track, as Controls.settingSlider lays them out
	private static final int SLIDER_BLOCK = 9 + 7 + 14;
	private static final int PICKER_SQUARE = 92;
	private static final int HUE_BAR = 10;
	private static final int POPUP_PAD = 10;

	private static final int DRAG_NONE = 0;
	private static final int DRAG_CROP_X = 1;
	private static final int DRAG_CROP_Y = 2;
	private static final int DRAG_BRIGHTNESS = 3;
	private static final int DRAG_CONTRAST = 4;
	private static final int DRAG_SATURATION = 5;
	private static final int DRAG_CORNER = 6;
	private static final int DRAG_PICKER = 7;
	private static final int KEY_ESCAPE = 256;

	private static final String[] STAIRCASE_HINTS = {
			"One shade per colour. Flat and easy to build.",
			"Three shades per colour by stepping up and down.",
			"Same shades as Classic, steps back down whenever it can so the build stays shorter.",
			"Only the darkest shade of each colour; the map is one descending slope.",
			"Only the brightest shade; one ascending slope.",
			"Includes a shade only water can make. Cannot be built, for previewing only."};

	private Font font;

	public final Rect bounds = new Rect();
	private float scroll;
	private float maxScroll;
	private int dragging = DRAG_NONE;

	private final Rect chooseImage = new Rect();
	private final Rect pasteImage = new Rect();
	private final Rect widthMinus = new Rect();
	private final Rect widthPlus = new Rect();
	private final Rect heightMinus = new Rect();
	private final Rect heightPlus = new Rect();
	private final Rect cropRect = new Rect();
	private final Rect cropXTrack = new Rect();
	private final Rect cropYTrack = new Rect();
	private final Rect gridToggle = new Rect();
	private final Rect staircaseRect = new Rect();
	private final Rect supportRect = new Rect();
	private final Rect betterColorToggle = new Rect();
	private final Rect ditherRect = new Rect();
	private final Rect adjustToggle = new Rect();
	private final Rect brightnessTrack = new Rect();
	private final Rect contrastTrack = new Rect();
	private final Rect saturationTrack = new Rect();
	private final Rect backgroundRect = new Rect();
	private final Rect anchorToggle = new Rect();
	private final Rect colourSwatch = new Rect();
	private final Rect popup = new Rect();
	private boolean popupOpen;
	private final Rect cornersToggle = new Rect();
	private final Rect cornerTrack = new Rect();
	private final Rect useMyY = new Rect();

	public final Dropdown cropDropdown = new Dropdown();
	public final Dropdown staircaseDropdown = new Dropdown();
	public final Dropdown supportDropdown = new Dropdown();
	public final Dropdown ditherDropdown = new Dropdown();
	public final Dropdown backgroundDropdown = new Dropdown();
	private final ColorPicker colourPicker = new ColorPicker();
	private final TextInput hexInput = new TextInput(7, "#0123456789abcdefABCDEF");
	private final Rect hexRect = new Rect();

	public MapartSettingsPanel()
	{
		this.colourPicker.seed(MapartOptions.DEFAULT_BACKGROUND);
	}

	public Dropdown[] dropdowns()
	{
		return new Dropdown[]{this.cropDropdown, this.staircaseDropdown, this.supportDropdown, this.ditherDropdown,
				this.backgroundDropdown};
	}

	public void render(GuiGraphics ctx, Font font, int x, int top, int width, int bottom, int mouseX, int mouseY)
	{
		this.font = font;
		this.bounds.set(x, top, width, bottom - top);
		ctx.enableScissor(x, top, x + width, bottom);

		MapartOptions options = MapartSession.options;
		int startY = top - Math.round(this.scroll);
		int y = this.renderHelperCard(ctx, x, startY, width, mouseX, mouseY);

		int pasteWidth = font.width(Theme.bold("Paste")) + 16;
		this.chooseImage.set(x, y, width - pasteWidth - ROW_GAP, IndexScreen.FIELD_HEIGHT);
		this.pasteImage.set(x + width - pasteWidth, y, pasteWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.chooseImage, "Choose Image", mouseX, mouseY, true);
		Buttons.pill(ctx, font, this.pasteImage, "Paste", mouseX, mouseY, false);
		y += IndexScreen.FIELD_HEIGHT + 2;
		String picked = MapartSession.source == null ? "No image chosen"
				: MapartSession.sourceName + "  " + MapartSession.source.width + "x" + MapartSession.source.height;
		Theme.text(ctx, font, Theme.clip(font, picked, width), x, y, Theme.TEXT_ASH);
		y += HINT_HEIGHT + ROW_GAP;

		y = this.renderMapSize(ctx, x, y, width, mouseX, mouseY);

		y = this.label(ctx, "Crop", x, y);
		this.cropRect.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		this.cropDropdown.render(ctx, font, this.cropRect, MapartOptions.CROP_LABELS, options.crop, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + ROW_GAP;

		if (options.crop == MapartOptions.CROP_MANUAL)
		{
			y = this.slider(ctx, this.cropXTrack, "X offset", options.cropX + "%", options.cropX / 100.0F, DRAG_CROP_X, x, y, width, mouseX, mouseY);
			y = this.slider(ctx, this.cropYTrack, "Y offset", options.cropY + "%", options.cropY / 100.0F, DRAG_CROP_Y, x, y, width, mouseX, mouseY);
		}

		this.gridToggle.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, font, this.gridToggle, "Grid (16x16 chunks, 1x1 maps)", MapartSession.grid, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + ROW_GAP;

		y = this.label(ctx, "Staircasing", x, y);
		this.staircaseRect.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		this.staircaseDropdown.render(ctx, font, this.staircaseRect, MapartOptions.STAIRCASE_LABELS, options.staircase, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + 2;
		y = this.wrappedHint(ctx, STAIRCASE_HINTS[Math.max(0, Math.min(STAIRCASE_HINTS.length - 1, options.staircase))], x, y, width);

		y = this.label(ctx, "Add blocks under", x, y);
		this.supportRect.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		this.supportDropdown.render(ctx, font, this.supportRect, MapartOptions.SUPPORT_LABELS, options.support, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + ROW_GAP;

		this.betterColorToggle.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, font, this.betterColorToggle, "Better color", options.betterColor, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + ROW_GAP;

		y = this.label(ctx, "Dithering", x, y);
		this.ditherRect.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		this.ditherDropdown.render(ctx, font, this.ditherRect, MapartOptions.DITHER_LABELS, options.dithering, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + ROW_GAP;

		y = this.label(ctx, "Colour adjustments", x, y);
		this.adjustToggle.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, font, this.adjustToggle, "Enable", options.preprocess, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + ROW_GAP;

		if (options.preprocess)
		{
			y = this.slider(ctx, this.brightnessTrack, "Brightness", Integer.toString(options.brightness), options.brightness / 200.0F, DRAG_BRIGHTNESS, x, y, width, mouseX, mouseY);
			y = this.slider(ctx, this.contrastTrack, "Contrast", Integer.toString(options.contrast), options.contrast / 200.0F, DRAG_CONTRAST, x, y, width, mouseX, mouseY);
			y = this.slider(ctx, this.saturationTrack, "Saturation", Integer.toString(options.saturation), options.saturation / 200.0F, DRAG_SATURATION, x, y, width, mouseX, mouseY);
		}

		y = this.label(ctx, "Background", x, y);
		this.backgroundRect.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		this.backgroundDropdown.render(ctx, font, this.backgroundRect, MapartOptions.BACKGROUND_LABELS, options.background, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + 2;
		y = this.wrappedHint(ctx, options.background == MapartOptions.BACKGROUND_OFF
				? "Off: transparent pixels become air (no block)" : "Transparent pixels take the colour below", x, y, width);
		y = this.renderColourSwatch(ctx, x, y, options, mouseX, mouseY);

		this.anchorToggle.set(x, y, width, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, font, this.anchorToggle, "Shaded Top Row", options.noobline, mouseX, mouseY);
		y += IndexScreen.FIELD_HEIGHT + 2;
		y = this.wrappedHint(ctx, "Adds a cobblestone row north of the map so the top row shades correctly.", x, y, width);

		ctx.disableScissor();
		this.maxScroll = Math.max(0.0F, y - ROW_GAP - startY - (bottom - top));
		this.scroll = Math.min(this.scroll, this.maxScroll);
		MapartUi.scrollbar(ctx, x + width + 3, top, bottom - top, this.scroll, this.maxScroll);
	}

	// The map corner overlay leads the column as its own card, since it is used while building, not converting
	private int renderHelperCard(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY)
	{
		Minecraft mc = Minecraft.getInstance();
		int minY = MapCorners.minHeight(mc.level);
		int maxY = MapCorners.maxHeight(mc.level);
		int corner = MapCorners.clampedHeight(mc.level);
		List<String> hint = wrap(this.font, "Marks the four corners of the 128x128 map you are standing in.", width - CARD_PAD * 2);
		int height = CARD_PAD + LABEL_HEIGHT + IndexScreen.FIELD_HEIGHT + 2 + hint.size() * HINT_HEIGHT + ROW_GAP
				+ SLIDER_BLOCK + CARD_PAD;
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.HAIRLINE);

		int innerX = x + CARD_PAD;
		int innerWidth = width - CARD_PAD * 2;
		int cy = this.label(ctx, Theme.bold("Mapart Placement Helper"), innerX, y + CARD_PAD);
		this.cornersToggle.set(innerX, cy, innerWidth, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, this.font, this.cornersToggle, "Show Map Corners", Settings.mapCorners(), mouseX, mouseY);
		cy += IndexScreen.FIELD_HEIGHT + 2;

		for (String row : hint)
		{
			Theme.text(ctx, this.font, row, innerX, cy, Theme.TEXT_ASH);
			cy += HINT_HEIGHT;
		}

		cy += ROW_GAP;
		int pillWidth = this.font.width(Theme.bold("Use my Y")) + 14;
		int trackWidth = innerWidth - pillWidth - 8;
		this.useMyY.set(innerX + innerWidth - pillWidth, cy + SLIDER_BLOCK - IndexScreen.FIELD_HEIGHT - 2, pillWidth, IndexScreen.FIELD_HEIGHT);
		Controls.settingSlider(ctx, this.font, this.cornerTrack, "Corner height", "Y " + corner,
				(corner - minY) / (float) (maxY - minY), this.dragging == DRAG_CORNER, innerX, cy, trackWidth, mouseX, mouseY);

		if (mc.player != null)
		{
			Buttons.pill(ctx, this.font, this.useMyY, "Use my Y", mouseX, mouseY, false);
		}
		else
		{
			Buttons.disabled(ctx, this.font, this.useMyY, "Use my Y");
		}

		return y + height + ROW_GAP;
	}

	private int renderMapSize(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY)
	{
		MapartOptions options = MapartSession.options;
		int baseline = y + (IndexScreen.FIELD_HEIGHT - this.font.lineHeight) / 2 + 1;
		Theme.text(ctx, this.font, "Map size", x, baseline, Theme.TEXT);
		int cursor = x + this.font.width("Map size") + 8;
		cursor = Buttons.stepper(ctx, this.font, this.widthMinus, this.widthPlus, Integer.toString(options.mapsX), cursor, y, mouseX, mouseY);
		Theme.text(ctx, this.font, "\u00d7", cursor + 4, baseline, Theme.TEXT_MUTE);
		cursor += 4 + this.font.width("\u00d7") + 4;
		Buttons.stepper(ctx, this.font, this.heightMinus, this.heightPlus, Integer.toString(options.mapsY), cursor, y, mouseX, mouseY);

		String pixels = (options.mapsX * MapartImage.MAP_SIZE) + "\u00d7" + (options.mapsY * MapartImage.MAP_SIZE);
		Theme.text(ctx, this.font, pixels, x + width - this.font.width(pixels), baseline, Theme.TEXT_ASH);
		return y + IndexScreen.FIELD_HEIGHT + ROW_GAP;
	}

	// A swatch the size of a field opens the full picker as a popup; only the readout lives in the column
	private int renderColourSwatch(GuiGraphics ctx, int x, int y, MapartOptions options, int mouseX, int mouseY)
	{
		Theme.text(ctx, this.font, "Colour", x, y + 4, Theme.TEXT);
		int swatchX = x + this.font.width("Colour") + 8;
		this.colourSwatch.set(swatchX, y, IndexScreen.FIELD_HEIGHT, IndexScreen.FIELD_HEIGHT);
		ctx.fill(swatchX, y, swatchX + IndexScreen.FIELD_HEIGHT, y + IndexScreen.FIELD_HEIGHT, 0xFF000000 | options.backgroundColor);
		boolean hovered = this.popupOpen || this.colourSwatch.contains(mouseX, mouseY);
		Theme.roundedOutline(ctx, swatchX, y, IndexScreen.FIELD_HEIGHT, IndexScreen.FIELD_HEIGHT, Theme.RADIUS_PILL,
				hovered ? Theme.ACCENT_BRIGHT : Theme.HAIRLINE);
		this.renderHex(ctx, swatchX + IndexScreen.FIELD_HEIGHT + 6, y, mouseX, mouseY);
		return y + IndexScreen.FIELD_HEIGHT + ROW_GAP;
	}

	// The readout doubles as a field: while it has focus it shows what is typed, otherwise the live colour
	private void renderHex(GuiGraphics ctx, int x, int y, int mouseX, int mouseY)
	{
		if (!this.hexInput.isFocused())
		{
			this.hexInput.set(hex(MapartSession.options.backgroundColor));
		}

		this.hexRect.set(x, y, this.font.width("#FFFFFF") + 12, IndexScreen.FIELD_HEIGHT);
		this.hexInput.render(ctx, this.font, this.hexRect, "#RRGGBB", mouseX, mouseY);
	}

	// A full six-digit value is applied as soon as it is typed, so the swatch and square follow the keys
	private void applyTypedHex()
	{
		String typed = this.hexInput.value().trim();

		if (typed.startsWith("#"))
		{
			typed = typed.substring(1);
		}

		if (typed.length() != 6)
		{
			return;
		}

		int rgb;

		try
		{
			rgb = Integer.parseInt(typed, 16);
		}
		catch (NumberFormatException e)
		{
			return;
		}

		this.colourPicker.seed(rgb);
		this.applyPickedColour();
	}

	// Drawn by the tab after every scissor is gone; sits right of the swatch, pulled back inside the page
	public void renderPopup(GuiGraphics ctx, Font font, int right, int bottom, int mouseX, int mouseY)
	{
		if (!this.popupOpen)
		{
			return;
		}

		int width = PICKER_SQUARE + POPUP_PAD * 2;
		int height = POPUP_PAD + PICKER_SQUARE + ROW_GAP + HUE_BAR + ROW_GAP + IndexScreen.FIELD_HEIGHT + POPUP_PAD;
		int x = Math.min(this.colourSwatch.x + this.colourSwatch.width + 6, right - width);
		int y = Math.max(this.bounds.y, Math.min(this.colourSwatch.y - POPUP_PAD, bottom - height));
		this.popup.set(x, y, width, height);

		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_MODAL, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_MODAL, Theme.HAIRLINE);
		int squareY = y + POPUP_PAD;
		this.colourPicker.renderSquare(ctx, x + POPUP_PAD, squareY, PICKER_SQUARE);
		this.colourPicker.renderHueBar(ctx, x + POPUP_PAD, squareY + PICKER_SQUARE + ROW_GAP, PICKER_SQUARE, HUE_BAR);
		this.renderHex(ctx, x + POPUP_PAD, squareY + PICKER_SQUARE + ROW_GAP + HUE_BAR + ROW_GAP, mouseX, mouseY);
	}

	// While the popup is open every click belongs to it: inside starts a drag, outside closes it
	public boolean popupClicked(double mouseX, double mouseY)
	{
		if (!this.popupOpen)
		{
			return false;
		}

		if (!this.popup.contains(mouseX, mouseY))
		{
			this.closePopup();
			Theme.click(0.9F);
			return true;
		}

		if (this.clickHex(mouseX, mouseY))
		{
			return true;
		}

		if (this.colourPicker.mouseClicked(mouseX, mouseY))
		{
			this.dragging = DRAG_PICKER;
			this.applyPickedColour();
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (this.hexInput.keyPressed(event))
		{
			this.applyTypedHex();
			return true;
		}

		if (!this.popupOpen || event.key() != KEY_ESCAPE)
		{
			return false;
		}

		this.closePopup();
		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		if (!this.hexInput.charTyped(event))
		{
			return false;
		}

		this.applyTypedHex();
		return true;
	}

	// Focus moves into the field on a hit; any other click drops it and applies whatever was typed
	private boolean clickHex(double mouseX, double mouseY)
	{
		boolean inside = this.hexInput.click(mouseX, mouseY);

		if (this.hexInput.takeCommitted())
		{
			this.applyTypedHex();
		}

		return inside;
	}

	private void closePopup()
	{
		this.hexInput.blur();
		this.hexInput.takeCommitted();
		this.applyTypedHex();
		this.popupOpen = false;
		this.colourPicker.hide();
		this.popup.set(0, 0, 0, 0);
	}

	private int label(GuiGraphics ctx, String text, int x, int y)
	{
		Theme.text(ctx, this.font, text, x, y, Theme.TEXT);
		return y + LABEL_HEIGHT;
	}

	private int wrappedHint(GuiGraphics ctx, String text, int x, int y, int width)
	{
		for (String row : wrap(this.font, text, width))
		{
			Theme.text(ctx, this.font, row, x, y, Theme.TEXT_ASH);
			y += HINT_HEIGHT;
		}

		return y + ROW_GAP;
	}

	private int slider(GuiGraphics ctx, Rect track, String label, String value, float fraction, int id, int x, int y,
			int width, int mouseX, int mouseY)
	{
		return Controls.settingSlider(ctx, this.font, track, label, value, fraction, this.dragging == id, x, y, width, mouseX, mouseY)
				+ ROW_GAP;
	}

	public static List<String> wrap(Font font, String text, int width)
	{
		List<String> rows = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (String word : text.split(" "))
		{
			String candidate = current.length() == 0 ? word : current + " " + word;

			if (font.width(candidate) > width && current.length() > 0)
			{
				rows.add(current.toString());
				current.setLength(0);
				current.append(word);
				continue;
			}

			current.setLength(0);
			current.append(candidate);
		}

		if (current.length() > 0)
		{
			rows.add(current.toString());
		}

		return rows;
	}

	static String hex(int rgb)
	{
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.bounds.contains(mouseX, mouseY))
		{
			return false;
		}

		MapartOptions options = MapartSession.options;

		if (this.chooseImage.contains(mouseX, mouseY))
		{
			MapartUi.press(this.chooseImage);
			MapartSession.openImagePicker();
			return true;
		}

		if (this.pasteImage.contains(mouseX, mouseY))
		{
			MapartUi.press(this.pasteImage);
			MapartSession.pasteFromClipboard();
			return true;
		}

		if (this.clickSize(mouseX, mouseY))
		{
			return true;
		}

		if (this.gridToggle.contains(mouseX, mouseY))
		{
			MapartUi.press(this.gridToggle);
			MapartSession.grid = !MapartSession.grid;
			return true;
		}

		if (this.betterColorToggle.contains(mouseX, mouseY))
		{
			MapartUi.press(this.betterColorToggle);
			MapartOptions next = options.copy();
			next.betterColor = !next.betterColor;
			MapartSession.update(next);
			return true;
		}

		if (this.adjustToggle.contains(mouseX, mouseY))
		{
			MapartUi.press(this.adjustToggle);
			MapartOptions next = options.copy();
			next.preprocess = !next.preprocess;
			MapartSession.update(next);
			return true;
		}

		if (this.anchorToggle.contains(mouseX, mouseY))
		{
			MapartUi.press(this.anchorToggle);
			MapartOptions next = options.copy();
			next.noobline = !next.noobline;
			MapartSession.update(next);
			return true;
		}

		if (this.cornersToggle.contains(mouseX, mouseY))
		{
			MapartUi.press(this.cornersToggle);
			MapCorners.enable(!Settings.mapCorners(), Minecraft.getInstance());
			return true;
		}

		if (this.useMyY.contains(mouseX, mouseY) && MapCorners.useCurrentY(Minecraft.getInstance()))
		{
			MapartUi.press(this.useMyY);
			return true;
		}

		if (this.colourSwatch.contains(mouseX, mouseY))
		{
			MapartUi.press(this.colourSwatch);
			this.colourPicker.seed(options.backgroundColor);
			this.popupOpen = true;
			return true;
		}

		if (this.clickHex(mouseX, mouseY))
		{
			return true;
		}

		int slider = this.sliderAt(mouseX, mouseY);

		if (slider != DRAG_NONE)
		{
			this.dragging = slider;
			this.dragTo(mouseX);
			return true;
		}

		return true;
	}

	private boolean clickSize(double mouseX, double mouseY)
	{
		boolean hit = this.widthMinus.contains(mouseX, mouseY) || this.widthPlus.contains(mouseX, mouseY)
				|| this.heightMinus.contains(mouseX, mouseY) || this.heightPlus.contains(mouseX, mouseY);

		if (!hit)
		{
			return false;
		}

		MapartOptions options = MapartSession.options;
		int mapsX = options.mapsX + (this.widthPlus.contains(mouseX, mouseY) ? 1 : 0) - (this.widthMinus.contains(mouseX, mouseY) ? 1 : 0);
		int mapsY = options.mapsY + (this.heightPlus.contains(mouseX, mouseY) ? 1 : 0) - (this.heightMinus.contains(mouseX, mouseY) ? 1 : 0);
		mapsX = Math.max(MapartOptions.MIN_MAPS, Math.min(MapartOptions.MAX_MAPS, mapsX));
		mapsY = Math.max(MapartOptions.MIN_MAPS, Math.min(MapartOptions.MAX_MAPS, mapsY));

		if (mapsX == options.mapsX && mapsY == options.mapsY)
		{
			return true;
		}

		Theme.click();
		MapartOptions next = options.copy();
		next.mapsX = mapsX;
		next.mapsY = mapsY;
		MapartSession.update(next);
		return true;
	}

	// Dropdown choices arrive from the tab's overlay pass, which owns the open list
	public void choose(Dropdown dropdown, int index)
	{
		MapartOptions next = MapartSession.options.copy();

		if (dropdown == this.cropDropdown)
		{
			next.crop = index;
		}
		else if (dropdown == this.staircaseDropdown)
		{
			next.staircase = index;
		}
		else if (dropdown == this.supportDropdown)
		{
			next.support = index;
		}
		else if (dropdown == this.ditherDropdown)
		{
			next.dithering = index;
		}
		else if (dropdown == this.backgroundDropdown)
		{
			next.background = index;
		}

		MapartSession.update(next);
	}

	private void applyPickedColour()
	{
		MapartOptions options = MapartSession.options;

		if (this.colourPicker.rgb() == options.backgroundColor)
		{
			return;
		}

		MapartOptions next = options.copy();
		next.backgroundColor = this.colourPicker.rgb();
		MapartSession.update(next);
	}

	private int sliderAt(double mouseX, double mouseY)
	{
		MapartOptions options = MapartSession.options;

		if (options.crop == MapartOptions.CROP_MANUAL && this.cropXTrack.contains(mouseX, mouseY))
		{
			return DRAG_CROP_X;
		}

		if (options.crop == MapartOptions.CROP_MANUAL && this.cropYTrack.contains(mouseX, mouseY))
		{
			return DRAG_CROP_Y;
		}

		if (options.preprocess && this.brightnessTrack.contains(mouseX, mouseY))
		{
			return DRAG_BRIGHTNESS;
		}

		if (options.preprocess && this.contrastTrack.contains(mouseX, mouseY))
		{
			return DRAG_CONTRAST;
		}

		if (options.preprocess && this.saturationTrack.contains(mouseX, mouseY))
		{
			return DRAG_SATURATION;
		}

		if (this.cornerTrack.contains(mouseX, mouseY))
		{
			return DRAG_CORNER;
		}

		return DRAG_NONE;
	}

	private void dragTo(double mouseX)
	{
		Rect track = switch (this.dragging)
		{
			case DRAG_CROP_X -> this.cropXTrack;
			case DRAG_CROP_Y -> this.cropYTrack;
			case DRAG_BRIGHTNESS -> this.brightnessTrack;
			case DRAG_CONTRAST -> this.contrastTrack;
			case DRAG_SATURATION -> this.saturationTrack;
			case DRAG_CORNER -> this.cornerTrack;
			default -> null;
		};

		if (track == null || track.width <= 0)
		{
			return;
		}

		float fraction = (float) Math.max(0.0, Math.min(1.0, (mouseX - track.x) / track.width));

		if (this.dragging == DRAG_CORNER)
		{
			int minY = MapCorners.minHeight(Minecraft.getInstance().level);
			int maxY = MapCorners.maxHeight(Minecraft.getInstance().level);
			Settings.setCornerHeight(minY + Math.round(fraction * (maxY - minY)));
			return;
		}

		int percent = Math.round(fraction * 100.0F);
		int adjust = Math.round(fraction * MapartOptions.ADJUST_MAX);
		MapartOptions options = MapartSession.options;
		MapartOptions next = options.copy();

		switch (this.dragging)
		{
			case DRAG_CROP_X -> next.cropX = percent;
			case DRAG_CROP_Y -> next.cropY = percent;
			case DRAG_BRIGHTNESS -> next.brightness = adjust;
			case DRAG_CONTRAST -> next.contrast = adjust;
			case DRAG_SATURATION -> next.saturation = adjust;
			default -> {
			}
		}

		if (next.cropX != options.cropX || next.cropY != options.cropY || next.brightness != options.brightness
				|| next.contrast != options.contrast || next.saturation != options.saturation)
		{
			MapartSession.update(next);
		}
	}

	public boolean mouseDragged(double mouseX, double mouseY)
	{
		if (this.dragging == DRAG_PICKER)
		{
			this.colourPicker.mouseDragged(mouseX, mouseY);
			this.applyPickedColour();
			return true;
		}

		if (this.dragging == DRAG_NONE)
		{
			return false;
		}

		this.dragTo(mouseX);
		return true;
	}

	public boolean mouseReleased()
	{
		if (this.dragging == DRAG_NONE)
		{
			return false;
		}

		this.colourPicker.mouseReleased();
		this.dragging = DRAG_NONE;
		Theme.click(1.0F);
		return true;
	}

	public boolean mouseScrolled(double scrollY)
	{
		this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}
}

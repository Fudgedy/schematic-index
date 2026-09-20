package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.gui.mapart.MapartBlockPicker;
import com.fudgedy.schematicindex.gui.mapart.MapartMaterialsPanel;
import com.fudgedy.schematicindex.gui.mapart.MapartSession;
import com.fudgedy.schematicindex.gui.mapart.MapartSettingsPanel;
import com.fudgedy.schematicindex.gui.mapart.MapartUi;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.mapart.MapartImage;
import com.fudgedy.schematicindex.mapart.MapartResult;
import com.fudgedy.schematicindex.mapart.MapartSchematic;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

// Settings left, the preview over the block picker in the middle, materials right, each column scrolling on its own;
// unlike the grid pages it spreads into the screen's spare width, so the picker rows wrap less and the preview grows
public class MapartTab
{
	private static final int MARGIN = 8;
	// Mirrors IndexScreen's private outer margin so the page lines up with the top bar's content edge
	private static final int OUTER_MARGIN = 8;
	private static final int MAX_WIDTH = 1180;
	private static final int COLUMN_GAP = 12;
	private static final int ROW_GAP = 6;
	private static final int SETTINGS_MIN_WIDTH = 200;
	private static final int SETTINGS_MAX_WIDTH = 260;
	private static final int MATERIALS_MIN_WIDTH = 140;
	private static final int MATERIALS_MAX_WIDTH = 200;
	private static final int PICKER_MIN_HEIGHT = 150;
	private static final int PREVIEW_PAD = 4;
	private static final int CLOSE_SIZE = 14;
	private static final int STEP_BUTTON = 16;
	private static final int CHUNK = 16;
	private static final int GRID_CHUNK = 0x40FFFFFF;
	private static final int GRID_MAP = 0xB0FFFFFF;

	private final IndexScreen screen;
	private final MapartSettingsPanel settings = new MapartSettingsPanel();
	private final MapartBlockPicker picker;
	private final MapartMaterialsPanel materials = new MapartMaterialsPanel();
	// Resolved on first render: Screen assigns its font in init(), after the field initialisers ran
	private Font font;

	private final Rect saveButton = new Rect();
	private final Rect saveSplitButton = new Rect();
	private final Rect loadButton = new Rect();
	private final Rect splitMinus = new Rect();
	private final Rect splitPlus = new Rect();
	private final Rect compareToggle = new Rect();
	private final Rect previewArea = new Rect();
	private final Rect clearButton = new Rect();

	public MapartTab(IndexScreen screen)
	{
		this.screen = screen;
		this.picker = new MapartBlockPicker(screen);
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		this.font = this.screen.font();
		MapartSession.uploadPending();

		int top = IndexScreen.TOP_BAR_HEIGHT + 10;
		int bottom = this.screen.height - MARGIN;
		int available = this.screen.width - IndexScreen.RAIL_WIDTH - OUTER_MARGIN * 2;
		int width = Math.min(available, MAX_WIDTH);
		int x = IndexScreen.RAIL_WIDTH + OUTER_MARGIN + (available - width) / 2;
		int settingsWidth = Math.max(SETTINGS_MIN_WIDTH, Math.min(SETTINGS_MAX_WIDTH, width * 28 / 100));
		int materialsWidth = Math.max(MATERIALS_MIN_WIDTH, Math.min(MATERIALS_MAX_WIDTH, width * 20 / 100));
		int previewX = x + settingsWidth + COLUMN_GAP;
		int previewWidth = width - settingsWidth - materialsWidth - COLUMN_GAP * 2;
		int materialsX = x + width - materialsWidth;
		// The preview keeps the larger share of the middle column; the picker takes what is left
		int pickerHeight = Math.max(PICKER_MIN_HEIGHT, (bottom - top) * 45 / 100);
		int pickerTop = bottom - pickerHeight;
		int previewBottom = pickerTop - ROW_GAP * 2;

		int panelMouseX = this.openDropdown() != null ? -1 : mouseX;
		int panelMouseY = this.openDropdown() != null ? -1 : mouseY;
		this.settings.render(ctx, this.font, x, top, settingsWidth, bottom, panelMouseX, panelMouseY);
		this.renderPreview(ctx, previewX, top, previewWidth, previewBottom, panelMouseX, panelMouseY);
		this.materials.render(ctx, this.font, materialsX, top, materialsWidth, bottom, panelMouseX, panelMouseY);
		this.picker.render(ctx, this.font, previewX, pickerTop, previewWidth, bottom, panelMouseX, panelMouseY);
		this.renderOverlays(ctx, mouseX, mouseY, x + width, bottom);
	}

	private void renderPreview(GuiGraphics ctx, int x, int top, int width, int bottom, int mouseX, int mouseY)
	{
		int line = this.font.lineHeight;
		Theme.text(ctx, this.font, Theme.bold("Map Preview"), x, top, Theme.TEXT);
		top += line + ROW_GAP;
		int footer = line + ROW_GAP + IndexScreen.FIELD_HEIGHT + ROW_GAP + IndexScreen.FIELD_HEIGHT + ROW_GAP;
		int maxHeight = Math.max(64, bottom - top - footer);
		MapartResult result = MapartSession.result;
		// The box follows the texture on screen, not the pending size, so nothing stretches mid-conversion
		int mapsX = result != null ? result.mapsX() : MapartSession.options.mapsX;
		int mapsY = result != null ? result.mapsY() : MapartSession.options.mapsY;
		boolean compare = MapartSession.compare && MapartSession.sourceTexture != null;
		int panes = compare ? 2 : 1;
		int paneWidth = (width - PREVIEW_PAD * (panes - 1)) / panes;
		int boxWidth = paneWidth;
		int boxHeight = boxWidth * mapsY / mapsX;

		if (boxHeight > maxHeight)
		{
			boxHeight = maxHeight;
			boxWidth = boxHeight * mapsX / mapsY;
		}

		int totalWidth = boxWidth * panes + PREVIEW_PAD * (panes - 1);
		int boxX = x + (width - totalWidth) / 2;
		this.previewArea.set(boxX, top, totalWidth, boxHeight);

		if (compare)
		{
			this.renderPane(ctx, MapartSession.sourceTexture, boxX, top, boxWidth, boxHeight, mapsX, mapsY, "Original", mouseX, mouseY);
			boxX += boxWidth + PREVIEW_PAD;
		}

		this.renderPane(ctx, MapartSession.previewTexture, boxX, top, boxWidth, boxHeight, mapsX, mapsY, compare ? "Map" : null, mouseX, mouseY);

		if (MapartSession.source != null)
		{
			this.clearButton.set(boxX + boxWidth - CLOSE_SIZE / 2, top - CLOSE_SIZE / 2, CLOSE_SIZE, CLOSE_SIZE);
			Buttons.cornerClose(ctx, this.clearButton, mouseX, mouseY);
		}
		else
		{
			this.clearButton.set(0, 0, 0, 0);
		}

		int y = top + boxHeight + ROW_GAP;
		String status = Theme.clip(this.font, this.statusLine(), width);
		Theme.text(ctx, this.font, status, x + (width - this.font.width(status)) / 2, y, Theme.TEXT_MUTE);
		y += line + ROW_GAP;

		boolean ready = MapartSession.ready();
		int saveWidth = this.font.width(Theme.bold("Save to Litematica")) + 16;
		int splitWidth = this.font.width(Theme.bold("Save as split")) + 16;
		this.saveButton.set(x, y, saveWidth, IndexScreen.FIELD_HEIGHT);
		this.saveSplitButton.set(x + saveWidth + ROW_GAP, y, splitWidth, IndexScreen.FIELD_HEIGHT);

		int loadWidth = this.font.width(Theme.bold("Temporary load")) + 16;
		this.loadButton.set(this.saveSplitButton.x + splitWidth + ROW_GAP, y, loadWidth, IndexScreen.FIELD_HEIGHT);

		if (ready)
		{
			Buttons.pill(ctx, this.font, this.saveButton, "Save to Litematica", mouseX, mouseY, true);
			Buttons.pill(ctx, this.font, this.saveSplitButton, "Save as split", mouseX, mouseY, false);
		}
		else
		{
			Buttons.disabled(ctx, this.font, this.saveButton, "Save to Litematica");
			Buttons.disabled(ctx, this.font, this.saveSplitButton, "Save as split");
		}

		if (ready && MapartSession.canLoadInWorld())
		{
			Buttons.pill(ctx, this.font, this.loadButton, "Temporary load", mouseX, mouseY, false);
		}
		else
		{
			Buttons.disabled(ctx, this.font, this.loadButton, "Temporary load");
		}

		y += IndexScreen.FIELD_HEIGHT + ROW_GAP;
		Theme.text(ctx, this.font, "Split size", x, y + 4, Theme.TEXT_MUTE);
		int cursor = x + this.font.width("Split size") + 6;
		this.splitMinus.set(cursor, y, STEP_BUTTON, STEP_BUTTON);
		Buttons.pill(ctx, this.font, this.splitMinus, "-", mouseX, mouseY, false);
		String size = this.splitSize() + "x" + this.splitSize();
		int sizeWidth = Math.max(20, this.font.width(size) + 6);
		Theme.text(ctx, this.font, size, cursor + STEP_BUTTON + (sizeWidth - this.font.width(size)) / 2, y + 4, Theme.TEXT);
		this.splitPlus.set(cursor + STEP_BUTTON + sizeWidth, y, STEP_BUTTON, STEP_BUTTON);
		Buttons.pill(ctx, this.font, this.splitPlus, "+", mouseX, mouseY, false);
		cursor += STEP_BUTTON * 2 + sizeWidth + MARGIN;

		String files = ready && result != null ? MapartSchematic.splitCount(result, this.splitSize()) + " files" : "";
		Theme.text(ctx, this.font, files, cursor, y + 4, Theme.TEXT_ASH);
		int compareWidth = this.font.width("Compare Map to Image") + 24;
		this.compareToggle.set(x + width - compareWidth, y, compareWidth, IndexScreen.FIELD_HEIGHT);
		Controls.toggle(ctx, this.font, this.compareToggle, "Compare Map to Image", MapartSession.compare, mouseX, mouseY);
	}

	private void renderPane(GuiGraphics ctx, @Nullable Identifier texture, int boxX, int top,
			int boxWidth, int boxHeight, int mapsX, int mapsY, @Nullable String caption, int mouseX, int mouseY)
	{
		boolean empty = MapartSession.source == null;
		boolean hovered = empty && Theme.inside(mouseX, mouseY, boxX, top, boxWidth, boxHeight);
		Theme.roundedRect(ctx, boxX, top, boxWidth, boxHeight, Theme.RADIUS_CARD,
				hovered ? Theme.lighten(Theme.SURFACE_CARD, 0.06F) : Theme.SURFACE_CARD);

		if (hovered)
		{
			Theme.roundedOutline(ctx, boxX, top, boxWidth, boxHeight, Theme.RADIUS_CARD, Theme.ACCENT_BRIGHT);
		}

		int imageX = boxX + PREVIEW_PAD;
		int imageY = top + PREVIEW_PAD;
		int imageWidth = boxWidth - PREVIEW_PAD * 2;
		int imageHeight = boxHeight - PREVIEW_PAD * 2;

		if (texture != null && MapartSession.result != null)
		{
			Theme.image(ctx, texture, imageX, imageY, imageWidth, imageHeight, MapartSession.textureWidth, MapartSession.textureHeight);

			if (MapartSession.grid)
			{
				this.renderGrid(ctx, imageX, imageY, imageWidth, imageHeight, mapsX, mapsY);
			}
		}
		else
		{
			String hint = MapartSession.source == null ? "Click to choose an image, or Ctrl+V to paste" : "Converting...";
			List<String> rows = MapartSettingsPanel.wrap(this.font, hint, imageWidth - PREVIEW_PAD * 2);
			int rowY = top + (boxHeight - rows.size() * this.font.lineHeight) / 2;

			for (String row : rows)
			{
				Theme.text(ctx, this.font, row, boxX + (boxWidth - this.font.width(row)) / 2, rowY, Theme.TEXT_ASH);
				rowY += this.font.lineHeight;
			}
		}

		if (caption != null)
		{
			Theme.text(ctx, this.font, caption, imageX + 2, imageY + 2, Theme.TEXT);
		}
	}

	// Chunk lines every 16 pixels, brighter map lines every 128; both scale with the on-screen box
	private void renderGrid(GuiGraphics ctx, int x, int y, int width, int height, int mapsX, int mapsY)
	{
		int pixelsX = mapsX * MapartImage.MAP_SIZE;
		int pixelsY = mapsY * MapartImage.MAP_SIZE;

		for (int px = CHUNK; px < pixelsX; px += CHUNK)
		{
			int lineX = x + px * width / pixelsX;
			ctx.fill(lineX, y, lineX + 1, y + height, px % MapartImage.MAP_SIZE == 0 ? GRID_MAP : GRID_CHUNK);
		}

		for (int pz = CHUNK; pz < pixelsY; pz += CHUNK)
		{
			int lineY = y + pz * height / pixelsY;
			ctx.fill(x, lineY, x + width, lineY + 1, pz % MapartImage.MAP_SIZE == 0 ? GRID_MAP : GRID_CHUNK);
		}
	}

	// Open dropdown lists, the colour popup and the block tooltip go last so they sit above every panel
	private void renderOverlays(GuiGraphics ctx, int mouseX, int mouseY, int right, int bottom)
	{
		for (Dropdown dropdown : this.dropdowns())
		{
			dropdown.renderOpen(ctx, this.font, mouseX, mouseY, bottom);
		}

		this.settings.renderPopup(ctx, this.font, right, bottom, mouseX, mouseY);
		String label = this.picker.hoverLabel();

		if (label == null || this.openDropdown() != null)
		{
			return;
		}

		int width = this.font.width(label) + 8;
		int height = this.font.lineHeight + 6;
		int x = Math.min(this.picker.hoverX() + 10, right - width - 2);
		int y = this.picker.hoverY() - height - 4;
		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL, Theme.HAIRLINE);
		Theme.text(ctx, this.font, label, x + 4, y + 4, Theme.TEXT);
	}

	private Dropdown[] dropdowns()
	{
		Dropdown[] settingsDropdowns = this.settings.dropdowns();
		Dropdown[] all = new Dropdown[settingsDropdowns.length + 1];
		System.arraycopy(settingsDropdowns, 0, all, 0, settingsDropdowns.length);
		all[settingsDropdowns.length] = this.picker.presetDropdown;
		return all;
	}

	private @Nullable Dropdown openDropdown()
	{
		for (Dropdown dropdown : this.dropdowns())
		{
			if (dropdown.isOpen())
			{
				return dropdown;
			}
		}

		return null;
	}

	private int splitSize()
	{
		MapartResult result = MapartSession.result;
		int largest = result != null ? Math.max(result.mapsX(), result.mapsY())
				: Math.max(MapartSession.options.mapsX, MapartSession.options.mapsY);
		return Math.max(1, Math.min(largest, MapartSession.splitSize));
	}

	private String statusLine()
	{
		if (!MapartSession.status.isEmpty())
		{
			return MapartSession.status;
		}

		MapartResult result = MapartSession.result;

		if (MapartSession.source == null)
		{
			return "Pick a PNG or JPG; each map is 128x128 pixels";
		}

		if (MapartSession.converting() || result == null)
		{
			return "Converting...";
		}

		String tall = result.staircase() ? ", " + result.sizeY() + " tall" : "";
		return String.format(Locale.ROOT, "%dx%d maps, %,d blocks%s", result.mapsX(), result.mapsY(), result.blockCount(), tall);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.settings.popupClicked(mouseX, mouseY))
		{
			return true;
		}

		Dropdown open = this.openDropdown();

		if (open != null)
		{
			this.chose(open, open.click(mouseX, mouseY));
			return true;
		}

		if (this.picker.clickFields(mouseX, mouseY))
		{
			return true;
		}

		// A pill scrolled out of its panel keeps a rect, so only the panel under the mouse may open one
		for (Dropdown dropdown : this.dropdowns())
		{
			boolean owned = dropdown == this.picker.presetDropdown ? this.picker.bounds.contains(mouseX, mouseY)
					: this.settings.bounds.contains(mouseX, mouseY);
			int choice = owned ? dropdown.click(mouseX, mouseY) : Dropdown.NOT_HANDLED;

			if (choice != Dropdown.NOT_HANDLED)
			{
				this.chose(dropdown, choice);
				return true;
			}
		}

		if (this.saveButton.contains(mouseX, mouseY) && MapartSession.ready())
		{
			MapartUi.press(this.saveButton);
			MapartSession.save(false);
			return true;
		}

		if (this.saveSplitButton.contains(mouseX, mouseY) && MapartSession.ready())
		{
			MapartUi.press(this.saveSplitButton);
			MapartSession.splitSize = this.splitSize();
			MapartSession.save(true);
			return true;
		}

		if (this.loadButton.contains(mouseX, mouseY) && MapartSession.ready() && MapartSession.canLoadInWorld())
		{
			MapartUi.press(this.loadButton);
			MapartSession.temporaryLoad();
			return true;
		}

		if (this.splitMinus.contains(mouseX, mouseY) || this.splitPlus.contains(mouseX, mouseY))
		{
			boolean plus = this.splitPlus.contains(mouseX, mouseY);
			MapartUi.press(plus ? this.splitPlus : this.splitMinus);
			MapartSession.splitSize = this.splitSize() + (plus ? 1 : -1);
			MapartSession.splitSize = this.splitSize();
			return true;
		}

		if (this.compareToggle.contains(mouseX, mouseY))
		{
			MapartUi.press(this.compareToggle);
			MapartSession.compare = !MapartSession.compare;
			return true;
		}

		if (this.clearButton.contains(mouseX, mouseY))
		{
			MapartUi.press(this.clearButton);
			MapartSession.clearImage();
			return true;
		}

		// The picture itself is a drop target of sorts: clicking it, empty or loaded, picks a file
		if (this.previewArea.contains(mouseX, mouseY))
		{
			Theme.click();
			MapartSession.openImagePicker();
			return true;
		}

		// Each panel scrolls, so a control scrolled out of view must not take clicks through the chrome
		return this.settings.mouseClicked(mouseX, mouseY) || this.materials.mouseClicked(mouseX, mouseY)
				|| this.picker.mouseClicked(mouseX, mouseY);
	}

	private void chose(Dropdown dropdown, int choice)
	{
		if (choice < 0)
		{
			return;
		}

		if (dropdown == this.picker.presetDropdown)
		{
			this.picker.choosePreset(choice);
			return;
		}

		this.settings.choose(dropdown, choice);
	}

	public boolean mouseDragged(double mouseX, double mouseY)
	{
		return this.settings.mouseDragged(mouseX, mouseY);
	}

	public boolean mouseReleased()
	{
		return this.settings.mouseReleased();
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		// A panel scrolling under an open list would carry its pill away from it
		if (this.openDropdown() != null)
		{
			return true;
		}

		if (this.materials.mouseScrolled(mouseX, mouseY, scrollY))
		{
			return true;
		}

		if (this.picker.bounds.contains(mouseX, mouseY))
		{
			return this.picker.mouseScrolled(scrollY);
		}

		if (this.settings.bounds.contains(mouseX, mouseY))
		{
			return this.settings.mouseScrolled(scrollY);
		}

		return true;
	}

	// The panels go first so a focused text field keeps its own paste
	public boolean keyPressed(KeyEvent event)
	{
		if (this.settings.keyPressed(event) || this.picker.keyPressed(event))
		{
			return true;
		}

		if (!IndexScreen.isPasteChord(event))
		{
			return false;
		}

		MapartSession.pasteFromClipboard();
		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		return this.settings.charTyped(event) || this.picker.charTyped(event);
	}
}

package com.fudgedy.schematicindex.gui.mapart;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.TextInput;
import com.fudgedy.schematicindex.mapart.MapPalette;
import com.fudgedy.schematicindex.mapart.MapartOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The block selection panel: presets on top, then one row per map colour with its swatch and
// the block alternatives as item icons, None first
public final class MapartBlockPicker
{
	private static final int CELL = 18;
	private static final int ICON = 16;
	private static final int SWATCH = 12;
	private static final int ROW_PAD = 1;
	private static final int GAP = 4;
	private static final int SELECTED_OUTLINE = 0xFFFFFFFF;
	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static final ItemStack NONE_ICON = new ItemStack(Items.BARRIER);

	private final IndexScreen screen;
	private Font font;

	public final Rect bounds = new Rect();
	private float scroll;
	private float maxScroll;
	private int selectedPreset = -1;
	private @Nullable String hoverLabel;
	private int hoverX;
	private int hoverY;

	public final Dropdown presetDropdown = new Dropdown();
	private final Rect presetRect = new Rect();
	private final Rect nameField = new Rect();
	private final Rect saveButton = new Rect();
	private final Rect deleteButton = new Rect();
	private final Rect importButton = new Rect();
	private final Rect exportButton = new Rect();
	private final TextInput nameInput = new TextInput(24, null);
	// Parallel to MapPalette ids; a row's cells are laid out again every frame from these origins
	private final int[] rowY = new int[MapPalette.GROUPS];
	private final int[] rowLines = new int[MapPalette.GROUPS];
	private int rowsX;
	private int rowsWidth;
	private int listTop;
	private int perLine = 1;

	public MapartBlockPicker(IndexScreen screen)
	{
		this.screen = screen;
	}

	public @Nullable String hoverLabel()
	{
		return this.hoverLabel;
	}

	public int hoverX()
	{
		return this.hoverX;
	}

	public int hoverY()
	{
		return this.hoverY;
	}

	public boolean anyFieldFocused()
	{
		return this.nameInput.isFocused();
	}

	public void render(GuiGraphicsExtractor ctx, Font font, int x, int top, int width, int bottom, int mouseX, int mouseY)
	{
		this.font = font;
		this.bounds.set(x, top, width, bottom - top);
		this.hoverLabel = null;
		int y = top;

		y = this.renderPresets(ctx, x, y, width, mouseX, mouseY);
		ctx.enableScissor(x, y, x + width, bottom);
		this.listTop = y;
		int listTop = y;
		int startY = listTop - Math.round(this.scroll);
		y = startY;
		this.rowsX = x;
		this.rowsWidth = width;
		this.perLine = Math.max(1, (width - SWATCH - GAP) / CELL);
		boolean hoverInside = this.bounds.contains(mouseX, mouseY) && mouseY >= listTop;

		for (int id : MapPalette.DISPLAY_ORDER)
		{
			MapPalette.Group group = MapPalette.group(id);
			int cells = group.options().length + 1;
			int lines = (cells + this.perLine - 1) / this.perLine;
			int rowHeight = lines * CELL + ROW_PAD * 2;
			this.rowY[id] = y;
			this.rowLines[id] = lines;

			if (y + rowHeight >= listTop && y <= bottom)
			{
				this.renderRow(ctx, group, x, y, width, rowHeight, hoverInside ? mouseX : -1, hoverInside ? mouseY : -1);
			}

			y += rowHeight;
		}

		ctx.disableScissor();
		this.maxScroll = Math.max(0.0F, y - startY - (bottom - listTop));
		this.scroll = Math.min(this.scroll, this.maxScroll);
		MapartUi.scrollbar(ctx, x + width + 3, listTop, bottom - listTop, this.scroll, this.maxScroll);
	}

	private int renderPresets(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY)
	{
		int dropdownWidth = Math.min(110, width / 3);
		int saveWidth = this.font.width(Theme.bold("Save")) + 14;
		int deleteWidth = this.font.width(Theme.bold("Delete")) + 14;
		int nameWidth = Math.max(60, width - dropdownWidth - saveWidth - deleteWidth - GAP * 3);

		this.presetRect.set(x, y, dropdownWidth, IndexScreen.FIELD_HEIGHT);
		this.presetDropdown.render(ctx, this.font, this.presetRect, this.presetLabels(), this.selectedPreset, "Custom", mouseX, mouseY);
		this.nameField.set(x + dropdownWidth + GAP, y, nameWidth, IndexScreen.FIELD_HEIGHT);
		this.nameInput.render(ctx, this.font, this.nameField, "Preset name", mouseX, mouseY);
		this.saveButton.set(this.nameField.x + nameWidth + GAP, y, saveWidth, IndexScreen.FIELD_HEIGHT);
		this.deleteButton.set(this.saveButton.x + saveWidth + GAP, y, deleteWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.saveButton, "Save", mouseX, mouseY, false);

		if (this.selectedPreset >= MapPalette.PRESET_LABELS.length)
		{
			Buttons.pill(ctx, this.font, this.deleteButton, "Delete", mouseX, mouseY, false);
		}
		else
		{
			Buttons.disabled(ctx, this.font, this.deleteButton, "Delete");
		}

		y += IndexScreen.FIELD_HEIGHT + GAP;
		int exportWidth = this.font.width(Theme.bold("Export")) + 14;
		int importWidth = this.font.width(Theme.bold("Import")) + 14;
		this.exportButton.set(x, y, exportWidth, IndexScreen.FIELD_HEIGHT);
		this.importButton.set(x + exportWidth + GAP, y, importWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.exportButton, "Export", mouseX, mouseY, false);
		Buttons.pill(ctx, this.font, this.importButton, "Import", mouseX, mouseY, false);
		return y + IndexScreen.FIELD_HEIGHT + GAP + 2;
	}

	private void renderRow(GuiGraphicsExtractor ctx, MapPalette.Group group, int x, int y, int width, int rowHeight,
			int mouseX, int mouseY)
	{
		MapartOptions options = MapartSession.options;
		boolean on = options.enabled[group.id()];
		int choice = Math.floorMod(options.blockChoice[group.id()], group.options().length);

		if (Theme.inside(mouseX, mouseY, x, y, width, rowHeight))
		{
			Theme.roundedRect(ctx, x, y, width, rowHeight, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
		}

		int swatchY = y + ROW_PAD + (CELL - SWATCH) / 2;
		ctx.fill(x + 2, swatchY, x + 2 + SWATCH, swatchY + SWATCH, MapPalette.argb(group.id(), MapPalette.SHADE_NORMAL));

		if (!on)
		{
			ctx.fill(x + 2, swatchY, x + 2 + SWATCH, swatchY + SWATCH, 0x88000000);
		}

		for (int cell = 0; cell <= group.options().length; cell++)
		{
			int cellX = x + SWATCH + GAP + (cell % this.perLine) * CELL;
			int cellY = y + ROW_PAD + (cell / this.perLine) * CELL;
			boolean selected = cell == 0 ? !on : on && choice == cell - 1;
			boolean hovered = Theme.inside(mouseX, mouseY, cellX, cellY, CELL, CELL);

			if (selected)
			{
				Theme.roundedRect(ctx, cellX, cellY, CELL, CELL, Theme.RADIUS_PILL, Theme.ACCENT);
			}
			else if (hovered)
			{
				Theme.roundedRect(ctx, cellX, cellY, CELL, CELL, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
			}

			ItemStack stack = cell == 0 ? NONE_ICON : icon(group.options()[cell - 1].block());
			Theme.itemScaled(ctx, stack, cellX + (CELL - ICON) / 2, cellY + (CELL - ICON) / 2, 1.0F);

			if (selected)
			{
				Theme.roundedOutline(ctx, cellX, cellY, CELL, CELL, Theme.RADIUS_PILL, SELECTED_OUTLINE);
			}

			if (hovered)
			{
				this.hoverLabel = cell == 0 ? "None" : group.options()[cell - 1].label();
				this.hoverX = mouseX;
				this.hoverY = mouseY;
			}
		}
	}

	// Item forms are the catalogue's block icon idiom; water has no item so its bucket stands in
	static ItemStack icon(String spec)
	{
		ItemStack cached = ICONS.get(spec);

		if (cached != null)
		{
			return cached;
		}

		int bracket = spec.indexOf('[');
		String id = bracket >= 0 ? spec.substring(0, bracket) : spec;
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
		Item item = block == null ? Items.BARRIER : block.asItem();
		ItemStack stack = new ItemStack(item == Items.AIR ? (id.endsWith("water") ? Items.WATER_BUCKET : Items.BARRIER) : item);
		ICONS.put(spec, stack);
		return stack;
	}

	private String[] presetLabels()
	{
		List<String> labels = new ArrayList<>(List.of(MapPalette.PRESET_LABELS));
		labels.addAll(Settings.mapartPresets().keySet());
		return labels.toArray(new String[0]);
	}

	public void choosePreset(int index)
	{
		MapartOptions next = MapartSession.options.copy();

		if (index < MapPalette.PRESET_LABELS.length)
		{
			MapPalette.applyPreset(index, next.enabled, next.blockChoice);
		}
		else
		{
			String[] labels = this.presetLabels();

			if (index >= labels.length || !MapPalette.decode(Settings.mapartPresets().get(labels[index]), next.enabled, next.blockChoice))
			{
				return;
			}

			this.nameInput.set(labels[index]);
		}

		this.selectedPreset = index;
		MapartSession.update(next);
	}

	public boolean clickFields(double mouseX, double mouseY)
	{
		return this.nameInput.click(mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.bounds.contains(mouseX, mouseY))
		{
			return false;
		}

		if (this.saveButton.contains(mouseX, mouseY))
		{
			MapartUi.press(this.saveButton);
			this.savePreset();
			return true;
		}

		if (this.deleteButton.contains(mouseX, mouseY) && this.selectedPreset >= MapPalette.PRESET_LABELS.length)
		{
			MapartUi.press(this.deleteButton);
			Settings.removeMapartPreset(this.presetLabels()[this.selectedPreset]);
			this.selectedPreset = -1;
			return true;
		}

		if (this.exportButton.contains(mouseX, mouseY))
		{
			MapartUi.press(this.exportButton);
			boolean copied = this.screen.copyToClipboard(MapPalette.encode(MapartSession.options.enabled, MapartSession.options.blockChoice));
			MapartSession.status = copied ? "Palette code copied to the clipboard." : "Could not reach the clipboard.";
			return true;
		}

		if (this.importButton.contains(mouseX, mouseY))
		{
			MapartUi.press(this.importButton);
			this.importPreset();
			return true;
		}

		return this.clickRows(mouseX, mouseY);
	}

	private void savePreset()
	{
		String name = this.nameInput.value().trim();

		if (name.isEmpty())
		{
			MapartSession.status = "Type a preset name first.";
			return;
		}

		Settings.putMapartPreset(name, MapPalette.encode(MapartSession.options.enabled, MapartSession.options.blockChoice));
		String[] labels = this.presetLabels();

		for (int i = MapPalette.PRESET_LABELS.length; i < labels.length; i++)
		{
			if (labels[i].equals(name))
			{
				this.selectedPreset = i;
			}
		}

		MapartSession.status = "Preset " + name + " saved.";
	}

	private void importPreset()
	{
		String clip;

		try
		{
			clip = Minecraft.getInstance().keyboardHandler.getClipboard();
		}
		catch (Exception e)
		{
			clip = "";
		}

		MapartOptions next = MapartSession.options.copy();

		if (!MapPalette.decode(clip, next.enabled, next.blockChoice))
		{
			MapartSession.status = "The clipboard does not hold a palette code.";
			return;
		}

		this.selectedPreset = -1;
		MapartSession.update(next);
		MapartSession.status = "Palette code imported.";
	}

	private boolean clickRows(double mouseX, double mouseY)
	{
		if (mouseY < this.listTop)
		{
			return true;
		}

		for (int id : MapPalette.DISPLAY_ORDER)
		{
			MapPalette.Group group = MapPalette.group(id);
			int rowHeight = this.rowLines[id] * CELL + ROW_PAD * 2;

			if (!Theme.inside(mouseX, mouseY, this.rowsX, this.rowY[id], this.rowsWidth, rowHeight))
			{
				continue;
			}

			int column = (int) ((mouseX - this.rowsX - SWATCH - GAP) / CELL);
			int line = (int) ((mouseY - this.rowY[id] - ROW_PAD) / CELL);
			int cell = line * this.perLine + column;

			if (column < 0 || column >= this.perLine || cell > group.options().length)
			{
				return true;
			}

			Theme.click();
			MapartOptions next = MapartSession.options.copy();
			next.enabled[id] = cell > 0;
			next.blockChoice[id] = Math.max(0, cell - 1);
			this.selectedPreset = -1;
			MapartSession.update(next);
			return true;
		}

		return true;
	}

	public boolean mouseScrolled(double scrollY)
	{
		this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		return this.nameInput.keyPressed(event);
	}

	public boolean charTyped(CharacterEvent event)
	{
		return this.nameInput.charTyped(event);
	}
}

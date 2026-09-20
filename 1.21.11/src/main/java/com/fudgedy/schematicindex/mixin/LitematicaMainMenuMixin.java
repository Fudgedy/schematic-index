package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.FolderIcon;
import com.fudgedy.schematicindex.gui.IconButton;
import com.fudgedy.schematicindex.gui.IndexIcon;
import com.fudgedy.schematicindex.gui.IndexScreen;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.selection.SelectionMode;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(GuiMainMenu.class)
public abstract class LitematicaMainMenuMixin extends GuiBase
{
	@Unique
	private static final String SCHEMATICINDEX$LABEL = "The Schematic Index";

	// A non-required mixin can silently no-op if Litematica's menu changes shape, so the absent log line
	// is what makes a vanished button diagnosable
	@Unique
	private static boolean schematicindex$buttonInjected;

	@Inject(method = "initGui", at = @At("RETURN"), remap = false)
	private void schematicindex$addLibraryButton(CallbackInfo info)
	{
		int buttonWidth = schematicindex$columnWidth();
		int x = 12 + buttonWidth + 20;

		IconButton button = new IconButton(x, 52, buttonWidth, 20, SCHEMATICINDEX$LABEL, IndexIcon.INSTANCE);
		button.setTextCentered(false);
		button.setIconAlignment(LeftRight.LEFT);
		this.addButton(button, (pressed, mouseButton) -> Minecraft.getInstance().setScreen(new IndexScreen(this)));

		ButtonGeneric folderButton = new ButtonGeneric(x, 52 + 20 + 4, buttonWidth, 20,
				"Open Schematics Folder", FolderIcon.INSTANCE);
		folderButton.setTextCentered(false);
		folderButton.setIconAlignment(LeftRight.LEFT);
		this.addButton(folderButton, (pressed, mouseButton) -> schematicindex$openSchematicsFolder());

		// Guarded on the flag because this runs every time the menu opens
		if (!schematicindex$buttonInjected)
		{
			schematicindex$buttonInjected = true;
			SchematicIndexMod.LOGGER.info("Injected The Schematic Index button into Litematica's main menu");
		}
	}

	@Unique
	private void schematicindex$openSchematicsFolder()
	{
		try
		{
			Path directory = Settings.downloadDirectory();
			Files.createDirectories(directory);
			Util.getPlatform().openPath(directory);
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.warn("Could not open the schematics folder", e);
		}
	}

	@Unique
	private int schematicindex$columnWidth()
	{
		int width = 0;

		for (GuiMainMenu.ButtonListenerChangeMenu.ButtonType type
				: GuiMainMenu.ButtonListenerChangeMenu.ButtonType.values())
				{
			width = Math.max(width, this.getStringWidth(type.getDisplayName()) + 30);
		}

		for (SelectionMode mode : SelectionMode.values())
		{
			String label = StringUtils.translate("litematica.gui.button.area_selection_mode", mode.getDisplayName());
			width = Math.max(width, this.getStringWidth(label) + 10);
		}

		return width;
	}
}

package com.fudgedy.schematicindex.gui.mapart;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.export.ExportActions;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.mapart.MapartExports;
import com.fudgedy.schematicindex.mapart.MapartResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

// Saved beside the mapart's .litematic, so the picture and the schematic travel together
public final class MapartPreviewExport
{
	private MapartPreviewExport()
	{
	}

	public static void save()
	{
		MapartResult finished = MapartSession.result;

		if (!MapartSession.ready() || finished == null)
		{
			return;
		}

		String baseName = MapartSession.baseName();
		Path target = Settings.downloadDirectory().resolve(baseName + MapartExports.PREVIEW_SUFFIX);
		MapartSession.status = "Saving preview...";

		Net.submit(() -> {
			BufferedImage image;

			try
			{
				image = MapartExports.writePreview(finished, target);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Mapart preview export failed for {}", baseName, e);
				Errors.report(Errors.MAPART_EXPORT);
				Minecraft.getInstance().execute(() -> MapartSession.status = "Preview export failed.");
				return;
			}

			if (McAuth.verified())
			{
				Backend.postEvent("export");
			}

			Usage.once("mapart_preview_png");
			String shown = target.getFileName().toString();

			Minecraft.getInstance().execute(() -> {
				MapartSession.status = "Saved " + shown;
				Theme.success();
				ExportActions.savedToast("Saved mapart preview", shown, new ItemStack(Items.FILLED_MAP), image, baseName, null);
			});
		});
	}
}

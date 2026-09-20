package com.fudgedy.schematicindex.gui.mapart;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.mapart.MapCorners;
import com.fudgedy.schematicindex.mapart.MapartClipboard;
import com.fudgedy.schematicindex.mapart.MapartConverter;
import com.fudgedy.schematicindex.mapart.MapartExports;
import com.fudgedy.schematicindex.mapart.MapartImage;
import com.fudgedy.schematicindex.mapart.MapartOptions;
import com.fudgedy.schematicindex.mapart.MapartResult;
import com.fudgedy.schematicindex.mapart.MapartSchematic;
import com.mojang.blaze3d.platform.NativeImage;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// The picture, settings and conversion shared by every part of the Mapart page; kept for the
// session so reopening the menu lands on the same picture and settings
public final class MapartSession
{
	public static MapartOptions options = new MapartOptions();
	public static @Nullable MapartImage source;
	public static String sourceName = "";
	public static @Nullable MapartResult result;
	public static String status = "";
	public static boolean grid;
	public static boolean compare;
	public static boolean maxPerSplit;
	public static int splitSize = 1;
	public static @Nullable Identifier previewTexture;
	public static @Nullable Identifier sourceTexture;
	public static int textureWidth;
	public static int textureHeight;

	private static @Nullable MapartResult pendingUpload;
	private static int textureCounter;
	private static long jobSequence;
	private static volatile long finishedSequence;
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "schematicindex-mapart");
		thread.setDaemon(true);
		return thread;
	});

	private MapartSession()
	{
	}

	public static boolean converting()
	{
		return source != null && finishedSequence != jobSequence;
	}

	// Back to the empty state: the picture, its conversion and both preview textures go
	public static void clearImage()
	{
		source = null;
		sourceName = "";
		result = null;
		pendingUpload = null;
		status = "";
		Minecraft client = Minecraft.getInstance();

		if (previewTexture != null)
		{
			client.getTextureManager().release(previewTexture);
			previewTexture = null;
		}

		if (sourceTexture != null)
		{
			client.getTextureManager().release(sourceTexture);
			sourceTexture = null;
		}
	}

	public static boolean ready()
	{
		return result != null && result.blockCount() > 0 && !converting();
	}

	public static void update(MapartOptions next)
	{
		options = next;
		status = "";
		requestConvert();
	}

	public static void openImagePicker()
	{
		new Thread(() -> {
			String picked;

			try (MemoryStack stack = MemoryStack.stackPush())
			{
				PointerBuffer filters = stack.mallocPointer(5);
				filters.put(stack.UTF8("*.png"));
				filters.put(stack.UTF8("*.jpg"));
				filters.put(stack.UTF8("*.jpeg"));
				filters.put(stack.UTF8("*.bmp"));
				filters.put(stack.UTF8("*.gif"));
				filters.flip();
				picked = TinyFileDialogs.tinyfd_openFileDialog("Choose the mapart image", "", filters, "Images", false);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Mapart image picker failed", e);
				Errors.report(Errors.MAPART_PICKER);
				return;
			}

			if (picked == null || picked.isBlank())
			{
				return;
			}

			Path file = Path.of(picked.trim());
			MapartImage decoded;

			try
			{
				decoded = MapartImage.decode(file);
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.warn("Mapart image {} failed to decode", file, e);
				Errors.report(Errors.MAPART_IMAGE);
				return;
			}

			Minecraft.getInstance().execute(() -> {
				source = decoded;
				sourceName = file.getFileName().toString();
				status = "";
				requestConvert();
			});
		}, "schematicindex-mapart-picker").start();
	}

	public static void pasteFromClipboard()
	{
		if (!MapartClipboard.supported())
		{
			status = "Clipboard paste is not supported on macOS.";
			return;
		}

		status = "Reading clipboard...";

		Net.submit(() -> {
			MapartClipboard.Pasted pasted;

			try
			{
				pasted = MapartClipboard.read();
			}
			catch (MapartClipboard.Refused e)
			{
				Minecraft.getInstance().execute(() -> status = e.getMessage());
				return;
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Mapart clipboard paste failed", e);
				Errors.report(Errors.MAPART_PASTE);
				Minecraft.getInstance().execute(() -> status = "Paste failed.");
				return;
			}

			Minecraft.getInstance().execute(() -> {
				if (pasted == null)
				{
					status = "Nothing on the clipboard to paste.";
					return;
				}

				source = pasted.image();
				sourceName = pasted.name();
				status = "";
				requestConvert();
			});
		});
	}

	// Latest request wins: a job that finds a newer sequence when it starts or finishes is dropped,
	// so slider drags never queue a backlog of stale conversions
	public static void requestConvert()
	{
		MapartImage image = source;

		if (image == null)
		{
			result = null;
			return;
		}

		final long sequence = ++jobSequence;
		final MapartOptions snapshot = options.copy();

		WORKER.execute(() -> {
			if (sequence != jobSequence)
			{
				return;
			}

			MapartResult converted;

			try
			{
				converted = MapartConverter.convert(image, snapshot);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Mapart conversion failed", e);
				Errors.report(Errors.MAPART_CONVERT);
				finishedSequence = sequence;
				return;
			}

			Minecraft.getInstance().execute(() -> {
				if (sequence != jobSequence)
				{
					return;
				}

				result = converted;
				pendingUpload = converted;
				finishedSequence = sequence;
				Usage.once("mapart_convert:" + sizeBucket(converted));
			});
		});
	}

	// Runs on the render thread: the finished conversion's pixels become the two preview textures
	public static void uploadPending()
	{
		MapartResult ready = pendingUpload;

		if (ready == null)
		{
			return;
		}

		pendingUpload = null;
		previewTexture = replace(previewTexture, ready.preview(), ready.width(), ready.height());
		sourceTexture = replace(sourceTexture, ready.sourcePreview(), ready.width(), ready.height());
		textureWidth = ready.width();
		textureHeight = ready.height();
	}

	private static String sizeBucket(MapartResult converted)
	{
		int maps = converted.mapsX() * converted.mapsY();
		return maps == 1 ? "1x1" : maps <= 4 ? "small" : "large";
	}

	private static Identifier replace(@Nullable Identifier previous, int[] argb, int width, int height)
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				image.setPixel(x, y, argb[y * width + x]);
			}
		}

		Minecraft client = Minecraft.getInstance();
		Identifier id = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "mapart/preview" + (textureCounter++));
		client.getTextureManager().register(id, new DynamicTexture(() -> "schematicindex-mapart", image));

		if (previous != null)
		{
			client.getTextureManager().release(previous);
		}

		return id;
	}

	public static String baseName()
	{
		String base = sourceName;
		int dot = base.lastIndexOf('.');

		if (dot > 0)
		{
			base = base.substring(0, dot);
		}

		return MapartSchematic.safeStem(base + "_mapart");
	}

	public static void save(boolean split)
	{
		MapartResult finished = result;

		if (!ready() || finished == null)
		{
			return;
		}

		final String baseName = baseName();
		final Path directory = Settings.downloadDirectory();
		final int tile = Math.max(1, Math.min(splitSize, Math.max(finished.mapsX(), finished.mapsY())));
		final int files = split ? MapartSchematic.splitCount(finished, tile) : 1;
		status = split ? "Saving " + files + " split maps..." : "Saving...";

		Net.submit(() -> {
			try
			{
				if (split)
				{
					MapartSchematic.saveSplit(finished, directory, baseName, tile);
				}
				else
				{
					MapartSchematic.save(finished, directory.resolve(baseName + MapartSchematic.EXTENSION));
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Mapart save failed for {}", baseName, e);
				Errors.report(Errors.MAPART_SAVE);
				Minecraft.getInstance().execute(() -> status = "Save failed.");
				return;
			}

			if (McAuth.verified())
			{
				Backend.postEvent("mapart");
				Usage.send("mapart_save");
				Shards.pokeSoon();
			}

			Minecraft.getInstance().execute(() -> {
				status = split ? "Saved " + files + " map files." : "Saved " + baseName + MapartSchematic.EXTENSION;
				Theme.success();
				Toasts.pushAction("Mapart saved", split ? baseName + " split into " + tile + "x" + tile + " maps" : baseName + MapartSchematic.EXTENSION,
						new ItemStack(Items.FILLED_MAP), "Show corners", () -> MapCorners.enable(true, Minecraft.getInstance()));
			});
		});
	}

	public static boolean canLoadInWorld()
	{
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.player != null;
	}

	// Placement locked to the map the player stands in: its north-west block and corner height, one row
	// further north when the shaded top row is on so pixel row 0 lands on the map's north edge
	public static void temporaryLoad()
	{
		MapartResult finished = result;
		Minecraft mc = Minecraft.getInstance();

		if (!ready() || finished == null || !canLoadInWorld())
		{
			return;
		}

		String name = baseName();
		int y = Settings.hasCornerHeight() ? Settings.cornerHeight() : mc.player.getBlockY();
		BlockPos origin = new BlockPos(MapCorners.mapEdge(mc.player.getBlockX()), y,
				MapCorners.mapEdge(mc.player.getBlockZ()) - finished.rowOffset());

		try
		{
			LitematicaSchematic schematic = MapartSchematic.build(finished, name);
			SchematicHolder.getInstance().addSchematic(schematic, false);
			SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, name, true, true);
			DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Mapart temporary load failed for {}", name, e);
			Errors.report(Errors.MAPART_LOAD);
			status = "Temporary load failed.";
			return;
		}

		status = "Loaded at " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ();
		Usage.send("mapart_temp_load");
		Backend.postEvent("temp_load");
		Shards.pokeSoon();
		Theme.success();
		Toasts.push("Mapart loaded", name + " placed at " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ(),
				new ItemStack(Items.STRUCTURE_BLOCK));
	}

	public static void saveMaterials()
	{
		MapartResult finished = result;

		if (!ready() || finished == null)
		{
			return;
		}

		final String baseName = baseName();
		final Path target = Settings.downloadDirectory().resolve(baseName + MapartExports.MATERIALS_SUFFIX);
		final boolean perMap = maxPerSplit;
		status = "Saving materials...";

		Net.submit(() -> {
			try
			{
				MapartExports.writeMaterials(finished, perMap, target);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Mapart materials export failed for {}", baseName, e);
				Errors.report(Errors.MAPART_EXPORT);
				Minecraft.getInstance().execute(() -> status = "Materials export failed.");
				return;
			}

			Minecraft.getInstance().execute(() -> {
				status = "Saved " + target.getFileName();
				Theme.success();
				Toasts.push("Materials saved", target.getFileName().toString(), new ItemStack(Items.WRITABLE_BOOK));
			});
		});
	}
}

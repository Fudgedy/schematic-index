package com.fudgedy.schematicindex.export;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.Toasts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Preview PNGs, image copies and share links, the ways a build leaves the game
public final class ExportActions
{
	private ExportActions()
	{
	}

	// AWT clipboard access is refused on macOS, matching pasteFromClipboard, where it deadlocks off
	// the AppKit main thread
	public static boolean canCopyImages()
	{
		return !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
	}

	// capturePng must start on the client thread, which the click handler already is; the encoded PNG
	// arrives on a pool thread, so the decode, watermark and file write never block the render loop
	public static void savePreviewPng(IndexScreen screen, SchematicEntry entry)
	{
		SchematicPreview.capturePng(entry.schematicSlot(), png -> {
			if (png == null)
			{
				Minecraft.getInstance().execute(() ->
						Toasts.push("Couldn't save preview", "The 3D preview isn't ready yet. (" + Errors.PREVIEW + ")",
								new ItemStack(Items.BARRIER)));
				return;
			}

			Path file;
			BufferedImage saved;

			try
			{
				Path dir = FabricLoader.getInstance().getGameDir()
						.resolve("screenshots").resolve(SchematicIndexMod.MOD_ID);
				Files.createDirectories(dir);

				String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
				file = dir.resolve(sanitizeFileName(entry.title()) + "_" + stamp + ".png");
				BufferedImage plain = ImageIO.read(new ByteArrayInputStream(png));
				saved = plain == null ? null : watermarked(plain, entry);

				if (saved != null && saved != plain)
				{
					ImageIO.write(saved, "png", file.toFile());
				}
				else
				{
					Files.write(file, png);
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Saving preview PNG failed", e);
				Minecraft.getInstance().execute(() ->
						Toasts.push("Couldn't save preview", "Writing the file failed. (" + Errors.PREVIEW + ")",
								new ItemStack(Items.BARRIER)));
				return;
			}

			if (McAuth.verified())
			{
				Backend.postEvent("export");
			}

			String shown = file.getFileName().toString();
			BufferedImage copyable = saved;
			Minecraft.getInstance().execute(() -> savedToast("Saved screenshot", shown, new ItemStack(Items.PAINTING),
					copyable, entry.title(), () -> copyShareLink(screen, entry)));
		});
	}

	// Offers Copy image where the clipboard takes images; macOS gets the share link instead, or nothing
	public static void savedToast(String title, String message, ItemStack icon, @Nullable BufferedImage image, String name,
			@Nullable Runnable macAction)
	{
		if (image != null && canCopyImages())
		{
			Toasts.pushAction(title, message, icon, "Copy image", () -> copyImageToClipboard(image, name));
			return;
		}

		if (macAction != null)
		{
			Toasts.pushAction(title, message, icon, "Copy share link", macAction);
			return;
		}

		Toasts.push(title, message, icon);
	}

	public static void copyImageToClipboard(IndexScreen screen, SchematicEntry entry)
	{
		if (!canCopyImages())
		{
			Toasts.push("Copy not supported", "Copying images to the clipboard isn't supported on macOS.",
					new ItemStack(Items.BARRIER));
			return;
		}

		String ref = screen.currentImageRef(entry);

		if (ref == null)
		{
			Toasts.push("Nothing to copy", "This post has no copyable image.", new ItemStack(Items.BARRIER));
			return;
		}

		// Off the client thread, since a cache miss downloads
		Thread worker = new Thread(() -> {
			try
			{
				byte[] bytes = ImageStore.cachedBytes(ref);

				if (bytes == null)
				{
					Minecraft.getInstance().execute(() ->
							Toasts.push("Couldn't copy image", "The image isn't available yet. (" + Errors.IMAGE + ")",
									new ItemStack(Items.BARRIER)));
					return;
				}

				BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));

				if (image == null)
				{
					Minecraft.getInstance().execute(() ->
							Toasts.push("Couldn't copy image", "This image format can't be copied. (" + Errors.IMAGE + ")",
									new ItemStack(Items.BARRIER)));
					return;
				}

				toClipboard(watermarked(image, entry));
				Minecraft.getInstance().execute(() ->
						Toasts.push("Copied image", entry.title() + " is now on your clipboard.",
								new ItemStack(Items.PAINTING)));
			}
			catch (Throwable e)
			{
				clipboardFailed(e);
			}
		}, "schematicindex-copy-png");
		worker.setDaemon(true);
		worker.start();
	}

	// The render just saved, from a toast action; the image is already decoded, so only the clipboard call is left
	public static void copyImageToClipboard(BufferedImage image, String name)
	{
		Thread worker = new Thread(() -> {
			try
			{
				toClipboard(image);
				Usage.once("copy_render");
				Minecraft.getInstance().execute(() ->
						Toasts.push("Copied image", name + " is now on your clipboard.", new ItemStack(Items.PAINTING)));
			}
			catch (Throwable e)
			{
				clipboardFailed(e);
			}
		}, "schematicindex-copy-render");
		worker.setDaemon(true);
		worker.start();
	}

	// GLFW text clipboard, so unlike Copy PNG this works on macOS too
	public static void copyShareLink(IndexScreen screen, SchematicEntry entry)
	{
		// The first copy of a session goes without the referral rather than waiting on it; the next one carries it
		if (McAuth.verified() && Shards.referral() == null)
		{
			Shards.refreshReferral();
		}

		String url = Backend.shareUrl(entry.id());
		SchematicIndexMod.LOGGER.debug("Share link for {}: {}", entry.id(), url);

		if (!screen.copyToClipboard(url))
		{
			Toasts.push("Couldn't copy link", "Copying the share link failed. (" + Errors.LINK + ")",
					new ItemStack(Items.BARRIER));
			return;
		}

		Toasts.push("Share link copied", "Paste it in Discord to share this post.", new ItemStack(Items.PAPER));
		Backend.postEvent("share_link");
		Shards.pokeSoon();
	}

	// Shared by the save and copy paths; a failed watermark still returns the plain image either way
	private static BufferedImage watermarked(BufferedImage plain, SchematicEntry entry)
	{
		if (!RemoteContent.feature("watermark"))
		{
			return plain;
		}

		String credit = entry.designer() == null || entry.designer().isBlank() ? entry.poster() : entry.designer();

		try
		{
			return Watermark.apply(plain, entry.title(), credit == null ? "" : credit);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Watermarking {} failed, saving it plain", entry.id(), e);
			return plain;
		}
	}

	private static void toClipboard(BufferedImage image)
	{
		Clipboard clipboard = IndexScreen.systemClipboard();
		clipboard.setContents(new ImageTransferable(image), null);
	}

	private static void clipboardFailed(Throwable e)
	{
		SchematicIndexMod.LOGGER.warn("Copying image to clipboard failed", e);
		String reason = e instanceof HeadlessException
				? "Clipboard not available on this platform."
				: "Copying to the clipboard failed.";
		Minecraft.getInstance().execute(() ->
				Toasts.push("Couldn't copy image", reason + " (" + Errors.IMAGE + ")", new ItemStack(Items.BARRIER)));
	}

	// Capped in length so the final path stays reasonable
	private static String sanitizeFileName(String raw)
	{
		String cleaned = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9-_]+", "_")
				.replaceAll("^_+|_+$", "");

		if (cleaned.isEmpty())
		{
			cleaned = "schematic";
		}

		return Download.deviceSafe(cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned);
	}

	private record ImageTransferable(Image image) implements Transferable
	{
		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return new DataFlavor[]{DataFlavor.imageFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return DataFlavor.imageFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
		{
			if (!DataFlavor.imageFlavor.equals(flavor))
			{
				throw new UnsupportedFlavorException(flavor);
			}

			return this.image;
		}
	}
}

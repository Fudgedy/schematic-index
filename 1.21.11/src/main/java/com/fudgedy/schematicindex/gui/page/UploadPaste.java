package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.gui.IndexScreen;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

class UploadPaste
{
	private final UploadPage page;

	UploadPaste(UploadPage page)
	{
		this.page = page;
	}

	void pasteFromClipboard()
	{
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"))
		{
			this.page.statusError("Clipboard paste is not supported on macOS.");
			return;
		}

		this.page.status("Reading clipboard...");

		Thread worker = new Thread(() -> {
			Path pastedImage = null;
			String pastedUrl = null;
			boolean headless = false;

			try
			{
				java.awt.datatransfer.Transferable contents = IndexScreen.systemClipboard().getContents(null);

				if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor))
				{
					java.awt.Image image = (java.awt.Image) contents.getTransferData(
							java.awt.datatransfer.DataFlavor.imageFlavor);
					pastedImage = this.writePastedPng(toBufferedImage(image));
				}
				else if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor))
				{
					String text = ((String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor)).trim();

					if (text.startsWith("http://") || text.startsWith("https://"))
					{
						pastedUrl = text.split("\\s+")[0];
					}
				}
				else if (contents != null)
				{
					StringBuilder flavors = new StringBuilder();

					for (java.awt.datatransfer.DataFlavor flavor : contents.getTransferDataFlavors())
					{
						flavors.append(flavor.getMimeType()).append(" | ");
					}

					SchematicIndexMod.LOGGER.warn("Clipboard had no image/text. Flavors: {}", flavors);
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Clipboard read failed", e);
				headless = e instanceof java.awt.HeadlessException;
			}

			Path downloaded = null;
			boolean downloadedSchematic = false;
			boolean unsupportedLink = false;

			if (pastedUrl != null)
			{
				String extension = urlExtension(pastedUrl);
				boolean isSchematic = extension.equals("litematic");
				boolean isImage = extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg");

				if (isSchematic || isImage)
				{
					try
					{
						Path target = this.page.files.pastedFile(isSchematic ? "litematic" : extension);

						if (Backend.downloadUserLink(pastedUrl, target))
						{
							downloaded = target;
							downloadedSchematic = isSchematic;
						}
					}
					catch (Exception e)
					{
						SchematicIndexMod.LOGGER.warn("Clipboard link download failed", e);
					}
				}
				else
				{
					unsupportedLink = true;
				}
			}

			Path image = pastedImage;
			Path file = downloaded;
			boolean asSchematic = downloadedSchematic;
			boolean hadUrl = pastedUrl != null;
			boolean badType = unsupportedLink;
			boolean noClipboard = headless;
			Minecraft.getInstance().execute(() -> {
				if (image != null)
				{
					this.page.files.addPicture(image);
					this.page.status("Pasted image added.");
				}
				else if (file != null && asSchematic)
				{
					this.page.schematic = file;
					this.page.status(file.getFileName() + " selected.");
					this.page.files.parseSchematicStats(file);
					this.page.files.checkDuplicate(file);
				}
				else if (file != null)
				{
					this.page.files.addPicture(file);
					this.page.status("Downloaded image added.");
				}
				else if (badType)
				{
					this.page.statusError("That link isn't a .litematic or image.");
				}
				else if (hadUrl)
				{
					this.page.statusError("Could not fetch that link - Discord links expire, copy a fresh one.");
				}
				else if (noClipboard)
				{
					this.page.statusError("Clipboard not available on this platform.");
				}
				else
				{
					this.page.statusError("No image or link on the clipboard.");
				}
			});
		}, "schematicindex-paste");
		worker.setDaemon(true);
		worker.start();
	}

	private Path writePastedPng(java.awt.image.BufferedImage image) throws IOException
	{
		Path file = this.page.files.pastedFile("png");
		javax.imageio.ImageIO.write(image, "png", file.toFile());
		return file;
	}

	private static String urlExtension(String url)
	{
		String path = url.split("[?#]")[0];
		int dot = path.lastIndexOf('.');
		int slash = path.lastIndexOf('/');
		return dot > slash && dot >= 0 ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}

	private static java.awt.image.BufferedImage toBufferedImage(java.awt.Image image)
	{
		if (image instanceof java.awt.image.BufferedImage buffered)
		{
			return buffered;
		}

		int width = Math.max(1, image.getWidth(null));
		int height = Math.max(1, image.getHeight(null));
		java.awt.image.BufferedImage buffered = new java.awt.image.BufferedImage(width, height,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = buffered.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return buffered;
	}
}

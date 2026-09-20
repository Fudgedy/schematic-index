package com.fudgedy.schematicindex.mapart;

import com.fudgedy.schematicindex.catalogue.Backend;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

// Turns whatever the system clipboard holds into a mapart picture: a bitmap, a copied file, a path,
// or a link to an image on one of the trusted file hosts
public final class MapartClipboard
{
	private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "bmp", "gif");

	private MapartClipboard()
	{
	}

	// AWT and GLFW fight over the AppKit main thread, so MainMixin leaves headless on there
	public static boolean supported()
	{
		return !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
	}

	// Null means the clipboard held nothing a mapart can use; a decode or download failure throws
	public static @Nullable Pasted read() throws Exception
	{
		Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);

		if (contents == null)
		{
			return null;
		}

		if (contents.isDataFlavorSupported(DataFlavor.imageFlavor))
		{
			Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
			return new Pasted(MapartImage.of(toBuffered(image)), "clipboard.png");
		}

		if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
		{
			return fromFiles((List<?>) contents.getTransferData(DataFlavor.javaFileListFlavor));
		}

		if (contents.isDataFlavorSupported(DataFlavor.stringFlavor))
		{
			return fromText(((String) contents.getTransferData(DataFlavor.stringFlavor)).trim());
		}

		return null;
	}

	private static @Nullable Pasted fromFiles(List<?> files) throws Exception
	{
		for (Object entry : files)
		{
			if (entry instanceof File file && isImageName(file.getName()))
			{
				return fromFile(file.toPath());
			}
		}

		return null;
	}

	private static @Nullable Pasted fromText(String text) throws Exception
	{
		String lower = text.toLowerCase(Locale.ROOT);

		if (lower.startsWith("http://") || lower.startsWith("https://"))
		{
			return fromUrl(text.split("\s+")[0]);
		}

		if (lower.startsWith("file:"))
		{
			return fromFile(Path.of(new URI(text)));
		}

		if (text.startsWith("/") || text.startsWith("\\\\") || text.matches("^[A-Za-z]:[\\/].*"))
		{
			return fromFile(Path.of(text));
		}

		return null;
	}

	private static Pasted fromFile(Path file) throws Exception
	{
		return new Pasted(MapartImage.decode(file), file.getFileName().toString());
	}

	// The link was the player's own choice, so any https host goes, the same as the upload form
	private static Pasted fromUrl(String url) throws Exception
	{
		if (!url.trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://"))
		{
			throw new Refused("Only https links can be pasted.");
		}

		String name = urlFileName(url);
		Path temporary = Files.createTempFile("schematicindex-paste-", "-" + name);

		try
		{
			if (!Backend.downloadUserLink(url, temporary))
			{
				throw new IOException("Download failed for " + url);
			}

			return new Pasted(MapartImage.decode(temporary), name);
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	private static String urlFileName(String url)
	{
		String path = url.split("[?#]")[0];
		String name = path.substring(path.lastIndexOf('/') + 1);
		return isImageName(name) ? name : "pasted.png";
	}

	private static boolean isImageName(String name)
	{
		int dot = name.lastIndexOf('.');
		return dot >= 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
	}

	private static BufferedImage toBuffered(Image image)
	{
		if (image instanceof BufferedImage buffered)
		{
			return buffered;
		}

		int width = Math.max(1, image.getWidth(null));
		int height = Math.max(1, image.getHeight(null));
		BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = buffered.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return buffered;
	}

	public record Pasted(MapartImage image, String name)
	{
	}

	// A clipboard entry the mod understood but will not use, with the sentence to show for it
	public static final class Refused extends Exception
	{
		public Refused(String message)
		{
			super(message);
		}
	}
}

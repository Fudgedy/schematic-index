package com.fudgedy.schematicindex.export;

import com.fudgedy.schematicindex.SchematicIndexMod;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

// Drawn with AWT on the export thread, so a saved image never costs the render loop a frame
public final class Watermark
{
	private static final String SITE = "schematicindex.com";
	private static final String ICON = "/assets/schematicindex/textures/font/modtag.png";
	private static final int ICON_PIXELS = 9;
	private static final float OPACITY = 0.7F;
	private static final Color TEXT = new Color(0xFFFFFF);
	private static final Color SHADOW = new Color(0x101010);
	private static final int FOOTER_HEIGHT = 20;
	private static final int FOOTER_TEXT = 11;
	private static final Color FOOTER_BACKGROUND = new Color(0x1B1D1C);
	private static final Color FOOTER_TEXT_COLOR = new Color(0xD8DEDB);

	private static volatile @Nullable BufferedImage icon;

	private Watermark()
	{
	}

	// Text height scales with the image so a 1080p capture gets 28 px, as on the share page mock
	public static BufferedImage apply(BufferedImage source, String title, String designer) throws IOException
	{
		int width = source.getWidth();
		int height = source.getHeight();
		int textSize = Math.max(10, Math.round(height * 28.0F / 1080.0F));
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		try
		{
			g.drawImage(source, 0, 0, null);
			smooth(g);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACITY));
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textSize));
			FontMetrics metrics = g.getFontMetrics();
			int margin = textSize;
			int baseline = height - margin - metrics.getDescent();
			int siteWidth = metrics.stringWidth(SITE);
			int iconSize = ICON_PIXELS * Math.max(1, Math.round(textSize * 1.3F / ICON_PIXELS));
			int gap = Math.max(2, textSize / 3);
			int siteX = width - margin - siteWidth;
			int iconX = siteX - gap - iconSize;
			int iconY = baseline - metrics.getAscent() + (metrics.getAscent() + metrics.getDescent() - iconSize) / 2;
			// Pixel art, so only whole multiples of the 9 px master, never filtered
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(icon(), iconX, iconY, iconSize, iconSize, null);
			shadowed(g, SITE, siteX, baseline);

			String caption = designer.isBlank() ? title : title + " by " + designer;
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, textSize));
			shadowed(g, fit(g.getFontMetrics(), caption, iconX - margin * 2), margin, baseline);
		}
		finally
		{
			g.dispose();
		}

		return image;
	}

	public static BufferedImage footer(BufferedImage source, String text)
	{
		int width = source.getWidth();
		BufferedImage image = new BufferedImage(width, source.getHeight() + FOOTER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		try
		{
			g.drawImage(source, 0, 0, null);
			g.setColor(FOOTER_BACKGROUND);
			g.fillRect(0, source.getHeight(), width, FOOTER_HEIGHT);
			smooth(g);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, FOOTER_TEXT));
			FontMetrics metrics = g.getFontMetrics();
			String shown = fit(metrics, text, width - 12);
			int baseline = source.getHeight() + (FOOTER_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;
			g.setColor(FOOTER_TEXT_COLOR);
			g.drawString(shown, (width - metrics.stringWidth(shown)) / 2, baseline);
		}
		finally
		{
			g.dispose();
		}

		return image;
	}

	private static void shadowed(Graphics2D g, String text, int x, int y)
	{
		g.setColor(SHADOW);
		g.drawString(text, x + 1, y + 1);
		g.setColor(TEXT);
		g.drawString(text, x, y);
	}

	private static String fit(FontMetrics metrics, String text, int room)
	{
		if (metrics.stringWidth(text) <= room)
		{
			return text;
		}

		String ellipsis = "...";
		int end = text.length();

		while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > room)
		{
			end--;
		}

		return end == 0 ? "" : text.substring(0, end) + ellipsis;
	}

	private static void smooth(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	}

	private static BufferedImage icon() throws IOException
	{
		BufferedImage cached = icon;

		if (cached != null)
		{
			return cached;
		}

		try (InputStream in = Watermark.class.getResourceAsStream(ICON))
		{
			if (in == null)
			{
				throw new IOException("Missing " + ICON);
			}

			BufferedImage loaded = ImageIO.read(in);

			if (loaded == null)
			{
				throw new IOException("Unreadable " + ICON);
			}

			SchematicIndexMod.LOGGER.debug("Watermark icon loaded ({}x{})", loaded.getWidth(), loaded.getHeight());
			icon = loaded;
			return loaded;
		}
	}
}

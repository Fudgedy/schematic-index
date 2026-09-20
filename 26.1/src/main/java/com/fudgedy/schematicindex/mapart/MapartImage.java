package com.fudgedy.schematicindex.mapart;

import com.fudgedy.schematicindex.gui.ImageStore;
import com.mojang.blaze3d.platform.NativeImage;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// Immutable ARGB pixel buffer with the crop/scale step that turns any picture into a map-sized one
public final class MapartImage
{
	public static final int MAP_SIZE = 128;

	public final int width;
	public final int height;
	public final int[] argb;

	public MapartImage(int width, int height, int[] argb)
	{
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	public static MapartImage decode(Path file) throws Exception
	{
		try (InputStream input = Files.newInputStream(file); NativeImage image = ImageStore.readImage(input))
		{
			return new MapartImage(image.getWidth(), image.getHeight(), image.getPixels());
		}
	}

	// Straight off the raster, so a clipboard bitmap skips a PNG round trip
	public static MapartImage of(BufferedImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int[] argb = new int[width * height];
		image.getRGB(0, 0, width, height, argb, 0, width);
		return new MapartImage(width, height, argb);
	}

	// Crop keeps the aspect ratio by trimming the longer side, panning the window by the manual
	// offsets; Off stretches the whole picture
	public MapartImage fit(int targetWidth, int targetHeight, int crop, int cropX, int cropY)
	{
		int sourceX = 0;
		int sourceY = 0;
		int sourceWidth = this.width;
		int sourceHeight = this.height;

		if (crop != MapartOptions.CROP_OFF)
		{
			double target = targetWidth / (double) targetHeight;
			double actual = this.width / (double) this.height;
			int panX = crop == MapartOptions.CROP_MANUAL ? cropX : 50;
			int panY = crop == MapartOptions.CROP_MANUAL ? cropY : 50;

			if (actual > target)
			{
				sourceWidth = Math.max(1, (int) Math.round(this.height * target));
				sourceX = (this.width - sourceWidth) * panX / 100;
			}
			else if (actual < target)
			{
				sourceHeight = Math.max(1, (int) Math.round(this.width / target));
				sourceY = (this.height - sourceHeight) * panY / 100;
			}
		}

		return this.resample(sourceX, sourceY, sourceWidth, sourceHeight, targetWidth, targetHeight);
	}

	// Brightness scales, contrast pivots on mid grey, saturation blends with the pixel's luma; each
	// 0..200 with 100 leaving the channel alone
	public MapartImage adjust(int brightness, int contrast, int saturation)
	{
		if (brightness == MapartOptions.ADJUST_DEFAULT && contrast == MapartOptions.ADJUST_DEFAULT
				&& saturation == MapartOptions.ADJUST_DEFAULT)
		{
			return this;
		}

		float bright = brightness / 100.0F;
		float contrastScale = contrast / 100.0F;
		float sat = saturation / 100.0F;
		int[] out = new int[this.argb.length];

		for (int i = 0; i < out.length; i++)
		{
			int pixel = this.argb[i];
			float r = ((pixel >> 16) & 0xFF) * bright;
			float g = ((pixel >> 8) & 0xFF) * bright;
			float b = (pixel & 0xFF) * bright;

			r = (r - 128.0F) * contrastScale + 128.0F;
			g = (g - 128.0F) * contrastScale + 128.0F;
			b = (b - 128.0F) * contrastScale + 128.0F;

			float luma = 0.299F * r + 0.587F * g + 0.114F * b;
			r = luma + (r - luma) * sat;
			g = luma + (g - luma) * sat;
			b = luma + (b - luma) * sat;

			out[i] = (pixel & 0xFF000000) | (clamp(Math.round(r)) << 16) | (clamp(Math.round(g)) << 8) | clamp(Math.round(b));
		}

		return new MapartImage(this.width, this.height, out);
	}

	// Every pixel is laid over the colour and becomes opaque, so nothing downstream sees alpha
	public MapartImage composite(int backgroundRgb)
	{
		int[] out = new int[this.argb.length];
		int bgR = (backgroundRgb >> 16) & 0xFF;
		int bgG = (backgroundRgb >> 8) & 0xFF;
		int bgB = backgroundRgb & 0xFF;

		for (int i = 0; i < out.length; i++)
		{
			int pixel = this.argb[i];
			int alpha = pixel >>> 24;
			int r = (((pixel >> 16) & 0xFF) * alpha + bgR * (255 - alpha)) / 255;
			int g = (((pixel >> 8) & 0xFF) * alpha + bgG * (255 - alpha)) / 255;
			int b = ((pixel & 0xFF) * alpha + bgB * (255 - alpha)) / 255;
			out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
		}

		return new MapartImage(this.width, this.height, out);
	}

	// Box filter when shrinking so fine detail averages instead of aliasing, bilinear when growing
	private MapartImage resample(int sourceX, int sourceY, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight)
	{
		int[] out = new int[targetWidth * targetHeight];
		boolean shrinkX = sourceWidth > targetWidth;
		boolean shrinkY = sourceHeight > targetHeight;

		for (int ty = 0; ty < targetHeight; ty++)
		{
			double y0 = sourceY + ty * (double) sourceHeight / targetHeight;
			double y1 = sourceY + (ty + 1) * (double) sourceHeight / targetHeight;

			for (int tx = 0; tx < targetWidth; tx++)
			{
				double x0 = sourceX + tx * (double) sourceWidth / targetWidth;
				double x1 = sourceX + (tx + 1) * (double) sourceWidth / targetWidth;

				if (shrinkX || shrinkY)
				{
					out[ty * targetWidth + tx] = this.average(x0, y0, x1, y1);
				}
				else
				{
					out[ty * targetWidth + tx] = this.bilinear((x0 + x1) * 0.5 - 0.5, (y0 + y1) * 0.5 - 0.5);
				}
			}
		}

		return new MapartImage(targetWidth, targetHeight, out);
	}

	private int average(double x0, double y0, double x1, double y1)
	{
		int startX = (int) Math.floor(x0);
		int endX = Math.max(startX + 1, (int) Math.ceil(x1));
		int startY = (int) Math.floor(y0);
		int endY = Math.max(startY + 1, (int) Math.ceil(y1));

		double a = 0.0;
		double r = 0.0;
		double g = 0.0;
		double b = 0.0;
		double weightSum = 0.0;

		for (int y = startY; y < endY; y++)
		{
			double wy = Math.min(y + 1, y1) - Math.max(y, y0);

			if (wy <= 0.0 || y < 0 || y >= this.height)
			{
				continue;
			}

			for (int x = startX; x < endX; x++)
			{
				double wx = Math.min(x + 1, x1) - Math.max(x, x0);

				if (wx <= 0.0 || x < 0 || x >= this.width)
				{
					continue;
				}

				double w = wx * wy;
				int pixel = this.argb[y * this.width + x];
				int alpha = pixel >>> 24;
				// Premultiplied so transparent pixels do not drag colour towards their hidden RGB
				a += alpha * w;
				r += ((pixel >> 16) & 0xFF) * alpha * w;
				g += ((pixel >> 8) & 0xFF) * alpha * w;
				b += (pixel & 0xFF) * alpha * w;
				weightSum += w;
			}
		}

		if (weightSum <= 0.0 || a <= 0.0)
		{
			return 0;
		}

		int outA = clamp((int) Math.round(a / weightSum));
		int outR = clamp((int) Math.round(r / a));
		int outG = clamp((int) Math.round(g / a));
		int outB = clamp((int) Math.round(b / a));
		return (outA << 24) | (outR << 16) | (outG << 8) | outB;
	}

	private int bilinear(double x, double y)
	{
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		double fx = x - x0;
		double fy = y - y0;

		int p00 = this.pixelClamped(x0, y0);
		int p10 = this.pixelClamped(x0 + 1, y0);
		int p01 = this.pixelClamped(x0, y0 + 1);
		int p11 = this.pixelClamped(x0 + 1, y0 + 1);

		int out = 0;

		for (int shift = 0; shift <= 24; shift += 8)
		{
			double top = ((p00 >>> shift) & 0xFF) * (1.0 - fx) + ((p10 >>> shift) & 0xFF) * fx;
			double bottom = ((p01 >>> shift) & 0xFF) * (1.0 - fx) + ((p11 >>> shift) & 0xFF) * fx;
			out |= clamp((int) Math.round(top * (1.0 - fy) + bottom * fy)) << shift;
		}

		return out;
	}

	private int pixelClamped(int x, int y)
	{
		int cx = Math.max(0, Math.min(this.width - 1, x));
		int cy = Math.max(0, Math.min(this.height - 1, y));
		return this.argb[cy * this.width + cx];
	}

	private static int clamp(int value)
	{
		return Math.max(0, Math.min(255, value));
	}
}

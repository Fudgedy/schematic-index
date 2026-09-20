package com.fudgedy.schematicindex.mapart;

// The published error-diffusion kernels and ordered threshold matrices the Dithering dropdown offers
public final class MapartDither
{
	// Each tap is {dx, dy, weight}; weights are divided by the divisor
	public record Kernel(int[][] taps, int divisor)
	{
	}

	// Threshold values are 0..cells-1 laid out row-major over a size x size tile
	public record Matrix(int size, int[] thresholds)
	{
	}

	private static final Kernel FLOYD_STEINBERG = new Kernel(new int[][]{
			{1, 0, 7}, {-1, 1, 3}, {0, 1, 5}, {1, 1, 1}}, 16);
	private static final Kernel JARVIS_JUDICE_NINKE = new Kernel(new int[][]{
			{1, 0, 7}, {2, 0, 5},
			{-2, 1, 3}, {-1, 1, 5}, {0, 1, 7}, {1, 1, 5}, {2, 1, 3},
			{-2, 2, 1}, {-1, 2, 3}, {0, 2, 5}, {1, 2, 3}, {2, 2, 1}}, 48);
	private static final Kernel BURKES = new Kernel(new int[][]{
			{1, 0, 8}, {2, 0, 4},
			{-2, 1, 2}, {-1, 1, 4}, {0, 1, 8}, {1, 1, 4}, {2, 1, 2}}, 32);
	private static final Kernel SIERRA_LITE = new Kernel(new int[][]{
			{1, 0, 2}, {-1, 1, 1}, {0, 1, 1}}, 4);
	private static final Kernel STUCKI = new Kernel(new int[][]{
			{1, 0, 8}, {2, 0, 4},
			{-2, 1, 2}, {-1, 1, 4}, {0, 1, 8}, {1, 1, 4}, {2, 1, 2},
			{-2, 2, 1}, {-1, 2, 2}, {0, 2, 4}, {1, 2, 2}, {2, 2, 1}}, 42);
	// Atkinson deliberately spreads only six eighths of the error, which is what gives it its contrast
	private static final Kernel ATKINSON = new Kernel(new int[][]{
			{1, 0, 1}, {2, 0, 1}, {-1, 1, 1}, {0, 1, 1}, {1, 1, 1}, {0, 2, 1}}, 8);

	private static final Matrix BAYER_4 = new Matrix(4, new int[]{
			0, 8, 2, 10,
			12, 4, 14, 6,
			3, 11, 1, 9,
			15, 7, 13, 5});
	private static final Matrix BAYER_2 = new Matrix(2, new int[]{0, 2, 3, 1});
	private static final Matrix ORDERED_3 = new Matrix(3, new int[]{0, 7, 3, 6, 5, 2, 4, 1, 8});

	private MapartDither()
	{
	}

	public static Kernel kernel(int dithering)
	{
		return switch (dithering)
		{
			case MapartOptions.DITHER_FLOYD_STEINBERG -> FLOYD_STEINBERG;
			case MapartOptions.DITHER_MIN_AVG_ERR -> JARVIS_JUDICE_NINKE;
			case MapartOptions.DITHER_BURKES -> BURKES;
			case MapartOptions.DITHER_SIERRA_LITE -> SIERRA_LITE;
			case MapartOptions.DITHER_STUCKI -> STUCKI;
			case MapartOptions.DITHER_ATKINSON -> ATKINSON;
			default -> null;
		};
	}

	public static Matrix matrix(int dithering)
	{
		return switch (dithering)
		{
			case MapartOptions.DITHER_BAYER_4 -> BAYER_4;
			case MapartOptions.DITHER_BAYER_2 -> BAYER_2;
			case MapartOptions.DITHER_ORDERED_3 -> ORDERED_3;
			default -> null;
		};
	}
}

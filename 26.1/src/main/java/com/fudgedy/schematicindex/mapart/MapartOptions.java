package com.fudgedy.schematicindex.mapart;

// Everything the tab lets the user change; the tab edits a copy and hands it to each conversion job
public final class MapartOptions
{
	public static final int CROP_OFF = 0;
	public static final int CROP_CENTER = 1;
	public static final int CROP_MANUAL = 2;
	public static final String[] CROP_LABELS = {"Off", "Center", "Manual"};

	public static final int STAIRCASE_OFF = 0;
	public static final int STAIRCASE_CLASSIC = 1;
	public static final int STAIRCASE_VALLEY = 2;
	public static final int STAIRCASE_FULL_DARK = 3;
	public static final int STAIRCASE_FULL_LIGHT = 4;
	public static final int STAIRCASE_FULL_UNOBTAINABLE = 5;
	public static final String[] STAIRCASE_LABELS = {"Off (flat)", "Classic (3D)", "Valley (3D, lower build)",
			"Dark shades only", "Light shades only", "Unobtainable shades (preview)"};

	public static final int SUPPORT_NONE = 0;
	public static final int SUPPORT_IMPORTANT = 1;
	public static final int SUPPORT_ALL = 2;
	public static final int SUPPORT_ALL_DOUBLE = 3;
	public static final String SUPPORT_BLOCK = MapPalette.SUPPORT_BLOCK;
	public static final String[] SUPPORT_LABELS = {"None", "Important blocks only", "All blocks",
			"All blocks, doubled"};

	public static final int DITHER_NONE = 0;
	public static final int DITHER_FLOYD_STEINBERG = 1;
	public static final int DITHER_BAYER_4 = 2;
	public static final int DITHER_BAYER_2 = 3;
	public static final int DITHER_ORDERED_3 = 4;
	public static final int DITHER_MIN_AVG_ERR = 5;
	public static final int DITHER_BURKES = 6;
	public static final int DITHER_SIERRA_LITE = 7;
	public static final int DITHER_STUCKI = 8;
	public static final int DITHER_ATKINSON = 9;
	public static final String[] DITHER_LABELS = {"None", "Floyd-Steinberg", "Bayer (4x4)", "Bayer (2x2)", "Ordered (3x3)",
			"Min. average error", "Burkes", "Sierra-Lite", "Stucki", "Atkinson"};

	public static final int BACKGROUND_OFF = 0;
	public static final int BACKGROUND_DITHERED = 1;
	public static final int BACKGROUND_SMOOTH = 2;
	public static final String[] BACKGROUND_LABELS = {"Off", "On (dithered)", "On (smooth)"};
	public static final int DEFAULT_BACKGROUND = 0x151515;

	public static final int MIN_MAPS = 1;
	public static final int MAX_MAPS = 16;
	public static final int ADJUST_MIN = 0;
	public static final int ADJUST_MAX = 200;
	public static final int ADJUST_DEFAULT = 100;

	public int mapsX = 1;
	public int mapsY = 1;
	public int crop = CROP_CENTER;
	// Manual crop pans the window across the trimmed axis, 0 = start edge, 100 = end edge
	public int cropX = 50;
	public int cropY = 50;
	public int staircase = STAIRCASE_CLASSIC;
	public int support = SUPPORT_IMPORTANT;
	public boolean betterColor = true;
	public int dithering = DITHER_FLOYD_STEINBERG;
	public boolean preprocess;
	public int brightness = ADJUST_DEFAULT;
	public int contrast = ADJUST_DEFAULT;
	public int saturation = ADJUST_DEFAULT;
	public int background = BACKGROUND_OFF;
	public int backgroundColor = DEFAULT_BACKGROUND;
	public boolean noobline = true;
	public final boolean[] enabled = new boolean[MapPalette.GROUPS];
	public final int[] blockChoice = new int[MapPalette.GROUPS];

	public MapartOptions()
	{
		for (int id = 0; id < MapPalette.GROUPS; id++)
		{
			this.enabled[id] = MapPalette.enabledByDefault(id);
		}
	}

	public MapartOptions copy()
	{
		MapartOptions out = new MapartOptions();
		out.mapsX = this.mapsX;
		out.mapsY = this.mapsY;
		out.crop = this.crop;
		out.cropX = this.cropX;
		out.cropY = this.cropY;
		out.staircase = this.staircase;
		out.support = this.support;
		out.betterColor = this.betterColor;
		out.dithering = this.dithering;
		out.preprocess = this.preprocess;
		out.brightness = this.brightness;
		out.contrast = this.contrast;
		out.saturation = this.saturation;
		out.background = this.background;
		out.backgroundColor = this.backgroundColor;
		out.noobline = this.noobline;
		System.arraycopy(this.enabled, 0, out.enabled, 0, MapPalette.GROUPS);
		System.arraycopy(this.blockChoice, 0, out.blockChoice, 0, MapPalette.GROUPS);
		return out;
	}

	public boolean isStaircase()
	{
		return this.staircase != STAIRCASE_OFF && this.staircase != STAIRCASE_FULL_UNOBTAINABLE;
	}

	public boolean isErrorDiffusion()
	{
		return this.dithering == DITHER_FLOYD_STEINBERG || this.dithering >= DITHER_MIN_AVG_ERR;
	}

	public String blockFor(int id)
	{
		MapPalette.Group group = MapPalette.group(id);
		return group == null ? null : group.block(this.blockChoice[id]);
	}

	public boolean supportFor(int id)
	{
		MapPalette.Group group = MapPalette.group(id);
		return group != null && group.supportFor(this.blockChoice[id]);
	}
}

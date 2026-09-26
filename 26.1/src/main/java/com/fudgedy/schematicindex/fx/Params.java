package com.fudgedy.schematicindex.fx;

import com.fudgedy.schematicindex.catalogue.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// One layer's tunables as the server sent them; every key falls back to the built-in look, so a definition only
// names what it changes
public final class Params
{
	public static final Params NONE = new Params(null);
	// Served values are clamped into ranges every kind can draw, so a typo on the server can never divide by zero,
	// loop without end or allocate without bound on every client
	private static final double[] TIMING = {20, 60_000};
	private static final double[] UNIT = {0, 1};
	private static final Map<String, double[]> RANGES = Map.ofEntries(
			Map.entry("periodMs", TIMING), Map.entry("sweepMs", TIMING), Map.entry("cycleMs", TIMING),
			Map.entry("currentMs", TIMING), Map.entry("stepMs", TIMING),
			Map.entry("scale", new double[] {0.1, 10}), Map.entry("slant", new double[] {-4, 4}),
			Map.entry("curve", new double[] {0.1, 5}), Map.entry("glintEvery", new double[] {0, 50}),
			Map.entry("snowRows", new double[] {-1, 7}), Map.entry("starEvery", new double[] {1, 200}),
			Map.entry("sag", new double[] {0, 3}),
			Map.entry("edge", UNIT), Map.entry("glint", UNIT), Map.entry("darkness", UNIT), Map.entry("ring", UNIT),
			Map.entry("keepColours", UNIT), Map.entry("pool", UNIT), Map.entry("bloom", UNIT),
			Map.entry("saturation", UNIT), Map.entry("value", UNIT));
	private static final int MAX_COLOURS = 16;

	private final @Nullable JsonObject json;

	public Params(@Nullable JsonObject json)
	{
		this.json = json;
	}

	public double num(String key, double fallback)
	{
		double value = Json.doubleOf(this.json, key, fallback);

		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return fallback;
		}

		double[] range = RANGES.get(key);
		return range == null ? value : Math.max(range[0], Math.min(range[1], value));
	}

	public float[] color(String key, int fallback)
	{
		return FxMath.rgb(hex(Json.stringOf(this.json, key, null), fallback));
	}

	public float[][] colors(String key, int... fallback)
	{
		JsonArray list = Json.arrayOf(this.json, key);

		// Kinds index into their palettes by position, so a shorter list than the default would read past its end
		if (list.size() < fallback.length || list.size() > MAX_COLOURS)
		{
			return rgbAll(fallback);
		}

		float[][] out = new float[list.size()][];

		for (int i = 0; i < out.length; i++)
		{
			out[i] = FxMath.rgb(hex(Json.asString(list.get(i), null), 0xFFFFFF));
		}

		return out;
	}

	// Pairs such as the Glitch ghosts: an array of two-colour arrays
	public float[][][] pairs(String key, int[][] fallback)
	{
		JsonArray list = Json.arrayOf(this.json, key);

		if (list.isEmpty() || list.size() > MAX_COLOURS)
		{
			float[][][] out = new float[fallback.length][][];

			for (int i = 0; i < fallback.length; i++)
			{
				out[i] = rgbAll(fallback[i]);
			}

			return out;
		}

		float[][][] out = new float[list.size()][][];

		for (int i = 0; i < out.length; i++)
		{
			JsonElement pair = list.get(i);
			JsonArray colours = pair.isJsonArray() ? pair.getAsJsonArray() : new JsonArray();
			out[i] = new float[][] {
					FxMath.rgb(hex(colours.size() > 0 ? Json.asString(colours.get(0), null) : null, 0xFF2A6D)),
					FxMath.rgb(hex(colours.size() > 1 ? Json.asString(colours.get(1), null) : null, 0x05D9E8))};
		}

		return out;
	}

	private static float[][] rgbAll(int[] hexes)
	{
		float[][] out = new float[hexes.length][];

		for (int i = 0; i < hexes.length; i++)
		{
			out[i] = FxMath.rgb(hexes[i]);
		}

		return out;
	}

	private static int hex(@Nullable String text, int fallback)
	{
		if (text == null)
		{
			return fallback;
		}

		String digits = text.startsWith("#") ? text.substring(1) : text;

		try
		{
			return digits.length() == 6 ? Integer.parseInt(digits, 16) : fallback;
		}
		catch (NumberFormatException ignored)
		{
			return fallback;
		}
	}
}

package com.fudgedy.schematicindex.fx;

import org.jetbrains.annotations.Nullable;

// A pixel keeps its brightness and trades its hue for the name colour at its column, so hot cores stay bright
final class NameTint
{
	// Art this bright takes the name colour exactly, darker multiplies toward black and brighter lifts toward white
	private static final float PIVOT = 0.8F;
	// A dark name still lights the effect, so navy lava reads as lava rather than a smudge
	private static final float MIN_VALUE = 0.7F;
	// White, grey and black are no colour to follow; the fade lets a gradient through white blend rather than step
	private static final float LOW = 0.08F;
	private static final float HIGH = 0.3F;
	private static final int STRIDE = 4;

	private final float[] columns; // r, g, b 0..1, weight

	private NameTint(float[] columns)
	{
		this.columns = columns;
	}

	// Null when no column has a colour to follow, so a plain or white name draws the effect untouched
	static @Nullable NameTint of(NameMask mask, float @Nullable [][] stops)
	{
		if (stops == null)
		{
			return null;
		}

		float[] columns = new float[mask.width * STRIDE];
		boolean isTinted = false;

		for (int x = 0; x < mask.width; x++)
		{
			float[] name = FxMath.ramp(stops, mask.across(x));
			float max = Math.max(name[0], Math.max(name[1], name[2])) / 255.0F;
			float min = Math.min(name[0], Math.min(name[1], name[2])) / 255.0F;
			float weight = max > 0.0F ? smoothstep((max - min) / max) : 0.0F;

			if (weight <= 0.0F)
			{
				continue;
			}

			float lift = Math.max(max, MIN_VALUE) / max / 255.0F;
			int i = x * STRIDE;
			columns[i] = Math.min(1.0F, name[0] * lift);
			columns[i + 1] = Math.min(1.0F, name[1] * lift);
			columns[i + 2] = Math.min(1.0F, name[2] * lift);
			columns[i + 3] = weight;
			isTinted = true;
		}

		return isTinted ? new NameTint(columns) : null;
	}

	void apply(int x, float[] colour)
	{
		int i = x * STRIDE;
		float weight = this.columns[i + 3];

		if (weight <= 0.0F)
		{
			return;
		}

		float luma = (0.299F * colour[0] + 0.587F * colour[1] + 0.114F * colour[2]) / 255.0F;
		float dark = Math.min(luma, PIVOT) / PIVOT;
		float highlight = luma > PIVOT ? (luma - PIVOT) / (1.0F - PIVOT) : 0.0F;
		highlight *= highlight;

		for (int c = 0; c < 3; c++)
		{
			float hue = this.columns[i + c];
			float tinted = (hue * dark + (1.0F - hue) * highlight) * 255.0F;
			colour[c] += (tinted - colour[c]) * weight;
		}
	}

	private static float smoothstep(float saturation)
	{
		float t = FxMath.clamp01((saturation - LOW) / (HIGH - LOW));
		return t * t * (3.0F - 2.0F * t);
	}
}

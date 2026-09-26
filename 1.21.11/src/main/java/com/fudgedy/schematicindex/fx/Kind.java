package com.fudgedy.schematicindex.fx;

import org.jetbrains.annotations.Nullable;

// One effect building block. prepare runs once per name and returns whatever layout the IGN seeds; draw paints a
// frame. stops is the wearer's gradient, or null when they wear no colours
public interface Kind
{
	default @Nullable Object prepare(NameMask mask, Params params)
	{
		return null;
	}

	void draw(NameMask mask, Canvas canvas, long now, float @Nullable [][] stops, @Nullable Object state, Params params);

	static float[] worn(NameMask mask, float @Nullable [][] stops, int x, float[] fallback)
	{
		return stops != null ? FxMath.ramp(stops, mask.across(x)) : fallback;
	}
}

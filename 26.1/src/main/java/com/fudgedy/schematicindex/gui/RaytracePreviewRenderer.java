package com.fudgedy.schematicindex.gui;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

public final class RaytracePreviewRenderer implements PreviewRenderer
{
	@Override
	public String id()
	{
		return "raytrace";
	}

	@Override
	public boolean requiresRenderThread()
	{
		return false;
	}

	@Override
	public NativeImage render(SchematicPreview.Model model, SchematicPreview.View view, @Nullable NativeImage reuse)
	{
		return SchematicPreview.rasterise(model, view, reuse);
	}
}

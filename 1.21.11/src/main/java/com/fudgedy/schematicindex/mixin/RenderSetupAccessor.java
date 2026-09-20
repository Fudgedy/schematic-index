package com.fudgedy.schematicindex.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The package-private draw state RenderType.draw reads; the cached replay needs the same four fields
@Mixin(RenderSetup.class)
public interface RenderSetupAccessor
{
	@Accessor("pipeline")
	RenderPipeline schematicindex$pipeline();

	@Accessor("layeringTransform")
	LayeringTransform schematicindex$layeringTransform();

	@Accessor("textureTransform")
	TextureTransform schematicindex$textureTransform();

	@Accessor("outputTarget")
	OutputTarget schematicindex$outputTarget();
}

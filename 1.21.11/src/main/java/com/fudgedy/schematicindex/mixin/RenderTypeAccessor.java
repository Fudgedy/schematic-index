package com.fudgedy.schematicindex.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the private RenderSetup so the GPU preview can replay RenderType.draw against a cached buffer
@Mixin(RenderType.class)
public interface RenderTypeAccessor
{
	@Accessor("state")
	RenderSetup schematicindex$state();
}

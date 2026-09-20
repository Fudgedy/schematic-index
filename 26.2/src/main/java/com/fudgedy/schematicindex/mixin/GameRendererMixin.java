package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.gui.GpuPreviewRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Feature renderers ignore RenderSystem's output override and resolve through mainRenderTarget()
@Mixin(GameRenderer.class)
public class GameRendererMixin
{
	@Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
	private void schematicindex$redirectMainRenderTarget(CallbackInfoReturnable<RenderTarget> cir)
	{
		RenderTarget redirect = GpuPreviewRenderer.REDIRECT_MAIN_TARGET;

		if (redirect != null)
		{
			cir.setReturnValue(redirect);
		}
	}
}

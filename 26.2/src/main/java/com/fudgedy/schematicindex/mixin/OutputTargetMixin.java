package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.gui.GpuPreviewRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Block-entity render types resolve through several OutputTargets, so all of them need redirecting
@Mixin(OutputTarget.class)
public class OutputTargetMixin
{
	@Inject(method = "getRenderTarget", at = @At("HEAD"), cancellable = true)
	private void schematicindex$redirectOutputTarget(CallbackInfoReturnable<RenderTarget> cir)
	{
		RenderTarget redirect = GpuPreviewRenderer.REDIRECT_MAIN_TARGET;

		if (redirect != null)
		{
			cir.setReturnValue(redirect);
		}
	}
}

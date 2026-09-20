package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla's toast pass runs after the HUD and any screen, so riding it keeps toasts above the inventory and out of F1
@Mixin(ToastManager.class)
public class ToastManagerMixin
{
	// The Index screen draws them itself, layered under its hard modals
	@Inject(method = "render", at = @At("TAIL"))
	private void schematicindex$renderToasts(GuiGraphics graphics, CallbackInfo info)
	{
		if (Minecraft.getInstance().screen instanceof IndexScreen)
		{
			return;
		}

		Toasts.render(graphics);
	}
}

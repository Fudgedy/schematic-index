package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Toasts draw on every screen through the toast manager, but only the Index screen routes clicks to
// them; a press on a toast's action pill anywhere else is taken here before the screen sees it
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin
{
	@Shadow @Final private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true, require = 0)
	private void schematicindex$clickToast(long window, MouseButtonInfo info, int action, CallbackInfo ci)
	{
		if (action != 1 || info.button() != 0 || this.minecraft.gui.screen() == null || this.minecraft.gui.screen() instanceof IndexScreen)
		{
			return;
		}

		MouseHandler self = (MouseHandler) (Object) this;
		double x = self.getScaledXPos(this.minecraft.getWindow());
		double y = self.getScaledYPos(this.minecraft.getWindow());

		if (Toasts.mouseClicked(x, y))
		{
			ci.cancel();
		}
	}
}

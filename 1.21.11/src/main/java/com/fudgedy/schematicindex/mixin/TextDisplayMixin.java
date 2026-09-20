package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Servers that replace the vanilla nametag draw a text_display, which never passes through getNameTag, so
// the badge and cosmetics are re-applied to that text. Name-only selector: getText must remap on 1.21.11
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin
{
	@Inject(method = "getText", at = @At("RETURN"), cancellable = true, require = 0)
	private void schematicindex$decorateServerNametag(CallbackInfoReturnable<Component> cir)
	{
		Display.TextDisplay self = (Display.TextDisplay) (Object) this;

		if (!self.level().isClientSide())
		{
			return;
		}

		Component decorated = ModTags.decorateServerTag(self, cir.getReturnValue());

		if (decorated != null)
		{
			cir.setReturnValue(decorated);
		}
	}
}

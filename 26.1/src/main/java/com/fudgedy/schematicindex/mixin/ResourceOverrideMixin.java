package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.ModIcon;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// Serves a downloaded nametag-icon override for one texture id, reusing the original resource's pack
// and swapping only its stream. Any miss falls straight through to the bundled texture
@Mixin(MultiPackResourceManager.class)
public class ResourceOverrideMixin
{
	@Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
	private void schematicindex$overrideModIcon(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir)
	{
		if (!ModIcon.isOverride(id))
		{
			return;
		}

		Optional<Resource> original = cir.getReturnValue();

		if (original.isEmpty())
		{
			return;
		}

		cir.setReturnValue(Optional.of(new Resource(original.get().source(), ModIcon::overrideStream)));
	}
}

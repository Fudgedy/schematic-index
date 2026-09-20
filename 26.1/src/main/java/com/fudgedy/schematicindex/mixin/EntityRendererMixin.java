package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.ModTags;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Name-only selector so the refmap can remap getNameTag on the obfuscated 1.21.11 runtime; players inherit
// the base method. Low priority runs first, so another badge mod's icon lands outside this one
@Mixin(value = EntityRenderer.class, priority = 100)
public class EntityRendererMixin
{
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void schematicindex$badgeModUsers(Entity entity, CallbackInfoReturnable<Component> cir)
	{
		Component name = cir.getReturnValue();

		if (name != null && entity instanceof AbstractClientPlayer player
				&& (ModTags.isModUser(player.getUUID()) || ModTags.isLocalPreview(player.getUUID())))
		{
			// Rebuilt from the bare name: the returned display name already carries the team prefix/suffix
			cir.setReturnValue(ModTags.decorate(player.getPlainTextName(), player.getTeam(), player.getUUID()));
		}
	}
}

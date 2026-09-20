package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.Settings;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Vanilla never shows the local player their own nametag; forced in third person so a wearer sees their
// cosmetics, yielding whenever the server draws its own tag or vanilla would hide one, so the two never overlap
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRenderMixin
{
	// Name only: an obfuscated runtime with no refmap cannot remap a Mojang descriptor. require = 0 so a
	// missed match costs the own-name preview rather than crashing the game at launch
	@Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true, require = 0)
	private void schematicindex$showOwnName(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir)
	{
		Minecraft mc = Minecraft.getInstance();

		if (entity != mc.player || !Settings.ownNametag() || mc.options.getCameraType() == CameraType.FIRST_PERSON)
		{
			return;
		}

		if (mc.gui.hud.isHidden() || entity.isVehicle() || hasServerNametag(entity))
		{
			return;
		}

		cir.setReturnValue(true);
	}

	// Servers hide vanilla tags through team nametagVisibility and draw a text_display riding the player
	// instead; vanilla skips the team switch for the local player, so it is mirrored here
	private static boolean hasServerNametag(LivingEntity entity)
	{
		Team team = entity.getTeam();

		if (team != null && team.getNameTagVisibility() != Team.Visibility.ALWAYS)
		{
			return true;
		}

		for (Entity passenger : entity.getPassengers())
		{
			if (passenger instanceof Display.TextDisplay)
			{
				return true;
			}
		}

		return false;
	}
}

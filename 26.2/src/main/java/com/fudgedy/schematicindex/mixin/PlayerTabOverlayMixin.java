package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.Settings;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

// The tab list builds each row's name itself, so mod users get the same badge, tag and colours there as above
// their heads. Name-only selector: getNameForDisplay must remap on 1.21.11
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin
{
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void schematicindex$styleTabNames(PlayerInfo info, CallbackInfoReturnable<Component> cir)
	{
		UUID id = info.getProfile().id();

		if (!Settings.tabNames() || !(ModTags.isModUser(id) || ModTags.isLocalPreview(id)))
		{
			return;
		}

		// A server-set display name keeps its own prefixes, so the name is restyled inside it rather than rebuilt
		if (info.getTabListDisplayName() != null)
		{
			Component decorated = ModTags.decorateNamesIn(cir.getReturnValue());

			if (decorated != null)
			{
				cir.setReturnValue(decorated);
			}

			return;
		}

		cir.setReturnValue(ModTags.decorateShown(cir.getReturnValue(), info.getProfile().name(), info.getTeam(), id));
	}
}

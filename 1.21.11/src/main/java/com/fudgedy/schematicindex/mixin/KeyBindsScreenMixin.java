package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.Keybinds;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// addOptions() is where the screen builds its KeyBindsList, which sorts the mappings by category
// order right then; putting the mod's category first at that moment beats any mod that registered after init
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin
{
	@Inject(method = "addOptions", at = @At("HEAD"))
	private void schematicindex$categoryFirst(CallbackInfo info)
	{
		Keybinds.ensureCategoryFirst();
	}
}

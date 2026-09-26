package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.DailyReminder;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The title screen is the first moment after launch where a verify and a clickable toast both make sense
@Mixin(TitleScreen.class)
public class TitleScreenMixin
{
	@Inject(method = "init", at = @At("TAIL"))
	private void schematicindex$dailyReminder(CallbackInfo info)
	{
		try
		{
			DailyReminder.onTitleScreen();
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.debug("Daily reminder failed to start", e);
		}
	}
}

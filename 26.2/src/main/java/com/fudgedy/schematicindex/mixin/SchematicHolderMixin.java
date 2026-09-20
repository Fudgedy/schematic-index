package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.LoadedCache;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SchematicHolder.class)
public class SchematicHolderMixin
{
	@Inject(method = "removeSchematic", at = @At("RETURN"), remap = false)
	private void schematicindex$onRemove(LitematicaSchematic schematic, CallbackInfoReturnable<Boolean> cir)
	{
		if (Boolean.TRUE.equals(cir.getReturnValue()))
		{
			LoadedCache.onUnloaded(schematic);
		}
	}
}

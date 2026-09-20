package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.Keybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Without Fabric API nothing appends to the key mapping array vanilla builds in its constructor, so the
// mappings are added just before load() reads options.txt, which gives them saved keys and a Controls row
@Mixin(Options.class)
public abstract class OptionsMixin
{
	@Shadow
	@Final
	@Mutable
	public KeyMapping[] keyMappings;

	@Inject(method = "load", at = @At("HEAD"))
	private void schematicindex$addKeyMappings(CallbackInfo info)
	{
		for (KeyMapping mapping : Keybinds.mappings())
		{
			if (!ArrayUtils.contains(this.keyMappings, mapping))
			{
				this.keyMappings = ArrayUtils.add(this.keyMappings, mapping);
			}
		}
	}
}

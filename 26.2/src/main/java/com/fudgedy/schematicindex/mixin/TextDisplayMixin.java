package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Servers that replace the vanilla nametag draw a text_display, which never passes through getNameTag, so
// the badge and cosmetics are re-applied to that text. Name-only selectors: they must remap on 1.21.11
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin
{
	@Shadow
	private Display.TextDisplay.TextRenderState textRenderState;

	@Shadow
	private Display.TextDisplay.CachedInfo clientDisplayCache;

	@Unique
	private int schematicindex$generation;

	@Shadow
	private Display.TextDisplay.TextRenderState createFreshTextRenderState()
	{
		throw new AssertionError();
	}

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

	// Vanilla reads the text once per server update and keeps it, so a static nametag spawned before the
	// peers poll named its player would stay bare for good; a new room answer or loadout rebuilds it
	@Inject(method = "textRenderState", at = @At("HEAD"), require = 0)
	private void schematicindex$refreshDecoration(CallbackInfoReturnable<Display.TextDisplay.TextRenderState> cir)
	{
		int generation = ModTags.generation();

		if (this.textRenderState == null || generation == this.schematicindex$generation)
		{
			return;
		}

		this.schematicindex$generation = generation;
		this.textRenderState = this.createFreshTextRenderState();
		this.clientDisplayCache = null;
	}
}

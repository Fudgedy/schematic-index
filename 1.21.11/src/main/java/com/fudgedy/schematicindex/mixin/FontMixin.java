package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.fx.EffectGlyphs;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Name-only selectors so the refmap can remap them on the obfuscated 1.21.11 runtime. Both drawing and measuring
// ask getGlyphSource for a font's glyphs, so the effect glyphs lay out at the name's own width
@Mixin(Font.class)
public abstract class FontMixin
{
	@Shadow
	@Final
	@Mutable
	private StringSplitter splitter;

	@Shadow
	private GlyphSource getGlyphSource(FontDescription font)
	{
		throw new AssertionError();
	}

	@Inject(method = "getGlyphSource", at = @At("HEAD"), cancellable = true)
	private void schematicindex$effectGlyphs(FontDescription font, CallbackInfoReturnable<GlyphSource> cir)
	{
		if (EffectGlyphs.FONT.equals(font))
		{
			cir.setReturnValue(EffectGlyphs.SOURCE);
		}
	}

	// Mods that flatten a nametag to a legacy string (Essential places its icon this way) measure every glyph in
	// the default font; the mod's own private glyphs are measured in their home font so those widths stay true.
	// Measuring only: drawing still goes through the component's own fonts
	@Inject(method = "<init>", at = @At("RETURN"))
	private void schematicindex$measureOwnGlyphs(CallbackInfo ci)
	{
		this.splitter = new StringSplitter((codepoint, style) -> this.getGlyphSource(homeFont(codepoint, style.getFont()))
				.getGlyph(codepoint).info().getAdvance(style.isBold()));
	}

	private static FontDescription homeFont(int codepoint, FontDescription font)
	{
		if (!FontDescription.DEFAULT.equals(font))
		{
			return font;
		}

		if (EffectGlyphs.owns(codepoint))
		{
			return EffectGlyphs.FONT;
		}

		return ModTags.owns(codepoint) ? ModTags.FONT : font;
	}
}

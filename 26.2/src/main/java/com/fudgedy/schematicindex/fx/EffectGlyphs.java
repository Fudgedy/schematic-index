package com.fudgedy.schematicindex.fx;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A worn effect becomes one glyph as wide as the name, drawn from a small texture repainted as the effect animates.
// Vanilla then renders it wherever text goes (nametags see-through and all, chat, tab, screens) like any other glyph
public final class EffectGlyphs
{
	public static final FontDescription FONT = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "name_fx"));
	public static final GlyphSource SOURCE = new Source();
	// Supplementary private use plane, so a slot can never collide with a real character in a name
	private static final int FIRST = 0xF0000;
	private static final int CAPACITY = 0xFFFD;
	private static final long FRAME_MS = 16L;
	private static final long IDLE_MS = 60_000L;
	private static final long SWEEP_MS = 10_000L;
	private static final Logger LOGGER = LoggerFactory.getLogger("SchematicIndex");
	private static final BakedGlyph EMPTY = new BakedGlyph()
	{
		@Override
		public GlyphInfo info()
		{
			return GlyphInfo.simple(0.0F);
		}

		@Override
		public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor, Style style,
				float boldOffset, float shadowOffset)
		{
			return null;
		}
	};

	private static final Map<String, Slot> BY_KEY = new HashMap<>();
	private static final List<Slot> BY_INDEX = new ArrayList<>();
	private static long swept;

	private EffectGlyphs()
	{
	}

	// One StringBuilder pass rather than the concatenation plus Arrays.toString this replaced, called every
	// frame per rendered name
	private static String keyOf(String name, String effect, int[] stops, boolean inline)
	{
		StringBuilder key = new StringBuilder(name.length() + effect.length() + stops.length * 8 + 8)
				.append(name).append('\u0000').append(effect).append('\u0000').append('[');

		for (int i = 0; i < stops.length; i++)
		{
			if (i > 0)
			{
				key.append(", ");
			}

			key.append(stops[i]);
		}

		return key.append(']').append('\u0000').append(inline).toString();
	}

	public static boolean owns(int codepoint)
	{
		return codepoint >= FIRST && codepoint < FIRST + CAPACITY;
	}

	// The one-character string that draws this name in this effect, or null to fall back to plain coloured text
	public static synchronized @Nullable String glyphFor(String name, String effect, int[] stops, boolean inline)
	{
		if (!Effects.has(effect))
		{
			return null;
		}

		String key = keyOf(name, effect, stops, inline);
		Slot slot = BY_KEY.get(key);

		if (slot == null)
		{
			NameMask mask = NameMask.of(name);

			if (mask == null || BY_INDEX.size() >= CAPACITY)
			{
				return null;
			}

			slot = new Slot(BY_INDEX.size(), mask, effect, stops, inline);
			BY_KEY.put(key, slot);
			BY_INDEX.add(slot);
		}

		return Character.toString(FIRST + slot.index);
	}

	// A new or retuned effect set re-prepares every name's layout on its next frame
	static synchronized void invalidate()
	{
		for (Slot slot : BY_INDEX)
		{
			slot.states = null;
			slot.broken = false;
		}
	}

	private static synchronized BakedGlyph glyphAt(int codepoint)
	{
		int index = codepoint - FIRST;
		return index >= 0 && index < BY_INDEX.size() ? BY_INDEX.get(index).glyph : EMPTY;
	}

	// Textures of names nobody has drawn for a minute are freed; the slot keeps its key and repaints on demand
	private static synchronized void sweep(long now)
	{
		if (now - swept < SWEEP_MS)
		{
			return;
		}

		swept = now;

		for (Slot slot : BY_INDEX)
		{
			if (slot.texture != null && now - slot.used > IDLE_MS)
			{
				Minecraft.getInstance().getTextureManager().release(slot.id);
				slot.texture = null;
			}
		}
	}

	private static float @Nullable [][] rgbStops(int[] stops)
	{
		if (stops.length == 0)
		{
			return null;
		}

		float[][] out = new float[stops.length][];

		for (int i = 0; i < stops.length; i++)
		{
			out[i] = FxMath.rgb(stops[i]);
		}

		return out;
	}

	private static final class Slot
	{
		final int index;
		final NameMask mask;
		final String effect;
		final float @Nullable [][] stops;
		final @Nullable NameTint tint;
		final boolean inline;
		final Identifier id;
		final GlyphRenderTypes types;
		final Canvas canvas;
		final BakedGlyph glyph;
		@Nullable DynamicTexture texture;
		Object @Nullable [] states;
		boolean broken;
		long painted;
		long used;

		Slot(int index, NameMask mask, String effect, int[] stops, boolean inline)
		{
			this.index = index;
			this.mask = mask;
			this.effect = effect;
			this.stops = rgbStops(stops);
			this.tint = NameTint.of(mask, this.stops);
			this.inline = inline;
			this.id = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "name_fx/" + index);
			this.types = GlyphRenderTypes.createForColorTexture(this.id);
			this.canvas = new Canvas(mask.width);
			GlyphInfo info = GlyphInfo.simple(mask.advance());
			this.glyph = new BakedGlyph()
			{
				@Override
				public GlyphInfo info()
				{
					return info;
				}

				@Override
				public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor, Style style,
						float boldOffset, float shadowOffset)
				{
					return Slot.this.ready() ? new EffectSprite(x, y, color, style, Slot.this.mask.width, Slot.this.height(),
							Slot.this.inline, Slot.this.types, Slot.this.texture) : null;
				}
			};
		}

		int height()
		{
			return this.inline ? Canvas.INLINE_HEIGHT : Canvas.HEIGHT;
		}

		// Texture work only happens on the render thread; a glyph laid out anywhere else draws on the next frame
		boolean ready()
		{
			if (!RenderSystem.isOnRenderThread())
			{
				return this.texture != null;
			}

			long now = Util.getMillis();
			this.used = now;
			sweep(now);

			if (this.texture == null)
			{
				this.texture = new DynamicTexture(() -> "schematicindex name effect", this.mask.width, this.height(), true);
				Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
				this.painted = 0L;
			}

			if (now - this.painted >= FRAME_MS)
			{
				this.paint(now);
			}

			return true;
		}

		// A served definition that trips a kind draws the name blank rather than taking the render thread down
		private void draw(Effect effect, long now)
		{
			boolean isTinted = effect.tints() && this.tint != null;
			// A tinted effect paints its own palette, not the name blended in, or the name colour would count twice
			float[][] stops = isTinted ? null : this.stops;

			try
			{
				if (this.states == null || this.states.length != effect.layers().size())
				{
					this.states = new Object[effect.layers().size()];

					for (int i = 0; i < this.states.length; i++)
					{
						Effect.Layer layer = effect.layers().get(i);
						this.states[i] = layer.kind().prepare(this.mask, layer.params());
					}
				}

				for (int i = 0; i < this.states.length; i++)
				{
					Effect.Layer layer = effect.layers().get(i);
					layer.kind().draw(this.mask, this.canvas, now, stops, this.states[i], layer.params());
				}

				if (isTinted)
				{
					this.canvas.tint(this.tint);
				}
			}
			catch (Throwable e)
			{
				LOGGER.warn("Failed to draw effect {} for {}", effect.id(), this.mask.name, e);
				this.broken = true;
				this.canvas.clear();
			}
		}

		private void paint(long now)
		{
			Effect effect = Effects.get(this.effect);
			this.painted = now;
			this.canvas.clear();

			if (effect != null && !this.broken)
			{
				this.draw(effect, now);
			}

			if (this.inline)
			{
				this.canvas.writeInlineTo(this.texture.getPixels(), this.mask);
			}
			else
			{
				this.canvas.writeTo(this.texture.getPixels());
			}

			this.texture.upload();
		}
	}

	private static final class Source implements GlyphSource
	{
		@Override
		public BakedGlyph getGlyph(int codepoint)
		{
			return glyphAt(codepoint);
		}

		@Override
		public BakedGlyph getRandomGlyph(RandomSource random, int width)
		{
			return EMPTY;
		}
	}
}

package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.fx.Effect;
import com.fudgedy.schematicindex.fx.EffectGlyphs;
import com.fudgedy.schematicindex.fx.Effects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The client's own nametag loadout, a gradient of up to three owned colours or a bought preset worn whole;
// slots hold owned colour ids rather than raw hex, so a refunded colour drops out of the loadout on its own
public final class Cosmetics
{
	public enum Mode
	{
		NONE,
		GRADIENT,
		PRESET
	}

	// UNPRICED falls back to the flat preset price, a served 0 is free; the window fields only come from the server list
	public record Preset(String name, int[] stops, int price, boolean limited, long untilMs, boolean vaulted)
	{
		public static final int UNPRICED = -1;

		public Preset(String name, int[] stops)
		{
			this(name, stops, UNPRICED, false, 0L, false);
		}
	}

	public record Worn(int[] stops, CosmeticTags.Tag tag, Set<String> effects)
	{
		// The one worn effect or null; taken in shop order should a peer's row ever carry two
		public String effect()
		{
			return effectIn(this.effects);
		}

		public boolean hasShine()
		{
			return this.effects.contains(SHINE);
		}

		public boolean hasFlow()
		{
			return this.effects.contains(FLOW);
		}
	}

	public static final int GRADIENT_SLOTS = 3;
	public static final String OBFUSCATED_FIRST = "obfuscated_first";
	public static final int EMPTY = -1;
	public static final String SHINE = "shine";
	public static final String FLOW = "flow";
	public static final String WAVE = "wave";
	public static final String PULSE = "pulse";
	public static final String NEON = "neon";
	public static final String FROZEN = "frozen";
	public static final String MOLTEN = "molten";
	public static final String TOXIC = "toxic";

	// The offline fallback; once /me/cosmetics answers, presets() is the server's list
	public static final List<Preset> PRESETS = List.of(
			new Preset("Emerald", new int[] {0x2A7A5B, 0xFFFFFF}),
			new Preset("Amethyst", new int[] {0x8B69CA, 0xE0C3FC}),
			new Preset("Sunset", new int[] {0xFF6B6B, 0xFFD93D}),
			new Preset("Ocean", new int[] {0x2E3192, 0x1BFFFF}),
			new Preset("Frost", new int[] {0x00C9FF, 0xFFFFFF}),
			new Preset("Fire", new int[] {0xFF0000, 0xFFA500, 0xFFFF00}),
			new Preset("Flamingo", new int[] {0xFC8EAC, 0xFFFFFF}),
			new Preset("Lunar", new int[] {0x555555, 0xFFFFFF}),
			new Preset("Midnight", new int[] {0x000000, 0x8A8A8A}));


	private static final int[] GRADIENT = new int[GRADIENT_SLOTS];
	private static final Set<String> EFFECTS = new HashSet<>();

	private static Mode mode = Mode.NONE;
	private static int[] presetStops = {};
	private static String presetName = "";

	static
	{
		Arrays.fill(GRADIENT, EMPTY);
	}

	private Cosmetics()
	{
	}

	public static Mode mode()
	{
		return mode;
	}

	public static void setMode(Mode value)
	{
		mode = value;
		store();
	}

	public static List<Preset> presets()
	{
		List<Preset> served = CosmeticColors.presets();
		return served.isEmpty() ? PRESETS : served;
	}

	public static void wearPreset(Preset preset)
	{
		presetStops = preset.stops().clone();
		presetName = preset.name();
		mode = Mode.PRESET;
		store();
	}

	public static int[] gradient()
	{
		return GRADIENT;
	}

	public static int gradientAt(int slot)
	{
		return slot >= 0 && slot < GRADIENT.length ? GRADIENT[slot] : EMPTY;
	}

	public static boolean isInGradient(int colorId)
	{
		for (int id : GRADIENT)
		{
			if (id == colorId)
			{
				return true;
			}
		}

		return false;
	}

	public static void clearGradientSlot(int slot)
	{
		if (slot >= 0 && slot < GRADIENT.length)
		{
			GRADIENT[slot] = EMPTY;
		}
		store();
	}

	public static void pushGradient(int colorId)
	{
		if (isInGradient(colorId))
		{
			return;
		}

		for (int i = 0; i < GRADIENT.length; i++)
		{
			if (GRADIENT[i] == EMPTY)
			{
				GRADIENT[i] = colorId;
				store();
				return;
			}
		}

		GRADIENT[0] = colorId;
		store();
	}

	public static void swapGradient(int from, int to)
	{
		if (from < 0 || to < 0 || from >= GRADIENT.length || to >= GRADIENT.length)
		{
			return;
		}

		int held = GRADIENT[from];
		GRADIENT[from] = GRADIENT[to];
		GRADIENT[to] = held;
		store();
	}

	public static String effect()
	{
		return effectIn(EFFECTS);
	}

	public static boolean hasShine()
	{
		return EFFECTS.contains(SHINE);
	}

	public static boolean hasFlow()
	{
		return EFFECTS.contains(FLOW);
	}

	public static Set<String> effects()
	{
		return Set.copyOf(EFFECTS);
	}

	// One effect at a time, which the server also enforces
	public static void setEffect(String id, boolean on)
	{
		if (on)
		{
			EFFECTS.clear();
			EFFECTS.add(id);
		}
		else
		{
			EFFECTS.remove(id);
		}

		store();
	}

	// Effects ride after a bar so a loadout saved by an older build still parses
	private static void store()
	{
		StringBuilder out = new StringBuilder(mode.name());

		if (mode == Mode.PRESET)
		{
			out.append(':').append(presetName);
		}
		else
		{
			for (int id : GRADIENT)
			{
				out.append(':').append(id);
			}
		}

		if (!EFFECTS.isEmpty())
		{
			out.append('|').append(String.join(",", EFFECTS));
		}

		Settings.setCosmeticLoadout(out.toString());
		CosmeticColors.publishLoadout();
	}

	public static void restore()
	{
		// Split rather than cut at the first bar: a loadout saved by a build that wrote a third segment still parses
		String[] segments = Settings.cosmeticLoadout().split("\\|", -1);
		restoreEffects(segments.length > 1 ? segments[1] : "");
		String[] parts = segments[0].split(":");

		if (parts.length >= 2 && parts[0].equals(Mode.PRESET.name()))
		{
			for (Preset preset : presets())
			{
				if (preset.name().equals(parts[1]))
				{
					wearPreset(preset);
					return;
				}
			}
		}
		else if (parts.length >= 2 && parts[0].equals(Mode.GRADIENT.name()))
		{
			for (int i = 0; i < GRADIENT.length && i + 1 < parts.length; i++)
			{
				GRADIENT[i] = parseId(parts[i + 1]);
			}

			mode = Mode.GRADIENT;
			pruneUnowned();
		}

		CosmeticColors.publishLoadout();
	}

	// An effect the account has lost is dropped silently rather than published and refused
	private static void restoreEffects(String saved)
	{
		EFFECTS.clear();

		for (String id : saved.split(","))
		{
			if (Effects.has(id) && CosmeticColors.ownsEffect(id))
			{
				EFFECTS.add(id);
			}
		}
	}

	private static int parseId(String value)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ignored)
		{
			return EMPTY;
		}
	}

	public static void pruneUnowned()
	{
		for (int i = 0; i < GRADIENT.length; i++)
		{
			if (GRADIENT[i] != EMPTY && !CosmeticColors.owns(GRADIENT[i]))
			{
				GRADIENT[i] = EMPTY;
			}
		}
	}

	public static int[] gradientStops()
	{
		int[] stops = new int[GRADIENT.length];
		int count = 0;

		for (int id : GRADIENT)
		{
			int hex = CosmeticColors.hexOf(id);

			if (hex >= 0)
			{
				stops[count++] = hex;
			}
		}

		return Arrays.copyOf(stops, count);
	}

	public static Component apply(String name)
	{
		return apply(name, Style.EMPTY, effect(), true);
	}

	// base carries whatever the surrounding text already styled the name with, so bold or italic survive. A name
	// inline in chat or the tab list is not floating and takes the one-line effect frame
	public static Component apply(String name, Style base, boolean floating)
	{
		return apply(name, base, effect(), floating);
	}

	public static Component apply(String name, Style base, String effect)
	{
		return apply(name, base, effect, true);
	}

	public static Component apply(String name, Style base, String effect, boolean floating)
	{
		return dressed(name, wornStops(), base, effect, floating);
	}

	public static Component preview(String name, int[] stops, Style base, boolean shine, boolean flow)
	{
		return preview(name, stops, base, effectOf(shine, flow));
	}

	public static Component preview(String name, int[] stops, Style base, String effect)
	{
		return dressed(name, stops, base, effect, true);
	}

	public static boolean isWorn()
	{
		return mode != Mode.NONE || CosmeticTags.equippedTag() != null || !EFFECTS.isEmpty();
	}

	// A tag's styling is authored by staff, so it never reads the player's palette
	public static Component tag()
	{
		CosmeticTags.Tag tag = CosmeticTags.equippedTag();
		return tag == null ? null : tagOf(tag);
	}

	public static Component tagOf(CosmeticTags.Tag tag)
	{
		String text = tag.bracketOpen() + tag.label() + tag.bracketClose();
		int[] stops = tag.gradient() ? tag.colors() : new int[] {tag.colors()[0]};

		if (tag.label().isEmpty() || !tag.styles().contains(OBFUSCATED_FIRST))
		{
			return colored(text, stops, styleOf(tag));
		}

		return scrambledAt(text, stops, styleOf(tag), tag.bracketOpen().codePointCount(0, tag.bracketOpen().length()));
	}

	// Glyph by glyph like colored(), with only the one at index obfuscated
	private static Component scrambledAt(String text, int[] stops, Style base, int index)
	{
		MutableComponent out = Component.empty();
		int[] glyphs = text.codePoints().toArray();

		for (int i = 0; i < glyphs.length; i++)
		{
			float t = glyphs.length == 1 ? 0.0F : (float) i / (glyphs.length - 1);
			Style style = base.withColor(TextColor.fromRgb(sample(stops, t))).withObfuscated(i == index || base.isObfuscated());
			out.append(Component.literal(Character.toString(glyphs[i])).withStyle(style));
		}

		return out;
	}

	private static Style styleOf(CosmeticTags.Tag tag)
	{
		Set<String> styles = tag.styles();
		return Style.EMPTY
				.withBold(styles.contains("bold"))
				.withItalic(styles.contains("italic"))
				.withUnderlined(styles.contains("underlined"))
				.withStrikethrough(styles.contains("strikethrough"))
				.withObfuscated(styles.contains("obfuscated"));
	}





	public static Component applyWorn(String name, Worn other)
	{
		return applyWorn(name, other, Style.EMPTY, true);
	}

	public static Component applyWorn(String name, Worn other, Style base, boolean floating)
	{
		return dressed(name, other.stops(), base, other.effect(), floating);
	}

	// An effect draws the whole name as one glyph laid out for this IGN; a name the font sheet cannot draw keeps
	// its plain colours. Chat and the tab list take the one-line frame so nothing spills onto neighbouring lines
	private static Component dressed(String name, int[] stops, Style base, String effect, boolean floating)
	{
		String glyph = effect == null || name.isEmpty() ? null : EffectGlyphs.glyphFor(name, effect, stops, !floating);

		if (glyph == null)
		{
			return colored(name, stops, base);
		}

		return Component.literal(glyph).withStyle(base.withFont(EffectGlyphs.FONT).withColor(TextColor.fromRgb(0xFFFFFF))
				.withBold(false).withItalic(false).withObfuscated(false));
	}






	public static int[] wornStops()
	{
		return mode == Mode.PRESET ? presetStops.clone() : mode == Mode.GRADIENT ? gradientStops() : new int[0];
	}



	private static Component colored(String text, int[] stops, Style base)
	{
		if (text.isEmpty() || stops.length == 0)
		{
			return Component.literal(text).withStyle(base);
		}

		if (stops.length == 1)
		{
			return Component.literal(text).withStyle(base.withColor(TextColor.fromRgb(stops[0])));
		}

		MutableComponent out = Component.empty();
		int[] glyphs = text.codePoints().toArray();

		// By code point: a tag's supplementary glyph split into lone surrogates would draw as two missing boxes
		for (int i = 0; i < glyphs.length; i++)
		{
			float t = glyphs.length == 1 ? 0.0F : (float) i / (glyphs.length - 1);
			out.append(Component.literal(Character.toString(glyphs[i])).withStyle(base.withColor(TextColor.fromRgb(sample(stops, t)))));
		}

		return out;
	}




	// Older callers pass the pair; shine wins, since only one effect is ever worn
	private static String effectOf(boolean shine, boolean flow)
	{
		return shine ? SHINE : flow ? FLOW : null;
	}

	private static String effectIn(Set<String> effects)
	{
		for (Effect effect : Effects.all())
		{
			if (effects.contains(effect.id()))
			{
				return effect.id();
			}
		}

		return null;
	}






	public static int sample(int[] stops, float t)
	{
		if (stops.length == 1)
		{
			return stops[0];
		}

		int segments = stops.length - 1;
		float scaled = t * segments;
		int index = Math.min((int) scaled, segments - 1);
		return lerp(stops[index], stops[index + 1], scaled - index);
	}


	private static int lerp(int a, int b, float t)
	{
		int red = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int green = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int blue = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (red << 16) | (green << 8) | blue;
	}

	public static int hsvToRgb(float hue, float saturation, float value)
	{
		float h = (hue % 1.0F + 1.0F) % 1.0F * 6.0F;
		int sector = (int) h;
		float f = h - sector;
		float p = value * (1.0F - saturation);
		float q = value * (1.0F - saturation * f);
		float t = value * (1.0F - saturation * (1.0F - f));

		float r;
		float g;
		float b;

		switch (sector)
		{
			case 0 -> { r = value; g = t; b = p; }
			case 1 -> { r = q; g = value; b = p; }
			case 2 -> { r = p; g = value; b = t; }
			case 3 -> { r = p; g = q; b = value; }
			case 4 -> { r = t; g = p; b = value; }
			default -> { r = value; g = p; b = q; }
		}

		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	public static float[] rgbToHsv(int rgb)
	{
		float r = ((rgb >> 16) & 0xFF) / 255.0F;
		float g = ((rgb >> 8) & 0xFF) / 255.0F;
		float b = (rgb & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;

		float hue = 0.0F;

		if (delta > 0.0F)
		{
			if (max == r)
			{
				hue = (g - b) / delta % 6.0F;
			}
			else if (max == g)
			{
				hue = (b - r) / delta + 2.0F;
			}
			else
			{
				hue = (r - g) / delta + 4.0F;
			}

			hue /= 6.0F;

			if (hue < 0.0F)
			{
				hue += 1.0F;
			}
		}

		return new float[] {hue, max == 0.0F ? 0.0F : delta / max, max};
	}
}

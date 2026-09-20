package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

public final class Theme
{
	public static final int ACCENT = 0xFF2A7A5B;
	public static final int ACCENT_PRESSED = 0xFF24684D;
	public static final int ACCENT_BRIGHT = 0xFF3FA87F;

	// The premium tab swaps the green accent for gold, kept at the green's muted saturation to match
	public static final int GOLD = 0xFFC9A24A;
	public static final int GOLD_BRIGHT = 0xFFE8C55E;
	public static final int ON_GOLD = 0xFF241C0A;

	public static final int DOWNLOAD_FILL = 0xFF6FD9AE;
	public static final int ON_ACCENT = 0xFFFFFFFF;

	// Anchored by the mod's green, all at similar saturation and brightness so they read as one set
	public static final int STAT_VIEWS = 0xFF54B98A;      // green (theme anchor)
	public static final int STAT_DOWNLOADS = 0xFF6E93E8;  // periwinkle blue
	public static final int STAT_LIKES = 0xFFE86E86;      // rose
	public static final int STAT_STARS = 0xFFE7B24A;      // amber / gold

	public static final int BACKDROP = 0xFF0F1114;
	public static final int SURFACE = 0xFF171A1E;
	public static final int SURFACE_CARD = 0xFF1E2227;
	public static final int SURFACE_ELEVATED = 0xFF23282E;
	public static final int HAIRLINE = 0xFF2C3238;

	public static final int TEXT = 0xFFF2F4F5;
	public static final int TEXT_MUTE = 0xFFA8B0B6;
	public static final int TEXT_ASH = 0xFF6E767C;

	public static final int SCRIM = 0x99000000;

	public static final int RAIL_TILE = 0xFF2A3037;
	public static final int RAIL_TILE_ACTIVE = 0xFF343B44;

	public static final int SKELETON = 0xFF262C33;
	public static final int SKELETON_SHINE = 0x14FFFFFF;

	public static final int RADIUS_PILL = 2;
	public static final int RADIUS_CARD = 3;
	public static final int RADIUS_MODAL = 4;

	private Theme()
	{
	}

	public static void roundedRect(GuiGraphics ctx, int x, int y, int width, int height, int radius, int color)
	{
		if (radius <= 0 || width < radius * 2 || height < radius * 2)
		{
			ctx.fill(x, y, x + width, y + height, color);
			return;
		}

		ctx.fill(x, y + radius, x + width, y + height - radius, color);

		for (int i = 0; i < radius; i++)
		{
			int inset = cornerInset(radius, i);
			ctx.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
			ctx.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
		}
	}

	public static void roundedOutline(GuiGraphics ctx, int x, int y, int width, int height, int radius, int color)
	{
		if (radius <= 0)
		{
			ctx.fill(x, y, x + width, y + 1, color);
			ctx.fill(x, y + height - 1, x + width, y + height, color);
			ctx.fill(x, y + 1, x + 1, y + height - 1, color);
			ctx.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
			return;
		}

		int topInset = cornerInset(radius, 0);
		ctx.fill(x + topInset, y, x + width - topInset, y + 1, color);
		ctx.fill(x + topInset, y + height - 1, x + width - topInset, y + height, color);
		ctx.fill(x, y + radius, x + 1, y + height - radius, color);
		ctx.fill(x + width - 1, y + radius, x + width, y + height - radius, color);

		// Each row runs out to where the row above began, so a large radius stays one unbroken ring where
		// the arc is nearly flat and the inset jumps several pixels between rows
		int previous = topInset + 1;

		for (int i = 0; i < radius; i++)
		{
			int inset = cornerInset(radius, i);
			int run = Math.max(1, previous - inset);
			ctx.fill(x + inset, y + i, x + inset + run, y + i + 1, color);
			ctx.fill(x + width - inset - run, y + i, x + width - inset, y + i + 1, color);
			ctx.fill(x + inset, y + height - i - 1, x + inset + run, y + height - i, color);
			ctx.fill(x + width - inset - run, y + height - i - 1, x + width - inset, y + height - i, color);
			previous = inset;
		}
	}

	public static void circleButton(GuiGraphics ctx, int x, int y, int size, int fill, int outline)
	{
		roundedRect(ctx, x, y, size, size, size / 2, fill);
		roundedOutline(ctx, x, y, size, size, size / 2, outline);
	}

	// An even box gets a two-pixel apex and four strands, an odd one a single pixel and five, so both centre exactly
	public static void chevronUp(GuiGraphics ctx, int x, int y, int size, int color)
	{
		boolean even = size % 2 == 0;
		int strands = even ? 4 : 5;
		int left = x + size / 2 - (even ? 1 : 0);
		int right = x + size / 2;
		int top = y + (size - strands) / 2;

		for (int i = 0; i < strands; i++)
		{
			ctx.fill(left - i, top + i, left - i + 1, top + i + 1, color);
			ctx.fill(right + i, top + i, right + i + 1, top + i + 1, color);
		}
	}

	private static int cornerInset(int radius, int row)
	{
		double dy = radius - row - 0.5D;
		double dx = Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
		return Math.max(0, radius - (int) Math.round(dx));
	}

	public static void text(GuiGraphics ctx, Font font, String value, int x, int y, int color)
	{
		ctx.drawString(font, value, x, y, color, false);
	}

	public static void text(GuiGraphics ctx, Font font, Component value, int x, int y, int color)
	{
		ctx.drawString(font, value, x, y, color, false);
	}

	public static void textScaled(GuiGraphics ctx, Font font, String value, int x, int y, float scale, int color)
	{
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x, (float) y);
		ctx.pose().scale(scale, scale);
		ctx.drawString(font, value, 0, 0, color, false);
		ctx.pose().popMatrix();
	}

	public static void textScaled(GuiGraphics ctx, Font font, Component value, int x, int y, float scale, int color)
	{
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x, (float) y);
		ctx.pose().scale(scale, scale);
		ctx.drawString(font, value, 0, 0, color, false);
		ctx.pose().popMatrix();
	}

	public static String bold(String value)
	{
		return "§l" + value;
	}

	// The Minecraft font has no kerning, so one pass over the glyph advances suffices
	public static String clip(Font font, String value, int maxWidth)
	{
		if (font.width(value) <= maxWidth)
		{
			return value;
		}

		String ellipsis = "...";
		int room = maxWidth - font.width(ellipsis);

		if (room <= 0)
		{
			return ellipsis;
		}

		StringBuilder out = new StringBuilder();
		int used = 0;

		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			int advance = font.width(String.valueOf(c));

			if (used + advance > room)
			{
				break;
			}

			used += advance;
			out.append(c);
		}

		return out + ellipsis;
	}

	public static String clipBold(Font font, String value, int maxWidth)
	{
		if (font.width(bold(value)) <= maxWidth)
		{
			return value;
		}

		String ellipsis = "...";
		int room = maxWidth - font.width(bold(ellipsis));

		if (room <= 0)
		{
			return "";
		}

		StringBuilder out = new StringBuilder();
		int used = 0;

		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			int advance = font.width(bold(String.valueOf(c)));

			if (used + advance > room)
			{
				break;
			}

			used += advance;
			out.append(c);
		}

		return out + ellipsis;
	}

	public static int boldFitCount(Font font, String value, int maxWidth)
	{
		int used = 0;

		for (int i = 0; i < value.length(); i++)
		{
			used += font.width(bold(String.valueOf(value.charAt(i))));

			if (used > maxWidth)
			{
				return i;
			}
		}

		return value.length();
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height)
	{
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	public static int lighten(int color, float amount)
	{
		int alpha = color >>> 24;
		int red = (color >> 16) & 0xFF;
		int green = (color >> 8) & 0xFF;
		int blue = color & 0xFF;
		red += Math.round((255 - red) * amount);
		green += Math.round((255 - green) * amount);
		blue += Math.round((255 - blue) * amount);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static final long HOVER_MS = 200L;
	private static final long PRESS_MS = 150L;
	private static final long POP_MS = 220L;
	public static final float HOVER_SCALE = 0.02F;
	public static final float PRESS_SCALE = 0.98F;
	
	public static final float POP_SCALE = 0.10F;

	private static final class Fx
	{
		float hover;
		long lastUpdate;
		long lastTouch;
		long pressAt = -1L;
		long popAt = -1L;
	}

	private static final Map<Object, Fx> BUTTON_FX = new IdentityHashMap<>();
	private static long lastSweep;

	// BUTTON_FX is keyed by Rects rebuilt on every resize, so dead entries would accumulate forever. A
	// live button touches its entry every frame; anything untouched past the longest animation is dead
	private static void sweep(long now)
	{
		if (now - lastSweep < POP_MS)
		{
			return;
		}

		lastSweep = now;
		BUTTON_FX.entrySet().removeIf(entry -> now - entry.getValue().lastTouch > POP_MS);
	}

	public static float buttonHover(Object key, boolean hovered)
	{
		long now = Minecraft.getInstance() == null ? 0L : System.currentTimeMillis();
		sweep(now);
		Fx fx = BUTTON_FX.get(key);

		if (fx == null)
		{
			fx = new Fx();
			fx.hover = hovered ? 1.0F : 0.0F;
			fx.lastUpdate = now;
			fx.lastTouch = now;
			BUTTON_FX.put(key, fx);
			return fx.hover;
		}

		float step = (now - fx.lastUpdate) / (float) HOVER_MS;
		fx.lastUpdate = now;
		fx.lastTouch = now;
		float target = hovered ? 1.0F : 0.0F;
		fx.hover = fx.hover < target
				? Math.min(target, fx.hover + step)
				: Math.max(target, fx.hover - step);
		return fx.hover;
	}

	public static void buttonPress(Object key)
	{
		Fx fx = BUTTON_FX.computeIfAbsent(key, k -> new Fx());
		long now = System.currentTimeMillis();
		fx.pressAt = now;
		fx.lastTouch = now;
	}

	public static float buttonScale(Object key, float base)
	{
		Fx fx = BUTTON_FX.get(key);

		if (fx == null || fx.pressAt < 0L)
		{
			return base;
		}

		long now = System.currentTimeMillis();
		fx.lastTouch = now;
		long age = now - fx.pressAt;

		if (age >= PRESS_MS)
		{
			fx.pressAt = -1L;
			return base;
		}

		float t = age / (float) PRESS_MS;
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return PRESS_SCALE + (base - PRESS_SCALE) * eased;
	}

	public static void buttonPop(Object key)
	{
		Fx fx = BUTTON_FX.computeIfAbsent(key, k -> new Fx());
		long now = System.currentTimeMillis();
		fx.popAt = now;
		fx.lastTouch = now;
	}

	public static float popScale(Object key, float base)
	{
		Fx fx = BUTTON_FX.get(key);

		if (fx == null || fx.popAt < 0L)
		{
			return base;
		}

		long now = System.currentTimeMillis();
		fx.lastTouch = now;
		long age = now - fx.popAt;

		if (age >= POP_MS)
		{
			fx.popAt = -1L;
			return base;
		}

		float t = age / (float) POP_MS;
		return base + POP_SCALE * (float) Math.sin(t * Math.PI);
	}

	public static void pushScale(GuiGraphics ctx, int x, int y, int width, int height, float scale)
	{
		float centreX = x + width / 2.0F;
		float centreY = y + height / 2.0F;
		ctx.pose().pushMatrix();
		ctx.pose().translate(centreX, centreY);
		ctx.pose().scale(scale, scale);
		ctx.pose().translate(-centreX, -centreY);
	}

	public static void pushRotate(GuiGraphics ctx, int x, int y, int width, int height, float radians)
	{
		float centreX = x + width / 2.0F;
		float centreY = y + height / 2.0F;
		ctx.pose().pushMatrix();
		ctx.pose().translate(centreX, centreY);
		ctx.pose().rotate(radians);
		ctx.pose().translate(-centreX, -centreY);
	}

	public static void pop(GuiGraphics ctx)
	{
		ctx.pose().popMatrix();
	}

	// Items always render at 16px; scale down so a reward icon matches the height of its label text
	public static void itemScaled(GuiGraphics ctx, ItemStack stack, int x, int y, float scale)
	{
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x, (float) y);
		ctx.pose().scale(scale, scale);
		ctx.renderItem(stack, 0, 0);
		ctx.pose().popMatrix();
	}

	public static void image(GuiGraphics ctx, Identifier texture, int x, int y, int width, int height)
	{
		image(ctx, texture, x, y, width, height, 512, 288);
	}

	public static void image(GuiGraphics ctx, Identifier texture, int x, int y, int width, int height,
			int sourceWidth, int sourceHeight)
	{
		ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height,
				sourceWidth, sourceHeight, sourceWidth, sourceHeight);
	}

	// For GPU framebuffer textures, whose colour attachment is bottom-left origin
	public static void imageFlippedV(GuiGraphics ctx, Identifier texture, int x, int y, int width, int height,
			int sourceWidth, int sourceHeight)
	{
		ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, (float) sourceHeight, width, height,
				sourceWidth, -sourceHeight, sourceWidth, sourceHeight);
	}

	private static final int PLACEHOLDER_TINT = 0xFF1B3A2F;
	private static final long PLACEHOLDER_CYCLE_MS = 2400L;
	private static final long PLACEHOLDER_DOT_MS = 400L;
	private static final int PLACEHOLDER_BAND = 8;
	private static final int PLACEHOLDER_LABEL_MIN_HEIGHT = 40;
	private static final String[] PLACEHOLDER_LABELS = { "Loading.", "Loading..", "Loading..." };

	// A diagonal accent-to-backdrop wash that drifts across the rect once per cycle, built from
	// vertical bands since the extractor only fills vertical gradients
	public static void blueprintPlaceholder(GuiGraphics ctx, int x, int y, int width, int height)
	{
		float offset = (System.currentTimeMillis() % PLACEHOLDER_CYCLE_MS) / (float) PLACEHOLDER_CYCLE_MS;

		for (int left = 0; left < width; left += PLACEHOLDER_BAND)
		{
			int right = Math.min(width, left + PLACEHOLDER_BAND);
			float u = (left + right) * 0.5F / width;
			ctx.fillGradient(x + left, y, x + right, y + height,
					placeholderColor(u * 0.25F + offset), placeholderColor(u * 0.25F + 0.25F + offset));
		}

		ctx.fill(x, y, x + width, y + 1, 0x14FFFFFF);
	}

	public static void loadingPlaceholder(GuiGraphics ctx, int x, int y, int width, int height)
	{
		blueprintPlaceholder(ctx, x, y, width, height);

		if (height < PLACEHOLDER_LABEL_MIN_HEIGHT)
		{
			return;
		}

		Font font = Minecraft.getInstance().font;
		String label = PLACEHOLDER_LABELS[(int) (System.currentTimeMillis() / PLACEHOLDER_DOT_MS % PLACEHOLDER_LABELS.length)];
		int labelX = x + (width - font.width(PLACEHOLDER_LABELS[PLACEHOLDER_LABELS.length - 1])) / 2;
		text(ctx, font, label, labelX, y + (height - font.lineHeight) / 2, TEXT_MUTE);
	}

	// A triangle wave over the phase so the wash wraps without a seam
	private static int placeholderColor(float phase)
	{
		float t = Math.abs(2.0F * (phase - (float) Math.floor(phase)) - 1.0F);
		return 0xFF000000 | lerpColor(PLACEHOLDER_TINT, BACKDROP, t);
	}

	public static void goldGloss(GuiGraphics ctx, int x, int y, int width, int height, float hover)
	{
		roundedRect(ctx, x, y, width, height, RADIUS_PILL, GOLD);
		ctx.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFF0EB6E, 0xFFFB8800);
		int sheenAlpha = 0x44 + Math.round(0x30 * hover);
		ctx.fillGradient(x + 1, y + 1, x + width - 1, y + Math.max(2, height / 2), (sheenAlpha << 24) | 0xFFFFFF, 0x00FFFFFF);
	}

	// Warm orange-to-yellow ramp drawn per character, for the premium tab's gold headings
	private static final int GOLD_GRAD_FROM = 0xFB8800;
	private static final int GOLD_GRAD_TO = 0xF0EB6E;

	public static void goldGradientText(GuiGraphics ctx, Font font, String text, int x, int y, boolean bold)
	{
		int denom = Math.max(1, text.length() - 1);
		int cx = x;

		for (int i = 0; i < text.length(); i++)
		{
			float t = text.length() <= 1 ? 0.0F : (float) i / denom;
			int color = 0xFF000000 | lerpColor(GOLD_GRAD_FROM, GOLD_GRAD_TO, t);
			String glyph = (bold ? "§l" : "") + text.charAt(i);
			text(ctx, font, glyph, cx, y, color);
			cx += font.width(glyph);
		}
	}

	public static void goldGradientTextScaled(GuiGraphics ctx, Font font, String text, int x, int y,
			float scale, boolean bold)
	{
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x, (float) y);
		ctx.pose().scale(scale, scale);

		int denom = Math.max(1, text.length() - 1);
		int cx = 0;

		for (int i = 0; i < text.length(); i++)
		{
			float t = text.length() <= 1 ? 0.0F : (float) i / denom;
			int color = 0xFF000000 | lerpColor(GOLD_GRAD_FROM, GOLD_GRAD_TO, t);
			String glyph = (bold ? "§l" : "") + text.charAt(i);
			ctx.drawString(font, glyph, cx, 0, color, false);
			cx += font.width(glyph);
		}

		ctx.pose().popMatrix();
	}

	private static int lerpColor(int from, int to, float t)
	{
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	public static final int SHARD = 0xFF8B69CA;
	public static final int SHARD_SPEND = 0xFFD97B76;

	public static int shardTint(int trend)
	{
		return trend > 0 ? ACCENT_BRIGHT : (trend < 0 ? SHARD_SPEND : SHARD);
	}

	// The font draws alpha below 4 as fully opaque, so a fade bottoms out just above it
	public static int withAlpha(int color, float alpha)
	{
		int a = Math.max(4, Math.min(255, Math.round(alpha * 255.0F)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static float easeOut(float t)
	{
		float p = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
		return 1.0F - p * p * p;
	}

	private static final String[] HEART_INNER = {
			"         ",
			"  XX XX  ",
			" XXXXXXX ",
			" XXXXXXX ",
			" XXXXXXX ",
			"  XXXXX  ",
			"   XXX   ",
			"    X    ",
			"         "
	};

	public static final int HEART_EMPTY = 0xFF8C959B;

	public static void arrow(GuiGraphics ctx, int x, int y, boolean left, int color)
	{
		for (int row = 0; row < 7; row++)
		{
			int width = Math.min(row, 6 - row) + 1;
			int rowX = left ? x + (4 - width) : x;
			ctx.fill(rowX, y + row, rowX + width, y + row + 1, color);
		}
	}

	public static void flag(GuiGraphics ctx, int x, int y, int size, int color)
	{
		int pole = Math.max(2, size / 12 + 1);
		ctx.fill(x, y, x + pole, y + size, color);

		int flagH = Math.max(3, size / 2);
		int flagW = Math.max(3, size / 2 + 1);

		for (int row = 0; row < flagH; row++)
		{
			int w = flagW - row * flagW / flagH;

			if (w <= 0)
			{
				break;
			}

			ctx.fill(x + pole, y + row, x + pole + w, y + row + 1, color);
		}
	}

	public static void cross(GuiGraphics ctx, int x, int y, int size, int color)
	{
		for (int i = 0; i < size; i++)
		{
			ctx.fill(x + i, y + i, x + i + 1, y + i + 1, color);
			ctx.fill(x + i, y + size - 1 - i, x + i + 1, y + size - i, color);
		}
	}

	public static void downloadGlyph(GuiGraphics ctx, int x, int y, int color)
	{
		ctx.fill(x + 2, y, x + 3, y + 2, color);
		ctx.fill(x, y + 2, x + 5, y + 3, color);
		ctx.fill(x + 1, y + 3, x + 4, y + 4, color);
		ctx.fill(x + 2, y + 4, x + 3, y + 5, color);
	}

	public static final int DOWNLOAD_GLYPH_WIDTH = 5;

	public static void eyeGlyph(GuiGraphics ctx, int x, int y, int color)
	{
		ctx.fill(x + 2, y, x + 5, y + 1, color);
		ctx.fill(x + 1, y + 1, x + 2, y + 4, color);
		ctx.fill(x + 5, y + 1, x + 6, y + 4, color);
		ctx.fill(x, y + 2, x + 1, y + 3, color);
		ctx.fill(x + 6, y + 2, x + 7, y + 3, color);
		ctx.fill(x + 2, y + 4, x + 5, y + 5, color);
		ctx.fill(x + 3, y + 2, x + 4, y + 3, color);
	}

	public static final int EYE_GLYPH_WIDTH = 7;

	public static void trashGlyph(GuiGraphics ctx, int x, int y, int color)
	{
		ctx.fill(x + 2, y, x + 5, y + 1, color);       // lid handle
		ctx.fill(x, y + 1, x + 7, y + 2, color);        // lid
		ctx.fill(x + 1, y + 2, x + 6, y + 8, color);    // body
	}

	public static final int TRASH_GLYPH_WIDTH = 7;

	public static void click(float pitch)
	{
		float volume = Settings.uiVolumeFraction();

		if (!Settings.sounds() || volume <= 0.0F)
		{
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client != null && client.getSoundManager() != null)
		{
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
		}
	}

	public static void click()
	{
		click(1.0F);
	}

	public static void sound(SoundEvent event, float pitch, float volume)
	{
		float master = Settings.uiVolumeFraction();

		if (!Settings.sounds() || master <= 0.0F)
		{
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client != null && client.getSoundManager() != null)
		{
			client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume * master));
		}
	}

	public static void tab()
	{
		// A soft, short chime rather than the amethyst break, since tabs change often
		sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.3F, 0.45F);
	}

	public static void amethystBreak()
	{
		sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.8F);
	}

	public static void beaconActivate()
	{
		sound(SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
	}

	public static void raidVictory()
	{
		sound(SoundEvents.RAID_HORN.value(), 1.0F, 0.6F);
	}

	public static void beaconPowerSelect()
	{
		sound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F);
	}

	public static void success()
	{
		sound(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F);
	}

	public static void failure()
	{
		sound(SoundEvents.VILLAGER_HURT, 1.0F, 1.0F);
	}

	public static void follow()
	{
		sound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.9F);
	}

	public static void like()
	{
		sound(SoundEvents.NOTE_BLOCK_HARP.value(), 1.0F, 0.9F);
	}

	public static void rate()
	{
		sound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 0.6F);
	}

	public static void editGlyph(GuiGraphics ctx, int x, int y, int color)
	{
		ctx.fill(x, y + 6, x + 2, y + 8, color);
		ctx.fill(x + 1, y + 5, x + 3, y + 7, color);
		ctx.fill(x + 2, y + 4, x + 4, y + 6, color);
		ctx.fill(x + 3, y + 3, x + 5, y + 5, color);
		ctx.fill(x + 4, y + 2, x + 6, y + 4, color);
		ctx.fill(x + 5, y + 1, x + 7, y + 3, color);
	}

	public static final int EDIT_GLYPH_WIDTH = 7;

	public static void heartPopped(GuiGraphics ctx, int x, int y, boolean liked, long age)
	{
		float scale = 1.0F;

		if (age >= 0 && age < HEART_POP_MILLIS)
		{
			float progress = age / (float) HEART_POP_MILLIS;

			scale = 1.0F + 0.22F * (float) Math.sin(progress * Math.PI);
		}

		if (scale == 1.0F)
		{
			heart(ctx, x, y, liked);
			return;
		}

		float centreX = x + 4.5F;
		float centreY = y + 4.5F;
		ctx.pose().pushMatrix();
		ctx.pose().translate(centreX, centreY);
		ctx.pose().scale(scale, scale);
		ctx.pose().translate(-centreX, -centreY);
		heart(ctx, x, y, liked);
		ctx.pose().popMatrix();
	}

	public static final long HEART_POP_MILLIS = 220L;

	public static void heart(GuiGraphics ctx, int x, int y, boolean liked)
	{
		ctx.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/container"), x, y, 9, 9);

		if (liked)
		{
			ctx.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/full"), x, y, 9, 9);
			return;
		}

		for (int row = 0; row < HEART_INNER.length; row++)
		{
			String line = HEART_INNER[row];
			int runStart = -1;

			for (int column = 0; column <= line.length(); column++)
			{
				boolean filled = column < line.length() && line.charAt(column) == 'X';

				if (filled && runStart < 0)
				{
					runStart = column;
				}
				else if (!filled && runStart >= 0)
				{
					ctx.fill(x + runStart, y + row, x + column, y + row + 1, HEART_EMPTY);
					runStart = -1;
				}
			}
		}
	}
}

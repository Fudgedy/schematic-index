package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Toasts
{
	private static final int MARGIN = 8;
	private static final int WIDTH = 190;
	private static final int GAP = 6;

	private static final int BAR = 4;
	private static final int ACTION_HEIGHT = 14;
	private static final long LIFETIME = 5200L;
	private static final long SLIDE = 260L;
	private static final int MAX_VISIBLE = 4;
	private static final int MAX_QUEUE = 16;
	// Each wrapped line past the first lengthens the lifetime, so a longer toast stays readable
	private static final long LINE_LIFETIME_BONUS = 700L;

	private static final List<Toast> ACTIVE = new CopyOnWriteArrayList<>();

	// Measured on the first render, where the Font is available: a toast can be pushed from a
	// background callback, so the measuring cannot happen at push time
	private static final class Toast
	{
		final String title;
		final String message;
		// Zero until the first draw: a toast pushed during loading must not be spent before a screen shows it
		long shownAt;
		final @Nullable ItemStack icon;
		final @Nullable String avatarUrl;
		final int textLeft;
		final @Nullable String actionLabel;
		final @Nullable Runnable action;
		// Screen rect of the action pill from the last draw, so a click on the mod screen can find it
		final Rect actionRect = new Rect();

		@Nullable List<String> lines;
		int cardHeight;
		final long baseLifetime;
		long lifetime;

		Toast(String title, String message, long baseLifetime, @Nullable ItemStack icon, @Nullable String avatarUrl,
				@Nullable String actionLabel, @Nullable Runnable action)
		{
			this.title = title;
			this.message = message;
			this.baseLifetime = baseLifetime;
			this.lifetime = baseLifetime;
			this.icon = icon;
			this.avatarUrl = avatarUrl;
			this.textLeft = icon != null || avatarUrl != null ? 30 : 14;
			this.actionLabel = actionLabel;
			this.action = action;
		}

		void prepare(Font font, long now)
		{
			if (this.lines != null)
			{
				return;
			}

			List<String> wrapped = wrap(font, this.message, WIDTH - this.textLeft - 10, 4);
			this.cardHeight = 11 + font.lineHeight + 4 + Math.max(1, wrapped.size()) * (font.lineHeight + 1) + 9
					+ (this.action != null ? ACTION_HEIGHT + 4 : 0);
			this.lifetime = this.baseLifetime + Math.max(0, wrapped.size() - 1) * LINE_LIFETIME_BONUS;
			this.lines = wrapped;
			this.shownAt = now;
		}
	}

	private Toasts()
	{
	}

	public static void push(String title, String message, @Nullable ItemStack icon)
	{
		add(new Toast(title, message, LIFETIME, icon, null, null, null));
	}

	// The action is a small pill on the card; it only reacts while the mod screen is up to take clicks
	public static void pushAction(String title, String message, @Nullable ItemStack icon, String actionLabel, Runnable action)
	{
		pushAction(title, message, icon, actionLabel, action, LIFETIME);
	}

	public static void pushAction(String title, String message, @Nullable ItemStack icon, String actionLabel, Runnable action,
			long lifetime)
	{
		add(new Toast(title, message, lifetime, icon, null, actionLabel, action));
	}

	public static long defaultLifetime()
	{
		return LIFETIME;
	}

	public static void pushAvatar(String title, String message, String avatarUrl)
	{
		add(new Toast(title, message, LIFETIME, null, avatarUrl, null, null));
	}

	// A 401 that outlived the session retry asks for a verify; anything unnamed is the generic server toast
	public static void refusal(Backend.ApiResult result, String code)
	{
		if (result.unverified() || result.is("not_verified"))
		{
			push("Verify your account first", "Your verified session ended.", new ItemStack(Items.NAME_TAG));
			return;
		}

		push("Couldn't reach the server", "Try again in a moment. (" + code + ")", new ItemStack(Items.BARRIER));
		Errors.report(code);
	}

	// "Not Enough Shards" is only ever the 402 answer; what names the item, such as "this colour"
	public static void shopRefusal(Backend.ApiResult result, String what, String code)
	{
		ItemStack shard = new ItemStack(Items.AMETHYST_SHARD);

		if (result.status() == 402 || result.is("insufficient"))
		{
			push("Not Enough Shards", "You don't have enough shards for " + what + ".", shard);
		}
		else if (result.is("already_owned") || result.is("owned"))
		{
			push("Already Owned", "You already own " + what + ".", shard);
		}
		else if (result.is("palette_full"))
		{
			push("Palette Full", "Refund a colour to make room for " + what + ".", shard);
		}
		else
		{
			refusal(result, code);
		}
	}

	public static boolean mouseClicked(double mouseX, double mouseY)
	{
		for (Toast toast : ACTIVE)
		{
			if (toast.action == null || !toast.actionRect.contains(mouseX, mouseY))
			{
				continue;
			}

			Theme.click();
			toast.action.run();
			ACTIVE.remove(toast);
			return true;
		}

		return false;
	}

	private static void add(Toast toast)
	{
		if (!Settings.toasts())
		{
			return;
		}

		ACTIVE.add(toast);

		// render() shows the first MAX_VISIBLE, so the dropped one is the oldest past that window rather than index 0
		if (ACTIVE.size() > MAX_QUEUE)
		{
			int drop = Math.min(MAX_VISIBLE, ACTIVE.size() - 1);
			ACTIVE.remove(drop);
		}
	}

	public static void clear()
	{
		ACTIVE.clear();
	}

	public static void render(GuiGraphics ctx)
	{
		if (ACTIVE.isEmpty())
		{
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client == null || client.font == null || client.getWindow() == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		ACTIVE.removeIf(toast -> toast.shownAt > 0L && now - toast.shownAt >= toast.lifetime);

		Font font = client.font;
		int baseY = client.getWindow().getGuiScaledHeight() - MARGIN;
		int shown = 0;

		for (Toast toast : ACTIVE)
		{
			if (shown >= MAX_VISIBLE)
			{
				break;
			}

			toast.prepare(font, now);
			long age = now - toast.shownAt;
			baseY -= toast.cardHeight;
			draw(ctx, font, toast, slideX(age, toast.lifetime), baseY, age);
			baseY -= GAP;
			shown++;
		}
	}

	private static void draw(GuiGraphics ctx, Font font, Toast toast, int x, int y, long age)
	{
		int height = toast.cardHeight;
		Theme.roundedRect(ctx, x, y, WIDTH, height, Theme.RADIUS_CARD, 0xF01A1E23);
		Theme.roundedOutline(ctx, x, y, WIDTH, height, Theme.RADIUS_CARD, Theme.HAIRLINE);

		ctx.fill(x + 1, y + 1, x + 1 + BAR, y + height - 1, Theme.ACCENT);

		if (toast.avatarUrl != null)
		{
			int size = 18;
			int ax = x + BAR + 6;
			int ay = y + (height - size) / 2;
			Identifier avatar = ImageStore.avatar(toast.avatarUrl);

			if (avatar != null)
			{
				Theme.image(ctx, avatar, ax, ay, size, size, 64, 64);
			}
			else
			{
				Theme.roundedRect(ctx, ax, ay, size, size, 4, Theme.SURFACE_ELEVATED);
			}
		}
		else if (toast.icon != null)
		{
			ctx.renderItem(toast.icon, x + BAR + 6, y + (height - 16) / 2);
		}

		int tx = x + toast.textLeft;
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, toast.title, WIDTH - toast.textLeft - 8)),
				tx, y + 8, Theme.TEXT);

		int ly = y + 8 + font.lineHeight + 3;

		if (toast.lines != null)
		{
			for (String line : toast.lines)
			{
				Theme.text(ctx, font, line, tx, ly, Theme.TEXT_MUTE);
				ly += font.lineHeight + 1;
			}
		}

		if (toast.action != null && toast.actionLabel != null)
		{
			int pillWidth = font.width(Theme.bold(toast.actionLabel)) + 12;
			toast.actionRect.set(x + WIDTH - 6 - pillWidth, y + height - 5 - ACTION_HEIGHT, pillWidth, ACTION_HEIGHT);
			Buttons.pill(ctx, font, toast.actionRect, toast.actionLabel, -1, -1, true);
		}

		float remaining = Math.max(0.0F, 1.0F - age / (float) toast.lifetime);
		int trackLeft = x + BAR + 4;
		int barWidth = Math.round((x + WIDTH - 4 - trackLeft) * remaining);
		ctx.fill(trackLeft, y + height - 3, trackLeft + barWidth, y + height - 2, Theme.ACCENT_BRIGHT);
	}

	private static int slideX(long age, long lifetime)
	{
		int hidden = -(WIDTH + MARGIN + 4);

		if (age < SLIDE)
		{
			float t = age / (float) SLIDE;
			float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
			return Math.round(hidden + (MARGIN - hidden) * eased);
		}

		if (age > lifetime - SLIDE)
		{
			float t = (age - (lifetime - SLIDE)) / (float) SLIDE;
			return Math.round(MARGIN + (hidden - MARGIN) * (t * t));
		}

		return MARGIN;
	}

	private static List<String> wrap(Font font, String text, int width, int maxLines)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (String word : text.split(" "))
		{
			String candidate = current.isEmpty() ? word : current + " " + word;

			if (font.width(candidate) > width && !current.isEmpty())
			{
				lines.add(current.toString());
				current = new StringBuilder(word);

				if (lines.size() == maxLines)
				{
					String last = lines.get(maxLines - 1);
					lines.set(maxLines - 1, Theme.clip(font, last + "...", width));
					return lines;
				}
			}
			else
			{
				current = new StringBuilder(candidate);
			}
		}

		if (!current.isEmpty() && lines.size() < maxLines)
		{
			lines.add(current.toString());
		}

		return lines;
	}
}

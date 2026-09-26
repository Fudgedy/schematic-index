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
import java.util.function.BooleanSupplier;

public final class Toasts
{
	private static final int MARGIN = 8;
	private static final int WIDTH = 190;
	private static final int GAP = 6;

	private static final int BAR = 4;
	private static final int ACTION_HEIGHT = 14;
	private static final long LIFETIME = 5200L;
	private static final long SLIDE = 260L;
	private static final String STORE_PATH = "/#store";
	private static final int MAX_VISIBLE = 4;
	private static final int MAX_QUEUE = 16;
	// Each wrapped line past the first lengthens the lifetime, so a longer toast stays readable
	private static final long LINE_LIFETIME_BONUS = 700L;

	private static final List<Toast> ACTIVE = new CopyOnWriteArrayList<>();
	// Guards merging against the render thread's expiry sweep, since pushes arrive from network threads
	private static final Object LOCK = new Object();
	// Set while a server notice pushes its toast, whose inbox row already exists server side
	private static final ThreadLocal<Boolean> FROM_NOTICE = new ThreadLocal<>();

	// Measured on the first render, where the Font is available: a toast can be pushed from a
	// background callback, so the measuring cannot happen at push time
	private static final class Toast
	{
		final String title;
		final String message;
		// Zero until the first draw: a toast pushed during loading must not be spent before a screen shows it
		volatile long shownAt;
		final @Nullable ItemStack icon;
		final @Nullable String avatarUrl;
		final boolean crown;
		final String iconKey;
		final int textLeft;
		@Nullable String actionLabel;
		@Nullable Runnable action;
		volatile int count = 1;
		// Screen rect of the action pill from the last draw, so a click on the mod screen can find it
		final Rect actionRect = new Rect();
		@Nullable String secondLabel;
		@Nullable Runnable secondAction;
		final Rect secondRect = new Rect();

		@Nullable List<String> lines;
		int cardHeight;
		final long baseLifetime;
		volatile long lifetime;
		long span;

		Toast(String title, String message, long baseLifetime, @Nullable ItemStack icon, @Nullable String avatarUrl,
				@Nullable String actionLabel, @Nullable Runnable action)
		{
			this(title, message, baseLifetime, icon, avatarUrl, false, actionLabel, action);
		}

		Toast(String title, String message, long baseLifetime, @Nullable ItemStack icon, @Nullable String avatarUrl,
				boolean crown, @Nullable String actionLabel, @Nullable Runnable action)
		{
			this.title = title;
			this.message = message;
			this.baseLifetime = baseLifetime;
			this.lifetime = baseLifetime;
			this.icon = icon;
			this.avatarUrl = avatarUrl;
			this.crown = crown;
			this.iconKey = crown ? ToastHistory.CROWN
					: avatarUrl != null ? ToastHistory.AVATAR_PREFIX + avatarUrl : ToastHistory.iconKey(icon);
			this.textLeft = icon != null || avatarUrl != null || crown ? 30 : 14;
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
			this.span = this.baseLifetime + Math.max(0, wrapped.size() - 1) * LINE_LIFETIME_BONUS;
			this.lifetime = this.span;
			this.lines = wrapped;
			this.shownAt = now;
		}

		boolean sameAs(Toast other)
		{
			return this.title.equals(other.title) && this.message.equals(other.message) && this.iconKey.equals(other.iconKey);
		}

		void absorb(Toast newer, long now)
		{
			this.count++;

			// The card height was measured with or without a pill, so only a like-for-like action is swapped in
			if (this.lines == null || (this.action == null) == (newer.action == null))
			{
				this.actionLabel = newer.actionLabel;
				this.action = newer.action;
				this.secondLabel = newer.secondLabel;
				this.secondAction = newer.secondAction;
			}

			if (this.shownAt > 0L)
			{
				this.lifetime = now - this.shownAt + this.span;
			}
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

	public static void pushCrown(String title, String message, @Nullable String actionLabel, @Nullable Runnable action)
	{
		add(new Toast(title, message, LIFETIME, null, null, true, action == null ? null : actionLabel, action));
	}

	// Two pills side by side, the first drawn as the primary one
	public static void pushActions(String title, String message, @Nullable ItemStack icon, String actionLabel, Runnable action,
			String secondLabel, Runnable secondAction)
	{
		Toast toast = new Toast(title, message, LIFETIME, icon, null, actionLabel, action);
		toast.secondLabel = secondLabel;
		toast.secondAction = secondAction;
		add(toast);
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

	// Every shortfall offers the store, the same page as the Shard menu's Buy button
	public static void notEnoughShards(String message)
	{
		pushAction("Not Enough Shards", message, new ItemStack(Items.AMETHYST_SHARD), "Buy shards", Toasts::openStore);
	}

	public static void openStore()
	{
		IndexScreen.openExternal(Backend.siteBase() + STORE_PATH);
	}

	// "Not Enough Shards" is only ever the 402 answer; what names the item, such as "this colour"
	public static void shopRefusal(Backend.ApiResult result, String what, String code)
	{
		ItemStack shard = new ItemStack(Items.AMETHYST_SHARD);

		if (result.status() == 402 || result.is("insufficient"))
		{
			notEnoughShards("You don't have enough shards for " + what + ".");
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

	// Runs a server notice's toast push without a local inbox row, since the notice already has one
	public static boolean fromNotice(BooleanSupplier push)
	{
		FROM_NOTICE.set(Boolean.TRUE);
		boolean pushed = push.getAsBoolean();
		FROM_NOTICE.remove();
		return pushed;
	}

	public static boolean mouseClicked(double mouseX, double mouseY)
	{
		for (Toast toast : ACTIVE)
		{
			Runnable action = toast.action != null && toast.actionRect.contains(mouseX, mouseY) ? toast.action
					: (toast.secondAction != null && toast.secondRect.contains(mouseX, mouseY) ? toast.secondAction : null);

			if (action == null)
			{
				continue;
			}

			Theme.click();
			action.run();
			ACTIVE.remove(toast);
			return true;
		}

		return false;
	}

	private static void add(Toast toast)
	{
		if (FROM_NOTICE.get() == null)
		{
			ToastHistory.record(toast.title, toast.message, toast.iconKey, toast.actionLabel, toast.action);
		}

		if (!Settings.toasts())
		{
			return;
		}

		synchronized (LOCK)
		{
			for (Toast active : ACTIVE)
			{
				if (active.sameAs(toast))
				{
					active.absorb(toast, System.currentTimeMillis());
					return;
				}
			}

			ACTIVE.add(toast);

			// render() shows the first MAX_VISIBLE, so the dropped one is the oldest past that window rather than index 0
			if (ACTIVE.size() > MAX_QUEUE)
			{
				int drop = Math.min(MAX_VISIBLE, ACTIVE.size() - 1);
				ACTIVE.remove(drop);
			}
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
		synchronized (LOCK)
		{
			ACTIVE.removeIf(toast -> toast.shownAt > 0L && now - toast.shownAt >= toast.lifetime);
		}

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
		else if (toast.crown)
		{
			Crown.draw(ctx, x + BAR + 8, y + (height - Crown.HEIGHT) / 2);
		}
		else if (toast.icon != null)
		{
			ctx.renderItem(toast.icon, x + BAR + 6, y + (height - 16) / 2);
		}

		int tx = x + toast.textLeft;
		String title = toast.count > 1 ? toast.title + " (" + toast.count + ")" : toast.title;
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, title, WIDTH - toast.textLeft - 8)),
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

			if (toast.secondAction != null && toast.secondLabel != null)
			{
				int secondWidth = font.width(Theme.bold(toast.secondLabel)) + 12;
				toast.secondRect.set(toast.actionRect.x - 4 - secondWidth, toast.actionRect.y, secondWidth, ACTION_HEIGHT);
				Buttons.pill(ctx, font, toast.secondRect, toast.secondLabel, -1, -1, false);
			}
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

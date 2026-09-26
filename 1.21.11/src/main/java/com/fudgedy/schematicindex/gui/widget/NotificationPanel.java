package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Notices;
import com.fudgedy.schematicindex.gui.Crown;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.ToastHistory;
import com.fudgedy.schematicindex.gui.mapart.MapartUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The bell inbox: server notices and the local toast history merged newest first
public class NotificationPanel
{
	private static final int PANEL_WIDTH = 300;
	private static final int MAX_HEIGHT = 440;
	private static final int HEADER_HEIGHT = 24;
	private static final int ROW_PAD = 6;
	private static final int TEXT_LEFT = 32;
	private static final int EMPTY_HEIGHT = 104;
	private static final long CONFIRM_MS = 3000L;
	// Time constant of the wheel ease, short enough that the list keeps up with a fast flick
	private static final float SCROLL_EASE_MS = 70.0F;
	private static final String CLEAR = "Clear all";
	private static final String CONFIRM = "Confirm?";

	private final IndexScreen screen;
	private final Rect bellButton = new Rect();
	private boolean open;
	// Refreshed each render for click-outside dismissal
	private final Rect bounds = new Rect();
	private final Rect body = new Rect();
	private final Rect clearButton = new Rect();
	private final Rect emptyAction = new Rect();
	private final List<Rect> rowRects = new ArrayList<>();
	private final List<Row> shownRows = new ArrayList<>();
	private final ItemStack bell = new ItemStack(Items.BELL);
	private List<Row> rows = List.of();
	private @Nullable List<Notices.Entry> builtServer;
	private @Nullable List<ToastHistory.Entry> builtLocal;
	private int builtWidth;
	private float scroll;
	private float scrollTarget;
	private float maxScroll;
	private long lastFrameAt;
	private long confirmUntil;

	public NotificationPanel(IndexScreen screen)
	{
		this.screen = screen;
	}

	public Rect bellButton()
	{
		return this.bellButton;
	}

	public boolean hasInbox()
	{
		return Notices.hasInbox() || !ToastHistory.entries().isEmpty();
	}

	public void pollIfDue(long nowMs)
	{
		Notices.pollIfDue(this.screen, nowMs);
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY, boolean modalOpen)
	{
		// Above the content, below hard modals; auto-dismisses when the bell itself is gone
		if (this.open && !modalOpen && this.hasInbox())
		{
			this.renderPanel(ctx, mouseX, mouseY);
		}
		else
		{
			this.open = false;
		}
	}

	public void renderBell(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		Rect r = this.bellButton;
		boolean hovered = r.contains(mouseX, mouseY);

		Theme.roundedRect(ctx, r.x - 2, r.y, r.width + 4, r.height, Theme.RADIUS_PILL,
				this.open || hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.item(ctx, this.bell, r.x, r.y);

		int unread = this.unread();

		if (unread > 0)
		{
			String label = unread > 9 ? "9+" : Integer.toString(unread);
			int badgeW = font.width(label) + 5;
			int badgeX = r.x + r.width - badgeW + 3;
			int badgeY = r.y - 3;
			Theme.roundedRect(ctx, badgeX, badgeY, badgeW, 9, Theme.RADIUS_PILL, Theme.ACCENT);
			Theme.text(ctx, font, label, badgeX + 3, badgeY + 1, Theme.ON_ACCENT);
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.hasInbox() && this.bellButton.contains(mouseX, mouseY))
		{
			this.toggle();
			return true;
		}

		// Swallowed so a click inside the panel does not fall through to the grid behind it
		if (this.open && this.bounds.contains(mouseX, mouseY))
		{
			if (this.clearButton.contains(mouseX, mouseY))
			{
				this.clickClear();
			}
			else if (this.body.contains(mouseX, mouseY))
			{
				this.clickRow(mouseX, mouseY);
			}

			return true;
		}

		if (this.open)
		{
			this.open = false;
		}

		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
	{
		if (!this.open || !this.bounds.contains(mouseX, mouseY))
		{
			return false;
		}

		this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll, this.scrollTarget - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}

	private void toggle()
	{
		this.open = !this.open;
		Theme.click(1.1F);

		if (this.open)
		{
			this.scroll = 0.0F;
			this.scrollTarget = 0.0F;
			this.confirmUntil = 0L;
			this.lastFrameAt = System.currentTimeMillis();
			this.markSeen();
		}
	}

	// Opening marks everything read; the server marker only ever advances to server times, as it doubles as ?since=
	private void markSeen()
	{
		long newest = Settings.notificationsSeenAt();

		for (Notices.Entry entry : Notices.entries())
		{
			newest = Math.max(newest, entry.at());
		}

		Settings.setNotificationsSeenAt(newest);
		ToastHistory.markSeen();
	}

	private void renderPanel(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		int panelRight = this.screen.closeButton.x + this.screen.closeButton.width;
		int panelW = Math.min(PANEL_WIDTH, panelRight - IndexScreen.RAIL_WIDTH - Theme.SPACE_XS);
		int panelX = panelRight - panelW;
		int panelY = IndexScreen.TOP_BAR_HEIGHT + Theme.SPACE_XS;
		int maxH = Math.min(MAX_HEIGHT, this.screen.height - panelY - IndexScreen.OUTER_MARGIN);
		int rowWidth = panelW - Theme.SPACE_XS * 2;

		this.rebuild(font, rowWidth);
		this.markSeen();

		int content = 0;

		for (Row row : this.rows)
		{
			content += row.height;
		}

		int bodyH = this.rows.isEmpty() ? EMPTY_HEIGHT : content;
		int panelH = Math.min(maxH, HEADER_HEIGHT + bodyH + Theme.SPACE_XS);
		int viewport = panelH - HEADER_HEIGHT - Theme.SPACE_XS;
		this.maxScroll = this.rows.isEmpty() ? 0.0F : Math.max(0, content - viewport);
		this.scrollTarget = Math.min(this.scrollTarget, this.maxScroll);
		this.easeScroll();

		this.bounds.set(panelX, panelY, panelW, panelH);
		this.body.set(panelX, panelY + HEADER_HEIGHT, panelW, viewport);

		Theme.roundedRect(ctx, panelX, panelY, panelW, panelH, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, panelX, panelY, panelW, panelH, Theme.RADIUS_CARD, Theme.HAIRLINE);
		Theme.text(ctx, font, Theme.bold("Notifications"), panelX + Theme.SPACE_S, panelY + Theme.SPACE_S, Theme.TEXT);
		this.renderClear(ctx, font, panelX + panelW - Theme.SPACE_S, panelY + Theme.SPACE_S, mouseX, mouseY);

		this.shownRows.clear();

		if (this.rows.isEmpty())
		{
			States.empty(ctx, font, panelX, this.body.y - Theme.SPACE_S, panelW, this.bell, "No notifications",
					"Toasts and account activity land here.", this.emptyAction, null, mouseX, mouseY);
			return;
		}

		boolean inBody = this.body.contains(mouseX, mouseY);
		int rowX = panelX + Theme.SPACE_XS;
		int y = this.body.y - Math.round(this.scroll);
		ctx.enableScissor(panelX, this.body.y, panelX + panelW, this.body.y + viewport);

		for (Row row : this.rows)
		{
			if (y + row.height > this.body.y && y < this.body.y + viewport)
			{
				Rect rect = Rect.pooled(this.rowRects, this.shownRows.size());
				rect.set(rowX, y, rowWidth, row.height);
				this.shownRows.add(row);
				this.drawRow(ctx, font, row, rect, inBody ? mouseX : -1, inBody ? mouseY : -1);
			}

			y += row.height;
		}

		ctx.disableScissor();
		MapartUi.scrollbar(ctx, panelX + panelW - 3, this.body.y, viewport, this.scroll, this.maxScroll);
	}

	private void drawRow(GuiGraphics ctx, Font font, Row row, Rect rect, int mouseX, int mouseY)
	{
		if (row.action() != null)
		{
			Rows.row(ctx, rect, true, mouseX, mouseY);
		}

		if (rect != this.rowRects.get(0))
		{
			ctx.fill(rect.x + Theme.SPACE_XS, rect.y, rect.x + rect.width - Theme.SPACE_XS, rect.y + 1, Theme.HAIRLINE);
		}

		this.drawIcon(ctx, row, rect.x + Theme.SPACE_S, rect.y + (rect.height - Theme.ICON_M) / 2);

		int tx = rect.x + TEXT_LEFT;
		int ty = rect.y + ROW_PAD;
		String time = relativeTime(row.at);
		Theme.text(ctx, font, time, rect.x + rect.width - Theme.SPACE_S - font.width(time), ty, Theme.TEXT_ASH);

		if (row.title != null)
		{
			Theme.text(ctx, font, Theme.bold(row.title), tx, ty, Theme.TEXT);
			ty += font.lineHeight + Theme.SPACE_2XS;
		}

		for (String line : row.lines)
		{
			Theme.text(ctx, font, line, tx, ty, row.title == null ? Theme.TEXT : Theme.TEXT_MUTE);
			ty += font.lineHeight + 1;
		}
	}

	private void drawIcon(GuiGraphics ctx, Row row, int x, int y)
	{
		if (row.crown)
		{
			Crown.draw(ctx, x + (Theme.ICON_M - Crown.WIDTH) / 2, y + (Theme.ICON_M - Crown.HEIGHT) / 2);
			return;
		}

		if (row.avatarUrl != null)
		{
			Identifier avatar = ImageStore.avatar(row.avatarUrl);

			if (avatar != null)
			{
				Theme.image(ctx, avatar, x, y, Theme.ICON_M, Theme.ICON_M, 64, 64);
				return;
			}
		}

		if (row.icon != null)
		{
			Theme.item(ctx, row.icon, x, y);
		}
	}

	private void renderClear(GuiGraphics ctx, Font font, int right, int y, int mouseX, int mouseY)
	{
		if (ToastHistory.entries().isEmpty())
		{
			this.clearButton.set(0, 0, 0, 0);
			return;
		}

		boolean confirming = System.currentTimeMillis() < this.confirmUntil;
		int width = Math.max(font.width(CLEAR), font.width(CONFIRM));
		this.clearButton.set(right - width - Theme.SPACE_XS, y - Theme.SPACE_XS, width + Theme.SPACE_S,
				font.lineHeight + Theme.SPACE_S);
		boolean hovered = this.clearButton.contains(mouseX, mouseY);
		String label = confirming ? CONFIRM : CLEAR;
		int color = confirming ? Theme.DANGER_TEXT : hovered ? Theme.TEXT : Theme.TEXT_MUTE;
		Theme.text(ctx, font, label, right - font.width(label), y, color);
	}

	private void clickClear()
	{
		Theme.click();

		if (System.currentTimeMillis() < this.confirmUntil)
		{
			this.confirmUntil = 0L;
			ToastHistory.clear();
			this.scrollTarget = 0.0F;
			return;
		}

		this.confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
	}

	private void clickRow(double mouseX, double mouseY)
	{
		for (int i = 0; i < this.shownRows.size() && i < this.rowRects.size(); i++)
		{
			Runnable action = this.shownRows.get(i).action();

			if (action != null && this.rowRects.get(i).contains(mouseX, mouseY))
			{
				Theme.click();
				this.open = false;
				action.run();
				return;
			}
		}
	}

	// Frame-rate independent, and snapped once close so the rows settle on whole pixels
	private void easeScroll()
	{
		long now = System.currentTimeMillis();
		float elapsed = Math.min(100L, Math.max(0L, now - this.lastFrameAt));
		this.lastFrameAt = now;
		float blend = 1.0F - (float) Math.exp(-elapsed / SCROLL_EASE_MS);
		this.scroll += (this.scrollTarget - this.scroll) * blend;

		if (Math.abs(this.scrollTarget - this.scroll) < 0.5F)
		{
			this.scroll = this.scrollTarget;
		}
	}

	// Laid out only when either source list or the width changes, so wrapping never runs per frame
	private void rebuild(Font font, int rowWidth)
	{
		List<Notices.Entry> server = Notices.entries();
		List<ToastHistory.Entry> local = ToastHistory.entries();

		if (server == this.builtServer && local == this.builtLocal && rowWidth == this.builtWidth)
		{
			return;
		}

		this.builtServer = server;
		this.builtLocal = local;
		this.builtWidth = rowWidth;
		int textWidth = rowWidth - TEXT_LEFT - Theme.SPACE_S;
		List<Row> merged = new ArrayList<>();
		int s = 0;
		int l = 0;

		while (s < server.size() || l < local.size())
		{
			boolean takeServer = l >= local.size() || (s < server.size() && server.get(s).at() >= local.get(l).at());

			if (takeServer)
			{
				Notices.Entry entry = server.get(s++);
				ItemStack icon = Notices.icon(entry.type());
				merged.add(this.row(font, null, entry.text(), icon, icon == null, null, entry.at(), textWidth,
						entry.action(), null));
			}
			else
			{
				ToastHistory.Entry entry = local.get(l++);
				String title = entry.count() > 1 ? entry.title() + " (" + entry.count() + ")" : entry.title();
				merged.add(this.row(font, title, entry.message(), entry.stack(), entry.crown(), entry.avatarUrl(),
						entry.at(), textWidth, null, entry));
			}
		}

		this.rows = merged;
	}

	private Row row(Font font, @Nullable String title, String text, @Nullable ItemStack icon, boolean crown,
			@Nullable String avatarUrl, long at, int textWidth, @Nullable Runnable action, @Nullable ToastHistory.Entry local)
	{
		// The first line shares its row with the time, so it wraps against the narrower width
		int timeRoom = font.width("59m ago") + Theme.SPACE_S;
		String clippedTitle = title == null ? null : Theme.clipBold(font, title, textWidth - timeRoom);
		List<String> lines = this.twoLines(text, title == null ? textWidth - timeRoom : textWidth);
		int textHeight = (title == null ? 0 : font.lineHeight + Theme.SPACE_2XS) + Math.max(1, lines.size()) * (font.lineHeight + 1);
		int height = Math.max(Theme.H_ROW, textHeight + ROW_PAD * 2 - 1);
		return new Row(clippedTitle, lines, icon, crown, avatarUrl, at, height, action, local);
	}

	private List<String> twoLines(String text, int width)
	{
		List<String> lines = this.screen.wrap(text, width, 3);

		if (lines.size() <= 2)
		{
			return lines;
		}

		return List.of(lines.get(0), this.screen.trimToWidth(lines.get(1) + " " + lines.get(2) + "...", width));
	}

	private int unread()
	{
		long seen = Settings.notificationsSeenAt();
		int count = ToastHistory.unread();

		for (Notices.Entry entry : Notices.entries())
		{
			if (entry.at() > seen)
			{
				count++;
			}
		}

		return count;
	}

	private static String relativeTime(long at)
	{
		if (at <= 0L)
		{
			return "";
		}

		long deltaMs = System.currentTimeMillis() - at;

		if (deltaMs < 60_000L)
		{
			return "just now";
		}

		long minutes = deltaMs / 60_000L;

		if (minutes < 60L)
		{
			return minutes + "m ago";
		}

		long hours = minutes / 60L;

		if (hours < 24L)
		{
			return hours + "h ago";
		}

		return (hours / 24L) + "d ago";
	}

	// A local row resolves its action on click, so one past its lifetime quietly stops being clickable
	private record Row(@Nullable String title, List<String> lines, @Nullable ItemStack icon, boolean crown,
			@Nullable String avatarUrl, long at, int height, @Nullable Runnable serverAction,
			@Nullable ToastHistory.Entry local)
	{
		@Nullable Runnable action()
		{
			return this.local != null ? this.local.liveAction() : this.serverAction;
		}
	}
}

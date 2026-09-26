package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Tabs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardPage
{
	private static final int RANGE_SEGMENT_WIDTH = 64;
	private final IndexScreen screen;
	public final DashboardStats stats;
	final StatsGraph graph;
	private final ClaimsSection claims;
	// Rebuilt on DashboardStats.apply or a filter change, not every frame
	private final List<SchematicEntry> shown = new ArrayList<>();
	private String[][] tiles;
	boolean cacheDirty = true;
	private Category cacheFilter;
	// When set, the graph shows just this post instead of the totals
	public @Nullable String selectedPost;
	private final List<Rect> postCardRects = new ArrayList<>();
	private final List<String> postCardIds = new ArrayList<>();
	private final List<Rect> postEditRects = new ArrayList<>();
	// The *Rects lists above only ever hold instances from these pools
	private final List<Rect> postCardPool = new ArrayList<>();
	private final List<Rect> postEditPool = new ArrayList<>();
	private final Rect uploadButton = new Rect();
	private final Rect statsRetryButton = new Rect();
	private int viewportTop;
	private int viewportBottom;
	private Category filter = Category.ALL;
	private final List<Rect> filterChips = new ArrayList<>();
	private final List<Rect> rangeSegments = new ArrayList<>();

	public DashboardPage(IndexScreen screen)
	{
		this.screen = screen;
		this.stats = new DashboardStats(this);
		this.graph = new StatsGraph(this, screen);
		this.claims = new ClaimsSection(this, screen);
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY, float partialTick)
	{
		Font font = this.screen.font();

		if (!this.stats.loaded && !this.stats.loading)
		{
			this.stats.load();
		}

		if (McAuth.verified() && !this.claims.loaded && !this.claims.loading)
		{
			this.claims.load();
		}

		int margin = 18;
		int areaX = this.screen.contentX + margin;
		int areaW = this.screen.contentWidth - margin * 2;
		int top = IndexScreen.TOP_BAR_HEIGHT + 16;

		// Room left of the buttons, shared with the head that follows
		int signOutWidth = UploaderAccess.hasCode() ? 58 + 6 : 0;
		int headerRoom = areaW - signOutWidth - 86 - 12;
		String signedIn = Theme.clipBold(font, "Signed in as " + UploaderAccess.profile(), headerRoom - 60);
		Theme.text(ctx, font, Theme.bold(signedIn), areaX, top, Theme.TEXT);

		String uploaderIgn = UploaderAccess.ign();

		if (uploaderIgn != null && !uploaderIgn.isBlank())
		{
			int headX = areaX + font.width(Theme.bold(signedIn)) + 6;
			int headY = top - 3;
			Identifier head = ImageStore.avatar("https://mc-heads.net/avatar/" + Backend.encode(uploaderIgn) + "/64.png");

			if (head != null)
			{
				Theme.image(ctx, head, headX, headY, 14, 14, 64, 64);
			}
		}

		UploadPage uploadPage = this.screen.uploadPage;
		uploadPage.signOutButton.set(0, 0, 0, 0);

		if (UploaderAccess.hasCode())
		{
			uploadPage.signOutButton.set(areaX + areaW - 58, top - 4, 58, IndexScreen.FIELD_HEIGHT);
			// "Confirm?" fits the existing pill width, so the adjacent "+ New post" button does not shift
			Buttons.pill(ctx, font, uploadPage.signOutButton, (uploadPage.signOutConfirmAt != 0L
					&& System.currentTimeMillis() - uploadPage.signOutConfirmAt <= UploadPage.SIGN_OUT_CONFIRM_MS)
					? "Confirm?" : "Sign out", mouseX, mouseY, false);
		}

		this.uploadButton.set(areaX + areaW - signOutWidth - 86, top - 4, 86, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.uploadButton, "+ New post", mouseX, mouseY, true);

		// A stale-triggered refresh keeps stats.ok true, so cached stats stay on screen during it
		this.statsRetryButton.set(0, 0, 0, 0);
		Tabs.clear(this.rangeSegments);

		if (!this.stats.ok)
		{
			if (this.stats.failed)
			{
				int msgY = top + 26 + 18;
				Theme.text(ctx, font, "Couldn't load your stats. (" + Errors.CREATOR_STATS + ")", areaX, msgY,
						Theme.TEXT_MUTE);
				int retryW = font.width(Theme.bold("Retry")) + 16;
				this.statsRetryButton.set(areaX, msgY + font.lineHeight + 8, retryW, IndexScreen.FIELD_HEIGHT);
				Buttons.pill(ctx, font, this.statsRetryButton, "Retry", mouseX, mouseY, true);
			}
			else
			{
				DashboardSkeleton.render(this.screen, ctx, areaX, top + 26, areaW);
			}

			return;
		}

		// One scrolling column; only the sign-in row above stays fixed
		int scrollTop = top + 26;
		int scrollBottom = this.screen.height - 6;
		this.viewportTop = scrollTop;
		this.viewportBottom = scrollBottom;

		int tileH = 42;
		int graphH = 150;
		int gap = IndexScreen.GUTTER;
		int cols = Math.max(1, (areaW + gap) / (112 + gap));
		int cw = (areaW - gap * (cols - 1)) / cols;
		int ch = IndexScreen.imageHeight(cw) + IndexScreen.CAPTION_HEIGHT;

		if (this.cacheDirty || this.tiles == null)
		{
			this.tiles = new String[][]
			{
				{"Posts", SchematicEntry.compact(this.stats.postsCount)},
				{"Views", SchematicEntry.compact(this.stats.views)},
				{"Downloads", SchematicEntry.compact(this.stats.downloads)},
				{"Likes", SchematicEntry.compact(this.stats.likes)},
				{"Avg Stars", this.stats.ratingCount > 0 ? String.format(Locale.ROOT, "%.1f", this.stats.rating) : "-"},
				{"Followers", SchematicEntry.compact(this.stats.followers)},
			};
		}

		if (this.cacheDirty || this.cacheFilter != this.filter)
		{
			this.shown.clear();

			for (SchematicEntry entry : this.stats.posts)
			{
				if (this.filter == Category.ALL || entry.category() == this.filter)
				{
					this.shown.add(entry);
				}
			}

			this.cacheFilter = this.filter;
		}

		this.cacheDirty = false;
		List<SchematicEntry> shown = this.shown;

		int uploadsHeaderH = font.lineHeight + 6;
		int chipsH = this.measureFilterChipsHeight(areaX, areaW);
		int postsH = shown.isEmpty()
				? font.lineHeight
				: ((shown.size() + cols - 1) / cols) * (ch + gap) - gap;

		int claimsH = this.claims.measureHeight();
		int rangeH = Theme.SPACE_S + Theme.H_CONTROL;
		int contentHeight = tileH + 14 + graphH + rangeH + 14 + uploadsHeaderH + chipsH + 6 + postsH + claimsH;
		this.screen.dashMaxScroll = Math.max(0.0F, contentHeight - (scrollBottom - scrollTop));
		this.screen.dashScroll = Math.max(0.0F, Math.min(this.screen.dashScroll, this.screen.dashMaxScroll));

		ctx.enableScissor(this.screen.contentX, scrollTop, this.screen.contentX + this.screen.contentWidth, scrollBottom);

		int y = scrollTop - Math.round(this.screen.dashScroll);

		String[][] tiles = this.tiles;
		int tileGap = 8;
		int tileW = (areaW - tileGap * (tiles.length - 1)) / tiles.length;

		for (int i = 0; i < tiles.length; i++)
		{
			int tx = areaX + i * (tileW + tileGap);
			Theme.roundedRect(ctx, tx, y, tileW, tileH, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
			Theme.text(ctx, font, tiles[i][0], tx + 8, y + 8, Theme.TEXT_MUTE);
			Theme.textScaled(ctx, font, Theme.bold(tiles[i][1]), tx + 8, y + 19, 1.5F, Theme.TEXT);
		}

		y += tileH + 14;

		this.graph.render(ctx, areaX, y, areaW, graphH, mouseX, mouseY);
		y += graphH + Theme.SPACE_S;

		int rangeW = Math.min(areaW, DashboardStats.RANGE_LABELS.length * RANGE_SEGMENT_WIDTH);
		Tabs.segmented(ctx, font, this.rangeSegments, DashboardStats.RANGE_LABELS, this.stats.range, areaX, y, rangeW,
				mouseX, mouseY);

		if (this.stats.isSwitching())
		{
			Theme.text(ctx, font, "Loading...", areaX + rangeW + Theme.SPACE_S,
					y + (Theme.H_CONTROL - font.lineHeight) / 2 + 1, Theme.TEXT_ASH);
		}

		y += Theme.H_CONTROL + 14;

		Theme.text(ctx, font, Theme.bold("Click your posts to view its stats on the graph"), areaX, y,
				Theme.TEXT_MUTE);
		y += font.lineHeight + 6;

		y = this.renderFilterChips(ctx, areaX, y, areaW, mouseX, mouseY) + 6;

		this.postCardRects.clear();
		this.postCardIds.clear();
		this.postEditRects.clear();

		if (shown.isEmpty())
		{
			String msg = this.stats.posts.isEmpty()
					? "You haven't uploaded anything yet. Hit New post to start."
					: "No posts in this category.";
			Theme.text(ctx, font, Theme.clip(font, msg, areaW), areaX, y, Theme.TEXT_ASH);
		}
		else
		{
			int savedW = this.screen.cardWidth;
			int savedH = this.screen.cardHeight;
			this.screen.cardWidth = cw;
			this.screen.cardHeight = ch;

			// An exception mid-loop must not leave the shared grid card size corrupted for later frames
			try
			{
				for (int i = 0; i < shown.size(); i++)
				{
					int col = i % cols;
					int row = i / cols;
					int cx = areaX + col * (cw + gap);
					int cy = y + row * (ch + gap);

					Rect rect = Rect.pooled(this.postCardPool, i);
					rect.set(cx, cy, cw, ch);
					this.postCardRects.add(rect);
					this.postCardIds.add(shown.get(i).id());

					Rect editRect = Rect.pooled(this.postEditPool, i);
					boolean editable = !this.stats.creditedIds.contains(shown.get(i).id());
					editRect.set(cx + cw - 19, cy + 3, editable ? 16 : 0, 16);
					this.postEditRects.add(editRect);

					if (cy + ch >= scrollTop && cy <= scrollBottom)
					{
						this.screen.browsePage.grid.renderCard(ctx, shown.get(i), cx, cy, -999, -999);

						// Held out of the catalogue until a moderator approves it
						if (this.stats.pendingIds.contains(shown.get(i).id()))
						{
							String badge = "In review";
							int bw = font.width(badge) + 10;
							Theme.roundedRect(ctx, cx + 4, cy + 4, bw, 14, Theme.RADIUS_PILL, 0xF0C8811E);
							Theme.text(ctx, font, badge, cx + 9, cy + 7, 0xFFFFFFFF);
						}

						boolean selected = shown.get(i).id().equals(this.selectedPost);
						boolean hovered = rect.contains(mouseX, mouseY)
								&& mouseY >= scrollTop && mouseY <= scrollBottom;

						if (selected)
						{
							Theme.roundedOutline(ctx, cx, cy, cw, ch, Theme.RADIUS_CARD, Theme.ACCENT_BRIGHT);
						}
						else if (hovered)
						{
							Theme.roundedOutline(ctx, cx, cy, cw, ch, Theme.RADIUS_CARD, Theme.HAIRLINE);
						}

						if (!editable)
						{
							continue;
						}

						boolean editHover = editRect.contains(mouseX, mouseY)
								&& mouseY >= scrollTop && mouseY <= scrollBottom;
						Theme.roundedRect(ctx, editRect.x, editRect.y, editRect.width, editRect.height,
								Theme.RADIUS_PILL, editHover ? 0xF0000000 : 0xB0000000);
						Theme.editIcon(ctx, editRect.x + 2, editRect.y + 2, editHover);
					}
				}
			}
			finally
			{
				this.screen.cardWidth = savedW;
				this.screen.cardHeight = savedH;
			}
		}

		this.claims.render(ctx, areaX, y + postsH, areaW, scrollTop, scrollBottom);

		ctx.disableScissor();

		this.screen.verticalScrollbar(ctx, IndexScreen.SCROLLBAR_DASH, this.screen.dashScroll, this.screen.dashMaxScroll,
				scrollTop, scrollBottom - scrollTop, areaX + areaW);
	}

	// Stats retry and the creator dashboard; the upload page owns the rest of the tab
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.statsRetryButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.stats.load();
			return true;
		}

		if (!this.screen.uploadPage.isOpen())
		{
			if (this.uploadButton.contains(mouseX, mouseY))
			{
				this.screen.uploadPage.setOpen(true);
				this.screen.scroll = 0.0F;
				Theme.click(1.1F);
				return true;
			}

			if (this.graph.mouseClicked(mouseX, mouseY))
			{
				return true;
			}

			int range = mouseY >= this.viewportTop && mouseY <= this.viewportBottom
					? Tabs.hit(this.rangeSegments, mouseX, mouseY) : -1;

			if (range >= 0)
			{
				if (range != this.stats.range)
				{
					Theme.click(0.9F);
					this.stats.selectRange(range);
				}

				return true;
			}

			Category[] all = Category.values();

			for (int i = 0; i < this.filterChips.size() && i < all.length; i++)
			{
				if (this.filterChips.get(i).contains(mouseX, mouseY))
				{
					this.filter = all[i];
					this.screen.dashScroll = 0.0F;
					Theme.click(0.9F);
					return true;
				}
			}

			if (mouseY >= this.viewportTop && mouseY <= this.viewportBottom)
			{
				for (int i = 0; i < this.postEditRects.size() && i < this.postCardIds.size(); i++)
				{
					if (this.postEditRects.get(i).contains(mouseX, mouseY))
					{
						this.screen.postOptionsModal.open(this.postCardIds.get(i));
						Theme.click(0.9F);
						return true;
					}
				}

				for (int i = 0; i < this.postCardRects.size() && i < this.postCardIds.size(); i++)
				{
					if (this.postCardRects.get(i).contains(mouseX, mouseY))
					{
						String id = this.postCardIds.get(i);
						this.selectedPost = id.equals(this.selectedPost) ? null : id;
						this.graph.animStart = System.currentTimeMillis();
						Theme.click(this.selectedPost != null ? 1.1F : 0.9F);
						return true;
					}
				}
			}

			return true;
		}

		return false;
	}

	public void expireStats()
	{
		if (!this.stats.ok
				|| System.currentTimeMillis() - this.stats.loadedAt > IndexScreen.MY_STATS_STALE_MS)
		{
			this.stats.loaded = false;
		}
	}

	// Mirrors the wrap in renderFilterChips, which cannot run before the dashboard is laid out
	private int measureFilterChipsHeight(int x, int w)
	{
		Font font = this.screen.font();
		int cx = x;
		int rows = 0;

		for (Category value : Category.values())
		{
			int chipW = font.width(Theme.bold(value.label())) + 12;

			if (cx + chipW > x + w)
			{
				cx = x;
				rows++;
			}

			cx += chipW + IndexScreen.CHIP_GAP;
		}

		return (rows + 1) * IndexScreen.CHIP_HEIGHT + rows * IndexScreen.CHIP_GAP;
	}

	private int renderFilterChips(GuiGraphics ctx, int x, int y, int w, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		Category[] all = Category.values();

		while (this.filterChips.size() < all.length)
		{
			this.filterChips.add(new Rect());
		}

		int cx = x;
		int cy = y;

		for (int i = 0; i < all.length; i++)
		{
			Category value = all[i];
			String label = Theme.bold(value.label());
			int chipW = font.width(label) + 12;

			if (cx + chipW > x + w)
			{
				cx = x;
				cy += IndexScreen.CHIP_HEIGHT + IndexScreen.CHIP_GAP;
			}

			Rect rect = this.filterChips.get(i);
			rect.set(cx, cy, chipW, IndexScreen.CHIP_HEIGHT);
			boolean active = value == this.filter;
			boolean hovered = rect.contains(mouseX, mouseY);
			int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
			Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);
			Theme.text(ctx, font, label, rect.x + 6, rect.y + (IndexScreen.CHIP_HEIGHT - font.lineHeight) / 2 + 1,
					active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);

			cx += chipW + IndexScreen.CHIP_GAP;
		}

		return cy + IndexScreen.CHIP_HEIGHT;
	}
}

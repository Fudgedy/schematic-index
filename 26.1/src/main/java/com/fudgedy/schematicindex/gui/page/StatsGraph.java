package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

class StatsGraph
{
	private final DashboardPage page;
	private final IndexScreen screen;
	private int mode;
	long animStart;
	private final Rect legendViewsRect = new Rect();
	private final Rect legendDownloadsRect = new Rect();
	private final Rect legendLikesRect = new Rect();
	private final Rect legendStarsRect = new Rect();

	StatsGraph(DashboardPage page, IndexScreen screen)
	{
		this.page = page;
		this.screen = screen;
	}

	void render(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		DashboardStats stats = this.page.stats;
		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.SURFACE_CARD);

		int legendChipH = font.lineHeight + 4;
		int legendY = y + 6;
		int legendToPlot = 11;
		int plotToLabel = 8;
		int labelH = font.lineHeight;
		int bottomGap = 5;

		int plotX = x + 32;
		int plotY = legendY + legendChipH + legendToPlot;
		int plotRight = x + w - 12;
		int plotBottom = y + h - bottomGap - labelH - plotToLabel;
		int plotW = plotRight - plotX;
		int plotH = plotBottom - plotY;

		int lx = x + 10;
		lx += this.legendChip(ctx, this.legendViewsRect, lx, legendY, "Views", Theme.STAT_VIEWS, this.mode == 1, mouseX, mouseY);
		lx += this.legendChip(ctx, this.legendDownloadsRect, lx, legendY, "Downloads", Theme.STAT_DOWNLOADS, this.mode == 2,
				mouseX, mouseY);
		lx += this.legendChip(ctx, this.legendLikesRect, lx, legendY, "Likes", Theme.STAT_LIKES, this.mode == 3, mouseX, mouseY);
		this.legendChip(ctx, this.legendStarsRect, lx, legendY, "Stars", Theme.STAT_STARS, this.mode == 4, mouseX, mouseY);

		if (stats.days.length == 0 || plotH < 12)
		{
			String msg = "No activity yet.";
			Theme.text(ctx, font, msg, x + (w - font.width(msg)) / 2, plotY + plotH / 2 - 4, Theme.TEXT_ASH);
			return;
		}

		boolean showViews = this.mode == 0 || this.mode == 1;
		boolean showDownloads = this.mode == 0 || this.mode == 2;
		boolean showLikes = this.mode == 0 || this.mode == 3;
		boolean showStars = this.mode == 0 || this.mode == 4;

		int[][] selected = this.page.selectedPost != null ? stats.postSeries.get(this.page.selectedPost) : null;
		int[] viewSeries = selected != null ? selected[0] : stats.viewSeries;
		int[] downloadSeries = selected != null ? selected[1] : stats.downloadSeries;
		int[] likeSeries = selected != null ? selected[2] : stats.likeSeries;
		int[] starSeries = selected != null && selected.length > 3 ? selected[3] : stats.starSeries;

		int rawMax = 1;
		if (showViews)
		{
			for (int v : viewSeries) rawMax = Math.max(rawMax, v);
		}
		if (showDownloads)
		{
			for (int v : downloadSeries) rawMax = Math.max(rawMax, v);
		}
		if (showLikes)
		{
			for (int v : likeSeries) rawMax = Math.max(rawMax, v);
		}
		if (showStars)
		{
			for (int v : starSeries) rawMax = Math.max(rawMax, v);
		}
		int max = niceCeil(rawMax);

		for (int g = 0; g <= 2; g++)
		{
			int value = max * g / 2;
			int gy = plotBottom - (int) Math.round((double) value / max * plotH);
			ctx.fill(plotX, gy, plotRight, gy + 1, g == 0 ? Theme.HAIRLINE : 0x18FFFFFF);
			String lbl = SchematicEntry.compact(value);
			Theme.text(ctx, font, lbl, plotX - 5 - font.width(lbl), gy - 3, Theme.TEXT_ASH);
		}

		float animT = this.anim();

		int n = stats.days.length;

		if (n == 1)
		{
			this.renderSinglePoint(ctx, max, plotX, plotY, plotW, plotH, animT, showViews ? viewSeries : null,
					showDownloads ? downloadSeries : null, showLikes ? likeSeries : null, showStars ? starSeries : null);
		}
		else
		{
			if (showViews)
			{
				this.plotSeries(ctx, viewSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_VIEWS, animT);
			}
			if (showDownloads)
			{
				this.plotSeries(ctx, downloadSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_DOWNLOADS, animT);
			}
			if (showLikes)
			{
				this.plotSeries(ctx, likeSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_LIKES, animT);
			}
			if (showStars)
			{
				this.plotSeries(ctx, starSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_STARS, animT);
			}
		}

		this.axisLabels(ctx, stats, plotX, plotW, plotBottom + plotToLabel);

		if (mouseX >= plotX - 4 && mouseX <= plotRight + 4 && mouseY >= plotY - 6 && mouseY <= plotBottom + 6)
		{
			int hi = n == 1 ? 0 : Math.max(0, Math.min(n - 1, (int) Math.round((double) (mouseX - plotX) / plotW * (n - 1))));
			int hx = pointX(hi, n, plotX, plotW);

			ctx.fill(hx, plotY, hx + 1, plotBottom, 0x33FFFFFF);

			List<String> rows = new ArrayList<>();
			List<Integer> rowColors = new ArrayList<>();

			if (showViews && hi < viewSeries.length)
			{
				int py = this.plotValueY(viewSeries[hi], max, plotY, plotH, animT);
				this.dot(ctx, hx, py, Theme.STAT_VIEWS);
				rows.add("Views: " + SchematicEntry.compact(viewSeries[hi]));
				rowColors.add(Theme.STAT_VIEWS);
			}
			if (showDownloads && hi < downloadSeries.length)
			{
				int py = this.plotValueY(downloadSeries[hi], max, plotY, plotH, animT);
				this.dot(ctx, hx, py, Theme.STAT_DOWNLOADS);
				rows.add("Downloads: " + SchematicEntry.compact(downloadSeries[hi]));
				rowColors.add(Theme.STAT_DOWNLOADS);
			}
			if (showLikes && hi < likeSeries.length)
			{
				int py = this.plotValueY(likeSeries[hi], max, plotY, plotH, animT);
				this.dot(ctx, hx, py, Theme.STAT_LIKES);
				rows.add("Likes: " + SchematicEntry.compact(likeSeries[hi]));
				rowColors.add(Theme.STAT_LIKES);
			}
			if (showStars && hi < starSeries.length)
			{
				int py = this.plotValueY(starSeries[hi], max, plotY, plotH, animT);
				this.dot(ctx, hx, py, Theme.STAT_STARS);
				rows.add("Stars: " + SchematicEntry.compact(starSeries[hi]));
				rowColors.add(Theme.STAT_STARS);
			}

			String date = stats.weekly ? "Week of " + fullDay(stats.days[hi]) : fullDay(stats.days[hi]);
			int pad = 6;
			int rowH = font.lineHeight + 2;
			int textWidth = font.width(Theme.bold(date));

			for (String row : rows)
			{
				textWidth = Math.max(textWidth, 10 + font.width(row));
			}

			int boxW = textWidth + pad * 2;
			int boxH = pad + font.lineHeight + 4 + rows.size() * rowH + pad - 2;
			int boxX = hx + 10 + boxW > x + w ? hx - 10 - boxW : hx + 10;
			boxX = Math.max(x + 2, Math.min(boxX, x + w - boxW - 2));
			int boxY = Math.max(y + 2, Math.min(mouseY - boxH / 2, y + h - boxH - 2));

			Theme.roundedRect(ctx, boxX, boxY, boxW, boxH, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
			Theme.roundedOutline(ctx, boxX, boxY, boxW, boxH, Theme.RADIUS_CARD, Theme.HAIRLINE);

			int ty = boxY + pad;
			Theme.text(ctx, font, Theme.bold(date), boxX + pad, ty, Theme.TEXT);
			ty += font.lineHeight + 4;

			for (int i = 0; i < rows.size(); i++)
			{
				ctx.fill(boxX + pad, ty + 2, boxX + pad + 4, ty + 6, rowColors.get(i));
				Theme.text(ctx, font, rows.get(i), boxX + pad + 10, ty, Theme.TEXT_MUTE);
				ty += rowH;
			}
		}
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.legendViewsRect.contains(mouseX, mouseY))
		{
			this.mode = this.mode == 1 ? 0 : 1;
			this.animStart = System.currentTimeMillis();
			Theme.click(0.9F);
			return true;
		}

		if (this.legendDownloadsRect.contains(mouseX, mouseY))
		{
			this.mode = this.mode == 2 ? 0 : 2;
			this.animStart = System.currentTimeMillis();
			Theme.click(0.9F);
			return true;
		}

		if (this.legendLikesRect.contains(mouseX, mouseY))
		{
			this.mode = this.mode == 3 ? 0 : 3;
			this.animStart = System.currentTimeMillis();
			Theme.click(0.9F);
			return true;
		}

		if (this.legendStarsRect.contains(mouseX, mouseY))
		{
			this.mode = this.mode == 4 ? 0 : 4;
			this.animStart = System.currentTimeMillis();
			Theme.click(0.9F);
			return true;
		}

		return false;
	}

	private void dot(GuiGraphicsExtractor ctx, int centreX, int centreY, int color)
	{
		Theme.roundedRect(ctx, centreX - 3, centreY - 3, 6, 6, 3, 0xFFFFFFFF);
		Theme.roundedRect(ctx, centreX - 2, centreY - 2, 4, 4, 2, color);
	}

	private static String fullDay(String iso)
	{
		try
		{
			String[] parts = iso.split("-");
			String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
			int month = Math.max(1, Math.min(12, Integer.parseInt(parts[1])));
			return months[month - 1] + " " + Integer.parseInt(parts[2]) + ", " + parts[0];
		}
		catch (Exception e)
		{
			return iso;
		}
	}

	private int legendChip(GuiGraphicsExtractor ctx, Rect rect, int x, int y, String label, int color, boolean active,
			int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		int chipW = 13 + font.width(label) + 8;
		int chipH = font.lineHeight + 4;
		rect.set(x, y, chipW, chipH);
		boolean hovered = rect.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, x, y, chipW, chipH, Theme.RADIUS_PILL,
				active || hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, chipW, chipH, Theme.RADIUS_PILL, active ? Theme.ACCENT_BRIGHT : Theme.HAIRLINE);
		ctx.fill(x + 6, y + chipH / 2 - 2, x + 10, y + chipH / 2 + 2, color);
		Theme.text(ctx, font, label, x + 13, y + (chipH - font.lineHeight) / 2 + 1, active ? Theme.TEXT : Theme.TEXT_MUTE);
		return chipW + 6;
	}

	private float anim()
	{
		float t = Math.max(0.0F, Math.min(1.0F, (System.currentTimeMillis() - this.animStart) / 350.0F));
		return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
	}

	private static int niceCeil(int value)
	{
		if (value <= 4)
		{
			return Math.max(1, value);
		}

		int pow = 1;

		while (pow * 10 < value)
		{
			pow *= 10;
		}

		for (int m : new int[]{1, 2, 5, 10})
		{
			if (m * pow >= value)
			{
				return m * pow;
			}
		}

		return value;
	}

	private void plotSeries(GuiGraphicsExtractor ctx, int[] series, int max, int plotX, int plotY, int plotW, int plotH,
			int color, float animT)
	{
		if (series.length < 2)
		{
			return;
		}

		int n = series.length;
		int prevX = 0;
		int prevY = 0;

		for (int i = 0; i < n; i++)
		{
			int px = plotX + (int) Math.round((double) i / (n - 1) * plotW);
			int py = this.plotValueY(series[i], max, plotY, plotH, animT);

			if (i > 0)
			{
				this.screen.drawLine(ctx, prevX, prevY, px, py, color);
			}

			prevX = px;
			prevY = py;
		}
	}

	// The 2px floor keeps an all-zero series readable as a flat line instead of merging into the axis
	private int plotValueY(int value, int max, int plotY, int plotH, float animT)
	{
		int usableH = Math.max(1, plotH - 2);
		return plotY + plotH - 2 - (int) Math.round((double) value / max * usableH * animT);
	}

	// A lone point sits mid-plot, so a single day reads as a value rather than an empty chart
	private static int pointX(int index, int count, int plotX, int plotW)
	{
		return count == 1 ? plotX + plotW / 2 : plotX + (int) Math.round((double) index / (count - 1) * plotW);
	}

	// Stems grouped around the centre, one per visible series, so a single day's values stay apart
	private void renderSinglePoint(GuiGraphicsExtractor ctx, int max, int plotX, int plotY, int plotW, int plotH, float animT,
			int[]... series)
	{
		int[] colors = {Theme.STAT_VIEWS, Theme.STAT_DOWNLOADS, Theme.STAT_LIKES, Theme.STAT_STARS};
		int visible = 0;

		for (int[] values : series)
		{
			if (values != null && values.length > 0)
			{
				visible++;
			}
		}

		int step = Theme.SPACE_M;
		int x = plotX + plotW / 2 - (visible - 1) * step / 2;
		int baseline = plotY + plotH - 2;

		for (int i = 0; i < series.length; i++)
		{
			if (series[i] == null || series[i].length == 0)
			{
				continue;
			}

			int y = this.plotValueY(series[i][0], max, plotY, plotH, animT);
			ctx.fill(x, y, x + 1, baseline + 1, colors[i]);
			this.dot(ctx, x, y, colors[i]);
			x += step;
		}
	}

	// Every point is labelled when they fit; a longer range labels its ends and middle
	private void axisLabels(GuiGraphicsExtractor ctx, DashboardStats stats, int plotX, int plotW, int y)
	{
		Font font = this.screen.font();
		String[] days = stats.days;
		int n = days.length;

		if (n == 1)
		{
			String label = stats.range == 0 ? "Today" : shortDay(days[0]);
			Theme.text(ctx, font, label, plotX + (plotW - font.width(label)) / 2, y, Theme.TEXT_ASH);
			return;
		}

		int labelW = font.width("00/00") + Theme.SPACE_S;
		int count = n * labelW <= plotW ? n : 3;
		int right = plotX + plotW;

		for (int k = 0; k < count; k++)
		{
			int index = count == n ? k : k * (n - 1) / (count - 1);
			String label = shortDay(days[index]);
			int width = font.width(label);
			int x = Math.max(plotX, Math.min(right - width, pointX(index, n, plotX, plotW) - width / 2));
			Theme.text(ctx, font, label, x, y, Theme.TEXT_ASH);
		}
	}

	private static String shortDay(String iso)
	{
		return iso.length() >= 10 ? iso.substring(5).replace('-', '/') : iso;
	}
}

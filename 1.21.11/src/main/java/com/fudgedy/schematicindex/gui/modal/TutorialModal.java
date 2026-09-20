package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TutorialModal
{
	private static final String[][] TUTORIAL = {
			{"Welcome to The Schematic Index",
					"Browse the latest community schematics via posts and download them directly into your "
							+ "schematics folder."},
			{"Find your way around",
					"Use the left bar to switch between Browse, Premium, Saved, Mapart, Upload, Cosmetics and "
							+ "Settings."},
			{"Filter by category",
					"Filter posts by seeing exactly what kind of schematic you want with our tags."},
			{"Search",
					"Use the search bar to search for schematic names, the posters, and even the designers."},
			{"Earn Shards",
					"Click on the shards tab in the top right, and earn Shards with a daily streak or earn them "
							+ "through completing quests. You can spend shards on different cosmetics and features "
							+ "around the mod."},
			{"Explore posts",
					"Click on a post to view more images of the build or even preview it in 3D. You can "
							+ "download the schematic or save the post for later."}};

	private static final int SHARD_STEP = 4;
	private static final int SHARD_STEP_MARGIN = 3;

	private final IndexScreen screen;
	private boolean open;
	private boolean shown;
	// Stepped aside while the shard panel the card points at is open; it comes back when that closes
	private boolean suspended;
	private int step;
	private final Rect next = new Rect();
	private final Rect back = new Rect();
	private final Rect skip = new Rect();

	public TutorialModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void maybeStart()
	{
		if (!Settings.tutorialSeen() && !this.shown)
		{
			this.shown = true;
			this.open = true;
			this.step = 0;
			this.screen.page = IndexScreen.Page.BROWSE;
		}
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (this.suspended && !this.screen.shardPanel.isOpen())
		{
			this.suspended = false;
			this.open = true;
		}

		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		int[] target = this.target();

		if (target == null)
		{
			ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);
		}
		else
		{
			int tx = target[0];
			int ty = target[1];
			int tw = target[2];
			int th = target[3];
			ctx.fill(0, 0, this.screen.width, ty, Theme.SCRIM);
			ctx.fill(0, ty + th, this.screen.width, this.screen.height, Theme.SCRIM);
			ctx.fill(0, ty, tx, ty + th, Theme.SCRIM);
			ctx.fill(tx + tw, ty, this.screen.width, ty + th, Theme.SCRIM);

			renderPulse(ctx, tx, ty, tw, th);
		}

		String[] card = TUTORIAL[this.step];
		int cardWidth = 260;
		int pad = 12;
		List<String> body = this.screen.wrap(card[1], cardWidth - pad * 2, 4);
		int cardHeight = pad + font.lineHeight + 4 + body.size() * (font.lineHeight + 1) + 10 + 16 + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = this.screen.height - cardHeight - 24;

		Theme.roundedRect(ctx, x, y, cardWidth, cardHeight, Theme.RADIUS_MODAL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, cardWidth, cardHeight, Theme.RADIUS_MODAL, Theme.HAIRLINE);

		int ty = y + pad;
		Theme.text(ctx, font, Theme.bold(card[0]), x + pad, ty, Theme.TEXT);
		ty += font.lineHeight + 4;

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + pad, ty, Theme.TEXT_MUTE);
			ty += font.lineHeight + 1;
		}

		int buttonY = y + cardHeight - pad - 16;
		boolean last = this.step == TUTORIAL.length - 1;
		String nextLabel = last ? "Done" : "Next";
		int nextWidth = font.width(Theme.bold(nextLabel)) + 20;
		int skipWidth = font.width(Theme.bold("Skip")) + 16;

		this.skip.set(x + pad, buttonY, skipWidth, 16);
		this.next.set(x + cardWidth - pad - nextWidth, buttonY, nextWidth, 16);
		Buttons.pill(ctx, font, this.skip, "Skip", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.next, nextLabel, mouseX, mouseY, true);

		if (this.step > 0)
		{
			int backWidth = font.width(Theme.bold("Back")) + 16;
			this.back.set(this.next.x - 6 - backWidth, buttonY, backWidth, 16);
			Buttons.pill(ctx, font, this.back, "Back", mouseX, mouseY, false);
		}
		else
		{
			this.back.set(0, 0, 0, 0);
		}

		int dots = TUTORIAL.length;
		int dotGap = 8;
		int dotsWidth = dots * 3 + (dots - 1) * (dotGap - 3);
		int dotX = x + (cardWidth - dotsWidth) / 2;
		int dotY = buttonY + (16 - 3) / 2;

		for (int i = 0; i < dots; i++)
		{
			Theme.roundedRect(ctx, dotX, dotY, 3, 3, 1, i == this.step ? Theme.ACCENT_BRIGHT : Theme.TEXT_ASH);
			dotX += dotGap;
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.step == SHARD_STEP && this.screen.shardPill().contains(mouseX, mouseY))
		{
			this.open = false;
			this.suspended = true;
			this.screen.openShardPanel();
			return true;
		}

		if (this.next.contains(mouseX, mouseY))
		{
			this.advance();
		}
		else if (this.back.contains(mouseX, mouseY))
		{
			this.retreat();
		}
		else if (this.skip.contains(mouseX, mouseY))
		{
			this.skip();
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		switch (event.key())
		{
			case 256 -> this.skip();
			case 257, 335, 262 -> this.advance();
			case 263 -> this.retreat();
			default -> {
			}
		}

		return true;
	}

	private void advance()
	{
		Theme.click(1.1F);

		if (this.step >= TUTORIAL.length - 1)
		{
			this.finish(false);
		}
		else
		{
			this.step++;
		}
	}

	private void retreat()
	{
		if (this.step == 0)
		{
			return;
		}

		Theme.click(0.9F);
		this.step--;
	}

	private void skip()
	{
		Theme.click(0.9F);
		this.finish(true);
	}

	public Rect[] pressButtons()
	{
		return new Rect[]{this.next, this.back, this.skip};
	}

	private int @Nullable [] target()
	{
		return switch (this.step)
		{
			case 1 -> {
				int top = this.screen.railRects.isEmpty()
						? IndexScreen.TOP_BAR_HEIGHT : this.screen.railRects.get(0).y - 3;
				int bottom = this.screen.railRects.isEmpty() ? this.screen.height
						: this.screen.railRects.get(this.screen.railRects.size() - 1).y
								+ this.screen.railRects.get(this.screen.railRects.size() - 1).height + 3;
				yield new int[]{0, top, IndexScreen.RAIL_WIDTH, bottom - top};
			}

			case 2 -> new int[]{this.screen.contentX - 4, IndexScreen.TOP_BAR_HEIGHT,
					this.screen.contentWidth + 8, this.screen.chipRowHeight};

			case 3 -> {
				int searchWidth = this.screen.searchWidth();
				int searchX = this.screen.contentX + (this.screen.contentWidth - searchWidth) / 2;
				int searchY = (IndexScreen.TOP_BAR_HEIGHT - 16) / 2;
				yield new int[]{searchX - 2, searchY - 2, searchWidth + 4, 20};
			}

			case SHARD_STEP -> {
				Rect pill = this.screen.shardPill();

				// Unverified players have no pill yet, so the card stands alone over a full scrim
				if (pill.width == 0)
				{
					yield null;
				}

				yield new int[]{pill.x - SHARD_STEP_MARGIN, pill.y - SHARD_STEP_MARGIN,
						pill.width + SHARD_STEP_MARGIN * 2, pill.height + SHARD_STEP_MARGIN * 2};
			}

			// An empty grid has no first card to ring, so the card stands alone over a full scrim
			case 5 -> this.screen.hasVisiblePosts() ? new int[]{this.screen.contentX, this.screen.gridTop,
					this.screen.cardWidth, this.screen.cardHeight} : null;
			default -> null;
		};
	}

	// An accent ring that breathes around the highlighted area, so the eye lands on what the card describes
	private static void renderPulse(GuiGraphics ctx, int x, int y, int width, int height)
	{
		float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
		int alpha = 0x70 + Math.round(0x8F * pulse);
		int ring = (alpha << 24) | (Theme.ACCENT_BRIGHT & 0xFFFFFF);
		int grow = Math.round(pulse * 2.0F);
		Theme.roundedOutline(ctx, x - 1 - grow, y - 1 - grow, width + 2 + grow * 2, height + 2 + grow * 2,
				Theme.RADIUS_CARD, ring);
	}

	private void finish(boolean skipped)
	{
		this.open = false;
		Usage.once(skipped ? "tutorial_skipped" : "tutorial_finished");
		Settings.markTutorialSeen();
		// The shard primer waits behind the tutorial
		this.screen.maybeShowShardWelcome();
	}
}

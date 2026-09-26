package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.update.UpdateGate;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TutorialModal
{
	private static final String[][] TUTORIAL = {
			{"Welcome to The Schematic Index",
					"Community schematics, ready to preview and download straight into your schematics folder."},
			{"Browse and search",
					"Scroll the feed, filter by category, and search titles, creators and designers."},
			{"Preview a build",
					"This is today's Build of the Day. Drag to orbit and scroll to zoom."},
			{"Download it",
					"Press Download to save it into your schematics folder, ready for Litematica."},
			{"Earn Shards",
					"Claim a daily reward and finish quests for Shards, then spend them on nametag cosmetics."}};

	private static final String NO_PICK_TEXT = "Click any card to preview it in 3D.";
	private static final int BROWSE_STEP = 1;
	private static final int PREVIEW_STEP = 2;
	private static final int DOWNLOAD_STEP = 3;
	private static final int SHARD_STEP = 4;
	private static final int SHARD_STEP_MARGIN = 3;

	private final IndexScreen screen;
	private boolean open;
	private boolean shown;
	// Stepped aside while the shard panel the card points at is open; it comes back when that closes
	private boolean suspended;
	private int step;
	// The Build of the Day this run opened, so moving on can close exactly that preview
	private @Nullable SchematicEntry featured;
	private boolean featuredMissing;
	private int run;
	private final Rect card = new Rect();
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
			this.begin();
			this.screen.page = IndexScreen.Page.BROWSE;
		}
	}

	// Replayed from Help or Settings whatever the seen flag says
	public void start()
	{
		this.screen.detailView.close();
		this.screen.switchPage(IndexScreen.Page.BROWSE);
		this.begin();
	}

	private void begin()
	{
		this.shown = true;
		this.open = true;
		this.suspended = false;
		this.featured = null;
		this.featuredMissing = false;
		this.run++;
		this.enter(0);
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
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

		// The preview steps leave the build itself unscrimmed, since it is what the card talks about
		if (this.previewShowing())
		{
			this.renderCard(ctx, font, mouseX, mouseY);
			return;
		}

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

		this.renderCard(ctx, font, mouseX, mouseY);
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
		else if (this.previewShowing() && !this.card.contains(mouseX, mouseY))
		{
			// Lets the drag the card asks for reach the preview underneath
			return false;
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

	public Rect[] pressButtons()
	{
		return new Rect[]{this.next, this.back, this.skip};
	}

	private void renderCard(GuiGraphicsExtractor ctx, Font font, int mouseX, int mouseY)
	{
		int cardWidth = 260;
		int pad = 12;
		List<String> body = this.screen.wrap(this.bodyText(), cardWidth - pad * 2, 4);
		int cardHeight = pad + font.lineHeight + 4 + body.size() * (font.lineHeight + 1) + 10 + 16 + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = this.screen.height - cardHeight - 24;
		this.card.set(x, y, cardWidth, cardHeight);

		Theme.roundedRect(ctx, x, y, cardWidth, cardHeight, Theme.RADIUS_MODAL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, cardWidth, cardHeight, Theme.RADIUS_MODAL, Theme.HAIRLINE);

		int textY = y + pad;
		Theme.text(ctx, font, Theme.bold(TUTORIAL[this.step][0]), x + pad, textY, Theme.TEXT);
		textY += font.lineHeight + 4;

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + pad, textY, Theme.TEXT_MUTE);
			textY += font.lineHeight + 1;
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

	private void advance()
	{
		Theme.click(1.1F);

		if (this.step >= TUTORIAL.length - 1)
		{
			this.finish(false);
		}
		else
		{
			this.enter(this.step + 1);
		}
	}

	private void retreat()
	{
		if (this.step == 0)
		{
			return;
		}

		Theme.click(0.9F);
		this.enter(this.step - 1);
	}

	private void skip()
	{
		Theme.click(0.9F);
		this.finish(true);
	}

	private void enter(int target)
	{
		this.step = target;
		Usage.once("tut_step_" + (target + 1));

		if (target == PREVIEW_STEP && this.featured == null && !this.featuredMissing)
		{
			this.loadFeatured();
		}

		if (target == PREVIEW_STEP && this.featured != null && !this.screen.detailView.isOpen())
		{
			this.screen.openDetail(this.featured);
		}

		// The shard pill lives in the header, which the preview card would cover
		if ((target < PREVIEW_STEP || target == SHARD_STEP) && this.featuredShowing())
		{
			this.screen.detailView.close();
		}
	}

	private void loadFeatured()
	{
		int started = this.run;
		Net.submit(() -> {
			JsonObject body = Backend.getJsonAnon("/featured/today");
			JsonObject post = Json.objectOf(body, "post");
			SchematicEntry entry = null;

			try
			{
				entry = post != null && post.has("id") ? Backend.parsePost(post) : null;
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.debug("Build of the Day parse failed", e);
			}

			SchematicEntry pick = entry;
			Minecraft.getInstance().execute(() -> this.onFeatured(started, pick));
		});
	}

	private void onFeatured(int started, @Nullable SchematicEntry pick)
	{
		if (started != this.run)
		{
			return;
		}

		this.featured = pick;
		this.featuredMissing = pick == null;

		if (pick != null && this.open && this.step == PREVIEW_STEP && !this.screen.detailView.isOpen())
		{
			this.screen.openDetail(pick);
		}
	}

	private String bodyText()
	{
		if (this.step == PREVIEW_STEP && this.featured == null)
		{
			return NO_PICK_TEXT;
		}

		return TUTORIAL[this.step][1];
	}

	private boolean featuredShowing()
	{
		SchematicEntry open = this.screen.detailView.entry();
		return this.featured != null && open != null && this.featured.id().equals(open.id());
	}

	private boolean previewShowing()
	{
		return (this.step == PREVIEW_STEP || this.step == DOWNLOAD_STEP) && this.screen.detailView.isOpen();
	}

	private int @Nullable [] target()
	{
		return switch (this.step)
		{
			case BROWSE_STEP -> {
				int searchY = (IndexScreen.TOP_BAR_HEIGHT - 16) / 2;
				yield new int[]{this.screen.contentX - 4, searchY - 2, this.screen.contentWidth + 8,
						IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight - searchY + 2};
			}

			// An empty grid has no first card to ring, so the card stands alone over a full scrim
			case PREVIEW_STEP -> this.featured == null && this.screen.browsePage.hasVisiblePosts()
					? new int[]{this.screen.contentX, this.screen.gridTop, this.screen.cardWidth, this.screen.cardHeight}
					: null;

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

			default -> null;
		};
	}

	// An accent ring that breathes around the highlighted area, so the eye lands on what the card describes
	private static void renderPulse(GuiGraphicsExtractor ctx, int x, int y, int width, int height)
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

		// A new player has just been shown around, so this version's notes would only repeat it
		if (Settings.text(SettingsKeys.LAST_SEEN_VERSION, "").isBlank())
		{
			Settings.setText(SettingsKeys.LAST_SEEN_VERSION, UpdateGate.currentVersion());
		}

		// The shard primer waits behind the tutorial
		this.screen.maybeShowShardWelcome();
	}
}

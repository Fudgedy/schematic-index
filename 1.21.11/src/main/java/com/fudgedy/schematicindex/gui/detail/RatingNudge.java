package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.Keybinds;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Notices;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.widget.Stars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// After a download the player is asked once per post to rate it, by the rate modal over the Index or else by the
// detail view's stars shimmering; nudged post ids persist
public final class RatingNudge
{
	private static final long PLAY_MS = 1600L;
	private static final long DURATION_MS = PLAY_MS * 2;
	private static final int REMEMBERED = 300;
	private static final Identifier GLINT = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/star_glint_anim.png");
	private static final Identifier BURST = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "textures/gui/star_burst_anim.png");
	private static final int GLINT_SIZE = 20;
	private static final int GLINT_FRAMES = 8;
	private static final int GLINT_FRAME_MS = 50;
	private static final int STAGGER_MS = 150;
	private static final int BURST_SIZE = 16;
	private static final int BURST_CENTRE = 7;
	private static final int BURST_FRAMES = 5;
	private static final int BURST_FRAME_MS = 60;
	private static final int BURST_START_MS = 1000;
	// Star and outer point per burst, a different three tips on each play so the second one reads fresh
	private static final int[][][] BURST_TIPS = { { { 1, 0 }, { 3, 1 }, { 4, 4 } }, { { 0, 1 }, { 2, 0 }, { 4, 0 } } };

	private static final Map<String, SchematicEntry> TOAST_ON_DONE = new HashMap<>();
	private static final Set<String> DUE = new HashSet<>();
	private static @Nullable LinkedHashSet<String> nudged;
	private static @Nullable String playingId;
	private static long playingSince;

	private RatingNudge()
	{
	}

	// Only a single download toasts; a batch keeps its one summary toast
	public static void track(SchematicEntry entry)
	{
		TOAST_ON_DONE.put(entry.id(), entry);
	}

	public static void failed(String postId)
	{
		TOAST_ON_DONE.remove(postId);
	}

	public static void downloaded(String postId)
	{
		boolean fresh = !nudged().contains(postId);

		if (fresh)
		{
			DUE.add(postId);
		}

		SchematicEntry entry = TOAST_ON_DONE.remove(postId);

		if (entry == null)
		{
			return;
		}

		String message = entry.title() + " saved to your schematics folder";
		ItemStack icon = new ItemStack(Items.STRUCTURE_BLOCK);
		IndexScreen screen = Keybinds.currentScreen() instanceof IndexScreen index ? index : null;

		if (hasRated(screen, entry) || isOwn(entry) || !Settings.flag(SettingsKeys.RATE_PROMPT, true))
		{
			Toasts.push("Downloaded", message, icon);
			return;
		}

		if (fresh && screen != null && canPrompt(screen))
		{
			SchematicIndexMod.LOGGER.debug("Asking for a rating of {}", postId);
			DUE.remove(postId);
			remember(postId);
			Toasts.push("Downloaded", message, icon);
			screen.rateModal.open(entry);
			return;
		}

		Toasts.pushAction("Downloaded", message, icon, "Rate it", () -> rate(entry));
	}

	// A download that finishes with the Index closed never reaches its frame poll, so the client tick settles it
	public static void tick()
	{
		if (TOAST_ON_DONE.isEmpty())
		{
			return;
		}

		for (String postId : List.copyOf(TOAST_ON_DONE.keySet()))
		{
			Download.Progress progress = Download.progress(postId);

			if (progress == null || progress.state() == Download.State.FAILED)
			{
				failed(postId);
			}
			else if (progress.state() == Download.State.DONE)
			{
				downloaded(postId);
			}
		}
	}

	// value is in half-stars; the prompt reports its own failures, since the detail view may not be open
	public static void submit(IndexScreen screen, SchematicEntry entry, int value)
	{
		SchematicIndexMod.LOGGER.debug("Rating {} as {} from the download prompt", entry.id(), value);
		Net.submit(() -> {
			Backend.ApiResult result = Backend.rate(entry.id(), value);
			Minecraft.getInstance().execute(() -> {
				if (result.ok())
				{
					Shards.pokeSoon();
					screen.detailView.applyRating(entry.id(), result.body(), value);
					return;
				}

				SchematicIndexMod.LOGGER.warn("Prompt rating of {} failed with status {}", entry.id(), result.status());
				Toasts.refusal(result, Errors.RATE_PROMPT);
			});
		});
	}

	// Progress through the shimmer for the star row being drawn, or -1 when it is idle
	static float progress(String postId, int myStars)
	{
		long now = System.currentTimeMillis();

		if (DUE.remove(postId) && myStars == 0)
		{
			playingId = postId;
			playingSince = now;
			remember(postId);
		}

		if (!postId.equals(playingId) || now - playingSince >= DURATION_MS)
		{
			return -1.0F;
		}

		return (now - playingSince) / (float) DURATION_MS;
	}

	// Hovering or rating means the nudge landed, so the rest of it would only get in the way
	static void stop(String postId)
	{
		if (postId.equals(playingId))
		{
			playingId = null;
		}
	}

	// Each star pings in turn, then sparkles pop at three tips; played twice
	static void shine(GuiGraphics ctx, Font font, int x, int y, float scale, float progress)
	{
		int cell = Stars.cellWidth(font, scale);
		int glyphW = Math.round(font.width(Stars.FULL) * scale);
		int glyphH = Math.round(font.lineHeight * scale) + 2;
		long elapsed = Math.round(progress * DURATION_MS);
		int play = (int) Math.min(1L, elapsed / PLAY_MS);
		int t = (int) (elapsed % PLAY_MS);
		// The strips are drawn against a 20 px star, which is glyphH at the detail view's scale; others stretch to match
		int glint = glyphH;

		for (int i = 0; i < 5; i++)
		{
			int local = t - i * STAGGER_MS;

			if (local < 0 || local >= GLINT_FRAMES * GLINT_FRAME_MS)
			{
				continue;
			}

			int cx = x + i * cell + glyphW / 2;
			int cy = y + glyphH / 2;
			Theme.frame(ctx, GLINT, cx - glint / 2, cy - glint / 2, glint, GLINT_SIZE, local / GLINT_FRAME_MS, GLINT_FRAMES);
		}

		int burst = t - BURST_START_MS;

		if (burst < 0 || burst >= BURST_FRAMES * BURST_FRAME_MS)
		{
			return;
		}

		int size = Math.round(BURST_SIZE * glyphH / (float) GLINT_SIZE);
		int centre = Math.round(BURST_CENTRE * glyphH / (float) GLINT_SIZE);
		float radius = glyphH / 2.0F - 1.0F;

		for (int[] tip : BURST_TIPS[play])
		{
			double angle = Math.toRadians(-90 + 72 * tip[1]);
			int tipX = Math.round(x + tip[0] * cell + glyphW / 2.0F + (float) Math.cos(angle) * radius);
			int tipY = Math.round(y + glyphH / 2.0F + (float) Math.sin(angle) * radius);
			Theme.frame(ctx, BURST, tipX - centre, tipY - centre, size, BURST_SIZE, burst / BURST_FRAME_MS, BURST_FRAMES);
		}
	}

	private static boolean hasRated(@Nullable IndexScreen screen, SchematicEntry entry)
	{
		return entry.myStars() > 0 || screen != null && screen.detailView.shownStars(entry.id()) > 0;
	}

	private static boolean isOwn(SchematicEntry entry)
	{
		String me = McAuth.verifiedName();

		if (me != null && (me.equalsIgnoreCase(entry.poster().trim()) || me.equalsIgnoreCase(entry.designer().trim())))
		{
			return true;
		}

		String profile = UploaderAccess.unlocked() ? UploaderAccess.profile() : null;
		return profile != null && profile.equalsIgnoreCase(entry.poster().trim());
	}

	// Rating needs a verified account, and the prompt never lands on top of another modal
	private static boolean canPrompt(IndexScreen screen)
	{
		return McAuth.verified() && Backend.configured() && !screen.modalOpen() && !screen.detailView.overlayOpen();
	}

	private static void rate(SchematicEntry entry)
	{
		DUE.add(entry.id());
		Notices.withIndex(screen -> screen.openDetail(entry));
	}

	private static void remember(String postId)
	{
		LinkedHashSet<String> ids = nudged();
		ids.remove(postId);
		ids.add(postId);

		while (ids.size() > REMEMBERED)
		{
			ids.remove(ids.iterator().next());
		}

		Settings.setText(SettingsKeys.RATING_NUDGED, String.join(",", ids));
	}

	private static LinkedHashSet<String> nudged()
	{
		if (nudged != null)
		{
			return nudged;
		}

		nudged = new LinkedHashSet<>();

		for (String id : Settings.text(SettingsKeys.RATING_NUDGED, "").split(","))
		{
			if (!id.isBlank())
			{
				nudged.add(id.trim());
			}
		}

		return nudged;
	}
}

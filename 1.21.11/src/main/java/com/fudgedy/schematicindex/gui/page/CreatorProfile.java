package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Follows;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class CreatorProfile
{
	private static final float NAME_SCALE = 1.15F;

	private final BrowsePage browse;
	private final IndexScreen screen;
	@Nullable String poster;
	private @Nullable String ign;
	private int[] stops = SchematicEntry.NO_STOPS;
	// The header name in the creator's own gradient, rebuilt only when the creator fetch lands or the width changes
	private Component name = Component.empty();
	private int nameWidth = -1;
	private int followers = -1;
	private int posts = -1;
	private int downloads = -1;
	private final Rect back = new Rect();
	private final Rect follow = new Rect();

	CreatorProfile(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.poster != null;
	}

	public @Nullable String poster()
	{
		return this.poster;
	}

	public void open(String poster)
	{
		IndexScreen screen = this.screen;
		this.browse.nav.push();
		this.poster = poster;
		this.ign = null;
		this.stops = SchematicEntry.NO_STOPS;
		this.nameWidth = -1;
		this.followers = -1;
		this.posts = -1;
		this.downloads = -1;
		screen.detailView.dismiss();
		screen.page = IndexScreen.Page.BROWSE;
		this.browse.activeTags.clear();
		this.browse.setSearch("");
		this.fetch(poster);
		this.browse.layoutChips();
		screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + screen.chipRowHeight;
		screen.scroll = 0.0F;
		this.browse.refilter();
	}

	public void close()
	{
		this.poster = null;
		this.browse.layoutChips();
		this.screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight;
		this.screen.scroll = 0.0F;
		this.browse.refilter();
	}

	public void fetch(String poster)
	{
		Thread worker = new Thread(() -> {
			JsonObject body = Backend.getJson("/creator/" + Backend.encode(poster));

			if (body == null)
			{
				return;
			}

			String ign = Json.stringOf(body, "ign", "");
			int followers = Json.intOf(body, "followers", 0);
			int posts = Json.intOf(body, "posts", 0);
			int downloads = Json.intOf(body, "downloads", 0);
			int[] stops = Backend.parseStops(body, "posterStops");

			Minecraft.getInstance().execute(() -> {
				if (poster.equals(this.poster))
				{
					this.ign = ign;
					this.stops = stops;
					this.nameWidth = -1;
					this.followers = followers;
					this.posts = posts;
					this.downloads = downloads;
				}
			});
		}, "schematicindex-creator");
		worker.setDaemon(true);
		worker.start();
	}

	void renderHeader(GuiGraphics ctx, int mouseX, int mouseY)
	{
		IndexScreen screen = this.screen;
		Font font = screen.font();
		int pad = 8;
		int y = IndexScreen.TOP_BAR_HEIGHT + pad;

		int backWidth = font.width(Theme.bold("< Back")) + 12;
		this.back.set(screen.contentX, y, backWidth, 13);
		Buttons.pill(ctx, font, this.back, "< Back", mouseX, mouseY, false);

		int avatarSize = 54;
		int avatarX = screen.contentX;
		int avatarY = y + 18;
		// Only a real IGN resolves to a head, so falling back to the display name yields a Steve
		Identifier avatar = this.ign != null && !this.ign.isBlank()
				? ImageStore.avatar("https://mc-heads.net/avatar/" + Backend.encode(this.ign) + "/64.png")
				: null;

		if (avatar != null)
		{
			Theme.image(ctx, avatar, avatarX, avatarY, avatarSize, avatarSize, 64, 64);
		}
		else
		{
			Theme.roundedRect(ctx, avatarX, avatarY, avatarSize, avatarSize, 6, Theme.SURFACE_ELEVATED);
		}

		int statsX = avatarX + avatarSize + 18;
		int statsWidth = screen.contentX + screen.contentWidth - statsX;
		int column = statsWidth / 3;
		int statTop = avatarY + avatarSize / 2 - font.lineHeight;
		this.stat(ctx, statsX + column / 2, statTop,
				this.posts < 0 ? "..." : SchematicEntry.compact(this.posts), "Posts");
		this.stat(ctx, statsX + column + column / 2, statTop,
				this.followers < 0 ? "..." : SchematicEntry.compact(this.followers), "Followers");
		this.stat(ctx, statsX + column * 2 + column / 2, statTop,
				this.downloads < 0 ? "..." : SchematicEntry.compact(this.downloads), "Downloads");

		int nameY = avatarY + avatarSize + 6;
		Theme.textScaled(ctx, font, this.name(), screen.contentX, nameY, NAME_SCALE, Theme.TEXT);

		boolean following = Follows.isFollowing(this.poster);
		String followLabel = following ? "Following" : "Follow";
		int followWidth = Math.min(160, screen.contentWidth);
		int followY = nameY + Math.round(font.lineHeight * 1.15F) + 6;
		this.follow.set(screen.contentX, followY, followWidth, 16);
		Buttons.pill(ctx, font, this.follow, followLabel, mouseX, mouseY, !following);
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.poster != null)
		{
			if (this.back.contains(mouseX, mouseY))
			{
				this.screen.navigateBack();
				return true;
			}

			if (this.follow.contains(mouseX, mouseY))
			{
				String poster = this.poster;
				this.screen.requireVerified(() -> this.toggleFollow(poster));
				return true;
			}
		}

		return false;
	}

	private void stat(GuiGraphics ctx, int centerX, int topY, String value, String label)
	{
		Font font = this.screen.font();
		int valueWidth = font.width(Theme.bold(value));
		Theme.text(ctx, font, Theme.bold(value), centerX - valueWidth / 2, topY, Theme.TEXT);
		int labelWidth = font.width(label);
		Theme.text(ctx, font, label, centerX - labelWidth / 2, topY + font.lineHeight + 2, Theme.TEXT_MUTE);
	}

	private Component name()
	{
		int contentWidth = this.screen.contentWidth;

		if (this.nameWidth == contentWidth)
		{
			return this.name;
		}

		int room = Math.round(contentWidth / NAME_SCALE);
		String name = Theme.clipBold(this.screen.font(), this.poster, room);
		this.name = ModTags.creator(name, this.stops, Style.EMPTY.withBold(true));
		this.nameWidth = contentWidth;
		return this.name;
	}

	private void toggleFollow(String poster)
	{
		boolean wasFollowing = Follows.isFollowing(poster);
		Follows.toggle(poster);

		if (wasFollowing)
		{
			if (this.followers > 0)
			{
				this.followers--;
			}

			Theme.click(0.8F);
			this.screen.verifyUnfollow(poster);
			return;
		}

		if (this.followers >= 0)
		{
			this.followers++;
		}

		Theme.follow();
		this.screen.verifyFollow(null, poster);
	}
}

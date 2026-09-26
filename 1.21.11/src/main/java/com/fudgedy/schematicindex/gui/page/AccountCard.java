package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.DiscordLink;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Privacy;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.page.SettingsRows.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// The Settings Profile tab: the linked Discord and who can see the account on the web and the leaderboards
class AccountCard
{
	// Unlinking drops the Discord perks, so it takes a second click inside this window
	private static final long CONFIRM_MS = 4000L;
	private static final String UNLINK = "Unlink";
	private static final String CONFIRM_UNLINK = "Confirm unlink";

	private final IndexScreen screen;
	private long unlinkArmedAt;

	AccountCard(IndexScreen screen)
	{
		this.screen = screen;
	}

	int render(GuiGraphics ctx, SettingsRows rows, int y)
	{
		y = rows.header(ctx, "Account", y);

		if (!McAuth.verified())
		{
			return rows.buttons(ctx, "Minecraft account", "Verify to link Discord and choose who sees your profile.", y,
					Action.primary("Verify", () -> this.screen.startVerify(null)));
		}

		DiscordLink.refreshOnOpen();
		Privacy.refreshIfStale();
		y = this.discordRow(ctx, rows, y);

		y = rows.header(ctx, "Privacy", y);
		boolean locked = !Privacy.loaded();
		y = rows.toggle(ctx, "Public profile", "Anyone can open your profile on the website.", Privacy.publicProfile(), locked,
				() -> this.savePrivacy(!Privacy.publicProfile(), Privacy.showOnLeaderboards()), y);
		y = rows.toggle(ctx, "Leaderboards", "Show your name on the Shards leaderboards.", Privacy.showOnLeaderboards(), locked,
				() -> this.savePrivacy(Privacy.publicProfile(), !Privacy.showOnLeaderboards()), y);

		if (!RemoteContent.feature("webProfiles"))
		{
			return y;
		}

		y = rows.header(ctx, "Profile page", y);
		return rows.buttons(ctx, "Your profile", profileUrl(), y,
				Action.quietSecondary("View", () -> this.screen.openLink(profileUrl())),
				Action.secondary("Copy link", this::copyProfile));
	}

	private int discordRow(GuiGraphics ctx, SettingsRows rows, int y)
	{
		DiscordLink.State state = DiscordLink.state();
		String status = state.linked() && state.discord() != null
				? "Linked as @" + state.discord().username()
				: DiscordLink.loaded() ? "Not linked" : "Checking...";
		String label = this.discordLabel(state);

		if (label == null)
		{
			return rows.note(ctx, "Discord", status, y);
		}

		Action action = state.linked()
				? Action.danger(label, CONFIRM_UNLINK, label.equals(CONFIRM_UNLINK), this::clickDiscord)
				: Action.primary(label, this::clickDiscord);
		return rows.buttons(ctx, "Discord", status, y, action);
	}

	// Unlink always works; linking and reviewing requests follow the discordLink switch
	private String discordLabel(DiscordLink.State state)
	{
		if (!DiscordLink.loaded())
		{
			return null;
		}

		if (state.linked())
		{
			return System.currentTimeMillis() - this.unlinkArmedAt < CONFIRM_MS ? CONFIRM_UNLINK : UNLINK;
		}

		if (!DiscordLink.enabled())
		{
			return null;
		}

		return state.pending().isEmpty() ? "Get link code" : "Review request";
	}

	private void clickDiscord()
	{
		DiscordLink.State state = DiscordLink.state();

		if (!state.linked())
		{
			if (state.pending().isEmpty())
			{
				this.screen.linkModal.open();
			}
			else
			{
				this.screen.linkRequestModal.open(state.pending().get(0).id());
			}

			return;
		}

		if (System.currentTimeMillis() - this.unlinkArmedAt >= CONFIRM_MS)
		{
			this.unlinkArmedAt = System.currentTimeMillis();
			return;
		}

		this.unlinkArmedAt = 0L;
		DiscordLink.unlink(() -> Toasts.push("Discord unlinked", "Your Discord account is no longer linked.",
				new ItemStack(Items.BARRIER)));
	}

	// A click mid-save is dropped, since the server answer would overwrite whichever change landed second
	private void savePrivacy(boolean publicProfile, boolean showOnLeaderboards)
	{
		if (!Privacy.saving())
		{
			Privacy.save(publicProfile, showOnLeaderboards);
		}
	}

	private void copyProfile()
	{
		if (this.screen.copyToClipboard(profileUrl()))
		{
			Toasts.push("Profile link copied", profileUrl(), new ItemStack(Items.NAME_TAG));
		}
	}

	private static String profileUrl()
	{
		String ign = McAuth.verifiedName();
		return Backend.siteBase() + "/u/" + Backend.encode(ign == null ? "" : ign);
	}
}

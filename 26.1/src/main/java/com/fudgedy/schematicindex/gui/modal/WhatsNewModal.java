package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.AccountFlags;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.NewsFeed;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.update.ModUpdater;
import com.fudgedy.schematicindex.update.UpdateGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The release highlights, shown once to a returning player after an update and re-openable from Help
public class WhatsNewModal implements Overlay
{
	private static final int PAD = 16;
	private static final int WIDTH = 320;
	private static final int MAX_ROWS = 5;
	private static final String SHARE_TEXT = "🎮 I browse Litematica builds in game with The Schematic Index!";

	private final IndexScreen screen;
	private @Nullable NewsFeed.Entry entry;
	private final List<ItemStack> icons = new ArrayList<>();
	// Set once this screen has settled whether the automatic showing applies, so it is not re-weighed per frame
	private boolean decided;
	private final Rect discord = new Rect();
	private final Rect share = new Rect();
	private final Rect gotIt = new Rect();

	public WhatsNewModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	@Override
	public boolean isOpen()
	{
		return this.entry != null;
	}

	// Waits for news and for the terms, tutorial and primer to clear; a missing entry leaves the marker as it was
	public void maybeShow()
	{
		if (this.decided || this.entry != null || !Settings.termsAccepted() || !Settings.tutorialSeen()
				|| this.screen.tutorialModal.isOpen() || this.screen.termsModal.isOpen()
				|| this.screen.shardWelcomeModal.isOpen() || !NewsFeed.loaded())
		{
			return;
		}

		this.decided = true;
		String current = UpdateGate.currentVersion();
		String seen = Settings.text(SettingsKeys.LAST_SEEN_VERSION, "");

		if (!RemoteContent.feature("whatsNew") || (!seen.isBlank() && !ModUpdater.isNewer(current, seen)))
		{
			return;
		}

		NewsFeed.Entry match = NewsFeed.forVersion(current);

		if (match != null)
		{
			this.show(match);
		}
	}

	// From Help or Settings: this version's notes if there are any, else the newest versioned entry
	public void openLatest()
	{
		if (NewsFeed.loaded())
		{
			this.showLatest();
			return;
		}

		Net.submit(() -> {
			NewsFeed.refresh();
			Minecraft.getInstance().execute(this::showLatest);
		});
	}

	private void showLatest()
	{
		NewsFeed.Entry match = NewsFeed.forVersion(UpdateGate.currentVersion());

		for (NewsFeed.Entry candidate : NewsFeed.entries())
		{
			if (match == null && candidate.version() != null)
			{
				match = candidate;
			}
		}

		if (match == null)
		{
			Toasts.push("What's new", "There are no release notes to show yet.", new ItemStack(Items.WRITABLE_BOOK));
			return;
		}

		this.show(match);
	}

	private void show(NewsFeed.Entry shown)
	{
		this.entry = shown;
		this.icons.clear();

		for (NewsFeed.Item item : shown.items())
		{
			this.icons.add(iconOf(item.icon()));
		}

		// The share line carries the referral code, which may not be loaded yet this session
		if (McAuth.verified() && Shards.referral() == null)
		{
			Shards.refreshReferral();
		}

		Usage.once("whats_new_shown");
		SchematicIndexMod.LOGGER.debug("Showing What's New for {}", shown.version());
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		NewsFeed.Entry shown = this.entry;

		if (shown == null)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int inner = WIDTH - PAD * 2;
		int line = font.lineHeight;
		int rows = Math.min(MAX_ROWS, shown.items().size());
		List<List<String>> wrapped = new ArrayList<>();
		int rowsHeight = 0;

		for (int i = 0; i < rows; i++)
		{
			List<String> text = this.screen.wrap(shown.items().get(i).text(), inner - 24, 2);
			wrapped.add(text);
			rowsHeight += Math.max(18, text.size() * (line + 1) + 6);
		}

		int height = PAD + line + 14 + rowsHeight + 14 + 18 + PAD;
		int x = (this.screen.width - WIDTH) / 2;
		int y = (this.screen.height - height) / 2;
		ModalChrome.frame(ctx, x, y, WIDTH, height, false);

		int ty = y + PAD;
		Theme.itemScaled(ctx, new ItemStack(Items.NETHER_STAR), x + PAD, ty - 1, 0.65F);
		String version = shown.version() == null ? UpdateGate.currentVersion() : shown.version();
		Theme.text(ctx, font, Theme.bold("What's new in " + version), x + PAD + 16, ty, Theme.TEXT);
		ty += line + 14;

		for (int i = 0; i < rows; i++)
		{
			List<String> text = wrapped.get(i);
			int rowHeight = Math.max(18, text.size() * (line + 1) + 6);
			Theme.itemScaled(ctx, this.icons.get(i), x + PAD, ty + (rowHeight - 16) / 2, 1.0F);
			int textY = ty + (rowHeight - text.size() * (line + 1)) / 2 + 1;

			for (String row : text)
			{
				Theme.text(ctx, font, row, x + PAD + 24, textY, Theme.TEXT_MUTE);
				textY += line + 1;
			}

			ty += rowHeight;
		}

		ty += 14;
		int gotWidth = font.width(Theme.bold("Got it")) + 20;
		int shareWidth = font.width(Theme.bold("Tell a friend")) + 16;
		this.gotIt.set(x + WIDTH - PAD - gotWidth, ty, gotWidth, 18);
		this.share.set(this.gotIt.x - 6 - shareWidth, ty, shareWidth, 18);
		Buttons.pill(ctx, font, this.gotIt, "Got it", mouseX, mouseY, true);
		Buttons.pill(ctx, font, this.share, "Tell a friend", mouseX, mouseY, false);

		if (AccountFlags.discordLinked)
		{
			this.discord.set(0, 0, 0, 0);
			return;
		}

		int discordWidth = font.width(Theme.bold("Join the Discord")) + 16;
		this.discord.set(x + PAD, ty, discordWidth, 18);
		Buttons.pill(ctx, font, this.discord, "Join the Discord", mouseX, mouseY, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.gotIt.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.close();
		}
		else if (this.share.contains(mouseX, mouseY))
		{
			Usage.once("whats_new_share");

			if (this.screen.copyToClipboard(shareText()))
			{
				Theme.click(1.1F);
				Toasts.push("Copied", "Copied. Paste it to a friend.", new ItemStack(Items.PAPER));
			}
		}
		else if (this.discord.contains(mouseX, mouseY))
		{
			Usage.once("whats_new_discord");
			this.close();
			this.screen.openLink(RemoteContent.discord());
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == 256 || event.key() == 257 || event.key() == 335)
		{
			this.close();
		}

		return true;
	}

	// Any way out counts as seen, so the notes never return for this version
	private void close()
	{
		this.entry = null;
		Settings.setText(SettingsKeys.LAST_SEEN_VERSION, UpdateGate.currentVersion());
	}

	private static String shareText()
	{
		Shards.Referral referral = Shards.referral();

		if (referral == null || referral.code() == null || referral.code().isBlank())
		{
			return SHARE_TEXT + " " + UpdateGate.PROJECT_URL;
		}

		return SHARE_TEXT + " 🎁 Use my code " + referral.code() + " for " + Theme.count(Shards.rewardOrDefault())
				+ " bonus Shards: " + UpdateGate.PROJECT_URL;
	}

	private static ItemStack iconOf(String id)
	{
		Identifier key = id == null || id.isBlank() ? null : Identifier.tryParse(id);
		Item item = key == null ? Items.PAPER : BuiltInRegistries.ITEM.getValue(key);
		return new ItemStack(item == Items.AIR ? Items.PAPER : item);
	}
}

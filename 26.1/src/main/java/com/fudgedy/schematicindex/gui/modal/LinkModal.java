package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.DiscordLink;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Shows a one-time link code for the Discord panel and watches /me/link until the bot redeems it
public class LinkModal implements Overlay
{
	private enum Phase
	{
		LOADING,
		CODE,
		LINKED,
		FAILED
	}

	private static final int WIDTH = 290;
	private static final int HEIGHT = 208;
	private static final int PAD = 16;
	private static final long POLL_MS = 3000L;
	private static final int FAILED_POLLS_SHOWN = 3;
	private static final int ERROR_TEXT = 0xFFE05555;
	private static final String[] STEPS = {"Step 1) Go to #free-cosmetics in our Discord", "Step 2) Press Claim",
			"Step 3) Paste this code"};

	private final IndexScreen screen;
	private final Rect copyButton = new Rect();
	private final Rect discordButton = new Rect();
	private final Rect retryButton = new Rect();
	private final Rect questsButton = new Rect();
	private final Rect closeButton = new Rect();
	private boolean open;
	private Phase phase = Phase.LOADING;
	private @Nullable DiscordLink.Code code;
	private long lastPoll;
	private boolean polling;
	private int failedPolls;

	public LinkModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	@Override
	public boolean isOpen()
	{
		return this.open;
	}

	public void open()
	{
		if (!DiscordLink.enabled())
		{
			return;
		}

		this.open = true;
		this.failedPolls = 0;
		Usage.once("link_code_open");
		Theme.click(1.1F);

		if (DiscordLink.state().linked())
		{
			this.phase = Phase.LINKED;
			return;
		}

		this.requestCode();
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		long now = System.currentTimeMillis();
		this.pollIfDue(now);

		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);
		int x = (this.screen.width - WIDTH) / 2;
		int y = (this.screen.height - HEIGHT) / 2;
		ModalChrome.frame(ctx, x, y, WIDTH, HEIGHT, false);
		Theme.itemScaled(ctx, new ItemStack(Items.NAME_TAG), x + PAD, y + PAD - 1, 0.65F);
		Theme.text(ctx, font, Theme.bold("Link your Discord"), x + PAD + 16, y + PAD, Theme.TEXT);
		int bodyY = y + PAD + font.lineHeight + 14;
		this.clearButtons();

		switch (this.phase)
		{
			case LOADING -> this.centred(ctx, "Getting a code...", x, bodyY + 30, Theme.TEXT_MUTE);
			case FAILED -> this.renderFailed(ctx, x, bodyY, mouseX, mouseY);
			case LINKED -> this.renderLinked(ctx, x, bodyY, mouseX, mouseY);
			case CODE -> this.renderCode(ctx, x, bodyY, now, mouseX, mouseY);
		}

		String close = this.phase == Phase.LINKED ? "Done" : "Close";
		int closeWidth = font.width(Theme.bold(close)) + 24;
		this.closeButton.set(x + WIDTH - PAD - closeWidth, y + HEIGHT - PAD - 18, closeWidth, 18);
		Buttons.pill(ctx, font, this.closeButton, close, mouseX, mouseY, this.phase == Phase.LINKED);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.closeButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.open = false;
		}
		else if (this.copyButton.contains(mouseX, mouseY) && this.code != null
				&& this.screen.copyToClipboard(this.code.code()))
		{
			Theme.click();
			Toasts.push("Code copied", "Paste it into the Claim popup in Discord.", new ItemStack(Items.NAME_TAG));
		}
		else if (this.discordButton.contains(mouseX, mouseY))
		{
			this.screen.openLink(RemoteContent.discord());
		}
		else if (this.retryButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.requestCode();
		}
		else if (this.questsButton.contains(mouseX, mouseY))
		{
			this.open = false;
			this.screen.openShardPanel();
			this.screen.shardPanel.showQuests();
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == 256)
		{
			this.open = false;
		}

		return true;
	}

	private void requestCode()
	{
		this.phase = Phase.LOADING;
		this.code = null;
		DiscordLink.startCode(code -> {
			this.code = code;
			this.phase = Phase.CODE;
			this.lastPoll = System.currentTimeMillis();
		}, () -> this.phase = DiscordLink.state().linked() ? Phase.LINKED : Phase.FAILED,
				() -> this.phase = Phase.FAILED);
	}

	private void pollIfDue(long now)
	{
		if (this.phase != Phase.CODE || this.polling || now - this.lastPoll < POLL_MS)
		{
			return;
		}

		this.polling = true;
		this.lastPoll = now;
		DiscordLink.status(ok -> {
			this.polling = false;
			this.failedPolls = ok ? 0 : this.failedPolls + 1;

			if (!ok)
			{
				SchematicIndexMod.LOGGER.debug("Link status poll failed {} in a row ({})", this.failedPolls,
						Errors.DISCORD_STATUS);
			}

			if (this.open && this.phase == Phase.CODE && DiscordLink.state().linked())
			{
				this.phase = Phase.LINKED;
				Theme.success();
			}
		});
	}

	private void renderCode(GuiGraphicsExtractor ctx, int x, int y, long now, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		DiscordLink.Code shown = this.code;

		if (shown == null)
		{
			return;
		}

		long left = shown.expiresAt() - now;

		if (left <= 0L)
		{
			this.centred(ctx, Theme.bold("Code expired"), x, y + 14, Theme.TEXT);
			this.retryButton.set(x + (WIDTH - 110) / 2, y + 36, 110, 18);
			Buttons.pill(ctx, font, this.retryButton, "Get a new code", mouseX, mouseY, true);
			return;
		}

		String text = Theme.bold(shown.code());
		float scale = 2.5F;
		int codeWidth = Math.round(font.width(text) * scale);
		Theme.textScaled(ctx, font, text, x + (WIDTH - codeWidth) / 2, y + 6, scale, Theme.TEXT);
		long seconds = left / 1000L;
		this.centred(ctx, "Expires in " + String.format("%d:%02d", seconds / 60L, seconds % 60L), x, y + 34,
				Theme.TEXT_ASH);
		int buttonsY = this.renderSteps(ctx, font, x, y + 50) + Theme.SPACE_S;

		int half = (WIDTH - PAD * 2 - 6) / 2;
		this.copyButton.set(x + PAD, buttonsY, half, 18);
		this.discordButton.set(x + WIDTH - PAD - half, buttonsY, half, 18);
		Buttons.pill(ctx, font, this.copyButton, "Copy", mouseX, mouseY, true);
		Buttons.pill(ctx, font, this.discordButton, "Open Discord", mouseX, mouseY, false);

		if (this.failedPolls >= FAILED_POLLS_SHOWN)
		{
			this.centred(ctx, "Can't reach the server right now. (" + Errors.DISCORD_STATUS + ")", x, buttonsY + 26, ERROR_TEXT);
		}
	}

	private void renderLinked(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY)
	{
		DiscordLink.State state = DiscordLink.state();
		String name = state.discord() == null ? "" : "@" + state.discord().username();
		this.centred(ctx, Theme.bold("Linked to " + name), x, y + 8, Theme.ACCENT_BRIGHT);
		this.centred(ctx, "{Discord} unlocked", x, y + 24, Theme.TEXT);

		if (!state.bonusAvailable() || state.bonusClaimed())
		{
			return;
		}

		this.centred(ctx, "Claim your 100 Shards in quests", x, y + 44, Theme.SHARD);
		this.questsButton.set(x + (WIDTH - 100) / 2, y + 60, 100, 18);
		Buttons.pill(ctx, this.screen.font(), this.questsButton, "Open quests", mouseX, mouseY, false);
	}

	private void renderFailed(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY)
	{
		this.centred(ctx, "Couldn't get a link code.", x, y + 8, ERROR_TEXT);
		this.centred(ctx, "Try again in a moment. (" + Errors.DISCORD_CODE + ")", x, y + 20, Theme.TEXT_MUTE);
		this.retryButton.set(x + (WIDTH - 90) / 2, y + 40, 90, 18);
		Buttons.pill(ctx, this.screen.font(), this.retryButton, "Try again", mouseX, mouseY, true);
	}

	// Left-aligned as one block centred in the modal, so the step numbers line up
	private int renderSteps(GuiGraphicsExtractor ctx, Font font, int x, int y)
	{
		int room = WIDTH - PAD * 2;
		List<String> lines = new ArrayList<>();

		for (String step : STEPS)
		{
			lines.addAll(this.screen.wrap(step, room, 2));
		}

		int blockWidth = 0;

		for (String line : lines)
		{
			blockWidth = Math.max(blockWidth, font.width(line));
		}

		int left = x + (WIDTH - blockWidth) / 2;

		for (String line : lines)
		{
			Theme.text(ctx, font, line, left, y, Theme.TEXT_MUTE);
			y += font.lineHeight + Theme.SPACE_2XS;
		}

		return y;
	}

	private void centred(GuiGraphicsExtractor ctx, String text, int x, int y, int color)
	{
		Font font = this.screen.font();
		String clipped = Theme.clip(font, text, WIDTH - PAD * 2);
		Theme.text(ctx, font, clipped, x + (WIDTH - font.width(clipped)) / 2, y, color);
	}

	private void clearButtons()
	{
		this.copyButton.set(0, 0, 0, 0);
		this.discordButton.set(0, 0, 0, 0);
		this.retryButton.set(0, 0, 0, 0);
		this.questsButton.set(0, 0, 0, 0);
	}
}

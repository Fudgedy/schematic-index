package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.DiscordLink;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

// A Discord account asked to link from the Discord side; the player confirms it is theirs here
public class LinkRequestModal implements Overlay
{
	private static final int WIDTH = 310;
	private static final int HEIGHT = 170;
	private static final int PAD = 16;
	private static final int AVATAR = 32;

	private final IndexScreen screen;
	private final Rect acceptButton = new Rect();
	private final Rect denyButton = new Rect();
	private final Rect blockButton = new Rect();
	private final Rect closeButton = new Rect();
	private boolean open;
	private boolean busy;
	private int requestId = -1;
	private @Nullable DiscordLink.Request request;

	public LinkRequestModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	@Override
	public boolean isOpen()
	{
		return this.open;
	}

	// Re-reads /me/link first, so a request answered or expired elsewhere never shows stale
	public void open(int requestId)
	{
		this.open = true;
		this.busy = true;
		this.requestId = requestId;
		this.request = null;
		Theme.click(1.1F);
		DiscordLink.status(ok -> {
			if (!this.open || this.requestId != requestId)
			{
				return;
			}

			this.busy = false;
			this.request = DiscordLink.request(requestId);

			if (this.request != null)
			{
				return;
			}

			this.open = false;
			String text = ok ? "That request expired." : "Can't reach the server right now. (" + Errors.DISCORD_STATUS + ")";
			Toasts.push("Discord link request", text, new ItemStack(Items.NAME_TAG));
		});
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);
		int x = (this.screen.width - WIDTH) / 2;
		int y = (this.screen.height - HEIGHT) / 2;
		ModalChrome.frame(ctx, x, y, WIDTH, HEIGHT, false);
		Theme.text(ctx, font, Theme.bold("Discord link request"), x + PAD, y + PAD, Theme.TEXT);
		this.acceptButton.set(0, 0, 0, 0);
		this.denyButton.set(0, 0, 0, 0);
		this.blockButton.set(0, 0, 0, 0);

		int closeWidth = font.width(Theme.bold("Close")) + 24;
		this.closeButton.set(x + WIDTH - PAD - closeWidth, y + PAD - 5, closeWidth, 18);
		Buttons.pill(ctx, font, this.closeButton, "Close", mouseX, mouseY, false);

		DiscordLink.Request shown = this.request;

		if (shown == null)
		{
			Theme.text(ctx, font, "Checking the request...", x + PAD, y + PAD + 30, Theme.TEXT_MUTE);
			return;
		}

		int top = y + PAD + font.lineHeight + 12;
		this.renderAvatar(ctx, shown, x + PAD, top);
		int textX = x + PAD + AVATAR + 10;
		int room = WIDTH - PAD * 2 - AVATAR - 10;
		Theme.text(ctx, font, Theme.bold(Theme.clipBold(font, shown.displayName(), room)), textX, top + 2, Theme.TEXT);
		Theme.text(ctx, font, Theme.clip(font, "@" + shown.username(), room), textX, top + 13, Theme.TEXT_MUTE);

		if (shown.discordCreatedAt() > 0L)
		{
			Theme.text(ctx, font, "Discord account since " + DiscordLink.monthYear(shown.discordCreatedAt()), textX,
					top + 24, Theme.TEXT_ASH);
		}

		int warnY = top + AVATAR + 14;
		Theme.text(ctx, font, "Only accept if this is your Discord account.", x + PAD, warnY, Theme.GOLD_BRIGHT);
		boolean expired = shown.expiresAt() <= System.currentTimeMillis();

		if (expired)
		{
			Theme.text(ctx, font, "This request expired. Ask for a new one in Discord.", x + PAD, warnY + 14,
					Theme.TEXT_ASH);
			return;
		}

		int buttonY = y + HEIGHT - PAD - 18;
		// Each button fits its own label, so the long danger label never spills past its fill
		int acceptWidth = font.width(Theme.bold("Accept")) + 24;
		int denyWidth = font.width(Theme.bold("Deny")) + 24;
		int blockWidth = font.width(Theme.bold("Deny and block")) + 24;
		int spare = Math.max(0, (WIDTH - PAD * 2 - 12 - acceptWidth - denyWidth - blockWidth) / 3);
		acceptWidth += spare;
		denyWidth += spare;
		blockWidth += spare;
		this.acceptButton.set(x + PAD, buttonY, acceptWidth, 18);
		this.denyButton.set(x + PAD + acceptWidth + 6, buttonY, denyWidth, 18);
		this.blockButton.set(x + PAD + acceptWidth + denyWidth + 12, buttonY, blockWidth, 18);

		if (this.busy)
		{
			Buttons.disabled(ctx, font, this.acceptButton, "Accept");
			Buttons.disabled(ctx, font, this.denyButton, "Deny");
			Buttons.disabled(ctx, font, this.blockButton, "Deny and block");
			return;
		}

		Buttons.pill(ctx, font, this.acceptButton, "Accept", mouseX, mouseY, true);
		Buttons.pill(ctx, font, this.denyButton, "Deny", mouseX, mouseY, false);
		Buttons.danger(ctx, font, this.blockButton, "Deny and block", mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.closeButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.open = false;
			return true;
		}

		if (this.busy || this.request == null)
		{
			return true;
		}

		int id = this.request.id();

		if (this.acceptButton.contains(mouseX, mouseY))
		{
			this.busy = true;
			Theme.click();
			Usage.once("link_accept");
			DiscordLink.accept(id, this::finish);
		}
		else if (this.denyButton.contains(mouseX, mouseY) || this.blockButton.contains(mouseX, mouseY))
		{
			this.busy = true;
			Theme.click();
			DiscordLink.deny(id, this.blockButton.contains(mouseX, mouseY), this::finish);
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

	private void finish()
	{
		this.busy = false;
		this.open = false;
	}

	private void renderAvatar(GuiGraphics ctx, DiscordLink.Request shown, int x, int y)
	{
		Identifier avatar = shown.avatarUrl() == null ? null : ImageStore.avatar(shown.avatarUrl());

		if (avatar == null)
		{
			Theme.roundedRect(ctx, x, y, AVATAR, AVATAR, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
			Theme.itemScaled(ctx, new ItemStack(Items.NAME_TAG), x + 8, y + 8, 1.0F);
			return;
		}

		Theme.image(ctx, avatar, x, y, AVATAR, AVATAR, 64, 64);
	}
}

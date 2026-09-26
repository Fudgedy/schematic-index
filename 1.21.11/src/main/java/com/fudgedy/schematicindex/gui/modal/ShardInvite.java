package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Glyphs;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Rows;
import com.fudgedy.schematicindex.gui.widget.States;
import com.fudgedy.schematicindex.gui.widget.TextInput;
import com.fudgedy.schematicindex.gui.widget.Tiles;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;

// The Referral tab: the player's own code to share, how it has paid out, and a field for a friend's code
final class ShardInvite
{
	private static final int KEY_ENTER = 257;
	private static final int KEY_KEYPAD_ENTER = 335;
	private static final long COPIED_FLASH_MS = 1400L;
	private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-";
	private static final int CODE_CARD_HEIGHT = 48;
	private static final int REDEEM_MIN_WIDTH = 72;
	private static final int STATS = 3;
	private static final float CODE_SCALE = 1.5F;
	private static final String COPIED = "Copied";
	private static final int HEART_SIZE = 9;
	// One full sine of t / 160 spans ~1005 ms; the negative half is clamped away so the heart rests between beats
	private static final long HEARTBEAT_MS = 1005L;
	private static final float HEARTBEAT_SCALE = 0.08F;

	private final ShardPanel panel;
	private final Rect copyCode = new Rect();
	private final Rect redeemButton = new Rect();
	private final Rect codeField = new Rect();
	private final Rect retry = new Rect();
	private final TextInput codeInput = new TextInput(12, CODE_CHARS);
	private long codeCopiedAt;

	ShardInvite(ShardPanel panel)
	{
		this.panel = panel;
	}

	void onOpen()
	{
		this.codeInput.set("");
		this.codeCopiedAt = 0L;
	}

	void clearRects()
	{
		this.copyCode.set(0, 0, 0, 0);
		this.redeemButton.set(0, 0, 0, 0);
		this.retry.set(0, 0, 0, 0);
		this.codeInput.bounds.set(0, 0, 0, 0);
	}

	void blur()
	{
		this.codeInput.blur();
	}

	boolean isFocused()
	{
		return this.codeInput.isFocused();
	}

	int render(GuiGraphics ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		Shards.Referral referral = Shards.referral();
		float beat = (float) Math.max(0.0, Math.sin(Util.getMillis() % HEARTBEAT_MS / 160.0));
		Theme.pushScale(ctx, x, y - 1, HEART_SIZE, HEART_SIZE, 1.0F + HEARTBEAT_SCALE * beat);
		Theme.heart(ctx, x, y - 1, true);
		Theme.pop(ctx);
		Theme.text(ctx, font, Theme.bold("Invite a friend"), x + HEART_SIZE + Theme.SPACE_S, y, Theme.TEXT);
		y += font.lineHeight + Theme.SPACE_XS;
		this.renderPitch(ctx, font, x, y, width);
		y += font.lineHeight + Theme.SPACE_M;

		if (referral == null)
		{
			this.clearRects();
			return this.renderUnloaded(ctx, font, x, y, width, mouseX, mouseY);
		}

		this.renderCode(ctx, font, referral.code(), x, y, width, mouseX, mouseY);
		y += CODE_CARD_HEIGHT + Theme.SPACE_M;
		int tileWidth = (width - Theme.SPACE_S * (STATS - 1)) / STATS;
		Tiles.stat(ctx, font, x, y, tileWidth, Theme.count(referral.invited()), "Invited");
		Tiles.stat(ctx, font, x + tileWidth + Theme.SPACE_S, y, tileWidth, Theme.count(referral.rewarded()), "Rewarded");
		Tiles.stat(ctx, font, x + (tileWidth + Theme.SPACE_S) * 2, y, width - (tileWidth + Theme.SPACE_S) * 2,
				Theme.count(referral.pending()), "Pending");
		y += Tiles.STAT_HEIGHT;

		if (referral.redeemed())
		{
			this.redeemButton.set(0, 0, 0, 0);
			this.codeInput.bounds.set(0, 0, 0, 0);
			return y;
		}

		return this.renderRedeem(ctx, font, referral, x, y + Theme.SPACE_L, width, mouseX, mouseY);
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.retry.contains(mouseX, mouseY))
		{
			Theme.click();
			Shards.refreshReferral();
			return true;
		}

		if (this.copyCode.contains(mouseX, mouseY) && Shards.referral() != null)
		{
			Theme.buttonPress(this.copyCode);
			this.panel.screen().copyToClipboard(Shards.referral().code());
			this.codeCopiedAt = System.currentTimeMillis();
			Theme.click(1.2F);
			return true;
		}

		if (this.redeemButton.contains(mouseX, mouseY))
		{
			Theme.buttonPress(this.redeemButton);
			this.redeem();
			return true;
		}

		return this.codeInput.click(mouseX, mouseY);
	}

	boolean keyPressed(KeyEvent event)
	{
		boolean enter = event.key() == KEY_ENTER || event.key() == KEY_KEYPAD_ENTER;
		this.codeInput.keyPressed(event);

		if (enter)
		{
			this.redeem();
		}

		return true;
	}

	boolean charTyped(CharacterEvent event)
	{
		return this.codeInput.charTyped(event);
	}

	private void redeem()
	{
		String code = this.codeInput.value().trim().toUpperCase();

		if (code.isEmpty() || Shards.isRedeeming())
		{
			return;
		}

		Theme.click();
		Shards.redeemReferral(code, () -> this.codeInput.set(""));
	}

	// Text, glyph, amount, text: the reward is the one purple thing on the line
	private void renderPitch(GuiGraphics ctx, Font font, int x, int y, int width)
	{
		String lead = "You both get ";
		String amount = Theme.bold(Theme.count(Shards.rewardOrDefault()));
		String tail = " once you each reach a " + Shards.streakRequiredOrDefault() + " day streak.";
		int right = x + Math.min(width, Theme.TEXT_MAX_WIDTH);
		Theme.text(ctx, font, lead, x, y, Theme.TEXT_MUTE);
		x += font.width(lead);
		Glyphs.shard(ctx, x, y);
		x += Theme.ICON_S + Theme.SPACE_2XS;
		Theme.text(ctx, font, amount, x, y, Theme.SHARD_TEXT);
		x += font.width(amount);
		Theme.text(ctx, font, Theme.clip(font, tail, right - x), x, y, Theme.TEXT_MUTE);
	}

	private int renderUnloaded(GuiGraphics ctx, Font font, int x, int y, int width, int mouseX, int mouseY)
	{
		if (!Shards.isLoadingReferral())
		{
			return States.error(ctx, font, x, y, width, "your invite code", Errors.SHARD_LOAD, this.retry, mouseX, mouseY);
		}

		States.skeleton(ctx, x, y, width, CODE_CARD_HEIGHT);
		y += CODE_CARD_HEIGHT + Theme.SPACE_M;
		int tileWidth = (width - Theme.SPACE_S * (STATS - 1)) / STATS;

		for (int i = 0; i < STATS; i++)
		{
			States.skeleton(ctx, x + i * (tileWidth + Theme.SPACE_S), y, tileWidth, Tiles.STAT_HEIGHT);
		}

		return y + Tiles.STAT_HEIGHT;
	}

	private void renderCode(GuiGraphics ctx, Font font, String code, int x, int y, int width, int mouseX, int mouseY)
	{
		Tiles.card(ctx, x, y, width, CODE_CARD_HEIGHT);
		Theme.text(ctx, font, "Your code", x + Theme.SPACE_M, y + Theme.SPACE_S, Theme.TEXT_ASH);

		int copyWidth = Buttons.width(font, COPIED) + Theme.ICON_S + Theme.SPACE_XS;
		this.copyCode.set(x + width - Theme.SPACE_M - copyWidth, y + CODE_CARD_HEIGHT - Theme.SPACE_M - Theme.H_CONTROL, copyWidth,
				Theme.H_CONTROL);
		boolean flash = System.currentTimeMillis() - this.codeCopiedAt < COPIED_FLASH_MS;
		Buttons.button(ctx, font, this.copyCode, flash ? "" : "Copy", Buttons.Kind.SECONDARY, true, mouseX, mouseY);

		if (flash)
		{
			String label = Theme.bold(COPIED);
			int contentWidth = font.width(label) + Theme.SPACE_XS + Theme.ICON_S;
			int labelX = this.copyCode.x + (this.copyCode.width - contentWidth) / 2;
			int labelY = this.copyCode.y + (Theme.H_CONTROL - font.lineHeight) / 2 + 1;
			Theme.text(ctx, font, label, labelX, labelY, Theme.SUCCESS);
			Glyphs.draw(ctx, Glyphs.CHECK, labelX + font.width(label) + Theme.SPACE_XS, labelY, Theme.SUCCESS);
		}

		int room = Math.round((this.copyCode.x - Theme.SPACE_M - (x + Theme.SPACE_M)) / CODE_SCALE);
		int codeY = y + CODE_CARD_HEIGHT - Theme.SPACE_M - Math.round(font.lineHeight * CODE_SCALE) + Theme.SPACE_2XS;
		Theme.textScaled(ctx, font, Theme.bold(Theme.clipBold(font, code, room)), x + Theme.SPACE_M, codeY, CODE_SCALE, Theme.TEXT);
	}

	private int renderRedeem(GuiGraphics ctx, Font font, Shards.Referral referral, int x, int y, int width, int mouseX,
			int mouseY)
	{
		y = Rows.header(ctx, font, "Have a friend's code?", null, x, y, width);
		boolean redeeming = Shards.isRedeeming();
		String label = redeeming ? "Redeeming…" : "Redeem";
		int buttonWidth = Math.max(REDEEM_MIN_WIDTH, Buttons.width(font, "Redeeming…"));
		this.redeemButton.set(x + width - buttonWidth, y, buttonWidth, Theme.H_CONTROL);
		this.codeField.set(x, y, width - buttonWidth - Theme.SPACE_S, Theme.H_CONTROL);

		if (!referral.canRedeem())
		{
			this.codeInput.renderDisabled(ctx, font, this.codeField, "Friend's code");
			Buttons.button(ctx, font, this.redeemButton, label, Buttons.Kind.SECONDARY, false, mouseX, mouseY);
			this.redeemButton.set(0, 0, 0, 0);
			String hint = "Unlocks at a " + referral.streakRequired() + " day streak (you're on " + referral.streak() + ")";
			int hintY = y + Theme.H_CONTROL + Theme.SPACE_XS;
			Theme.text(ctx, font, Theme.clip(font, hint, width), x, hintY, Theme.TEXT_ASH);
			return hintY + font.lineHeight;
		}

		this.codeInput.render(ctx, font, this.codeField, "Friend's code", mouseX, mouseY);
		boolean typed = !this.codeInput.value().trim().isEmpty();
		Buttons.Kind kind = typed ? Buttons.Kind.PRIMARY : Buttons.Kind.SECONDARY;
		Buttons.button(ctx, font, this.redeemButton, label, kind, !redeeming, mouseX, mouseY);

		if (redeeming)
		{
			this.redeemButton.set(0, 0, 0, 0);
		}

		return y + Theme.H_CONTROL;
	}
}

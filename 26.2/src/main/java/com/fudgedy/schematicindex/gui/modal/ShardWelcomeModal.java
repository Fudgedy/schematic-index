package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// A one-time primer shown the first time an account verifies, explaining shards and the 250 bonus the
// server holds until it is dismissed; the seen flag in Settings keeps it from reappearing once paid
public class ShardWelcomeModal
{
	private static final int PAD = 18;
	private static final int WELCOME_SHARDS = 250;
	private static final String BODY = "Earn Shards from logging in daily or completing quests. "
			+ "Spend them on nametag colours, effects and tags in the Cosmetics tab, or on premium schematics.";

	private final IndexScreen screen;
	private boolean open;
	private final Rect bounds = new Rect();
	private final Rect dismiss = new Rect();
	private final Rect cosmetics = new Rect();

	public ShardWelcomeModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	// Opens once per client; the caller only fires this after a verify actually succeeds
	public void maybeShow()
	{
		if (McAuth.welcomePending() && !Settings.shardWelcomeSeen() && !this.open)
		{
			this.open = true;
			Theme.amethystBreak();
		}
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int width = 300;
		int inner = width - PAD * 2;
		List<String> body = this.screen.wrap(BODY, inner, 5);
		int line = font.lineHeight;
		int height = PAD + line + 12 + body.size() * (line + 2) + 14 + 26 + 14 + 18 + PAD;
		int x = (this.screen.width - width) / 2;
		int y = (this.screen.height - height) / 2;
		this.bounds.set(x, y, width, height);

		ModalChrome.frame(ctx, x, y, width, height, false);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_MODAL, Theme.SHARD);

		int ty = y + PAD;
		Theme.itemScaled(ctx, new ItemStack(Items.AMETHYST_SHARD), x + PAD, ty - 1, 0.65F);
		Theme.text(ctx, font, Theme.bold("How Shards Work"), x + PAD + 16, ty, Theme.TEXT);
		ty += line + 12;

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + PAD, ty, Theme.TEXT_MUTE);
			ty += line + 2;
		}

		ty += 14;
		String bonus = "+" + WELCOME_SHARDS + " to get started";
		Theme.roundedRect(ctx, x + PAD, ty, inner, 26, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x + PAD, ty, inner, 26, Theme.RADIUS_CARD, Theme.HAIRLINE);
		int bonusWidth = 16 + font.width(Theme.bold(bonus));
		int bonusX = x + (width - bonusWidth) / 2;
		Theme.itemScaled(ctx, new ItemStack(Items.AMETHYST_SHARD), bonusX, ty + 8, 0.65F);
		Theme.text(ctx, font, Theme.bold(bonus), bonusX + 16, ty + 9, Theme.SHARD);
		ty += 26 + 14;

		int half = (inner - 6) / 2;
		this.cosmetics.set(x + PAD, ty, half, 18);
		this.dismiss.set(x + width - PAD - half, ty, half, 18);
		Buttons.pill(ctx, font, this.cosmetics, "See cosmetics", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.dismiss, "Let's go", mouseX, mouseY, true);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.dismiss.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.finish();
		}
		else if (this.cosmetics.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.finish();
			this.screen.switchPage(IndexScreen.Page.COSMETICS);
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (event.key() == 256 || event.key() == 257 || event.key() == 335)
		{
			this.finish();
		}

		return true;
	}

	private void finish()
	{
		this.open = false;
		Shards.claimWelcome();
	}
}

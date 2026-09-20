package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.update.UpdateNotice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

public class TermsModal
{
	private static final String TERMS_BODY =
			"By using The Schematic Index, you agree to these terms. If you do not agree, you cannot use "
					+ "the online service.\n\n"
					+ "Data & Privacy: Nothing is sent anywhere until you accept these terms. Once accepted, "
					+ "the Service connects to external servers to sync the online catalog. Browsing and "
					+ "downloading send nothing about you. Liking, rating, following, reporting, uploading and "
					+ "cosmetics first verify your Minecraft account with Mojang; from then on those actions and "
					+ "a periodic presence beat carry that account so the server can keep your likes, shards "
					+ "and cosmetics and count how many players are online. Sign out in Settings forgets the "
					+ "verification on this device. You can review these terms anytime in Settings; declining "
					+ "there returns you to the Litematica menu.\n\n"
					+ "Content Ownership: Do not upload content you do not have the legal rights to "
					+ "distribute. By uploading, you grant us a license to host and share your schematic. We "
					+ "reserve the right to remove infringing content and terminate access for abuse.\n\n"
					+ "Disclaimer: This service is provided \"as-is.\" We are not liable for server downtime "
					+ "or issues caused by third-party files.";
	public static final String USAGE_DATA_LABEL = "Allow us to collect usage data, to help improve our mod";
	// Short terms still get a card this tall; long ones grow to the window and then scroll
	private static final int MIN_CARD_HEIGHT = 232;

	private final IndexScreen screen;
	private boolean open;
	private float scroll;
	private float maxScroll;

	private boolean scrolledBottom;
	// Opened from Settings to review granted consent: Close keeps using the mod, Decline withdraws it
	private boolean reviewMode;
	private final Rect agree = new Rect();
	private final Rect decline = new Rect();
	private final Rect usageData = new Rect();

	public TermsModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open()
	{
		this.reviewMode = false;
		this.open = true;
		this.scroll = 0.0F;
		this.scrolledBottom = false;
	}

	public void openReview()
	{
		this.reviewMode = true;
		this.open = true;
		this.scroll = 0.0F;
		this.scrolledBottom = true;
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, 0xE0000000);

		int pad = 14;
		int line = font.lineHeight;
		int cardWidth = Math.min(this.screen.width - 40, 360);
		int bodyWidth = cardWidth - pad * 2;
		List<String> body = this.screen.wrapParagraphs(this.effectiveBody(), bodyWidth - 6);
		int contentHeight = body.size() * (line + 1);
		// Title above, the button row and the usage toggle below; the body takes what the window leaves
		int header = pad + line + 6;
		int footer = 8 + 16 + 6 + 16 + pad;
		int cardHeight = Math.max(MIN_CARD_HEIGHT,
				Math.min(this.screen.height - 40, header + contentHeight + footer));
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;

		Theme.roundedRect(ctx, x, y, cardWidth, cardHeight, Theme.RADIUS_MODAL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, cardWidth, cardHeight, Theme.RADIUS_MODAL, Theme.ACCENT);
		Theme.text(ctx, font, Theme.bold("Terms of Service"), x + pad, y + pad, Theme.TEXT);

		int bodyTop = y + header;
		int buttonY = y + cardHeight - pad - 16 - 6 - 16;
		int bodyBottom = buttonY - 8;
		int viewport = bodyBottom - bodyTop;
		this.maxScroll = Math.max(0.0F, contentHeight - viewport);
		this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll));

		if (this.scroll >= this.maxScroll - 0.5F)
		{
			this.scrolledBottom = true;
		}

		ctx.enableScissor(x + pad, bodyTop, x + cardWidth - pad, bodyBottom);
		int ty = bodyTop - Math.round(this.scroll);

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + pad, ty, Theme.TEXT_MUTE);
			ty += line + 1;
		}

		ctx.disableScissor();

		if (this.maxScroll > 0.0F)
		{
			int trackX = x + cardWidth - pad + 2;
			Theme.roundedRect(ctx, trackX, bodyTop, 2, viewport, 1, Theme.SURFACE_CARD);
			int thumbHeight = Math.max(12, Math.round((float) viewport * viewport / contentHeight));
			int thumbY = bodyTop + Math.round((viewport - thumbHeight) * (this.scroll / this.maxScroll));
			this.screen.drawScrollThumb(ctx, IndexScreen.SCROLLBAR_TOS, trackX, 2, thumbY, thumbHeight, 1, bodyTop,
					viewport, Theme.ACCENT_BRIGHT);
		}

		this.usageData.set(x + pad, buttonY + 16 + 6, cardWidth - pad * 2, 16);
		Controls.toggle(ctx, font, this.usageData, USAGE_DATA_LABEL, Settings.usageData(), mouseX, mouseY);

		if (this.reviewMode)
		{
			int closeWidth = font.width(Theme.bold("Close")) + 20;
			int declineWidth = font.width(Theme.bold("Decline")) + 20;
			this.decline.set(x + pad, buttonY, declineWidth, 16);
			this.agree.set(x + cardWidth - pad - closeWidth, buttonY, closeWidth, 16);
			Buttons.pill(ctx, font, this.decline, "Decline", mouseX, mouseY, false);
			Buttons.pill(ctx, font, this.agree, "Close", mouseX, mouseY, true);
			return;
		}

		int agreeWidth = font.width(Theme.bold("I Agree")) + 20;
		int declineWidth = font.width(Theme.bold("Decline")) + 20;
		this.decline.set(x + pad, buttonY, declineWidth, 16);
		this.agree.set(x + cardWidth - pad - agreeWidth, buttonY, agreeWidth, 16);

		if (this.scrolledBottom)
		{
			Buttons.pill(ctx, font, this.decline, "Decline", mouseX, mouseY, false);
			Buttons.pill(ctx, font, this.agree, "I Agree", mouseX, mouseY, true);
		}
		else
		{
			Buttons.disabled(ctx, font, this.decline, "Decline");
			Buttons.disabled(ctx, font, this.agree, "I Agree");
			String hint = "Scroll down to continue";
			Theme.text(ctx, font, hint, x + (cardWidth - font.width(hint)) / 2, buttonY + 4, Theme.TEXT_ASH);
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.usageData.contains(mouseX, mouseY))
		{
			Settings.toggleUsageData();
			Theme.click(1.1F);
			return true;
		}

		if (this.reviewMode)
		{
			if (this.agree.contains(mouseX, mouseY))
			{
				this.open = false;
				Theme.click();
			}
			else if (this.decline.contains(mouseX, mouseY))
			{
				Settings.revokeTerms();
				this.screen.onClose();
			}

			return true;
		}

		if (!this.scrolledBottom)
		{
			return true;
		}

		if (this.agree.contains(mouseX, mouseY))
		{
			Settings.acceptTerms();
			// Nothing contacts the server before this point; verify now so the shard pill appears at once
			Catalogue.refresh();
			McAuth.ensureVerified(this.screen::maybeShowShardWelcome);
			UpdateNotice.start();
			this.open = false;
			Theme.click(1.2F);
			this.screen.tutorialModal.maybeStart();
		}
		else if (this.decline.contains(mouseX, mouseY))
		{
			Settings.revokeTerms();
			this.screen.onClose();
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (event.key() == 256)
		{
			if (this.reviewMode)
			{
				// In review mode Escape closes without withdrawing consent
				this.open = false;
			}
			else if (this.scrolledBottom)
			{
				Settings.revokeTerms();
				this.screen.onClose();
			}
		}

		return true;
	}

	public boolean mouseScrolled(double scrollY)
	{
		if (!this.open)
		{
			return false;
		}

		this.scroll = Math.max(0.0F,
				Math.min(this.maxScroll, this.scroll - (float) scrollY * IndexScreen.SCROLL_STEP));
		return true;
	}

	public void scrollTo(float fraction)
	{
		this.scroll = fraction * this.maxScroll;

		if (this.scroll >= this.maxScroll - 0.5F)
		{
			this.scrolledBottom = true;
		}
	}

	public Rect[] pressButtons()
	{
		return this.scrolledBottom ? new Rect[]{this.agree, this.decline, this.usageData} : new Rect[]{this.usageData};
	}

	private String effectiveBody()
	{
		RemoteContent.Terms terms = RemoteContent.terms();

		if (terms != null && !terms.body().isBlank())
		{
			return terms.body();
		}

		String cached = Settings.cachedTermsBody();
		return cached != null ? cached : TERMS_BODY;
	}
}

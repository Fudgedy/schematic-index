package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.modal.TermsModal;
import com.fudgedy.schematicindex.gui.page.SettingsRows.Action;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// The Settings Help & Info tab: guides and release notes, the terms and data choices, storage, and credits
class SettingsExtras
{
	private final IndexScreen screen;

	SettingsExtras(IndexScreen screen)
	{
		this.screen = screen;
	}

	int render(GuiGraphicsExtractor ctx, SettingsRows rows, int y)
	{
		y = rows.header(ctx, "Help", y);
		y = rows.buttons(ctx, "Tutorial", "Walk through the tour of the menu again.", y,
				Action.secondary("Replay", this.screen.tutorialModal::start));
		y = rows.buttons(ctx, "What's new", "Read the notes for the latest release.", y,
				Action.secondary("Open", this.screen.whatsNewModal::openLatest));
		y = rows.buttons(ctx, "Guides", "Pick any guide from the list.", y,
				Action.secondary("Browse", this.screen.helpMenu::open));

		y = rows.header(ctx, "Privacy & data", y);
		y = rows.buttons(ctx, "Terms of service", "Review or decline the terms you agreed to.", y,
				Action.secondary("Review", this.screen.termsModal::openReview));
		y = rows.toggle(ctx, "Usage data", TermsModal.USAGE_DATA_LABEL, Settings.usageData(), Settings::toggleUsageData, y);
		y = rows.buttons(ctx, "Cache", "Free the memory used by thumbnails and 3D previews.", y,
				Action.secondary("Clear cache", SettingsExtras::clearCache));

		y = rows.header(ctx, "Credits", y);
		return CreditsPanel.render(this.screen, ctx, rows.x(), y, rows.width());
	}

	private static void clearCache()
	{
		ImageStore.releaseAll();
		ImageStore.clearDiskCache();
		SchematicPreview.clearCache();
		Toasts.push("Cache cleared", "Thumbnails and previews will reload as needed.", new ItemStack(Items.BUCKET));
	}
}

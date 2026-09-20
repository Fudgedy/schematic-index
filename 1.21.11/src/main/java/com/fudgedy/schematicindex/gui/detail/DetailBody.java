package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.catalogue.Premium;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// The description / materials region below the picture, and the button row under it
class DetailBody
{
	private final DetailView view;

	// Sorted, prettified and clipped once per opened entry; the render loop only walks these
	private List<MaterialRow> materialRows = List.of();
	private @Nullable SchematicEntry materialRowsEntry;
	private int materialRowsRoom = -1;
	private long materialTotal;

	DetailBody(DetailView view)
	{
		this.view = view;
	}

	void render(GuiGraphics ctx, SchematicEntry entry, int mouseX, int mouseY, DetailView.Layout layout)
	{
		IndexScreen screen = this.view.screen;
		Font font = screen.font();
		int x = layout.x();
		int y = layout.y();
		int pad = layout.pad();
		int modalWidth = layout.width();
		int modalHeight = layout.height();
		int imageWidth = layout.imageWidth();
		int imageHeight = layout.imageHeight();

		int regionTop = y + pad + imageHeight + 6;
		// Reserves a band for the hint line drawn at descBottom + 2, clear of the button row
		int descBottom = y + modalHeight - pad - 16 - (font.lineHeight + 12);
		int lineStep = font.lineHeight + 1;

		// Both tabs reuse the one scroll region below the header; premium hides the Materials tab
		int tabHeaderH = font.lineHeight + 5;
		int descLabelW = font.width("Description") + 12;
		this.view.descTab.set(x + pad, regionTop, descLabelW, tabHeaderH - 2);

		if (this.view.isPremium())
		{
			this.view.materialsTab = false;
			this.view.materialsTabRect.set(0, 0, 0, 0);
			this.tab(ctx, this.view.descTab, "Description", true, mouseX, mouseY);
		}
		else
		{
			int matLabelW = font.width("Materials") + 12;
			this.view.materialsTabRect.set(x + pad + descLabelW + 4, regionTop, matLabelW, tabHeaderH - 2);
			this.tab(ctx, this.view.descTab, "Description", !this.view.materialsTab, mouseX, mouseY);
			this.tab(ctx, this.view.materialsTabRect, "Materials", this.view.materialsTab, mouseX, mouseY);
		}

		int descTop = regionTop + tabHeaderH + 5;
		int descViewport = Math.max(lineStep, descBottom - descTop);
		this.view.descBounds.set(x + pad, descTop, imageWidth, descViewport);

		if (this.view.materialsTab)
		{
			this.materials(ctx, entry, x + pad, descTop, imageWidth, descViewport, lineStep);
		}
		else
		{
			// Wrapped uncapped, so a long description scrolls inside its region instead of clipping
			List<String> descLines = screen.wrap(entry.description(), imageWidth, 4096);
			int descContentHeight = descLines.size() * lineStep;
			this.view.descMaxScroll = Math.max(0.0F, descContentHeight - descViewport);
			this.view.descScroll = Math.max(0.0F, Math.min(this.view.descScroll, this.view.descMaxScroll));

			ctx.enableScissor(x + pad, descTop, x + pad + imageWidth, descTop + descViewport);
			int descriptionY = descTop - Math.round(this.view.descScroll);

			for (String row : descLines)
			{
				if (descriptionY + lineStep >= descTop && descriptionY <= descTop + descViewport)
				{
					Theme.text(ctx, font, row, x + pad, descriptionY, Theme.TEXT_MUTE);
				}

				descriptionY += lineStep;
			}

			ctx.disableScissor();

			if (this.view.descMaxScroll > 0.0F)
			{
				int thumbH = Math.max(12, Math.round(descViewport * (descViewport / (float) descContentHeight)));
				int thumbY = descTop
						+ Math.round((descViewport - thumbH) * (this.view.descScroll / this.view.descMaxScroll));
				Theme.roundedRect(ctx, x + pad + imageWidth - 3, thumbY, 3, thumbH, 1, Theme.HAIRLINE);

				if (this.view.descScroll < this.view.descMaxScroll - 0.5F)
				{
					Theme.text(ctx, font, "...", x + pad, descBottom - font.lineHeight, Theme.TEXT_ASH);
				}
			}
		}

		if (!screen.status.isEmpty())
		{
			int statusY = descTop + descViewport + 2;
			Theme.text(ctx, font, Theme.clip(font, screen.status, modalWidth - pad * 2),
					x + pad, statusY, Theme.ACCENT_BRIGHT);
		}

		int buttonY = y + modalHeight - pad - 16;

		if (this.view.isPremium())
		{
			// A bought shard listing flips its action from Buy to a direct Download
			boolean owned = this.view.premium.type() == Premium.Type.SHARD
					&& Premium.owned(this.view.premium.post().id());
			String action = owned ? "Download" : "Buy";
			int closeWidth = font.width(Theme.bold("Close")) + 18;
			int buyWidth = font.width(Theme.bold(action)) + 26;

			this.view.closeButton.set(x + pad, buttonY, closeWidth, 16);
			this.view.download.set(x + modalWidth - pad - buyWidth, buttonY, buyWidth, 16);
			// A premium listing offers only Close and its Buy/Download; nothing else applies to it
			this.view.save.set(0, 0, 0, 0);
			this.view.collection.set(0, 0, 0, 0);
			this.view.preview3d.set(0, 0, 0, 0);
			this.view.load.set(0, 0, 0, 0);

			Buttons.pill(ctx, font, this.view.closeButton, "Close", mouseX, mouseY, false);
			Buttons.gold(ctx, font, this.view.download, action, mouseX, mouseY);
		}
		else
		{
			int downloadWidth = font.width(Theme.bold("Downloading")) + 18;
			String previewLabel = this.view.model ? "Pictures" : "3D preview";
			int previewWidth = font.width(Theme.bold(previewLabel)) + 18;
			int closeWidth = font.width(Theme.bold("Close")) + 18;
			String saveLabel = IndexScreen.isSaved(entry) ? "Saved" : "Save for later";
			int saveWidth = font.width(Theme.bold(saveLabel)) + 18;

			int loadWidth = font.width(Theme.bold("Temporary Load")) + 18;
			int collectWidth = font.width(Theme.bold("Collections")) + 16;

			this.view.closeButton.set(x + pad, buttonY, closeWidth, 16);
			this.view.save.set(this.view.closeButton.x + closeWidth + 6, buttonY, saveWidth, 16);
			this.view.collection.set(this.view.save.x + saveWidth + 6, buttonY, collectWidth, 16);
			this.view.download.set(x + modalWidth - pad - downloadWidth, buttonY, downloadWidth, 16);
			this.view.preview3d.set(this.view.download.x - 6 - previewWidth, buttonY, previewWidth, 16);
			this.view.load.set(this.view.preview3d.x - 6 - loadWidth, buttonY, loadWidth, 16);

			Buttons.pill(ctx, font, this.view.closeButton, "Close", mouseX, mouseY, false);
			Buttons.save(ctx, font, this.view.save, IndexScreen.isSaved(entry), mouseX, mouseY);
			Buttons.pill(ctx, font, this.view.collection, "Collections", mouseX, mouseY,
					this.view.collectionMenu.isOpen());
			Buttons.pill(ctx, font, this.view.load, "Temporary Load", mouseX, mouseY, true);
			Buttons.pill(ctx, font, this.view.preview3d, previewLabel, mouseX, mouseY, false);
			Buttons.download(ctx, font, this.view.download, entry, mouseX, mouseY,
					screen.detailDownloadLocked);
		}

		if (this.view.collectionMenu.isOpen())
		{
			this.view.collectionMenu.render(ctx, mouseX, mouseY, entry, this.view.collection);
		}

		if (this.view.load.contains(mouseX, mouseY))
		{
			String tip = "Temporarily load this schematic in game, close anytime with litematica";
			int tipWidth = font.width(tip) + 8;
			int tipX = Math.max(x + 2, Math.min(this.view.load.x, x + modalWidth - tipWidth - 2));
			int tipY = buttonY - 16;
			Theme.roundedRect(ctx, tipX, tipY, tipWidth, 12, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
			Theme.roundedOutline(ctx, tipX, tipY, tipWidth, 12, Theme.RADIUS_PILL, Theme.HAIRLINE);
			Theme.text(ctx, font, tip, tipX + 4, tipY + 2, Theme.TEXT);
		}

		int cornerSize = 14;
		this.view.cornerClose.set(x + modalWidth - cornerSize / 2, y - cornerSize / 2, cornerSize, cornerSize);
		Buttons.cornerClose(ctx, this.view.cornerClose, mouseX, mouseY);

		// No reporting a premium listing; the flag is hidden and its hotspot cleared
		if (this.view.isPremium())
		{
			this.view.report.set(0, 0, 0, 0);
		}
		else
		{
			int flagSize = 22;
			this.view.report.set(x - flagSize / 2, y + modalHeight - flagSize / 2, flagSize, flagSize);
			boolean flagHover = this.view.report.contains(mouseX, mouseY);
			float flagScale = Theme.buttonScale(this.view.report,
					1.0F + Theme.HOVER_SCALE * Theme.buttonHover(this.view.report, flagHover));
			Theme.pushScale(ctx, this.view.report.x, this.view.report.y, flagSize, flagSize, flagScale);
			Theme.roundedRect(ctx, this.view.report.x, this.view.report.y, flagSize, flagSize,
					Theme.RADIUS_CARD, flagHover ? Theme.ACCENT : Theme.SURFACE_CARD);
			Theme.roundedOutline(ctx, this.view.report.x, this.view.report.y, flagSize, flagSize,
					Theme.RADIUS_CARD, flagHover ? Theme.ACCENT_BRIGHT : Theme.HAIRLINE);
			Theme.flag(ctx, this.view.report.x + 7, this.view.report.y + 6, 10,
					flagHover ? Theme.ON_ACCENT : Theme.TEXT);
			Theme.pop(ctx);

			if (flagHover)
			{
				String tip = "Report this Post";
				int tipWidth = font.width(tip) + 8;
				int tipX = this.view.report.x + flagSize + 3;
				int tipY = this.view.report.y + (flagSize - 12) / 2;
				Theme.roundedRect(ctx, tipX, tipY, tipWidth, 12, Theme.RADIUS_PILL, 0xF00F1114);
				Theme.text(ctx, font, tip, tipX + 4, tipY + 2, Theme.TEXT);
			}
		}

		// Drawn here rather than at the chip, so it paints over the meta rows beneath it
		if (this.view.claim.contains(mouseX, mouseY))
		{
			String claimTip = "I designed this - claim designer credit";
			int claimTipWidth = font.width(claimTip) + 8;
			int claimTipX = Math.max(x + pad, Math.min(this.view.claim.x, x + modalWidth - pad - claimTipWidth));
			int claimTipY = this.view.claim.y + this.view.claim.height + 3;
			Theme.roundedRect(ctx, claimTipX, claimTipY, claimTipWidth, 12, Theme.RADIUS_PILL, 0xF00F1114);
			Theme.text(ctx, font, claimTip, claimTipX + 4, claimTipY + 2, Theme.TEXT);
		}
	}

	private void tab(GuiGraphics ctx, Rect rect, String label, boolean active, int mouseX, int mouseY)
	{
		Font font = this.view.screen.font();
		boolean hovered = rect.contains(mouseX, mouseY);
		int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);
		int textColor = active ? Theme.ON_ACCENT : (hovered ? Theme.TEXT : Theme.TEXT_MUTE);
		Theme.text(ctx, font, label, rect.x + (rect.width - font.width(label)) / 2,
				rect.y + (rect.height - font.lineHeight) / 2 + 1, textColor);
	}

	private void materials(GuiGraphics ctx, SchematicEntry entry, int px, int top, int wide,
			int viewport, int lineStep)
	{
		Font font = this.view.screen.font();
		List<SchematicEntry.Material> materials = entry.materials();

		if (materials == null || materials.isEmpty())
		{
			this.view.descMaxScroll = 0.0F;
			this.view.descScroll = 0.0F;
			Theme.text(ctx, font, entry.materialsLoaded() ? "Material list not available for this post."
					: "Loading material list...", px, top, Theme.TEXT_MUTE);
			return;
		}

		viewport = Math.max(lineStep * 4, Math.round(viewport * 0.72F));

		int iconSize = 16;
		int rowStep = Math.max(lineStep, 18);
		int nameX = px + iconSize + 2; // [16px icon] + 2px gap, then the material name
		int countRight = px + wide - 8; // right edge of the count column, kept clear of the scroll thumb

		// Name room is measured from countRight, so it is the same for every row of a given width
		int nameRoom = countRight - nameX - 6;
		List<MaterialRow> rows = this.materialRows(entry, materials, font, nameRoom);

		int rowCount = rows.size() + 2;
		int contentHeight = rowCount * rowStep;
		this.view.descMaxScroll = Math.max(0.0F, contentHeight - viewport);
		this.view.descScroll = Math.max(0.0F, Math.min(this.view.descScroll, this.view.descMaxScroll));

		ctx.enableScissor(px, top, px + wide, top + viewport);
		int rowY = top - Math.round(this.view.descScroll);

		for (MaterialRow row : rows)
		{
			if (rowY + rowStep >= top && rowY <= top + viewport)
			{
				int countX = countRight - row.countWidth();
				int textY = rowY + (rowStep - font.lineHeight) / 2; // center text in the taller row

				if (row.icon() != null)
				{
					ctx.renderItem(row.icon(), px, rowY + (rowStep - iconSize) / 2);
				}

				Theme.text(ctx, font, row.name(), nameX, textY, Theme.TEXT_MUTE);
				Theme.text(ctx, font, row.countLabel(), countX, textY, Theme.TEXT);
			}

			rowY += rowStep;
		}

		rowY += rowStep;

		if (rowY + rowStep >= top && rowY <= top + viewport)
		{
			String totalCount = SchematicEntry.compact((int) Math.min(Integer.MAX_VALUE, this.materialTotal));
			int totalWidth = font.width(Theme.bold(totalCount));
			int textY = rowY + (rowStep - font.lineHeight) / 2;
			Theme.text(ctx, font, Theme.bold("Total"), px, textY, Theme.TEXT);
			Theme.text(ctx, font, Theme.bold(totalCount), countRight - totalWidth, textY, Theme.TEXT);
		}

		ctx.disableScissor();

		if (this.view.descMaxScroll > 0.0F)
		{
			int thumbH = Math.max(12, Math.round(viewport * (viewport / (float) contentHeight)));
			int thumbY = top + Math.round((viewport - thumbH) * (this.view.descScroll / this.view.descMaxScroll));
			Theme.roundedRect(ctx, px + wide - 3, thumbY, 3, thumbH, 1, Theme.HAIRLINE);
		}
	}

	private List<MaterialRow> materialRows(SchematicEntry entry, List<SchematicEntry.Material> materials, Font font,
			int nameRoom)
	{
		if (entry == this.materialRowsEntry && nameRoom == this.materialRowsRoom)
		{
			return this.materialRows;
		}

		// A private copy, so the source list order is left untouched
		List<SchematicEntry.Material> sorted = new ArrayList<>(materials);
		sorted.sort(Comparator.comparingInt(SchematicEntry.Material::count).reversed()
				.thenComparing(m -> prettify(m.name()), String.CASE_INSENSITIVE_ORDER));

		List<MaterialRow> rows = new ArrayList<>(sorted.size());
		long total = 0L;

		for (SchematicEntry.Material m : sorted)
		{
			total += m.count();
			String countLabel = "x" + SchematicEntry.compact(m.count());
			int countWidth = font.width(countLabel);
			String name = Theme.clip(font, prettify(m.name()), nameRoom - countWidth);
			rows.add(new MaterialRow(name, materialIcon(m.name()), countLabel, countWidth));
		}

		this.materialRows = List.copyOf(rows);
		this.materialRowsEntry = entry;
		this.materialRowsRoom = nameRoom;
		this.materialTotal = total;
		return this.materialRows;
	}

	// "minecraft:oak_stairs" becomes "Oak Stairs"
	private static String prettify(String raw)
	{
		String name = raw;
		int colon = name.indexOf(':');

		if (colon >= 0)
		{
			name = name.substring(colon + 1);
		}

		String[] words = name.split("_");
		StringBuilder out = new StringBuilder();

		for (String word : words)
		{
			if (word.isEmpty())
			{
				continue;
			}

			if (out.length() > 0)
			{
				out.append(' ');
			}

			out.append(Character.toUpperCase(word.charAt(0)));

			if (word.length() > 1)
			{
				out.append(word.substring(1));
			}
		}

		return out.length() == 0 ? raw : out.toString();
	}

	// The ITEM registry is defaulted, so getValue returns AIR rather than null for an unknown id
	@Nullable
	private static ItemStack materialIcon(String name)
	{
		if (name == null || name.isEmpty())
		{
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(name));
		if (item == Items.AIR)
		{
			return null;
		}
		return new ItemStack(item);
	}

	private record MaterialRow(String name, @Nullable ItemStack icon, String countLabel, int countWidth)
	{
	}
}

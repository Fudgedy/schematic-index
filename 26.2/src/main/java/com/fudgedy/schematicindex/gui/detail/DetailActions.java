package com.fudgedy.schematicindex.gui.detail;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.LoadedCache;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.Follows;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Premium;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.FileType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Everything a click inside the open detail card can trigger, including its download and load flows
class DetailActions
{
	private final DetailView view;

	DetailActions(DetailView view)
	{
		this.view = view;
	}

	boolean mouseClicked(double mouseX, double mouseY)
	{
		SchematicEntry entry = this.view.entry();

		if (entry == null)
		{
			return false;
		}

		IndexScreen screen = this.view.screen;
		DetailCamera camera = this.view.camera;

		if (screen.overwriteConfirm.isOpen())
		{
			screen.overwriteConfirm.mouseClicked(mouseX, mouseY);
			return true;
		}

		if (screen.reportModal.isPickerOpen())
		{
			screen.reportModal.clickPicker(mouseX, mouseY);
			return true;
		}

		if (screen.reportModal.isContextOpen())
		{
			screen.reportModal.clickContext(mouseX, mouseY);
			return true;
		}

		if (screen.claimModal.isOpen())
		{
			screen.claimModal.mouseClicked(mouseX, mouseY);
			return true;
		}

		if (this.view.model && camera.layerSlider.contains(mouseX, mouseY))
		{
			camera.draggingLayer = true;
			camera.setLayerFromMouse(mouseX);
			return true;
		}

		// Sits inside the preview image, so it must be handled before the orbit-drag start below
		if (this.view.model && this.view.savePngButton.width > 0
				&& this.view.savePngButton.contains(mouseX, mouseY))
		{
			screen.savePreviewPng(entry);
			Theme.click();
			return true;
		}

		if (!this.view.model && this.view.copyPngButton.width > 0
				&& this.view.copyPngButton.contains(mouseX, mouseY))
		{
			screen.copyImageToClipboard(entry);
			Theme.click();
			return true;
		}

		if (!this.view.model && this.view.copyLinkButton.width > 0
				&& this.view.copyLinkButton.contains(mouseX, mouseY))
		{
			screen.copyShareLink(entry);
			Theme.click();
			return true;
		}

		if (this.view.model && this.view.imageRect.contains(mouseX, mouseY))
		{
			if (SchematicPreview.failed(entry.schematicSlot()))
			{
				SchematicPreview.retry(entry.schematicSlot());
				Theme.click(0.9F);
				return true;
			}

			camera.orbiting = true;
		}

		this.clickCard(mouseX, mouseY, entry);
		return true;
	}

	private void clickCard(double mouseX, double mouseY, SchematicEntry entry)
	{
		IndexScreen screen = this.view.screen;
		DetailCamera camera = this.view.camera;

		if (this.view.collectionMenu.isOpen())
		{
			this.view.collectionMenu.mouseClicked(mouseX, mouseY, entry);
			return;
		}

		if (this.view.collection.contains(mouseX, mouseY))
		{
			Theme.click(1.0F);
			this.view.collectionMenu.open();
			return;
		}

		if (this.view.posterRect.contains(mouseX, mouseY) || this.view.viewProfile.contains(mouseX, mouseY))
		{
			Theme.click(1.0F);
			screen.openProfile(entry.poster());
			return;
		}

		for (int i = 0; i < this.view.starRects.length; i++)
		{
			if (this.view.starRects[i].contains(mouseX, mouseY))
			{
				int star = i;
				screen.requireVerified(() -> this.view.rateStar(entry, star));
				return;
			}
		}

		if (this.view.descTab.contains(mouseX, mouseY))
		{
			Theme.click(this.view.materialsTab ? 1.0F : 0.9F);
			this.view.materialsTab = false;
			this.view.descScroll = 0.0F;
			return;
		}

		if (this.view.materialsTabRect.contains(mouseX, mouseY))
		{
			Theme.click(this.view.materialsTab ? 0.9F : 1.0F);
			this.view.materialsTab = true;
			this.view.descScroll = 0.0F;
			return;
		}

		if (this.view.closeButton.contains(mouseX, mouseY) || this.view.cornerClose.contains(mouseX, mouseY)
				|| !this.view.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.view.close();
		}
		else if (this.view.heart.contains(mouseX, mouseY))
		{
			screen.requireVerified(() -> screen.toggleLike(entry));
		}
		else if (this.view.prev.contains(mouseX, mouseY))
		{
			Theme.click();
			this.view.image = Math.floorMod(this.view.image - 1, entry.imageCount());
			this.view.preloadGallery();
		}
		else if (this.view.next.contains(mouseX, mouseY))
		{
			Theme.click();
			this.view.image = Math.floorMod(this.view.image + 1, entry.imageCount());
			this.view.preloadGallery();
		}
		else if (camera.cutawayToggle.contains(mouseX, mouseY) && !camera.freeLook)
		{
			camera.cutaway = !camera.cutaway;
			Theme.click(camera.cutaway ? 1.3F : 0.9F);
		}
		else if (camera.spectatorButton.contains(mouseX, mouseY))
		{
			if (!camera.freeLook)
			{
				camera.setMode(true, entry);
			}
		}
		else if (camera.orbitButton.contains(mouseX, mouseY))
		{
			if (camera.freeLook)
			{
				camera.setMode(false, entry);
			}
		}
		else if (camera.resetViewButton.contains(mouseX, mouseY))
		{
			Theme.click();
			camera.reset();
		}
		else if (this.view.save.contains(mouseX, mouseY))
		{
			IndexScreen.toggleSaved(entry);
		}
		else if (this.view.follow.contains(mouseX, mouseY))
		{
			screen.requireVerified(() -> this.clickFollow(screen, entry));
		}
		else if (this.view.load.contains(mouseX, mouseY))
		{
			this.loadSchematic(entry);
		}
		else if (this.view.download.contains(mouseX, mouseY))
		{
			if (this.view.isPremium())
			{
				Premium.Entry premium = this.view.premium;
				Theme.click(1.0F);

				if (premium.type() == Premium.Type.SHARD)
				{
					this.shardBuyOrDownload(entry, premium);
				}
				else
				{
					Backend.premiumBuyAsync(entry.id());
					screen.premiumBuyModal.open(premium);
				}
			}
			else if (!screen.detailDownloadLocked)
			{
				screen.requestDownload(entry);
			}
		}
		else if (this.view.preview3d.contains(mouseX, mouseY))
		{
			Theme.click(1.2F);
			this.view.model = !this.view.model;
			if (!this.view.model)
			{
				camera.clearHeldKeys();
			}
			screen.status = this.view.model ? "Drag to orbit, scroll to zoom." : "";
		}
		else if (this.view.claim.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);

			// Claiming needs a verified account; the handshake is silent when the session is good
			screen.startVerify(() -> {
				if (this.view.isOpen())
				{
					screen.claimModal.open();
				}
			});
		}
		else if (this.view.joinDiscord.contains(mouseX, mouseY) && this.view.isPremium())
		{
			Theme.click(1.0F);
			screen.openLink(this.view.premium.partnerUrl());
		}
		else if (this.view.report.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			screen.requireVerified(() -> {
				if (this.view.isOpen())
				{
					screen.reportModal.openPicker();
				}
			});
		}
		else if (!this.view.isPremium() && screen.staff != null)
		{
			screen.staff.clickDetailControls(mouseX, mouseY);
		}
	}

	private void clickFollow(IndexScreen screen, SchematicEntry entry)
	{
		if (!Follows.isFollowing(entry.poster()))
		{
			Follows.toggle(entry.poster());
			Theme.follow();
			this.view.followConfirm = false;
			screen.verifyFollow(entry.id(), entry.poster());
		}
		else if (!this.view.followConfirm)
		{
			this.view.followConfirm = true;
			this.view.followConfirmAt = System.currentTimeMillis();
			Theme.click(0.9F);
		}
		else
		{
			Follows.toggle(entry.poster());
			Theme.click(0.8F);
			this.view.followConfirm = false;
			screen.verifyUnfollow(entry.poster());
		}
	}

	// Owned shard listings download straight away; an unowned one buys first, then downloads
	private void shardBuyOrDownload(SchematicEntry entry, Premium.Entry premium)
	{
		if (Premium.owned(entry.id()))
		{
			this.downloadShard(entry, false);
			return;
		}

		Net.submit(() -> {
			Backend.ApiResult result = Backend.buyShardSchematic(entry.id());
			Minecraft.getInstance().execute(() -> this.onShardBought(entry, premium, result));
		});
	}

	private void onShardBought(SchematicEntry entry, Premium.Entry premium, Backend.ApiResult result)
	{
		if (result.status() == 402)
		{
			Theme.failure();
			Toasts.push("Not Enough Shards", "You don't have enough shards to purchase this schematic.",
					new ItemStack(Items.AMETHYST_SHARD));
			return;
		}

		if (!result.ok())
		{
			this.view.screen.showError(Errors.SHARD_BUY);
			return;
		}

		Premium.markOwned(entry.id());

		if (result.body() != null)
		{
			Shards.applyPanel(result.body());
		}

		// The already-owned case re-downloads without a spend, so it skips the redeem toast
		boolean already = Json.boolOf(result.body(), "alreadyOwned", false);

		if (!already)
		{
			Toasts.push("Redeemed " + premium.shardPrice() + " Shards", "You bought " + entry.title(),
					new ItemStack(Items.AMETHYST_SHARD));
		}

		this.downloadShard(entry, !already);
	}

	private void downloadShard(SchematicEntry entry, boolean justBought)
	{
		IndexScreen screen = this.view.screen;
		Path target = Download.resolveTarget(screen.downloadFileName(entry));

		Net.submit(() -> {
			boolean ok = Backend.downloadShardFile(entry.id(), target);
			Minecraft.getInstance().execute(() -> {
				if (ok)
				{
					Theme.success();
					if (!justBought)
					{
						Toasts.push("Downloaded", entry.title() + " saved to your schematics folder",
								new ItemStack(Items.STRUCTURE_BLOCK));
					}
				}
				else
				{
					Theme.failure();
					screen.showError(Errors.DOWNLOAD);
				}
			});
		});
	}

	private void loadSchematic(SchematicEntry entry)
	{
		IndexScreen screen = this.view.screen;
		Theme.click(1.2F);
		String cacheName = Download.safeName(screen.downloadFileName(entry));
		Path target = LoadedCache.resolve(cacheName);

		// An updated post changes the hash, so a stale cache is re-fetched
		String expectedHash = entry.fileHash();
		if (expectedHash == null || expectedHash.isBlank())
		{
			expectedHash = Backend.expectedHash(entry.id());
		}

		if (Files.exists(target) && LoadedCache.isFresh(cacheName, expectedHash))
		{
			this.loadIntoGame(target, entry.title());
			return;
		}

		String url = entry.fileUrl();
		Path source = url == null || url.isBlank() ? SchematicPreview.pathFor(entry.schematicSlot()) : null;
		screen.status = "Loading the schematic...";
		final String hashToStore = expectedHash;

		Thread worker = new Thread(() -> {
			boolean ok;

			try
			{
				if (url != null && !url.isBlank())
				{
					ok = Backend.download(url, target);
				}
				else if (source != null)
				{
					Files.createDirectories(target.getParent());
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
					ok = true;
				}
				else
				{
					ok = false;
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.debug("Load download failed", e);
				ok = false;
			}

			boolean success = ok && Files.exists(target);

			// Written off-thread with the bytes, so a future load can detect an updated post
			if (success)
			{
				LoadedCache.store(cacheName, hashToStore);
			}

			Minecraft.getInstance().execute(() -> {
				if (success)
				{
					this.loadIntoGame(target, entry.title());
				}
				else
				{
					screen.status = "";
					screen.showError(Errors.LOAD);
				}
			});
		}, "schematicindex-load");
		worker.setDaemon(true);
		worker.start();
	}

	private void loadIntoGame(Path file, String name)
	{
		IndexScreen screen = this.view.screen;

		try
		{
			LitematicaSchematic schematic = LitematicaSchematic.createFromFile(
					file.getParent(), file.getFileName().toString(), FileType.LITEMATICA_SCHEMATIC);

			if (schematic == null)
			{
				screen.showError(Errors.LOAD);
				return;
			}

			SchematicHolder.getInstance().addSchematic(schematic, false);

			Minecraft client = Minecraft.getInstance();
			BlockPos origin = client.player != null ? client.player.blockPosition() : BlockPos.ZERO;
			SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, name, true, true);
			DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);

			// The title is passed untruncated; the toast renderer handles any wrapping
			Toasts.push("Loaded a Schematic", name + " has been temporarily loaded",
					new ItemStack(Items.STRUCTURE_BLOCK));

			screen.status = "";
			screen.onClose();
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Loading into game failed", e);
			screen.showError(Errors.LOAD);
		}
	}
}

package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

class ClaimsSection
{
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 4;

	private final DashboardPage page;
	private final IndexScreen screen;
	private List<Item> items = List.of();
	boolean loading;
	boolean loaded;
	private boolean failed;

	ClaimsSection(DashboardPage page, IndexScreen screen)
	{
		this.page = page;
		this.screen = screen;
	}

	// Split out from render so the scroll-height sum and the render cannot drift
	int measureHeight()
	{
		Font font = this.screen.font();

		if (!McAuth.verified() || (this.items.isEmpty() && !this.failed))
		{
			return 0;
		}

		if (this.failed)
		{
			return 14 + font.lineHeight;
		}

		return 14 + font.lineHeight + 6
				+ this.items.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
	}

	// Returns the y just below what it drew, always matching measureHeight above
	int render(GuiGraphicsExtractor ctx, int x, int y, int w, int scrollTop, int scrollBottom)
	{
		Font font = this.screen.font();

		if (!McAuth.verified() || (this.items.isEmpty() && !this.failed))
		{
			return y;
		}

		y += 14;

		if (this.failed)
		{
			Theme.text(ctx, font, "Couldn't load your claims (" + Errors.CLAIMS_LIST + ").", x, y,
					Theme.TEXT_ASH);
			return y + font.lineHeight;
		}

		Theme.text(ctx, font, Theme.bold("My claims"), x, y, Theme.TEXT);
		y += font.lineHeight + 6;

		for (Item claim : this.items)
		{
			String status;
			int pillColor;

			if ("approved".equalsIgnoreCase(claim.status()))
			{
				status = "Approved";
				pillColor = 0xF03FB950;
			}
			else if ("denied".equalsIgnoreCase(claim.status()))
			{
				status = "Denied";
				pillColor = 0xF0D64545;
			}
			else
			{
				status = "In review";
				pillColor = 0xF0C8811E;
			}

			if (y + ROW_HEIGHT >= scrollTop && y <= scrollBottom)
			{
				int pillW = font.width(status) + 10;
				int titleY = y + (ROW_HEIGHT - font.lineHeight) / 2;
				int pillY = y + (ROW_HEIGHT - 14) / 2;
				String title = this.screen.trimToWidth(claim.title(), w - pillW - 10);
				Theme.text(ctx, font, title, x, titleY, Theme.TEXT_MUTE);
				Theme.roundedRect(ctx, x + w - pillW, pillY, pillW, 14, Theme.RADIUS_PILL, pillColor);
				Theme.text(ctx, font, status, x + w - pillW + 5, pillY + 3, 0xFFFFFFFF);
			}

			y += ROW_HEIGHT + ROW_GAP;
		}

		return y - ROW_GAP;
	}

	// The backend keys claims off X-Session, so callers gate on McAuth.verified()
	void load()
	{
		this.loading = true;
		Thread worker = new Thread(() -> {
			SchematicIndexMod.LOGGER.debug("Loading my claims");
			JsonObject data = Backend.myClaims();
			Minecraft.getInstance().execute(() -> {
				this.loading = false;

				if (data == null || !data.has("claims") || !data.get("claims").isJsonArray())
				{
					this.failed = data == null;
					this.loaded = true;
					return;
				}

				List<Item> items = new ArrayList<>();

				for (JsonElement element : data.getAsJsonArray("claims"))
				{
					if (!element.isJsonObject())
					{
						continue;
					}

					JsonObject row = element.getAsJsonObject();
					String title = Json.stringOf(row, "title", "Unknown post");
					String status = Json.stringOf(row, "status", "pending");
					String postId = Json.stringOf(row, "postId", "");
					items.add(new Item(postId, title, status));
				}

				this.items = List.copyOf(items);
				this.failed = false;
				this.loaded = true;
				this.page.cacheDirty = true;
			});
		}, "schematicindex-myclaims");
		worker.setDaemon(true);
		worker.start();
	}

	private record Item(String postId, String title, String status)
	{
	}
}

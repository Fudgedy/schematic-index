package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ModalChrome;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.TextBoxes;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LoadCodeModal
{
	private final IndexScreen screen;
	private boolean open;
	private String input = "";
	private String status = "";
	private final Rect confirmButton = new Rect();
	private final Rect cancelButton = new Rect();
	// Captured during render so a click on the scrim outside it dismisses the modal
	private final Rect bounds = new Rect();

	public LoadCodeModal(IndexScreen screen)
	{
		this.screen = screen;
	}

	public boolean isOpen()
	{
		return this.open;
	}

	public void open()
	{
		this.open = true;
		this.input = "";
		this.status = "";
		this.screen.modalFocus = -1;
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (!this.open)
		{
			return;
		}

		Font font = this.screen.font();
		ctx.fill(0, 0, this.screen.width, this.screen.height, Theme.SCRIM);

		int pad = 16;
		int cardWidth = Math.min(this.screen.width - 40, 300);
		int line = font.lineHeight;
		int cardHeight = pad + line + 10 + IndexScreen.FIELD_HEIGHT + 8 + line + 12 + IndexScreen.FIELD_HEIGHT + pad;
		int x = (this.screen.width - cardWidth) / 2;
		int y = (this.screen.height - cardHeight) / 2;
		this.bounds.set(x, y, cardWidth, cardHeight);

		ModalChrome.frame(ctx, x, y, cardWidth, cardHeight, false);
		Theme.text(ctx, font, Theme.bold("Load a collection code"), x + pad, y + pad, Theme.TEXT);

		int fieldY = y + pad + line + 10;
		int fieldWidth = cardWidth - pad * 2;
		TextBoxes.underscoreLine(ctx, font, x + pad, fieldY, fieldWidth, IndexScreen.FIELD_HEIGHT, this.input,
				"5-character code", false);

		int statusY = fieldY + IndexScreen.FIELD_HEIGHT + 8;

		if (!this.status.isEmpty())
		{
			Theme.text(ctx, font, Theme.clip(font, this.status, fieldWidth), x + pad, statusY, Theme.ACCENT_BRIGHT);
		}

		int btnY = statusY + line + 12;
		int cancelWidth = font.width(Theme.bold("Cancel")) + 20;
		int loadWidth = font.width(Theme.bold("Load")) + 20;
		this.cancelButton.set(x + pad, btnY, cancelWidth, IndexScreen.FIELD_HEIGHT);
		this.confirmButton.set(x + cardWidth - pad - loadWidth, btnY, loadWidth, IndexScreen.FIELD_HEIGHT);
		Buttons.pill(ctx, font, this.cancelButton, "Cancel", mouseX, mouseY, false);
		Buttons.pill(ctx, font, this.confirmButton, "Load", mouseX, mouseY, true);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.open)
		{
			return false;
		}

		if (this.confirmButton.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.confirm();
		}
		else if (this.cancelButton.contains(mouseX, mouseY) || !this.bounds.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			this.open = false;
		}

		return true;
	}

	public boolean keyPressed(KeyEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		if (IndexScreen.isPasteChord(event))
		{
			this.paste();
			return true;
		}

		switch (event.key())
		{
			case 259 -> {
				if (!this.input.isEmpty())
				{
					this.input = this.input.substring(0, this.input.length() - 1);
				}
			}
			case 257, 335 -> this.confirm();
			case 256 -> this.open = false;
			default -> {
			}
		}

		return true;
	}

	public boolean charTyped(CharacterEvent event)
	{
		if (!this.open)
		{
			return false;
		}

		String typed = event.codepointAsString();

		if (this.input.length() < 5 && !typed.isEmpty() && Character.isLetterOrDigit(typed.charAt(0)))
		{
			this.input += typed.toUpperCase(Locale.ROOT);
		}

		return true;
	}

	private void paste()
	{
		String clip;

		try
		{
			clip = Minecraft.getInstance().keyboardHandler.getClipboard();
		}
		catch (Exception e)
		{
			return;
		}

		for (int i = 0; i < clip.length() && this.input.length() < 5; i++)
		{
			char c = clip.charAt(i);

			if (Character.isLetterOrDigit(c))
			{
				this.input += Character.toUpperCase(c);
			}
		}
	}

	private void confirm()
	{
		String code = this.input.trim().toUpperCase(Locale.ROOT);

		if (code.length() != 5)
		{
			this.status = "Codes are 5 characters.";
			return;
		}

		this.status = "Loading...";

		Thread worker = new Thread(() -> {
			JsonObject data = Backend.loadCollectionCode(code);
			Minecraft.getInstance().execute(() -> {
				if (data == null)
				{
					this.status = "Unknown code.";
					return;
				}

				// An unexpected response shape must show a message, not throw out of the client-thread task
				List<String> ids = new ArrayList<>();
				String original;

				try
				{
					if (!data.has("postIds") || !data.get("postIds").isJsonArray())
					{
						this.status = "Couldn't read that collection.";
						return;
					}

					ids.addAll(Json.listOf(data, "postIds"));
					original = Json.stringOf(data, "name", "").trim();
				}
				catch (Exception e)
				{
					SchematicIndexMod.LOGGER.debug("Load-code response was malformed", e);
					this.status = "Couldn't read that collection.";
					return;
				}

				if (ids.isEmpty())
				{
					this.status = "That collection is empty.";
					return;
				}

				String name = original.isEmpty() ? "Shared " + code : original;

				if (CollectionStore.names().contains(name))
				{
					name = name + " (" + code + ")";
				}

				CollectionStore.create(name);

				for (String id : ids)
				{
					if (!CollectionStore.contains(name, id))
					{
						CollectionStore.toggle(name, id);
					}
				}

				this.open = false;
				this.screen.activeCollection = name;
				this.screen.sharedCode = null;
				this.screen.layoutChips();
				this.screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight;
				this.screen.refilter();
				Toasts.push("Collection loaded", ids.size() + " posts added.", new ItemStack(Items.BOOKSHELF));
			});
		}, "schematicindex-loadcode");
		worker.setDaemon(true);
		worker.start();
	}
}

package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// The "?" in the header: every guide in one list, each re-openable whatever its seen flag says
public class HelpMenu implements Overlay
{
	private static final String[] LABELS = {"Tutorial", "Preview controls", "Loading a download", "Mapart", "Cosmetics",
			"Collections", "Leaderboards", "Build of the Day", "What's new"};
	private static final ItemStack[] ICONS = {new ItemStack(Items.COMPASS), new ItemStack(Items.SPYGLASS),
			new ItemStack(Items.STRUCTURE_BLOCK), new ItemStack(Items.FILLED_MAP), new ItemStack(Items.NAME_TAG),
			new ItemStack(Items.ENDER_CHEST), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.GOLDEN_APPLE),
			new ItemStack(Items.NETHER_STAR)};
	private static final int ROW_HEIGHT = 18;
	private static final int PAD = 4;

	private final IndexScreen screen;
	private boolean open;
	private final Rect button = new Rect();
	private final List<Rect> rows = new ArrayList<>();

	public HelpMenu(IndexScreen screen)
	{
		this.screen = screen;

		for (int i = 0; i < LABELS.length; i++)
		{
			this.rows.add(new Rect());
		}
	}

	@Override
	public boolean isOpen()
	{
		return this.open;
	}

	public void open()
	{
		this.open = true;
	}

	// Takes the bell's place when the bell is hidden, else sits just left of it
	public void layoutButton(Rect bell, boolean bellShown)
	{
		this.button.set(bellShown ? bell.x - 6 - 16 : bell.x, bell.y, 16, 16);
	}

	public Rect button()
	{
		return this.button;
	}

	public void renderButton(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Buttons.glyph(ctx, this.screen.font(), this.button, "?", mouseX, mouseY, this.open);
	}

	public boolean buttonClicked(double mouseX, double mouseY)
	{
		if (!this.button.contains(mouseX, mouseY))
		{
			return false;
		}

		Theme.click(this.open ? 0.9F : 1.1F);
		this.open = !this.open;
		return true;
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		int width = 0;

		for (String label : LABELS)
		{
			width = Math.max(width, font.width(label));
		}

		width += 24 + PAD * 2 + 6;
		int height = LABELS.length * ROW_HEIGHT + PAD * 2;
		int x = Math.max(4, this.button.x + this.button.width - width);
		int y = this.button.y + this.button.height + 4;

		Theme.roundedRect(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_CARD, Theme.HAIRLINE);

		for (int i = 0; i < LABELS.length; i++)
		{
			Rect row = this.rows.get(i);
			row.set(x + PAD, y + PAD + i * ROW_HEIGHT, width - PAD * 2, ROW_HEIGHT);

			if (row.contains(mouseX, mouseY))
			{
				Theme.roundedRect(ctx, row.x, row.y, row.width, row.height, Theme.RADIUS_PILL, Theme.SURFACE_CARD);
			}

			Theme.itemScaled(ctx, ICONS[i], row.x + 3, row.y + 3, 0.75F);
			Theme.text(ctx, font, LABELS[i], row.x + 20, row.y + (ROW_HEIGHT - font.lineHeight) / 2 + 1, Theme.TEXT);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		this.open = false;

		if (this.button.contains(mouseX, mouseY))
		{
			Theme.click(0.9F);
			return true;
		}

		for (int i = 0; i < this.rows.size(); i++)
		{
			if (this.rows.get(i).contains(mouseX, mouseY))
			{
				Theme.click(1.1F);
				this.openGuide(i);
				return true;
			}
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

	public void openGuide(int index)
	{
		switch (index)
		{
			case 0 -> this.screen.tutorialModal.start();
			case 1 -> this.screen.coachMark.replay(CoachMark.Kind.PREVIEW);
			case 2 -> this.screen.loadGuideModal.open();
			case 3 -> this.screen.coachMark.replay(CoachMark.Kind.MAPART);
			case 4 -> this.screen.coachMark.replay(CoachMark.Kind.COSMETICS);
			case 5 -> this.screen.coachMark.replay(CoachMark.Kind.COLLECTIONS);
			case 6 -> this.screen.coachMark.replay(CoachMark.Kind.LEADERBOARDS);
			case 7 -> this.screen.coachMark.replay(CoachMark.Kind.BUILD_OF_DAY);
			default -> this.screen.whatsNewModal.openLatest();
		}
	}
}

package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BrowseSearch
{
	private final BrowsePage browse;
	private final IndexScreen screen;
	EditBox box;

	// Recomputed only when the query or the catalogue changes, not per frame
	private final List<String> suggestions = new ArrayList<>();
	private final List<String> suggestCreators = new ArrayList<>();
	private String suggestQuery = null;
	private long suggestRevision = -1;
	// Title rows first, then creator rows, matching the render order
	private final List<Rect> suggestRects = new ArrayList<>();

	BrowseSearch(BrowsePage browse, IndexScreen screen)
	{
		this.browse = browse;
		this.screen = screen;
	}

	public void build(int searchX, int searchY, int searchWidth)
	{
		this.box = FormFields.textField(this.screen, searchX + 6, searchY + 4, searchWidth - 12, "Search",
				this.browse.query);
		this.box.setResponder(value -> {
			this.browse.query = value;
			// Suggestions and the chip row are cheap enough to keep responsive on every keystroke;
			// only the refilter is debounced
			this.recomputeSuggestions();
			this.browse.layoutChips();
			this.screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight;
			this.browse.searchRefilterPending = true;
			this.browse.searchRefilterAt = System.currentTimeMillis() + BrowsePage.SEARCH_DEBOUNCE_MS;
		});
	}

	public void reposition(int searchX, int searchW)
	{
		if (this.box != null)
		{
			this.box.setX(searchX + 6);
			this.box.setWidth(searchW - 12);
		}
	}

	public boolean isFocused()
	{
		return this.box.isFocused();
	}

	public void render(GuiGraphics ctx, int mouseX, int mouseY, float partialTick, boolean overlayOpen)
	{
		this.box.setVisible(this.screen.gridPage());

		if (this.screen.gridPage())
		{
			this.box.render(ctx, mouseX, mouseY, partialTick);

			if (!overlayOpen)
			{
				this.renderSuggestions(ctx, mouseX, mouseY);
			}
			else
			{
				this.suggestRects.clear();
			}
		}
		else if (this.box.isFocused())
		{
			this.box.setFocused(false);
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (!this.box.isFocused() || this.screen.detailView.isOpen())
		{
			return false;
		}

		for (int i = 0; i < this.suggestRects.size(); i++)
		{
			if (!this.suggestRects.get(i).contains(mouseX, mouseY))
			{
				continue;
			}

			Theme.click();

			if (i < this.suggestions.size())
			{
				this.browse.setSearch(this.suggestions.get(i));
				this.browse.layoutChips();
				this.screen.gridTop = IndexScreen.TOP_BAR_HEIGHT + this.screen.chipRowHeight;
				this.browse.refilter();
			}
			else
			{
				int creatorIndex = i - this.suggestions.size();

				if (creatorIndex < this.suggestCreators.size())
				{
					this.browse.profile.open(this.suggestCreators.get(creatorIndex));
				}
			}

			return true;
		}

		return false;
	}

	private void recomputeSuggestions()
	{
		String query = this.browse.query;
		this.suggestQuery = query;
		this.suggestRevision = Catalogue.revision();
		this.suggestions.clear();
		this.suggestCreators.clear();

		String q = query.trim().toLowerCase(Locale.ROOT);

		if (q.length() < 2)
		{
			return;
		}

		for (SchematicEntry entry : Catalogue.posts())
		{
			String title = entry.title();

			if (this.suggestions.size() < 6
					&& title.toLowerCase(Locale.ROOT).contains(q)
					&& !this.suggestions.contains(title))
			{
				this.suggestions.add(title);
			}

			String poster = entry.poster();

			if (this.suggestCreators.size() < 4
					&& poster.toLowerCase(Locale.ROOT).contains(q)
					&& !this.suggestCreators.contains(poster))
			{
				this.suggestCreators.add(poster);
			}

			if (this.suggestions.size() >= 6 && this.suggestCreators.size() >= 4)
			{
				break;
			}
		}
	}

	private void renderSuggestions(GuiGraphics ctx, int mouseX, int mouseY)
	{
		this.suggestRects.clear();

		if (!this.box.isFocused() || this.screen.detailView.isOpen())
		{
			return;
		}

		// The responder already covers query changes; this catches a catalogue change under focus
		if (this.suggestRevision != Catalogue.revision())
		{
			this.recomputeSuggestions();
		}

		if (this.suggestions.isEmpty() && this.suggestCreators.isEmpty())
		{
			return;
		}

		Font font = this.screen.font();
		int x = this.box.getX() - 6;
		int w = this.box.getWidth() + 12;
		int rowH = font.lineHeight + 5;
		int y = IndexScreen.TOP_BAR_HEIGHT + 2;
		int rows = this.suggestions.size() + this.suggestCreators.size();
		int h = rows * rowH + 6;

		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.HAIRLINE);

		int ty = y + 5;

		for (String match : this.suggestions)
		{
			Rect row = new Rect();
			row.set(x, ty - 2, w, rowH);
			this.suggestRects.add(row);

			boolean hovered = row.contains(mouseX, mouseY);

			if (hovered)
			{
				Theme.roundedRect(ctx, x + 2, ty - 2, w - 4, rowH, Theme.RADIUS_PILL, Theme.HAIRLINE);
			}

			Theme.text(ctx, font, Theme.clip(font, match, w - 12), x + 6, ty,
					hovered ? Theme.TEXT : Theme.TEXT_MUTE);
			ty += rowH;
		}

		// Creator rects follow the title rects so mouseClicked can split them by index
		for (String creator : this.suggestCreators)
		{
			Rect row = new Rect();
			row.set(x, ty - 2, w, rowH);
			this.suggestRects.add(row);

			boolean hovered = row.contains(mouseX, mouseY);

			if (hovered)
			{
				Theme.roundedRect(ctx, x + 2, ty - 2, w - 4, rowH, Theme.RADIUS_PILL, Theme.HAIRLINE);
			}

			Theme.text(ctx, font, Theme.clip(font, "by " + creator, w - 12), x + 6, ty,
					hovered ? Theme.ACCENT_BRIGHT : Theme.TEXT_ASH);
			ty += rowH;
		}
	}
}

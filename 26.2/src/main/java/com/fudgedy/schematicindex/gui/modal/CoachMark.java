package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Overlay;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

// One just-in-time bubble per feature, the first time the player reaches it; queued until nothing else is up
public class CoachMark implements Overlay
{
	public enum Kind
	{
		PREVIEW(SettingsKeys.COACH_PREVIEW, "preview", "Previewing a build",
				"Drag to orbit · scroll to zoom · the layers slider cuts away floors · F to fly through · the "
						+ "material list shows what you need."),
		DOWNLOAD(SettingsKeys.COACH_DOWNLOAD, "download", "Loading it in Litematica", ""),
		MAPART(SettingsKeys.COACH_MAPART, "mapart", "Making a mapart",
				"Pick an image, choose staircase mode and dithering, split big images across maps, then line it up "
						+ "with the corner overlay."),
		COSMETICS(SettingsKeys.COACH_COSMETICS, "cosmetics", "Cosmetics",
				"Your first colour is free. Add effects and tags with Shards, earn Shards every day, and link Discord "
						+ "in Settings for a free tag."),
		COLLECTIONS(SettingsKeys.COACH_COLLECTIONS, "collections", "Collections",
				"Save posts, organise them into collections, and share a collection code with friends."),
		LEADERBOARDS(SettingsKeys.COACH_LEADERBOARDS, "leaderboards", "Leaderboards",
				"Three boards: Most Shards, Streaks and Highest Streaks. Your rank always shows, even outside "
						+ "the top ten. Turn off \"Show me on leaderboards\" in Settings under Profile to opt out."),
		BUILD_OF_DAY(SettingsKeys.COACH_BUILD_OF_DAY, "build_of_day", "Build of the Day",
				"Picked daily from yesterday's most popular builds. Vote once a day in Browse. The poster earns "
						+ "200 Shards, the designer 250.");

		private final String key;
		private final String usage;
		private final String title;
		private final String text;

		Kind(String key, String usage, String title, String text)
		{
			this.key = key;
			this.usage = usage;
			this.title = title;
			this.text = text;
		}
	}

	private static final int WIDTH = 240;
	private static final int PAD = 10;
	private static final int ARROW = 5;

	private final IndexScreen screen;
	private final ArrayDeque<Kind> queue = new ArrayDeque<>();
	private @Nullable Kind showing;
	private String downloadFile = "";
	// Collections before the name prompt opened, so a cancelled prompt does not count as a new collection
	private int collectionsBefore = -1;
	private final Rect gotIt = new Rect();
	private final Rect showMe = new Rect();

	public CoachMark(IndexScreen screen)
	{
		this.screen = screen;
	}

	public static void maybe(IndexScreen screen, Kind kind)
	{
		screen.coachMark.request(kind);
	}

	public static void download(IndexScreen screen, String fileName)
	{
		screen.coachMark.downloadFile = Download.safeName(fileName);
		screen.coachMark.request(Kind.DOWNLOAD);
	}

	// Veterans already know every feature, so only players new in this version get the bubbles
	public static void migrate()
	{
		if (Settings.flag(SettingsKeys.COACH_MIGRATED, false))
		{
			return;
		}

		if (Settings.tutorialSeen())
		{
			for (Kind kind : Kind.values())
			{
				Settings.setFlag(kind.key, true);
			}
		}

		Settings.setFlag(SettingsKeys.COACH_MIGRATED, true);
	}

	@Override
	public boolean isOpen()
	{
		return this.showing != null;
	}

	public void request(Kind kind)
	{
		if (!RemoteContent.feature("coachMarks") || Settings.flag(kind.key, false) || this.showing == kind
				|| this.queue.contains(kind))
		{
			return;
		}

		if (kind == Kind.COLLECTIONS)
		{
			this.collectionsBefore = CollectionStore.names().size();
		}

		this.queue.add(kind);
	}

	// From the Help menu: shown now, whatever the seen flags say
	public void replay(Kind kind)
	{
		this.queue.remove(kind);

		if (kind == Kind.DOWNLOAD && this.downloadFile.isEmpty())
		{
			this.downloadFile = "<file>";
		}

		this.showing = kind;
	}

	public void tick()
	{
		Kind next = this.queue.peek();

		if (this.showing != null || next == null || this.blocked())
		{
			return;
		}

		this.queue.poll();

		if (!this.stillRelevant(next))
		{
			return;
		}

		this.showing = next;
		Settings.setFlag(next.key, true);
		Usage.once("coach_" + next.usage);
	}

	@Override
	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		Kind kind = this.showing;

		if (kind == null)
		{
			return;
		}

		Font font = this.screen.font();
		List<String> body = this.screen.wrap(this.textOf(kind), WIDTH - PAD * 2, 6);
		int height = PAD + font.lineHeight + 4 + body.size() * (font.lineHeight + 1) + 8 + 16 + PAD;
		Rect anchor = this.anchorOf(kind);
		int x;
		int y;

		if (anchor == null)
		{
			x = (this.screen.width - WIDTH) / 2;
			y = this.screen.height - height - 24;
		}
		else
		{
			x = anchor.x + anchor.width + ARROW + 4;
			y = Math.max(IndexScreen.TOP_BAR_HEIGHT + 4, Math.min(anchor.y + anchor.height / 2 - height / 2,
					this.screen.height - height - 8));
			Theme.roundedOutline(ctx, anchor.x - 1, anchor.y - 1, anchor.width + 2, anchor.height + 2, Theme.RADIUS_CARD,
					Theme.ACCENT_BRIGHT);
			this.renderArrow(ctx, x, anchor.y + anchor.height / 2);
		}

		Theme.roundedRect(ctx, x, y, WIDTH, height, Theme.RADIUS_MODAL, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, WIDTH, height, Theme.RADIUS_MODAL, Theme.ACCENT);

		int ty = y + PAD;
		Theme.text(ctx, font, Theme.bold(kind.title), x + PAD, ty, Theme.TEXT);
		ty += font.lineHeight + 4;

		for (String row : body)
		{
			Theme.text(ctx, font, row, x + PAD, ty, Theme.TEXT_MUTE);
			ty += font.lineHeight + 1;
		}

		int buttonY = y + height - PAD - 16;
		int gotWidth = font.width(Theme.bold("Got it")) + 20;
		this.gotIt.set(x + WIDTH - PAD - gotWidth, buttonY, gotWidth, 16);
		Buttons.pill(ctx, font, this.gotIt, "Got it", mouseX, mouseY, true);

		if (kind != Kind.DOWNLOAD)
		{
			this.showMe.set(0, 0, 0, 0);
			return;
		}

		int showWidth = font.width(Theme.bold("Show me")) + 16;
		this.showMe.set(this.gotIt.x - 6 - showWidth, buttonY, showWidth, 16);
		Buttons.pill(ctx, font, this.showMe, "Show me", mouseX, mouseY, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY)
	{
		if (this.gotIt.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.showing = null;
		}
		else if (this.showMe.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.showing = null;
			this.screen.loadGuideModal.open();
		}

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == 256 || event.key() == 257 || event.key() == 335)
		{
			this.showing = null;
		}

		return true;
	}

	private String textOf(Kind kind)
	{
		if (kind != Kind.DOWNLOAD)
		{
			return kind.text;
		}

		Path folder = Settings.downloadDirectory().getFileName();
		String where = (folder == null ? "schematics" : folder.toString()) + "/" + this.downloadFile;
		return "Saved to " + where + ". In game press M (Litematica menu) > Load Schematics > pick it > Load.";
	}

	// Rail pages ahead of any conditional tab keep their ordinal as their rail slot
	private @Nullable Rect anchorOf(Kind kind)
	{
		IndexScreen.Page page = switch (kind)
		{
			case MAPART -> IndexScreen.Page.MAPART;
			case COSMETICS -> IndexScreen.Page.COSMETICS;
			case COLLECTIONS -> IndexScreen.Page.SAVED;
			default -> null;
		};

		if (page == null || page.ordinal() >= this.screen.railRects.size())
		{
			return null;
		}

		return this.screen.railRects.get(page.ordinal());
	}

	private void renderArrow(GuiGraphicsExtractor ctx, int bubbleX, int tipY)
	{
		for (int i = 0; i < ARROW; i++)
		{
			ctx.fill(bubbleX - ARROW + i, tipY - i, bubbleX - ARROW + i + 1, tipY + i + 1, Theme.ACCENT);
		}
	}

	private boolean stillRelevant(Kind kind)
	{
		return switch (kind)
		{
			case PREVIEW -> this.screen.detailView.isOpen();
			case COLLECTIONS -> CollectionStore.names().size() > this.collectionsBefore;
			default -> true;
		};
	}

	// Waits out every modal that owns the screen, so a bubble never lands on top of a prompt
	private boolean blocked()
	{
		return this.screen.tutorialModal.isOpen() || this.screen.termsModal.isOpen()
				|| this.screen.shardWelcomeModal.isOpen() || this.screen.nameInputModal.isOpen()
				|| this.screen.loadCodeModal.isOpen() || this.screen.errorModal.isOpen()
				|| this.screen.downloadAllModal.isOpen() || this.screen.overwriteConfirm.isOpen()
				|| this.screen.shardPanel.isOpen() || this.screen.premiumBuyModal.isOpen()
				|| this.screen.whatsNewModal.isOpen() || this.screen.updateRequiredModal.isOpen()
				|| this.screen.loadGuideModal.isOpen() || this.screen.helpMenu.isOpen();
	}
}

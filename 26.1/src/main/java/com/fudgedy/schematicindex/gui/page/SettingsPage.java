package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import com.fudgedy.schematicindex.gui.page.SettingsRows.Action;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.Tabs;
import com.fudgedy.schematicindex.gui.widget.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Settings as a category sidebar beside one scrolling column of uniform rows; the open category is saved
public class SettingsPage
{
	private static final int PROFILE = 0;
	private static final int GENERAL = 1;
	private static final int APPEARANCE = 2;
	private static final int NOTIFICATIONS = 3;
	private static final int HELP = 4;
	private static final int CONTROLS = 5;
	private static final String[] LABELS = {"Profile", "General", "Appearance", "Notifications", "Help & Info", "Controls"};
	// Persisted by id rather than index, so reordering the sidebar never lands a player on the wrong tab
	private static final String[] IDS = {"profile", "general", "appearance", "notifications", "help", "controls"};
	private static final String RETIRED_DISCORD_ID = "discord";
	private static final int SIDEBAR_WIDTH = 104;
	private static final int FORM_MAX_WIDTH = 480;
	private static final int KEY_DOWN = 264;
	private static final int KEY_UP = 265;
	private static final String DENSITY_WIDEST = "Comfortable";

	private final IndexScreen screen;
	private final SettingsRows rows;
	private final SettingsExtras extras;
	private final AccountCard account;
	private final List<Rect> tabHits = new ArrayList<>();
	private final Rect body = new Rect();
	private final Rect volumeTrack = new Rect();
	private final Rect fovTrack = new Rect();
	private boolean draggingVolume;
	private boolean draggingFov;

	public SettingsPage(IndexScreen screen)
	{
		this.screen = screen;
		this.rows = new SettingsRows(screen);
		this.extras = new SettingsExtras(screen);
		this.account = new AccountCard(screen);
	}

	// For entry points that exist to change something on the Profile tab, such as the leaderboard opt-out
	public static void showProfile()
	{
		Settings.setText(SettingsKeys.SETTINGS_TAB, IDS[PROFILE]);
	}

	public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		Font font = this.screen.font();
		int pageX = this.screen.contentX + Theme.SPACE_L;
		int pageRight = this.screen.contentX + this.screen.contentWidth - Theme.SPACE_L;
		int titleY = IndexScreen.TOP_BAR_HEIGHT + Theme.SPACE_L;
		Theme.textScaled(ctx, font, Theme.bold("Settings"), pageX, titleY, 1.5F, Theme.TEXT);

		int top = titleY + Math.round(font.lineHeight * 1.5F) + Theme.SPACE_L;
		int bottom = this.screen.height - IndexScreen.OUTER_MARGIN;
		int selected = selected();
		Tabs.sidebar(ctx, font, this.tabHits, LABELS, selected, pageX, top, SIDEBAR_WIDTH, mouseX, mouseY);

		int bodyX = pageX + SIDEBAR_WIDTH + Theme.SPACE_XL;
		this.body.set(bodyX, top, Math.min(FORM_MAX_WIDTH, pageRight - bodyX), bottom - top);
		// Rows scrolled under the title must not light up or take the click meant for the frame
		boolean inBody = this.body.contains(mouseX, mouseY);
		int bodyMouseX = inBody ? mouseX : -1;
		int bodyMouseY = inBody ? mouseY : -1;
		this.volumeTrack.set(0, 0, 0, 0);
		this.fovTrack.set(0, 0, 0, 0);

		int startY = top - Math.round(this.screen.scroll);
		this.rows.begin(this.body.x, this.body.width, bodyMouseX, bodyMouseY);
		ctx.enableScissor(this.body.x, this.body.y, this.body.x + this.body.width, this.body.y + this.body.height);
		int end = switch (selected)
		{
			case PROFILE -> this.account.render(ctx, this.rows, startY);
			case APPEARANCE -> this.appearance(ctx, startY);
			case NOTIFICATIONS -> this.notifications(ctx, startY);
			case HELP -> this.extras.render(ctx, this.rows, startY);
			case CONTROLS -> this.controls(ctx, startY);
			default -> this.general(ctx, startY);
		};
		ctx.disableScissor();

		this.screen.maxScroll = Math.max(0.0F, end - startY - this.body.height);
		this.screen.scroll = Math.min(this.screen.scroll, this.screen.maxScroll);
		this.screen.verticalScrollbar(ctx, IndexScreen.SCROLLBAR_MAIN, this.screen.scroll, this.screen.maxScroll, this.body.y,
				this.body.height, this.body.x + this.body.width + Theme.SPACE_XS);
		Tooltip.render(ctx, font, mouseX, mouseY, this.screen.width, this.screen.height);
	}

	public boolean mouseClicked(double mouseX, double mouseY)
	{
		int tab = Tabs.hit(this.tabHits, mouseX, mouseY);

		if (tab >= 0)
		{
			this.select(tab);
			return true;
		}

		if (!this.body.contains(mouseX, mouseY))
		{
			return true;
		}

		if (this.volumeTrack.contains(mouseX, mouseY))
		{
			this.draggingVolume = true;
			this.setVolumeFromMouse(mouseX);
			return true;
		}

		if (this.fovTrack.contains(mouseX, mouseY))
		{
			this.draggingFov = true;
			this.setFovFromMouse(mouseX);
			return true;
		}

		this.rows.mouseClicked(mouseX, mouseY);
		return true;
	}

	public boolean keyPressed(int key)
	{
		if (key != KEY_DOWN && key != KEY_UP)
		{
			return false;
		}

		this.select(Math.floorMod(selected() + (key == KEY_DOWN ? 1 : -1), LABELS.length));
		return true;
	}

	// Only this frame's buttons, so a control on a hidden tab can never take the press animation
	public Rect[] pressButtons(Rect closeButton)
	{
		List<Rect> pressable = this.rows.pressable();
		Rect[] buttons = new Rect[pressable.size() + 1];
		buttons[0] = closeButton;

		for (int i = 0; i < pressable.size(); i++)
		{
			buttons[i + 1] = pressable.get(i);
		}

		return buttons;
	}

	public boolean mouseDragged(double mouseX)
	{
		if (this.draggingVolume)
		{
			this.setVolumeFromMouse(mouseX);
			return true;
		}

		if (this.draggingFov)
		{
			this.setFovFromMouse(mouseX);
			return true;
		}

		return false;
	}

	public boolean mouseReleased()
	{
		if (!this.draggingVolume && !this.draggingFov)
		{
			return false;
		}

		this.draggingVolume = false;
		this.draggingFov = false;
		// Only on release, so the one click lands at the volume just picked
		Theme.click(1.0F);
		return true;
	}

	private int general(GuiGraphicsExtractor ctx, int y)
	{
		SettingsRows rows = this.rows;
		y = rows.header(ctx, "Sound", y);
		y = rows.toggle(ctx, "Sound effects", "Play clicks and menu sounds.", Settings.sounds(), Settings::toggleSounds, y);
		y = rows.slider(ctx, this.volumeTrack, "Volume", "How loud the menu sounds play.", Settings.uiVolume() + "%",
				Settings.uiVolumeFraction(), this.draggingVolume, y);

		y = rows.header(ctx, "Downloads", y);
		y = rows.toggle(ctx, "Confirm overwrite", "Ask before replacing a schematic with the same name.",
				Settings.confirmOverwrite(), Settings::toggleConfirmOverwrite, y);
		Action change = Action.secondary("Change", this::openDownloadPicker);
		Action open = Action.secondary("Open", this::openDownloadFolder);
		String folder = Settings.downloadDirectory().toString();
		y = Settings.hasCustomDownloadDirectory()
				? rows.buttons(ctx, "Download folder", folder, y, change, open, Action.secondary("Reset", Settings::clearDownloadDirectory))
				: rows.buttons(ctx, "Download folder", folder, y, change, open);

		y = rows.header(ctx, "Viewing", y);
		y = rows.buttons(ctx, "Grid density", "Compact fits more posts, Large shows bigger pictures.", y,
				Action.cycling(Settings.gridDensityLabel(), DENSITY_WIDEST, this.screen.browsePage::cycleGridDensity));
		return rows.slider(ctx, this.fovTrack, "Preview FOV", "Lower zooms in, higher shows more.",
				Integer.toString(Settings.previewFov()), (Settings.previewFov() - 30) / 80.0F, this.draggingFov, y);
	}

	private int appearance(GuiGraphicsExtractor ctx, int y)
	{
		SettingsRows rows = this.rows;
		y = rows.header(ctx, "Nametags", y);
		y = rows.toggle(ctx, "Nametag icon", "Show the mod icon on your nametag.", Settings.modTags(), Settings::toggleModTags, y);
		y = rows.toggle(ctx, "Own nametag", "See your nametag and cosmetics in third person.", Settings.ownNametag(),
				Settings::toggleOwnNametag, y);

		y = rows.header(ctx, "Styled names", y);
		y = rows.toggle(ctx, "In chat", "Draw mod users' colours and tags in chat.", Settings.chatNames(),
				Settings::toggleChatNames, y);
		y = rows.toggle(ctx, "In the tab list", "Draw mod users' colours and tags in the player list.", Settings.tabNames(),
				Settings::toggleTabNames, y);

		return this.discord(ctx, y);
	}

	private int notifications(GuiGraphicsExtractor ctx, int y)
	{
		SettingsRows rows = this.rows;
		y = rows.header(ctx, "Toasts", y);
		y = rows.toggle(ctx, "Show toasts", "Slide-in cards for downloads, follows and more.", Settings.toasts(),
				Settings::toggleToasts, y);

		y = rows.header(ctx, "Alerts", y);
		y = rows.toggle(ctx, "New posts", "When a creator you follow posts a build.", Settings.notifications(),
				Settings::toggleNotifications, y);

		if (UploaderAccess.unlocked())
		{
			y = rows.toggle(ctx, "Follows & likes", "When someone follows you or likes one of your posts.",
					Settings.creatorAlerts(), Settings::toggleCreatorAlerts, y);
		}

		y = rows.flag(ctx, "Rate prompt", "Ask me to rate after downloading a build.", SettingsKeys.RATE_PROMPT, y);
		y = rows.flag(ctx, "Daily reward", "When your daily Shards are ready to claim.", SettingsKeys.DAILY_REMINDER, y);
		return rows.flag(ctx, "Update notices", "When a new version of the mod is out.", SettingsKeys.UPDATE_NOTICES, y);
	}

	// Every row here follows a server switch, so the section can end up with nothing to show
	private int discord(GuiGraphicsExtractor ctx, int y)
	{
		boolean presence = RemoteContent.feature("richPresence");
		boolean openInGame = RemoteContent.feature("openInGame");

		if (!presence && !openInGame)
		{
			return y;
		}

		SettingsRows rows = this.rows;
		y = rows.header(ctx, "Discord", y);

		if (presence)
		{
			y = rows.flag(ctx, "Discord presence", "Show off that you're using the Schematic Index.",
					SettingsKeys.DISCORD_PRESENCE, y);

			if (Settings.flag(SettingsKeys.DISCORD_PRESENCE, true))
			{
				y = rows.flag(ctx, "Show server", "Display the name of the server you're playing on (from your server list), or singleplayer on a local world.",
						SettingsKeys.DISCORD_PRESENCE_SERVER, y);
			}
		}

		if (openInGame)
		{
			y = rows.flag(ctx, "Open in game", "Let schematicindex.com open builds here. Only that site can.",
					SettingsKeys.WEB_OPEN, y);
		}

		return y;
	}

	private int controls(GuiGraphicsExtractor ctx, int y)
	{
		y = this.rows.header(ctx, "Keybinds", y);
		return this.rows.note(ctx, "Keybinds", "Change them in Options > Controls > Schematic Index.", y);
	}

	private void select(int tab)
	{
		if (tab == selected())
		{
			return;
		}

		Settings.setText(SettingsKeys.SETTINGS_TAB, IDS[tab]);
		this.screen.scroll = 0.0F;
		this.draggingVolume = false;
		this.draggingFov = false;
		Theme.tab();
	}

	private static int selected()
	{
		String id = Settings.text(SettingsKeys.SETTINGS_TAB, IDS[PROFILE]);

		if (RETIRED_DISCORD_ID.equals(id))
		{
			return APPEARANCE;
		}

		for (int i = 0; i < IDS.length; i++)
		{
			if (IDS[i].equals(id))
			{
				return i;
			}
		}

		return PROFILE;
	}

	private void openDownloadPicker()
	{
		new Thread(() -> {
			String result;

			try
			{
				result = TinyFileDialogs.tinyfd_selectFolderDialog(
						"Choose a download folder", Settings.downloadDirectory().toString());
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Folder picker failed", e);
				return;
			}

			if (result == null || result.isBlank())
			{
				return;
			}

			Minecraft.getInstance().execute(() -> Settings.setDownloadDirectory(Path.of(result.trim())));
		}, "schematicindex-folder-picker").start();
	}

	private void openDownloadFolder()
	{
		try
		{
			Path directory = Settings.downloadDirectory();
			Files.createDirectories(directory);
			Util.getPlatform().openPath(directory);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not open the download folder", e);
		}
	}

	private void setVolumeFromMouse(double mouseX)
	{
		int trackX = this.volumeTrack.x;
		int trackW = this.volumeTrack.width;

		if (trackW > 0)
		{
			Settings.setUiVolume((int) Math.round((mouseX - trackX) / trackW * 100.0));
		}
	}

	private void setFovFromMouse(double mouseX)
	{
		int trackX = this.fovTrack.x;
		int trackW = this.fovTrack.width;

		if (trackW <= 0)
		{
			return;
		}

		int before = Settings.previewFov();
		Settings.setPreviewFov((int) Math.round(30 + (mouseX - trackX) / trackW * 80.0));

		if (Settings.previewFov() != before)
		{
			Usage.once("preview_fov_changed");
		}
	}
}

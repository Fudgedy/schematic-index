package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Bookmarks;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.catalogue.DiscordLink;
import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.Featured;
import com.fudgedy.schematicindex.catalogue.Follows;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Premium;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.export.ExportActions;
import com.fudgedy.schematicindex.gui.detail.ClaimModal;
import com.fudgedy.schematicindex.gui.detail.DetailView;
import com.fudgedy.schematicindex.gui.detail.OverwriteConfirm;
import com.fudgedy.schematicindex.gui.detail.RatingNudge;
import com.fudgedy.schematicindex.gui.detail.ReportModal;
import com.fudgedy.schematicindex.gui.modal.CoachMark;
import com.fudgedy.schematicindex.gui.modal.CollectionOptionsModal;
import com.fudgedy.schematicindex.gui.modal.DeleteCollectionModal;
import com.fudgedy.schematicindex.gui.modal.DesignerWarnModal;
import com.fudgedy.schematicindex.gui.modal.DownloadAllModal;
import com.fudgedy.schematicindex.gui.modal.DuplicateModal;
import com.fudgedy.schematicindex.gui.modal.EditPostModal;
import com.fudgedy.schematicindex.gui.modal.ErrorModal;
import com.fudgedy.schematicindex.gui.modal.HelpMenu;
import com.fudgedy.schematicindex.gui.modal.LinkModal;
import com.fudgedy.schematicindex.gui.modal.LinkRequestModal;
import com.fudgedy.schematicindex.gui.modal.LoadCodeModal;
import com.fudgedy.schematicindex.gui.modal.LoadGuideModal;
import com.fudgedy.schematicindex.gui.modal.NameInputModal;
import com.fudgedy.schematicindex.gui.modal.PhotoMode;
import com.fudgedy.schematicindex.gui.modal.PostOptionsModal;
import com.fudgedy.schematicindex.gui.modal.PremiumBuyModal;
import com.fudgedy.schematicindex.gui.modal.RateModal;
import com.fudgedy.schematicindex.gui.modal.RenameCollectionModal;
import com.fudgedy.schematicindex.gui.modal.ShardPanel;
import com.fudgedy.schematicindex.gui.modal.ShardWelcomeModal;
import com.fudgedy.schematicindex.gui.modal.TermsModal;
import com.fudgedy.schematicindex.gui.modal.TutorialModal;
import com.fudgedy.schematicindex.gui.modal.UnpublishModal;
import com.fudgedy.schematicindex.gui.modal.UpdateRequiredModal;
import com.fudgedy.schematicindex.gui.modal.WhatsNewModal;
import com.fudgedy.schematicindex.gui.page.BrowsePage;
import com.fudgedy.schematicindex.gui.page.CosmeticsPage;
import com.fudgedy.schematicindex.gui.page.DashboardPage;
import com.fudgedy.schematicindex.gui.page.SettingsPage;
import com.fudgedy.schematicindex.gui.page.UploadPage;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.NotificationPanel;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.gui.widget.UpdatePill;
import com.fudgedy.schematicindex.staff.StaffHook;
import com.fudgedy.schematicindex.staff.StaffScreen;
import com.fudgedy.schematicindex.update.UpdateGate;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class IndexScreen extends Screen
{
	// Pinned while this screen is open: GUI Scale Auto picks up to 9x on a 4K window, shrinking the
	// logical resolution until the fixed-pixel chrome cramps everything
	private static final int FIXED_SCALE = 2;
	private double restoreScale = -1.0;

	public static final int OUTER_MARGIN = 8;
	private static final int CONTENT_MAX_WIDTH = 720;
	public static final int TOP_BAR_HEIGHT = 32;
	public static final int RAIL_WIDTH = 34;
	private static final int RAIL_ITEM_HEIGHT = 34;
	private static final int PARTNER_RAIL_WIDTH = 58;
	
	private static final float RAIL_HOVER_GROW = 0.14F;
	private static final int RAIL_ITEM_GAP = 8;
	private static final int RAIL_LINK_HEIGHT = 26;
	public static final int GUTTER = 6;
	public static final int CAPTION_HEIGHT = 28;
	public static final int CHIP_HEIGHT = 14;
	public static final int CHIP_GAP = 8;
	public static final int SCROLL_STEP = 24;
	public static final int HEART_SIZE = 9;
	public static final int FIELD_HEIGHT = 16;
	// One spacing rhythm for the settings page: rows share a trailing gap, sections a larger lead-in
	public static final int SETTINGS_ROW_GAP = 12;
	private static final int SETTINGS_SECTION_GAP = 10;
	private static final int SETTINGS_HEADER_GAP = 8;
	public static final int SETTINGS_HINT_GAP = 5;
	public static final int DESC_FIELD_HEIGHT = 64;
	public static final int DESC_CHAR_LIMIT = 500;

	private static final Map<String, Long> LIKE_POPS = new HashMap<>();

	// Polled by tickDownloads rather than by the button render, so completion still fires with the
	// detail modal closed
	private final Map<String, Download.State> downloadStates = new HashMap<>();

	public enum Page
	{
		BROWSE("Browse"),
		PREMIUM("Premium"),
		SAVED("Saved"),
		MAPART("Mapart"),
		UPLOAD("Upload"),
		COSMETICS("Cosmetics"),
		SETTINGS("Settings"),
		STAFF("Staff");

		private final String label;

		Page(String label)
		{
			this.label = label;
		}

		ItemStack icon()
		{
			return switch (this)
			{
				case BROWSE -> new ItemStack(Items.SPYGLASS);
				case PREMIUM -> new ItemStack(Items.NETHERITE_INGOT);
				case SAVED -> new ItemStack(Items.ENDER_CHEST);
				case MAPART -> new ItemStack(Items.FILLED_MAP);
				case UPLOAD -> new ItemStack(Items.WRITABLE_BOOK);
				case COSMETICS -> new ItemStack(Items.NAME_TAG);
				case SETTINGS -> new ItemStack(Items.ANVIL);
				case STAFF -> new ItemStack(Items.COMMAND_BLOCK);
			};
		}
	}

	private final @Nullable Screen parent;
	public Page page = Page.BROWSE;
	// Static, so a menu reopen lands on the same view and a Minecraft restart resets it
	private static Page sessionPage = Page.BROWSE;
	// Silent identify runs once per launch; the manual Verify pill still retries after a failure
	private static boolean autoVerifyTried;
	private long catalogueRevision = -1;

	// Keyed by width, maxLines and text; cleared on init/resize
	private final Map<String, List<String>> wrapCache = new HashMap<>();
	private static final int WRAP_CACHE_MAX = 512;

	public int contentX;
	public int contentWidth;
	public int chipRowHeight = 26;
	public int gridTop;
	public int gridBottom;
	public int columns = 4;
	public int cardWidth = 120;
	public int cardHeight = 96;

	public float scroll;
	public float maxScroll;

	// Each scrollbar drawn this frame records its geometry and channel so a click can grab the thumb
	private static final int SCROLLBAR_NONE = 0;
	public static final int SCROLLBAR_MAIN = 1; // this.scroll / this.maxScroll
	public static final int SCROLLBAR_DASH = 2; // dashScroll / dashMaxScroll
	public static final int SCROLLBAR_TOS = 3;  // TermsModal's own scroll
	private int scrollbarChannel = SCROLLBAR_NONE;
	private int scrollbarX, scrollbarWidth, scrollbarThumbY, scrollbarThumbHeight;
	private int scrollbarTrackTop, scrollbarTrackHeight;
	private boolean draggingScrollbar;
	private int dragScrollChannel = SCROLLBAR_NONE;
	private float scrollbarGrabOffset;
	
	// -1 until an arrow key is pressed, so the focus ring only shows once it is used
	public int modalFocus = -1;

	public final Rect closeButton = new Rect();
	// Far-right shard balance pill; opens the daily-login and quests panel
	private final Rect shardButton = new Rect();
	private final Rect backToTopButton = new Rect();
	// Swapped whole from the folder scan, which runs off-thread; the newest scan wins
	private volatile Set<String> downloadedNames = Set.of();
	private int downloadScan;
	// isDownloadStale hashes the local file, far too costly per frame, so the answer is kept for the
	// session and dropped with downloadedNames when the folder is rescanned
	private final Map<String, Boolean> staleCache = new HashMap<>();
	private final MapartTab mapartTab = new MapartTab(this);
	public final List<Rect> railRects = new ArrayList<>();
	private int railLinksLaid;
	// Reuse pool for the premium tab's cards; premiumCardEntries tracks which are live this frame
	private final List<Rect> premiumCardRects = new ArrayList<>();
	private final List<Premium.Entry> premiumCardEntries = new ArrayList<>();

	// A reinstalled or second-PC client folds server-side like and follow state back in once per launch
	private static boolean accountSyncDone;

	public static final long MY_STATS_STALE_MS = 45_000L;
	private static long lastPremiumPull;
	private static long lastCosmeticsPull;
	public float dashScroll;
	public float dashMaxScroll;

	// Glyph x offsets by title text, so drawGradientTitle skips a per-character font.width() every
	// frame; cleared on resize to pick up a font change
	private final Map<String, int[]> titleLayoutCache = new HashMap<>();
	public static final int CARD_TEXT_CACHE_MAX = 512;
	// Download.safeName + toLowerCase per title, since isDownloaded runs per visible card per frame
	private final Map<String, String> downloadedKeyCache = new HashMap<>();
	// Both are written by the screen-level download bookkeeping and read by the detail card
	public boolean detailDownloadLocked;
	public String status = "";

	private @Nullable RemoteContent.Announcement bannerAnnouncement;
	private @Nullable String bannerId;
	private long bannerAnimStart;
	private boolean bannerEntering;
	private boolean bannerActive;
	private final Rect bannerClose = new Rect();
	// Static, so reopening the menu resumes the same schedule rather than polling again immediately
	private static long lastContentPoll;
	private static long lastShardPoll;
	// A toast action outlives the screen that saw it pushed, so it asks whichever screen is live to open the panel
	private static boolean shardPanelRequested;
	private int lastPartnerCount = -1;
	private final List<Rect> railLinkRects = new ArrayList<>();
	private final List<Rect> partnerRects = new ArrayList<>();
	// The *Rects lists above only ever hold instances from these pools
	private final List<Rect> railLinkPool = new ArrayList<>();
	private final List<Rect> partnerPool = new ArrayList<>();

	private final List<Overlay> overlays = new ArrayList<>();
	private static volatile @Nullable String pendingOpenPostId;

	public final BatchDownload batchDownload = new BatchDownload(this);
	public final CardMenu cardMenu = new CardMenu(this);
	// Each owns its own flag, rects and text; this screen only routes render, clicks and keys to them
	public final ErrorModal errorModal = new ErrorModal(this);
	public final TutorialModal tutorialModal = new TutorialModal(this);
	public final UpdatePill updatePill = new UpdatePill(this);
	public final WhatsNewModal whatsNewModal = this.overlay(new WhatsNewModal(this));
	public final CoachMark coachMark = this.overlay(new CoachMark(this));
	public final LoadGuideModal loadGuideModal = this.overlay(new LoadGuideModal(this));
	public final HelpMenu helpMenu = this.overlay(new HelpMenu(this));
	public final UpdateRequiredModal updateRequiredModal = this.overlay(new UpdateRequiredModal(this));
	public final TermsModal termsModal = new TermsModal(this);
	public final ShardWelcomeModal shardWelcomeModal = new ShardWelcomeModal(this);
	public final NameInputModal nameInputModal = new NameInputModal(this);
	public final LoadCodeModal loadCodeModal = new LoadCodeModal(this);
	public final DuplicateModal duplicateModal = new DuplicateModal(this);
	public final CollectionOptionsModal collectionOptionsModal = new CollectionOptionsModal(this);
	public final RenameCollectionModal renameCollectionModal = new RenameCollectionModal(this);
	public final DeleteCollectionModal deleteCollectionModal = new DeleteCollectionModal(this);
	public final PostOptionsModal postOptionsModal = new PostOptionsModal(this);
	public final UnpublishModal unpublishModal = new UnpublishModal(this);
	public final DesignerWarnModal designerWarnModal = new DesignerWarnModal(this);
	public final EditPostModal editPostModal = new EditPostModal(this);
	public final OverwriteConfirm overwriteConfirm = new OverwriteConfirm(this);
	public final DownloadAllModal downloadAllModal = new DownloadAllModal(this);
	public final ReportModal reportModal = new ReportModal(this);
	public final ClaimModal claimModal = new ClaimModal(this);
	public final LinkModal linkModal = this.overlay(new LinkModal(this));
	public final LinkRequestModal linkRequestModal = this.overlay(new LinkRequestModal(this));
	public final PhotoMode photoMode = this.overlay(new PhotoMode(this));
	public final RateModal rateModal = this.overlay(new RateModal(this));
	public final PremiumBuyModal premiumBuyModal = new PremiumBuyModal(this);
	public final ShardPanel shardPanel = new ShardPanel(this);
	public final DetailView detailView = new DetailView(this);
	private final CosmeticsPage cosmeticsPage = new CosmeticsPage(this);
	private final SettingsPage settingsPage = new SettingsPage(this);
	public final UploadPage uploadPage = new UploadPage(this);
	public final DashboardPage dashboardPage = new DashboardPage(this);
	public final BrowsePage browsePage = new BrowsePage(this);
	private final NotificationPanel notifications = new NotificationPanel(this);
	// Null in the community jar; the staff jar attaches its dashboard and moderation controls here
	public final @Nullable StaffScreen staff = StaffHook.attach(this);

	public IndexScreen(@Nullable Screen parent)
	{
		super(Component.literal("The Schematic Index"));
		this.parent = parent;
	}

	// Screen keeps the font protected, and the modal package draws with it
	public Font font()
	{
		return this.font;
	}

	@Override
	protected void init()
	{
		// Runs before any layout, and again on every resize, adjusting this.width/height in place
		this.schematicindex$applyStableGuiScale();

		ImageStore.discover();
		SchematicPreview.discover();
		Catalogue.ensureLoaded();
		this.reportOpen();
		DiscordLink.refreshOnOpen();

		// Silent identify on first open; gated on terms, since verifying before consent would fail and burn
		// this one-shot flag for the session
		if (!autoVerifyTried && !McAuth.verified() && Settings.termsAccepted())
		{
			autoVerifyTried = true;
			McAuth.ensureVerified(this::maybeShowShardWelcome);
		}

		// These memos are width and font dependent, so a resize must invalidate them
		this.wrapCache.clear();
		this.titleLayoutCache.clear();
		this.browsePage.grid.clearTextCache();

		this.page = sessionPage == Page.STAFF && (this.staff == null || !this.staff.available())
				? Page.BROWSE : sessionPage;
		this.browsePage.nav.restoreSession();

		this.layoutContent();

		int searchWidth = this.searchWidth();
		int searchX = this.contentX + (this.contentWidth - searchWidth) / 2;
		int searchY = (TOP_BAR_HEIGHT - 16) / 2;

		this.browsePage.search.build(searchX, searchY, searchWidth);

		int closeMenuW = this.font.width(Theme.bold("Close Menu")) + 14;
		this.closeButton.set(this.contentX + this.contentWidth - closeMenuW, searchY, closeMenuW, 16);
		this.notifications.bellButton().set(this.closeButton.x - 6 - 16, searchY, 16, 16);

		this.layoutRail();
		UpdateGate.onIndexOpen(this);

		this.uploadPage.buildFields();
		this.cosmeticsPage.buildFields();

		if (this.staff != null)
		{
			this.staff.buildFields();
		}
		this.uploadPage.restoreDraft();
		this.refreshDownloadedNames();

		BrowsePage.captureNewSince();

		this.uploadPage.limitFields();

		this.browsePage.layoutChips();
		Featured.refreshIfStale();

		if (!Settings.termsAccepted())
		{
			this.termsModal.open();
		}
		else
		{
			this.tutorialModal.maybeStart();
			this.maybeSyncAccount();
		}

		String queuedPost = pendingOpenPostId;

		if (queuedPost != null && Settings.termsAccepted())
		{
			pendingOpenPostId = null;
			this.openPostById(queuedPost);
		}

		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.gridBottom = this.height - OUTER_MARGIN;
		this.browsePage.refilter();
		this.browsePage.nav.restoreScroll();
	}

	// The Staff page exists only in the staff jar and only for a staff account; every rail pass
	// walks this list rather than Page.values() so the community build never shows the slot
	private Page[] railPages()
	{
		Page[] all = Page.values();
		boolean staff = this.staff != null && this.staff.available();
		Page[] pages = new Page[all.length];
		int n = 0;

		for (Page page : all)
		{
			if (page != Page.STAFF || staff)
			{
				pages[n++] = page;
			}
		}

		return Arrays.copyOf(pages, n);
	}

	// Verification can finish after init, so the rail is re-laid whenever the page count changes
	private void layoutRail()
	{
		this.railRects.clear();
		this.railLinksLaid = RemoteContent.links().size();

		int railCount = this.railPages().length;
		// The remote link icons sit at the bottom of the rail; the pages get whatever is left above them
		int linksHeight = this.railLinksLaid == 0 ? 0 : this.railLinksLaid * RAIL_LINK_HEIGHT + OUTER_MARGIN;
		int bandTop = TOP_BAR_HEIGHT + 10;
		int bandHeight = Math.max(railCount, this.height - bandTop - OUTER_MARGIN - linksHeight);
		int itemHeight = Math.min(RAIL_ITEM_HEIGHT, bandHeight / railCount);
		int gap = railCount <= 1 ? 0
				: Math.max(0, Math.min(RAIL_ITEM_GAP, (bandHeight - railCount * itemHeight) / (railCount - 1)));
		int groupHeight = railCount * itemHeight + (railCount - 1) * gap;
		int railTop = bandTop + (bandHeight - groupHeight) / 2;

		for (int i = 0; i < railCount; i++)
		{
			Rect rect = new Rect();
			rect.set(0, railTop + i * (itemHeight + gap), RAIL_WIDTH, itemHeight);
			this.railRects.add(rect);
		}
	}

	private static boolean stale(long lastPull)
	{
		return lastPull == 0L || Util.getMillis() - lastPull > MY_STATS_STALE_MS;
	}

	// Screen.addWidget and removeWidget are protected, and the page and modal packages build boxes of their own
	public <T extends GuiEventListener & Renderable & NarratableEntry> void addModalWidget(T widget)
	{
		this.addWidget(widget);
	}

	public void removeModalWidget(GuiEventListener widget)
	{
		this.removeWidget(widget);
	}

	public int searchWidth()
	{
		return Math.max(80, Math.min(200, this.contentWidth - 260));
	}

	public static int imageHeight(int cardWidth)
	{
		return Math.round(cardWidth * 9.0F / 16.0F);
	}

	public static boolean isSaved(SchematicEntry entry)
	{
		return Bookmarks.isSaved(entry.id());
	}

	public static void toggleSaved(SchematicEntry entry)
	{
		Theme.click(Bookmarks.toggleSaved(entry.id()) ? 1.3F : 0.9F);
	}

	// The server count already holds a like it reported; only a local like it has not seen adds one
	public static int likesOf(SchematicEntry entry)
	{
		return entry.likes() + (!entry.liked() && Bookmarks.isLiked(entry.id()) ? 1 : 0);
	}

	public static boolean isLikedBy(SchematicEntry entry)
	{
		return entry.liked() || Bookmarks.isLiked(entry.id());
	}

	private static int imageSlot(SchematicEntry entry, int offset)
	{
		return entry.imageStart() + offset;
	}

	public @Nullable Identifier imageTexture(SchematicEntry entry, int index)
	{
		List<String> urls = entry.imageUrls();

		if (!urls.isEmpty())
		{
			return ImageStore.texture(urls.get(Math.floorMod(index, urls.size())));
		}

		return ImageStore.texture(imageSlot(entry, index));
	}

	// Null when the post has no addressable image URLs. Follows imageTexture's index so Copy PNG and
	// Copy link hand out the picture on screen, at full upload resolution where the server has it
	public @Nullable String currentImageRef(SchematicEntry entry)
	{
		List<String> urls = entry.imageUrls();

		if (urls.isEmpty())
		{
			return null;
		}

		int index = Math.floorMod(this.detailView.image(), urls.size());
		List<String> originals = entry.originalUrls();

		if (index < originals.size() && !originals.get(index).isBlank())
		{
			return originals.get(index);
		}

		return urls.get(index);
	}

	public @Nullable Identifier thumbnailTexture(SchematicEntry entry)
	{
		if (entry.thumbnailUrl() != null && !entry.thumbnailUrl().isBlank())
		{
			return ImageStore.thumbnail(entry.thumbnailUrl());
		}

		List<String> urls = entry.imageUrls();

		if (!urls.isEmpty())
		{
			return ImageStore.thumbnail(urls.get(0));
		}

		return ImageStore.thumbnail(imageSlot(entry, 0));
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float partialTick)
	{
		ctx.fill(0, 0, this.width, this.height, Theme.BACKDROP);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float partialTick)
	{
		ImageStore.uploadPending();
		SchematicPreview.uploadPending();

		this.detailView.tickSpectator();
		this.photoMode.tickSpectator();

		// Whichever scrollbar draws below re-records its geometry
		this.scrollbarChannel = SCROLLBAR_NONE;

		if (Catalogue.revision() != this.catalogueRevision)
		{
			this.catalogueRevision = Catalogue.revision();
			this.browsePage.refilter();
		}

		this.browsePage.refilterIfDue();

		if (RemoteContent.partners().size() != this.lastPartnerCount)
		{
			this.lastPartnerCount = RemoteContent.partners().size();
			this.relayout();
		}

		this.errorModal.takePending();

		// Ahead of any page or modal render, so a download finishing with the detail modal closed
		// still gets its feedback, filter refresh and cleanup
		this.tickDownloads();
		this.batchDownload.tick();

		this.renderBackground(ctx, mouseX, mouseY, partialTick);

		boolean modalOpen = this.detailView.isOpen();
		boolean overlayOpen = this.blockingOverlayOpen();
		boolean hoverBlocked = this.pageBlocked();
		int hoverX = hoverBlocked ? -1 : mouseX;
		int hoverY = hoverBlocked ? -1 : mouseY;

		if (this.page == Page.UPLOAD)
		{
			this.uploadPage.render(ctx, hoverX, hoverY, partialTick);
		}
		else if (this.page == Page.PREMIUM)
		{
			this.renderPremium(ctx, hoverX, hoverY);
		}
		else if (this.page == Page.SETTINGS)
		{
			this.settingsPage.render(ctx, hoverX, hoverY);
		}
		else if (this.page == Page.COSMETICS)
		{
			this.cosmeticsPage.render(ctx, hoverX, hoverY, partialTick);
		}
		else if (this.page == Page.MAPART)
		{
			this.mapartTab.render(ctx, hoverX, hoverY);
		}
		else if (this.page == Page.STAFF && this.staff != null)
		{
			this.staff.render(ctx, hoverX, hoverY, partialTick);
		}
		else
		{
			this.browsePage.render(ctx, hoverX, hoverY);
			this.renderPartnerRail(ctx, hoverX, hoverY);
		}

		this.renderRail(ctx, hoverX, hoverY);
		this.renderTopBar(ctx, hoverX, hoverY);
		this.renderBackToTop(ctx, hoverX, hoverY, overlayOpen);
		this.browsePage.dropdowns.renderLists(ctx, mouseX, mouseY, overlayOpen);

		if (this.gridPage())
		{
			this.cardMenu.render(ctx, mouseX, mouseY);
		}

		long nowMs = System.currentTimeMillis();

		// Announcements and partner rails change on the order of days, not seconds
		if (nowMs - this.lastContentPoll > 120000L)
		{
			this.lastContentPoll = nowMs;
			RemoteContent.pollAsync();
		}

		this.notifications.pollIfDue(nowMs);

		// A quest finished while browsing is announced without the panel open
		if (McAuth.verified() && nowMs - this.lastShardPoll > 60000L)
		{
			this.lastShardPoll = nowMs;
			Shards.refresh();
		}

		if (shardPanelRequested)
		{
			shardPanelRequested = false;
			this.openShardPanel();
		}

		this.detailView.flushPendingRate(false);

		this.updateBanner();
		this.renderBanner(ctx, mouseX, mouseY);

		this.browsePage.search.render(ctx, mouseX, mouseY, partialTick, overlayOpen);

		this.notifications.render(ctx, mouseX, mouseY, modalOpen);

		if (modalOpen)
		{
			boolean covered = this.openOverlay() != null;
			this.detailView.render(ctx, covered ? -1 : mouseX, covered ? -1 : mouseY);

			if (this.overwriteConfirm.isOpen())
			{
				this.overwriteConfirm.render(ctx, mouseX, mouseY);
			}
			else if (this.reportModal.isPickerOpen())
			{
				this.reportModal.renderPicker(ctx, mouseX, mouseY);
			}
			else if (this.reportModal.isContextOpen())
			{
				this.reportModal.renderContext(ctx, mouseX, mouseY);
			}
			else if (this.claimModal.isOpen())
			{
				this.claimModal.render(ctx, mouseX, mouseY);
			}
		}
		else if (this.overwriteConfirm.isOpen())
		{
			// Reached from the card menu, so it must show without the detail card behind it
			this.overwriteConfirm.render(ctx, mouseX, mouseY);
		}

		this.premiumBuyModal.render(ctx, mouseX, mouseY);
		this.shardPanel.render(ctx, mouseX, mouseY);
		this.designerWarnModal.render(ctx, mouseX, mouseY);

		Toasts.render(ctx);

		this.tutorialModal.render(ctx, mouseX, mouseY);
		this.shardWelcomeModal.render(ctx, mouseX, mouseY);

		for (Overlay overlay : this.overlays)
		{
			if (overlay.isOpen())
			{
				overlay.render(ctx, mouseX, mouseY);
			}
		}

		this.termsModal.render(ctx, mouseX, mouseY);
		this.errorModal.render(ctx, mouseX, mouseY);
		this.nameInputModal.render(ctx, mouseX, mouseY);
		this.loadCodeModal.render(ctx, mouseX, mouseY);
		this.duplicateModal.render(ctx, mouseX, mouseY);
		this.collectionOptionsModal.render(ctx, mouseX, mouseY);
		this.renameCollectionModal.render(ctx, mouseX, mouseY);
		this.deleteCollectionModal.render(ctx, mouseX, mouseY);
		this.postOptionsModal.render(ctx, mouseX, mouseY);
		this.downloadAllModal.render(ctx, mouseX, mouseY);

		this.editPostModal.render(ctx, mouseX, mouseY, partialTick);
		this.unpublishModal.render(ctx, mouseX, mouseY);

		if (this.staff != null)
		{
			this.staff.renderModal(ctx, mouseX, mouseY);
		}

		Rect[] focusButtons = this.modalFocusButtons();

		if (focusButtons != null && this.modalFocus >= 0 && this.modalFocus < focusButtons.length)
		{
			Rect f = focusButtons[this.modalFocus];

			if (f.width > 0 && f.height > 0)
			{
				Theme.roundedOutline(ctx, f.x - 1, f.y - 1, f.width + 2, f.height + 2, Theme.RADIUS_PILL,
						Theme.ACCENT_BRIGHT);
			}
		}
	}

	// Primary first, or null when no such modal is open
	private Rect[] modalFocusButtons()
	{
		if (this.errorModal.isOpen())
		{
			return this.errorModal.focusButtons();
		}

		if (this.renameCollectionModal.isOpen())
		{
			return this.renameCollectionModal.focusButtons();
		}

		if (this.collectionOptionsModal.isOpen())
		{
			return this.collectionOptionsModal.focusButtons();
		}

		if (this.deleteCollectionModal.isOpen())
		{
			return this.deleteCollectionModal.focusButtons();
		}

		if (this.nameInputModal.isOpen())
		{
			return this.nameInputModal.focusButtons();
		}

		if (this.overwriteConfirm.isOpen())
		{
			return this.overwriteConfirm.focusButtons();
		}

		if (this.downloadAllModal.isOpen())
		{
			return this.downloadAllModal.focusButtons();
		}

		if (this.postOptionsModal.isOpen())
		{
			return this.postOptionsModal.focusButtons();
		}

		// Detail-scoped, so only focusable while its own detail card is open
		if (this.claimModal.isOpen() && this.detailView.isOpen())
		{
			return this.claimModal.focusButtons();
		}

		return null;
	}

	// What Enter activates with no explicit focus, so it must never be destructive: the
	// delete-collection modal lists Delete at index 0, so it defaults to Cancel at index 1
	private int safeModalFocus()
	{
		if (this.deleteCollectionModal.isOpen())
		{
			return 1;
		}

		return 0;
	}

	private void activateModalFocus()
	{
		Rect[] buttons = this.modalFocusButtons();

		if (buttons == null)
		{
			return;
		}

		int i = this.modalFocus < 0 || this.modalFocus >= buttons.length ? this.safeModalFocus() : this.modalFocus;

		if (this.errorModal.isOpen())
		{
			this.errorModal.activateFocus(buttons[i], i);
		}
		else if (this.renameCollectionModal.isOpen())
		{
			this.renameCollectionModal.activateFocus(buttons[i], i);
		}
		else if (this.collectionOptionsModal.isOpen())
		{
			this.collectionOptionsModal.activateFocus(buttons[i], i);
		}
		else if (this.deleteCollectionModal.isOpen())
		{
			this.deleteCollectionModal.activateFocus(buttons[i], i);
		}
		else if (this.nameInputModal.isOpen())
		{
			this.nameInputModal.activateFocus(buttons[i], i);
		}
		else if (this.overwriteConfirm.isOpen())
		{
			this.overwriteConfirm.activateFocus(buttons[i], i);
		}
		else if (this.downloadAllModal.isOpen())
		{
			this.downloadAllModal.activateFocus(buttons[i], i);
		}
		else if (this.postOptionsModal.isOpen())
		{
			this.postOptionsModal.activateFocus(buttons[i], i);
		}
		else if (this.claimModal.isOpen() && this.detailView.isOpen())
		{
			// Mirrors ClaimModal.mouseClicked's early return, so the keyboard path cannot close the
			// modal or fire a second submit while one is outstanding
			if (this.claimModal.isBusy())
			{
				return;
			}

			this.claimModal.activateFocus(buttons[i], i);
		}

		Theme.click(i == 0 ? 1.1F : 0.9F);
	}

	// Shared by the settings pill and the claim gates so every entry point reports failures alike
	public void startVerify(@Nullable Runnable onVerified)
	{
		McAuth.ensureVerified(() -> {
			if (!McAuth.verified())
			{
				this.showError(McAuth.failureCode());
				return;
			}

			if (onVerified != null)
			{
				onVerified.run();
			}
		});
	}

	// Every write the server keys off X-Session enters here, so an unverified user gets the handshake
	// first and the action once it succeeds; with no backend the action stays local and runs at once
	public void requireVerified(Runnable action)
	{
		if (McAuth.verified() || !Backend.configured())
		{
			action.run();
			return;
		}

		this.startVerify(action);
	}

	public void openEditPost(String id)
	{
		this.editPostModal.open(id);
	}

	public void showError(String code)
	{
		this.errorModal.open(code);
	}

	// Restores a reinstalled or second-PC client so its saved and followed overlays match the
	// account, not only what this machine touched. Silent on success; a null state no-ops
	private void maybeSyncAccount()
	{
		if (accountSyncDone)
		{
			return;
		}
		accountSyncDone = true;

		Thread worker = new Thread(() -> {
			Backend.AccountState state;

			try
			{
				state = Backend.myState();
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.debug("Account sync failed", e);
				return;
			}

			if (state == null)
			{
				return;
			}

			Minecraft.getInstance().execute(() -> {
				// Stars are skipped: ratings live server-side only, with no local store to merge into
				Bookmarks.mergeLikedFromServer(state.likedPostIds());
				Follows.mergeFromServer(state.followedPosters());
				this.browsePage.refilter();
			});
		}, "schematicindex-account-sync");
		worker.setDaemon(true);
		worker.start();
	}

	private void renderTopBar(GuiGraphics ctx, int mouseX, int mouseY)
	{
		ctx.fill(0, 0, this.width, TOP_BAR_HEIGHT, Theme.SURFACE);
		ctx.fill(0, TOP_BAR_HEIGHT - 1, this.width, TOP_BAR_HEIGHT, Theme.HAIRLINE);

		int searchWidth = this.searchWidth();
		int searchX = this.contentX + (this.contentWidth - searchWidth) / 2;
		int titleX = OUTER_MARGIN;
		int titleRoom = searchX - titleX - 8;

		if (titleRoom >= this.font.width(Theme.bold(TITLE_TEXT)) * 2)
		{
			this.drawGradientTitle(ctx, TITLE_TEXT, 0, titleX, 7, 2.0F);
		}
		else if (titleRoom >= this.font.width(TITLE_TEXT))
		{
			this.drawGradientTitle(ctx, TITLE_TEXT, 0, titleX, 12, 1.0F);
		}
		else if (titleRoom > 0)
		{
			this.drawGradientTitle(ctx, "Index", 14, titleX, 12, 1.0F);
		}

		if (this.gridPage())
		{
			int searchY = (TOP_BAR_HEIGHT - 16) / 2;
			boolean focused = this.browsePage.search.isFocused();
			Theme.roundedRect(ctx, searchX, searchY, searchWidth, 16, Theme.RADIUS_PILL,
					focused ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

			if (focused)
			{
				Theme.roundedOutline(ctx, searchX, searchY, searchWidth, 16, Theme.RADIUS_PILL, Theme.ACCENT);
			}
		}

		this.layoutTopRight((TOP_BAR_HEIGHT - 16) / 2);
		Buttons.pill(ctx, this.font, this.closeButton, "Close Menu", mouseX, mouseY, false);

		if (this.notifications.hasInbox())
		{
			this.notifications.renderBell(ctx, mouseX, mouseY);
		}

		UpdateGate.tick(this);
		this.updatePill.render(ctx, mouseX, mouseY);
		this.helpMenu.renderButton(ctx, mouseX, mouseY);

		this.renderShardLabel(ctx, mouseX, mouseY);
	}

	// Reserves the far-right corner for the shard pill when verified, then Close Menu, then the bell
	private void layoutTopRight(int searchY)
	{
		int rightEdge = this.contentX + this.contentWidth;

		if (McAuth.verified())
		{
			int shown = Math.max(McAuth.shards(), Shards.displayedBalance());
			int shardW = 24 + this.font.width(Theme.bold(Integer.toString(shown))) + 6;
			this.shardButton.set(rightEdge - shardW, searchY, shardW, 16);
			rightEdge = this.shardButton.x - 6;
		}
		else
		{
			this.shardButton.set(0, 0, 0, 0);
		}

		int closeMenuW = this.font.width(Theme.bold("Close Menu")) + 14;
		this.closeButton.set(rightEdge - closeMenuW, searchY, closeMenuW, 16);
		this.notifications.bellButton().set(this.closeButton.x - 6 - 16, searchY, 16, 16);
		this.helpMenu.layoutButton(this.notifications.bellButton(), this.notifications.hasInbox());
		this.updatePill.layout(this.helpMenu.button());
	}

	private void renderShardLabel(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (this.shardButton.width == 0)
		{
			return;
		}

		Rect r = this.shardButton;
		boolean hovered = r.contains(mouseX, mouseY);
		// The one pulse on screen, and only while today's reward waits; the panel's banner states any risk
		boolean waiting = !Shards.days().isEmpty() && !Shards.claimedToday();
		int idle = waiting ? Theme.mix(Theme.SURFACE_CARD, Theme.SHARD_TINT, Theme.pulse()) : Theme.SURFACE_CARD;
		Theme.roundedRect(ctx, r.x, r.y, r.width, r.height, Theme.RADIUS_PILL, hovered ? Theme.SURFACE_ELEVATED : idle);

		if (hovered)
		{
			Theme.roundedOutline(ctx, r.x, r.y, r.width, r.height, Theme.RADIUS_PILL, Theme.HAIRLINE_STRONG);
		}

		int trend = Shards.balanceTrend();
		Theme.itemScaled(ctx, new ItemStack(Items.AMETHYST_SHARD), r.x + 5, r.y + 3, 0.6F);
		Theme.text(ctx, this.font, Theme.bold(Integer.toString(Shards.displayedBalance())), r.x + 22, r.y + 4,
				trend > 0 ? Theme.SUCCESS : (trend < 0 ? Theme.SHARD_SPEND : Theme.SHARD_TEXT));
	}

	// Zero-sized until the account is verified
	public Rect shardPill()
	{
		return this.shardButton;
	}

	public void openShardPanel()
	{
		Theme.amethystBreak();
		this.shardPanel.open();
	}

	public static void requestShardPanel()
	{
		shardPanelRequested = true;
	}

	// Fired after a verify succeeds; the primer waits behind the ToS and tutorial so it lands last
	public void maybeShowShardWelcome()
	{
		if (McAuth.verified() && !this.termsModal.isOpen() && !this.tutorialModal.isOpen())
		{
			this.shardWelcomeModal.maybeShow();
		}
	}

	// font.width only, so no mapping-specific substring helper is needed
	public String trimToWidth(String text, int maxWidth)
	{
		if (maxWidth <= 0 || this.font.width(text) <= maxWidth)
		{
			return text;
		}

		String ellipsis = "...";
		int budget = maxWidth - this.font.width(ellipsis);

		if (budget <= 0)
		{
			return ellipsis;
		}

		StringBuilder out = new StringBuilder();

		for (int i = 0; i < text.length(); i++)
		{
			if (this.font.width(out.toString() + text.charAt(i)) > budget)
			{
				break;
			}

			out.append(text.charAt(i));
		}

		return out + ellipsis;
	}

	private static final String TITLE_TEXT = "The Schematic Index";
	private static final int[] TITLE_COLORS = {
			0xFFA1A1A1, 0xFFB2B2B2, 0xFFC3C3C3, 0,
			0xFFD4D4D4, 0xFFE5E5E5, 0xFFF6F6F6, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0,
			0xFF1B503B, 0xFF23664B, 0xFF2A7B5B, 0xFF34976F, 0xFF3DB283,
	};

	private void drawGradientTitle(GuiGraphics ctx, String text, int startIndex, int x, int y, float scale)
	{
		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x, (float) y);
		ctx.pose().scale(scale, scale);

		// Glyph advances depend only on the character, so the layout is stable until the font changes
		int[] offsets = this.titleLayoutCache.get(text);

		if (offsets == null)
		{
			offsets = new int[text.length()];
			int measure = 0;

			for (int i = 0; i < text.length(); i++)
			{
				offsets[i] = measure;
				measure += this.font.width("§l" + text.charAt(i));
			}

			this.titleLayoutCache.put(text, offsets);
		}

		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);

			if (c != ' ')
			{
				ctx.drawString(this.font, "§l" + c, offsets[i], 0, TITLE_COLORS[startIndex + i], false);
			}
		}

		ctx.pose().popMatrix();
	}

	public boolean gridPage()
	{
		return this.page == Page.BROWSE || this.page == Page.SAVED;
	}

	private boolean partnerReserve()
	{
		return this.gridPage() && !RemoteContent.partners().isEmpty();
	}

	private void layoutContent()
	{
		int rightRail = this.partnerReserve() ? PARTNER_RAIL_WIDTH : 0;
		int available = this.width - RAIL_WIDTH - rightRail - OUTER_MARGIN * 2;
		this.contentWidth = Math.min(available, CONTENT_MAX_WIDTH);
		this.contentX = RAIL_WIDTH + OUTER_MARGIN + (available - this.contentWidth) / 2;

		this.browsePage.layoutGrid();
	}

	private void relayout()
	{
		this.layoutContent();

		int searchW = this.searchWidth();
		int searchX = this.contentX + (this.contentWidth - searchW) / 2;
		int searchY = (TOP_BAR_HEIGHT - 16) / 2;

		this.browsePage.search.reposition(searchX, searchW);

		int closeMenuW = this.font.width(Theme.bold("Close Menu")) + 14;
		this.closeButton.set(this.contentX + this.contentWidth - closeMenuW, searchY, closeMenuW, 16);
		this.notifications.bellButton().set(this.closeButton.x - 6 - 16, searchY, 16, 16);
		this.browsePage.layoutChips();
		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.browsePage.refilter();
	}

	private void renderRail(GuiGraphics ctx, int mouseX, int mouseY)
	{
		ctx.fill(0, TOP_BAR_HEIGHT, RAIL_WIDTH, this.height, Theme.SURFACE);
		ctx.fill(RAIL_WIDTH - 1, TOP_BAR_HEIGHT, RAIL_WIDTH, this.height, Theme.HAIRLINE);

		Page[] pages = this.railPages();

		if (pages.length != this.railRects.size() || RemoteContent.links().size() != this.railLinksLaid)
		{
			this.layoutRail();
		}

		for (int i = 0; i < pages.length && i < this.railRects.size(); i++)
		{
			Rect rect = this.railRects.get(i);
			boolean active = pages[i] == this.page;
			boolean hovered = rect.contains(mouseX, mouseY);

			if (active || hovered)
			{
				ctx.fill(rect.x, rect.y, rect.x + rect.width - 1, rect.y + rect.height, Theme.SURFACE_ELEVATED);
			}

			if (active && pages[i] == Page.PREMIUM)
			{
				ctx.fillGradient(0, rect.y, 2, rect.y + rect.height, 0xFFF0EB6E, 0xFFFB8800);
			}
			else if (active)
			{
				ctx.fill(0, rect.y, 2, rect.y + rect.height, Theme.ACCENT);
			}

			int iconX = rect.x + (RAIL_WIDTH - 17) / 2;
			int iconY = rect.y + (rect.height - 16) / 2;

			float hover = Theme.buttonHover(rect, hovered);
			float scale = Theme.popScale(rect, 1.0F + RAIL_HOVER_GROW * hover);
			Theme.pushScale(ctx, iconX - 2, iconY - 2, 20, 20, scale);

			Theme.roundedRect(ctx, iconX - 2, iconY - 2, 20, 20, Theme.RADIUS_PILL,
					active ? Theme.RAIL_TILE_ACTIVE : Theme.RAIL_TILE);
			ctx.renderItem(pages[i].icon(), iconX, iconY);
			Theme.pop(ctx);

			if (hovered && !active)
			{
				String label = pages[i].label;
				int width = this.font.width(label) + 8;
				Theme.roundedRect(ctx, RAIL_WIDTH + 2, rect.y + 8, width, 12, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);

				if (pages[i] == Page.PREMIUM)
				{
					Theme.goldGradientText(ctx, this.font, label, RAIL_WIDTH + 6, rect.y + 10, false);
				}
				else
				{
					Theme.text(ctx, this.font, label, RAIL_WIDTH + 6, rect.y + 10, Theme.TEXT);
				}
			}
		}

		this.renderRailLinks(ctx, mouseX, mouseY);
	}

	private void renderRailLinks(GuiGraphics ctx, int mouseX, int mouseY)
	{
		List<RemoteContent.Link> links = RemoteContent.links();
		this.railLinkRects.clear();

		if (links.isEmpty())
		{
			return;
		}

		int item = RAIL_LINK_HEIGHT;
		int size = 18;
		int y = this.height - OUTER_MARGIN - links.size() * item;
		int poolIndex = 0;

		for (RemoteContent.Link link : links)
		{
			Rect rect = Rect.pooled(this.railLinkPool, poolIndex++);
			rect.set(0, y, RAIL_WIDTH, item);
			this.railLinkRects.add(rect);

			boolean hovered = rect.contains(mouseX, mouseY);

			if (hovered)
			{
				ctx.fill(rect.x, rect.y, rect.x + rect.width - 1, rect.y + rect.height, Theme.SURFACE_ELEVATED);
			}

			int iconX = rect.x + (RAIL_WIDTH - size) / 2;
			int iconY = rect.y + (item - size) / 2;
			Identifier icon = ImageStore.avatar(link.iconUrl());

			if (icon != null)
			{
				Theme.image(ctx, icon, iconX, iconY, size, size, 64, 64);
			}
			else
			{
				Theme.roundedRect(ctx, iconX, iconY, size, size, 4, Theme.RAIL_TILE);
			}

			if (hovered && !link.label().isBlank())
			{
				String label = Theme.clip(this.font, link.label(), this.width - RAIL_WIDTH - 14);
				int width = this.font.width(label) + 8;
				Theme.roundedRect(ctx, RAIL_WIDTH + 2, rect.y + (item - 12) / 2, width, 12, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
				Theme.text(ctx, this.font, label, RAIL_WIDTH + 6, rect.y + (item - 12) / 2 + 2, Theme.TEXT);
			}

			y += item;
		}
	}

	private void renderPartnerRail(GuiGraphics ctx, int mouseX, int mouseY)
	{
		List<RemoteContent.Partner> partners = RemoteContent.partners();
		this.partnerRects.clear();

		if (partners.isEmpty())
		{
			return;
		}

		int railX = this.width - PARTNER_RAIL_WIDTH;
		ctx.fill(railX, TOP_BAR_HEIGHT, this.width, this.height, Theme.SURFACE);
		ctx.fill(railX, TOP_BAR_HEIGHT, railX + 1, this.height, Theme.HAIRLINE);

		int centreX = railX + PARTNER_RAIL_WIDTH / 2;

		String header = "Partners";
		Theme.text(ctx, this.font, header, centreX - this.font.width(header) / 2, TOP_BAR_HEIGHT + 8, Theme.TEXT_MUTE);

		int item = RAIL_ITEM_HEIGHT;
		int size = 22;
		int y = TOP_BAR_HEIGHT + 22;
		int poolIndex = 0;

		for (RemoteContent.Partner partner : partners)
		{
			if (y > this.height)
			{
				break;
			}

			Rect rect = Rect.pooled(this.partnerPool, poolIndex++);
			rect.set(railX, y, PARTNER_RAIL_WIDTH, item);
			this.partnerRects.add(rect);

			boolean hovered = rect.contains(mouseX, mouseY);
			int iconX = centreX - size / 2;
			int iconY = y + (item - size) / 2;

			if (hovered)
			{
				Theme.roundedRect(ctx, iconX - 3, iconY - 3, size + 6, size + 6, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
			}

			Identifier texture = ImageStore.avatar(partner.iconUrl());

			if (texture != null)
			{
				Theme.image(ctx, texture, iconX, iconY, size, size, 64, 64);
			}
			else
			{
				Theme.roundedRect(ctx, iconX, iconY, size, size, 5, Theme.RAIL_TILE);
			}

			if (hovered && !partner.name().isBlank())
			{
				String name = Theme.clip(this.font, partner.name(), railX - RAIL_WIDTH - 16);
				int width = this.font.width(name) + 8;
				int tipX = railX - width - 4;
				int tipY = y + (item - 12) / 2;
				Theme.roundedRect(ctx, tipX, tipY, width, 12, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
				Theme.text(ctx, this.font, name, tipX + 4, tipY + 2, Theme.TEXT);
			}

			y += item;
		}
	}

	// False only at the root, where the caller is free to close the whole screen
	public boolean navigateBack()
	{
		if (this.detailView.isOpen())
		{
			this.detailView.close();
			return true;
		}

		if (this.browsePage.profile.isOpen())
		{
			if (this.browsePage.nav.hasHistory())
			{
				this.browsePage.nav.back();
			}
			else
			{
				this.browsePage.profile.close();
			}

			Theme.click(0.9F);
			return true;
		}

		if (this.page == Page.UPLOAD && this.uploadPage.isOpen())
		{
			this.uploadPage.setOpen(false);
			this.setFocused(null);
			this.relayout();
			Theme.click(0.9F);
			return true;
		}

		if (this.browsePage.nav.hasHistory())
		{
			this.browsePage.nav.back();
			Theme.click(0.9F);
			return true;
		}

		return false;
	}

	// Null when a collection references a post that has since been removed
	private @Nullable SchematicEntry entryById(String id)
	{
		for (SchematicEntry entry : Catalogue.posts())
		{
			if (entry.id().equals(id))
			{
				return entry;
			}
		}

		return null;
	}

	// Null means everything saved; a name means that collection, in its stored order
	public void openDownloadAll(@Nullable String collection)
	{
		if (this.batchDownload.isActive())
		{
			Theme.click(0.9F);
			return;
		}

		List<SchematicEntry> entries = new ArrayList<>();

		if (collection == null)
		{
			for (SchematicEntry entry : Catalogue.posts())
			{
				if (isSaved(entry))
				{
					entries.add(entry);
				}
			}
		}
		else
		{
			for (String id : CollectionStore.postIds(collection))
			{
				SchematicEntry entry = this.entryById(id);

				if (entry != null)
				{
					entries.add(entry);
				}
			}
		}

		if (entries.isEmpty())
		{
			Theme.click(0.9F);
			Toasts.push("Nothing to download", "Save some posts first.", new ItemStack(Items.BOOKSHELF));
			return;
		}

		this.downloadAllModal.open(collection == null ? "All saved" : collection, entries);
	}

	public boolean copyToClipboard(String text)
	{
		try
		{
			Minecraft.getInstance().keyboardHandler.setClipboard(text);
			return true;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Copying text to clipboard failed", e);
			return false;
		}
	}

	// Newlines and control chars are dropped so a pasted multi-line value stays single-line
	public String pasteInto(String current, int max)
	{
		String clip;

		try
		{
			clip = Minecraft.getInstance().keyboardHandler.getClipboard();
		}
		catch (Exception e)
		{
			return current;
		}

		StringBuilder out = new StringBuilder(current);

		for (int i = 0; i < clip.length() && out.length() < max; i++)
		{
			char c = clip.charAt(i);

			if (c >= ' ')
			{
				out.append(c);
			}
		}

		return out.length() > max ? out.substring(0, max) : out.toString();
	}

	private void renderPremium(GuiGraphics ctx, int mouseX, int mouseY)
	{
		int top = TOP_BAR_HEIGHT + 12;
		int bottom = this.height - OUTER_MARGIN;

		Premium.ensureLoaded();
		this.premiumCardEntries.clear();

		ctx.enableScissor(this.contentX, top, this.contentX + this.contentWidth, bottom);

		int startY = top - Math.round(this.scroll);
		int y = startY;

		Theme.goldGradientTextScaled(ctx, this.font, "Premium Schematics", this.contentX, y, 1.5F, true);
		y += 22;

		for (String row : this.wrap("These are paid, boost, or shard schematics that must be bought. They are all "
				+ "private and high quality schematics featured by our partners.", this.contentWidth - 20, 3))
		{
			Theme.text(ctx, this.font, row, this.contentX, y, Theme.TEXT_MUTE);
			y += this.font.lineHeight + 2;
		}

		y += 14;

		y = this.renderPremiumSection(ctx, "Paid Schematics", Premium.Type.PAID, y, mouseX, mouseY);
		y = this.renderPremiumSection(ctx, "Shard Schematics", Premium.Type.SHARD, y, mouseX, mouseY);
		y = this.renderPremiumSection(ctx, "Booster Schematics", Premium.Type.BOOSTER, y, mouseX, mouseY);

		ctx.disableScissor();

		int contentHeight = y - startY;
		this.maxScroll = Math.max(0.0F, contentHeight - (bottom - top));
		this.scroll = Math.min(this.scroll, this.maxScroll);
		this.verticalScrollbar(ctx, SCROLLBAR_MAIN, this.scroll, this.maxScroll, top, bottom - top,
				this.contentX + this.contentWidth);
	}

	private int renderPremiumSection(GuiGraphics ctx, String label, Premium.Type type,
			int y, int mouseX, int mouseY)
	{
		Theme.goldGradientText(ctx, this.font, label, this.contentX, y, true);
		int lineX = this.contentX + this.font.width(Theme.bold(label)) + 10;
		int lineRight = this.contentX + this.contentWidth;

		if (lineRight - lineX > 8)
		{
			Theme.roundedRect(ctx, lineX, y + this.font.lineHeight / 2, lineRight - lineX, 1, 0, Theme.SURFACE_ELEVATED);
		}

		y += this.font.lineHeight + 10;

		List<Premium.Entry> entries = Premium.section(type);

		if (entries.isEmpty())
		{
			Theme.text(ctx, this.font, "Nothing here yet.", this.contentX, y, Theme.TEXT_MUTE);
			return y + this.font.lineHeight + 20;
		}

		int rowHeight = this.cardHeight + GUTTER;
		int rows = (entries.size() + this.columns - 1) / this.columns;

		for (int i = 0; i < entries.size(); i++)
		{
			int x = this.contentX + (i % this.columns) * (this.cardWidth + GUTTER);
			int cy = y + (i / this.columns) * rowHeight;
			Premium.Entry entry = entries.get(i);
			this.renderPremiumCard(ctx, entry, x, cy, mouseX, mouseY);

			Rect rect = Rect.pooled(this.premiumCardRects, this.premiumCardEntries.size());
			rect.set(x, cy, this.cardWidth, this.cardHeight);
			this.premiumCardEntries.add(entry);
		}

		return y + rows * rowHeight + 8;
	}

	// Browse card minus the view/like/save badges, plus a gold price tag and gold hover
	private void renderPremiumCard(GuiGraphics ctx, Premium.Entry entry, int x, int y, int mouseX, int mouseY)
	{
		SchematicEntry post = entry.post();
		boolean hovered = Theme.inside(mouseX, mouseY, x, y, this.cardWidth, this.cardHeight);

		Theme.roundedRect(ctx, x, y, this.cardWidth, this.cardHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);

		int imageHeight = imageHeight(this.cardWidth);
		Identifier texture = this.thumbnailTexture(post);

		if (texture != null)
		{
			Theme.image(ctx, texture, x, y, this.cardWidth, imageHeight);
		}
		else
		{
			Theme.loadingPlaceholder(ctx, x, y, this.cardWidth, imageHeight);
		}

		if (entry.type() == Premium.Type.SHARD)
		{
			// The amethyst shard doubles as the shard-currency icon, so the price sits beside it
			String price = String.valueOf(entry.shardPrice());
			int iconX = x + this.cardWidth - 18;
			int priceWidth = this.font.width(Theme.bold(price));
			int pillX = iconX - priceWidth - 6;
			Theme.roundedRect(ctx, pillX - 3, y + 4, priceWidth + 6, 11, Theme.RADIUS_PILL, 0xCC0F1114);
			Theme.text(ctx, this.font, Theme.bold(price), pillX, y + 6, Theme.SHARD);
			ctx.renderItem(new ItemStack(Items.AMETHYST_SHARD), iconX, y + 2);
		}
		else
		{
			String badge = entry.type() == Premium.Type.PAID ? "Paid" : "Booster";
			int badgeWidth = this.font.width(Theme.bold(badge)) + 10;
			int badgeX = x + this.cardWidth - badgeWidth - 4;
			Theme.roundedRect(ctx, badgeX - 1, y + 3, badgeWidth + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.goldGloss(ctx, badgeX, y + 4, badgeWidth, 11, 0.0F);
			Theme.text(ctx, this.font, Theme.bold(badge), badgeX + 5, y + 6, Theme.ON_GOLD);
		}

		// No poster on a premium listing, so the title spans both text lines instead of clipping
		this.drawPremiumTitle(ctx, post.cardName(), x + 5, y + imageHeight + 6, this.cardWidth - 10);

		if (hovered)
		{
			Theme.roundedOutline(ctx, x, y, this.cardWidth, this.cardHeight, Theme.RADIUS_CARD, Theme.GOLD_BRIGHT);
		}
	}

	// One bold line when the title fits, else the last word that fits leads a wrapped second line
	private void drawPremiumTitle(GuiGraphics ctx, String title, int x, int y, int width)
	{
		if (this.font.width(Theme.bold(title)) <= width)
		{
			Theme.text(ctx, this.font, Theme.bold(title), x, y, Theme.TEXT);
			return;
		}

		String[] words = title.trim().split("\\s+");
		StringBuilder first = new StringBuilder();

		for (int i = 0; i < words.length; i++)
		{
			String candidate = first.length() == 0 ? words[i] : first + " " + words[i];

			if (first.length() > 0 && this.font.width(Theme.bold(candidate)) > width)
			{
				String rest = String.join(" ", Arrays.copyOfRange(words, i, words.length));
				Theme.text(ctx, this.font, Theme.bold(Theme.clipBold(this.font, first.toString(), width)), x, y, Theme.TEXT);
				Theme.text(ctx, this.font, Theme.bold(Theme.clipBold(this.font, rest, width)),
						x, y + this.font.lineHeight + 2, Theme.TEXT);
				return;
			}

			first.setLength(0);
			first.append(candidate);
		}

		Theme.text(ctx, this.font, Theme.bold(Theme.clipBold(this.font, title, width)), x, y, Theme.TEXT);
	}

	private boolean clickPremium(double mouseX, double mouseY)
	{
		int top = TOP_BAR_HEIGHT + 12;
		int bottom = this.height - OUTER_MARGIN;

		for (int i = 0; i < this.premiumCardEntries.size(); i++)
		{
			Rect rect = this.premiumCardRects.get(i);

			// A scrolled-off card keeps its stale rect; ignore anything outside the visible band
			if (rect.y + this.cardHeight <= top || rect.y >= bottom)
			{
				continue;
			}

			if (rect.contains(mouseX, mouseY))
			{
				// Premium cards open the same detail card as browse, in premium mode (no 3D, Buy button)
				this.detailView.openPremium(this.premiumCardEntries.get(i).post(), this.premiumCardEntries.get(i));
				return true;
			}
		}

		return true;
	}

	private void renderBackToTop(GuiGraphics ctx, int mouseX, int mouseY, boolean overlayOpen)
	{
		boolean dashboard = this.page == Page.UPLOAD && !this.uploadPage.isOpen();
		float active = dashboard ? this.dashScroll : this.scroll;

		if (overlayOpen || active < 140.0F)
		{
			this.backToTopButton.set(0, 0, 0, 0);
			return;
		}

		int size = 22;
		int rightEdge = this.page == Page.BROWSE && !RemoteContent.partners().isEmpty()
				? this.width - PARTNER_RAIL_WIDTH : this.contentX + this.contentWidth;
		int bx = rightEdge - size - 8;
		int by = this.height - size - 10;
		this.backToTopButton.set(bx, by, size, size);

		boolean hovered = this.backToTopButton.contains(mouseX, mouseY);
		Theme.circleButton(ctx, bx, by, size, hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD,
				hovered ? Theme.ACCENT : Theme.HAIRLINE);
		Theme.chevronUp(ctx, bx, by, size, hovered ? Theme.ACCENT_BRIGHT : Theme.TEXT);
	}

	public void verticalScrollbar(GuiGraphics ctx, int channel, float scroll, float maxScroll,
			int trackTop, int trackHeight, int rightEdge)
	{
		if (maxScroll <= 0.0F)
		{
			return;
		}

		int barHeight = Math.max(16, Math.round(trackHeight * (trackHeight / (trackHeight + maxScroll))));
		int barY = trackTop + Math.round((trackHeight - barHeight) * (scroll / maxScroll));
		int barX = Math.min(rightEdge + 2, this.width - 4);
		this.drawScrollThumb(ctx, channel, barX, 3, barY, barHeight, 1, trackTop, trackHeight, Theme.HAIRLINE);
	}

	// Records the thumb geometry so it can be grabbed and dragged
	public void drawScrollThumb(GuiGraphics ctx, int channel, int barX, int barWidth, int barY, int barHeight,
			int radius, int trackTop, int trackHeight, int baseColor)
	{
		this.scrollbarChannel = channel;
		this.scrollbarX = barX;
		this.scrollbarWidth = barWidth;
		this.scrollbarThumbY = barY;
		this.scrollbarThumbHeight = barHeight;
		this.scrollbarTrackTop = trackTop;
		this.scrollbarTrackHeight = trackHeight;
		int color = this.draggingScrollbar && this.dragScrollChannel == channel ? lighten(baseColor) : baseColor;
		Theme.roundedRect(ctx, barX, barY, barWidth, barHeight, radius, color);
	}

	private static int lighten(int argb)
	{
		int a = Math.min(255, ((argb >>> 24) & 0xFF) + 80);
		int r = Math.min(255, ((argb >> 16) & 0xFF) + 60);
		int g = Math.min(255, ((argb >> 8) & 0xFF) + 60);
		int b = Math.min(255, (argb & 0xFF) + 60);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	// Every overlay that owns the screen while open; nothing beneath one may take a click, key, scroll or hover
	private boolean blockingOverlayOpen()
	{
		return this.detailView.isOpen() || this.modalOpen();
	}

	public boolean modalOpen()
	{
		return this.errorModal.isOpen() || this.nameInputModal.isOpen()
				|| this.deleteCollectionModal.isOpen() || this.collectionOptionsModal.isOpen()
				|| this.renameCollectionModal.isOpen() || this.tutorialModal.isOpen() || this.termsModal.isOpen()
				|| this.designerWarnModal.isOpen() || this.overwriteConfirm.isOpen()
				|| this.postOptionsModal.isOpen() || this.editPostModal.isOpen() || this.unpublishModal.isOpen()
				|| this.loadCodeModal.isOpen() || this.duplicateModal.isOpen() || this.downloadAllModal.isOpen()
				|| this.premiumBuyModal.isOpen() || this.shardPanel.isOpen() || this.shardWelcomeModal.isOpen()
				|| this.openOverlay() != null
				|| (this.staff != null && this.staff.isModalOpen());
	}

	private @Nullable Overlay openOverlay()
	{
		for (int i = this.overlays.size() - 1; i >= 0; i--)
		{
			if (this.overlays.get(i).isOpen())
			{
				return this.overlays.get(i);
			}
		}

		return null;
	}

	private boolean pageBlocked()
	{
		return this.blockingOverlayOpen() || this.cardMenu.isOpen() || this.browsePage.dropdowns.openDropdown() != null;
	}

	// A few pixels of horizontal slack make the thin bar easier to grab
	private boolean tryGrabScrollbar(double mouseX, double mouseY)
	{
		if (this.scrollbarChannel == SCROLLBAR_NONE || this.pageBlocked())
		{
			return false;
		}

		boolean inTrackX = mouseX >= this.scrollbarX - 4 && mouseX <= this.scrollbarX + this.scrollbarWidth + 4;

		if (!inTrackX || mouseY < this.scrollbarTrackTop || mouseY > this.scrollbarTrackTop + this.scrollbarTrackHeight)
		{
			return false;
		}

		this.draggingScrollbar = true;
		this.dragScrollChannel = this.scrollbarChannel;

		if (mouseY >= this.scrollbarThumbY && mouseY <= this.scrollbarThumbY + this.scrollbarThumbHeight)
		{
			// Thumb grab: keep the point under the cursor fixed while dragging
			this.scrollbarGrabOffset = (float) mouseY - this.scrollbarThumbY;
		}
		else
		{
			// Track click: jump the thumb centre to the cursor, then drag
			this.scrollbarGrabOffset = this.scrollbarThumbHeight / 2.0F;
			this.dragScrollbarTo(mouseY);
		}

		Theme.click(0.8F);
		return true;
	}

	private void dragScrollbarTo(double mouseY)
	{
		int travel = this.scrollbarTrackHeight - this.scrollbarThumbHeight;

		if (travel <= 0)
		{
			return;
		}

		float fraction = (float) ((mouseY - this.scrollbarGrabOffset - this.scrollbarTrackTop) / travel);
		fraction = Math.max(0.0F, Math.min(1.0F, fraction));

		switch (this.dragScrollChannel)
		{
			case SCROLLBAR_MAIN -> this.scroll = fraction * this.maxScroll;
			case SCROLLBAR_DASH -> this.dashScroll = fraction * this.dashMaxScroll;
			case SCROLLBAR_TOS -> this.termsModal.scrollTo(fraction);
			default -> {
			}
		}
	}

	public void drawLine(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color)
	{
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);

		if (length < 0.5F)
		{
			ctx.fill(x0, y0 - 1, x0 + 1, y0 + 1, color);
			return;
		}

		ctx.pose().pushMatrix();
		ctx.pose().translate((float) x0, (float) y0);
		ctx.pose().rotate((float) Math.atan2(dy, dx));
		ctx.fill(0, -1, Math.round(length) + 1, 1, color);
		ctx.pose().popMatrix();
	}

	public int settingsSectionHeader(GuiGraphics ctx, String label, int x, int y)
	{
		y += SETTINGS_SECTION_GAP;
		Theme.text(ctx, this.font, Theme.bold(label), x, y, Theme.TEXT);
		return y + this.font.lineHeight + SETTINGS_HEADER_GAP;
	}

	// Wrapped grey explainer under a section header; leaves a hint-sized gap before its control
	public int settingsDescription(GuiGraphics ctx, String text, int lines, int x, int y, int width)
	{
		for (String row : this.wrap(text, width, lines))
		{
			Theme.text(ctx, this.font, row, x, y, Theme.TEXT_ASH);
			y += this.font.lineHeight + 2;
		}

		return y + SETTINGS_HINT_GAP;
	}

	private void updateBanner()
	{
		RemoteContent.Announcement announcement = RemoteContent.announcement();
		String activeId = announcement != null && !announcement.id().isBlank()
				&& !announcement.id().equals(Settings.dismissedAnnouncement()) ? announcement.id() : null;

		if (activeId != null && !activeId.equals(this.bannerId))
		{
			this.bannerId = activeId;
			this.bannerAnnouncement = announcement;
			this.bannerEntering = true;
			this.bannerActive = true;
			this.bannerAnimStart = System.currentTimeMillis();
		}
		else if (this.bannerActive && this.bannerEntering && activeId == null && this.bannerId != null)
		{
			this.bannerEntering = false;
			this.bannerAnimStart = System.currentTimeMillis();
		}
	}

	private void dismissBanner()
	{
		if (this.bannerId != null)
		{
			Settings.dismissAnnouncement(this.bannerId);
		}

		this.bannerEntering = false;
		this.bannerAnimStart = System.currentTimeMillis();
	}

	private void renderBanner(GuiGraphics ctx, int mouseX, int mouseY)
	{
		if (!this.bannerActive || this.bannerAnnouncement == null)
		{
			return;
		}

		long elapsed = System.currentTimeMillis() - this.bannerAnimStart;
		float t = Math.max(0.0F, Math.min(1.0F, elapsed / 420.0F));
		float progress;

		if (this.bannerEntering)
		{
			progress = easeOutBack(t);
		}
		else
		{
			if (t >= 1.0F)
			{
				this.bannerActive = false;
				return;
			}

			progress = 1.0F - easeInBack(t);
		}

		RemoteContent.Announcement announcement = this.bannerAnnouncement;
		int pad = 12;
		int cardWidth = Math.min(this.contentWidth - 16, 430);
		int cardX = this.contentX + (this.contentWidth - cardWidth) / 2;

		List<String> desc = this.wrap(announcement.description(), cardWidth - pad * 2 - 4, 3);
		int titleRow = this.font.lineHeight + 2;
		int cardHeight = pad + titleRow + 6 + Math.max(1, desc.size()) * (this.font.lineHeight + 2) + pad;

		int restY = TOP_BAR_HEIGHT + 8;
		int hiddenY = -cardHeight - 6;
		int cardY = Math.round(hiddenY + (restY - hiddenY) * progress);

		int accent = parseColor(announcement.color(), Theme.ACCENT);

		Theme.roundedRect(ctx, cardX, cardY, cardWidth, cardHeight, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, cardX, cardY, cardWidth, cardHeight, Theme.RADIUS_CARD, Theme.HAIRLINE);
		ctx.fill(cardX + 1, cardY + 1, cardX + 5, cardY + cardHeight - 1, accent);

		int tx = cardX + pad + 4;
		int ty = cardY + pad;

		Theme.text(ctx, this.font, Theme.bold(Theme.clipBold(this.font, announcement.title(), cardWidth - pad * 2 - 70)),
				tx, ty, Theme.TEXT);

		if (!announcement.tag().isBlank())
		{
			int tagX = tx + this.font.width(Theme.bold(announcement.title())) + 8;
			int tagWidth = this.font.width(announcement.tag()) + 12;

			if (tagX + tagWidth < cardX + cardWidth - 24)
			{
				Theme.roundedRect(ctx, tagX, ty - 2, tagWidth, this.font.lineHeight + 4, Theme.RADIUS_PILL, accent);
				Theme.text(ctx, this.font, announcement.tag(), tagX + 6, ty, Theme.ON_ACCENT);
			}
		}

		int closeSize = 14;
		int closeX = cardX + cardWidth - pad - closeSize + 4;
		int closeY = cardY + pad - 3;
		this.bannerClose.set(closeX, closeY, closeSize, closeSize);
		boolean hover = this.bannerClose.contains(mouseX, mouseY);
		int closeColor = hover ? Theme.TEXT : Theme.TEXT_MUTE;
		this.drawLine(ctx, closeX + 3, closeY + 3, closeX + closeSize - 3, closeY + closeSize - 3, closeColor);
		this.drawLine(ctx, closeX + 3, closeY + closeSize - 3, closeX + closeSize - 3, closeY + 3, closeColor);

		int lineY = cardY + pad + titleRow + 6;

		for (String row : desc)
		{
			Theme.text(ctx, this.font, row, tx, lineY, Theme.TEXT_ASH);
			lineY += this.font.lineHeight + 2;
		}
	}

	private static float easeOutBack(float t)
	{
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float p = t - 1.0F;
		return 1.0F + c3 * p * p * p + c1 * p * p;
	}

	private static float easeInBack(float t)
	{
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		return c3 * t * t * t - c1 * t * t;
	}

	private static int parseColor(String hex, int fallback)
	{
		if (hex == null)
		{
			return fallback;
		}

		String value = hex.trim();

		if (value.startsWith("#"))
		{
			value = value.substring(1);
		}

		if (value.length() != 6)
		{
			return fallback;
		}

		try
		{
			return 0xFF000000 | Integer.parseInt(value, 16);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	// Honours blank lines between paragraphs; memoized so a constant body is wrapped once
	public List<String> wrapParagraphs(String text, int width)
	{
		if (text == null || text.isEmpty())
		{
			return List.of();
		}

		String key = "P" + width + " " + text;
		List<String> cached = this.wrapCache.get(key);

		if (cached != null)
		{
			return cached;
		}

		if (this.wrapCache.size() >= WRAP_CACHE_MAX)
		{
			this.wrapCache.clear();
		}

		List<String> out = new ArrayList<>();

		for (String paragraph : text.split("\n"))
		{
			if (paragraph.isEmpty())
			{
				out.add("");
			}
			else
			{
				out.addAll(this.wrapContext(paragraph, width));
			}
		}

		List<String> computed = List.copyOf(out);
		this.wrapCache.put(key, computed);
		return computed;
	}

	// Matches wrap() and shares its memoization cache
	public List<String> wrapContext(String text, int width)
	{
		return this.wrap(text, width, Integer.MAX_VALUE);
	}

	public int metaRow(GuiGraphics ctx, String label, String value, int x, int y, int width)
	{
		Theme.text(ctx, this.font, label, x, y, Theme.TEXT_ASH);
		Theme.text(ctx, this.font, value, x + width - this.font.width(value), y, Theme.TEXT);
		return y + this.font.lineHeight + 2;
	}

	// The returned list is immutable, so a caller that needs to mutate must copy it
	public List<String> wrap(String text, int width, int maxLines)
	{
		if (text == null || text.isEmpty())
		{
			return List.of();
		}

		String key = width + "\0" + maxLines + "\0" + text;
		List<String> cached = this.wrapCache.get(key);

		if (cached != null)
		{
			return cached;
		}

		if (this.wrapCache.size() >= WRAP_CACHE_MAX)
		{
			this.wrapCache.clear();
		}

		List<String> computed = List.copyOf(this.wrapLines(text, width, maxLines));
		this.wrapCache.put(key, computed);
		return computed;
	}

	private List<String> wrapLines(String text, int width, int maxLines)
	{
		List<String> lines = new ArrayList<>();

		if (text == null || text.isEmpty())
		{
			return lines;
		}

		// A word wider than the line is hard-broken, so an unbroken run cannot overflow the column
		for (String segment : text.split("\n", -1))
		{
			StringBuilder current = new StringBuilder();

			for (String rawWord : segment.split(" "))
			{
				String word = rawWord;

				while (this.font.width(word) > width)
				{
					int fit = 1;

					while (fit < word.length() && this.font.width(word.substring(0, fit + 1)) <= width)
					{
						fit++;
					}

					if (!current.isEmpty())
					{
						lines.add(current.toString());
						current = new StringBuilder();

						if (lines.size() == maxLines)
						{
							return lines;
						}
					}

					lines.add(word.substring(0, fit));
					word = word.substring(fit);

					if (lines.size() == maxLines)
					{
						return lines;
					}
				}

				String candidate = current.isEmpty() ? word : current + " " + word;

				if (this.font.width(candidate) > width)
				{
					lines.add(current.toString());

					if (lines.size() == maxLines)
					{
						return lines;
					}

					current = new StringBuilder(word);
				}
				else
				{
					current = new StringBuilder(candidate);
				}
			}

			lines.add(current.toString());

			if (lines.size() == maxLines)
			{
				return lines;
			}
		}

		return lines;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		double mouseX = event.x();
		double mouseY = event.y();

		this.registerButtonPress(mouseX, mouseY);

		if (Toasts.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		Overlay clickTarget = this.openOverlay();

		if (clickTarget != null)
		{
			clickTarget.mouseClicked(mouseX, mouseY);
			return true;
		}

		// The one-time shard primer sits above everything and swallows the click behind it
		if (this.shardWelcomeModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (event.button() == 0 && this.tryGrabScrollbar(mouseX, mouseY))
		{
			return true;
		}

		if (!this.pageBlocked() && this.backToTopButton.width > 0 && this.backToTopButton.contains(mouseX, mouseY))
		{
			this.scroll = 0.0F;
			this.dashScroll = 0.0F;
			Theme.click();
			return true;
		}

		if (this.premiumBuyModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.shardPanel.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.errorModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.nameInputModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.loadCodeModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.duplicateModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.deleteCollectionModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.collectionOptionsModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.renameCollectionModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.postOptionsModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.downloadAllModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.editPostModal.mouseClicked(event, doubleClick, mouseX, mouseY))
		{
			return true;
		}

		if (this.unpublishModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.staff != null && this.staff.modalClicked(mouseX, mouseY))
		{
			return true;
		}

		if (!this.detailView.isOpen() && this.overwriteConfirm.isOpen())
		{
			this.overwriteConfirm.mouseClicked(mouseX, mouseY);
			return true;
		}

		if (this.cardMenu.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.browsePage.collections.optionsClicked(event, mouseX, mouseY))
		{
			return true;
		}

		if (this.bannerActive && this.bannerEntering && !this.detailView.isOpen() && !this.termsModal.isOpen()
				&& !this.tutorialModal.isOpen() && !this.designerWarnModal.isOpen()
				&& this.bannerClose.contains(mouseX, mouseY))
		{
			this.dismissBanner();
			Theme.click(0.9F);
			return true;
		}

		if (this.termsModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.tutorialModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.designerWarnModal.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.detailView.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.browsePage.dropdowns.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.shardButton.width > 0 && this.shardButton.contains(mouseX, mouseY))
		{
			this.openShardPanel();
			return true;
		}

		if (this.helpMenu.buttonClicked(mouseX, mouseY) || this.updatePill.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.notifications.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.closeButton.contains(mouseX, mouseY))
		{
			this.onClose();
			return true;
		}

		// Checked before the rail and grid, so the overlaying dropdown wins the click
		if (this.gridPage() && this.browsePage.search.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		Page[] railPages = this.railPages();

		for (int i = 0; i < this.railRects.size() && i < railPages.length; i++)
		{
			if (this.railRects.get(i).contains(mouseX, mouseY))
			{
				Theme.buttonPop(this.railRects.get(i));
				this.switchPage(railPages[i]);
				return true;
			}
		}

		List<RemoteContent.Link> railLinks = RemoteContent.links();

		for (int i = 0; i < this.railLinkRects.size() && i < railLinks.size(); i++)
		{
			if (this.railLinkRects.get(i).contains(mouseX, mouseY))
			{
				this.openLink(railLinks.get(i).url());
				return true;
			}
		}

		if (this.gridPage())
		{
			List<RemoteContent.Partner> partners = RemoteContent.partners();

			for (int i = 0; i < this.partnerRects.size() && i < partners.size(); i++)
			{
				if (this.partnerRects.get(i).contains(mouseX, mouseY))
				{
					this.openLink(partners.get(i).url());
					return true;
				}
			}
		}

		if (this.page == Page.UPLOAD)
		{
			return this.uploadPage.mouseClicked(event, doubleClick, mouseX, mouseY);
		}

		if (this.page == Page.PREMIUM)
		{
			return this.clickPremium(mouseX, mouseY);
		}

		if (this.page == Page.SETTINGS)
		{
			return this.settingsPage.mouseClicked(mouseX, mouseY);
		}

		if (this.page == Page.COSMETICS)
		{
			return this.cosmeticsPage.mouseClicked(event, doubleClick, mouseX, mouseY);
		}

		if (this.page == Page.STAFF && this.staff != null)
		{
			return this.staff.mouseClicked(event, doubleClick, mouseX, mouseY);
		}

		if (this.page == Page.MAPART)
		{
			return this.mapartTab.mouseClicked(mouseX, mouseY);
		}

		if (this.browsePage.mouseClicked(event, mouseX, mouseY))
		{
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	public <T extends Overlay> T overlay(T overlay)
	{
		this.overlays.add(overlay);
		return overlay;
	}

	// Used when no Index is on screen yet: the next init opens the post
	public static void queueOpenPost(String postId)
	{
		pendingOpenPostId = postId;
	}

	public void openPostById(String postId)
	{
		SchematicIndexMod.LOGGER.debug("Opening post {} by id", postId);
		Net.submit(() -> {
			Backend.ApiResult result = Backend.getForApiResult("/post/" + Backend.encode(postId));
			JsonObject body = result.body();
			SchematicEntry entry = result.ok() && body != null && body.has("id") ? Backend.parsePost(body) : null;

			Minecraft.getInstance().execute(() -> {
				if (entry != null)
				{
					this.switchPage(Page.BROWSE);
					this.openDetail(entry);
					return;
				}

				SchematicIndexMod.LOGGER.debug("Open post {} failed with status {}", postId, result.status());
				boolean gone = result.status() == 404;
				String code = gone ? Errors.OPEN_NOT_FOUND : Errors.OPEN_FAILED;
				String text = gone ? "That build isn't in the catalogue any more." : "Couldn't load that build. Try again in a moment.";
				Toasts.push("Couldn't open build", text + " (" + code + ")", new ItemStack(Items.BARRIER));
			});
		});
	}

	public void openDetail(SchematicEntry entry)
	{
		this.detailView.open(entry);
	}

	private void cycleRailPage(int step)
	{
		Page[] pages = this.railPages();
		int current = 0;

		for (int i = 0; i < pages.length; i++)
		{
			if (pages[i] == this.page)
			{
				current = i;
			}
		}

		this.switchPage(pages[Math.floorMod(current + step, pages.length)]);
	}

	// The search box can be blurred without clearing the screen's focus, so the child's own flag decides
	private boolean textFieldFocused()
	{
		GuiEventListener focused = this.getFocused();
		return (focused != null && focused.isFocused()) || this.browsePage.search.isFocused()
				|| this.uploadPage.anyFieldFocused();
	}

	public void switchPage(Page target)
	{
		if (target != this.page)
		{
			Theme.tab();
		}

		if (target == Page.UPLOAD)
		{
			// The upload page keeps its open state, so returning to Upload lands back on the same form
			this.dashboardPage.expireStats();
			this.dashScroll = 0.0F;
		}

		if (target == Page.PREMIUM)
		{
			// Opening the tab re-pulls /premium so a newly published listing shows without a restart
			if (stale(lastPremiumPull))
			{
				lastPremiumPull = Util.getMillis();
				Premium.refresh();
			}
		}

		if (target == Page.COSMETICS)
		{
			Usage.once("cosmetics_open");
			CoachMark.maybe(this, CoachMark.Kind.COSMETICS);

			// Pulls the owned palette, tags and shard balance so prices reflect the server
			if (stale(lastCosmeticsPull))
			{
				lastCosmeticsPull = Util.getMillis();
				CosmeticColors.refresh();
				CosmeticTags.refresh();
			}
		}

		this.page = target;
		sessionPage = target;
		this.browsePage.dropdowns.closeDropdowns();
		this.cosmeticsPage.resetPreviewEffect();
		this.scroll = 0.0F;
		this.uploadPage.clearStatus();
		this.browsePage.resetView();
		this.setFocused(null);
		this.relayout();
	}

	public void openLink(String url)
	{
		openExternal(url);
	}

	// Every link the mod opens comes from our own site or server, so it opens straight away; the scheme check
	// keeps a bad remote value from reaching anything but the browser
	public static void openExternal(@Nullable String url)
	{
		if (url == null || !(url.startsWith("https://") || url.startsWith("http://")))
		{
			return;
		}

		Theme.click(1.0F);
		Util.getPlatform().openUri(url);
	}

	public String downloadFileName(SchematicEntry entry)
	{
		return entry.title() + " - The Schematic Index Addon.litematic";
	}

	// Keeps the downloaded filter off the disk during refilter and init off a slow folder
	public void refreshDownloadedNames()
	{
		// A re-download may have brought a local copy back in sync, so cached answers are suspect
		this.staleCache.clear();
		int scan = ++this.downloadScan;
		Path directory = Settings.downloadDirectory();

		Net.submit(() -> {
			Set<String> names = new HashSet<>();

			try (Stream<Path> files = Files.list(directory))
			{
				files.forEach(p -> names.add(p.getFileName().toString().toLowerCase(Locale.ROOT)));
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.debug("Could not list the download folder", e);
			}

			Minecraft.getInstance().execute(() -> {
				if (scan != this.downloadScan || names.equals(this.downloadedNames))
				{
					return;
				}

				this.downloadedNames = names;
				this.browsePage.refilter();
			});
		});
	}

	public boolean isDownloaded(SchematicEntry entry)
	{
		String key = this.downloadedKeyCache.get(entry.title());

		if (key == null)
		{
			if (this.downloadedKeyCache.size() >= CARD_TEXT_CACHE_MAX)
			{
				this.downloadedKeyCache.clear();
			}

			key = Download.safeName(this.downloadFileName(entry)).toLowerCase(Locale.ROOT);
			this.downloadedKeyCache.put(entry.title(), key);
		}

		return this.downloadedNames.contains(key);
	}

	// isDownloadStale hashes a file, and the "Update" badge queries this every frame
	public boolean isUpdateAvailable(SchematicEntry entry)
	{
		if (!this.isDownloaded(entry))
		{
			return false;
		}

		Boolean cached = this.staleCache.get(entry.id());

		if (cached != null)
		{
			return cached;
		}

		boolean stale = Backend.isDownloadStale(entry);
		this.staleCache.put(entry.id(), stale);
		return stale;
	}

	// The UI is optimistic, so an unsaved follow is reverted here with an error code
	public void verifyFollow(String postId, String poster)
	{
		Thread worker = new Thread(() -> {
			Backend.ApiResult result = Backend.follow(postId, poster);
			boolean ok = result.ok();
			String ign = poster;

			if (ok)
			{
				Shards.pokeSoon();
				JsonObject creator = Backend.getJson("/creator/" + Backend.encode(poster));

				String creatorIgn = Json.stringOf(creator, "ign", "");

				if (!creatorIgn.isBlank())
				{
					ign = creatorIgn;
				}
			}

			String skinName = ign;
			Minecraft.getInstance().execute(() -> {
				if (ok)
				{
					ItemStack head = new ItemStack(Items.PLAYER_HEAD);
					head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(skinName));
					Toasts.push("Followed " + poster, "You'll be notified whenever they post a new schematic", head);
				}
				else
				{
					if (Follows.isFollowing(poster))
					{
						Follows.toggle(poster);
					}

					if (poster.equals(this.browsePage.profile.poster()))
					{
						this.browsePage.profile.fetch(poster);
					}

					Toasts.refusal(result, Errors.FOLLOW);
				}
			});
		}, "schematicindex-follow");
		worker.setDaemon(true);
		worker.start();
	}

	public void verifyUnfollow(String poster)
	{
		Thread worker = new Thread(() -> {
			Backend.ApiResult result = Backend.unfollow(poster);

			if (result.ok())
			{
				Shards.pokeSoon();
			}

			Minecraft.getInstance().execute(() -> {
				if (!result.ok())
				{
					if (!Follows.isFollowing(poster))
					{
						Follows.toggle(poster);
					}

					if (poster.equals(this.browsePage.profile.poster()))
					{
						this.browsePage.profile.fetch(poster);
					}

					Toasts.refusal(result, Errors.FOLLOW);
				}
			});
		}, "schematicindex-unfollow");
		worker.setDaemon(true);
		worker.start();
	}

	// Owns the DONE and FAILED transitions, and runs whatever page or modal is open, so they fire
	// exactly once even when the user closed the detail modal mid-download
	private void tickDownloads()
	{
		if (this.downloadStates.isEmpty())
		{
			return;
		}

		Iterator<Map.Entry<String, Download.State>> it = this.downloadStates.entrySet().iterator();

		while (it.hasNext())
		{
			Map.Entry<String, Download.State> tracked = it.next();
			Download.Progress progress = Download.progress(tracked.getKey());

			if (progress == null)
			{
				continue;
			}

			Download.State state = progress.state();

			if (state == tracked.getValue())
			{
				continue;
			}

			if (state == Download.State.DONE)
			{
				Theme.success();
				this.status = "";
				// Otherwise the Downloaded / Not yet filter goes stale after an in-session download
				this.refreshDownloadedNames();
				this.browsePage.refilter();
				RatingNudge.downloaded(tracked.getKey());
				it.remove();
			}
			else if (state == Download.State.FAILED)
			{
				Theme.failure();
				this.status = "Download failed. Press Retry to try again.";
				this.detailDownloadLocked = false;
				this.showError(Errors.DOWNLOAD);
				RatingNudge.failed(tracked.getKey());
				it.remove();
			}
			else
			{
				tracked.setValue(state);
			}
		}
	}

	public void copyShareLink(SchematicEntry entry)
	{
		ExportActions.copyShareLink(this, entry);
	}

	// The detail card's Download button and the card menu share this so both confirm an overwrite alike
	public void requestDownload(SchematicEntry entry)
	{
		if (Settings.confirmOverwrite() && !this.overwriteConfirm.isOpen())
		{
			Download.Progress progress = Download.progress(entry.id());
			boolean alreadyDone = progress != null && progress.state() == Download.State.DONE;

			if (!alreadyDone && Files.exists(Download.resolveTarget(this.downloadFileName(entry))))
			{
				this.overwriteConfirm.open(entry);
				return;
			}
		}

		this.beginDownload(entry);
	}

	public void beginDownload(SchematicEntry entry)
	{
		this.beginDownload(entry, true);
	}

	// A batch passes chime=false so a long queue does not click once per file
	public void beginDownload(SchematicEntry entry, boolean chime)
	{
		Usage.once("first_download");

		if (chime)
		{
			Theme.click(1.2F);
			RatingNudge.track(entry);
		}

		String url = entry.fileUrl();

		if (url != null && !url.isBlank())
		{
			Download.start(entry.id(), this.downloadFileName(entry), url, null);
		}
		else
		{
			Path source = SchematicPreview.pathFor(entry.schematicSlot());

			if (source == null)
			{
				this.status = "No file to download.";
				return;
			}

			Download.start(entry.id(), this.downloadFileName(entry), null, source);
		}

		Backend.downloadAsync(entry.id());
		Shards.pokeSoon();
		this.detailDownloadLocked = true;
		this.status = "Downloading the schematic into your selected folder...";
		this.downloadStates.put(entry.id(), Download.State.RUNNING);
		CoachMark.download(this, this.downloadFileName(entry));
	}

	public void beginUpload()
	{
		this.uploadPage.beginUpload();
	}

	public void toggleLike(SchematicEntry entry)
	{
		Usage.once("first_like");
		boolean nowLiked = Bookmarks.toggleLike(entry.id());

		if (nowLiked)
		{
			LIKE_POPS.put(entry.id(), System.currentTimeMillis());
			Theme.like();
		}
		else
		{
			Theme.click(0.9F);
		}

		// With no backend a like is purely local, so there is nothing to verify or roll back
		if (!Backend.configured())
		{
			return;
		}

		// A refused request rolls the local state back, so the heart cannot stay lit for a like the
		// server never recorded; a slow answer is waited for, never timed out into a rollback
		String postId = entry.id();
		Thread worker = new Thread(() -> {
			Backend.ApiResult result = Backend.like(postId, nowLiked);

			if (result.ok())
			{
				Shards.pokeSoon();
				int likes = Json.intOf(result.body(), "likes", entry.likes());
				boolean liked = Json.boolOf(result.body(), "liked", nowLiked);
				Catalogue.updateLikes(postId, likes, liked);
				Minecraft.getInstance().execute(() -> this.detailView.applyLikes(postId, likes, liked));
				return;
			}

			Minecraft.getInstance().execute(() -> {
				if (Bookmarks.isLiked(postId) == nowLiked)
				{
					Bookmarks.toggleLike(postId);
				}

				if (nowLiked)
				{
					LIKE_POPS.remove(postId);
				}

				Toasts.refusal(result, Errors.LIKE);
			});
		}, "schematicindex-like");
		worker.setDaemon(true);
		worker.start();
	}

	public static long popAge(SchematicEntry entry)
	{
		Long popped = LIKE_POPS.get(entry.id());

		if (popped == null)
		{
			return -1L;
		}

		long age = System.currentTimeMillis() - popped;

		// Dropped as it is queried, so the session map cannot grow unbounded
		if (age >= Theme.HEART_POP_MILLIS)
		{
			LIKE_POPS.remove(entry.id());
			return -1L;
		}

		return age;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
	{
		if (this.photoMode.mouseDragged(event.x(), dragX, dragY))
		{
			return true;
		}

		if (this.draggingScrollbar)
		{
			this.dragScrollbarTo(event.y());
			return true;
		}

		if (this.settingsPage.mouseDragged(event.x()))
		{
			return true;
		}

		if (this.mapartTab.mouseDragged(event.x(), event.y()))
		{
			return true;
		}

		if (this.cosmeticsPage.mouseDragged(event.x(), event.y()))
		{
			return true;
		}

		if (this.detailView.mouseDragged(event.x(), dragX, dragY))
		{
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event)
	{
		this.detailView.releaseDrags();
		this.photoMode.releaseDrags();

		if (this.draggingScrollbar)
		{
			this.draggingScrollbar = false;
			this.dragScrollChannel = SCROLLBAR_NONE;
			return true;
		}

		if (this.settingsPage.mouseReleased())
		{
			return true;
		}

		if (this.mapartTab.mouseReleased())
		{
			return true;
		}

		if (this.cosmeticsPage.mouseReleased(event.x(), event.y()))
		{
			return true;
		}

		return super.mouseReleased(event);
	}

	private void registerButtonPress(double mouseX, double mouseY)
	{
		Rect[] buttons;

		if (this.termsModal.isOpen())
		{
			buttons = this.termsModal.pressButtons();
		}
		else if (this.tutorialModal.isOpen())
		{
			buttons = this.tutorialModal.pressButtons();
		}
		else if (this.overwriteConfirm.isOpen())
		{
			buttons = this.overwriteConfirm.pressButtons();
		}
		else if (this.downloadAllModal.isOpen())
		{
			buttons = this.downloadAllModal.pressButtons();
		}
		else if (this.reportModal.isPickerOpen())
		{
			buttons = this.reportModal.pickerPressButtons();
		}
		else if (this.reportModal.isContextOpen())
		{
			buttons = this.reportModal.contextPressButtons();
		}
		else if (this.claimModal.isOpen())
		{
			buttons = this.claimModal.pressButtons();
		}
		else if (this.detailView.isOpen())
		{
			buttons = this.detailView.pressButtons();
		}
		else if (this.page == Page.SETTINGS)
		{
			buttons = this.settingsPage.pressButtons(this.closeButton);
		}
		else
		{
			buttons = new Rect[]{this.closeButton, this.browsePage.grid.retryButton, this.uploadPage.unlockButton,
					this.uploadPage.signOutButton, this.uploadPage.categoryButton, this.uploadPage.picturesButton,
					this.uploadPage.schematicButton, this.uploadPage.photoModeButton, this.uploadPage.postButton,
					this.browsePage.chips.followingFilterButton, this.browsePage.chips.historyButton,
					this.uploadPage.clearButton};
		}

		for (Rect rect : buttons)
		{
			if (rect.contains(mouseX, mouseY))
			{
				Theme.buttonPress(rect);
				return;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
	{
		Overlay scrollTarget = this.openOverlay();

		if (scrollTarget != null)
		{
			scrollTarget.mouseScrolled(mouseX, mouseY, scrollY);
			return true;
		}

		if (this.notifications.mouseScrolled(mouseX, mouseY, scrollY))
		{
			return true;
		}

		if (this.cardMenu.mouseScrolled(mouseX, mouseY, scrollY))
		{
			return true;
		}

		// Anchored to a card that would otherwise move under it, so the tick folds the menu instead
		if (this.cardMenu.isOpen())
		{
			this.cardMenu.close();
			return true;
		}

		// The shard panel is a full-screen overlay; it scrolls the day strip and swallows the rest
		if (this.shardPanel.isOpen())
		{
			return this.shardPanel.mouseScrolled(mouseX, mouseY, scrollY);
		}

		if (this.shardWelcomeModal.isOpen())
		{
			return true;
		}

		if (this.termsModal.mouseScrolled(scrollY))
		{
			return true;
		}

		if (this.editPostModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY))
		{
			return true;
		}

		if (this.detailView.mouseScrolled(mouseX, mouseY, scrollY))
		{
			return true;
		}

		if (this.pageBlocked())
		{
			return true;
		}

		this.cosmeticsPage.cancelReveal();

		if (this.page == Page.MAPART)
		{
			return this.mapartTab.mouseScrolled(mouseX, mouseY, scrollY);
		}

		if (this.page == Page.STAFF && this.staff != null)
		{
			return this.staff.mouseScrolled(mouseX, mouseY, scrollY);
		}

		if (this.page == Page.UPLOAD)
		{
			if (this.uploadPage.isOpen())
			{
				return this.uploadPage.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
			}

			this.dashScroll = Math.max(0.0F, Math.min(this.dashMaxScroll, this.dashScroll - (float) scrollY * SCROLL_STEP));
			return true;
		}

		this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll - (float) scrollY * SCROLL_STEP));
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event)
	{
		Overlay typeTarget = this.openOverlay();

		if (typeTarget != null)
		{
			typeTarget.charTyped(event);
			return true;
		}

		if (this.shardPanel.charTyped(event))
		{
			return true;
		}

		if (this.nameInputModal.charTyped(event))
		{
			return true;
		}

		if (this.renameCollectionModal.charTyped(event))
		{
			return true;
		}

		if (this.loadCodeModal.charTyped(event))
		{
			return true;
		}

		if (this.reportModal.charTyped(event))
		{
			return true;
		}

		if (this.claimModal.charTyped(event))
		{
			return true;
		}

		// The edit-post boxes are screen widgets and take their text through super
		if (this.blockingOverlayOpen() && !this.editPostModal.isOpen())
		{
			return true;
		}

		if (this.page == Page.MAPART && this.mapartTab.charTyped(event))
		{
			return true;
		}

		return super.charTyped(event);
	}

	@Override
	public void onFilesDrop(List<Path> paths)
	{
		if (this.page != Page.UPLOAD || !this.uploadPage.isOpen() || paths.isEmpty())
		{
			super.onFilesDrop(paths);
			return;
		}

		this.uploadPage.dropFiles(paths);
	}

	// Super as well as Control, so the custom text modals accept Cmd+V on macOS
	public static boolean isPasteChord(KeyEvent event)
	{
		return event.key() == 86 && (event.modifiers() & (0x2 | 0x8)) != 0;
	}

	public void savePreviewPng(SchematicEntry entry)
	{
		ExportActions.savePreviewPng(this, entry);
	}

	public void copyImageToClipboard(SchematicEntry entry)
	{
		ExportActions.copyImageToClipboard(this, entry);
	}

	// MainMixin clears java.awt.headless before the toolkit can cache it; setting it again here is too
	// late to matter once the toolkit exists, and only a HeadlessToolkit can still throw from this call
	public static java.awt.datatransfer.Clipboard systemClipboard()
	{
		System.setProperty("java.awt.headless", "false");
		return java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		Overlay keyTarget = this.openOverlay();

		if (keyTarget != null)
		{
			keyTarget.keyPressed(event);
			return true;
		}

		// The one-time shard primer captures keys until dismissed
		if (this.shardWelcomeModal.keyPressed(event))
		{
			return true;
		}

		// The panel swallows every key while open, so Escape closes it rather than the whole screen
		if (this.shardPanel.keyPressed(event))
		{
			return true;
		}

		// Other keys fall through to the per-modal handlers
		Rect[] modalButtons = this.modalFocusButtons();

		if (modalButtons != null)
		{
			switch (event.key())
			{
				case 262, 264 -> { // right / down
					this.modalFocus = this.modalFocus < 0 ? 0 : (this.modalFocus + 1) % modalButtons.length;
					Theme.click();
					return true;
				}
				case 263, 265 -> { // left / up
					this.modalFocus = this.modalFocus < 0
							? 0
							: (this.modalFocus + modalButtons.length - 1) % modalButtons.length;
					Theme.click();
					return true;
				}
				case 257, 335 -> { // enter
					this.activateModalFocus();
					return true;
				}
				default -> {
				}
			}
		}

		if (this.editPostModal.isOpen())
		{
			if (this.editPostModal.keyPressed(event))
			{
				return true;
			}

			return super.keyPressed(event);
		}

		if (this.postOptionsModal.keyPressed(event))
		{
			return true;
		}

		if (this.downloadAllModal.keyPressed(event))
		{
			return true;
		}

		if (this.unpublishModal.keyPressed(event))
		{
			return true;
		}

		if (this.staff != null && this.staff.keyPressed(event))
		{
			return true;
		}

		if (this.errorModal.keyPressed(event))
		{
			return true;
		}

		if (this.nameInputModal.keyPressed(event))
		{
			return true;
		}

		if (this.renameCollectionModal.keyPressed(event))
		{
			return true;
		}

		if (this.duplicateModal.keyPressed(event))
		{
			return true;
		}

		if (this.loadCodeModal.keyPressed(event))
		{
			return true;
		}

		if (this.collectionOptionsModal.keyPressed(event))
		{
			return true;
		}

		if (this.deleteCollectionModal.keyPressed(event))
		{
			return true;
		}

		if (this.termsModal.keyPressed(event))
		{
			return true;
		}

		if (this.tutorialModal.keyPressed(event))
		{
			return true;
		}

		if (this.premiumBuyModal.keyPressed(event))
		{
			return true;
		}

		if (this.designerWarnModal.keyPressed(event))
		{
			return true;
		}

		if (this.reportModal.keyPressed(event))
		{
			return true;
		}

		if (this.claimModal.keyPressed(event))
		{
			return true;
		}

		if (this.detailView.keyPressed(event))
		{
			return true;
		}

		if (!this.detailView.isOpen() && this.overwriteConfirm.isOpen())
		{
			if (event.key() == 256)
			{
				this.overwriteConfirm.cancel();
			}

			return true;
		}

		// The detail card alone lets keys through, for its gallery; anything stacked on it owns them
		if (this.detailView.isOpen() ? this.detailView.overlayOpen() : this.blockingOverlayOpen())
		{
			return true;
		}

		if (this.cardMenu.keyPressed(event))
		{
			return true;
		}

		if (this.page == Page.MAPART && this.mapartTab.keyPressed(event))
		{
			return true;
		}

		if (event.key() == 256 && this.browsePage.dropdowns.closeDropdowns())
		{
			return true;
		}

		// Escape steps back one level, and only falls through to the close when nothing is left
		if (event.key() == 256 && this.navigateBack())
		{
			return true;
		}

		if (event.key() == 298 && FabricLoader.getInstance().isDevelopmentEnvironment())
		{
			this.showError("SI-TEST99");
			return true;
		}

		if (this.page == Page.UPLOAD && this.uploadPage.isOpen() && event.key() == 86
				&& (event.modifiers() & 0x2) != 0 && !this.uploadPage.anyFieldFocused())
		{
			this.uploadPage.pasteFromClipboard();
			return true;
		}

		if (this.detailView.imageKeyPressed(event))
		{
			return true;
		}

		if (this.page == Page.SETTINGS && !this.pageBlocked() && this.settingsPage.keyPressed(event.key()))
		{
			return true;
		}

		// Tab walks the rail top to bottom and Shift+Tab back up, but only while vanilla's own Tab focus
		// cycling has no text field to serve
		if (event.key() == 258 && !this.textFieldFocused() && !this.pageBlocked())
		{
			this.cycleRailPage(event.hasShiftDown() ? -1 : 1);
			return true;
		}

		// A committed search skips the debounce window; falls through so the field still sees the key
		if (this.browsePage.search.isFocused() && (event.key() == 257 || event.key() == 335))
		{
			this.browsePage.flushSearchDebounce();
		}

		if (this.gridPage() && !this.pageBlocked() && !this.browsePage.search.isFocused()
				&& this.browsePage.nav.handleGridKey(event.key()))
		{
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event)
	{
		this.detailView.keyReleased(event);
		this.photoMode.keyReleased(event);

		return super.keyReleased(event);
	}

	@Override
	public void onClose()
	{
		this.browsePage.nav.saveSession();
		this.uploadPage.saveDraft();
		this.detailView.flushPendingRate(true);

		ImageStore.releaseAll();
		SchematicPreview.releaseAll();

		// Otherwise the map accumulates stale entries across repeated opens
		this.downloadStates.clear();

		if (this.minecraft != null)
		{
			this.minecraft.setScreen(this.parent);
		}
	}

	@Override
	public void removed()
	{
		// removed() runs before the next screen initialises, so the game returns to the player's scale
		this.schematicindex$restoreGuiScale();
		this.photoMode.close();
		super.removed();
	}

	// Recomputes this.width/height from the pinned scale, so the layout that follows uses it
	private void schematicindex$applyStableGuiScale()
	{
		Minecraft mc = this.minecraft;

		if (mc == null)
		{
			return;
		}

		Window window = mc.getWindow();
		boolean unicode = mc.isEnforceUnicode();

		// Minecraft's own "Auto" value: drops to 1 on a window too small for scale 2, as vanilla does
		int maxScale = Math.max(1, window.calculateScale(0, unicode));
		int target = Math.min(FIXED_SCALE, maxScale);

		this.restoreScale = window.calculateScale(mc.options.guiScale().get(), unicode);

		if (window.getGuiScale() != target)
		{
			window.setGuiScale(target);
			this.width = window.getGuiScaledWidth();
			this.height = window.getGuiScaledHeight();
		}
	}

	// The scale reported is the player's own, read before applyStableGuiScale pinned the menu to FIXED_SCALE
	private void reportOpen()
	{
		Usage.once("first_open");

		if (this.minecraft == null)
		{
			return;
		}

		int scale = Math.max(1, Math.min(4, (int) Math.round(this.restoreScale)));
		Usage.once("ui:scale_" + scale);
		int width = this.minecraft.getWindow().getWidth();
		Usage.once("ui:width_" + (width < 1280 ? "small" : width >= 1920 ? "large" : "medium"));
	}

	private void schematicindex$restoreGuiScale()
	{
		Minecraft mc = this.minecraft;

		if (mc == null || this.restoreScale <= 0)
		{
			return;
		}

		Window window = mc.getWindow();
		// Recomputed from the live options, in case the window was resized while the menu was open
		int scale = window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());

		if (window.getGuiScale() != scale)
		{
			window.setGuiScale(scale);
		}

		this.restoreScale = -1.0;
	}
}

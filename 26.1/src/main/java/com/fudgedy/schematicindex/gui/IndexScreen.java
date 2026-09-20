package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.Cosmetics;
import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Bookmarks;
import com.fudgedy.schematicindex.catalogue.Catalogue;
import com.fudgedy.schematicindex.catalogue.Category;
import com.fudgedy.schematicindex.catalogue.CollectionStore;
import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.catalogue.Download;
import com.fudgedy.schematicindex.catalogue.Follows;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Premium;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import com.fudgedy.schematicindex.catalogue.Shards;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.detail.ClaimModal;
import com.fudgedy.schematicindex.gui.detail.DetailView;
import com.fudgedy.schematicindex.gui.detail.OverwriteConfirm;
import com.fudgedy.schematicindex.gui.detail.ReportModal;
import com.fudgedy.schematicindex.gui.modal.CollectionOptionsModal;
import com.fudgedy.schematicindex.gui.modal.DeleteCollectionModal;
import com.fudgedy.schematicindex.gui.modal.DesignerWarnModal;
import com.fudgedy.schematicindex.gui.modal.DownloadAllModal;
import com.fudgedy.schematicindex.gui.modal.DuplicateModal;
import com.fudgedy.schematicindex.gui.modal.EditPostModal;
import com.fudgedy.schematicindex.gui.modal.ErrorModal;
import com.fudgedy.schematicindex.gui.modal.LoadCodeModal;
import com.fudgedy.schematicindex.gui.modal.NameInputModal;
import com.fudgedy.schematicindex.gui.modal.PostOptionsModal;
import com.fudgedy.schematicindex.gui.modal.PremiumBuyModal;
import com.fudgedy.schematicindex.gui.modal.RenameCollectionModal;
import com.fudgedy.schematicindex.gui.modal.ShardPanel;
import com.fudgedy.schematicindex.gui.modal.ShardWelcomeModal;
import com.fudgedy.schematicindex.gui.modal.TermsModal;
import com.fudgedy.schematicindex.gui.modal.TutorialModal;
import com.fudgedy.schematicindex.gui.modal.UnpublishModal;
import com.fudgedy.schematicindex.gui.widget.Buttons;
import com.fudgedy.schematicindex.gui.widget.ColorPicker;
import com.fudgedy.schematicindex.gui.widget.Controls;
import com.fudgedy.schematicindex.gui.widget.Dropdown;
import com.fudgedy.schematicindex.gui.widget.Fields;
import com.fudgedy.schematicindex.gui.widget.Rect;
import com.fudgedy.schematicindex.staff.StaffHook;
import com.fudgedy.schematicindex.staff.StaffScreen;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
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

	private static final int OUTER_MARGIN = 8;
	private static final int CONTENT_MAX_WIDTH = 720;
	public static final int TOP_BAR_HEIGHT = 32;
	public static final int RAIL_WIDTH = 34;
	private static final int RAIL_ITEM_HEIGHT = 34;
	private static final int PARTNER_RAIL_WIDTH = 58;
	
	private static final float RAIL_HOVER_GROW = 0.14F;
	private static final int RAIL_ITEM_GAP = 8;
	private static final int RAIL_LINK_HEIGHT = 26;
	private static final int GUTTER = 6;
	private static final int CAPTION_HEIGHT = 28;
	private static final int CHIP_HEIGHT = 14;
	private static final int CHIP_GAP = 8;
	private static final String SORT_CAPTION = "Sort: ";
	private static final String[] SORT_LABELS = sortLabels();
	private static final String DOWNLOAD_FILTER_CAPTION = "Show: ";
	private static final String[] DOWNLOAD_FILTER_LABELS = {"All", "Downloaded", "Not downloaded"};
	private static final int OFFLINE_BANNER_HEIGHT = 15;
	private static final int TRASH_CELL_WIDTH = 13; // trash glyph + padding reserved in named chips
	public static final int SCROLL_STEP = 24;
	public static final int HEART_SIZE = 9;
	public static final int FIELD_HEIGHT = 16;
	// One spacing rhythm for the settings page: rows share a trailing gap, sections a larger lead-in
	// Palette geometry: three rows of six, dropping to five columns once the gradient strip claims a column
	private static final int PALETTE_SLOTS = 18;
	private static final int PALETTE_ROWS = 3;
	private static final int PALETTE_SWATCH = 26;
	private static final int PALETTE_GAP = 6;
	private static final int GRADIENT_STRIP_GAP = 24;
	// The effect shop in card order; ids match what the server sells
	private static final String[] EFFECT_IDS = {Cosmetics.SHINE, Cosmetics.FLOW};
	private static final String[] EFFECT_LABELS = {"Shiny", "Colour Flow"};
	private static final long COSMETIC_REVEAL_MS = 220L;
	private static final Item[] EFFECT_ICONS = {Items.NETHER_STAR, Items.PRISMARINE_CRYSTALS};
	private static final float PROFILE_NAME_SCALE = 1.15F;
	private static final int SETTINGS_ROW_GAP = 12;
	private static final int SETTINGS_SECTION_GAP = 10;
	private static final int SETTINGS_HEADER_GAP = 8;
	private static final int SETTINGS_HINT_GAP = 5;
	public static final int DESC_FIELD_HEIGHT = 64;
	public static final int DESC_CHAR_LIMIT = 500;
	// Independent of the field's pixel width so a long draft is stored in full, not truncated to fit
	private static final int TITLE_CHAR_LIMIT = 120;

	private static final int PAGE_SIZE = 12;
	// Below this many ratings a star average says little, so those posts sort behind the rest
	private static final int RATED_MIN_RATINGS = 3;
	
	private static final int LOADING_ROW = 20;

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

	// Captured on diving into a sub-view so back lands where the user left off, not on a default BROWSE
	private record NavSnapshot(Page page, Set<Category> activeTags, Catalogue.Sort sort, String query,
			int downloadFilter, @Nullable String profilePoster, @Nullable String activeCollection,
			float scroll, int shownCount)
	{
	}

	// at is epoch millis
	private record NotifItem(String type, String text, long at)
	{
	}

	private final @Nullable Screen parent;
	private final List<SchematicEntry> visible = new ArrayList<>();

	public Page page = Page.BROWSE;
	// Empty means All; never contains Category.ALL. EnumSet gives a stable order for summaries and keys
	private final Set<Category> activeTags = EnumSet.noneOf(Category.class);

	// Static, so a menu reopen lands on the same view and a Minecraft restart resets it
	private static Catalogue.Sort sessionSort = Catalogue.Sort.TRENDING;
	private static Page sessionPage = Page.BROWSE;
	private static Set<Category> sessionTags = EnumSet.noneOf(Category.class);
	private static float sessionScroll;
	private static int sessionShown = PAGE_SIZE;
	private static int sessionDownloadFilter;
	// Silent identify runs once per launch; the manual Verify pill still retries after a failure
	private static boolean autoVerifyTried;
	private Catalogue.Sort sort = Catalogue.Sort.TRENDING;
	private String query = "";
	private long catalogueRevision = -1;

	// A keystroke schedules the heavy refilter instead of running it; render() fires it once typing
	// pauses, and any other refilter cancels the pending one
	private static final long SEARCH_DEBOUNCE_MS = 120L;
	private boolean searchRefilterPending;
	private long searchRefilterAt;

	// Recomputed only when the query or the catalogue changes, not per frame
	private final List<String> searchSuggestions = new ArrayList<>();
	private final List<String> searchSuggestCreators = new ArrayList<>();
	private String searchSuggestQuery = null;
	private long searchSuggestRevision = -1;
	// Title rows first, then creator rows, matching the render order
	private final List<Rect> searchSuggestRects = new ArrayList<>();

	// Keyed by width, maxLines and text; cleared on init/resize
	private final Map<String, List<String>> wrapCache = new HashMap<>();
	private static final int WRAP_CACHE_MAX = 512;

	// Rebuilt only when a form field feeding it changes
	private SchematicEntry cachedPreview;
	private String cachedPreviewKey;

	// Rebuilt on applyMyStats or a filter change, not every frame
	private final List<SchematicEntry> dashShown = new ArrayList<>();
	private String[][] dashTiles;
	private boolean dashCacheDirty = true;
	private Category dashCacheFilter;

	public int contentX;
	public int contentWidth;
	public int chipRowHeight = 26;
	public int gridTop;
	private int gridBottom;
	private int columns = 4;
	public int cardWidth = 120;
	public int cardHeight = 96;

	private float scroll;
	private float maxScroll;

	// Each scrollbar drawn this frame records its geometry and channel so a click can grab the thumb
	private static final int SCROLLBAR_NONE = 0;
	private static final int SCROLLBAR_MAIN = 1; // this.scroll / this.maxScroll
	private static final int SCROLLBAR_DASH = 2; // dashScroll / dashMaxScroll
	public static final int SCROLLBAR_TOS = 3;  // TermsModal's own scroll
	private int scrollbarChannel = SCROLLBAR_NONE;
	private int scrollbarX, scrollbarWidth, scrollbarThumbY, scrollbarThumbHeight;
	private int scrollbarTrackTop, scrollbarTrackHeight;
	private boolean draggingScrollbar;
	private int dragScrollChannel = SCROLLBAR_NONE;
	private float scrollbarGrabOffset;
	
	private int shownCount = PAGE_SIZE;
	// Bounded so deep navigation cannot grow it forever
	private final ArrayDeque<NavSnapshot> navStack = new ArrayDeque<>();
	// An unchanged signature means a background refresh, so scroll and page depth survive the refilter
	private String lastFilterKey = "";
	private int focusedCard = -1;

	// -1 until an arrow key is pressed, so the focus ring only shows once it is used
	public int modalFocus = -1;

	private EditBox searchBox;
	private EditBox codeBox;
	private EditBox titleBox;
	private EditBox thumbnailBox;
	private EditBox designerBox;
	private MultiLineEditBox descriptionBox;
	private final Rect uploadDescriptionBounds = new Rect();

	private final Rect closeButton = new Rect();
	// Opening the inbox panel marks everything read and clears the unread badge
	private final Rect bellButton = new Rect();
	// Far-right shard balance pill; opens the daily-login and quests panel
	private final Rect shardButton = new Rect();
	private boolean notifPanelOpen;
	// Refreshed each render for click-outside dismissal
	private final Rect notifPanelBounds = new Rect();
	private final List<NotifItem> notificationItems = new ArrayList<>();
	// Separate from the persisted seen timestamp so toasts and inbox read-state do not fight each
	// other; -1 means not yet initialised this session
	private static long notifToastMarker = -1L;
	private final Rect backToTopButton = new Rect();
	private final Rect sortButton = new Rect();
	private final Rect downloadFilterButton = new Rect();
	private final Dropdown sortDropdown = new Dropdown();
	private final Dropdown downloadFilterDropdown = new Dropdown();
	private final Rect clearFiltersButton = new Rect();
	// 0 = all, 1 = only downloaded, 2 = not yet downloaded
	private int downloadFilter;
	private final Rect followingFilterButton = new Rect();
	private boolean followingFilter;
	// In-memory only, so the history view resets on a Minecraft restart
	private final Rect historyButton = new Rect();
	private boolean historyView;
	// Guarded by generatingThumbnail so a second click cannot queue a duplicate render
	private final Rect generateThumbnailButton = new Rect();
	private boolean generatingThumbnail;
	// Swapped whole from the folder scan, which runs off-thread; the newest scan wins
	private volatile Set<String> downloadedNames = Set.of();
	private int downloadScan;
	// isDownloadStale hashes the local file, far too costly per frame, so the answer is kept for the
	// session and dropped with downloadedNames when the folder is rescanned
	private final Map<String, Boolean> staleCache = new HashMap<>();
	private final Rect retryButton = new Rect();
	private final Rect offlineRefresh = new Rect();
	private final MapartTab mapartTab = new MapartTab(this);
	private final List<Rect> chipRects = new ArrayList<>();
	private final List<Category> chipOrder = new ArrayList<>();
	public final List<Rect> railRects = new ArrayList<>();
	private int railLinksLaid;
	// Reuse pool for the premium tab's cards; premiumCardEntries tracks which are live this frame
	private final List<Rect> premiumCardRects = new ArrayList<>();
	private final List<Premium.Entry> premiumCardEntries = new ArrayList<>();

	private final Rect unlockButton = new Rect();
	private final Rect signOutButton = new Rect();
	// Two-step confirm, because Sign out sits right next to "+ New post". 0 means not armed
	private long signOutConfirmAt = 0L;
	private static final long SIGN_OUT_CONFIRM_MS = 3000L;
	private final Rect formCategoryButton = new Rect();
	private final Rect formImagePrev = new Rect();
	private final Rect formImageNext = new Rect();
	private final Rect formImageRemove = new Rect();
	private final Rect formThumbnailButton = new Rect();
	private int formThumbnailIndex;
	private final Rect uploadPicturesButton = new Rect();
	private final Rect uploadSchematicButton = new Rect();
	private @Nullable Path formSchematic;
	private final List<Path> formPictures = new ArrayList<>();
	private int formPictureStart = -1;
	private int formPicturePreview;
	private final Rect postButton = new Rect();
	private Category formCategory = Category.FARMS;
	private String formStatus = "";
	private boolean uploading;
	private long uploadStartedAt;
	private long uploadFileSize;
	private int formSizeX;
	private int formSizeY;
	private int formSizeZ;
	private int formBlockCount;
	// A background parse checks this before applying, in case the file was swapped or the form left
	private long schematicParseSeq;
	private boolean uploadFormOpen;

	// Most recently opened first, in-memory
	private static final List<String> RECENT_VIEWED = new ArrayList<>();
	private static final int RECENT_VIEWED_MAX = 24;

	// Cards in this set show a "Viewed" badge explaining why they sit above the sorted order
	private final Set<String> viewedFrontRow = new HashSet<>();

	// Captured once per launch from the stored last-visit time, which is then advanced to now
	private static long newSinceCutoff = -1L;

	// Kept across a menu close so a half-filled post survives; cleared on restart or a successful upload
	private static boolean draftSaved;
	private static boolean draftFormOpen;
	// A reinstalled or second-PC client folds server-side like and follow state back in once per launch
	private static boolean accountSyncDone;
	private static String draftTitle = "";
	private static String draftThumbnail = "";
	private static String draftDesigner = "";
	private static String draftDescription = "";
	private static @Nullable Category draftCategory;
	private static @Nullable Path draftSchematic;
	private static final List<Path> draftPictures = new ArrayList<>();

	private boolean myStatsLoading;
	public boolean myStatsLoaded;
	// Revisiting the Upload tab reuses the cached stats instead of flashing a skeleton
	private long myStatsLoadedAt;
	private static final long MY_STATS_STALE_MS = 45_000L;
	private static long lastPremiumPull;
	private static long lastCosmeticsPull;
	private int myPostsCount;
	private int myViews;
	private int myDownloads;
	private int myLikes;
	private int myFollowers;
	private double myRating;
	private int myRatingCount;
	private String[] myDays = new String[0];
	private int[] myViewSeries = new int[0];
	private int[] myDownloadSeries = new int[0];
	private int[] myLikeSeries = new int[0];
	private int[] myStarSeries = new int[0];
	public final List<SchematicEntry> myPosts = new ArrayList<>();
	// Shown with an "In review" badge so an upload never looks like it vanished
	private final Set<String> myPendingIds = new HashSet<>();
	private record ClaimItem(String postId, String title, String status)
	{
	}

	private List<ClaimItem> myClaims = List.of();
	private boolean myClaimsLoading;
	private boolean myClaimsLoaded;
	private boolean myClaimsFailed;
	// Post id to daily views/downloads/likes series
	private final Map<String, int[][]> myPostSeries = new HashMap<>();
	// When set, the graph shows just this post instead of the totals
	private @Nullable String selectedDashPost;
	private final List<Rect> myPostCardRects = new ArrayList<>();
	private final List<String> myPostCardIds = new ArrayList<>();
	private final List<Rect> myPostEditRects = new ArrayList<>();
	// The *Rects lists above only ever hold instances from these pools
	private final List<Rect> myPostCardPool = new ArrayList<>();
	private final List<Rect> myPostEditPool = new ArrayList<>();
	private final Rect dashUploadButton = new Rect();
	private final Rect statsRetryButton = new Rect();
	private final Rect uploadBackButton = new Rect();
	private float dashScroll;
	private float dashMaxScroll;
	private int dashViewportTop;
	private int dashViewportBottom;
	private Category myFilter = Category.ALL;
	private final List<Rect> myFilterChips = new ArrayList<>();
	private boolean myStatsOk;
	private boolean myStatsFailed;
	private int graphMode;
	private long graphAnimStart;
	private final Rect legendViewsRect = new Rect();
	private final Rect legendDownloadsRect = new Rect();
	private final Rect legendLikesRect = new Rect();
	private final Rect legendStarsRect = new Rect();

	// Glyph x offsets by title text, so drawGradientTitle skips a per-character font.width() every
	// frame; cleared on resize to pick up a font change
	private final Map<String, int[]> titleLayoutCache = new HashMap<>();
	// Clipped and formatted card strings by entry id, valid for one entry instance and card width, so a
	// visible card does not re-clip its name and re-format its counts every frame
	private record CardText(SchematicEntry entry, int width, String tag, String name, String downloads,
			int downloadsWidth, Component credit)
	{
	}

	private final Map<String, CardText> cardTextCache = new HashMap<>();
	private static final int CARD_TEXT_CACHE_MAX = 512;
	// Download.safeName + toLowerCase per title, since isDownloaded runs per visible card per frame
	private final Map<String, String> downloadedKeyCache = new HashMap<>();
	private final Rect heartRectPool = new Rect();
	// Shared instance: trashRect()'s value is always consumed immediately
	private final Rect trashRectPool = new Rect();
	// Shared instances: renderUpload runs every frame and Buttons.mock only reads them
	private final Rect previewFollowRect = new Rect();
	private final Rect previewCloseRect = new Rect();
	private final Rect previewSaveRect = new Rect();
	private final Rect previewDownloadRect = new Rect();
	private final Rect previewPreview3dRect = new Rect();

	// Both are written by the screen-level download bookkeeping and read by the detail card
	public boolean detailDownloadLocked;
	public String status = "";

	private final Rect soundsToggle = new Rect();
	private final Rect modTagsToggle = new Rect();
	private final Rect ownNametagToggle = new Rect();
	private final Rect volumeSliderTrack = new Rect();
	private boolean draggingVolume;
	private final Rect fovSliderTrack = new Rect();
	private boolean draggingFov;
	private final Rect creatorNotificationsToggle = new Rect();
	private final Rect overwriteToggle = new Rect();
	private final Rect changeFolderButton = new Rect();
	private final Rect openFolderButton = new Rect();
	private final Rect resetFolderButton = new Rect();
	private final Rect gridDensityButton = new Rect();
	private final Rect clearCacheButton = new Rect();
	private final Rect toastsToggle = new Rect();
	private final Rect notificationsToggle = new Rect();
	private final Rect termsButton = new Rect();
	private final Rect usageDataToggle = new Rect();

	private final Rect cosmeticOffButton = new Rect();
	private final Rect cosmeticGradientButton = new Rect();
	private final Rect cosmeticBuyButton = new Rect();
	private final Rect cosmeticRefundButton = new Rect();
	private final Rect cosmeticPresetBuyButton = new Rect();
	private final Rect cosmeticPresetWearButton = new Rect();
	private final ColorPicker cosmeticPicker = new ColorPicker();
	private final Rect[] cosmeticSlotRects = newRects(PALETTE_SLOTS);
	// The owned colour drawn in each palette slot, so a click maps back to the id the server knows
	private final int[] cosmeticSlotIds = new int[PALETTE_SLOTS];
	private final Rect[] cosmeticGradientRects = newRects(Cosmetics.GRADIENT_SLOTS);
	private final List<Rect> cosmeticPresetRects = new ArrayList<>();
	private final Rect cosmeticTagBuyButton = new Rect();
	// One chip per owned or shop tag, laid out fresh each frame from whatever the server sent
	private final List<Rect> cosmeticTagRects = new ArrayList<>();
	private final List<Integer> cosmeticTagIds = new ArrayList<>();
	private final Rect[] cosmeticEffectCards = newRects(EFFECT_IDS.length);
	private final Rect[] cosmeticEffectButtons = newRects(EFFECT_IDS.length);
	// The effect card under the pointer, played on the preview before it is bought
	private int cosmeticEffectHover = -1;
	private int cosmeticPreviewEffect = -1;
	// The picker keeps its own HSV so hue survives dragging saturation or value to zero
	private int cosmeticPickColor = Theme.ACCENT & 0xFFFFFF;
	// The gradient slot the pointer picked up; a release on the same slot is a click, not a reorder
	private int cosmeticGradientDrag = -1;
	private int cosmeticDragX;
	private int cosmeticDragY;
	// Buy only offers itself once the player has chosen a colour
	private boolean cosmeticPicked;
	private int cosmeticSelected = Cosmetics.EMPTY;
	private int cosmeticPreviewPreset = -1;
	private int cosmeticPreviewTag = CosmeticTags.NONE;
	// Armed by a shop tag or preset click; the frame after lays out its Buy button and glides down to it
	private boolean cosmeticRevealBuy;
	private float cosmeticScrollFrom;
	private float cosmeticScrollTarget = -1.0F;
	private long cosmeticScrollStartedAt;
	private int cosmeticTagRowBottom;
	// Set while the field is rewritten from the picker, so the responder ignores its own write
	private boolean cosmeticSyncingHex;
	private EditBox cosmeticHexBox;

	private @Nullable RemoteContent.Announcement bannerAnnouncement;
	private @Nullable String bannerId;
	private long bannerAnimStart;
	private boolean bannerEntering;
	private boolean bannerActive;
	private final Rect bannerClose = new Rect();
	// Static, so reopening the menu resumes the same schedule rather than polling again immediately
	private static long lastContentPoll;
	private static long lastNotifPoll;
	private static long lastShardPoll;
	// A toast action outlives the screen that saw it pushed, so it asks whichever screen is live to open the panel
	private static boolean shardPanelRequested;
	private int lastPartnerCount = -1;
	private final List<Rect> railLinkRects = new ArrayList<>();
	private final List<Rect> partnerRects = new ArrayList<>();
	// The *Rects lists above only ever hold instances from these pools
	private final List<Rect> railLinkPool = new ArrayList<>();
	private final List<Rect> partnerPool = new ArrayList<>();

	// Local, user-made groups of posts shown in the Saved tab
	public @Nullable String activeCollection;
	private @Nullable String addingToCollection;
	private final List<Rect> collectionChips = new ArrayList<>();
	private final Rect newCollectionChip = new Rect();
	private final Rect addPostsChip = new Rect();
	private final Rect loadCodeChip = new Rect();
	private final Rect generateCodeChip = new Rect();
	private final Rect downloadAllChip = new Rect();
	public final BatchDownload batchDownload = new BatchDownload(this);
	public final CardMenu cardMenu = new CardMenu(this);
	private final Rect menuGlyphRectPool = new Rect();
	public @Nullable String sharedCode;   // generated share code for the active collection
	private boolean sharingCode;
	private long codeCopiedAt;             // shows "Copied!" on the chip briefly after copying

	private final Rect addModeDone = new Rect();

	private @Nullable String profilePoster;
	private @Nullable String profileIgn;
	private int[] profileStops = SchematicEntry.NO_STOPS;
	// The header name in the creator's own gradient, rebuilt only when the creator fetch lands or the width changes
	private Component profileName = Component.empty();
	private int profileNameWidth = -1;
	private int profileFollowers = -1;
	private int profilePosts = -1;
	private int profileDownloads = -1;
	private final Rect profileBack = new Rect();
	private final Rect profileFollow = new Rect();

	// Each owns its own flag, rects and text; this screen only routes render, clicks and keys to them
	public final ErrorModal errorModal = new ErrorModal(this);
	public final TutorialModal tutorialModal = new TutorialModal(this);
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
	public final PremiumBuyModal premiumBuyModal = new PremiumBuyModal(this);
	public final ShardPanel shardPanel = new ShardPanel(this);
	public final DetailView detailView = new DetailView(this);
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
		this.cardTextCache.clear();

		this.page = sessionPage == Page.STAFF && (this.staff == null || !this.staff.available())
				? Page.BROWSE : sessionPage;
		this.sort = sessionSort;
		this.activeTags.clear();
		this.activeTags.addAll(sessionTags);
		this.downloadFilter = sessionDownloadFilter;

		this.layoutContent();

		int searchWidth = this.searchWidth();
		int searchX = this.contentX + (this.contentWidth - searchWidth) / 2;
		int searchY = (TOP_BAR_HEIGHT - 16) / 2;

		this.searchBox = this.textField(searchX + 6, searchY + 4, searchWidth - 12, "Search", this.query);
		this.searchBox.setResponder(value -> {
			this.query = value;
			// Suggestions and the chip row are cheap enough to keep responsive on every keystroke;
			// only the refilter is debounced
			this.recomputeSearchSuggestions();
			this.layoutChips();
			this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
			this.searchRefilterPending = true;
			this.searchRefilterAt = System.currentTimeMillis() + SEARCH_DEBOUNCE_MS;
		});

		int closeMenuW = this.font.width(Theme.bold("Close Menu")) + 14;
		this.closeButton.set(this.contentX + this.contentWidth - closeMenuW, searchY, closeMenuW, 16);
		this.bellButton.set(this.closeButton.x - 6 - 16, searchY, 16, 16);

		this.layoutRail();

		this.buildUploadFields();
		this.buildCosmeticsFields();

		if (this.staff != null)
		{
			this.staff.buildFields();
		}
		this.restoreDraft();
		this.refreshDownloadedNames();

		if (newSinceCutoff < 0L)
		{
			newSinceCutoff = Settings.lastVisitAt();
			Settings.setLastVisitAt(System.currentTimeMillis());
		}

		int avgBoldChar = Math.max(4, this.font.width(Theme.bold("abcdefghijklmnopqrstuvwxyz")) / 26 + 1);
		this.thumbnailBox.setMaxLength(Math.max(10, (this.cardWidth - 12) / avgBoldChar));
		// Deliberately not width-derived like the thumbnail box: a narrow layout must not truncate a draft
		this.titleBox.setMaxLength(TITLE_CHAR_LIMIT);

		this.layoutChips();

		if (!Settings.termsAccepted())
		{
			this.termsModal.open();
		}
		else
		{
			this.tutorialModal.maybeStart();
			this.maybeSyncAccount();
		}

		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.gridBottom = this.height - OUTER_MARGIN;
		this.refilter();

		this.shownCount = Math.max(PAGE_SIZE, Math.min(sessionShown, this.visible.size()));
		this.recomputeScrollBounds();
		this.scroll = Math.min(sessionScroll, this.maxScroll);
	}

	// The Staff page exists only in the staff jar and only for a staff account; every rail pass
	// walks this list rather than Page.values() so the community build never shows the slot
	private Page[] railPages()
	{
		Page[] all = Page.values();

		if (this.staff != null && this.staff.available())
		{
			return all;
		}

		Page[] pages = new Page[all.length - 1];
		int n = 0;

		for (Page page : all)
		{
			if (page != Page.STAFF)
			{
				pages[n++] = page;
			}
		}

		return pages;
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

	public EditBox textField(int x, int y, int width, String hint, String value)
	{
		EditBox box = new EditBox(this.font, x, y, width, 10, Component.literal(hint));
		box.setBordered(false);
		box.setMaxLength(120);
		box.setTextColor(Theme.TEXT);
		box.setHint(Component.literal(hint));
		box.setValue(value);
		this.addWidget(box);
		return box;
	}

	private void buildUploadFields()
	{
		int formWidth = Math.min(this.contentWidth, 300);
		int formX = this.contentX + (this.contentWidth - formWidth) / 2;
		int y = TOP_BAR_HEIGHT + 60;

		this.codeBox = this.textField(formX + 6, y + 4, formWidth - 12, "Access code",
				this.codeBox == null ? "" : this.codeBox.getValue());
		this.titleBox = this.textField(formX + 6, y + 4, formWidth - 12, "Schematic name",
				this.titleBox == null ? "" : this.titleBox.getValue());
		this.thumbnailBox = this.textField(formX + 6, y + 4, formWidth - 12, "Thumbnail name",
				this.thumbnailBox == null ? "" : this.thumbnailBox.getValue());
		this.designerBox = this.textField(formX + 6, y + 4, formWidth - 12, "Designed by",
				this.designerBox == null ? "" : this.designerBox.getValue());
		String uploadDescription = this.descriptionBox == null ? "" : this.descriptionBox.getValue();
		this.descriptionBox = MultiLineEditBox.builder()
				.setPlaceholder(Component.literal("Description"))
				.setTextColor(Theme.TEXT)
				.setTextShadow(false)
				.setShowBackground(false)
				.setShowDecorations(false)
				.build(this.font, 100, DESC_FIELD_HEIGHT, Component.literal("Description"));
		this.descriptionBox.setCharacterLimit(DESC_CHAR_LIMIT);
		this.descriptionBox.setValue(uploadDescription);
		this.addWidget(this.descriptionBox);

		this.editPostModal.buildFields();
	}

	private void buildCosmeticsFields()
	{
		this.cosmeticHexBox = this.textField(0, 0, 70, "RRGGBB", String.format("%06X", this.cosmeticPickColor));
		this.cosmeticHexBox.setMaxLength(6);
		this.cosmeticHexBox.setResponder(this::applyCosmeticHex);
		this.seedPicker();
	}

	// A half-typed hex is not an error; the last valid colour stays until six digits parse
	private void applyCosmeticHex(String value)
	{
		if (this.cosmeticSyncingHex)
		{
			return;
		}

		try
		{
			this.cosmeticPickColor = Integer.parseInt(value.trim(), 16) & 0xFFFFFF;
			this.cosmeticPicked = true;
			this.seedPicker();
		}
		catch (NumberFormatException ignored)
		{
		}
	}

	// Loads the square and bar from the picked colour; kept separate so a drag never re-derives its own hue
	private void seedPicker()
	{
		this.cosmeticPicker.seed(this.cosmeticPickColor);
	}

	private void refreshHexBox()
	{
		if (this.cosmeticHexBox == null)
		{
			return;
		}

		// Re-seeding from this write would round the hue back through 8-bit RGB and lose it at zero
		// saturation or value, snapping the bar to red mid-drag
		this.cosmeticSyncingHex = true;
		this.cosmeticHexBox.setValue(String.format("%06X", this.cosmeticPickColor));
		this.cosmeticSyncingHex = false;
	}

	private static boolean stale(long lastPull)
	{
		return lastPull == 0L || Util.getMillis() - lastPull > MY_STATS_STALE_MS;
	}

	private static Rect[] newRects(int count)
	{
		Rect[] rects = new Rect[count];

		for (int i = 0; i < count; i++)
		{
			rects[i] = new Rect();
		}

		return rects;
	}

	// Screen.addWidget is protected, and the modal package builds boxes of its own
	public void addModalWidget(MultiLineEditBox box)
	{
		this.addWidget(box);
	}

	private void restoreDraft()
	{
		if (!draftSaved)
		{
			return;
		}

		this.fillEditField(this.titleBox, draftTitle);
		this.fillEditField(this.thumbnailBox, draftThumbnail);
		this.fillEditField(this.designerBox, draftDesigner);
		this.descriptionBox.setValue(draftDescription);

		if (draftCategory != null)
		{
			this.formCategory = draftCategory;
		}

		this.formSchematic = draftSchematic;
		this.formPictures.clear();
		this.formPictures.addAll(draftPictures);
		this.uploadFormOpen = draftFormOpen;
	}

	private void saveDraft()
	{
		if (!UploaderAccess.unlocked() || this.titleBox == null)
		{
			return;
		}

		draftTitle = this.titleBox.getValue();
		draftThumbnail = this.thumbnailBox.getValue();
		draftDesigner = this.designerBox.getValue();
		draftDescription = this.descriptionBox.getValue();
		draftCategory = this.formCategory;
		draftSchematic = this.formSchematic;
		draftPictures.clear();
		draftPictures.addAll(this.formPictures);
		draftFormOpen = this.uploadFormOpen;
		draftSaved = true;
	}

	private static void clearDraft()
	{
		draftSaved = false;
		draftFormOpen = false;
		draftTitle = "";
		draftThumbnail = "";
		draftDesigner = "";
		draftDescription = "";
		draftCategory = null;
		draftSchematic = null;
		draftPictures.clear();
	}

	public int searchWidth()
	{
		return Math.max(80, Math.min(200, this.contentWidth - 260));
	}

	private static int columnsFor(int width)
	{
		if (width < 340)
		{
			return 2;
		}

		if (width < 500)
		{
			return 3;
		}

		if (width < 660)
		{
			return 4;
		}

		return 5;
	}

	public static int imageHeight(int cardWidth)
	{
		return Math.round(cardWidth * 9.0F / 16.0F);
	}

	public void layoutChips()
	{
		this.chipRects.clear();
		this.chipOrder.clear();

		if (this.profilePoster != null)
		{
			this.chipRowHeight = 128;
			this.sortButton.set(0, 0, 0, 0);
			return;
		}

		if (this.page != Page.BROWSE && this.page != Page.SAVED)
		{
			this.chipRowHeight = 26;
			return;
		}

		if (this.page == Page.SAVED)
		{
			this.layoutCollectionChips();
			return;
		}

		int sortWidth = Dropdown.width(this.font, SORT_CAPTION, SORT_LABELS);
		int filterWidth = Dropdown.width(this.font, DOWNLOAD_FILTER_CAPTION, DOWNLOAD_FILTER_LABELS);
		int followWidth = this.font.width(Theme.bold(this.followingChipLabel())) + 12;
		int historyWidth = this.font.width(Theme.bold("History")) + 12;

		boolean showClear = this.filtersActive();
		int clearWidth = showClear ? this.font.width(Theme.bold("Clear filters")) + 12 : 0;
		int firstRowLimit = this.contentX + this.contentWidth - sortWidth - 6 - filterWidth
				- 6 - followWidth - 6 - historyWidth - 18;
		int limit = this.contentX + this.contentWidth;

		int x = this.contentX;
		int row = 0;

		for (Category value : Category.values())
		{
			int width = this.font.width(Theme.bold(value.label())) + 12;
			int rowLimit = row == 0 ? firstRowLimit : limit;

			if (x + width > rowLimit && x > this.contentX)
			{
				row++;
				x = this.contentX;
			}

			Rect rect = new Rect();
			rect.set(x, TOP_BAR_HEIGHT + 6 + row * (CHIP_HEIGHT + CHIP_GAP), width, CHIP_HEIGHT);
			this.chipRects.add(rect);
			this.chipOrder.add(value);
			x += width + CHIP_GAP;
		}

		// Clear filters sits on its own second line, so an active filter forces two rows
		int lastRow = showClear ? Math.max(row, 1) : row;
		this.chipRowHeight = 6 + (lastRow + 1) * CHIP_HEIGHT + lastRow * CHIP_GAP + 6;

		if (this.batchDownload.isActive())
		{
			this.chipRowHeight += BatchDownload.STRIP_HEIGHT;
		}

		if (Catalogue.isStale())
		{
			this.chipRowHeight += OFFLINE_BANNER_HEIGHT;
		}

		this.sortButton.set(this.contentX + this.contentWidth - sortWidth, TOP_BAR_HEIGHT + 6, sortWidth, CHIP_HEIGHT);
		this.downloadFilterButton.set(this.sortButton.x - 6 - filterWidth, TOP_BAR_HEIGHT + 6, filterWidth, CHIP_HEIGHT);
		this.followingFilterButton.set(this.downloadFilterButton.x - 6 - followWidth, TOP_BAR_HEIGHT + 6, followWidth, CHIP_HEIGHT);
		this.historyButton.set(this.followingFilterButton.x - 6 - historyWidth, TOP_BAR_HEIGHT + 6, historyWidth, CHIP_HEIGHT);

		if (showClear)
		{
			int clearY = TOP_BAR_HEIGHT + 6 + (CHIP_HEIGHT + CHIP_GAP);
			this.clearFiltersButton.set(this.sortButton.x + this.sortButton.width - clearWidth, clearY,
					clearWidth, CHIP_HEIGHT);
		}
		else
		{
			this.clearFiltersButton.set(0, 0, 0, 0);
		}
	}

	private void layoutCollectionChips()
	{
		this.collectionChips.clear();

		int sortWidth = Dropdown.width(this.font, SORT_CAPTION, SORT_LABELS);
		int filterWidth = Dropdown.width(this.font, DOWNLOAD_FILTER_CAPTION, DOWNLOAD_FILTER_LABELS);
		int firstRowLimit = this.contentX + this.contentWidth - sortWidth - 6 - filterWidth - 8;
		int limit = this.contentX + this.contentWidth;
		int x = this.contentX;
		int[] row = {0};

		List<String> labels = new ArrayList<>();
		labels.add("All saved");
		labels.addAll(CollectionStore.names());

		for (int idx = 0; idx < labels.size(); idx++)
		{
			int width = this.font.width(Theme.bold(labels.get(idx))) + 12;

			// Named collections carry a trash icon on the right
			if (idx > 0)
			{
				width += TRASH_CELL_WIDTH;
			}

			x = this.wrapChip(x, width, firstRowLimit, limit, row);
			Rect rect = new Rect();
			rect.set(x, TOP_BAR_HEIGHT + 6 + row[0] * (CHIP_HEIGHT + CHIP_GAP), width, CHIP_HEIGHT);
			this.collectionChips.add(rect);
			x += width + CHIP_GAP;
		}

		int newWidth = this.font.width(Theme.bold("+ New")) + 12;
		x = this.wrapChip(x, newWidth, firstRowLimit, limit, row);
		this.newCollectionChip.set(x, TOP_BAR_HEIGHT + 6 + row[0] * (CHIP_HEIGHT + CHIP_GAP), newWidth, CHIP_HEIGHT);
		x += newWidth + CHIP_GAP;

		if (this.activeCollection != null)
		{
			int addWidth = this.font.width(Theme.bold("+ Add posts")) + 12;
			x = this.wrapChip(x, addWidth, firstRowLimit, limit, row);
			this.addPostsChip.set(x, TOP_BAR_HEIGHT + 6 + row[0] * (CHIP_HEIGHT + CHIP_GAP), addWidth, CHIP_HEIGHT);
			x += addWidth + CHIP_GAP;

			String genLabel = this.generateCodeLabel();
			int genTextWidth = this.font.width(Theme.bold(genLabel));

			// Size to the wider of "Copied!" and the code so the chip does not resize when one lapses
			if (this.sharedCode != null)
			{
				genTextWidth = Math.max(genTextWidth,
						Math.max(this.font.width(Theme.bold("Copied!")), this.font.width(Theme.bold(this.sharedCode))));
			}

			int genWidth = genTextWidth + 12;
			x = this.wrapChip(x, genWidth, firstRowLimit, limit, row);
			this.generateCodeChip.set(x, TOP_BAR_HEIGHT + 6 + row[0] * (CHIP_HEIGHT + CHIP_GAP), genWidth, CHIP_HEIGHT);
			x += genWidth + CHIP_GAP;
		}
		else
		{
			this.addPostsChip.set(0, 0, 0, 0);
			this.generateCodeChip.set(0, 0, 0, 0);
		}

		int dlWidth = this.font.width(Theme.bold("Download all")) + 12;
		x = this.wrapChip(x, dlWidth, firstRowLimit, limit, row);
		this.downloadAllChip.set(x, TOP_BAR_HEIGHT + 6 + row[0] * (CHIP_HEIGHT + CHIP_GAP), dlWidth, CHIP_HEIGHT);
		x += dlWidth + CHIP_GAP;

		int loadWidth = this.font.width(Theme.bold("Load code")) + 12;
		x = this.wrapChip(x, loadWidth, firstRowLimit, limit, row);
		this.loadCodeChip.set(x, TOP_BAR_HEIGHT + 6 + row[0] * (CHIP_HEIGHT + CHIP_GAP), loadWidth, CHIP_HEIGHT);
		x += loadWidth + CHIP_GAP;

		this.chipRowHeight = 6 + (row[0] + 1) * CHIP_HEIGHT + row[0] * CHIP_GAP + 6;

		if (this.batchDownload.isActive())
		{
			this.chipRowHeight += BatchDownload.STRIP_HEIGHT;
		}

		this.sortButton.set(this.contentX + this.contentWidth - sortWidth, TOP_BAR_HEIGHT + 6, sortWidth, CHIP_HEIGHT);
		this.downloadFilterButton.set(this.sortButton.x - 6 - filterWidth, TOP_BAR_HEIGHT + 6, filterWidth, CHIP_HEIGHT);
	}

	// Chip-row geometry feeds gridTop, so anything that adds or drops a row re-runs both
	public void refreshChipRow()
	{
		this.layoutChips();
		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.recomputeScrollBounds();
	}

	private String generateCodeLabel()
	{
		if (this.sharedCode != null)
		{
			return System.currentTimeMillis() - this.codeCopiedAt < 1400L ? "Copied!" : this.sharedCode;
		}

		return this.sharingCode ? "..." : "Generate code";
	}

	private int wrapChip(int x, int width, int firstRowLimit, int limit, int[] row)
	{
		int rowLimit = row[0] == 0 ? firstRowLimit : limit;

		if (x + width > rowLimit && x > this.contentX)
		{
			row[0]++;
			return this.contentX;
		}

		return x;
	}

	private void flushSearchDebounce()
	{
		if (this.searchRefilterPending)
		{
			this.refilter();
		}
	}

	public void refilter()
	{
		// Any refilter satisfies a pending debounce
		this.searchRefilterPending = false;
		this.visible.clear();
		String needle = this.query.trim().toLowerCase(Locale.ROOT);

		String filterKey = this.page + "|" + this.activeTags + "|" + this.sort + "|" + needle + "|"
				+ this.downloadFilter + "|" + this.profilePoster + "|" + this.activeCollection
				+ "|" + this.followingFilter + "|" + this.historyView;
		boolean sameFilter = filterKey.equals(this.lastFilterKey);
		int prevShown = this.shownCount;

		for (SchematicEntry entry : Catalogue.posts())
		{
			if (this.profilePoster != null)
			{
				if (!entry.poster().equals(this.profilePoster))
				{
					continue;
				}
			}
			else if (this.page == Page.SAVED)
			{
				if (this.activeCollection != null)
				{
					if (!CollectionStore.contains(this.activeCollection, entry.id()))
					{
						continue;
					}
				}
				else if (!isSaved(entry))
				{
					continue;
				}
			}

			if (this.page != Page.SAVED && !this.activeTags.isEmpty() && !this.activeTags.contains(entry.category()))
			{
				continue;
			}

			// The profilePoster guard keeps a creator's own profile grid from being hidden
			if (this.followingFilter && this.page == Page.BROWSE && this.profilePoster == null
					&& !Follows.isFollowing(entry.poster()))
			{
				continue;
			}

			if (this.historyView && this.page == Page.BROWSE && this.profilePoster == null
					&& !RECENT_VIEWED.contains(entry.id()))
			{
				continue;
			}

			if (!needle.isEmpty()
					&& !entry.title().toLowerCase(Locale.ROOT).contains(needle)
					&& !entry.poster().toLowerCase(Locale.ROOT).contains(needle)
					&& !entry.designer().toLowerCase(Locale.ROOT).contains(needle))
			{
				continue;
			}

			// A profile grid is also page BROWSE, so the profilePoster guard keeps its posts visible
			if (this.downloadFilter != 0 && this.profilePoster == null)
			{
				boolean owned = this.isDownloaded(entry);

				if (this.downloadFilter == 1 && !owned)
				{
					continue;
				}

				if (this.downloadFilter == 2 && owned)
				{
					continue;
				}
			}

			this.visible.add(entry);
		}

		Comparator<SchematicEntry> comparator;

		// On Saved, "Newest" means most recently added to Saved, not the post's upload date
		if (this.page == Page.SAVED && this.sort == Catalogue.Sort.NEWEST)
		{
			List<String> order = this.activeCollection != null
					? new ArrayList<>(CollectionStore.postIds(this.activeCollection))
					: Bookmarks.savedOrder();
			Map<String, Integer> rank = new HashMap<>();

			for (int i = 0; i < order.size(); i++)
			{
				rank.put(order.get(i), i);
			}

			comparator = Comparator.comparingInt((SchematicEntry e) -> rank.getOrDefault(e.id(), -1)).reversed();
		}
		else
		{
			comparator = switch (this.sort)
			{
				case TRENDING -> Comparator.comparingDouble(SchematicEntry::trendScore).reversed()
						.thenComparing(Comparator.comparingLong(SchematicEntry::postedAt).reversed());
				case NEWEST -> Comparator.comparingLong(SchematicEntry::postedAt).reversed();
				case DOWNLOADS -> Comparator.comparingInt(SchematicEntry::downloads).reversed();
				case LIKES -> Comparator.comparingInt(IndexScreen::likesOf).reversed();
				case RATED -> Comparator.comparingInt((SchematicEntry e) -> e.starCount() >= RATED_MIN_RATINGS ? 0 : 1)
						.thenComparing(Comparator.comparingDouble(SchematicEntry::starAvg).reversed())
						.thenComparing(Comparator.comparingInt(SchematicEntry::starCount).reversed());
			};
		}

		this.visible.sort(comparator);
		this.viewedFrontRow.clear();

		// Overrides the sort above so the grid reads as a view-history timeline
		if (this.historyView && this.page == Page.BROWSE && this.profilePoster == null)
		{
			Map<String, Integer> viewedRank = new HashMap<>();

			for (int i = 0; i < RECENT_VIEWED.size(); i++)
			{
				viewedRank.put(RECENT_VIEWED.get(i), i);
			}

			this.visible.sort(Comparator.comparingInt(e -> viewedRank.getOrDefault(e.id(), Integer.MAX_VALUE)));
		}

		// Only on an unfiltered browse view: the top row becomes the most recently viewed posts
		if (this.page == Page.BROWSE && this.profilePoster == null && this.activeTags.isEmpty()
				&& needle.isEmpty() && this.downloadFilter == 0 && !this.historyView && !this.followingFilter
				&& !RECENT_VIEWED.isEmpty())
		{
			int topRow = Math.max(1, this.columns);
			List<SchematicEntry> front = new ArrayList<>();

			for (String id : RECENT_VIEWED)
			{
				if (front.size() >= topRow)
				{
					break;
				}

				for (int i = 0; i < this.visible.size(); i++)
				{
					if (this.visible.get(i).id().equals(id))
					{
						front.add(this.visible.remove(i));
						this.viewedFrontRow.add(id);
						break;
					}
				}
			}

			this.visible.addAll(0, front);
		}

		if (sameFilter)
		{
			// A background refresh keeps the card count, and the clamp in recomputeScrollBounds keeps
			// the scroll offset, so a scrolled user is not yanked upward
			this.shownCount = Math.max(PAGE_SIZE, Math.min(prevShown, Math.max(PAGE_SIZE, this.visible.size())));
		}
		else
		{
			this.lastFilterKey = filterKey;
			this.shownCount = PAGE_SIZE;
		}

		this.focusedCard = -1;
		this.recomputeScrollBounds();
	}

	private void scrollToFocused()
	{
		if (this.focusedCard < 0)
		{
			return;
		}

		int rowHeight = this.cardHeight + GUTTER;
		int cardTop = (this.focusedCard / this.columns) * rowHeight;
		int cardBottom = cardTop + this.cardHeight;
		int viewTop = Math.round(this.scroll);
		int viewHeight = this.gridBottom - this.gridTop;

		if (cardTop < viewTop)
		{
			this.scroll = cardTop;
		}
		else if (cardBottom > viewTop + viewHeight)
		{
			this.scroll = cardBottom - viewHeight;
		}

		this.scroll = Math.max(0.0F, Math.min(this.scroll, this.maxScroll));
	}

	private boolean handleGridKey(int key)
	{
		int shown = this.shownCap();

		if (shown <= 0)
		{
			return false;
		}

		if (key == 257 || key == 335)
		{
			if (this.focusedCard >= 0 && this.focusedCard < this.visible.size())
			{
				this.openDetail(this.visible.get(this.focusedCard));
			}
			else
			{
				this.focusedCard = 0;
				this.scrollToFocused();
			}

			return true;
		}

		if (key != 262 && key != 263 && key != 264 && key != 265)
		{
			return false;
		}

		if (this.focusedCard < 0)
		{
			this.focusedCard = 0;
		}
		else if (key == 262)
		{
			this.focusedCard = Math.min(shown - 1, this.focusedCard + 1);
		}
		else if (key == 263)
		{
			this.focusedCard = Math.max(0, this.focusedCard - 1);
		}
		else if (key == 264)
		{
			this.focusedCard = Math.min(shown - 1, this.focusedCard + this.columns);
		}
		else
		{
			this.focusedCard = Math.max(0, this.focusedCard - this.columns);
		}

		this.maybeLoadMore();
		this.scrollToFocused();
		return true;
	}

	private int shownCap()
	{
		return Math.min(this.shownCount, this.visible.size());
	}

	public boolean hasVisiblePosts()
	{
		return !this.visible.isEmpty();
	}

	private void recomputeScrollBounds()
	{
		int shown = this.shownCap();
		int rows = (shown + this.columns - 1) / this.columns;
		int contentHeight = rows * this.cardHeight + Math.max(0, rows - 1) * GUTTER;

		if (shown < this.visible.size())
		{
			contentHeight += LOADING_ROW;
		}

		this.maxScroll = Math.max(0.0F, contentHeight - (this.gridBottom - this.gridTop));
		this.scroll = Math.min(this.scroll, this.maxScroll);
	}

	private void maybeLoadMore()
	{
		if (this.shownCount >= this.visible.size())
		{
			return;
		}

		if (this.scroll >= this.maxScroll - (this.cardHeight + GUTTER))
		{
			this.shownCount = Math.min(this.visible.size(), this.shownCount + PAGE_SIZE);
			this.recomputeScrollBounds();
		}
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

	private @Nullable Identifier thumbnailTexture(SchematicEntry entry)
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
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick)
	{
		ctx.fill(0, 0, this.width, this.height, Theme.BACKDROP);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick)
	{
		ImageStore.uploadPending();
		SchematicPreview.uploadPending();

		this.detailView.tickSpectator();

		// Whichever scrollbar draws below re-records its geometry
		this.scrollbarChannel = SCROLLBAR_NONE;

		if (Catalogue.revision() != this.catalogueRevision)
		{
			this.catalogueRevision = Catalogue.revision();
			this.refilter();
		}

		if (this.searchRefilterPending && System.currentTimeMillis() >= this.searchRefilterAt)
		{
			this.refilter();
		}

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

		this.extractBackground(ctx, mouseX, mouseY, partialTick);

		boolean modalOpen = this.detailView.isOpen();
		boolean overlayOpen = this.blockingOverlayOpen();
		boolean hoverBlocked = this.pageBlocked();
		int hoverX = hoverBlocked ? -1 : mouseX;
		int hoverY = hoverBlocked ? -1 : mouseY;

		if (this.page == Page.UPLOAD)
		{
			this.renderUpload(ctx, hoverX, hoverY, partialTick);
		}
		else if (this.page == Page.PREMIUM)
		{
			this.renderPremium(ctx, hoverX, hoverY);
		}
		else if (this.page == Page.SETTINGS)
		{
			this.renderSettings(ctx, hoverX, hoverY);
		}
		else if (this.page == Page.COSMETICS)
		{
			this.renderCosmetics(ctx, hoverX, hoverY, partialTick);
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
			this.renderGrid(ctx, hoverX, hoverY);
			this.renderScrollbar(ctx);
			this.renderChipRow(ctx, hoverX, hoverY);
			this.renderOfflineBanner(ctx, hoverX, hoverY);
			this.renderAddModeBanner(ctx, hoverX, hoverY);
			this.renderPartnerRail(ctx, hoverX, hoverY);
		}

		this.renderRail(ctx, hoverX, hoverY);
		this.renderTopBar(ctx, hoverX, hoverY);
		this.renderBackToTop(ctx, hoverX, hoverY, overlayOpen);
		this.renderChipDropdownLists(ctx, mouseX, mouseY, overlayOpen);

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

		if (this.hasInbox() && Settings.creatorAlerts() && nowMs - this.lastNotifPoll > 120000L)
		{
			this.lastNotifPoll = nowMs;
			this.pollNotifications();
		}

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

		this.searchBox.setVisible(this.gridPage());

		if (this.gridPage())
		{
			this.searchBox.extractWidgetRenderState(ctx, mouseX, mouseY, partialTick);

			if (!overlayOpen)
			{
				this.renderSearchSuggestions(ctx, mouseX, mouseY);
			}
			else
			{
				this.searchSuggestRects.clear();
			}
		}
		else if (this.searchBox.isFocused())
		{
			this.searchBox.setFocused(false);
		}

		// Above the content, below hard modals; auto-dismisses when the bell itself is gone
		if (this.notifPanelOpen && !modalOpen && this.hasInbox())
		{
			this.renderNotifPanel(ctx, mouseX, mouseY);
		}
		else
		{
			this.notifPanelOpen = false;
		}

		if (modalOpen)
		{
			this.detailView.render(ctx, mouseX, mouseY);

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

	// Parks the cursor at the start so a long value shows from the beginning, not its end
	public void fillEditField(EditBox box, String value)
	{
		box.setValue(value);
		box.setCursorPosition(0);
		box.setHighlightPos(0);
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
				this.refilter();
			});
		}, "schematicindex-account-sync");
		worker.setDaemon(true);
		worker.start();
	}

	private void renderTopBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
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
			boolean focused = this.searchBox.isFocused();
			Theme.roundedRect(ctx, searchX, searchY, searchWidth, 16, Theme.RADIUS_PILL,
					focused ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

			if (focused)
			{
				Theme.roundedOutline(ctx, searchX, searchY, searchWidth, 16, Theme.RADIUS_PILL, Theme.ACCENT);
			}
		}

		this.layoutTopRight((TOP_BAR_HEIGHT - 16) / 2);
		Buttons.pill(ctx, this.font, this.closeButton, "Close Menu", mouseX, mouseY, false);

		if (this.hasInbox())
		{
			this.renderBell(ctx, mouseX, mouseY);
		}

		this.renderShardLabel(ctx, mouseX, mouseY);
	}

	// Follow, like and claim events reach anyone the server can address, by upload code or by session
	private boolean hasInbox()
	{
		return UploaderAccess.unlocked() || McAuth.verified();
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
		this.bellButton.set(this.closeButton.x - 6 - 16, searchY, 16, 16);
	}

	private void renderShardLabel(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (this.shardButton.width == 0)
		{
			return;
		}

		Rect r = this.shardButton;
		boolean hovered = r.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, r.x, r.y, r.width, r.height, Theme.RADIUS_PILL,
				hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

		if (hovered)
		{
			Theme.roundedOutline(ctx, r.x, r.y, r.width, r.height, Theme.RADIUS_PILL, Theme.SHARD);
		}
		else if (Shards.streakAtRisk())
		{
			Theme.roundedOutline(ctx, r.x, r.y, r.width, r.height, Theme.RADIUS_PILL, ShardPanel.pulse(Theme.GOLD));
		}

		Theme.itemScaled(ctx, new ItemStack(Items.AMETHYST_SHARD), r.x + 5, r.y + 3, 0.6F);
		Theme.text(ctx, this.font, Theme.bold(Integer.toString(Shards.displayedBalance())), r.x + 22, r.y + 4,
				Theme.shardTint(Shards.balanceTrend()));
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

	private void renderBell(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		Rect r = this.bellButton;
		boolean hovered = r.contains(mouseX, mouseY);

		Theme.roundedRect(ctx, r.x - 2, r.y, r.width + 4, r.height, Theme.RADIUS_PILL,
				this.notifPanelOpen || hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		ctx.item(new ItemStack(Items.BELL), r.x, r.y);

		int unread = this.unreadNotifications();

		if (unread > 0)
		{
			String label = unread > 9 ? "9+" : Integer.toString(unread);
			int badgeW = this.font.width(label) + 5;
			int badgeX = r.x + r.width - badgeW + 3;
			int badgeY = r.y - 3;
			Theme.roundedRect(ctx, badgeX, badgeY, badgeW, 9, Theme.RADIUS_PILL, Theme.ACCENT);
			Theme.text(ctx, this.font, label, badgeX + 3, badgeY + 1, Theme.ON_ACCENT);
		}
	}

	// Opening marks everything read by advancing the persisted seen timestamp to the newest event
	private void toggleNotifPanel()
	{
		this.notifPanelOpen = !this.notifPanelOpen;
		Theme.click(1.1F);

		if (this.notifPanelOpen)
		{
			long newest = Settings.notificationsSeenAt();

			for (NotifItem item : this.notificationItems)
			{
				newest = Math.max(newest, item.at());
			}

			Settings.setNotificationsSeenAt(newest);
		}
	}

	// font.width only, so no mapping-specific substring helper is needed
	private String trimToWidth(String text, int maxWidth)
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

	private void renderNotifPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		int rowHeight = 24;
		int headerHeight = 18;
		// Eight rows, fewer on a window too short for them, so the "+N more" strip always stays on screen
		int room = this.height - TOP_BAR_HEIGHT - 4 - headerHeight - 20 - OUTER_MARGIN;
		int maxRows = Math.max(1, Math.min(8, room / rowHeight));
		int shown = Math.min(this.notificationItems.size(), maxRows);
		int bodyHeight = this.notificationItems.isEmpty() ? 20 : shown * rowHeight;
		boolean overflow = this.notificationItems.size() > maxRows;
		int moreStrip = overflow ? 12 : 0;

		int panelW = 224;
		int panelH = headerHeight + bodyHeight + moreStrip + 8;
		int panelRight = this.closeButton.x + this.closeButton.width;
		int panelX = Math.max(RAIL_WIDTH + 4, panelRight - panelW);
		int panelY = TOP_BAR_HEIGHT + 4;
		this.notifPanelBounds.set(panelX, panelY, panelW, panelH);

		Theme.roundedRect(ctx, panelX, panelY, panelW, panelH, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, panelX, panelY, panelW, panelH, Theme.RADIUS_CARD, Theme.HAIRLINE);

		Theme.text(ctx, this.font, Theme.bold("Notifications"), panelX + 8, panelY + 5, Theme.TEXT);

		if (this.notificationItems.isEmpty())
		{
			Theme.text(ctx, this.font, "Nothing yet.", panelX + 8, panelY + headerHeight + 4, Theme.TEXT_MUTE);
			return;
		}

		int rowY = panelY + headerHeight;

		for (int i = 0; i < shown; i++)
		{
			NotifItem item = this.notificationItems.get(i);
			int cy = rowY + i * rowHeight;

			if (i > 0)
			{
				ctx.fill(panelX + 6, cy, panelX + panelW - 6, cy + 1, Theme.HAIRLINE);
			}

			String time = relativeTime(item.at());
			int timeWidth = this.font.width(time);
			String line = this.trimToWidth(item.text(), panelW - 20 - timeWidth);
			Theme.text(ctx, this.font, line, panelX + 8, cy + 6, Theme.TEXT);
			Theme.text(ctx, this.font, time, panelX + panelW - 8 - timeWidth, cy + 6, Theme.TEXT_MUTE);
		}

		if (overflow)
		{
			Theme.text(ctx, this.font, "+" + (this.notificationItems.size() - maxRows) + " more",
					panelX + 8, panelY + headerHeight + bodyHeight + 2, Theme.TEXT_MUTE);
		}
	}

	private static final String TITLE_TEXT = "The Schematic Index";
	private static final int[] TITLE_COLORS = {
			0xFFA1A1A1, 0xFFB2B2B2, 0xFFC3C3C3, 0,
			0xFFD4D4D4, 0xFFE5E5E5, 0xFFF6F6F6, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0,
			0xFF1B503B, 0xFF23664B, 0xFF2A7B5B, 0xFF34976F, 0xFF3DB283,
	};

	private void drawGradientTitle(GuiGraphicsExtractor ctx, String text, int startIndex, int x, int y, float scale)
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
				ctx.text(this.font, "§l" + c, offsets[i], 0, TITLE_COLORS[startIndex + i], false);
			}
		}

		ctx.pose().popMatrix();
	}

	private boolean gridPage()
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

		this.columns = Math.max(2, Math.min(7, columnsFor(this.contentWidth) + Settings.gridDensity()));
		this.cardWidth = (this.contentWidth - GUTTER * (this.columns - 1)) / this.columns;
		this.cardHeight = imageHeight(this.cardWidth) + CAPTION_HEIGHT;
	}

	private void relayout()
	{
		this.layoutContent();

		int searchW = this.searchWidth();
		int searchX = this.contentX + (this.contentWidth - searchW) / 2;
		int searchY = (TOP_BAR_HEIGHT - 16) / 2;

		if (this.searchBox != null)
		{
			this.searchBox.setX(searchX + 6);
			this.searchBox.setWidth(searchW - 12);
		}

		int closeMenuW = this.font.width(Theme.bold("Close Menu")) + 14;
		this.closeButton.set(this.contentX + this.contentWidth - closeMenuW, searchY, closeMenuW, 16);
		this.bellButton.set(this.closeButton.x - 6 - 16, searchY, 16, 16);
		this.layoutChips();
		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.refilter();
	}

	private void renderRail(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
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
			ctx.item(pages[i].icon(), iconX, iconY);
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

	private void renderRailLinks(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
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

	private void renderPartnerRail(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
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

	private void renderChipRow(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		ctx.fill(RAIL_WIDTH, TOP_BAR_HEIGHT, this.width, TOP_BAR_HEIGHT + this.chipRowHeight, Theme.BACKDROP);

		if (this.profilePoster != null)
		{
			this.renderProfileHeader(ctx, mouseX, mouseY);
			return;
		}

		if (this.page == Page.SAVED)
		{
			this.renderCollectionChips(ctx, mouseX, mouseY);
			this.renderChipDropdowns(ctx, mouseX, mouseY);
			this.renderBatchStrip(ctx, mouseX, mouseY);
			return;
		}

		for (int i = 0; i < this.chipRects.size(); i++)
		{
			Rect rect = this.chipRects.get(i);
			Category value = this.chipOrder.get(i);
			boolean active = value == Category.ALL ? this.activeTags.isEmpty() : this.activeTags.contains(value);
			boolean hovered = rect.contains(mouseX, mouseY);
			int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);

			float hover = Theme.buttonHover(rect, hovered);
			float scale = Theme.popScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
			Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

			Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);
			Theme.text(ctx, this.font, Theme.bold(value.label()), rect.x + 6,
					rect.y + (CHIP_HEIGHT - this.font.lineHeight) / 2 + 1,
					active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);

			Theme.pop(ctx);
		}

		Buttons.pill(ctx, this.font, this.historyButton, "History", mouseX, mouseY, this.historyView);
		Buttons.pill(ctx, this.font, this.followingFilterButton, this.followingChipLabel(), mouseX, mouseY,
				this.followingFilter);
		this.renderChipDropdowns(ctx, mouseX, mouseY);

		if (this.clearFiltersButton.width > 0)
		{
			this.chipPill(ctx, this.clearFiltersButton, "Clear filters", true, mouseX, mouseY);
		}

		this.renderBatchStrip(ctx, mouseX, mouseY);
	}

	private void renderChipDropdowns(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		this.downloadFilterDropdown.render(ctx, this.font, this.downloadFilterButton, DOWNLOAD_FILTER_LABELS,
				this.downloadFilter, "", DOWNLOAD_FILTER_CAPTION, mouseX, mouseY);
		this.sortDropdown.render(ctx, this.font, this.sortButton, SORT_LABELS, this.sort.ordinal(), "", SORT_CAPTION,
				mouseX, mouseY);
	}

	// Drawn after the rail and top bar, so an open list sits above the grid rather than under the next card row
	private void renderChipDropdownLists(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean overlayOpen)
	{
		if (overlayOpen || !this.gridPage() || this.profilePoster != null)
		{
			this.closeChipDropdowns();
			return;
		}

		this.sortDropdown.renderOpen(ctx, this.font, mouseX, mouseY, this.height);
		this.downloadFilterDropdown.renderOpen(ctx, this.font, mouseX, mouseY, this.height);
	}

	private @Nullable Dropdown openChipDropdown()
	{
		if (this.sortDropdown.isOpen())
		{
			return this.sortDropdown;
		}

		return this.downloadFilterDropdown.isOpen() ? this.downloadFilterDropdown : null;
	}

	private boolean closeChipDropdowns()
	{
		boolean wasOpen = this.openChipDropdown() != null;
		this.sortDropdown.close();
		this.downloadFilterDropdown.close();
		return wasOpen;
	}

	private void choseChipDropdown(Dropdown dropdown, int choice)
	{
		if (choice < 0)
		{
			return;
		}

		if (dropdown == this.sortDropdown)
		{
			this.sort = Catalogue.Sort.values()[choice];
			this.refilter();
			return;
		}

		this.downloadFilter = choice;
		this.refreshDownloadedNames();
		this.scroll = 0.0F;
		this.refilter();
	}

	// Sits above the offline banner's slot, since layoutChips stacks the two strips in that order
	private void renderBatchStrip(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (!this.batchDownload.isActive())
		{
			return;
		}

		int y = TOP_BAR_HEIGHT + this.chipRowHeight - BatchDownload.STRIP_HEIGHT;

		if (this.page == Page.BROWSE && Catalogue.isStale())
		{
			y -= OFFLINE_BANNER_HEIGHT;
		}

		this.batchDownload.render(ctx, this.font, this.contentX, y, this.contentWidth, mouseX, mouseY);
	}

	private void renderCollectionChips(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		List<String> names = CollectionStore.names();

		for (int i = 0; i < this.collectionChips.size(); i++)
		{
			Rect rect = this.collectionChips.get(i);

			if (i == 0)
			{
				this.chipPill(ctx, rect, "All saved", this.activeCollection == null, mouseX, mouseY);
				continue;
			}

			String label = i - 1 < names.size() ? names.get(i - 1) : "";
			this.namedCollectionChip(ctx, rect, label, label.equals(this.activeCollection), mouseX, mouseY);
		}

		this.chipPill(ctx, this.newCollectionChip, "+ New", false, mouseX, mouseY);

		if (this.activeCollection != null)
		{
			this.chipPill(ctx, this.addPostsChip, "+ Add posts", false, mouseX, mouseY);
			// The only chip whose label changes between layouts, so it alone is still clipped on draw
			this.chipPill(ctx, this.generateCodeChip,
					Theme.clipBold(this.font, this.generateCodeLabel(), this.generateCodeChip.width - 8),
					this.sharedCode != null, mouseX, mouseY);
		}

		// Highlighted while a batch runs so the chip reads as a state, not a fresh action
		this.chipPill(ctx, this.downloadAllChip, "Download all", this.batchDownload.isActive(), mouseX, mouseY);
		this.chipPill(ctx, this.loadCodeChip, "Load code", false, mouseX, mouseY);
	}

	// Every chip rect is sized from its own label at layout time, so the label needs no clipping here
	private void chipPill(GuiGraphicsExtractor ctx, Rect rect, String label, boolean active, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.popScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);
		Theme.text(ctx, this.font, Theme.bold(label), rect.x + 6,
				rect.y + (CHIP_HEIGHT - this.font.lineHeight) / 2 + 1, active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);
		Theme.pop(ctx);
	}

	private void namedCollectionChip(GuiGraphicsExtractor ctx, Rect rect, String label, boolean active, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.popScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);
		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);

		Theme.text(ctx, this.font, Theme.bold(label), rect.x + 6,
				rect.y + (CHIP_HEIGHT - this.font.lineHeight) / 2 + 1, active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);

		Rect trash = this.trashRect(rect);
		boolean trashHovered = trash.contains(mouseX, mouseY);
		int trashColor = trashHovered ? 0xFFE05555 : (active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);
		Theme.trashGlyph(ctx, trash.x + (trash.width - Theme.TRASH_GLYPH_WIDTH) / 2,
				trash.y + (trash.height - 8) / 2, trashColor);
		Theme.pop(ctx);
	}

	private Rect trashRect(Rect chip)
	{
		// Shared instance: the caller consumes it before the next trashRect() call
		this.trashRectPool.set(chip.x + chip.width - TRASH_CELL_WIDTH, chip.y, TRASH_CELL_WIDTH, chip.height);
		return this.trashRectPool;
	}

	// Sits in the strip layoutChips reserves; clears itself once a live refresh drops staleness
	private void renderOfflineBanner(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (this.profilePoster != null || this.page != Page.BROWSE || !Catalogue.isStale())
		{
			this.offlineRefresh.set(0, 0, 0, 0);
			return;
		}

		int y = TOP_BAR_HEIGHT + this.chipRowHeight - OFFLINE_BANNER_HEIGHT;
		ctx.fill(RAIL_WIDTH, y, this.width, y + OFFLINE_BANNER_HEIGHT, 0xF0332A12);
		ctx.fill(RAIL_WIDTH, y + OFFLINE_BANNER_HEIGHT - 1, this.width, y + OFFLINE_BANNER_HEIGHT, Theme.HAIRLINE);

		int refreshWidth = this.font.width(Theme.bold("Refresh")) + 12;
		this.offlineRefresh.set(this.contentX + this.contentWidth - refreshWidth, y + 1, refreshWidth,
				OFFLINE_BANNER_HEIGHT - 3);

		String text = "Couldn't reach the server. Showing the last catalogue you loaded.";
		Theme.text(ctx, this.font, Theme.clip(this.font, text, this.contentWidth - refreshWidth - 8),
				this.contentX, y + (OFFLINE_BANNER_HEIGHT - this.font.lineHeight) / 2, Theme.TEXT);
		Buttons.pill(ctx, this.font, this.offlineRefresh, "Refresh", mouseX, mouseY, false);
	}

	private void renderAddModeBanner(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		if (this.addingToCollection == null)
		{
			return;
		}

		int height = 20;
		int y = TOP_BAR_HEIGHT + this.chipRowHeight;
		ctx.fill(RAIL_WIDTH, y, this.width, y + height, Theme.ACCENT);

		String text = "Tap posts to add them to \"" + this.addingToCollection + "\"";
		Theme.text(ctx, this.font, Theme.bold(Theme.clipBold(this.font, text, this.contentWidth - 90)),
				this.contentX, y + (height - this.font.lineHeight) / 2, Theme.ON_ACCENT);

		int doneWidth = this.font.width(Theme.bold("Done")) + 16;
		this.addModeDone.set(this.contentX + this.contentWidth - doneWidth, y + 2, doneWidth, height - 4);
		boolean hovered = this.addModeDone.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, this.addModeDone.x, this.addModeDone.y, doneWidth, height - 4, Theme.RADIUS_PILL,
				hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.text(ctx, this.font, Theme.bold("Done"), this.addModeDone.x + 8, this.addModeDone.y + 2, Theme.TEXT);
	}

	private void renderProfileHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		int pad = 8;
		int y = TOP_BAR_HEIGHT + pad;

		int backWidth = this.font.width(Theme.bold("< Back")) + 12;
		this.profileBack.set(this.contentX, y, backWidth, 13);
		Buttons.pill(ctx, this.font, this.profileBack, "< Back", mouseX, mouseY, false);

		int avatarSize = 54;
		int avatarX = this.contentX;
		int avatarY = y + 18;
		// Only a real IGN resolves to a head, so falling back to the display name yields a Steve
		Identifier avatar = this.profileIgn != null && !this.profileIgn.isBlank()
				? ImageStore.avatar("https://mc-heads.net/avatar/" + Backend.encode(this.profileIgn) + "/64.png")
				: null;

		if (avatar != null)
		{
			Theme.image(ctx, avatar, avatarX, avatarY, avatarSize, avatarSize, 64, 64);
		}
		else
		{
			Theme.roundedRect(ctx, avatarX, avatarY, avatarSize, avatarSize, 6, Theme.SURFACE_ELEVATED);
		}

		int statsX = avatarX + avatarSize + 18;
		int statsWidth = this.contentX + this.contentWidth - statsX;
		int column = statsWidth / 3;
		int statTop = avatarY + avatarSize / 2 - this.font.lineHeight;
		this.profileStat(ctx, statsX + column / 2, statTop,
				this.profilePosts < 0 ? "..." : SchematicEntry.compact(this.profilePosts), "Posts");
		this.profileStat(ctx, statsX + column + column / 2, statTop,
				this.profileFollowers < 0 ? "..." : SchematicEntry.compact(this.profileFollowers), "Followers");
		this.profileStat(ctx, statsX + column * 2 + column / 2, statTop,
				this.profileDownloads < 0 ? "..." : SchematicEntry.compact(this.profileDownloads), "Downloads");

		int nameY = avatarY + avatarSize + 6;
		Theme.textScaled(ctx, this.font, this.profileName(), this.contentX, nameY, PROFILE_NAME_SCALE, Theme.TEXT);

		boolean following = Follows.isFollowing(this.profilePoster);
		String followLabel = following ? "Following" : "Follow";
		int followWidth = Math.min(160, this.contentWidth);
		int followY = nameY + Math.round(this.font.lineHeight * 1.15F) + 6;
		this.profileFollow.set(this.contentX, followY, followWidth, 16);
		Buttons.pill(ctx, this.font, this.profileFollow, followLabel, mouseX, mouseY, !following);
	}

	private void profileStat(GuiGraphicsExtractor ctx, int centerX, int topY, String value, String label)
	{
		int valueWidth = this.font.width(Theme.bold(value));
		Theme.text(ctx, this.font, Theme.bold(value), centerX - valueWidth / 2, topY, Theme.TEXT);
		int labelWidth = this.font.width(label);
		Theme.text(ctx, this.font, label, centerX - labelWidth / 2, topY + this.font.lineHeight + 2, Theme.TEXT_MUTE);
	}

	public void openProfile(String poster)
	{
		this.pushNav();
		this.profilePoster = poster;
		this.profileIgn = null;
		this.profileStops = SchematicEntry.NO_STOPS;
		this.profileNameWidth = -1;
		this.profileFollowers = -1;
		this.profilePosts = -1;
		this.profileDownloads = -1;
		this.detailView.dismiss();
		this.page = Page.BROWSE;
		this.activeTags.clear();
		this.setSearch("");
		this.fetchCreator(poster);
		this.layoutChips();
		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.scroll = 0.0F;
		this.refilter();
	}

	private void closeProfile()
	{
		this.profilePoster = null;
		this.layoutChips();
		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.scroll = 0.0F;
		this.refilter();
	}

	private void pushNav()
	{
		// A copy, not the live set, so later edits do not mutate history
		this.navStack.push(new NavSnapshot(this.page, EnumSet.copyOf(this.activeTags), this.sort, this.query,
				this.downloadFilter, this.profilePoster, this.activeCollection, this.scroll,
				this.shownCount));

		while (this.navStack.size() > 16)
		{
			this.navStack.removeLast();
		}
	}

	// Scroll is applied after the list is rebuilt, so the clamp runs against the real content height
	private void restoreNav(NavSnapshot snap)
	{
		this.page = snap.page();
		this.activeTags.clear();
		this.activeTags.addAll(snap.activeTags());
		this.sort = snap.sort();
		this.setSearch(snap.query());
		this.downloadFilter = snap.downloadFilter();
		this.profilePoster = snap.profilePoster();
		this.activeCollection = snap.activeCollection();
		this.setFocused(null);

		// Both must run before the grid is rebuilt
		if (this.profilePoster != null)
		{
			this.fetchCreator(this.profilePoster);
		}

		this.refreshDownloadedNames();
		this.layoutChips();
		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.refilter();

		// Card count, then bounds, then the scroll offset last
		this.shownCount = Math.max(PAGE_SIZE, Math.min(snap.shownCount(), Math.max(PAGE_SIZE, this.visible.size())));
		this.recomputeScrollBounds();
		this.scroll = Math.max(0.0F, Math.min(snap.scroll(), this.maxScroll));
	}

	// False only at the root, where the caller is free to close the whole screen
	private boolean navigateBack()
	{
		if (this.detailView.isOpen())
		{
			this.detailView.close();
			return true;
		}

		if (this.profilePoster != null)
		{
			if (!this.navStack.isEmpty())
			{
				this.restoreNav(this.navStack.pop());
			}
			else
			{
				this.closeProfile();
			}

			Theme.click(0.9F);
			return true;
		}

		if (this.page == Page.UPLOAD && this.uploadFormOpen)
		{
			this.uploadFormOpen = false;
			this.setFocused(null);
			this.relayout();
			Theme.click(0.9F);
			return true;
		}

		if (!this.navStack.isEmpty())
		{
			this.restoreNav(this.navStack.pop());
			Theme.click(0.9F);
			return true;
		}

		return false;
	}

	// A creator profile reuses BROWSE but is not a filter the Clear chip should reset
	private boolean filtersActive()
	{
		return this.page == Page.BROWSE && this.profilePoster == null
				&& (!this.activeTags.isEmpty() || !this.query.trim().isEmpty() || this.downloadFilter != 0
						|| this.followingFilter || this.historyView);
	}

	private String activeFilterSummary()
	{
		List<String> parts = new ArrayList<>();

		// EnumSet iterates in natural enum order, so the summary is stable
		if (!this.activeTags.isEmpty())
		{
			List<String> tagLabels = new ArrayList<>();

			for (Category tag : this.activeTags)
			{
				tagLabels.add(tag.label());
			}

			parts.add(String.join(", ", tagLabels));
		}

		String q = this.query.trim();

		if (!q.isEmpty())
		{
			parts.add("\"" + q + "\"");
		}

		if (this.downloadFilter != 0)
		{
			parts.add(this.downloadFilterLabel());
		}

		if (this.followingFilter)
		{
			parts.add("Following");
		}

		if (this.historyView)
		{
			parts.add("History");
		}

		return String.join(", ", parts);
	}

	private void clearFilters()
	{
		this.activeTags.clear();
		this.setSearch("");
		this.downloadFilter = 0;
		this.followingFilter = false;
		this.historyView = false;
		this.refreshDownloadedNames();
		this.setFocused(null);
		Theme.click(0.9F);
		this.layoutChips();
		this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
		this.scroll = 0.0F;
		this.refilter();
	}

	private void fetchCreator(String poster)
	{
		Thread worker = new Thread(() -> {
			JsonObject body = Backend.getJson("/creator/" + Backend.encode(poster));

			if (body == null)
			{
				return;
			}

			String ign = Json.stringOf(body, "ign", "");
			int followers = Json.intOf(body, "followers", 0);
			int posts = Json.intOf(body, "posts", 0);
			int downloads = Json.intOf(body, "downloads", 0);
			int[] stops = Backend.parseStops(body, "posterStops");

			Minecraft.getInstance().execute(() -> {
				if (poster.equals(this.profilePoster))
				{
					this.profileIgn = ign;
					this.profileStops = stops;
					this.profileNameWidth = -1;
					this.profileFollowers = followers;
					this.profilePosts = posts;
					this.profileDownloads = downloads;
				}
			});
		}, "schematicindex-creator");
		worker.setDaemon(true);
		worker.start();
	}

	private Component profileName()
	{
		if (this.profileNameWidth == this.contentWidth)
		{
			return this.profileName;
		}

		int room = Math.round(this.contentWidth / PROFILE_NAME_SCALE);
		String name = Theme.clipBold(this.font, this.profilePoster, room);
		this.profileName = ModTags.creator(name, this.profileStops, Style.EMPTY.withBold(true));
		this.profileNameWidth = this.contentWidth;
		return this.profileName;
	}

	private void setSearch(String value)
	{
		this.query = value;

		if (this.searchBox != null)
		{
			this.searchBox.setValue(value);
		}
	}

	private boolean clickCollectionChips(double mouseX, double mouseY)
	{
		List<String> names = CollectionStore.names();

		for (int i = 1; i < this.collectionChips.size(); i++)
		{
			if (this.trashRect(this.collectionChips.get(i)).contains(mouseX, mouseY))
			{
				if (i - 1 < names.size())
				{
					this.deleteCollectionModal.open(names.get(i - 1));
					Theme.click(0.9F);
				}

				return true;
			}
		}

		for (int i = 0; i < this.collectionChips.size(); i++)
		{
			if (this.collectionChips.get(i).contains(mouseX, mouseY))
			{
				this.activeCollection = i == 0 ? null : (i - 1 < names.size() ? names.get(i - 1) : null);
				this.sharedCode = null;
				Theme.click();
				this.scroll = 0.0F;
				this.layoutChips();
				this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
				this.refilter();
				return true;
			}
		}

		if (this.newCollectionChip.contains(mouseX, mouseY))
		{
			Theme.click(1.1F);
			this.nameInputModal.open(null);
			return true;
		}

		if (this.activeCollection != null && this.addPostsChip.contains(mouseX, mouseY))
		{
			this.addingToCollection = this.activeCollection;
			this.page = Page.BROWSE;
			this.activeTags.clear();
			this.setSearch("");
			Theme.click(1.1F);
			this.layoutChips();
			this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight + 20;
			this.scroll = 0.0F;
			this.refilter();
			return true;
		}

		if (this.activeCollection != null && this.generateCodeChip.contains(mouseX, mouseY))
		{
			if (this.sharedCode != null)
			{
				this.copyToClipboard(this.sharedCode);
				this.codeCopiedAt = System.currentTimeMillis();
				this.layoutChips();
				Theme.click(1.2F);
			}
			else if (!this.sharingCode)
			{
				this.generateShareCode();
			}

			return true;
		}

		if (this.downloadAllChip.contains(mouseX, mouseY))
		{
			this.openDownloadAll(this.activeCollection);
			return true;
		}

		if (this.loadCodeChip.contains(mouseX, mouseY))
		{
			this.loadCodeModal.open();
			Theme.click(1.1F);
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

	private void generateShareCode()
	{
		String collection = this.activeCollection;

		if (collection == null)
		{
			return;
		}

		List<String> ids = new ArrayList<>(CollectionStore.postIds(collection));

		if (ids.isEmpty())
		{
			this.status = "Add some posts before sharing this collection.";
			return;
		}

		Theme.click(1.1F);
		this.requireVerified(() -> {
			this.sharingCode = true;
			Thread worker = new Thread(() -> {
				String code = Backend.shareCollection(collection, ids);

				if (code != null)
				{
					Shards.pokeSoon();
				}

				Minecraft.getInstance().execute(() -> {
					this.sharingCode = false;

					if (code != null && collection.equals(this.activeCollection))
					{
						this.sharedCode = code;
						this.layoutChips();
					}
					else if (code == null)
					{
						this.status = "Could not create a code, try again.";
					}
				});
			}, "schematicindex-share");
			worker.setDaemon(true);
			worker.start();
		});
	}

	private void renderPremium(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
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

	private int renderPremiumSection(GuiGraphicsExtractor ctx, String label, Premium.Type type,
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
	private void renderPremiumCard(GuiGraphicsExtractor ctx, Premium.Entry entry, int x, int y, int mouseX, int mouseY)
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
			ctx.item(new ItemStack(Items.AMETHYST_SHARD), iconX, y + 2);
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
	private void drawPremiumTitle(GuiGraphicsExtractor ctx, String title, int x, int y, int width)
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

	private void renderGrid(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		Catalogue.State state = Catalogue.state();

		if (state != Catalogue.State.READY)
		{
			this.renderSkeleton(ctx, state == Catalogue.State.OFFLINE, mouseX, mouseY);
			return;
		}

		this.retryButton.set(0, 0, 0, 0);

		if (this.visible.isEmpty())
		{
			String message = this.page == Page.SAVED && this.downloadFilter == 0
					? "Nothing saved yet - open a post and Save for later"
					: "Nothing matches that filter";
			int centreX = this.contentX + this.contentWidth / 2;
			Theme.text(ctx, this.font, message,
					centreX - this.font.width(message) / 2, this.gridTop + 40, Theme.TEXT_ASH);

			if (this.filtersActive())
			{
				String active = Theme.clip(this.font, "Active: " + this.activeFilterSummary(), this.contentWidth - 16);
				Theme.text(ctx, this.font, active, centreX - this.font.width(active) / 2,
						this.gridTop + 40 + this.font.lineHeight + 6, Theme.TEXT_MUTE);
				String hint = "Use \"Clear filters\" above to reset.";
				Theme.text(ctx, this.font, hint, centreX - this.font.width(hint) / 2,
						this.gridTop + 40 + (this.font.lineHeight + 6) * 2, Theme.TEXT_MUTE);
			}

			return;
		}

		this.maybeLoadMore();
		int shown = this.shownCap();

		ctx.enableScissor(this.contentX, this.gridTop, this.contentX + this.contentWidth, this.gridBottom);

		int rowHeight = this.cardHeight + GUTTER;
		int firstRow = Math.max(0, (int) (this.scroll / rowHeight));
		int lastRow = Math.min((shown - 1) / this.columns,
				(int) ((this.scroll + (this.gridBottom - this.gridTop)) / rowHeight));

		for (int row = firstRow; row <= lastRow; row++)
		{
			for (int column = 0; column < this.columns; column++)
			{
				int index = row * this.columns + column;

				if (index >= shown)
				{
					break;
				}

				int x = this.contentX + column * (this.cardWidth + GUTTER);
				int y = this.gridTop + row * rowHeight - Math.round(this.scroll);
				SchematicEntry card = this.visible.get(index);
				this.renderCard(ctx, card, x, y, mouseX, mouseY);

				if (index == this.focusedCard)
				{
					// Flush to the card, so the grid scissor cannot clip the top edge on the top row
					Theme.roundedOutline(ctx, x, y, this.cardWidth, this.cardHeight,
							Theme.RADIUS_CARD, Theme.ACCENT_BRIGHT);
				}

				if (this.addingToCollection != null && CollectionStore.contains(this.addingToCollection, card.id()))
				{
					Theme.roundedOutline(ctx, x - 1, y - 1, this.cardWidth + 2, this.cardHeight + 2,
							Theme.RADIUS_CARD, Theme.ACCENT);
					int badge = this.font.width(Theme.bold("Added")) + 10;
					Theme.roundedRect(ctx, x + this.cardWidth - badge - 4, y + 4, badge, 12, Theme.RADIUS_PILL, Theme.ACCENT);
					Theme.text(ctx, this.font, Theme.bold("Added"), x + this.cardWidth - badge, y + 6, Theme.ON_ACCENT);
				}
			}
		}

		if (shown < this.visible.size())
		{
			int rows = (shown + this.columns - 1) / this.columns;
			int y = this.gridTop + rows * rowHeight - Math.round(this.scroll) + 4;
			String more = "Loading more...";
			Theme.text(ctx, this.font, more, this.contentX + (this.contentWidth - this.font.width(more)) / 2, y,
					Theme.TEXT_ASH);
		}

		ctx.disableScissor();
	}

	private void renderSkeleton(GuiGraphicsExtractor ctx, boolean offline, int mouseX, int mouseY)
	{
		ctx.enableScissor(this.contentX, this.gridTop, this.contentX + this.contentWidth, this.gridBottom);

		int rowHeight = this.cardHeight + GUTTER;
		int rows = (this.gridBottom - this.gridTop) / rowHeight + 1;
		int imageHeight = imageHeight(this.cardWidth);
		long now = System.currentTimeMillis();

		for (int row = 0; row < rows; row++)
		{
			for (int column = 0; column < this.columns; column++)
			{
				int x = this.contentX + column * (this.cardWidth + GUTTER);
				int y = this.gridTop + row * rowHeight;

				Theme.roundedRect(ctx, x, y, this.cardWidth, this.cardHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
				Theme.roundedRect(ctx, x, y, this.cardWidth, imageHeight, Theme.RADIUS_CARD, Theme.SKELETON);
				Theme.roundedRect(ctx, x + 5, y + imageHeight + 6, this.cardWidth * 2 / 3, 5, 1, Theme.SKELETON);
				Theme.roundedRect(ctx, x + 5, y + imageHeight + 15, this.cardWidth / 3, 5, 1, Theme.SKELETON);

				if (!offline)
				{
					int span = this.cardWidth + 40;
					int offset = (int) ((now / 4L + (long) (row + column) * 40L) % span) - 20;
					int shimmerX = x + offset;
					ctx.enableScissor(Math.max(this.contentX, x), y,
							Math.min(this.contentX + this.contentWidth, x + this.cardWidth), y + imageHeight);
					Theme.roundedRect(ctx, shimmerX, y, 18, imageHeight, 0, Theme.SKELETON_SHINE);
					ctx.disableScissor();
				}
			}
		}

		ctx.disableScissor();

		if (!offline)
		{
			this.retryButton.set(0, 0, 0, 0);
			return;
		}

		ctx.fill(this.contentX, this.gridTop, this.contentX + this.contentWidth, this.gridBottom, 0xCC0F1114);

		String headline = "Can't reach the index";
		String detail = "Check your connection and try again.";
		int centreX = this.contentX + this.contentWidth / 2;
		int centreY = this.gridTop + (this.gridBottom - this.gridTop) / 2;

		Theme.textScaled(ctx, this.font, Theme.bold(headline),
				centreX - this.font.width(Theme.bold(headline)), centreY - 24, 2.0F, Theme.TEXT);
		Theme.text(ctx, this.font, detail, centreX - this.font.width(detail) / 2, centreY - 2, Theme.TEXT_MUTE);

		int retryWidth = this.font.width(Theme.bold("Try again")) + 20;
		this.retryButton.set(centreX - retryWidth / 2, centreY + 12, retryWidth, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.retryButton, "Try again", mouseX, mouseY, true);
	}

	private void renderCard(GuiGraphicsExtractor ctx, SchematicEntry entry, int x, int y, int mouseX, int mouseY)
	{
		boolean hovered = Theme.inside(mouseX, mouseY, x, y, this.cardWidth, this.cardHeight)
				&& mouseY >= this.gridTop && mouseY < this.gridBottom;

		Theme.roundedRect(ctx, x, y, this.cardWidth, this.cardHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);

		int imageHeight = imageHeight(this.cardWidth);
		Identifier texture = this.thumbnailTexture(entry);

		if (texture != null)
		{
			Theme.image(ctx, texture, x, y, this.cardWidth, imageHeight);
		}
		else
		{
			Theme.loadingPlaceholder(ctx, x, y, this.cardWidth, imageHeight);
		}

		CardText text = this.cardText(entry);
		String tag = text.tag();
		int tagWidth = this.font.width(tag) + 8;
		Theme.roundedRect(ctx, x + 4, y + imageHeight - 15, tagWidth, 11, Theme.RADIUS_PILL, 0xCC0F1114);
		Theme.text(ctx, this.font, tag, x + 8, y + imageHeight - 13, Theme.TEXT);

		Rect heart = this.heartRect(x, y, imageHeight);
		Theme.roundedRect(ctx, heart.x - 2, heart.y - 2, HEART_SIZE + 4, HEART_SIZE + 4, Theme.RADIUS_PILL, 0xCC0F1114);
		Theme.heartPopped(ctx, heart.x, heart.y, isLikedBy(entry), popAge(entry));

		int rightBadgeY = y + 4;
		// The corner cell stays free for the menu glyph, so the badges never jump when it appears
		int badgeRight = x + this.cardWidth - 4 - CardMenu.GLYPH_CELL - 3;

		if (isSaved(entry))
		{
			int badge = this.font.width("Saved") + 8;
			int badgeX = badgeRight - badge;
			Theme.roundedRect(ctx, badgeX - 1, rightBadgeY - 1, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, badgeX, rightBadgeY, badge, 11, Theme.RADIUS_PILL, Theme.ACCENT);
			Theme.text(ctx, this.font, "Saved", badgeX + 4, rightBadgeY + 2, Theme.ON_ACCENT);
			rightBadgeY += 15;
		}

		if (this.viewedFrontRow.contains(entry.id()))
		{
			int badge = this.font.width("Viewed") + 8;
			int badgeX = badgeRight - badge;
			Theme.roundedRect(ctx, badgeX - 1, rightBadgeY - 1, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, badgeX, rightBadgeY, badge, 11, Theme.RADIUS_PILL, Theme.SURFACE_ELEVATED);
			Theme.text(ctx, this.font, "Viewed", badgeX + 4, rightBadgeY + 2, Theme.TEXT);
			rightBadgeY += 15;
		}

		// isUpdateAvailable is cached per entry id so this does not re-hash the file every frame
		if (this.isUpdateAvailable(entry))
		{
			int badge = this.font.width("Update") + 8;
			int badgeX = badgeRight - badge;
			Theme.roundedRect(ctx, badgeX - 1, rightBadgeY - 1, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, badgeX, rightBadgeY, badge, 11, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
			Theme.text(ctx, this.font, "Update", badgeX + 4, rightBadgeY + 2, Theme.ON_ACCENT);
		}

		if (newSinceCutoff > 0L && entry.postedAt() > newSinceCutoff && !entry.id().equals("preview"))
		{
			int badge = this.font.width(Theme.bold("New")) + 8;
			Theme.roundedRect(ctx, x + 3, y + 3, badge + 2, 13, Theme.RADIUS_PILL, 0xFF000000);
			Theme.roundedRect(ctx, x + 4, y + 4, badge, 11, Theme.RADIUS_PILL, Theme.ACCENT_BRIGHT);
			Theme.text(ctx, this.font, Theme.bold("New"), x + 8, y + 6, Theme.ON_ACCENT);
		}

		int textX = x + 5;
		Theme.text(ctx, this.font, text.name(), textX, y + imageHeight + 6, Theme.TEXT);

		int metaY = y + imageHeight + 6 + this.font.lineHeight + 2;
		int countX = x + this.cardWidth - 5 - text.downloadsWidth();
		Theme.text(ctx, this.font, text.downloads(), countX, metaY, Theme.TEXT_MUTE);
		Theme.downloadGlyph(ctx, countX - Theme.DOWNLOAD_GLYPH_WIDTH - 3, metaY + 2, Theme.TEXT_MUTE);
		Theme.text(ctx, this.font, text.credit(), textX, metaY, Theme.TEXT_MUTE);

		if (hovered || this.cardMenu.isFor(entry))
		{
			Theme.roundedOutline(ctx, x, y, this.cardWidth, this.cardHeight, Theme.RADIUS_CARD, Theme.ACCENT_BRIGHT);
			int glyphX = x + this.cardWidth - 4 - CardMenu.GLYPH_CELL;
			CardMenu.glyph(ctx, glyphX, y + 4, Theme.inside(mouseX, mouseY, glyphX, y + 4, CardMenu.GLYPH_CELL, 11));
		}
	}

	private CardText cardText(SchematicEntry entry)
	{
		CardText cached = this.cardTextCache.get(entry.id());

		if (cached != null && cached.entry() == entry && cached.width() == this.cardWidth)
		{
			return cached;
		}

		if (this.cardTextCache.size() >= CARD_TEXT_CACHE_MAX)
		{
			this.cardTextCache.clear();
		}

		String tag = Theme.clip(this.font, entry.category().label(), this.cardWidth - 34);
		String name = Theme.bold(Theme.clipBold(this.font, entry.cardName(), this.cardWidth - 10));
		String downloads = entry.downloadsLabel();
		int downloadsWidth = this.font.width(downloads);
		// Mirrors renderCard's geometry: the credit runs from textX to the download glyph
		int posterRoom = this.cardWidth - 5 - downloadsWidth - Theme.DOWNLOAD_GLYPH_WIDTH - 6 - 5;
		Component credit = this.creditLine(entry, posterRoom);

		CardText computed = new CardText(entry, this.cardWidth, tag, name, downloads, downloadsWidth, credit);
		this.cardTextCache.put(entry.id(), computed);
		return computed;
	}

	// The poster's gradient only when the credit names them; a designer credit stays plain
	private Component creditLine(SchematicEntry entry, int room)
	{
		if (!entry.isCreditPoster() || !entry.hasPosterStyle())
		{
			return Component.literal(Theme.clip(this.font, entry.credit(), room));
		}

		String name = Theme.clip(this.font, entry.poster(), room);
		return ModTags.creator(name, entry.posterStops(), Style.EMPTY);
	}

	private Rect heartRect(int cardX, int cardY, int imageHeight)
	{
		this.heartRectPool.set(cardX + this.cardWidth - HEART_SIZE - 6, cardY + imageHeight - HEART_SIZE - 5,
				HEART_SIZE, HEART_SIZE);
		return this.heartRectPool;
	}

	private void renderBackToTop(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean overlayOpen)
	{
		boolean dashboard = this.page == Page.UPLOAD && !this.uploadFormOpen;
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

	private void recomputeSearchSuggestions()
	{
		this.searchSuggestQuery = this.query;
		this.searchSuggestRevision = Catalogue.revision();
		this.searchSuggestions.clear();
		this.searchSuggestCreators.clear();

		String q = this.query.trim().toLowerCase(Locale.ROOT);

		if (q.length() < 2)
		{
			return;
		}

		for (SchematicEntry entry : Catalogue.posts())
		{
			String title = entry.title();

			if (this.searchSuggestions.size() < 6
					&& title.toLowerCase(Locale.ROOT).contains(q)
					&& !this.searchSuggestions.contains(title))
			{
				this.searchSuggestions.add(title);
			}

			String poster = entry.poster();

			if (this.searchSuggestCreators.size() < 4
					&& poster.toLowerCase(Locale.ROOT).contains(q)
					&& !this.searchSuggestCreators.contains(poster))
			{
				this.searchSuggestCreators.add(poster);
			}

			if (this.searchSuggestions.size() >= 6 && this.searchSuggestCreators.size() >= 4)
			{
				break;
			}
		}
	}

	private void renderSearchSuggestions(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		this.searchSuggestRects.clear();

		if (!this.searchBox.isFocused() || this.detailView.isOpen())
		{
			return;
		}

		// The responder already covers query changes; this catches a catalogue change under focus
		if (this.searchSuggestRevision != Catalogue.revision())
		{
			this.recomputeSearchSuggestions();
		}

		if (this.searchSuggestions.isEmpty() && this.searchSuggestCreators.isEmpty())
		{
			return;
		}

		int x = this.searchBox.getX() - 6;
		int w = this.searchBox.getWidth() + 12;
		int rowH = this.font.lineHeight + 5;
		int y = TOP_BAR_HEIGHT + 2;
		int rows = this.searchSuggestions.size() + this.searchSuggestCreators.size();
		int h = rows * rowH + 6;

		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
		Theme.roundedOutline(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.HAIRLINE);

		int ty = y + 5;

		for (String match : this.searchSuggestions)
		{
			Rect row = new Rect();
			row.set(x, ty - 2, w, rowH);
			this.searchSuggestRects.add(row);

			boolean hovered = row.contains(mouseX, mouseY);

			if (hovered)
			{
				Theme.roundedRect(ctx, x + 2, ty - 2, w - 4, rowH, Theme.RADIUS_PILL, Theme.HAIRLINE);
			}

			Theme.text(ctx, this.font, Theme.clip(this.font, match, w - 12), x + 6, ty,
					hovered ? Theme.TEXT : Theme.TEXT_MUTE);
			ty += rowH;
		}

		// Creator rects follow the title rects so clickSearchSuggestion can split them by index
		for (String creator : this.searchSuggestCreators)
		{
			Rect row = new Rect();
			row.set(x, ty - 2, w, rowH);
			this.searchSuggestRects.add(row);

			boolean hovered = row.contains(mouseX, mouseY);

			if (hovered)
			{
				Theme.roundedRect(ctx, x + 2, ty - 2, w - 4, rowH, Theme.RADIUS_PILL, Theme.HAIRLINE);
			}

			Theme.text(ctx, this.font, Theme.clip(this.font, "by " + creator, w - 12), x + 6, ty,
					hovered ? Theme.ACCENT_BRIGHT : Theme.TEXT_ASH);
			ty += rowH;
		}
	}

	private boolean clickSearchSuggestion(double mouseX, double mouseY)
	{
		if (!this.searchBox.isFocused() || this.detailView.isOpen())
		{
			return false;
		}

		for (int i = 0; i < this.searchSuggestRects.size(); i++)
		{
			if (!this.searchSuggestRects.get(i).contains(mouseX, mouseY))
			{
				continue;
			}

			Theme.click();

			if (i < this.searchSuggestions.size())
			{
				this.setSearch(this.searchSuggestions.get(i));
				this.layoutChips();
				this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
				this.refilter();
			}
			else
			{
				int creatorIndex = i - this.searchSuggestions.size();

				if (creatorIndex < this.searchSuggestCreators.size())
				{
					this.openProfile(this.searchSuggestCreators.get(creatorIndex));
				}
			}

			return true;
		}

		return false;
	}

	private void renderScrollbar(GuiGraphicsExtractor ctx)
	{
		if (this.maxScroll <= 0.0F)
		{
			return;
		}

		this.verticalScrollbar(ctx, SCROLLBAR_MAIN, this.scroll, this.maxScroll, this.gridTop,
				this.gridBottom - this.gridTop, this.contentX + this.contentWidth);
	}

	private void verticalScrollbar(GuiGraphicsExtractor ctx, int channel, float scroll, float maxScroll,
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
	public void drawScrollThumb(GuiGraphicsExtractor ctx, int channel, int barX, int barWidth, int barY, int barHeight,
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
		return this.detailView.isOpen() || this.errorModal.isOpen() || this.nameInputModal.isOpen()
				|| this.deleteCollectionModal.isOpen() || this.collectionOptionsModal.isOpen()
				|| this.renameCollectionModal.isOpen() || this.tutorialModal.isOpen() || this.termsModal.isOpen()
				|| this.designerWarnModal.isOpen() || this.overwriteConfirm.isOpen()
				|| this.postOptionsModal.isOpen() || this.editPostModal.isOpen() || this.unpublishModal.isOpen()
				|| this.loadCodeModal.isOpen() || this.duplicateModal.isOpen() || this.downloadAllModal.isOpen()
				|| this.premiumBuyModal.isOpen() || this.shardPanel.isOpen() || this.shardWelcomeModal.isOpen()
				|| (this.staff != null && this.staff.isModalOpen());
	}

	private boolean pageBlocked()
	{
		return this.blockingOverlayOpen() || this.cardMenu.isOpen() || this.openChipDropdown() != null;
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

	private void renderUpload(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick)
	{
		int formWidth = Math.min(this.contentWidth, 300);
		int formX = this.contentX + (this.contentWidth - formWidth) / 2;
		int y = TOP_BAR_HEIGHT + 18;

		if (!UploaderAccess.unlocked())
		{
			Theme.textScaled(ctx, this.font, "Uploader access", formX, y, 1.0F, Theme.TEXT);
			y += 14;

			for (String line : this.wrap("Posting is invite only. Enter the access code you were given "
					+ "to unlock the upload form.", formWidth, 3))
			{
				Theme.text(ctx, this.font, line, formX, y, Theme.TEXT_MUTE);
				y += this.font.lineHeight + 1;
			}

			y += 6;
			Fields.single(ctx, this.codeBox, formX, y, formWidth, FIELD_HEIGHT, mouseX, mouseY, partialTick);
			y += FIELD_HEIGHT + 6;

			this.unlockButton.set(formX, y, 70, FIELD_HEIGHT);
			Buttons.pill(ctx, this.font, this.unlockButton, "Unlock", mouseX, mouseY, true);
			y += FIELD_HEIGHT + 8;

			if (!this.formStatus.isEmpty())
			{
				Theme.text(ctx, this.font, Theme.clip(this.font, this.formStatus, formWidth), formX, y, Theme.ACCENT_BRIGHT);
				y += this.font.lineHeight + 6;
			}

			return;
		}

		if (!this.uploadFormOpen)
		{
			this.renderUploadDashboard(ctx, mouseX, mouseY, partialTick);
			return;
		}

		int margin = 18;
		int areaX = this.contentX + margin;
		int areaW = this.contentWidth - margin * 2;
		int top = TOP_BAR_HEIGHT + 16;

		this.uploadBackButton.set(areaX, top - 4, 52, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.uploadBackButton, "< Back", mouseX, mouseY, false);
		Theme.text(ctx, this.font, Theme.bold("New post"), areaX + 62, top, Theme.TEXT);
		this.signOutButton.set(areaX + areaW - 58, top - 4, 58, FIELD_HEIGHT);
		// "Confirm?" fits the existing pill width, so the adjacent "+ New post" button does not shift
		Buttons.pill(ctx, this.font, this.signOutButton, (this.signOutConfirmAt != 0L
				&& System.currentTimeMillis() - this.signOutConfirmAt <= SIGN_OUT_CONFIRM_MS)
				? "Confirm?" : "Sign out", mouseX, mouseY, false);

		int gap = 24;
		int leftW = Math.min(258, (areaW - gap) * 44 / 100);
		int leftX = areaX;
		int rightX = areaX + leftW + gap;
		int rightW = areaX + areaW - rightX;

		// The header row stays put; both columns scroll beneath it on a window too short for the form
		int viewTop = top + FIELD_HEIGHT + 4;
		int viewBottom = this.height - OUTER_MARGIN;
		ctx.enableScissor(this.contentX, viewTop, this.contentX + this.contentWidth, viewBottom);

		int startY = top + 26 - Math.round(this.scroll);
		y = startY;

		Theme.text(ctx, this.font, "Schematic name", leftX, y, Theme.TEXT_ASH);
		Theme.text(ctx, this.font, "on the post page",
				leftX + leftW - this.font.width("on the post page"), y, Theme.TEXT_ASH);
		y += this.font.lineHeight + 3;
		Fields.single(ctx, this.titleBox, leftX, y, leftW, FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += FIELD_HEIGHT + 12;

		Theme.text(ctx, this.font, "Thumbnail name", leftX, y, Theme.TEXT_ASH);
		Theme.text(ctx, this.font, "on the card", leftX + leftW - this.font.width("on the card"), y, Theme.TEXT_ASH);
		y += this.font.lineHeight + 3;
		Fields.single(ctx, this.thumbnailBox, leftX, y, leftW, FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += FIELD_HEIGHT + 12;

		Theme.text(ctx, this.font, "Designed by", leftX, y, Theme.TEXT_ASH);
		y += this.font.lineHeight + 3;
		Fields.single(ctx, this.designerBox, leftX, y, leftW, FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += FIELD_HEIGHT + 12;

		this.descriptionBox = this.ensureMultiline(this.descriptionBox, leftW, DESC_FIELD_HEIGHT);
		Theme.text(ctx, this.font, "Description", leftX, y, Theme.TEXT_ASH);
		String uploadCount = this.descriptionBox.getValue().length() + " / " + DESC_CHAR_LIMIT;
		Theme.text(ctx, this.font, uploadCount, leftX + leftW - this.font.width(uploadCount), y, Theme.TEXT_ASH);
		y += this.font.lineHeight + 3;
		Fields.multiline(ctx, this.descriptionBox, this.uploadDescriptionBounds, leftX, y, leftW,
				DESC_FIELD_HEIGHT, mouseX, mouseY, partialTick);
		y += DESC_FIELD_HEIGHT + 14;

		this.formCategoryButton.set(leftX, y, leftW, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.formCategoryButton, "Category: " + this.formCategory.label(), mouseX, mouseY, false);
		y += FIELD_HEIGHT + 14;

		Theme.text(ctx, this.font, "Schematic file", leftX, y, Theme.TEXT_ASH);
		y += this.font.lineHeight + 3;
		this.uploadSchematicButton.set(leftX, y, leftW, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.uploadSchematicButton,
				this.formSchematic == null ? "Choose .litematic" : "Change file", mouseX, mouseY, false);
		y += FIELD_HEIGHT + 4;

		String chosen = this.formSchematic == null ? "No file chosen" : this.formSchematic.getFileName().toString();
		Theme.text(ctx, this.font, Theme.clip(this.font, chosen, leftW), leftX, y,
				this.formSchematic == null ? Theme.TEXT_ASH : Theme.ACCENT_BRIGHT);
		y += this.font.lineHeight + 6;

		// Only wired once a file is picked; the label reflects the disabled and rendering states
		this.generateThumbnailButton.set(leftX, y, leftW, FIELD_HEIGHT);
		String genLabel = this.generatingThumbnail ? "Rendering thumbnail..."
				: (this.formSchematic == null ? "Generate thumbnail - pick a file first" : "Generate thumbnail");
		Buttons.pill(ctx, this.font, this.generateThumbnailButton, genLabel, mouseX, mouseY, false);
		y += FIELD_HEIGHT + 14;

		String pictureCount = this.formPictures.isEmpty() ? "1-5 images" : this.formPictures.size() + " selected";
		Theme.text(ctx, this.font, "Pictures", leftX, y, Theme.TEXT_ASH);
		Theme.text(ctx, this.font, pictureCount, leftX + leftW - this.font.width(pictureCount), y, Theme.TEXT_ASH);
		y += this.font.lineHeight + 3;
		this.uploadPicturesButton.set(leftX, y, leftW, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.uploadPicturesButton, "Upload Pictures", mouseX, mouseY, false);
		y += FIELD_HEIGHT + 16;

		this.postButton.set(leftX, y, leftW, FIELD_HEIGHT);

		if (this.uploading)
		{
			Buttons.uploading(ctx, this.font, this.postButton, this.uploadStartedAt);
		}
		else
		{
			Buttons.pill(ctx, this.font, this.postButton, "Post", mouseX, mouseY, true);
		}

		y += FIELD_HEIGHT + 8;

		for (String line : this.wrap("Ctrl+V to paste an image or discord link to a schematic", leftW, 2))
		{
			Theme.text(ctx, this.font, line, leftX, y, Theme.TEXT_MUTE);
			y += this.font.lineHeight + 2;
		}

		y += 5;

		if (this.uploading)
		{
			long elapsed = System.currentTimeMillis() - this.uploadStartedAt;

			if (this.uploadFileSize > 8L * 1024 * 1024 || elapsed > 10_000L)
			{
				for (String line : this.wrap("This is a large schematic file, uploading may take a while.", leftW, 2))
				{
					Theme.text(ctx, this.font, line, leftX, y, Theme.TEXT_ASH);
					y += this.font.lineHeight + 1;
				}
			}
		}
		else if (!this.formStatus.isEmpty())
		{
			Theme.text(ctx, this.font, Theme.clip(this.font, this.formStatus, leftW), leftX, y, Theme.ACCENT_BRIGHT);
		}

		SchematicEntry preview = this.previewEntry();
		int ry = startY;

		Theme.text(ctx, this.font, Theme.bold("Post Thumbnail"), rightX, ry, Theme.TEXT_MUTE);
		ry += this.font.lineHeight + 6;

		int cardPreviewWidth = Math.min(rightW, 138);
		int savedCardWidth = this.cardWidth;
		int savedCardHeight = this.cardHeight;
		this.cardWidth = cardPreviewWidth;
		this.cardHeight = imageHeight(cardPreviewWidth) + CAPTION_HEIGHT;

		// An exception mid-render must not leave the shared grid card size corrupted for later frames
		try
		{
			this.renderCard(ctx, this.thumbnailPreviewEntry(preview), rightX, ry, -999, -999);
			ry += this.cardHeight + 16;
		}
		finally
		{
			this.cardWidth = savedCardWidth;
			this.cardHeight = savedCardHeight;
		}

		Theme.text(ctx, this.font, Theme.bold("In the post"), rightX, ry, Theme.TEXT_MUTE);
		ry += this.font.lineHeight + 6;
		ry = this.renderDetailPreview(ctx, preview, rightX, ry, rightW, mouseX, mouseY);

		ctx.disableScissor();

		int contentHeight = Math.max(y, ry) + OUTER_MARGIN - startY;
		this.maxScroll = Math.max(0.0F, contentHeight - (viewBottom - viewTop));
		this.scroll = Math.min(this.scroll, this.maxScroll);
		this.verticalScrollbar(ctx, SCROLLBAR_MAIN, this.scroll, this.maxScroll, viewTop, viewBottom - viewTop,
				this.contentX + this.contentWidth);
	}

	// First image repointed at the chosen thumbnail, so the small card shows the picked picture
	private SchematicEntry thumbnailPreviewEntry(SchematicEntry preview)
	{
		if (this.formPictures.isEmpty() || this.formPictureStart < 0 || this.formThumbnailIndex <= 0)
		{
			return preview;
		}

		int start = this.formPictureStart + Math.min(this.formThumbnailIndex, this.formPictures.size() - 1);
		return SchematicEntry.local("preview", preview.title(), preview.thumbnailName(), preview.poster(),
				preview.designer(), preview.category(), preview.sizeX(), preview.sizeY(), preview.sizeZ(),
				preview.blockCount(), 0, 0, preview.postedAt(), preview.description(), preview.imageCount(),
				start, -1, false);
	}

	private SchematicEntry previewEntry()
	{
		String title = this.titleBox.getValue().trim();

		if (title.isEmpty())
		{
			title = "Your build";
		}

		String poster = UploaderAccess.profile() == null ? "you" : UploaderAccess.profile();
		String designer = this.designerBox.getValue().trim();
		String thumbnail = this.thumbnailBox.getValue().trim();
		String description = this.descriptionBox.getValue().trim();

		String key = title + '\0' + poster + '\0' + designer + '\0' + thumbnail + '\0'
				+ this.formCategory + '\0' + this.formSizeX + '\0' + this.formSizeY + '\0'
				+ this.formSizeZ + '\0' + this.formBlockCount + '\0' + description + '\0'
				+ this.formPictures.size() + '\0' + this.formPictureStart;

		if (this.cachedPreview != null && key.equals(this.cachedPreviewKey))
		{
			return this.cachedPreview;
		}

		this.cachedPreviewKey = key;
		this.cachedPreview = SchematicEntry.local("preview", title, thumbnail, poster,
				designer.isEmpty() ? "Unknown" : designer, this.formCategory,
				this.formSizeX, this.formSizeY, this.formSizeZ, this.formBlockCount, 0, 0,
				System.currentTimeMillis(), description,
				this.formPictures.size(), this.formPictureStart, -1, false);
		return this.cachedPreview;
	}

	private int renderDetailPreview(GuiGraphicsExtractor ctx, SchematicEntry entry, int x, int y, int modalWidth, int mouseX, int mouseY)
	{
		int pad = 12;
		int imageWidth = Math.round((modalWidth - pad * 3) * 0.56F);
		int imageHeight = imageHeight(imageWidth);

		List<String> descLines = this.wrap(entry.description(), modalWidth - pad * 2, 2);
		int modalHeight = pad + imageHeight + 6 + descLines.size() * (this.font.lineHeight + 1) + 10 + 16 + pad;

		Theme.roundedRect(ctx, x, y, modalWidth, modalHeight, Theme.RADIUS_MODAL, Theme.SURFACE_ELEVATED);

		Identifier texture = entry.imageCount() > 0 ? this.imageTexture(entry, this.formPicturePreview) : null;

		if (texture != null)
		{
			Theme.image(ctx, texture, x + pad, y + pad, imageWidth, imageHeight);
		}
		else
		{
			Theme.blueprintPlaceholder(ctx, x + pad, y + pad, imageWidth, imageHeight);
			String empty = "No pictures yet";
			Theme.text(ctx, this.font, empty, x + pad + (imageWidth - this.font.width(empty)) / 2, y + pad + imageHeight / 2 - 4, Theme.TEXT_ASH);
		}

		if (entry.imageCount() > 1)
		{
			this.formImagePrev.set(x + pad + 2, y + pad + imageHeight / 2 - 8, 12, 16);
			this.formImageNext.set(x + pad + imageWidth - 14, y + pad + imageHeight / 2 - 8, 12, 16);
			Buttons.arrow(ctx, this.formImagePrev, true, mouseX, mouseY);
			Buttons.arrow(ctx, this.formImageNext, false, mouseX, mouseY);

			String counter = (this.formPicturePreview + 1) + "/" + entry.imageCount();
			int cw = this.font.width(counter) + 8;
			Theme.roundedRect(ctx, x + pad + imageWidth - cw - 3, y + pad + imageHeight - 14, cw, 11, Theme.RADIUS_PILL, 0xCC0F1114);
			Theme.text(ctx, this.font, counter, x + pad + imageWidth - cw + 1, y + pad + imageHeight - 12, Theme.TEXT);
		}
		else
		{
			this.formImagePrev.set(0, 0, 0, 0);
			this.formImageNext.set(0, 0, 0, 0);
		}

		if (entry.imageCount() > 0)
		{
			int xs = 14;
			int rx = x + pad + imageWidth - xs - 4;
			int ry = y + pad + 4;
			this.formImageRemove.set(rx, ry, xs, xs);
			boolean removeHover = this.formImageRemove.contains(mouseX, mouseY);
			Theme.roundedRect(ctx, rx, ry, xs, xs, Theme.RADIUS_CARD, removeHover ? 0xEED64545 : 0xCC0F1114);
			Theme.roundedOutline(ctx, rx, ry, xs, xs, Theme.RADIUS_CARD, Theme.HAIRLINE);
			this.drawLine(ctx, rx + 4, ry + 4, rx + xs - 4, ry + xs - 4, Theme.TEXT);
			this.drawLine(ctx, rx + 4, ry + xs - 4, rx + xs - 4, ry + 4, Theme.TEXT);

			boolean isThumb = this.formPicturePreview == this.formThumbnailIndex;
			String label = isThumb ? "★ Thumbnail" : "Set as thumbnail";
			int tw = this.font.width(label) + 10;
			int tx = x + pad + 4;
			int ty = y + pad + imageHeight - 15;
			this.formThumbnailButton.set(tx, ty, tw, 12);
			boolean thumbHover = this.formThumbnailButton.contains(mouseX, mouseY);
			Theme.roundedRect(ctx, tx, ty, tw, 12, Theme.RADIUS_PILL,
					isThumb ? Theme.ACCENT : (thumbHover ? 0xEE000000 : 0xCC0F1114));
			Theme.text(ctx, this.font, label, tx + 5, ty + 2, isThumb ? Theme.ON_ACCENT : Theme.TEXT);
		}
		else
		{
			this.formImageRemove.set(0, 0, 0, 0);
			this.formThumbnailButton.set(0, 0, 0, 0);
		}

		int infoX = x + pad + imageWidth + pad;
		int infoWidth = modalWidth - (infoX - x) - pad;
		int line = y + pad;

		String age = entry.agoLabel();
		int ageWidth = this.font.width(age);
		Theme.text(ctx, this.font, age, x + modalWidth - pad - ageWidth, y + pad, Theme.TEXT_ASH);

		Theme.text(ctx, this.font, Theme.bold(Theme.clipBold(this.font, entry.title(), infoWidth - ageWidth - 6)), infoX, line, Theme.TEXT);
		line += this.font.lineHeight + 4;

		String followLabel = "Follow";
		int followWidth = this.font.width(Theme.bold(followLabel)) + 12;
		Rect follow = this.previewFollowRect;
		follow.set(infoX + infoWidth - followWidth, line - 1, followWidth, 12);
		Buttons.mock(ctx, this.font, follow, followLabel);

		Theme.text(ctx, this.font, Theme.clip(this.font, "Posted by " + entry.poster(), infoWidth - followWidth - 6), infoX, line, Theme.TEXT_MUTE);
		line += this.font.lineHeight + 3;

		String designer = entry.designer().isBlank() ? "Unknown" : entry.designer();
		Theme.text(ctx, this.font, Theme.clip(this.font, "Designed by " + designer, infoWidth), infoX, line, Theme.TEXT_MUTE);
		line += this.font.lineHeight + 6;

		line = this.metaRow(ctx, "Dimensions", entry.dimensionsLabel(), infoX, line, infoWidth);
		line = this.metaRow(ctx, "Total Blocks", entry.blockCountLabel(), infoX, line, infoWidth);
		line = this.metaRow(ctx, "Volume", entry.volumeLabel(), infoX, line, infoWidth);

		Theme.text(ctx, this.font, "Downloads", infoX, line, Theme.TEXT_ASH);
		String downloads = entry.downloadsLabel();
		int downloadsWidth = this.font.width(downloads);
		Theme.text(ctx, this.font, downloads, infoX + infoWidth - downloadsWidth, line, Theme.TEXT);
		Theme.downloadGlyph(ctx, infoX + infoWidth - downloadsWidth - Theme.DOWNLOAD_GLYPH_WIDTH - 3, line + 2, Theme.TEXT_MUTE);
		line += this.font.lineHeight + 2;

		Theme.text(ctx, this.font, "Likes", infoX, line, Theme.TEXT_ASH);
		String likeCount = SchematicEntry.compact(entry.likes());
		int likeWidth = this.font.width(likeCount);
		Theme.text(ctx, this.font, likeCount, infoX + infoWidth - likeWidth, line, Theme.TEXT);
		Theme.heart(ctx, infoX + infoWidth - likeWidth - HEART_SIZE - 4, line - 1, false);

		int descriptionY = y + pad + imageHeight + 6;

		for (String row : descLines)
		{
			Theme.text(ctx, this.font, row, x + pad, descriptionY, Theme.TEXT_MUTE);
			descriptionY += this.font.lineHeight + 1;
		}

		int buttonY = y + modalHeight - pad - 16;
		int downloadW = this.font.width(Theme.bold("Download")) + 18;
		int previewW = this.font.width(Theme.bold("3D preview")) + 18;
		int closeW = this.font.width(Theme.bold("Close")) + 18;
		int saveW = this.font.width(Theme.bold("Save for later")) + 18;

		Rect close = this.previewCloseRect;
		close.set(x + pad, buttonY, closeW, 16);
		Rect save = this.previewSaveRect;
		save.set(close.x + closeW + 6, buttonY, saveW, 16);
		Rect download = this.previewDownloadRect;
		download.set(x + modalWidth - pad - downloadW, buttonY, downloadW, 16);
		Rect preview3d = this.previewPreview3dRect;
		preview3d.set(download.x - 6 - previewW, buttonY, previewW, 16);

		Buttons.mock(ctx, this.font, close, "Close");
		Buttons.mock(ctx, this.font, save, "Save for later");
		Buttons.mock(ctx, this.font, preview3d, "3D preview");
		Buttons.mock(ctx, this.font, download, "Download");
		return y + modalHeight;
	}

	private void renderUploadDashboard(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick)
	{
		if (!this.myStatsLoaded && !this.myStatsLoading)
		{
			this.loadMyStats();
		}

		if (McAuth.verified() && !this.myClaimsLoaded && !this.myClaimsLoading)
		{
			this.loadMyClaims();
		}

		int margin = 18;
		int areaX = this.contentX + margin;
		int areaW = this.contentWidth - margin * 2;
		int top = TOP_BAR_HEIGHT + 16;

		// Room left of the two buttons, shared with the head and the verified name that follow
		int headerRoom = areaW - 58 - 6 - 86 - 12;
		String signedIn = Theme.clipBold(this.font, "Signed in as " + UploaderAccess.profile(), headerRoom - 60);
		Theme.text(ctx, this.font, Theme.bold(signedIn), areaX, top, Theme.TEXT);

		String uploaderIgn = UploaderAccess.ign();
		int badgeX = areaX + this.font.width(Theme.bold(signedIn)) + 6;

		if (uploaderIgn != null && !uploaderIgn.isBlank())
		{
			int headX = badgeX;
			int headY = top - 3;
			Identifier head = ImageStore.avatar("https://mc-heads.net/avatar/" + Backend.encode(uploaderIgn) + "/64.png");

			if (head != null)
			{
				Theme.image(ctx, head, headX, headY, 14, 14, 64, 64);
			}

			badgeX = headX + 14 + 4;
		}

		if (McAuth.verified())
		{
			String verified = McAuth.verifiedName() == null ? "Verified" : McAuth.verifiedName();
			Theme.text(ctx, this.font, Theme.clip(this.font, "✓ " + verified, areaX + headerRoom - badgeX), badgeX, top,
					0xFF3FB950);
		}

		this.signOutButton.set(areaX + areaW - 58, top - 4, 58, FIELD_HEIGHT);
		// "Confirm?" fits the existing pill width, so the adjacent "+ New post" button does not shift
		Buttons.pill(ctx, this.font, this.signOutButton, (this.signOutConfirmAt != 0L
				&& System.currentTimeMillis() - this.signOutConfirmAt <= SIGN_OUT_CONFIRM_MS)
				? "Confirm?" : "Sign out", mouseX, mouseY, false);
		this.dashUploadButton.set(areaX + areaW - 58 - 6 - 86, top - 4, 86, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.dashUploadButton, "+ New post", mouseX, mouseY, true);

		// A stale-triggered refresh keeps myStatsOk true, so cached stats stay on screen during it
		this.statsRetryButton.set(0, 0, 0, 0);

		if (!this.myStatsOk)
		{
			if (this.myStatsFailed)
			{
				int msgY = top + 26 + 18;
				Theme.text(ctx, this.font, "Couldn't load your stats.", areaX, msgY, Theme.TEXT_MUTE);
				int retryW = this.font.width(Theme.bold("Retry")) + 16;
				this.statsRetryButton.set(areaX, msgY + this.font.lineHeight + 8, retryW, FIELD_HEIGHT);
				Buttons.pill(ctx, this.font, this.statsRetryButton, "Retry", mouseX, mouseY, true);
			}
			else
			{
				this.renderDashboardSkeleton(ctx, areaX, top + 26, areaW);
			}

			return;
		}

		// One scrolling column; only the sign-in row above stays fixed
		int scrollTop = top + 26;
		int scrollBottom = this.height - 6;
		this.dashViewportTop = scrollTop;
		this.dashViewportBottom = scrollBottom;

		int tileH = 42;
		int graphH = 150;
		int gap = GUTTER;
		int cols = Math.max(1, (areaW + gap) / (112 + gap));
		int cw = (areaW - gap * (cols - 1)) / cols;
		int ch = imageHeight(cw) + CAPTION_HEIGHT;

		if (this.dashCacheDirty || this.dashTiles == null)
		{
			this.dashTiles = new String[][]
			{
				{"Posts", SchematicEntry.compact(this.myPostsCount)},
				{"Views", SchematicEntry.compact(this.myViews)},
				{"Downloads", SchematicEntry.compact(this.myDownloads)},
				{"Likes", SchematicEntry.compact(this.myLikes)},
				{"Avg Stars", this.myRatingCount > 0 ? String.format(Locale.ROOT, "%.1f", this.myRating) : "-"},
				{"Followers", SchematicEntry.compact(this.myFollowers)},
			};
		}

		if (this.dashCacheDirty || this.dashCacheFilter != this.myFilter)
		{
			this.dashShown.clear();

			for (SchematicEntry entry : this.myPosts)
			{
				if (this.myFilter == Category.ALL || entry.category() == this.myFilter)
				{
					this.dashShown.add(entry);
				}
			}

			this.dashCacheFilter = this.myFilter;
		}

		this.dashCacheDirty = false;
		List<SchematicEntry> shown = this.dashShown;

		int uploadsHeaderH = this.font.lineHeight + 6;
		int chipsH = this.measureFilterChipsHeight(areaX, areaW);
		int postsH = shown.isEmpty()
				? this.font.lineHeight
				: ((shown.size() + cols - 1) / cols) * (ch + gap) - gap;

		int claimsH = this.measureMyClaimsHeight();
		int contentHeight = tileH + 14 + graphH + 14 + uploadsHeaderH + chipsH + 6 + postsH + claimsH;
		this.dashMaxScroll = Math.max(0.0F, contentHeight - (scrollBottom - scrollTop));
		this.dashScroll = Math.max(0.0F, Math.min(this.dashScroll, this.dashMaxScroll));

		ctx.enableScissor(this.contentX, scrollTop, this.contentX + this.contentWidth, scrollBottom);

		int y = scrollTop - Math.round(this.dashScroll);

		String[][] tiles = this.dashTiles;
		int tileGap = 8;
		int tileW = (areaW - tileGap * (tiles.length - 1)) / tiles.length;

		for (int i = 0; i < tiles.length; i++)
		{
			int tx = areaX + i * (tileW + tileGap);
			Theme.roundedRect(ctx, tx, y, tileW, tileH, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
			Theme.text(ctx, this.font, tiles[i][0], tx + 8, y + 8, Theme.TEXT_MUTE);
			Theme.textScaled(ctx, this.font, Theme.bold(tiles[i][1]), tx + 8, y + 19, 1.5F, Theme.TEXT);
		}

		y += tileH + 14;

		this.renderLineGraph(ctx, areaX, y, areaW, graphH, mouseX, mouseY);
		y += graphH + 14;

		Theme.text(ctx, this.font, Theme.bold("Click your posts to view its stats on the graph"), areaX, y,
				Theme.TEXT_MUTE);
		y += this.font.lineHeight + 6;

		y = this.renderMyFilterChips(ctx, areaX, y, areaW, mouseX, mouseY) + 6;

		this.myPostCardRects.clear();
		this.myPostCardIds.clear();
		this.myPostEditRects.clear();

		if (shown.isEmpty())
		{
			String msg = this.myPosts.isEmpty()
					? "You haven't uploaded anything yet. Hit New post to start."
					: "No posts in this category.";
			Theme.text(ctx, this.font, Theme.clip(this.font, msg, areaW), areaX, y, Theme.TEXT_ASH);
		}
		else
		{
			int savedW = this.cardWidth;
			int savedH = this.cardHeight;
			this.cardWidth = cw;
			this.cardHeight = ch;

			// An exception mid-loop must not leave the shared grid card size corrupted for later frames
			try
			{
				for (int i = 0; i < shown.size(); i++)
				{
					int col = i % cols;
					int row = i / cols;
					int cx = areaX + col * (cw + gap);
					int cy = y + row * (ch + gap);

					Rect rect = Rect.pooled(this.myPostCardPool, i);
					rect.set(cx, cy, cw, ch);
					this.myPostCardRects.add(rect);
					this.myPostCardIds.add(shown.get(i).id());

					Rect editRect = Rect.pooled(this.myPostEditPool, i);
					editRect.set(cx + cw - 16, cy + 3, 13, 13);
					this.myPostEditRects.add(editRect);

					if (cy + ch >= scrollTop && cy <= scrollBottom)
					{
						this.renderCard(ctx, shown.get(i), cx, cy, -999, -999);

						// Held out of the catalogue until a moderator approves it
						if (this.myPendingIds.contains(shown.get(i).id()))
						{
							String badge = "In review";
							int bw = this.font.width(badge) + 10;
							Theme.roundedRect(ctx, cx + 4, cy + 4, bw, 14, Theme.RADIUS_PILL, 0xF0C8811E);
							Theme.text(ctx, this.font, badge, cx + 9, cy + 7, 0xFFFFFFFF);
						}

						boolean selected = shown.get(i).id().equals(this.selectedDashPost);
						boolean hovered = rect.contains(mouseX, mouseY)
								&& mouseY >= scrollTop && mouseY <= scrollBottom;

						if (selected)
						{
							Theme.roundedOutline(ctx, cx, cy, cw, ch, Theme.RADIUS_CARD, Theme.ACCENT_BRIGHT);
						}
						else if (hovered)
						{
							Theme.roundedOutline(ctx, cx, cy, cw, ch, Theme.RADIUS_CARD, Theme.HAIRLINE);
						}

						boolean editHover = editRect.contains(mouseX, mouseY)
								&& mouseY >= scrollTop && mouseY <= scrollBottom;
						Theme.roundedRect(ctx, editRect.x, editRect.y, editRect.width, editRect.height,
								Theme.RADIUS_PILL, editHover ? 0xF0000000 : 0xB0000000);
						Theme.editGlyph(ctx, editRect.x + 3, editRect.y + 3,
								editHover ? Theme.ACCENT_BRIGHT : Theme.TEXT);
					}
				}
			}
			finally
			{
				this.cardWidth = savedW;
				this.cardHeight = savedH;
			}
		}

		this.renderMyClaimsSection(ctx, areaX, y + postsH, areaW, scrollTop, scrollBottom);

		ctx.disableScissor();

		this.verticalScrollbar(ctx, SCROLLBAR_DASH, this.dashScroll, this.dashMaxScroll, scrollTop,
				scrollBottom - scrollTop, areaX + areaW);
	}

	// Mirrors the wrap in renderMyFilterChips, which cannot run before the dashboard is laid out
	private int measureFilterChipsHeight(int x, int w)
	{
		int cx = x;
		int rows = 0;

		for (Category value : Category.values())
		{
			int chipW = this.font.width(Theme.bold(value.label())) + 12;

			if (cx + chipW > x + w)
			{
				cx = x;
				rows++;
			}

			cx += chipW + CHIP_GAP;
		}

		return (rows + 1) * CHIP_HEIGHT + rows * CHIP_GAP;
	}

	private int renderMyFilterChips(GuiGraphicsExtractor ctx, int x, int y, int w, int mouseX, int mouseY)
	{
		Category[] all = Category.values();

		while (this.myFilterChips.size() < all.length)
		{
			this.myFilterChips.add(new Rect());
		}

		int cx = x;
		int cy = y;

		for (int i = 0; i < all.length; i++)
		{
			Category value = all[i];
			String label = Theme.bold(value.label());
			int chipW = this.font.width(label) + 12;

			if (cx + chipW > x + w)
			{
				cx = x;
				cy += CHIP_HEIGHT + CHIP_GAP;
			}

			Rect rect = this.myFilterChips.get(i);
			rect.set(cx, cy, chipW, CHIP_HEIGHT);
			boolean active = value == this.myFilter;
			boolean hovered = rect.contains(mouseX, mouseY);
			int fill = active ? Theme.ACCENT : (hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
			Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL, fill);
			Theme.text(ctx, this.font, label, rect.x + 6, rect.y + (CHIP_HEIGHT - this.font.lineHeight) / 2 + 1,
					active ? Theme.ON_ACCENT : Theme.TEXT_MUTE);

			cx += chipW + CHIP_GAP;
		}

		return cy + CHIP_HEIGHT;
	}

	private static final int CLAIM_ROW_HEIGHT = 20;
	private static final int CLAIM_ROW_GAP = 4;

	// Split out from renderMyClaimsSection so the scroll-height sum and the render cannot drift
	private int measureMyClaimsHeight()
	{
		if (!McAuth.verified() || (this.myClaims.isEmpty() && !this.myClaimsFailed))
		{
			return 0;
		}

		if (this.myClaimsFailed)
		{
			return 14 + this.font.lineHeight;
		}

		return 14 + this.font.lineHeight + 6
				+ this.myClaims.size() * (CLAIM_ROW_HEIGHT + CLAIM_ROW_GAP) - CLAIM_ROW_GAP;
	}

	// Returns the y just below what it drew, always matching measureMyClaimsHeight above
	private int renderMyClaimsSection(GuiGraphicsExtractor ctx, int x, int y, int w, int scrollTop,
			int scrollBottom)
	{
		if (!McAuth.verified() || (this.myClaims.isEmpty() && !this.myClaimsFailed))
		{
			return y;
		}

		y += 14;

		if (this.myClaimsFailed)
		{
			Theme.text(ctx, this.font, "Couldn't load your claims (" + Errors.CLAIMS_LIST + ").", x, y,
					Theme.TEXT_ASH);
			return y + this.font.lineHeight;
		}

		Theme.text(ctx, this.font, Theme.bold("My claims"), x, y, Theme.TEXT);
		y += this.font.lineHeight + 6;

		for (ClaimItem claim : this.myClaims)
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

			if (y + CLAIM_ROW_HEIGHT >= scrollTop && y <= scrollBottom)
			{
				int pillW = this.font.width(status) + 10;
				int titleY = y + (CLAIM_ROW_HEIGHT - this.font.lineHeight) / 2;
				int pillY = y + (CLAIM_ROW_HEIGHT - 14) / 2;
				String title = this.trimToWidth(claim.title(), w - pillW - 10);
				Theme.text(ctx, this.font, title, x, titleY, Theme.TEXT_MUTE);
				Theme.roundedRect(ctx, x + w - pillW, pillY, pillW, 14, Theme.RADIUS_PILL, pillColor);
				Theme.text(ctx, this.font, status, x + w - pillW + 5, pillY + 3, 0xFFFFFFFF);
			}

			y += CLAIM_ROW_HEIGHT + CLAIM_ROW_GAP;
		}

		return y - CLAIM_ROW_GAP;
	}

	private void renderLineGraph(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY)
	{
		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.SURFACE_CARD);

		int legendChipH = this.font.lineHeight + 4;
		int legendY = y + 6;
		int legendToPlot = 11;
		int plotToLabel = 8;
		int labelH = this.font.lineHeight;
		int bottomGap = 5;

		int plotX = x + 32;
		int plotY = legendY + legendChipH + legendToPlot;
		int plotRight = x + w - 12;
		int plotBottom = y + h - bottomGap - labelH - plotToLabel;
		int plotW = plotRight - plotX;
		int plotH = plotBottom - plotY;

		int lx = x + 10;
		lx += this.legendChip(ctx, this.legendViewsRect, lx, legendY, "Views", Theme.STAT_VIEWS, this.graphMode == 1, mouseX, mouseY);
		lx += this.legendChip(ctx, this.legendDownloadsRect, lx, legendY, "Downloads", Theme.STAT_DOWNLOADS, this.graphMode == 2, mouseX, mouseY);
		lx += this.legendChip(ctx, this.legendLikesRect, lx, legendY, "Likes", Theme.STAT_LIKES, this.graphMode == 3, mouseX, mouseY);
		this.legendChip(ctx, this.legendStarsRect, lx, legendY, "Stars", Theme.STAT_STARS, this.graphMode == 4, mouseX, mouseY);

		if (this.myDays.length < 2 || plotH < 12)
		{
			String msg = "No activity yet.";
			Theme.text(ctx, this.font, msg, x + (w - this.font.width(msg)) / 2, plotY + plotH / 2 - 4, Theme.TEXT_ASH);
			return;
		}

		boolean showViews = this.graphMode == 0 || this.graphMode == 1;
		boolean showDownloads = this.graphMode == 0 || this.graphMode == 2;
		boolean showLikes = this.graphMode == 0 || this.graphMode == 3;
		boolean showStars = this.graphMode == 0 || this.graphMode == 4;

		int[][] selected = this.selectedDashPost != null ? this.myPostSeries.get(this.selectedDashPost) : null;
		int[] viewSeries = selected != null ? selected[0] : this.myViewSeries;
		int[] downloadSeries = selected != null ? selected[1] : this.myDownloadSeries;
		int[] likeSeries = selected != null ? selected[2] : this.myLikeSeries;
		int[] starSeries = selected != null && selected.length > 3 ? selected[3] : this.myStarSeries;

		int rawMax = 1;
		if (showViews)
		{
			for (int v : viewSeries) rawMax = Math.max(rawMax, v);
		}
		if (showDownloads)
		{
			for (int v : downloadSeries) rawMax = Math.max(rawMax, v);
		}
		if (showLikes)
		{
			for (int v : likeSeries) rawMax = Math.max(rawMax, v);
		}
		if (showStars)
		{
			for (int v : starSeries) rawMax = Math.max(rawMax, v);
		}
		int max = niceCeil(rawMax);

		for (int g = 0; g <= 2; g++)
		{
			int value = max * g / 2;
			int gy = plotBottom - (int) Math.round((double) value / max * plotH);
			ctx.fill(plotX, gy, plotRight, gy + 1, g == 0 ? Theme.HAIRLINE : 0x18FFFFFF);
			String lbl = SchematicEntry.compact(value);
			Theme.text(ctx, this.font, lbl, plotX - 5 - this.font.width(lbl), gy - 3, Theme.TEXT_ASH);
		}

		float animT = this.graphAnim();

		if (showViews)
		{
			this.plotSeries(ctx, viewSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_VIEWS, animT);
		}
		if (showDownloads)
		{
			this.plotSeries(ctx, downloadSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_DOWNLOADS, animT);
		}
		if (showLikes)
		{
			this.plotSeries(ctx, likeSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_LIKES, animT);
		}
		if (showStars)
		{
			this.plotSeries(ctx, starSeries, max, plotX, plotY, plotW, plotH, Theme.STAT_STARS, animT);
		}

		Theme.text(ctx, this.font, shortDay(this.myDays[0]), plotX, plotBottom + plotToLabel, Theme.TEXT_ASH);
		String lastLabel = shortDay(this.myDays[this.myDays.length - 1]);
		Theme.text(ctx, this.font, lastLabel, plotRight - this.font.width(lastLabel), plotBottom + plotToLabel, Theme.TEXT_ASH);

		if (mouseX >= plotX - 4 && mouseX <= plotRight + 4 && mouseY >= plotY - 6 && mouseY <= plotBottom + 6)
		{
			int n = this.myDays.length;
			int hi = Math.max(0, Math.min(n - 1, (int) Math.round((double) (mouseX - plotX) / plotW * (n - 1))));
			int hx = plotX + (int) Math.round((double) hi / (n - 1) * plotW);

			ctx.fill(hx, plotY, hx + 1, plotBottom, 0x33FFFFFF);

			List<String> rows = new ArrayList<>();
			List<Integer> rowColors = new ArrayList<>();

			if (showViews && hi < viewSeries.length)
			{
				int py = this.plotValueY(viewSeries[hi], max, plotY, plotH, animT);
				this.graphDot(ctx, hx, py, Theme.STAT_VIEWS);
				rows.add("Views: " + SchematicEntry.compact(viewSeries[hi]));
				rowColors.add(Theme.STAT_VIEWS);
			}
			if (showDownloads && hi < downloadSeries.length)
			{
				int py = this.plotValueY(downloadSeries[hi], max, plotY, plotH, animT);
				this.graphDot(ctx, hx, py, Theme.STAT_DOWNLOADS);
				rows.add("Downloads: " + SchematicEntry.compact(downloadSeries[hi]));
				rowColors.add(Theme.STAT_DOWNLOADS);
			}
			if (showLikes && hi < likeSeries.length)
			{
				int py = this.plotValueY(likeSeries[hi], max, plotY, plotH, animT);
				this.graphDot(ctx, hx, py, Theme.STAT_LIKES);
				rows.add("Likes: " + SchematicEntry.compact(likeSeries[hi]));
				rowColors.add(Theme.STAT_LIKES);
			}
			if (showStars && hi < starSeries.length)
			{
				int py = this.plotValueY(starSeries[hi], max, plotY, plotH, animT);
				this.graphDot(ctx, hx, py, Theme.STAT_STARS);
				rows.add("Stars: " + SchematicEntry.compact(starSeries[hi]));
				rowColors.add(Theme.STAT_STARS);
			}

			String date = fullDay(this.myDays[hi]);
			int pad = 6;
			int rowH = this.font.lineHeight + 2;
			int textWidth = this.font.width(Theme.bold(date));

			for (String row : rows)
			{
				textWidth = Math.max(textWidth, 10 + this.font.width(row));
			}

			int boxW = textWidth + pad * 2;
			int boxH = pad + this.font.lineHeight + 4 + rows.size() * rowH + pad - 2;
			int boxX = hx + 10 + boxW > x + w ? hx - 10 - boxW : hx + 10;
			boxX = Math.max(x + 2, Math.min(boxX, x + w - boxW - 2));
			int boxY = Math.max(y + 2, Math.min(mouseY - boxH / 2, y + h - boxH - 2));

			Theme.roundedRect(ctx, boxX, boxY, boxW, boxH, Theme.RADIUS_CARD, Theme.SURFACE_ELEVATED);
			Theme.roundedOutline(ctx, boxX, boxY, boxW, boxH, Theme.RADIUS_CARD, Theme.HAIRLINE);

			int ty = boxY + pad;
			Theme.text(ctx, this.font, Theme.bold(date), boxX + pad, ty, Theme.TEXT);
			ty += this.font.lineHeight + 4;

			for (int i = 0; i < rows.size(); i++)
			{
				ctx.fill(boxX + pad, ty + 2, boxX + pad + 4, ty + 6, rowColors.get(i));
				Theme.text(ctx, this.font, rows.get(i), boxX + pad + 10, ty, Theme.TEXT_MUTE);
				ty += rowH;
			}
		}
	}

	private void graphDot(GuiGraphicsExtractor ctx, int centreX, int centreY, int color)
	{
		Theme.roundedRect(ctx, centreX - 3, centreY - 3, 6, 6, 3, 0xFFFFFFFF);
		Theme.roundedRect(ctx, centreX - 2, centreY - 2, 4, 4, 2, color);
	}

	private static String fullDay(String iso)
	{
		try
		{
			String[] parts = iso.split("-");
			String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
			int month = Math.max(1, Math.min(12, Integer.parseInt(parts[1])));
			return months[month - 1] + " " + Integer.parseInt(parts[2]) + ", " + parts[0];
		}
		catch (Exception e)
		{
			return iso;
		}
	}

	private int legendChip(GuiGraphicsExtractor ctx, Rect rect, int x, int y, String label, int color, boolean active, int mouseX, int mouseY)
	{
		int chipW = 13 + this.font.width(label) + 8;
		int chipH = this.font.lineHeight + 4;
		rect.set(x, y, chipW, chipH);
		boolean hovered = rect.contains(mouseX, mouseY);
		Theme.roundedRect(ctx, x, y, chipW, chipH, Theme.RADIUS_PILL, active || hovered ? Theme.SURFACE_ELEVATED : Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, x, y, chipW, chipH, Theme.RADIUS_PILL, active ? Theme.ACCENT_BRIGHT : Theme.HAIRLINE);
		ctx.fill(x + 6, y + chipH / 2 - 2, x + 10, y + chipH / 2 + 2, color);
		Theme.text(ctx, this.font, label, x + 13, y + (chipH - this.font.lineHeight) / 2 + 1, active ? Theme.TEXT : Theme.TEXT_MUTE);
		return chipW + 6;
	}

	private float graphAnim()
	{
		float t = Math.max(0.0F, Math.min(1.0F, (System.currentTimeMillis() - this.graphAnimStart) / 350.0F));
		return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
	}

	private static int niceCeil(int value)
	{
		if (value <= 4)
		{
			return Math.max(1, value);
		}

		int pow = 1;

		while (pow * 10 < value)
		{
			pow *= 10;
		}

		for (int m : new int[]{1, 2, 5, 10})
		{
			if (m * pow >= value)
			{
				return m * pow;
			}
		}

		return value;
	}

	private void plotSeries(GuiGraphicsExtractor ctx, int[] series, int max, int plotX, int plotY, int plotW, int plotH, int color, float animT)
	{
		if (series.length < 2)
		{
			return;
		}

		int n = series.length;
		int prevX = 0;
		int prevY = 0;

		for (int i = 0; i < n; i++)
		{
			int px = plotX + (int) Math.round((double) i / (n - 1) * plotW);
			int py = this.plotValueY(series[i], max, plotY, plotH, animT);

			if (i > 0)
			{
				this.drawLine(ctx, prevX, prevY, px, py, color);
			}

			prevX = px;
			prevY = py;
		}
	}

	// The 2px floor keeps an all-zero series readable as a flat line instead of merging into the axis
	private int plotValueY(int value, int max, int plotY, int plotH, float animT)
	{
		int usableH = Math.max(1, plotH - 2);
		return plotY + plotH - 2 - (int) Math.round((double) value / max * usableH * animT);
	}

	private void renderDashboardSkeleton(GuiGraphicsExtractor ctx, int x, int y, int w)
	{
		int tileGap = 8;
		int tileW = (w - tileGap * 5) / 6;
		int tileH = 42;

		for (int i = 0; i < 6; i++)
		{
			this.skeletonRect(ctx, x + i * (tileW + tileGap), y, tileW, tileH);
		}

		// Matches the real graph height so the layout does not jump when the stats finish loading
		int gy = y + tileH + 14;
		this.skeletonRect(ctx, x, gy, w, 150);
		gy += 150 + 14;
		this.skeletonRect(ctx, x, gy, 64, this.font.lineHeight + 2);
		gy += this.font.lineHeight + 10;

		int gap = GUTTER;
		int cols = Math.max(1, (w + gap) / (112 + gap));
		int cw = (w - gap * (cols - 1)) / cols;
		int ch = imageHeight(cw) + CAPTION_HEIGHT;
		int bottom = this.height - 6;

		while (gy + ch <= bottom)
		{
			for (int col = 0; col < cols; col++)
			{
				this.skeletonRect(ctx, x + col * (cw + gap), gy, cw, ch);
			}

			gy += ch + gap;
		}
	}

	private void skeletonRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h)
	{
		Theme.roundedRect(ctx, x, y, w, h, Theme.RADIUS_CARD, Theme.SKELETON);

		int period = 1400;
		float progress = (System.currentTimeMillis() % period) / (float) period;
		int shineX = x - 20 + Math.round(progress * (w + 40));

		ctx.enableScissor(x, y, x + w, y + h);

		for (int i = 0; i < 14; i++)
		{
			ctx.fill(shineX + i, y, shineX + i + 1, y + h, Theme.SKELETON_SHINE);
		}

		ctx.disableScissor();
	}

	private void drawLine(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int color)
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

	private static String shortDay(String iso)
	{
		return iso.length() >= 10 ? iso.substring(5).replace('-', '/') : iso;
	}

	// Feeds both the transient toasts and the bell inbox. The persisted seen timestamp advances only
	// when the user opens the bell panel, so the unread badge survives a poll
	private void pollNotifications()
	{
		String code = UploaderAccess.unlocked() ? UploaderAccess.code() : null;

		Thread worker = new Thread(() -> {
			SchematicIndexMod.LOGGER.debug("Polling notifications");
			JsonObject data = Backend.notifications(code);

			if (data == null || !data.has("items") || !data.get("items").isJsonArray())
			{
				return;
			}

			List<NotifItem> parsed = new ArrayList<>();
			long newest = 0L;

			for (JsonElement el : data.getAsJsonArray("items"))
			{
				if (el == null || !el.isJsonObject())
				{
					continue;
				}

				JsonObject item = el.getAsJsonObject();
				long at = Json.longOf(item, "at", 0L);
				String type = Json.stringOf(item, "type", "");
				parsed.add(new NotifItem(type, notifText(item, type), at));
				newest = Math.max(newest, at);
			}

			parsed.sort((a, b) -> Long.compare(b.at(), a.at()));
			long newestFinal = newest;

			Minecraft.getInstance().execute(() -> {
				this.notificationItems.clear();
				this.notificationItems.addAll(parsed);

				// The first poll starts from the persisted seen time, so offline events still toast once
				long floor = notifToastMarker < 0L ? Settings.notificationsSeenAt() : notifToastMarker;

				// A zero floor means nothing was ever seen, so skip the historical backlog
				if (floor != 0L)
				{
					int shown = 0;

					for (NotifItem item : parsed)
					{
						if (item.at() <= floor)
						{
							continue;
						}

						if (shown++ >= 5)
						{
							break;
						}

						if (item.type().equals("follow"))
						{
							Toasts.push("New follower", item.text(), new ItemStack(Items.PLAYER_HEAD));
						}
						else if (item.type().equals("like"))
						{
							Toasts.push("New like", item.text(), new ItemStack(Items.POPPY));
						}
						else if (item.type().equals("claim"))
						{
							Toasts.push("Claim reviewed", item.text(), new ItemStack(Items.WRITABLE_BOOK));
						}
						else
						{
							Toasts.push("New activity", item.text(), new ItemStack(Items.NAME_TAG));
						}
					}
				}

				// The persisted seen time stays put, so the bell keeps its badge
				notifToastMarker = Math.max(floor, newestFinal);
			});
		}, "schematicindex-notif");
		worker.setDaemon(true);
		worker.start();
	}

	// A server-side addition degrades to a generic line rather than failing
	private static String notifText(JsonObject item, String type)
	{
		String title = Json.stringOf(item, "postTitle", "your post");

		return switch (type)
		{
			case "follow" -> {
				String follower = Json.stringOf(item, "follower", Json.stringOf(item, "name", ""));
				yield (follower.isBlank() ? "Someone" : follower) + " followed you.";
			}
			case "like" -> "Someone liked " + title + ".";
			case "post", "new_post", "newpost" -> "New post: " + title + ".";
			case "claim" -> {
				String status = Json.stringOf(item, "status", "");
				String verdict = "approved".equalsIgnoreCase(status) ? "approved"
						: "denied".equalsIgnoreCase(status) ? "denied" : "updated";
				yield "Your credit claim for \"" + title + "\" was " + verdict + ".";
			}
			default -> "New activity on " + title + ".";
		};
	}

	private int unreadNotifications()
	{
		long seen = Settings.notificationsSeenAt();
		int count = 0;

		for (NotifItem item : this.notificationItems)
		{
			if (item.at() > seen)
			{
				count++;
			}
		}

		return count;
	}

	private static String relativeTime(long at)
	{
		if (at <= 0L)
		{
			return "";
		}

		long deltaMs = System.currentTimeMillis() - at;

		if (deltaMs < 60_000L)
		{
			return "just now";
		}

		long minutes = deltaMs / 60_000L;

		if (minutes < 60L)
		{
			return minutes + "m ago";
		}

		long hours = minutes / 60L;

		if (hours < 24L)
		{
			return hours + "h ago";
		}

		return (hours / 24L) + "d ago";
	}

	private void loadMyStats()
	{
		String code = UploaderAccess.code();

		if (code == null)
		{
			return;
		}

		this.myStatsLoading = true;
		this.myStatsOk = false;
		this.myStatsFailed = false;
		new Thread(() -> {
			// Rolling window ending today, so the graph never sticks to a fixed range
			int days = 30;

			JsonObject data = Backend.myStats(code, days);
			Minecraft.getInstance().execute(() -> {
				this.myStatsLoading = false;
				this.myStatsLoaded = true;

				if (data != null)
				{
					this.applyMyStats(data);
					this.myStatsOk = true;
					this.myStatsFailed = false;
					this.myStatsLoadedAt = System.currentTimeMillis();
					this.graphAnimStart = System.currentTimeMillis();
				}
				else
				{
					// Only a failed first load reaches the Retry state, since myStatsOk gates it
					this.myStatsFailed = true;
				}
			});
		}, "schematicindex-mystats").start();
	}

	private void applyMyStats(JsonObject data)
	{
		try
		{
			JsonObject totals = Json.objectOf(data, "totals");
			this.myPostsCount = Json.intOf(totals, "posts", 0);
			this.myViews = Json.intOf(totals, "views", 0);
			this.myDownloads = Json.intOf(totals, "downloads", 0);
			this.myLikes = Json.intOf(totals, "likes", 0);
			this.myFollowers = Json.intOf(totals, "followers", 0);
			this.myRating = Json.doubleOf(totals, "rating", 0.0);
			this.myRatingCount = Json.intOf(totals, "ratingCount", 0);

			this.myDays = Json.listOf(data, "days").toArray(new String[0]);

			JsonObject series = Json.objectOf(data, "series");
			this.myViewSeries = toIntArray(Json.arrayOf(series, "views"));
			this.myDownloadSeries = toIntArray(Json.arrayOf(series, "downloads"));
			this.myLikeSeries = toIntArray(Json.arrayOf(series, "likes"));
			this.myStarSeries = toIntArray(Json.arrayOf(series, "stars"));

			this.myPostSeries.clear();

			if (data.has("postSeries") && data.get("postSeries").isJsonObject())
			{
				JsonObject perPost = data.getAsJsonObject("postSeries");

				for (Map.Entry<String, JsonElement> entry : perPost.entrySet())
				{
					if (!entry.getValue().isJsonObject())
					{
						continue;
					}

					JsonObject s = entry.getValue().getAsJsonObject();
					this.myPostSeries.put(entry.getKey(), new int[][]
					{
							toIntArray(Json.arrayOf(s, "views")),
							toIntArray(Json.arrayOf(s, "downloads")),
							toIntArray(Json.arrayOf(s, "likes")),
							toIntArray(Json.arrayOf(s, "stars")),
					});
				}
			}

			if (this.selectedDashPost != null && !this.myPostSeries.containsKey(this.selectedDashPost))
			{
				this.selectedDashPost = null;
			}

			this.myPosts.clear();
			this.myPendingIds.clear();

			for (JsonElement element : Json.arrayOf(data, "posts"))
			{
				if (!element.isJsonObject())
				{
					continue;
				}

				JsonObject p = element.getAsJsonObject();
				String id = Json.stringOf(p, "id", null);

				if (id == null)
				{
					continue;
				}

				if ("pending".equals(Json.stringOf(p, "status", "")))
				{
					this.myPendingIds.add(id);
				}
				this.myPosts.add(new SchematicEntry(
						id, Json.stringOf(p, "title", ""), Json.stringOf(p, "thumbnailName", ""),
						Json.stringOf(p, "poster", ""), "", Category.fromName(Json.stringOf(p, "category", "")),
						0, 0, 0, 0, Json.intOf(p, "downloads", 0), Json.intOf(p, "likes", 0), Json.longOf(p, "postedAt", 0L),
						"", 0, -1, -1, false, Json.stringOf(p, "thumbnailUrl", null),
						List.of(), List.of(), null, null, 0L, false, 0.0, Json.intOf(p, "views", 0),
					0.0, 0, 0, List.of()));
			}

			this.dashCacheDirty = true;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not apply my stats", e);
		}
	}

	// The backend keys claims off X-Session, so callers gate on McAuth.verified()
	private void loadMyClaims()
	{
		this.myClaimsLoading = true;
		Thread worker = new Thread(() -> {
			SchematicIndexMod.LOGGER.debug("Loading my claims");
			JsonObject data = Backend.myClaims();
			Minecraft.getInstance().execute(() -> {
				this.myClaimsLoading = false;

				if (data == null || !data.has("claims") || !data.get("claims").isJsonArray())
				{
					this.myClaimsFailed = data == null;
					this.myClaimsLoaded = true;
					return;
				}

				List<ClaimItem> items = new ArrayList<>();

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
					items.add(new ClaimItem(postId, title, status));
				}

				this.myClaims = List.copyOf(items);
				this.myClaimsFailed = false;
				this.myClaimsLoaded = true;
				this.dashCacheDirty = true;
			});
		}, "schematicindex-myclaims");
		worker.setDaemon(true);
		worker.start();
	}

	private static int[] toIntArray(JsonArray array)
	{
		int[] out = new int[array.size()];

		for (int i = 0; i < array.size(); i++)
		{
			out[i] = Json.asInt(array.get(i), 0);
		}

		return out;
	}

	// A MultiLineEditBox wraps to the width it was built with, so a resize needs a fresh box
	public MultiLineEditBox ensureMultiline(MultiLineEditBox box, int width, int height)
	{
		if (box != null && box.getWidth() == width && box.getHeight() == height)
		{
			return box;
		}

		String value = box == null ? "" : box.getValue();

		if (box != null)
		{
			this.removeWidget(box);
		}

		MultiLineEditBox rebuilt = MultiLineEditBox.builder()
				.setPlaceholder(Component.literal("Description"))
				.setTextColor(Theme.TEXT)
				.setTextShadow(false)
				.setShowBackground(false)
				.setShowDecorations(false)
				.build(this.font, width, height, Component.literal("Description"));
		rebuilt.setCharacterLimit(DESC_CHAR_LIMIT);
		rebuilt.setValue(value);
		this.addWidget(rebuilt);
		return rebuilt;
	}

	private void renderSettings(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
	{
		int top = TOP_BAR_HEIGHT + 12;
		int bottom = this.height - OUTER_MARGIN;
		int formWidth = Math.min(this.contentWidth - 48, 420);
		int formX = this.contentX + 24;

		ctx.enableScissor(this.contentX, top, this.contentX + this.contentWidth, bottom);

		int startY = top - Math.round(this.scroll);
		int y = startY;

		Theme.textScaled(ctx, this.font, Theme.bold("Settings"), formX, y, 1.5F, Theme.TEXT);
		y += 36;

		y = this.settingsSectionHeader(ctx, "General", formX, y);

		y = this.settingRow(ctx, this.soundsToggle, "Sound effects",
				"Toggle on/off sound effects like button clicks when opening menus.",
				Settings.sounds(), formX, y, formWidth, mouseX, mouseY);
		y = this.volumeSlider(ctx, formX, y, formWidth, mouseX, mouseY);
		y = this.settingRow(ctx, this.overwriteToggle, "Confirm before overwriting",
				"Confirm first when overwriting a file that contains the same name, when downloading "
						+ "a new schematic.", Settings.confirmOverwrite(),
				formX, y, formWidth, mouseX, mouseY);

		y = this.settingsSectionHeader(ctx, "Appearance", formX, y);
		y = this.settingsDescription(ctx, "Changing the grid density decides how many posts can fit on one row, "
				+ "compact provides more posts but smaller thumbnails, while large gives you a great view of "
				+ "the thumbnails.", 5, formX, y, formWidth);

		int densityWidth = this.font.width(Theme.bold("Grid: Comfortable")) + 16;
		this.gridDensityButton.set(formX, y, densityWidth, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.gridDensityButton, "Grid: " + Settings.gridDensityLabel(), mouseX, mouseY, false);
		y += FIELD_HEIGHT + SETTINGS_ROW_GAP;

		y = this.settingRow(ctx, this.modTagsToggle, "Mod Icon On Nameplate",
				"Enable / Disable the icon showing on your nametag.",
				Settings.modTags(), formX, y, formWidth, mouseX, mouseY);

		y = this.settingRow(ctx, this.ownNametagToggle, "View Nametag in 3rd Person",
				"Show your own nametag above your head in third person, so you can see your cosmetics.",
				Settings.ownNametag(), formX, y, formWidth, mouseX, mouseY);

		y = this.settingsSectionHeader(ctx, "Preview", formX, y);
		y = this.settingsDescription(ctx, "Field of view for the 3D preview. Lower values zoom in, higher "
				+ "values show more of the build at once.", 5, formX, y, formWidth);
		y = this.fovSlider(ctx, formX, y, formWidth, mouseX, mouseY);

		y = this.settingsSectionHeader(ctx, "Notifications", formX, y);
		y = this.settingRow(ctx, this.toastsToggle, "Show toasts",
				"Show the slide-in notification cards in the corner for things like downloads and follows.",
				Settings.toasts(), formX, y, formWidth, mouseX, mouseY);
		y = this.settingRow(ctx, this.notificationsToggle, "New post alerts",
				"Get a toast when a creator you follow posts a new schematic.",
				Settings.notifications(), formX, y, formWidth, mouseX, mouseY);

		if (UploaderAccess.unlocked())
		{
			y = this.settingRow(ctx, this.creatorNotificationsToggle, "Follow & like alerts",
					"As a creator, get a toast when someone follows you or likes one of your posts.",
					Settings.creatorAlerts(), formX, y, formWidth, mouseX, mouseY);
		}
		else
		{
			this.creatorNotificationsToggle.set(0, 0, 0, 0);
		}

		y = this.settingsSectionHeader(ctx, "Controls", formX, y);
		y = this.settingsDescription(ctx, "Keybinds are in Options > Controls > Schematic Index.", 1, formX, y, formWidth);

		y = this.settingsSectionHeader(ctx, "Downloads", formX, y);
		y = this.settingsDescription(ctx, "The Download folder is meant to be your Schematic folder where you can "
				+ "easily store or access your schematics in Litematica. It is automatically assigned to this "
				+ "client's schematic folder, but you can change it if you prefer to download to a different "
				+ "location.", 6, formX, y, formWidth);

		for (String row : this.wrap(Settings.downloadDirectory().toString(), formWidth, 2))
		{
			Theme.text(ctx, this.font, row, formX, y, Theme.TEXT_MUTE);
			y += this.font.lineHeight + 2;
		}

		y += SETTINGS_HINT_GAP;
		int changeWidth = this.font.width(Theme.bold("Change")) + 16;
		int openWidth = this.font.width(Theme.bold("Open folder")) + 16;
		this.changeFolderButton.set(formX, y, changeWidth, FIELD_HEIGHT);
		this.openFolderButton.set(formX + changeWidth + 6, y, openWidth, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.changeFolderButton, "Change", mouseX, mouseY, false);
		Buttons.pill(ctx, this.font, this.openFolderButton, "Open folder", mouseX, mouseY, false);

		if (Settings.hasCustomDownloadDirectory())
		{
			int resetWidth = this.font.width(Theme.bold("Reset")) + 16;
			this.resetFolderButton.set(this.openFolderButton.x + openWidth + 6, y, resetWidth, FIELD_HEIGHT);
			Buttons.pill(ctx, this.font, this.resetFolderButton, "Reset", mouseX, mouseY, false);
		}
		else
		{
			this.resetFolderButton.set(0, 0, 0, 0);
		}

		y += FIELD_HEIGHT + SETTINGS_ROW_GAP;
		y = this.settingsSectionHeader(ctx, "Storage", formX, y);
		y = this.settingsDescription(ctx, "Clearing the cache frees the memory used by loaded thumbnails and 3D "
				+ "previews; they reload when next shown.", 4, formX, y, formWidth);

		int clearWidth = this.font.width(Theme.bold("Clear cache")) + 16;
		this.clearCacheButton.set(formX, y, clearWidth, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.clearCacheButton, "Clear cache", mouseX, mouseY, false);
		y += FIELD_HEIGHT + SETTINGS_ROW_GAP;

		y = this.settingsSectionHeader(ctx, "Terms of service", formX, y);
		y = this.settingsDescription(ctx, "Review the terms you agreed to. If you decline you'll return "
				+ "to the Litematica menu and must agree again to use the online features.", 3, formX, y, formWidth);

		int termsWidth = this.font.width(Theme.bold("Review terms")) + 16;
		this.termsButton.set(formX, y, termsWidth, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.termsButton, "Review terms", mouseX, mouseY, false);
		y += FIELD_HEIGHT + SETTINGS_ROW_GAP;
		y = this.settingRow(ctx, this.usageDataToggle, TermsModal.USAGE_DATA_LABEL,
				"Sends which features you use so we can see what to improve.",
				Settings.usageData(), formX, y, formWidth, mouseX, mouseY);

		ctx.disableScissor();

		int contentHeight = y - startY;
		this.maxScroll = Math.max(0.0F, contentHeight - (bottom - top));
		this.scroll = Math.min(this.scroll, this.maxScroll);

		this.verticalScrollbar(ctx, SCROLLBAR_MAIN, this.scroll, this.maxScroll, top, bottom - top,
				this.contentX + this.contentWidth);

		int rightX = formX + formWidth + 24;
		int rightWidth = this.contentX + this.contentWidth - rightX;

		if (rightWidth >= 170)
		{
			this.renderCreditsPanel(ctx, rightX, top, rightWidth, bottom);
		}
	}

	private void renderCreditsPanel(GuiGraphicsExtractor ctx, int x, int top, int width, int bottom)
	{
		ctx.enableScissor(x, top, x + width, bottom);

		int y = top;
		Theme.text(ctx, this.font, Theme.bold("Credits"), x, y, Theme.TEXT);
		y += this.font.lineHeight + 8;

		List<RemoteContent.Credit> credits = RemoteContent.credits();

		if (credits.isEmpty())
		{
			String message = RemoteContent.loaded() ? "No credits yet." : "Loading credits.";
			Theme.text(ctx, this.font, message, x, y, Theme.TEXT_MUTE);
			ctx.disableScissor();
			return;
		}

		int head = 24;

		for (RemoteContent.Credit credit : credits)
		{
			if (y > bottom)
			{
				break;
			}

			Identifier avatar = ImageStore.avatar(credit.avatarUrl());

			if (avatar != null)
			{
				Theme.image(ctx, avatar, x, y, head, head, 64, 64);
			}
			else
			{
				Theme.roundedRect(ctx, x, y, head, head, 4, Theme.SURFACE_ELEVATED);
			}

			int textX = x + head + 8;
			int textWidth = x + width - textX;
			Theme.text(ctx, this.font, Theme.bold(Theme.clipBold(this.font, credit.displayName(), textWidth)),
					textX, y + 2, Theme.TEXT);

			if (!credit.role().isBlank())
			{
				Theme.text(ctx, this.font, Theme.clip(this.font, credit.role(), textWidth),
						textX, y + 2 + this.font.lineHeight + 2, Theme.ACCENT_BRIGHT);
			}

			int lineY = y + head + 4;

			for (String row : this.wrap(credit.description(), width, 4))
			{
				Theme.text(ctx, this.font, row, x, lineY, Theme.TEXT_ASH);
				lineY += this.font.lineHeight + 2;
			}

			y = lineY + 12;
		}

		ctx.disableScissor();
	}

	private void renderCosmetics(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick)
	{
		int top = TOP_BAR_HEIGHT + 12;
		int bottom = this.height - OUTER_MARGIN;
		int formWidth = Math.min(this.contentWidth - 48, 420);
		int formX = this.contentX + 24;

		ctx.enableScissor(this.contentX, top, this.contentX + this.contentWidth, bottom);

		int startY = top - Math.round(this.scroll);
		int y = startY;

		// A refunded colour must not keep styling the name, so the loadout is reconciled before it is drawn
		Cosmetics.pruneUnowned();
		this.easeCosmeticScroll();
		this.cosmeticEffectHover = this.hoveredEffect(mouseX, mouseY);

		Theme.textScaled(ctx, this.font, Theme.bold("Nametag Cosmetics"), formX, y, 1.5F, Theme.TEXT);
		y += 36;

		y = this.settingsDescription(ctx, "Use Colours & Tags, to customize your name exactly how you want "
				+ "to, your nametag is visible to all who are playing with the mod, including your cosmetic "
				+ "changes.", 4, formX, y, formWidth);

		y = this.renderCosmeticPreview(ctx, formX, y, formWidth);

		y = this.settingsSectionHeader(ctx, "Style", formX, y);

		int segment = (formWidth - 6) / 2;
		this.cosmeticOffButton.set(formX, y, segment, FIELD_HEIGHT);
		this.cosmeticGradientButton.set(formX + segment + 6, y, formWidth - segment - 6, FIELD_HEIGHT);
		Buttons.pill(ctx, this.font, this.cosmeticOffButton, "Off", mouseX, mouseY,
				Cosmetics.mode() == Cosmetics.Mode.NONE);
		Buttons.pill(ctx, this.font, this.cosmeticGradientButton, "Gradient", mouseX, mouseY,
				Cosmetics.mode() == Cosmetics.Mode.GRADIENT);
		y += FIELD_HEIGHT + SETTINGS_ROW_GAP;

		y = this.renderPalette(ctx, formX, y, formWidth, mouseX, mouseY);
		y = this.renderColorPicker(ctx, formX, y, formWidth, mouseX, mouseY, partialTick);
		y = this.renderPresets(ctx, formX, y, formWidth, mouseX, mouseY);
		y = this.renderEffects(ctx, formX, y, formWidth, mouseX, mouseY);
		y = this.renderTags(ctx, formX, y, formWidth, mouseX, mouseY);
		this.renderCarriedSlot(ctx);

		ctx.disableScissor();

		int contentHeight = y - startY;
		this.maxScroll = Math.max(0.0F, contentHeight - (bottom - top));
		this.scroll = Math.min(this.scroll, this.maxScroll);

		if (this.cosmeticRevealBuy)
		{
			this.cosmeticRevealBuy = false;
			this.revealCosmeticButton(this.cosmeticPreviewTag != CosmeticTags.NONE
					? this.cosmeticTagBuyButton : this.cosmeticPresetBuyButton, bottom);
		}

		this.verticalScrollbar(ctx, SCROLLBAR_MAIN, this.scroll, this.maxScroll, top, bottom - top,
				this.contentX + this.contentWidth);
	}

	// Scrolls down only, and only as far as the button's bottom needs to clear the viewport edge
	private void revealCosmeticButton(Rect button, int bottom)
	{
		float overflow = button.y + button.height + SETTINGS_ROW_GAP - bottom;

		if (button.height <= 0 || overflow <= 0.0F)
		{
			return;
		}

		this.cosmeticScrollFrom = this.scroll;
		this.cosmeticScrollTarget = Math.min(this.maxScroll, this.scroll + overflow);
		this.cosmeticScrollStartedAt = Util.getMillis();
	}

	private void easeCosmeticScroll()
	{
		if (this.cosmeticScrollTarget < 0.0F)
		{
			return;
		}

		float fraction = Theme.easeOut((Util.getMillis() - this.cosmeticScrollStartedAt) / (float) COSMETIC_REVEAL_MS);
		float target = Math.min(this.cosmeticScrollTarget, this.maxScroll);
		this.scroll = this.cosmeticScrollFrom + (target - this.cosmeticScrollFrom) * fraction;

		if (fraction >= 1.0F)
		{
			this.cosmeticScrollTarget = -1.0F;
		}
	}

	// Stands in for the in-world nametag, which the game never draws for the local player. A previewed preset
	// wins over the worn loadout, and the current team wraps the name exactly as the nametag mixin does
	private int renderCosmeticPreview(GuiGraphicsExtractor ctx, int formX, int y, int formWidth)
	{
		int previewHeight = 46;
		Theme.roundedRect(ctx, formX, y, formWidth, previewHeight, Theme.RADIUS_CARD, Theme.SURFACE_CARD);
		Theme.roundedOutline(ctx, formX, y, formWidth, previewHeight, Theme.RADIUS_CARD, Theme.HAIRLINE);

		String name = this.minecraft != null && this.minecraft.getUser() != null
				? this.minecraft.getUser().getName() : "Fudgedy";
		boolean shine = Cosmetics.hasShine() || this.previewsEffect(Cosmetics.SHINE);
		boolean flow = Cosmetics.hasFlow() || this.previewsEffect(Cosmetics.FLOW);
		Component styled = PlayerTeam.formatNameForTeam(
				this.minecraft == null || this.minecraft.player == null ? null : this.minecraft.player.getTeam(),
				this.cosmeticPreviewPreset >= 0
						? Cosmetics.preview(name, Cosmetics.PRESETS.get(this.cosmeticPreviewPreset).stops(), Style.EMPTY, shine, flow)
						: Cosmetics.apply(name, Style.EMPTY, shine, flow));
		CosmeticTags.Tag previewTag = this.previewedShopTag();
		Component worn = previewTag != null ? Cosmetics.tagOf(previewTag) : Cosmetics.tag();
		Component preview = worn == null ? styled
				: Component.empty().append(worn).append(Component.literal(" ")).append(styled);
		int previewTextWidth = Math.max(1, this.font.width(preview));
		int drawX = formX + (formWidth - previewTextWidth) / 2;
		int drawY = y + (previewHeight - this.font.lineHeight) / 2;
		float previewScale = Math.min(2.0F, (formWidth - 12) / (float) previewTextWidth);
		Theme.pushScale(ctx, drawX, drawY, previewTextWidth, this.font.lineHeight, previewScale);
		Theme.text(ctx, this.font, preview, drawX, drawY, Theme.TEXT);
		Theme.pop(ctx);

		return y + previewHeight + SETTINGS_ROW_GAP;
	}

	// The owned palette: every slot carries a white border, an unowned one stays blank. In gradient mode the
	// three gradient slots leave the grid and sit apart as their own horizontal strip
	private int renderPalette(GuiGraphicsExtractor ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		boolean gradient = Cosmetics.mode() == Cosmetics.Mode.GRADIENT;
		int max = Math.min(CosmeticColors.max(), PALETTE_SLOTS);
		int columns = PALETTE_SLOTS / PALETTE_ROWS;

		y = this.settingsSectionHeader(ctx, "Your palette", formX, y);
		y = this.settingsDescription(ctx, "You own " + CosmeticColors.count() + " of " + max + " colours. A "
				+ "colour is fixed once bought" + (gradient
						? ". Click one to send it to the gradient; drag the gradient slots to reorder, click one to empty it."
						: ". Click one to wear it."), 3, formX, y, formWidth);

		this.layOutPalette(max);

		int slotIndex = 0;

		for (int row = 0; row < PALETTE_ROWS; row++)
		{
			for (int column = 0; column < columns && slotIndex < max; column++, slotIndex++)
			{
				int sx = formX + column * (PALETTE_SWATCH + PALETTE_GAP);
				int sy = y + row * (PALETTE_SWATCH + PALETTE_GAP);
				this.cosmeticSlotRects[slotIndex].set(sx, sy, PALETTE_SWATCH, PALETTE_SWATCH);
				int id = this.cosmeticSlotIds[slotIndex];
				boolean inGradient = id != Cosmetics.EMPTY && Cosmetics.isInGradient(id);
				this.renderSlot(ctx, sx, sy, CosmeticColors.hexOf(id), inGradient, 255);

				if (id != Cosmetics.EMPTY && id == this.cosmeticSelected)
				{
					Theme.roundedOutline(ctx, sx - 3, sy - 3, PALETTE_SWATCH + 6, PALETTE_SWATCH + 6,
							Theme.RADIUS_CARD, Theme.SHARD);
				}
			}
		}

		for (int i = slotIndex; i < this.cosmeticSlotRects.length; i++)
		{
			this.cosmeticSlotRects[i].set(0, 0, 0, 0);
		}

		int gridHeight = PALETTE_ROWS * PALETTE_SWATCH + (PALETTE_ROWS - 1) * PALETTE_GAP;

		if (gradient)
		{
			this.renderGradientStrip(ctx, formX + columns * (PALETTE_SWATCH + PALETTE_GAP) + GRADIENT_STRIP_GAP,
					y + (gridHeight - PALETTE_SWATCH) / 2, mouseX, mouseY);
		}
		else
		{
			for (Rect slot : this.cosmeticGradientRects)
			{
				slot.set(0, 0, 0, 0);
			}
		}

		y += gridHeight + SETTINGS_ROW_GAP;

		if (this.cosmeticSelected != Cosmetics.EMPTY && CosmeticColors.isRefundable(this.cosmeticSelected))
		{
			// the swatch and the ringed slot answer the same question from both ends of the page
			int hex = CosmeticColors.hexOf(this.cosmeticSelected);
			Theme.roundedRect(ctx, formX, y + 2, 12, 12, Theme.RADIUS_CARD, 0xFF000000 | hex);
			Theme.roundedOutline(ctx, formX, y + 2, 12, 12, Theme.RADIUS_CARD, 0xFFFFFFFF);
			String label = "Refund #" + String.format("%06X", hex);
			int width = this.shardButtonWidth(label, CosmeticColors.refundValue());
			this.cosmeticRefundButton.set(formX + 18, y, width, FIELD_HEIGHT);
			this.renderShardBuyButton(ctx, this.cosmeticRefundButton, label, CosmeticColors.refundValue(),
					mouseX, mouseY);
			y += FIELD_HEIGHT + SETTINGS_ROW_GAP;
		}
		else
		{
			this.cosmeticRefundButton.set(0, 0, 0, 0);
		}

		return y;
	}

	// The three gradient stops, left to right; an emptied one stays half-there so the strip keeps its shape
	private void renderGradientStrip(GuiGraphicsExtractor ctx, int stripX, int stripY, int mouseX, int mouseY)
	{
		Theme.text(ctx, this.font, Theme.bold("Gradient"), stripX, stripY - this.font.lineHeight - 5, Theme.TEXT_ASH);

		for (int i = 0; i < Cosmetics.GRADIENT_SLOTS; i++)
		{
			int sx = stripX + i * (PALETTE_SWATCH + PALETTE_GAP);
			this.cosmeticGradientRects[i].set(sx, stripY, PALETTE_SWATCH, PALETTE_SWATCH);
			int hex = CosmeticColors.hexOf(Cosmetics.gradientAt(i));
			boolean target = this.cosmeticGradientDrag >= 0 && this.cosmeticGradientDrag != i
					&& this.cosmeticGradientRects[i].contains(mouseX, mouseY);

			// the lifted slot leaves a hollow behind it and is redrawn under the pointer once the page is done
			if (i == this.cosmeticGradientDrag)
			{
				this.renderSlot(ctx, sx, stripY, -1, false, 128);
				continue;
			}

			this.renderSlot(ctx, sx, stripY, hex, target, hex < 0 ? 128 : 255);
		}
	}

	// Fills the slot-to-colour map the click handler reads back; a gradient slot only points at a colour
	// here, it never takes one out of the palette
	private void layOutPalette(int max)
	{
		Arrays.fill(this.cosmeticSlotIds, Cosmetics.EMPTY);
		int slot = 0;

		for (CosmeticColors.Owned owned : CosmeticColors.colors())
		{
			if (slot >= max)
			{
				break;
			}

			this.cosmeticSlotIds[slot++] = owned.id();
		}
	}

	private void renderCarriedSlot(GuiGraphicsExtractor ctx)
	{
		if (this.cosmeticGradientDrag < 0)
		{
			return;
		}

		int hex = CosmeticColors.hexOf(Cosmetics.gradientAt(this.cosmeticGradientDrag));
		this.renderSlot(ctx, this.cosmeticDragX - PALETTE_SWATCH / 2, this.cosmeticDragY - PALETTE_SWATCH / 2,
				hex, true, 220);
	}

	// A white edge one pixel wider says this colour is in the gradient; purple is the refund pick
	private void renderSlot(GuiGraphicsExtractor ctx, int x, int y, int hex, boolean marked, int alpha)
	{
		int fill = hex < 0 ? Theme.SURFACE_CARD : 0xFF000000 | hex;
		Theme.roundedRect(ctx, x, y, PALETTE_SWATCH, PALETTE_SWATCH, Theme.RADIUS_CARD, withAlpha(fill, alpha));
		Theme.roundedOutline(ctx, x, y, PALETTE_SWATCH, PALETTE_SWATCH, Theme.RADIUS_CARD,
				withAlpha(0xFFFFFFFF, alpha));

		if (!marked)
		{
			return;
		}

		Theme.roundedOutline(ctx, x - 1, y - 1, PALETTE_SWATCH + 2, PALETTE_SWATCH + 2, Theme.RADIUS_CARD,
				withAlpha(0xFFFFFFFF, alpha));
	}

	private static int withAlpha(int argb, int alpha)
	{
		return (argb & 0x00FFFFFF) | (Math.min(255, alpha) << 24);
	}

	// The picker plus its Buy button; the button only appears once a colour has been chosen
	private int renderColorPicker(GuiGraphicsExtractor ctx, int formX, int y, int formWidth, int mouseX, int mouseY,
			float partialTick)
	{
		y = this.settingsSectionHeader(ctx, "Buy a colour", formX, y);

		if (CosmeticColors.count() >= Math.min(CosmeticColors.max(), PALETTE_SLOTS))
		{
			this.cosmeticPicker.hide();
			this.cosmeticBuyButton.set(0, 0, 0, 0);
			return this.settingsDescription(ctx, "Your palette is full. Refund a colour to make room.",
					1, formX, y, formWidth);
		}

		int squareSize = 92;
		this.cosmeticPicker.renderSquare(ctx, formX, y, squareSize);
		Fields.single(ctx, this.cosmeticHexBox, formX + squareSize + 12, y, 74, FIELD_HEIGHT,
				mouseX, mouseY, partialTick);

		int sideX = formX + squareSize + 12;
		int swatchY = y + FIELD_HEIGHT + 8;
		this.renderSlot(ctx, sideX, swatchY, this.cosmeticPickColor, false, 255);

		if (this.cosmeticPicked)
		{
			int price = CosmeticColors.nextPrice();
			int buttonY = swatchY + PALETTE_SWATCH + 8;

			if (price > 0)
			{
				this.cosmeticBuyButton.set(sideX, buttonY, this.shardButtonWidth("Buy", price), FIELD_HEIGHT);
				this.renderShardBuyButton(ctx, this.cosmeticBuyButton, "Buy", price, mouseX, mouseY);
			}
			else
			{
				this.cosmeticBuyButton.set(sideX, buttonY, this.font.width(Theme.bold("Free")) + 24, FIELD_HEIGHT);
				Buttons.pill(ctx, this.font, this.cosmeticBuyButton, "Free", mouseX, mouseY, true);
			}
		}
		else
		{
			this.cosmeticBuyButton.set(0, 0, 0, 0);
		}

		y += squareSize + 6;

		int hueHeight = 10;
		this.cosmeticPicker.renderHueBar(ctx, formX, y, squareSize, hueHeight);

		return y + hueHeight + SETTINGS_ROW_GAP;
	}

	// Presets show the name first and only then offer the buy, so nobody pays for a palette sight unseen
	private int renderPresets(GuiGraphicsExtractor ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		y = this.settingsSectionHeader(ctx, "Presets", formX, y);
		y = this.settingsDescription(ctx, "A preset is a ready made gradient which you can buy for a discount "
				+ "compared to buying colours separately. Click on a preset to preview it above. Owned presets "
				+ "have a dot in the top right corner.", 4, formX, y, formWidth);

		int perRow = 5;
		int presetWidth = (formWidth - (perRow - 1) * 6) / perRow;
		int rows = 0;

		for (int i = 0; i < Cosmetics.PRESETS.size(); i++)
		{
			rows = i / perRow;
			int px = formX + i % perRow * (presetWidth + 6);
			int py = y + rows * (FIELD_HEIGHT + 6);
			Rect.pooled(this.cosmeticPresetRects, i).set(px, py, presetWidth, FIELD_HEIGHT);
			this.renderPresetSwatch(ctx, Cosmetics.PRESETS.get(i), px, py, presetWidth, FIELD_HEIGHT, mouseX, mouseY,
					i == this.cosmeticPreviewPreset, ownsPreset(Cosmetics.PRESETS.get(i)));
		}

		y += (rows + 1) * (FIELD_HEIGHT + 6) - 6 + SETTINGS_ROW_GAP;

		if (this.cosmeticPreviewPreset < 0)
		{
			this.cosmeticPresetBuyButton.set(0, 0, 0, 0);
			this.cosmeticPresetWearButton.set(0, 0, 0, 0);
			return y;
		}

		Cosmetics.Preset preset = Cosmetics.PRESETS.get(this.cosmeticPreviewPreset);

		if (ownsPreset(preset))
		{
			this.cosmeticPresetBuyButton.set(0, 0, 0, 0);
			String wear = "Wear (" + preset.name() + ")";
			int wearWidth = this.font.width(Theme.bold(wear)) + 24;
			this.cosmeticPresetWearButton.set(formX, y, wearWidth, FIELD_HEIGHT);
			Buttons.pill(ctx, this.font, this.cosmeticPresetWearButton, wear, mouseX, mouseY, true);
			return y + FIELD_HEIGHT + SETTINGS_ROW_GAP;
		}

		this.cosmeticPresetWearButton.set(0, 0, 0, 0);

		int width = this.shardButtonWidth("Buy", CosmeticColors.presetPrice());
		this.cosmeticPresetBuyButton.set(formX, y, width, FIELD_HEIGHT);
		this.renderShardBuyButton(ctx, this.cosmeticPresetBuyButton, "Buy", CosmeticColors.presetPrice(),
				mouseX, mouseY);

		return y + FIELD_HEIGHT + SETTINGS_ROW_GAP;
	}

	// One card per effect; hovering or selecting it plays the effect on the preview, so it is seen before it is bought
	private int renderEffects(GuiGraphicsExtractor ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		y = this.settingsSectionHeader(ctx, "Effects", formX, y);
		y = this.settingsDescription(ctx, "Effects play over whatever colours you wear.", 1, formX, y, formWidth);

		for (int i = 0; i < EFFECT_IDS.length; i++)
		{
			y = this.renderEffectCard(ctx, i, formX, y, formWidth, mouseX, mouseY);
		}

		return y;
	}

	private int renderEffectCard(GuiGraphicsExtractor ctx, int index, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		String id = EFFECT_IDS[index];
		int cardHeight = FIELD_HEIGHT + 12;
		boolean worn = Cosmetics.effects().contains(id);
		Rect card = this.cosmeticEffectCards[index];
		Rect button = this.cosmeticEffectButtons[index];
		card.set(formX, y, formWidth, cardHeight);
		boolean hovered = card.contains(mouseX, mouseY);
		boolean selected = index == this.cosmeticPreviewEffect;
		Theme.roundedRect(ctx, formX, y, formWidth, cardHeight, Theme.RADIUS_CARD,
				Theme.lighten(Theme.SURFACE_CARD, hovered ? 0.06F : 0.0F));
		Theme.roundedOutline(ctx, formX, y, formWidth, cardHeight, Theme.RADIUS_CARD,
				worn || selected ? Theme.ACCENT_BRIGHT : hovered ? Theme.SHARD : Theme.HAIRLINE);

		int textY = y + (cardHeight - this.font.lineHeight) / 2 + 1;
		Theme.itemScaled(ctx, new ItemStack(EFFECT_ICONS[index]), formX + 6, y + (cardHeight - 16) / 2, 1.0F);
		String title = Theme.bold(EFFECT_LABELS[index]);
		int textX = formX + 28;
		Theme.text(ctx, this.font, title, textX, textY, Theme.TEXT);
		textX += this.font.width(title);

		if (CosmeticColors.ownsEffect(id))
		{
			Theme.text(ctx, this.font, " \u00b7 Owned", textX, textY, Theme.TEXT_ASH);
			String label = worn ? "Remove" : "Wear";
			int width = this.font.width(Theme.bold(label)) + 24;
			button.set(formX + formWidth - 6 - width, y + (cardHeight - FIELD_HEIGHT) / 2, width, FIELD_HEIGHT);
			Buttons.pill(ctx, this.font, button, label, mouseX, mouseY, !worn);
			return y + cardHeight + SETTINGS_ROW_GAP;
		}

		int price = CosmeticColors.effectPrice(id);
		Theme.text(ctx, this.font, " \u00b7 " + price, textX, textY, Theme.TEXT_ASH);
		int width = this.shardButtonWidth("Buy", price);
		button.set(formX + formWidth - 6 - width, y + (cardHeight - FIELD_HEIGHT) / 2, width, FIELD_HEIGHT);
		this.renderShardBuyButton(ctx, button, "Buy", price, mouseX, mouseY);
		return y + cardHeight + SETTINGS_ROW_GAP;
	}

	private int hoveredEffect(int mouseX, int mouseY)
	{
		for (int i = 0; i < this.cosmeticEffectCards.length; i++)
		{
			if (this.cosmeticEffectCards[i].contains(mouseX, mouseY))
			{
				return i;
			}
		}

		return -1;
	}

	private boolean previewsEffect(String id)
	{
		return (this.cosmeticEffectHover >= 0 && EFFECT_IDS[this.cosmeticEffectHover].equals(id))
				|| (this.cosmeticPreviewEffect >= 0 && EFFECT_IDS[this.cosmeticPreviewEffect].equals(id));
	}

	// Earned tags: staff author them, a player only wears one. Owned tags toggle on click; a shop tag is
	// previewed first and only then offers its buy, matching how presets are sold
	private int renderTags(GuiGraphicsExtractor ctx, int formX, int y, int formWidth, int mouseX, int mouseY)
	{
		y = this.settingsSectionHeader(ctx, "Tags", formX, y);
		y = this.settingsDescription(ctx, "Show off your nametag with a cool tag alongside it.",
				1, formX, y, formWidth);

		this.cosmeticTagIds.clear();

		if (CosmeticTags.owned().isEmpty() && CosmeticTags.shop().isEmpty())
		{
			this.cosmeticTagBuyButton.set(0, 0, 0, 0);
			return this.settingsDescription(ctx, "You have no tags yet.", 1, formX, y, formWidth);
		}

		int chip = 0;

		if (!CosmeticTags.owned().isEmpty())
		{
			y = this.settingsSectionHeader(ctx, "Owned", formX, y);
			chip = this.renderTagRow(ctx, CosmeticTags.owned(), chip, formX, y, formWidth, mouseX, mouseY, true);
			y = this.tagRowBottom(chip, y);
		}

		if (!CosmeticTags.shop().isEmpty())
		{
			y = this.settingsSectionHeader(ctx, "Unowned", formX, y);
			int before = chip;
			chip = this.renderTagRow(ctx, CosmeticTags.shop(), chip, formX, y, formWidth, mouseX, mouseY, false);
			y = this.tagRowBottom(chip - before, y);
		}

		CosmeticTags.Tag previewed = this.previewedShopTag();

		if (previewed == null)
		{
			this.cosmeticTagBuyButton.set(0, 0, 0, 0);
			return y;
		}

		int width = this.shardButtonWidth("Buy", previewed.price());
		this.cosmeticTagBuyButton.set(formX, y, width, FIELD_HEIGHT);
		this.renderShardBuyButton(ctx, this.cosmeticTagBuyButton, "Buy", previewed.price(), mouseX, mouseY);

		return y + FIELD_HEIGHT + SETTINGS_ROW_GAP;
	}

	// Lays one wrapped run of tag chips; returns the next free chip slot so the two sections share the map
	private int renderTagRow(GuiGraphicsExtractor ctx, List<CosmeticTags.Tag> tags, int chip, int formX, int y,
			int formWidth, int mouseX, int mouseY, boolean owned)
	{
		int x = formX;
		int row = y;

		for (CosmeticTags.Tag tag : tags)
		{
			Component drawn = Cosmetics.tagOf(tag);
			int width = Math.min(this.font.width(drawn) + 14, formWidth);

			if (x + width > formX + formWidth)
			{
				x = formX;
				row += FIELD_HEIGHT + 6;
			}

			boolean marked = owned ? tag.id() == CosmeticTags.equipped() : tag.id() == this.cosmeticPreviewTag;
			Rect.pooled(this.cosmeticTagRects, chip).set(x, row, width, FIELD_HEIGHT);
			this.cosmeticTagIds.add(tag.id());
			this.renderTagChip(ctx, drawn, x, row, width, marked, mouseX, mouseY);
			x += width + 6;
			chip++;
		}

		this.cosmeticTagRowBottom = row;
		return chip;
	}

	private int tagRowBottom(int drawn, int y)
	{
		return drawn == 0 ? y : this.cosmeticTagRowBottom + FIELD_HEIGHT + SETTINGS_ROW_GAP;
	}

	private void renderTagChip(GuiGraphicsExtractor ctx, Component drawn, int x, int y, int width, boolean marked,
			int mouseX, int mouseY)
	{
		boolean hovered = Theme.inside(mouseX, mouseY, x, y, width, FIELD_HEIGHT);
		Theme.roundedRect(ctx, x, y, width, FIELD_HEIGHT, Theme.RADIUS_PILL,
				Theme.lighten(Theme.SURFACE_CARD, hovered ? 0.12F : 0.0F));
		Theme.roundedOutline(ctx, x, y, width, FIELD_HEIGHT, Theme.RADIUS_PILL,
				marked ? Theme.ACCENT_BRIGHT : Theme.HAIRLINE);
		// A styled component has no ellipsis form, so an over-long tag is cut at the chip edge instead
		ctx.enableScissor(x, y, x + width - 4, y + FIELD_HEIGHT);
		Theme.text(ctx, this.font, drawn, x + 7, y + (FIELD_HEIGHT - this.font.lineHeight) / 2 + 1, Theme.TEXT);
		ctx.disableScissor();
	}

	private CosmeticTags.Tag previewedShopTag()
	{
		for (CosmeticTags.Tag tag : CosmeticTags.shop())
		{
			if (tag.id() == this.cosmeticPreviewTag)
			{
				return tag;
			}
		}

		return null;
	}

	// An owned tag toggles on and off; a shop tag only arms the preview, so nothing is bought by one click
	private boolean clickTag(int id)
	{
		this.setFocused(null);

		for (CosmeticTags.Tag tag : CosmeticTags.owned())
		{
			if (tag.id() == id)
			{
				this.cosmeticPreviewTag = CosmeticTags.NONE;
				CosmeticTags.equip(id == CosmeticTags.equipped() ? CosmeticTags.NONE : id,
						this::rebuildCosmetics);
				Theme.click();
				return true;
			}
		}

		this.cosmeticPreviewTag = this.cosmeticPreviewTag == id ? CosmeticTags.NONE : id;
		this.cosmeticPreviewPreset = -1;
		this.cosmeticRevealBuy = this.cosmeticPreviewTag != CosmeticTags.NONE;
		Theme.click();
		return true;
	}

	private void renderPresetSwatch(GuiGraphicsExtractor ctx, Cosmetics.Preset preset, int x, int y, int width,
			int height, int mouseX, int mouseY, boolean previewing, boolean owned)
	{
		int[] stops = preset.stops();

		for (int sx = 0; sx < width; sx++)
		{
			float t = width <= 1 ? 0.0F : (float) sx / (width - 1);
			ctx.fill(x + sx, y, x + sx + 1, y + height, 0xFF000000 | Cosmetics.sample(stops, t));
		}

		boolean hovered = Theme.inside(mouseX, mouseY, x, y, width, height);
		Theme.roundedOutline(ctx, x, y, width, height, Theme.RADIUS_PILL,
				hovered || previewing ? Theme.ACCENT_BRIGHT : 0xFFFFFFFF);

		if (owned)
		{
			ctx.fill(x + width - 6, y + 2, x + width - 2, y + 6, Theme.ACCENT_BRIGHT);
		}
	}

	// The server grants a preset as plain colours, so owning one is owning every hex it would hand over
	private static boolean ownsPreset(Cosmetics.Preset preset)
	{
		return CosmeticColors.ownsPreset(preset.name());
	}

	private int shardButtonWidth(String label, int price)
	{
		return 8 + this.font.width(Theme.bold(label)) + 6 + 13 + this.font.width(Theme.bold(Integer.toString(price))) + 8;
	}

	// A buy button that spells out the shard cost the way the rest of the mod does: amethyst icon plus purple price
	private void renderShardBuyButton(GuiGraphicsExtractor ctx, Rect rect, String label, int price, int mouseX, int mouseY)
	{
		boolean hovered = rect.contains(mouseX, mouseY);
		float hover = Theme.buttonHover(rect, hovered);
		float scale = Theme.buttonScale(rect, 1.0F + Theme.HOVER_SCALE * hover);
		Theme.pushScale(ctx, rect.x, rect.y, rect.width, rect.height, scale);

		Theme.roundedRect(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				Theme.lighten(Theme.SURFACE_CARD, 0.12F * hover));
		Theme.roundedOutline(ctx, rect.x, rect.y, rect.width, rect.height, Theme.RADIUS_PILL,
				hovered ? Theme.SHARD : Theme.HAIRLINE);

		int textY = rect.y + (rect.height - this.font.lineHeight) / 2 + 1;
		int tx = rect.x + 8;
		String bold = Theme.bold(label);
		Theme.text(ctx, this.font, bold, tx, textY, Theme.TEXT);
		tx += this.font.width(bold) + 6;
		Theme.itemScaled(ctx, new ItemStack(Items.AMETHYST_SHARD), tx, rect.y + (rect.height - 10) / 2, 0.6F);
		tx += 13;
		Theme.text(ctx, this.font, Theme.bold(Integer.toString(price)), tx, textY, Theme.SHARD);

		Theme.pop(ctx);
	}

	private boolean clickCosmetics(MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY)
	{
		if (this.cosmeticOffButton.contains(mouseX, mouseY))
		{
			return this.switchCosmeticMode(Cosmetics.Mode.NONE);
		}
		if (this.cosmeticGradientButton.contains(mouseX, mouseY))
		{
			return this.switchCosmeticMode(Cosmetics.Mode.GRADIENT);
		}
		if (this.cosmeticBuyButton.contains(mouseX, mouseY))
		{
			int hex = this.cosmeticPickColor;
			CosmeticColors.buy(hex, () ->
			{
				this.cosmeticPicked = false;
				this.wearNewest();
				this.rebuildCosmetics();
			});
			return true;
		}
		if (this.cosmeticRefundButton.contains(mouseX, mouseY))
		{
			int id = this.cosmeticSelected;
			CosmeticColors.refund(id, () ->
			{
				this.cosmeticSelected = Cosmetics.EMPTY;
				this.afterCosmeticChange();
			});
			return true;
		}
		if (this.cosmeticTagBuyButton.contains(mouseX, mouseY))
		{
			CosmeticTags.Tag tag = this.previewedShopTag();

			if (tag != null)
			{
				CosmeticTags.buy(tag, () ->
				{
					this.cosmeticPreviewTag = CosmeticTags.NONE;
					CosmeticTags.equip(tag.id(), this::rebuildCosmetics);
				});
			}

			return true;
		}
		for (int i = 0; i < this.cosmeticEffectButtons.length; i++)
		{
			if (this.cosmeticEffectButtons[i].contains(mouseX, mouseY))
			{
				return this.clickEffect(i);
			}
		}
		for (int i = 0; i < this.cosmeticEffectCards.length; i++)
		{
			if (this.cosmeticEffectCards[i].contains(mouseX, mouseY))
			{
				this.cosmeticPreviewEffect = this.cosmeticPreviewEffect == i ? -1 : i;
				this.setFocused(null);
				Theme.click();
				return true;
			}
		}
		if (this.cosmeticPresetWearButton.contains(mouseX, mouseY) && this.cosmeticPreviewPreset >= 0)
		{
			this.wearPreset(Cosmetics.PRESETS.get(this.cosmeticPreviewPreset));
			this.cosmeticPreviewPreset = -1;
			this.afterCosmeticChange();
			return true;
		}
		if (this.cosmeticPresetBuyButton.contains(mouseX, mouseY) && this.cosmeticPreviewPreset >= 0)
		{
			Cosmetics.Preset preset = Cosmetics.PRESETS.get(this.cosmeticPreviewPreset);
			// the preview survives the purchase so Wear is waiting where the Buy button just was
			CosmeticColors.buyPreset(preset.name(), this::rebuildCosmetics);
			return true;
		}

		// The strip is picked up rather than acted on, so the release can tell a reorder from a click
		for (int i = 0; i < this.cosmeticGradientRects.length; i++)
		{
			if (this.cosmeticGradientRects[i].contains(mouseX, mouseY))
			{
				this.cosmeticGradientDrag = i;
				this.cosmeticDragX = (int) mouseX;
				this.cosmeticDragY = (int) mouseY;
				this.setFocused(null);
				return true;
			}
		}

		for (int i = 0; i < this.cosmeticSlotRects.length; i++)
		{
			if (this.cosmeticSlotRects[i].contains(mouseX, mouseY) && this.cosmeticSlotIds[i] != Cosmetics.EMPTY)
			{
				return this.clickPaletteSlot(this.cosmeticSlotIds[i]);
			}
		}

		for (int i = 0; i < this.cosmeticTagIds.size(); i++)
		{
			if (this.cosmeticTagRects.get(i).contains(mouseX, mouseY))
			{
				return this.clickTag(this.cosmeticTagIds.get(i));
			}
		}

		for (int i = 0; i < Cosmetics.PRESETS.size() && i < this.cosmeticPresetRects.size(); i++)
		{
			if (this.cosmeticPresetRects.get(i).contains(mouseX, mouseY))
			{
				this.cosmeticPreviewPreset = this.cosmeticPreviewPreset == i ? -1 : i;
				this.cosmeticPreviewTag = CosmeticTags.NONE;
				this.cosmeticRevealBuy = this.cosmeticPreviewPreset >= 0 && !ownsPreset(Cosmetics.PRESETS.get(i));
				this.setFocused(null);
				Theme.click();
				return true;
			}
		}

		if (this.cosmeticPicker.mouseClicked(mouseX, mouseY))
		{
			this.setFocused(null);
			this.afterPickerDrag();
			return true;
		}

		if (this.cosmeticHexBox != null && this.focusField(this.cosmeticHexBox, event, doubleClick, mouseX, mouseY))
		{
			return true;
		}

		this.setFocused(null);
		return true;
	}

	// A palette colour is always selected for refund; in gradient mode it also joins the gradient
	private boolean clickPaletteSlot(int id)
	{
		this.cosmeticSelected = id;
		this.cosmeticPreviewPreset = -1;

		Cosmetics.pushGradient(id);

		if (Cosmetics.mode() != Cosmetics.Mode.GRADIENT)
		{
			Cosmetics.setMode(Cosmetics.Mode.GRADIENT);
		}

		this.setFocused(null);
		Theme.click();
		return true;
	}

	private boolean clickEffect(int index)
	{
		String id = EFFECT_IDS[index];

		if (CosmeticColors.ownsEffect(id))
		{
			Cosmetics.setEffect(id, !Cosmetics.effects().contains(id));
			this.afterCosmeticChange();
			return true;
		}

		CosmeticColors.buyEffect(id, EFFECT_LABELS[index], CosmeticColors.effectPrice(id), () ->
		{
			Cosmetics.setEffect(id, true);
			this.rebuildCosmetics();
		});
		return true;
	}

	// A freshly bought colour goes straight on, so buying one visibly does something
	private void wearNewest()
	{
		if (CosmeticColors.colors().isEmpty())
		{
			return;
		}

		int id = CosmeticColors.colors().get(CosmeticColors.count() - 1).id();
		this.cosmeticSelected = id;

		// a gradient the player arranged is theirs to change, so only an empty loadout takes the new colour
		if (Cosmetics.mode() != Cosmetics.Mode.NONE)
		{
			return;
		}

		Cosmetics.pushGradient(id);
		Cosmetics.setMode(Cosmetics.Mode.GRADIENT);
	}

	// Points the loadout at the palette colours a preset granted; the gradient only references them, so
	// wearing one never costs a palette slot
	private void wearPreset(Cosmetics.Preset preset)
	{
		// A preset is an owned wearable gradient in its own right; it never touches the palette
		Cosmetics.wearPreset(preset);
	}

	// A release on the slot that was picked up is a click, which empties it; anywhere else reorders the strip
	private boolean releaseGradientDrag(double mouseX, double mouseY)
	{
		int from = this.cosmeticGradientDrag;
		this.cosmeticGradientDrag = -1;

		for (int i = 0; i < this.cosmeticGradientRects.length; i++)
		{
			if (this.cosmeticGradientRects[i].contains(mouseX, mouseY))
			{
				if (i == from)
				{
					Cosmetics.clearGradientSlot(from);
				}
				else
				{
					Cosmetics.swapGradient(from, i);
				}

				Theme.click();
				return true;
			}
		}

		return true;
	}

	private void afterPickerDrag()
	{
		this.cosmeticPickColor = this.cosmeticPicker.rgb();
		this.cosmeticPicked = true;
		this.refreshHexBox();
	}

	private boolean switchCosmeticMode(Cosmetics.Mode mode)
	{
		Cosmetics.setMode(mode);
		this.afterCosmeticChange();
		return true;
	}

	// After any change to the palette or loadout, rebuild the hex field and re-seed the picker
	private void afterCosmeticChange()
	{
		this.rebuildCosmetics();
		Theme.click();
	}

	// The silent half, for callbacks that land after the click or beacon their action already played
	private void rebuildCosmetics()
	{
		this.setFocused(null);
		this.buildCosmeticsFields();
	}

	private int settingsSectionHeader(GuiGraphicsExtractor ctx, String label, int x, int y)
	{
		y += SETTINGS_SECTION_GAP;
		Theme.text(ctx, this.font, Theme.bold(label), x, y, Theme.TEXT);
		return y + this.font.lineHeight + SETTINGS_HEADER_GAP;
	}

	// Wrapped grey explainer under a section header; leaves a hint-sized gap before its control
	private int settingsDescription(GuiGraphicsExtractor ctx, String text, int lines, int x, int y, int width)
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

	private void renderBanner(GuiGraphicsExtractor ctx, int mouseX, int mouseY)
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

	private int volumeSlider(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY)
	{
		return this.settingSlider(ctx, this.volumeSliderTrack, "SFX Volume", Settings.uiVolume() + "%",
				Settings.uiVolumeFraction(), this.draggingVolume, x, y, width, mouseX, mouseY);
	}

	private int fovSlider(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY)
	{
		return this.settingSlider(ctx, this.fovSliderTrack, "Preview FOV", Integer.toString(Settings.previewFov()),
				(Settings.previewFov() - 30) / 80.0F, this.draggingFov, x, y, width, mouseX, mouseY);
	}

	private int settingSlider(GuiGraphicsExtractor ctx, Rect track, String label, String value, float frac,
			boolean dragging, int x, int y, int width, int mouseX, int mouseY)
	{
		return Controls.settingSlider(ctx, this.font, track, label, value, frac, dragging, x, y, width, mouseX, mouseY)
				+ SETTINGS_ROW_GAP;
	}

	private int settingRow(GuiGraphicsExtractor ctx, Rect rect, String label, String hint, boolean on,
			int x, int y, int width, int mouseX, int mouseY)
	{
		rect.set(x, y, width, FIELD_HEIGHT);
		Controls.toggle(ctx, this.font, rect, label, on, mouseX, mouseY);
		y += FIELD_HEIGHT + SETTINGS_HINT_GAP;

		for (String row : this.wrap(hint, width, 2))
		{
			Theme.text(ctx, this.font, row, x, y, Theme.TEXT_ASH);
			y += this.font.lineHeight + 2;
		}

		return y + SETTINGS_ROW_GAP;
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

	public int metaRow(GuiGraphicsExtractor ctx, String label, String value, int x, int y, int width)
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

		if (event.button() == 1 && this.page == Page.SAVED && !this.detailView.isOpen()
				&& !this.termsModal.isOpen() && !this.tutorialModal.isOpen()
				&& !this.designerWarnModal.isOpen())
		{
			for (int i = 1; i < this.collectionChips.size(); i++)
			{
				if (this.collectionChips.get(i).contains(mouseX, mouseY))
				{
					List<String> names = CollectionStore.names();

					if (i - 1 < names.size())
					{
						this.collectionOptionsModal.open(names.get(i - 1));
						Theme.click(0.9F);
					}

					return true;
				}
			}
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

		// An open chip-row list takes the click whole, so a click-away dismisses it without reaching the grid
		Dropdown openDropdown = this.openChipDropdown();

		if (openDropdown != null)
		{
			this.choseChipDropdown(openDropdown, openDropdown.click(mouseX, mouseY));
			return true;
		}

		if (this.shardButton.width > 0 && this.shardButton.contains(mouseX, mouseY))
		{
			this.openShardPanel();
			return true;
		}

		if (this.hasInbox() && this.bellButton.contains(mouseX, mouseY))
		{
			this.toggleNotifPanel();
			return true;
		}

		// Swallowed so a click inside the panel does not fall through to the grid behind it
		if (this.notifPanelOpen && this.notifPanelBounds.contains(mouseX, mouseY))
		{
			return true;
		}

		if (this.notifPanelOpen)
		{
			this.notifPanelOpen = false;
		}

		if (this.closeButton.contains(mouseX, mouseY))
		{
			this.onClose();
			return true;
		}

		// Checked before the rail and grid, so the overlaying dropdown wins the click
		if (this.gridPage() && this.clickSearchSuggestion(mouseX, mouseY))
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
			return this.clickUpload(event, doubleClick, mouseX, mouseY);
		}

		if (this.page == Page.PREMIUM)
		{
			return this.clickPremium(mouseX, mouseY);
		}

		if (this.page == Page.SETTINGS)
		{
			return this.clickSettings(mouseX, mouseY);
		}

		if (this.page == Page.COSMETICS)
		{
			return this.clickCosmetics(event, doubleClick, mouseX, mouseY);
		}

		if (this.page == Page.STAFF && this.staff != null)
		{
			return this.staff.mouseClicked(event, doubleClick, mouseX, mouseY);
		}

		if (this.page == Page.MAPART)
		{
			return this.mapartTab.mouseClicked(mouseX, mouseY);
		}

		if (this.retryButton.contains(mouseX, mouseY) || this.offlineRefresh.contains(mouseX, mouseY))
		{
			Theme.click();
			Catalogue.refresh();
			return true;
		}

		if (this.profilePoster != null)
		{
			if (this.profileBack.contains(mouseX, mouseY))
			{
				this.navigateBack();
				return true;
			}

			if (this.profileFollow.contains(mouseX, mouseY))
			{
				String poster = this.profilePoster;
				this.requireVerified(() -> this.toggleProfileFollow(poster));
				return true;
			}
		}

		if (this.addingToCollection != null && this.addModeDone.contains(mouseX, mouseY))
		{
			String collection = this.addingToCollection;
			this.addingToCollection = null;
			this.page = Page.SAVED;
			this.activeCollection = collection;
			this.setSearch("");
			Theme.click(0.9F);
			this.layoutChips();
			this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
			this.scroll = 0.0F;
			this.refilter();
			return true;
		}

		if (this.profilePoster == null && this.batchDownload.mouseClicked(mouseX, mouseY))
		{
			return true;
		}

		if (this.gridPage() && this.profilePoster == null
				&& this.sortDropdown.click(mouseX, mouseY) != Dropdown.NOT_HANDLED)
		{
			return true;
		}

		if (this.page == Page.BROWSE && this.clearFiltersButton.width > 0
				&& this.clearFiltersButton.contains(mouseX, mouseY))
		{
			this.clearFilters();
			return true;
		}

		if (this.gridPage() && this.profilePoster == null
				&& this.downloadFilterDropdown.click(mouseX, mouseY) != Dropdown.NOT_HANDLED)
		{
			return true;
		}

		if (this.page == Page.BROWSE && this.profilePoster == null
				&& this.followingFilterButton.contains(mouseX, mouseY))
		{
			this.followingFilter = !this.followingFilter;
			Theme.click(this.followingFilter ? 1.1F : 0.9F);
			this.layoutChips();
			this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
			this.scroll = 0.0F;
			this.refilter();
			return true;
		}

		if (this.page == Page.BROWSE && this.profilePoster == null
				&& this.historyButton.contains(mouseX, mouseY))
		{
			this.historyView = !this.historyView;
			Theme.click(this.historyView ? 1.1F : 0.9F);
			this.layoutChips();
			this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
			this.scroll = 0.0F;
			this.refilter();
			return true;
		}

		if (this.page == Page.SAVED && this.clickCollectionChips(mouseX, mouseY))
		{
			return true;
		}

		for (int i = 0; i < this.chipRects.size(); i++)
		{
			if (this.chipRects.get(i).contains(mouseX, mouseY))
			{
				Theme.buttonPop(this.chipRects.get(i));
				Category clicked = this.chipOrder.get(i);

				if (clicked == Category.ALL)
				{
					this.activeTags.clear();
				}
				else if (this.activeTags.contains(clicked))
				{
					this.activeTags.remove(clicked);
				}
				else
				{
					this.activeTags.add(clicked);
				}

				Theme.click();
				this.scroll = 0.0F;
				// A tag toggle can add or drop the Clear-filters chip, so re-lay the row first
				this.layoutChips();
				this.gridTop = TOP_BAR_HEIGHT + this.chipRowHeight;
				this.refilter();
				return true;
			}
		}

		SchematicEntry hit = this.entryAt(mouseX, mouseY);

		if (hit != null)
		{
			if (event.button() == 1)
			{
				this.cardMenu.open(hit, (int) mouseX, (int) mouseY);
			}
			else if (this.menuGlyphRect(hit).contains(mouseX, mouseY))
			{
				Rect glyph = this.menuGlyphRect(hit);
				this.cardMenu.open(hit, glyph.x + glyph.width - CardMenu.WIDTH, glyph.y + glyph.height + 2);
			}
			else if (this.addingToCollection != null)
			{
				boolean nowIn = CollectionStore.toggle(this.addingToCollection, hit.id());
				Theme.click(nowIn ? 1.2F : 0.9F);
			}
			else if (this.heartAt(hit, mouseX, mouseY))
			{
				this.requireVerified(() -> {
					this.toggleLike(hit);

					if (this.page == Page.SAVED)
					{
						this.refilter();
					}
				});
			}
			else
			{
				this.openDetail(hit);
			}

			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	public void openDetail(SchematicEntry entry)
	{
		this.detailView.open(entry);
	}

	// Feeds the Recently viewed row; only the detail card appends to it
	public void recordViewed(String id)
	{
		RECENT_VIEWED.remove(id);
		RECENT_VIEWED.add(0, id);

		while (RECENT_VIEWED.size() > RECENT_VIEWED_MAX)
		{
			RECENT_VIEWED.remove(RECENT_VIEWED.size() - 1);
		}
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
		return (focused != null && focused.isFocused()) || this.searchBox.isFocused() || this.anyFormFieldFocused();
	}

	public void switchPage(Page target)
	{
		if (target != this.page)
		{
			Theme.tab();
		}

		if (target == Page.UPLOAD)
		{
			// uploadFormOpen is left alone, so returning to Upload lands back on the same form
			if (!this.myStatsOk
					|| System.currentTimeMillis() - this.myStatsLoadedAt > MY_STATS_STALE_MS)
			{
				this.myStatsLoaded = false;
			}

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
		this.closeChipDropdowns();
		this.cosmeticPreviewEffect = -1;
		this.scroll = 0.0F;
		this.formStatus = "";
		this.addingToCollection = null;
		this.activeCollection = null;
		this.profilePoster = null;
		this.navStack.clear();
		this.setFocused(null);
		this.relayout();
	}

	// redeem() collapses an unreachable server and an invalid code into the same null result. Runs
	// off the render thread and never throws
	private static boolean backendReachable()
	{
		String host = Backend.baseHost();

		if (host == null || host.isBlank())
		{
			return false;
		}

		// A short TCP connect to 443 separates an answering server from an unreachable one
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress(host, 443), 4000);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	private boolean clickUpload(MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY)
	{
		if (!UploaderAccess.unlocked())
		{
			if (this.unlockButton.contains(mouseX, mouseY))
			{
				String value = this.codeBox.getValue();
				this.formStatus = "Checking code...";
				this.setFocused(null);
				Thread worker = new Thread(() -> {
					String owner = UploaderAccess.redeem(value);
					// Probed only on failure, so the message can name the real reason
					boolean reachable = owner != null || backendReachable();
					Minecraft.getInstance().execute(() -> {
						if (owner != null)
						{
							this.formStatus = "Unlocked as " + owner + ".";
						}
						else if (!reachable)
						{
							this.formStatus = "Couldn't reach the server - try again.";
						}
						else
						{
							this.formStatus = "That code is not valid.";
						}
					});
				}, "schematicindex-code");
				worker.setDaemon(true);
				worker.start();
				return true;
			}

			return this.focusField(this.codeBox, event, doubleClick, mouseX, mouseY);
		}

		if (this.signOutButton.contains(mouseX, mouseY))
		{
			long now = System.currentTimeMillis();

			// The first click only arms the confirm, guarding the button beside "+ New post"
			if (this.signOutConfirmAt != 0L && now - this.signOutConfirmAt <= SIGN_OUT_CONFIRM_MS)
			{
				this.signOutConfirmAt = 0L;
				UploaderAccess.signOut();
				this.formStatus = "";
				this.selectedDashPost = null;
				this.setFocused(null);
			}
			else
			{
				this.signOutConfirmAt = now;
				Theme.click();
			}

			return true;
		}

		this.signOutConfirmAt = 0L;

		if (this.statsRetryButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.loadMyStats();
			return true;
		}

		if (!this.uploadFormOpen)
		{
			if (this.dashUploadButton.contains(mouseX, mouseY))
			{
				this.uploadFormOpen = true;
				this.scroll = 0.0F;
				Theme.click(1.1F);
				return true;
			}

			if (this.legendViewsRect.contains(mouseX, mouseY))
			{
				this.graphMode = this.graphMode == 1 ? 0 : 1;
				this.graphAnimStart = System.currentTimeMillis();
				Theme.click(0.9F);
				return true;
			}

			if (this.legendDownloadsRect.contains(mouseX, mouseY))
			{
				this.graphMode = this.graphMode == 2 ? 0 : 2;
				this.graphAnimStart = System.currentTimeMillis();
				Theme.click(0.9F);
				return true;
			}

			if (this.legendLikesRect.contains(mouseX, mouseY))
			{
				this.graphMode = this.graphMode == 3 ? 0 : 3;
				this.graphAnimStart = System.currentTimeMillis();
				Theme.click(0.9F);
				return true;
			}

			if (this.legendStarsRect.contains(mouseX, mouseY))
			{
				this.graphMode = this.graphMode == 4 ? 0 : 4;
				this.graphAnimStart = System.currentTimeMillis();
				Theme.click(0.9F);
				return true;
			}

			Category[] all = Category.values();

			for (int i = 0; i < this.myFilterChips.size() && i < all.length; i++)
			{
				if (this.myFilterChips.get(i).contains(mouseX, mouseY))
				{
					this.myFilter = all[i];
					this.dashScroll = 0.0F;
					Theme.click(0.9F);
					return true;
				}
			}

			if (mouseY >= this.dashViewportTop && mouseY <= this.dashViewportBottom)
			{
				for (int i = 0; i < this.myPostEditRects.size() && i < this.myPostCardIds.size(); i++)
				{
					if (this.myPostEditRects.get(i).contains(mouseX, mouseY))
					{
						this.postOptionsModal.open(this.myPostCardIds.get(i));
						Theme.click(0.9F);
						return true;
					}
				}

				for (int i = 0; i < this.myPostCardRects.size() && i < this.myPostCardIds.size(); i++)
				{
					if (this.myPostCardRects.get(i).contains(mouseX, mouseY))
					{
						String id = this.myPostCardIds.get(i);
						this.selectedDashPost = id.equals(this.selectedDashPost) ? null : id;
						this.graphAnimStart = System.currentTimeMillis();
						Theme.click(this.selectedDashPost != null ? 1.1F : 0.9F);
						return true;
					}
				}
			}

			return true;
		}

		if (this.uploadBackButton.contains(mouseX, mouseY))
		{
			this.uploadFormOpen = false;
			this.myStatsLoaded = false;
			Theme.click(0.9F);
			return true;
		}

		if (this.formCategoryButton.contains(mouseX, mouseY))
		{
			this.formCategory = Category.next(this.formCategory.name());
			return true;
		}

		if (this.formImageRemove.contains(mouseX, mouseY) && !this.formPictures.isEmpty())
		{
			this.removeFormPicture(this.formPicturePreview);
			Theme.click(0.9F);
			return true;
		}

		if (this.formThumbnailButton.width > 0 && this.formThumbnailButton.contains(mouseX, mouseY))
		{
			this.formThumbnailIndex = this.formPicturePreview;
			Theme.click(1.1F);
			return true;
		}

		if (this.formImagePrev.contains(mouseX, mouseY))
		{
			this.formPicturePreview = Math.floorMod(this.formPicturePreview - 1, Math.max(1, this.formPictures.size()));
			return true;
		}

		if (this.formImageNext.contains(mouseX, mouseY))
		{
			this.formPicturePreview = Math.floorMod(this.formPicturePreview + 1, Math.max(1, this.formPictures.size()));
			return true;
		}

		if (this.uploadPicturesButton.contains(mouseX, mouseY))
		{
			this.openPicturePicker();
			return true;
		}

		if (this.uploadSchematicButton.contains(mouseX, mouseY))
		{
			this.openSchematicPicker();
			return true;
		}

		if (this.generateThumbnailButton.contains(mouseX, mouseY))
		{
			this.generateThumbnail();
			return true;
		}

		if (this.postButton.contains(mouseX, mouseY))
		{
			this.submitPost();
			return true;
		}

		if (this.focusField(this.titleBox, event, doubleClick, mouseX, mouseY)
				|| this.focusField(this.thumbnailBox, event, doubleClick, mouseX, mouseY)
				|| this.focusField(this.designerBox, event, doubleClick, mouseX, mouseY))
		{
			return true;
		}

		if (this.descriptionBox != null && this.uploadDescriptionBounds.contains(mouseX, mouseY))
		{
			this.setFocused(this.descriptionBox);
			this.descriptionBox.setFocused(true);
			this.descriptionBox.mouseClicked(event, doubleClick);
			return true;
		}

		// Steps out of any text box, so Ctrl+V pastes a schematic instead of typing into a field
		this.clearFormFocus();
		return true;
	}

	private void clearFormFocus()
	{
		this.setFocused(null);

		for (EditBox box : new EditBox[]{this.codeBox, this.titleBox, this.thumbnailBox, this.designerBox})
		{
			if (box != null)
			{
				box.setFocused(false);
			}
		}

		if (this.descriptionBox != null)
		{
			this.descriptionBox.setFocused(false);
		}
	}

	private boolean clickSettings(double mouseX, double mouseY)
	{
		if (this.volumeSliderTrack.contains(mouseX, mouseY))
		{
			this.draggingVolume = true;
			this.setVolumeFromMouse(mouseX);
			Theme.click(1.0F);
			return true;
		}

		if (this.fovSliderTrack.contains(mouseX, mouseY))
		{
			this.draggingFov = true;
			this.setFovFromMouse(mouseX);
			Theme.click(1.0F);
			return true;
		}

		if (this.soundsToggle.contains(mouseX, mouseY))
		{
			Settings.toggleSounds();

			Theme.click(1.2F);
		}
		else if (this.modTagsToggle.contains(mouseX, mouseY))
		{
			Settings.toggleModTags();
			Theme.click(1.1F);
		}
		else if (this.ownNametagToggle.contains(mouseX, mouseY))
		{
			Settings.toggleOwnNametag();
			Theme.click(1.1F);
		}
		else if (this.overwriteToggle.contains(mouseX, mouseY))
		{
			Settings.toggleConfirmOverwrite();
			Theme.click(1.1F);
		}
		else if (this.toastsToggle.contains(mouseX, mouseY))
		{
			Settings.toggleToasts();
			Theme.click(1.1F);
		}
		else if (this.notificationsToggle.contains(mouseX, mouseY))
		{
			Settings.toggleNotifications();
			Theme.click(1.1F);
		}
		else if (this.creatorNotificationsToggle.contains(mouseX, mouseY))
		{
			Settings.toggleCreatorAlerts();
			Theme.click(1.1F);
		}
		else if (this.changeFolderButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.openDownloadPicker();
		}
		else if (this.openFolderButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.openDownloadFolder();
		}
		else if (this.resetFolderButton.contains(mouseX, mouseY))
		{
			Theme.click();
			Settings.clearDownloadDirectory();
		}
		else if (this.gridDensityButton.contains(mouseX, mouseY))
		{
			Theme.click();
			Settings.cycleGridDensity();

			this.columns = Math.max(2, Math.min(7, columnsFor(this.contentWidth) + Settings.gridDensity()));
			this.cardWidth = (this.contentWidth - GUTTER * (this.columns - 1)) / this.columns;
			this.cardHeight = imageHeight(this.cardWidth) + CAPTION_HEIGHT;
			this.refilter();
		}
		else if (this.clearCacheButton.contains(mouseX, mouseY))
		{
			Theme.click();
			ImageStore.releaseAll();
			ImageStore.clearDiskCache();
			SchematicPreview.clearCache();
			Toasts.push("Cache cleared", "Thumbnails and previews will reload as needed.",
					new ItemStack(Items.BUCKET));
		}
		else if (this.termsButton.contains(mouseX, mouseY))
		{
			Theme.click();
			this.termsModal.openReview();
		}
		else if (this.usageDataToggle.contains(mouseX, mouseY))
		{
			Settings.toggleUsageData();
			Theme.click(1.1F);
		}

		return true;
	}

	public void openLink(String url)
	{
		if (url == null || url.isBlank())
		{
			return;
		}

		Theme.click(1.0F);
		ConfirmLinkScreen.confirmLinkNow(this, url, true);
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
				this.refilter();
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

	private String downloadFilterLabel()
	{
		return DOWNLOAD_FILTER_CAPTION + DOWNLOAD_FILTER_LABELS[this.downloadFilter];
	}

	private static String[] sortLabels()
	{
		Catalogue.Sort[] all = Catalogue.Sort.values();
		String[] labels = new String[all.length];

		for (int i = 0; i < all.length; i++)
		{
			labels[i] = all[i].label();
		}

		return labels;
	}

	// The "N new" suffix is dropped once the filter is on, to keep the label terse
	private String followingChipLabel()
	{
		if (!this.followingFilter)
		{
			int fresh = this.newFollowedPostCount();

			if (fresh > 0)
			{
				return "Following (" + fresh + " new)";
			}
		}

		return "Following";
	}

	// Linear scan, called only while laying out the chip row
	private int newFollowedPostCount()
	{
		if (newSinceCutoff <= 0L)
		{
			return 0;
		}

		int count = 0;

		for (SchematicEntry entry : Catalogue.posts())
		{
			if (entry.postedAt() > newSinceCutoff && Follows.isFollowing(entry.poster()))
			{
				count++;
			}
		}

		return count;
	}

	private void toggleProfileFollow(String poster)
	{
		boolean wasFollowing = Follows.isFollowing(poster);
		Follows.toggle(poster);

		if (wasFollowing)
		{
			if (this.profileFollowers > 0)
			{
				this.profileFollowers--;
			}

			Theme.click(0.8F);
			this.verifyUnfollow(poster);
			return;
		}

		if (this.profileFollowers >= 0)
		{
			this.profileFollowers++;
		}

		Theme.follow();
		this.verifyFollow(null, poster);
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

					if (poster.equals(this.profilePoster))
					{
						this.fetchCreator(poster);
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

					if (poster.equals(this.profilePoster))
					{
						this.fetchCreator(poster);
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
				this.refilter();
				it.remove();
			}
			else if (state == Download.State.FAILED)
			{
				Theme.failure();
				this.status = "Download failed. Press Retry to try again.";
				this.detailDownloadLocked = false;
				this.showError(Errors.DOWNLOAD);
				it.remove();
			}
			else
			{
				tracked.setValue(state);
			}
		}
	}

	// GLFW text clipboard, so unlike Copy PNG this works on macOS too
	public void copyShareLink(SchematicEntry entry)
	{
		String url = Backend.shareUrl(entry.id());
		SchematicIndexMod.LOGGER.debug("Share link for {}: {}", entry.id(), url);

		if (!this.copyToClipboard(url))
		{
			Toasts.push("Couldn't copy link", "Copying the share link failed. (" + Errors.LINK + ")",
					new ItemStack(Items.BARRIER));
			return;
		}

		Toasts.push("Share link copied", "Paste it in Discord to share this post.", new ItemStack(Items.PAPER));
		Backend.postEvent("share_link");
		Shards.pokeSoon();
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
	}

	public boolean focusField(EditBox box, MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY)
	{
		if (!Theme.inside(mouseX, mouseY, box.getX() - 6, box.getY() - 4, box.getWidth() + 12, FIELD_HEIGHT))
		{
			return false;
		}

		this.setFocused(box);
		box.setFocused(true);
		box.mouseClicked(event, doubleClick);
		return true;
	}

	private void submitPost()
	{
		if (this.uploading)
		{
			return;
		}

		String title = this.titleBox.getValue().trim();

		if (title.isEmpty())
		{
			this.formStatus = "Give it a name first.";
			return;
		}

		if (this.formSchematic == null)
		{
			this.formStatus = "Choose the .litematic file first.";
			return;
		}

		if (this.formPictures.isEmpty())
		{
			this.formStatus = "Select at least one picture.";
			return;
		}

		if (UploaderAccess.code() == null)
		{
			this.formStatus = "Your session expired - unlock again.";
			return;
		}

		if (this.designerBox.getValue().trim().isEmpty() && !Settings.skipDesignerWarning())
		{
			this.designerWarnModal.open();
			return;
		}

		this.beginUpload();
	}

	public void beginUpload()
	{
		if (this.uploading || this.formSchematic == null || UploaderAccess.code() == null)
		{
			return;
		}

		Usage.once("first_upload_attempt");
		String designer = this.designerBox.getValue().trim();

		JsonObject meta = new JsonObject();
		meta.addProperty("title", this.titleBox.getValue().trim());
		meta.addProperty("thumbnailName", this.thumbnailBox.getValue().trim());
		meta.addProperty("designer", designer.isEmpty() ? "Unknown" : designer);
		meta.addProperty("category", this.formCategory.name());
		meta.addProperty("description", this.descriptionBox.getValue().trim());
		meta.addProperty("thumbnailIndex", this.formThumbnailIndex);

		String code = UploaderAccess.code();
		Path schematic = this.formSchematic;
		List<Path> pictures = new ArrayList<>(this.formPictures);
		this.formStatus = "";
		this.uploading = true;
		this.uploadStartedAt = System.currentTimeMillis();

		try
		{
			this.uploadFileSize = Files.size(schematic);
		}
		catch (Exception e)
		{
			this.uploadFileSize = 0L;
		}

		Thread worker = new Thread(() -> {
			Backend.UploadResult result = Backend.upload(code, meta.toString(), schematic, pictures);
			Minecraft.getInstance().execute(() -> {
				this.uploading = false;

				if (result.status() == 201)
				{
					this.formPictures.clear();
					this.formPictureStart = -1;
					this.formPicturePreview = 0;
					this.formThumbnailIndex = 0;
					this.formSchematic = null;
					this.formSizeX = 0;
					this.formSizeY = 0;
					this.formSizeZ = 0;
					this.formBlockCount = 0;
					this.titleBox.setValue("");
					this.thumbnailBox.setValue("");
					this.designerBox.setValue("");
					this.descriptionBox.setValue("");
					this.formStatus = "";
					this.uploadFormOpen = false;
					clearDraft();
					Catalogue.refresh();
					// The form clears and the page switches before the refresh lands, so the toast is the
					// only success signal that survives
					Toasts.push("Submitted for review", "Your post will go live once a moderator approves it.",
							new ItemStack(Items.WRITABLE_BOOK));
					this.switchPage(Page.BROWSE);
				}
				else if (result.status() == 409)
				{
					this.formStatus = "";
					this.duplicateModal.open();
				}
				else if (result.message() != null && !result.message().isBlank())
				{
					this.formStatus = result.message();
				}
				else
				{
					this.formStatus = "Upload failed.";
					this.showError(Errors.UPLOAD);
				}
			});
		}, "schematicindex-upload");
		worker.setDaemon(true);
		worker.start();
	}

	private void openPicturePicker()
	{
		new Thread(() -> {
			String result;

			try (MemoryStack stack = MemoryStack.stackPush())
			{
				PointerBuffer filters = stack.mallocPointer(3);
				filters.put(stack.UTF8("*.png"));
				filters.put(stack.UTF8("*.jpg"));
				filters.put(stack.UTF8("*.jpeg"));
				filters.flip();
				result = TinyFileDialogs.tinyfd_openFileDialog("Select 1 to 5 pictures", "", filters, "Images", true);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("File picker failed", e);
				return;
			}

			if (result == null || result.isBlank())
			{
				return;
			}

			List<Path> chosen = Arrays.stream(result.split("\\|"))
					.filter(value -> !value.isBlank())
					.map(Path::of)
					.toList();
			Minecraft.getInstance().execute(() -> this.applyPictures(chosen));
		}, "schematicindex-picture-picker").start();
	}

	private void openSchematicPicker()
	{
		new Thread(() -> {
			String result;

			try (MemoryStack stack = MemoryStack.stackPush())
			{
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.litematic"));
				filters.flip();
				result = TinyFileDialogs.tinyfd_openFileDialog(
						"Choose the schematic", "", filters, "Litematica schematic", false);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("File picker failed", e);
				return;
			}

			if (result == null || result.isBlank())
			{
				return;
			}

			Path chosen = Path.of(result.trim());
			Minecraft.getInstance().execute(() -> {
				this.formSchematic = chosen;
				this.formStatus = chosen.getFileName() + " selected.";
				this.parseSchematicStats(chosen);
			});
		}, "schematicindex-schematic-picker").start();
	}

	private void parseSchematicStats(Path file)
	{
		this.formSizeX = 0;
		this.formSizeY = 0;
		this.formSizeZ = 0;
		this.formBlockCount = 0;

		// A .litematic read is a full gzip and NBT decompress, which would freeze the render thread.
		// The pick sequence lets a slow parse drop its result if the file was swapped meanwhile
		final long seq = ++this.schematicParseSeq;
		final String pickedName = file.getFileName().toString();
		this.formStatus = "Reading " + pickedName + "...";

		Net.submit(() -> {
			int sizeX = 0;
			int sizeY = 0;
			int sizeZ = 0;
			int blockCount = 0;

			try
			{
				LitematicaSchematic schematic = LitematicaSchematic.createFromFile(
						file.getParent(), file.getFileName().toString(), FileType.LITEMATICA_SCHEMATIC);

				if (schematic != null)
				{
					Vec3i size = schematic.getMetadata().getEnclosingSize();
					sizeX = Math.abs(size.getX());
					sizeY = Math.abs(size.getY());
					sizeZ = Math.abs(size.getZ());
					blockCount = (int) Math.max(0L, schematic.getMetadata().getTotalBlocks());
				}
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.debug("Could not read schematic stats", e);
			}

			final int fx = sizeX;
			final int fy = sizeY;
			final int fz = sizeZ;
			final int fb = blockCount;
			Minecraft.getInstance().execute(() -> {
				if (seq != this.schematicParseSeq || !file.equals(this.formSchematic) || !this.uploadFormOpen)
				{
					return;
				}

				this.formSizeX = fx;
				this.formSizeY = fy;
				this.formSizeZ = fz;
				this.formBlockCount = fb;
				this.formStatus = pickedName + " selected.";
			});
		});
	}

	private void removeFormPicture(int index)
	{
		if (index < 0 || index >= this.formPictures.size())
		{
			return;
		}

		List<Path> remaining = new ArrayList<>(this.formPictures);
		remaining.remove(index);

		this.formPictures.clear();
		this.formPictures.addAll(remaining);
		this.formPictureStart = ImageStore.register(remaining);
		this.formPicturePreview = remaining.isEmpty() ? 0 : Math.min(index, remaining.size() - 1);

		if (this.formThumbnailIndex == index)
		{
			this.formThumbnailIndex = 0;
		}
		else if (this.formThumbnailIndex > index)
		{
			this.formThumbnailIndex--;
		}

		this.formThumbnailIndex = remaining.isEmpty() ? 0 : Math.min(this.formThumbnailIndex, remaining.size() - 1);
		this.formStatus = remaining.isEmpty()
				? "All pictures removed."
				: remaining.size() + (remaining.size() == 1 ? " picture selected." : " pictures selected.");
	}

	private void applyPictures(List<Path> chosen)
	{
		boolean truncated = chosen.size() > 5;
		List<Path> capped = truncated ? List.copyOf(chosen.subList(0, 5)) : chosen;

		this.formPictures.clear();
		this.formPictures.addAll(capped);
		this.formPictureStart = ImageStore.register(capped);
		this.formPicturePreview = 0;
		this.formStatus = truncated
				? "Only the first 5 pictures were kept."
				: capped.size() + (capped.size() == 1 ? " picture selected." : " pictures selected.");
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

	private boolean heartAt(SchematicEntry entry, double mouseX, double mouseY)
	{
		int index = this.visible.indexOf(entry);

		if (index < 0)
		{
			return false;
		}

		int row = index / this.columns;
		int column = index % this.columns;
		int x = this.contentX + column * (this.cardWidth + GUTTER);
		int y = this.gridTop + row * (this.cardHeight + GUTTER) - Math.round(this.scroll);
		Rect heart = this.heartRect(x, y, imageHeight(this.cardWidth));
		return Theme.inside(mouseX, mouseY, heart.x - 2, heart.y - 2, HEART_SIZE + 4, HEART_SIZE + 4);
	}

	// Shared instance, consumed before the next call, like heartRect
	private Rect menuGlyphRect(SchematicEntry entry)
	{
		int index = this.visible.indexOf(entry);
		int row = index / this.columns;
		int column = index % this.columns;
		int x = this.contentX + column * (this.cardWidth + GUTTER);
		int y = this.gridTop + row * (this.cardHeight + GUTTER) - Math.round(this.scroll);
		this.menuGlyphRectPool.set(x + this.cardWidth - 4 - CardMenu.GLYPH_CELL, y + 4, CardMenu.GLYPH_CELL, 11);
		return this.menuGlyphRectPool;
	}

	private @Nullable SchematicEntry entryAt(double mouseX, double mouseY)
	{
		if (mouseY < this.gridTop || mouseY >= this.gridBottom || mouseX < this.contentX)
		{
			return null;
		}

		int rowHeight = this.cardHeight + GUTTER;
		int relativeY = (int) (mouseY - this.gridTop + this.scroll);
		int row = relativeY / rowHeight;

		if (relativeY % rowHeight > this.cardHeight)
		{
			return null;
		}

		int relativeX = (int) (mouseX - this.contentX);
		int columnWidth = this.cardWidth + GUTTER;
		int column = relativeX / columnWidth;

		if (column >= this.columns || relativeX % columnWidth > this.cardWidth)
		{
			return null;
		}

		int index = row * this.columns + column;
		return index >= 0 && index < this.shownCap() ? this.visible.get(index) : null;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
	{
		if (this.draggingScrollbar)
		{
			this.dragScrollbarTo(event.y());
			return true;
		}

		if (this.draggingVolume)
		{
			this.setVolumeFromMouse(event.x());
			return true;
		}

		if (this.draggingFov)
		{
			this.setFovFromMouse(event.x());
			return true;
		}

		if (this.mapartTab.mouseDragged(event.x(), event.y()))
		{
			return true;
		}

		if (this.cosmeticPicker.mouseDragged(event.x(), event.y()))
		{
			this.afterPickerDrag();
			return true;
		}

		if (this.cosmeticGradientDrag >= 0)
		{
			this.cosmeticDragX = (int) event.x();
			this.cosmeticDragY = (int) event.y();
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

		if (this.draggingScrollbar)
		{
			this.draggingScrollbar = false;
			this.dragScrollChannel = SCROLLBAR_NONE;
			return true;
		}

		if (this.draggingVolume)
		{
			this.draggingVolume = false;
			Theme.click(1.0F);
			return true;
		}

		if (this.draggingFov)
		{
			this.draggingFov = false;
			Theme.click(1.0F);
			return true;
		}

		if (this.mapartTab.mouseReleased())
		{
			return true;
		}

		if (this.cosmeticPicker.mouseReleased())
		{
			return true;
		}

		if (this.cosmeticGradientDrag >= 0)
		{
			return this.releaseGradientDrag(event.x(), event.y());
		}

		return super.mouseReleased(event);
	}

	private void setVolumeFromMouse(double mouseX)
	{
		int trackX = this.volumeSliderTrack.x;
		int trackW = this.volumeSliderTrack.width;

		if (trackW > 0)
		{
			Settings.setUiVolume((int) Math.round((mouseX - trackX) / trackW * 100.0));
		}
	}

	private void setFovFromMouse(double mouseX)
	{
		int trackX = this.fovSliderTrack.x;
		int trackW = this.fovSliderTrack.width;

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
		else
		{
			buttons = new Rect[]{this.closeButton, this.retryButton, this.unlockButton,
					this.signOutButton, this.formCategoryButton, this.uploadPicturesButton,
					this.uploadSchematicButton, this.generateThumbnailButton, this.postButton, this.changeFolderButton,
					this.openFolderButton, this.resetFolderButton, this.soundsToggle, this.overwriteToggle,
					this.toastsToggle, this.notificationsToggle, this.gridDensityButton, this.clearCacheButton,
					this.followingFilterButton, this.historyButton, this.termsButton, this.usageDataToggle};
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

		this.cosmeticScrollTarget = -1.0F;

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
			if (this.uploadFormOpen)
			{
				if (this.descriptionBox != null && this.uploadDescriptionBounds.contains(mouseX, mouseY))
				{
					this.descriptionBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
					return true;
				}

				this.scroll = Math.max(0.0F, Math.min(this.maxScroll, this.scroll - (float) scrollY * SCROLL_STEP));
				return true;
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
		if (this.page != Page.UPLOAD || !this.uploadFormOpen || paths.isEmpty())
		{
			super.onFilesDrop(paths);
			return;
		}

		Path schematic = null;
		List<Path> images = new ArrayList<>();

		for (Path path : paths)
		{
			String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

			if (name.endsWith(".litematic"))
			{
				schematic = path;
			}
			else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
			{
				images.add(path);
			}
		}

		if (schematic != null)
		{
			this.formSchematic = schematic;
			this.formStatus = schematic.getFileName() + " selected.";
			this.parseSchematicStats(schematic);
		}

		if (!images.isEmpty())
		{
			List<Path> combined = new ArrayList<>(this.formPictures);
			combined.addAll(images);
			this.applyPictures(combined);
		}

		if (schematic == null && images.isEmpty())
		{
			this.formStatus = "Drop a .litematic file or PNG/JPG images.";
		}
	}

	// Super as well as Control, so the custom text modals accept Cmd+V on macOS
	public static boolean isPasteChord(KeyEvent event)
	{
		return event.key() == 86 && (event.modifiers() & (0x2 | 0x8)) != 0;
	}

	private boolean anyFormFieldFocused()
	{
		return (this.titleBox != null && this.titleBox.isFocused())
				|| (this.thumbnailBox != null && this.thumbnailBox.isFocused())
				|| (this.designerBox != null && this.designerBox.isFocused())
				|| (this.descriptionBox != null && this.descriptionBox.isFocused())
				|| (this.codeBox != null && this.codeBox.isFocused());
	}

	private void pasteFromClipboard()
	{
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"))
		{
			this.formStatus = "Clipboard paste is not supported on macOS.";
			return;
		}

		this.formStatus = "Reading clipboard...";

		Thread worker = new Thread(() -> {
			Path pastedImage = null;
			String pastedUrl = null;
			boolean headless = false;

			try
			{
				java.awt.datatransfer.Transferable contents = systemClipboard().getContents(null);

				if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor))
				{
					java.awt.Image image = (java.awt.Image) contents.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
					pastedImage = this.writePastedPng(toBufferedImage(image));
				}
				else if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor))
				{
					String text = ((String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor)).trim();

					if (text.startsWith("http://") || text.startsWith("https://"))
					{
						pastedUrl = text.split("\\s+")[0];
					}
				}
				else if (contents != null)
				{
					StringBuilder flavors = new StringBuilder();

					for (java.awt.datatransfer.DataFlavor flavor : contents.getTransferDataFlavors())
					{
						flavors.append(flavor.getMimeType()).append(" | ");
					}

					SchematicIndexMod.LOGGER.warn("Clipboard had no image/text. Flavors: {}", flavors);
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Clipboard read failed", e);
				headless = e instanceof java.awt.HeadlessException;
			}

			Path downloaded = null;
			boolean downloadedSchematic = false;
			boolean unsupportedLink = false;

			if (pastedUrl != null)
			{
				String extension = urlExtension(pastedUrl);
				boolean isSchematic = extension.equals("litematic");
				boolean isImage = extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg");

				if (isSchematic || isImage)
				{
					try
					{
						Path target = this.pastedFile(isSchematic ? "litematic" : extension);

						if (Backend.downloadUserLink(pastedUrl, target))
						{
							downloaded = target;
							downloadedSchematic = isSchematic;
						}
					}
					catch (Exception e)
					{
						SchematicIndexMod.LOGGER.warn("Clipboard link download failed", e);
					}
				}
				else
				{
					unsupportedLink = true;
				}
			}

			Path image = pastedImage;
			Path file = downloaded;
			boolean asSchematic = downloadedSchematic;
			boolean hadUrl = pastedUrl != null;
			boolean badType = unsupportedLink;
			boolean noClipboard = headless;
			Minecraft.getInstance().execute(() -> {
				if (image != null)
				{
					this.addFormPicture(image);
					this.formStatus = "Pasted image added.";
				}
				else if (file != null && asSchematic)
				{
					this.formSchematic = file;
					this.formStatus = file.getFileName() + " selected.";
					this.parseSchematicStats(file);
				}
				else if (file != null)
				{
					this.addFormPicture(file);
					this.formStatus = "Downloaded image added.";
				}
				else if (badType)
				{
					this.formStatus = "That link isn't a .litematic or image.";
				}
				else if (hadUrl)
				{
					this.formStatus = "Could not fetch that link - Discord links expire, copy a fresh one.";
				}
				else if (noClipboard)
				{
					this.formStatus = "Clipboard not available on this platform.";
				}
				else
				{
					this.formStatus = "No image or link on the clipboard.";
				}
			});
		}, "schematicindex-paste");
		worker.setDaemon(true);
		worker.start();
	}

	// renderThumbnail completes on a pipeline thread, so everything touching the form is marshalled
	// back to the client thread
	private void generateThumbnail()
	{
		if (this.generatingThumbnail)
		{
			return;
		}

		Path picked = this.formSchematic;

		if (picked == null)
		{
			this.formStatus = "Choose the .litematic file first.";
			return;
		}

		this.generatingThumbnail = true;
		this.formStatus = "Rendering thumbnail...";
		Theme.click();

		SchematicPreview.renderThumbnail(picked).whenComplete((bytes, error) ->
				Minecraft.getInstance().execute(() -> {
					this.generatingThumbnail = false;

					if (error != null)
					{
						SchematicIndexMod.LOGGER.warn("Thumbnail render failed", error);
						this.formStatus = "Couldn't render a thumbnail. (" + Errors.PREVIEW + ")";
						return;
					}

					if (bytes == null)
					{
						this.formStatus = "Couldn't render a thumbnail from that file.";
						return;
					}

					// applyPictures would silently drop a 6th image
					if (this.formPictures.size() >= 5)
					{
						this.formStatus = "Remove a picture first - 5 is the maximum.";
						return;
					}

					try
					{
						Path file = this.pastedFile("png");
						Files.write(file, bytes);
						this.addFormPicture(file);
						this.formStatus = "Thumbnail added to your pictures.";
					}
					catch (Exception e)
					{
						SchematicIndexMod.LOGGER.warn("Saving rendered thumbnail failed", e);
						this.formStatus = "Rendered, but saving it failed. (" + Errors.PREVIEW + ")";
					}
				}));
	}

	private void addFormPicture(Path path)
	{
		List<Path> combined = new ArrayList<>(this.formPictures);
		combined.add(path);
		this.applyPictures(combined);
	}

	private Path writePastedPng(java.awt.image.BufferedImage image) throws IOException
	{
		Path file = this.pastedFile("png");
		javax.imageio.ImageIO.write(image, "png", file.toFile());
		return file;
	}

	// capturePng must start on the client thread, which the click handler already is; the encoded PNG
	// arrives on a pool thread, so the file write never blocks the render loop
	public void savePreviewPng(SchematicEntry entry)
	{
		SchematicPreview.capturePng(entry.schematicSlot(), png -> {
			if (png == null)
			{
				// A notice rather than an empty file
				Minecraft.getInstance().execute(() ->
						Toasts.push("Couldn't save preview", "The 3D preview isn't ready yet. (" + Errors.PREVIEW + ")",
								new ItemStack(Items.BARRIER)));
				return;
			}

			try
			{
				Path dir = FabricLoader.getInstance().getGameDir()
						.resolve("screenshots").resolve(SchematicIndexMod.MOD_ID);
				Files.createDirectories(dir);

				String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
				Path file = dir.resolve(sanitizeFileName(entry.title()) + "_" + stamp + ".png");
				Files.write(file, png);

				String shown = file.getFileName().toString();
				Minecraft.getInstance().execute(() ->
						Toasts.push("Saved screenshot", shown, new ItemStack(Items.PAINTING)));
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Saving preview PNG failed", e);
				Minecraft.getInstance().execute(() ->
						Toasts.push("Couldn't save preview", "Writing the file failed. (" + Errors.PREVIEW + ")",
								new ItemStack(Items.BARRIER)));
			}
		});
	}

	// AWT clipboard access is refused on macOS, matching pasteFromClipboard, where it deadlocks off
	// the AppKit main thread
	public void copyImageToClipboard(SchematicEntry entry)
	{
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"))
		{
			Toasts.push("Copy not supported", "Copying images to the clipboard isn't supported on macOS.",
					new ItemStack(Items.BARRIER));
			return;
		}

		String ref = this.currentImageRef(entry);

		if (ref == null)
		{
			Toasts.push("Nothing to copy", "This post has no copyable image.", new ItemStack(Items.BARRIER));
			return;
		}

		// Off the client thread, since a cache miss downloads
		Thread worker = new Thread(() -> {
			try
			{
				byte[] bytes = ImageStore.cachedBytes(ref);

				if (bytes == null)
				{
					Minecraft.getInstance().execute(() ->
							Toasts.push("Couldn't copy image", "The image isn't available yet. (" + Errors.IMAGE + ")",
									new ItemStack(Items.BARRIER)));
					return;
				}

				java.awt.datatransfer.Clipboard clipboard = systemClipboard();
				java.awt.image.BufferedImage image =
						javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));

				if (image == null)
				{
					Minecraft.getInstance().execute(() ->
							Toasts.push("Couldn't copy image", "This image format can't be copied. (" + Errors.IMAGE + ")",
									new ItemStack(Items.BARRIER)));
					return;
				}

				clipboard.setContents(new ImageTransferable(image), null);

				Minecraft.getInstance().execute(() ->
						Toasts.push("Copied image", entry.title() + " is now on your clipboard.",
								new ItemStack(Items.PAINTING)));
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Copying image to clipboard failed", e);
				String reason = e instanceof java.awt.HeadlessException
						? "Clipboard not available on this platform."
						: "Copying to the clipboard failed.";
				Minecraft.getInstance().execute(() ->
						Toasts.push("Couldn't copy image", reason + " (" + Errors.IMAGE + ")",
								new ItemStack(Items.BARRIER)));
			}
		}, "schematicindex-copy-png");
		worker.setDaemon(true);
		worker.start();
	}

	// MainMixin clears java.awt.headless before the toolkit can cache it; setting it again here is too
	// late to matter once the toolkit exists, and only a HeadlessToolkit can still throw from this call
	private static java.awt.datatransfer.Clipboard systemClipboard()
	{
		System.setProperty("java.awt.headless", "false");
		return java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
	}

	private static final class ImageTransferable implements java.awt.datatransfer.Transferable
	{
		private final java.awt.Image image;

		ImageTransferable(java.awt.Image image)
		{
			this.image = image;
		}

		@Override
		public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors()
		{
			return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor)
		{
			return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
				throws java.awt.datatransfer.UnsupportedFlavorException
		{
			if (!java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor))
			{
				throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
			}

			return this.image;
		}
	}

	// Capped in length so the final path stays reasonable
	private static String sanitizeFileName(String raw)
	{
		String cleaned = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9-_]+", "_")
				.replaceAll("^_+|_+$", "");

		if (cleaned.isEmpty())
		{
			cleaned = "schematic";
		}

		return Download.deviceSafe(cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned);
	}

	private Path pastedFile(String extension) throws IOException
	{
		Path dir = FabricLoader.getInstance().getGameDir()
				.resolve(SchematicIndexMod.MOD_ID).resolve("pasted");
		Files.createDirectories(dir);
		return dir.resolve("paste-" + System.currentTimeMillis() + "." + extension);
	}

	private static String urlExtension(String url)
	{
		String path = url.split("[?#]")[0];
		int dot = path.lastIndexOf('.');
		int slash = path.lastIndexOf('/');
		return dot > slash && dot >= 0 ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}

	private static java.awt.image.BufferedImage toBufferedImage(java.awt.Image image)
	{
		if (image instanceof java.awt.image.BufferedImage buffered)
		{
			return buffered;
		}

		int width = Math.max(1, image.getWidth(null));
		int height = Math.max(1, image.getHeight(null));
		java.awt.image.BufferedImage buffered = new java.awt.image.BufferedImage(width, height,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = buffered.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return buffered;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
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

		if (event.key() == 256 && this.closeChipDropdowns())
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

		if (this.page == Page.UPLOAD && this.uploadFormOpen && event.key() == 86
				&& (event.modifiers() & 0x2) != 0 && !this.anyFormFieldFocused())
		{
			this.pasteFromClipboard();
			return true;
		}

		if (this.detailView.imageKeyPressed(event))
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
		if (this.searchBox.isFocused() && (event.key() == 257 || event.key() == 335))
		{
			this.flushSearchDebounce();
		}

		if (this.gridPage() && !this.pageBlocked() && !this.searchBox.isFocused()
				&& this.handleGridKey(event.key()))
		{
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event)
	{
		this.detailView.keyReleased(event);

		return super.keyReleased(event);
	}

	@Override
	public void onClose()
	{
		sessionSort = this.sort;
		sessionTags.clear();
		sessionTags.addAll(this.activeTags);
		sessionScroll = this.scroll;
		sessionShown = this.shownCount;
		sessionDownloadFilter = this.downloadFilter;
		this.saveDraft();
		this.detailView.flushPendingRate(true);

		ImageStore.releaseAll();
		SchematicPreview.releaseAll();

		// Otherwise the map accumulates stale entries across repeated opens
		this.downloadStates.clear();

		if (this.minecraft != null)
		{
			this.minecraft.setScreenAndShow(this.parent);
		}
	}

	@Override
	public void removed()
	{
		// removed() runs before the next screen initialises, so the game returns to the player's scale
		this.schematicindex$restoreGuiScale();
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

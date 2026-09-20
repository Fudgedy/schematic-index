package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.LitematicaCompat;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.mojang.blaze3d.platform.NativeImage;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.util.FileType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public final class SchematicPreview
{
	public static final int WIDTH = 480;
	public static final int HEIGHT = 270;

	public static final float MIN_ZOOM = 0.8F;

	// The raytracer only renders at WIDTH x HEIGHT, so a thumbnail is a full frame box-downscaled to here
	public static final int THUMBNAIL_WIDTH = 240;
	public static final int THUMBNAIL_HEIGHT = 135;

	// Fixed, so every thumbnail is framed identically regardless of how the user last orbited the preview
	private static final float THUMBNAIL_YAW = 30.0F;
	private static final float THUMBNAIL_PITCH = 30.0F;
	private static final int THUMBNAIL_FOV = 70;

	private static final long THUMBNAIL_TIMEOUT_MS = 30_000L;

	// One short per voxel, so this bounds a downsampled grid to about 8 MB of heap
	private static final int MAX_VOXELS = 4_000_000;

	private static final int MAX_CACHED_MODELS = 3;

	private static final long MAX_SCHCACHE_BYTES = 256L * 1024 * 1024;

	private static final int BACKGROUND = 0xFF10151A;
	private static final double FIELD_OF_VIEW = 70.0D;

	private static final double WATER_ALPHA = 0.55D;

	// Lava reads as self-lit next to directionally shaded neighbours instead of being darkened by them
	private static final double LAVA_EMISSIVE = 1.2D;

	// Caps the idle re-render rate of an animated schematic so its sprites move without pinning the CPU
	private static final long ANIM_FRAME_MS = 80L;

	private static final int PICKED_BASE = 1_000_000;
	private static final List<Path> PICKED = new CopyOnWriteArrayList<>();

	// Appended under registerUrl's lock but read from the render and executor threads without it
	private static final int URL_BASE = 2_000_000;
	private static final List<String> URLS = new CopyOnWriteArrayList<>();
	private static final List<Path> URL_FILES = new CopyOnWriteArrayList<>();
	private static final Map<String, Integer> URL_INDEX = new HashMap<>();

	private static final long FAILED_BACKOFF_MS = 30_000L;

	private static final Map<Integer, Model> MODELS = new ConcurrentHashMap<>();
	private static final Set<Integer> LOADING = new CopyOnWriteArraySet<>();
	private static final Map<Integer, Long> FAILED = new ConcurrentHashMap<>();
	private static final Queue<Integer> MODEL_ORDER = new ConcurrentLinkedQueue<>();
	private static final Queue<Frame> PENDING = new ConcurrentLinkedQueue<>();

	// An AtomicReference because load() writes it on the Net pool and the client thread reads it
	private static final AtomicReference<String> STAGE = new AtomicReference<>("");

	private static final ExecutorService RENDERER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "schematicindex-preview");
		thread.setDaemon(true);
		return thread;
	});

	// Not the shared commonPool: the load pipeline runs there, so a heavy trace could starve loads
	private static final int RAYTRACE_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
	private static final ForkJoinPool RAYTRACE_POOL = new ForkJoinPool(
			RAYTRACE_THREADS,
			pool -> {
				ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
				worker.setName("schematicindex-raytrace-" + worker.getPoolIndex());
				worker.setDaemon(true);
				return worker;
			},
			null,
			false);

	private static PreviewRenderer renderer = new GpuPreviewRenderer();

	public static void setRenderer(PreviewRenderer next)
	{
		if (next == null || next == renderer)
		{
			return;
		}

		PreviewRenderer previous = renderer;
		renderer = next;

		try
		{
			previous.close();
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not close previous preview renderer", e);
		}
	}

	public static String rendererId()
	{
		return renderer.id();
	}

	private static @Nullable Identifier viewId;
	private static @Nullable DynamicTexture viewTexture;
	// Blit source size: WIDTH x HEIGHT on the CPU path, the framebuffer size on a direct-to-texture one
	private static int renderWidth = WIDTH;
	private static int renderHeight = HEIGHT;
	private static @Nullable NativeImage scratch;
	private static @Nullable View shown;
	private static @Nullable View queued;
	private static boolean rendering;
	// A render job deferred to run inside a live frame; uploadPending runs it from the screen's render()
	private static @Nullable Runnable frameRenderJob;
	private static boolean firstFrameReported;

	// Raw inputs of the previous request, so an identical follow-up returns without rebuilding a View
	private static int lastSlot = Integer.MIN_VALUE;
	private static float lastYaw;
	private static float lastPitch;
	private static float lastZoom;
	private static float lastMaxLayer;
	private static int lastFov;
	private static boolean lastCutaway;
	private static boolean lastFreeLook;
	private static boolean lastEyeNull = true;
	private static double lastEyeX;
	private static double lastEyeY;
	private static double lastEyeZ;

	private static long lastAnimFrame = Long.MIN_VALUE;

	record View(int slot, float yaw, float pitch, float zoom, boolean cutaway, boolean freeLook,
			double eyeX, double eyeY, double eyeZ, float maxLayer, int fov)
	{
	}

	private record Frame(View view, @Nullable NativeImage image)
	{
	}

	// `states` runs parallel to `palette`: a cell value v maps to index v - 1 in both, v == 0 being air
	record Model(short[] cells, BlockShapes.Shape[] palette, BlockState[] states, int sizeX, int sizeY, int sizeZ,
			double radius, double fitScale, boolean animated,
			Map<Integer, CompoundTag> blockEntityNbt)
	{
		int at(int x, int y, int z)
		{
			if (x < 0 || y < 0 || z < 0 || x >= this.sizeX || y >= this.sizeY || z >= this.sizeZ)
			{
				return 0;
			}

			return this.cells[(y * this.sizeZ + z) * this.sizeX + x] & 0xFFFF;
		}

		@Nullable
		CompoundTag nbtAt(int x, int y, int z)
		{
			if (this.blockEntityNbt == null || x < 0 || y < 0 || z < 0
					|| x >= this.sizeX || y >= this.sizeY || z >= this.sizeZ)
			{
				return null;
			}

			return this.blockEntityNbt.get((y * this.sizeZ + z) * this.sizeX + x);
		}
	}

	private record Draft(short[] cells, List<BlockState> states, int sizeX, int sizeY, int sizeZ,
			Map<Integer, CompoundTag> blockEntityNbt)
	{
	}

	private SchematicPreview()
	{
	}

	// A no-op: previews are addressed only by picked-file and URL slots, so there is nothing to discover
	public static void discover()
	{
	}

	public static int layerHeight(int slot)
	{
		Model model = MODELS.get(normalise(slot));
		return model == null ? -1 : model.sizeY();
	}

	public static int register(Path file)
	{
		int slot = PICKED_BASE + PICKED.size();
		PICKED.add(file);
		return slot;
	}

	public static synchronized int registerUrl(String url)
	{
		Integer existing = URL_INDEX.get(url);

		if (existing != null)
		{
			return URL_BASE + existing;
		}

		int index = URLS.size();
		URLS.add(url);
		URL_FILES.add(null);
		URL_INDEX.put(url, index);
		return URL_BASE + index;
	}

	private static @Nullable Path fileFor(int slot)
	{
		if (slot >= URL_BASE)
		{
			int i = slot - URL_BASE;
			return i >= 0 && i < URL_FILES.size() ? URL_FILES.get(i) : null;
		}

		if (slot >= PICKED_BASE)
		{
			int picked = slot - PICKED_BASE;
			return picked < PICKED.size() ? PICKED.get(picked) : null;
		}

		return null;
	}

	public static @Nullable Path pathFor(int slot)
	{
		return fileFor(normalise(slot));
	}

	private static boolean hasSource(int index)
	{
		if (index >= URL_BASE)
		{
			int i = index - URL_BASE;
			return i >= 0 && i < URLS.size();
		}

		return fileFor(index) != null;
	}

	public static boolean hasSchematic(int slot)
	{
		int index = normalise(slot);
		return index >= 0 && hasSource(index);
	}

	public static boolean failed(int slot)
	{
		Long failedAt = FAILED.get(normalise(slot));
		return failedAt != null && System.currentTimeMillis() - failedAt < FAILED_BACKOFF_MS;
	}

	public static boolean hosted(int slot)
	{
		return normalise(slot) >= URL_BASE;
	}

	public static String loadingStage()
	{
		return STAGE.get();
	}

	public static void retry(int slot)
	{
		int index = normalise(slot);

		if (index >= 0)
		{
			FAILED.remove(index);
		}
	}

	private static int normalise(int slot)
	{
		return slot >= PICKED_BASE ? slot : -1;
	}

	public static String name(int slot)
	{
		if (slot >= URL_BASE)
		{
			int i = slot - URL_BASE;
			String url = i >= 0 && i < URLS.size() ? URLS.get(i) : "";
			String file = url.substring(url.lastIndexOf('/') + 1);
			return file.endsWith(".litematic") ? file.substring(0, file.length() - 10) : file;
		}

		Path source = fileFor(normalise(slot));

		if (source == null)
		{
			return "";
		}

		String file = source.getFileName().toString();
		return file.endsWith(".litematic") ? file.substring(0, file.length() - 10) : file;
	}

	private static @Nullable Path ensureLocal(int slot)
	{
		if (slot < URL_BASE)
		{
			return fileFor(slot);
		}

		int i = slot - URL_BASE;

		if (i < 0 || i >= URLS.size())
		{
			return null;
		}

		Path cached = URL_FILES.get(i);

		if (cached != null && Files.exists(cached))
		{
			return cached;
		}

		try
		{
			Path dir = schcacheDir();
			Files.createDirectories(dir);
			// String.hashCode collides across URLs, which previewed the wrong build for the colliding pair
			Path file = dir.resolve(hashUrl(URLS.get(i)) + ".litematic");

			// The cache outlives the session, so a build already on disk must not be pulled down again
			if (Files.exists(file))
			{
				URL_FILES.set(i, file);
				return file;
			}

			if (!Backend.download(URLS.get(i), file))
			{
				return null;
			}

			URL_FILES.set(i, file);
			pruneSchcache(dir);
			return file;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not cache preview file", e);
			return null;
		}
	}

	private static Path schcacheDir()
	{
		return FabricLoader.getInstance().getGameDir().resolve(SchematicIndexMod.MOD_ID).resolve("schcache");
	}

	private static String hashUrl(String url)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();

			for (int i = 0; i < 16; i++)
			{
				hex.append(String.format("%02x", hash[i]));
			}

			return hex.toString();
		}
		catch (Exception e)
		{
			// SHA-256 is guaranteed present on every JVM, so this is unreachable in practice
			return Integer.toHexString(url.hashCode());
		}
	}

	private static void pruneSchcache(Path dir)
	{
		try (java.util.stream.Stream<Path> stream = Files.list(dir))
		{
			List<Path> files = stream.filter(Files::isRegularFile)
					.sorted((a, b) -> Long.compare(lastModified(a), lastModified(b)))
					.collect(java.util.stream.Collectors.toList());

			long total = 0L;

			for (Path file : files)
			{
				total += sizeOf(file);
			}

			for (Path file : files)
			{
				if (total <= MAX_SCHCACHE_BYTES)
				{
					break;
				}

				long size = sizeOf(file);

				// Evicting a file a live URL slot points at would force an immediate re-download
				if (URL_FILES.contains(file))
				{
					continue;
				}

				try
				{
					Files.deleteIfExists(file);
					total -= size;
				}
				catch (Exception ignored)
				{
				}
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not prune schematic cache", e);
		}
	}

	private static long lastModified(Path file)
	{
		try
		{
			return Files.getLastModifiedTime(file).toMillis();
		}
		catch (Exception e)
		{
			return 0L;
		}
	}

	private static long sizeOf(Path file)
	{
		try
		{
			return Files.size(file);
		}
		catch (Exception e)
		{
			return 0L;
		}
	}

	public static float clampZoom(int slot, float zoom)
	{
		Model model = MODELS.get(normalise(slot));
		float max = model == null ? 12.0F : (float) (Math.min(WIDTH, HEIGHT) / 2.0D / model.fitScale());
		return Math.max(MIN_ZOOM, Math.min(Math.max(MIN_ZOOM, max), zoom));
	}

	public static double @Nullable [] eye(int slot, float yaw, float pitch, float zoom, boolean cutaway)
	{
		Model model = MODELS.get(normalise(slot));
		return model == null ? null : eye(model, slot, yaw, pitch, zoom, cutaway);
	}

	private static double[] eye(Model model, int slot, float yaw, float pitch, float zoom, boolean cutaway)
	{
		double radians = Math.toRadians(yaw);
		double tilt = Math.toRadians(pitch);
		double dirX = Math.sin(radians) * Math.cos(tilt);
		double dirY = -Math.sin(tilt);
		double dirZ = Math.cos(radians) * Math.cos(tilt);

		double distance = model.radius() * 2.0D / Math.max(0.05D, clampZoom(slot, zoom));

		if (!cutaway)
		{
			distance = Math.max(distance, model.radius() + 2.0D);
		}

		return new double[]
		{
				model.sizeX() / 2.0D - dirX * distance,
				model.sizeY() / 2.0D - dirY * distance,
				model.sizeZ() / 2.0D - dirZ * distance
		};
	}

	public static void request(int slot, float yaw, float pitch, float zoom, boolean cutaway,
			boolean freeLook, double @Nullable [] eye, float maxLayer)
	{
		int index = normalise(slot);

		if (index < 0 || !hasSource(index))
		{
			return;
		}

		if (!MODELS.containsKey(index))
		{
			load(index);
			lastSlot = Integer.MIN_VALUE;
			return;
		}

		// Marks the model most-recently-used, which is what makes the cache LRU rather than FIFO
		touchModel(index);

		boolean eyeNull = eye == null;

		Model current = MODELS.get(index);
		boolean animated = current != null && current.animated();
		long animFrame = animated ? System.currentTimeMillis() / ANIM_FRAME_MS : 0L;
		int fov = Settings.previewFov();

		if (index == lastSlot && yaw == lastYaw && pitch == lastPitch && zoom == lastZoom
				&& maxLayer == lastMaxLayer && fov == lastFov && cutaway == lastCutaway && freeLook == lastFreeLook
				&& eyeNull == lastEyeNull
				&& (eyeNull || (eye[0] == lastEyeX && eye[1] == lastEyeY && eye[2] == lastEyeZ))
				&& (!animated || animFrame == lastAnimFrame))
		{
			return;
		}

		lastAnimFrame = animFrame;
		lastSlot = index;
		lastYaw = yaw;
		lastPitch = pitch;
		lastZoom = zoom;
		lastMaxLayer = maxLayer;
		lastFov = fov;
		lastCutaway = cutaway;
		lastFreeLook = freeLook;
		lastEyeNull = eyeNull;

		if (!eyeNull)
		{
			lastEyeX = eye[0];
			lastEyeY = eye[1];
			lastEyeZ = eye[2];
		}

		double[] from = eye != null ? eye
				: (freeLook ? eye(MODELS.get(index), index, yaw, pitch, zoom, cutaway) : null);

		View wanted = new View(index, yaw, pitch, clampZoom(index, zoom), cutaway, freeLook,
				from == null ? 0.0D : from[0], from == null ? 0.0D : from[1], from == null ? 0.0D : from[2],
				Math.max(0.0F, Math.min(1.0F, maxLayer)), fov);

		// An animated schematic's View can be identical while its sprite phase has advanced
		if (!animated && (wanted.equals(queued) || (!rendering && wanted.equals(shown))))
		{
			return;
		}

		queued = wanted;
		startNext();
	}

	private static void startNext()
	{
		if (rendering || queued == null)
		{
			return;
		}

		View view = queued;
		Model model = MODELS.get(view.slot());

		if (model == null)
		{
			return;
		}

		queued = null;
		rendering = true;

		// Snapshotted so a mid-flight setRenderer cannot split this job across two renderers
		PreviewRenderer active = renderer;

		long started = System.nanoTime();

		// A direct-to-texture renderer skips the NativeImage / PENDING / DynamicTexture pipeline entirely
		if (active.rendersToTexture())
		{
			frameRenderJob = () -> {
				try
				{
					if (viewId == null)
					{
						viewId = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "preview");
					}

					if (active.renderToTexture(model, view, viewId))
					{
						shown = view;
						renderWidth = active.textureWidth();
						renderHeight = active.textureHeight();
						reportFirstFrame(started);
					}
				}
				catch (Exception e)
				{
					SchematicIndexMod.LOGGER.warn("Preview render failed", e);
				}

				rendering = false;
				startNext();
			};

			return;
		}

		NativeImage buffer = scratch;
		scratch = null;

		Runnable job = () -> {
			try
			{
				// A sink rather than a return value: a fence-deferred readback lands on a later tick
				active.renderAsync(model, view, buffer, image -> {
					if (image != null)
					{
						reportFirstFrame(started);
					}

					PENDING.add(new Frame(view, image));
				});
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Preview render failed", e);

				if (buffer != null)
				{
					buffer.close();
				}

				PENDING.add(new Frame(view, null));
			}
		};

		if (active.requiresRenderThread())
		{
			// The block pipelines need per-frame GPU state, and Minecraft.execute runs between frames
			frameRenderJob = job;
		}
		else
		{
			RENDERER.execute(job);
		}
	}

	// Wall time from the render request to the frame being usable, so a fenced readback counts its wait
	private static void reportFirstFrame(long startedNanos)
	{
		if (firstFrameReported)
		{
			return;
		}

		firstFrameReported = true;
		long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
		SchematicIndexMod.LOGGER.debug("First preview frame in {} ms", ms);
		Usage.once("perf:preview_" + (ms < 100L ? "fast" : ms > 500L ? "slow" : "ok"));
	}

	private static void load(int index)
	{
		Long failedAt = FAILED.get(index);

		if (failedAt != null)
		{
			if (System.currentTimeMillis() - failedAt < FAILED_BACKOFF_MS)
			{
				return;
			}

			FAILED.remove(index);
		}

		// LOADING is both the in-flight guard and the state IndexScreen paints its placeholder from
		if (!LOADING.add(index))
		{
			return;
		}

		// Only the sprite/shape bake needs the block renderer; it hops back to the client thread later
		Net.submit(() -> {
			Path file = null;
			Draft draft = null;

			try
			{
				STAGE.set(index >= URL_BASE ? "Downloading" : "Reading");
				file = ensureLocal(index);

				if (file != null)
				{
					LitematicaSchematic schematic;

					// Some schematics' parse touches RenderSystem, which throws off the client thread
					STAGE.set("Reading");
					Path dir = file.getParent();
					String name = file.getFileName().toString();

					try
					{
						schematic = LitematicaSchematic.createFromFile(dir, name, FileType.LITEMATICA_SCHEMATIC);
					}
					catch (Exception offThread)
					{
						try
						{
							schematic = Minecraft.getInstance().<LitematicaSchematic>submit(() ->
									LitematicaSchematic.createFromFile(dir, name, FileType.LITEMATICA_SCHEMATIC)).join();
						}
						catch (Exception onThread)
						{
							SchematicIndexMod.LOGGER.warn("Could not read {}", name, onThread);
							schematic = null;
						}
					}

					if (schematic != null)
					{
						STAGE.set("Building");
						draft = collect(schematic);
					}
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Could not read preview file", e);
				draft = null;
			}

			if (draft == null)
			{
				FAILED.put(index, System.currentTimeMillis());
				LOADING.remove(index);
				STAGE.set("");
				return;
			}

			STAGE.set("Rendering");

			// LOADING stays set until registerModel clears it, so request() cannot re-enter load()
			Draft ready = draft;
			Path source = file;
			Minecraft.getInstance().execute(() -> registerModel(index, ready, source));
		});
	}

	// Palette entries baked per client frame. Baking a whole palette at once hitched a visible frame
	private static final int BAKE_CHUNK = 48;

	private static void registerModel(int index, Draft draft, Path file)
	{
		// Not in LOADING means releaseAll or clearCache ran mid-parse, so this result is stale
		if (!LOADING.contains(index))
		{
			STAGE.set("");
			return;
		}

		BlockShapes.Shape[] palette = new BlockShapes.Shape[draft.states().size()];
		bakeChunk(index, draft, file, palette, 0);
	}

	// The Model is not published until the whole palette is baked, so a partial one is never traced
	private static void bakeChunk(int index, Draft draft, Path file, BlockShapes.Shape[] palette, int start)
	{
		if (!LOADING.contains(index))
		{
			STAGE.set("");
			return;
		}

		int count = palette.length;
		int end = Math.min(count, start + BAKE_CHUNK);

		try
		{
			for (int i = start; i < end; i++)
			{
				BlockState state = draft.states().get(i);

				// A null palette slot reads as empty, so one bad state does not fail the whole preview
				try
				{
					BlockTextures.Resolved sprites = BlockTextures.resolveSprites(state);
					BlockShapes.Raw shape = BlockShapes.extract(state);
					BlockTextures.Faces faces = BlockTextures.load(sprites, state);
					palette[i] = BlockShapes.build(shape, faces);
				}
				catch (Exception e)
				{
					palette[i] = null;
					SchematicIndexMod.LOGGER.debug("Skipping unsupported block {} in preview", state, e);
				}
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not build model for {}", file.getFileName(), e);
			FAILED.put(index, System.currentTimeMillis());
			LOADING.remove(index);
			STAGE.set("");
			return;
		}

		if (end < count)
		{
			Minecraft.getInstance().execute(() -> bakeChunk(index, draft, file, palette, end));
			return;
		}

		try
		{
			MODELS.put(index, finish(draft, palette));
			touchModel(index);
			evictModels(index);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not build model for {}", file.getFileName(), e);
			FAILED.put(index, System.currentTimeMillis());
		}
		finally
		{
			LOADING.remove(index);
			STAGE.set("");
		}
	}

	private static void touchModel(int index)
	{
		MODEL_ORDER.remove(index);
		MODEL_ORDER.add(index);
	}

	private static void evictModels(int keep)
	{
		while (MODELS.size() > MAX_CACHED_MODELS)
		{
			Integer oldest = MODEL_ORDER.poll();

			if (oldest == null)
			{
				break;
			}

			if (oldest == keep)
			{
				MODEL_ORDER.add(oldest);
				continue;
			}

			MODELS.remove(oldest);

			// Without this, texture() keeps returning the evicted model's orphaned last render
			if (shown != null && shown.slot() == oldest)
			{
				shown = null;
				queued = null;
				lastSlot = Integer.MIN_VALUE;
			}
		}
	}

	private static @Nullable Draft collect(LitematicaSchematic schematic)
	{
		Map<String, BlockPos> sizes = schematic.getAreaSizes();

		if (sizes.isEmpty())
		{
			return null;
		}

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (Map.Entry<String, BlockPos> region : sizes.entrySet())
		{
			BlockPos origin = schematic.getSubRegionPosition(region.getKey());

			if (origin == null)
			{
				continue;
			}

			BlockPos corner = farCorner(origin, region.getValue());
			minX = Math.min(minX, Math.min(origin.getX(), corner.getX()));
			minY = Math.min(minY, Math.min(origin.getY(), corner.getY()));
			minZ = Math.min(minZ, Math.min(origin.getZ(), corner.getZ()));
			maxX = Math.max(maxX, Math.max(origin.getX(), corner.getX()));
			maxY = Math.max(maxY, Math.max(origin.getY(), corner.getY()));
			maxZ = Math.max(maxZ, Math.max(origin.getZ(), corner.getZ()));
		}

		if (minX > maxX)
		{
			return null;
		}

		int spanX = maxX - minX + 1;
		int spanY = maxY - minY + 1;
		int spanZ = maxZ - minZ + 1;

		int step = 1;

		// Ceiling division: floor division dropped the outermost source layers and clipped the far edges
		while ((long) ceilDiv(spanX, step) * ceilDiv(spanY, step) * ceilDiv(spanZ, step) > MAX_VOXELS)
		{
			step++;
		}

		int sizeX = Math.max(1, ceilDiv(spanX, step));
		int sizeY = Math.max(1, ceilDiv(spanY, step));
		int sizeZ = Math.max(1, ceilDiv(spanZ, step));
		short[] cells = new short[sizeX * sizeY * sizeZ];

		List<BlockState> states = new ArrayList<>();
		Map<BlockState, Integer> indices = new HashMap<>();
		Map<Integer, CompoundTag> blockEntityNbt = new HashMap<>();

		for (Map.Entry<String, BlockPos> region : sizes.entrySet())
		{
			String name = region.getKey();
			BlockPos origin = schematic.getSubRegionPosition(name);
			LitematicaBlockStateContainer container = schematic.getSubRegionContainer(name);

			if (origin == null || container == null)
			{
				continue;
			}

			// Keyed by region-local BlockPos, matching the cx,cy,cz loop below
			Map<BlockPos, CompoundTag> regionTileEntities = LitematicaCompat.blockEntities(schematic, name);

			try
			{
				BlockPos corner = farCorner(origin, region.getValue());
				int baseX = Math.min(origin.getX(), corner.getX());
				int baseY = Math.min(origin.getY(), corner.getY());
				int baseZ = Math.min(origin.getZ(), corner.getZ());

				Vec3i containerSize = container.getSize();

				for (int cx = 0; cx < containerSize.getX(); cx++)
				{
					for (int cy = 0; cy < containerSize.getY(); cy++)
					{
						for (int cz = 0; cz < containerSize.getZ(); cz++)
						{
							try
							{
								BlockState state = container.get(cx, cy, cz);

								if (state == null || state.isAir())
								{
									continue;
								}

								int gx = (baseX + cx - minX) / step;
								int gy = (baseY + cy - minY) / step;
								int gz = (baseZ + cz - minZ) / step;

								if (gx < 0 || gy < 0 || gz < 0 || gx >= sizeX || gy >= sizeY || gz >= sizeZ)
								{
									continue;
								}

								Integer palette = indices.get(state);

								if (palette == null)
								{
									palette = states.size();
									indices.put(state, palette);
									states.add(state);
								}

								int flatIndex = (gy * sizeZ + gz) * sizeX + gx;
								cells[flatIndex] = (short) (palette + 1);

								// When downsampling collapses several source cells into one, the last write wins
								if (!regionTileEntities.isEmpty())
								{
									CompoundTag data = regionTileEntities.get(new BlockPos(cx, cy, cz));

									if (data != null)
									{
										blockEntityNbt.put(flatIndex, data);
									}
								}
							}
							catch (Exception e)
							{
								SchematicIndexMod.LOGGER.debug("Skipping unreadable block at {} {},{},{}",
										name, cx, cy, cz, e);
							}
						}
					}
				}
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.debug("Skipping unreadable region {}", name, e);
			}
		}

		return new Draft(cells, states, sizeX, sizeY, sizeZ, blockEntityNbt);
	}

	private static int ceilDiv(int value, int divisor)
	{
		return (value + divisor - 1) / divisor;
	}

	private static BlockPos farCorner(BlockPos origin, Vec3i size)
	{
		return new BlockPos(
				origin.getX() + size.getX() - (size.getX() < 0 ? -1 : 1),
				origin.getY() + size.getY() - (size.getY() < 0 ? -1 : 1),
				origin.getZ() + size.getZ() - (size.getZ() < 0 ? -1 : 1));
	}

	private static Model finish(Draft draft, BlockShapes.Shape[] palette)
	{
		double radius = 0.5D * Math.sqrt((double) draft.sizeX() * draft.sizeX()
				+ (double) draft.sizeY() * draft.sizeY()
				+ (double) draft.sizeZ() * draft.sizeZ());
		double fitScale = Math.min(WIDTH, HEIGHT) / (2.0D * Math.max(1.0D, radius)) * 0.95D;

		boolean animated = false;

		for (BlockShapes.Shape shape : palette)
		{
			if (shape != null && shape.animated())
			{
				animated = true;
				break;
			}
		}

		BlockState[] states = draft.states().toArray(new BlockState[0]);

		return new Model(draft.cells(), palette, states, draft.sizeX(), draft.sizeY(), draft.sizeZ(), radius,
				fitScale, animated, draft.blockEntityNbt());
	}

	static NativeImage rasterise(Model model, View view, @Nullable NativeImage buffer)
	{
		// Snapshots the animation clock, so every sprite in this frame samples the same phase
		BlockTextures.beginFrame();

		NativeImage image = buffer != null && buffer.getWidth() == WIDTH && buffer.getHeight() == HEIGHT
				? buffer
				: new NativeImage(NativeImage.Format.RGBA, WIDTH, HEIGHT, false);

		double yaw = Math.toRadians(view.yaw());
		double pitch = Math.toRadians(view.pitch());
		double sinYaw = Math.sin(yaw);
		double cosYaw = Math.cos(yaw);
		double sinPitch = Math.sin(pitch);
		double cosPitch = Math.cos(pitch);

		double dirX = sinYaw * cosPitch;
		double dirY = -sinPitch;
		double dirZ = cosYaw * cosPitch;
		double rightX = cosYaw;
		double rightZ = -sinYaw;
		double upX = sinPitch * sinYaw;
		double upY = cosPitch;
		double upZ = sinPitch * cosYaw;

		double scale = model.fitScale() * view.zoom();

		int lod = lodFor(scale);

		double distance = model.radius() * 2.0D / Math.max(0.05D, view.zoom());

		if (!view.cutaway() && !view.freeLook())
		{
			distance = Math.max(distance, model.radius() + 2.0D);
		}
		double centreX = model.sizeX() / 2.0D;
		double centreY = model.sizeY() / 2.0D;
		double centreZ = model.sizeZ() / 2.0D;

		double planeDistance = view.cutaway() && !view.freeLook()
				? centreX * dirX + centreY * dirY + centreZ * dirZ - distance
				: Double.NEGATIVE_INFINITY;

		int layerCeiling = view.maxLayer() >= 1.0F
				? model.sizeY()
				: Math.max(1, Math.round(view.maxLayer() * model.sizeY()));

		int step = 1;
		int rowBlocks = (HEIGHT + step - 1) / step;

		final double fDirX = dirX, fDirY = dirY, fDirZ = dirZ;
		final double fRightX = rightX, fRightZ = rightZ;
		final double fUpX = upX, fUpY = upY, fUpZ = upZ;
		final double fScale = scale, fDistance = distance;
		final double fCentreX = centreX, fCentreY = centreY, fCentreZ = centreZ;
		final double fPlaneDistance = planeDistance;
		final int fLayerCeiling = layerCeiling, fLod = lod, fStep = step;
		final NativeImage target = image;

		if (view.freeLook())
		{
			final double focal = 1.0D / Math.tan(Math.toRadians(FIELD_OF_VIEW) / 2.0D);
			final double halfHeight = HEIGHT / 2.0D;
			final double eyeX = view.eyeX(), eyeY = view.eyeY(), eyeZ = view.eyeZ();

			runParallelRows(rowBlocks, rb -> {
				int py = rb * fStep;
				double screenY = (halfHeight - py - 0.5D) / halfHeight;
				ShapeTracer.Hit hit = new ShapeTracer.Hit();

				for (int px = 0; px < WIDTH; px += fStep)
				{
					double screenX = (px + 0.5D - WIDTH / 2.0D) / halfHeight;

					double rayX = fDirX * focal + fRightX * screenX + fUpX * screenY;
					double rayY = fDirY * focal + fUpY * screenY;
					double rayZ = fDirZ * focal + fRightZ * screenX + fUpZ * screenY;
					double length = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);

					int color = trace(model, eyeX, eyeY, eyeZ, rayX / length, rayY / length, rayZ / length,
							fPlaneDistance, fLayerCeiling, fLod, hit);
					fillBlock(target, px, py, fStep, color);
				}
			});

			return image;
		}

		runParallelRows(rowBlocks, rb -> {
			int py = rb * fStep;
			double screenY = (HEIGHT / 2.0D - py - 0.5D) / fScale;
			ShapeTracer.Hit hit = new ShapeTracer.Hit();

			for (int px = 0; px < WIDTH; px += fStep)
			{
				double screenX = (px + 0.5D - WIDTH / 2.0D) / fScale;

				double originX = fCentreX + fRightX * screenX + fUpX * screenY - fDirX * fDistance;
				double originY = fCentreY + fUpY * screenY - fDirY * fDistance;
				double originZ = fCentreZ + fRightZ * screenX + fUpZ * screenY - fDirZ * fDistance;

				int color = trace(model, originX, originY, originZ, fDirX, fDirY, fDirZ,
						fPlaneDistance, fLayerCeiling, fLod, hit);
				fillBlock(target, px, py, fStep, color);
			}
		});

		return image;
	}

	// A parallel stream forked inside a pool worker runs on that pool, confining the trace to RAYTRACE_POOL
	private static void runParallelRows(int rowBlocks, java.util.function.IntConsumer body)
	{
		RAYTRACE_POOL.submit(() -> IntStream.range(0, rowBlocks).parallel().forEach(body)).join();
	}

	private static void fillBlock(NativeImage image, int px, int py, int step, int color)
	{
		if (step == 1)
		{
			image.setPixel(px, py, color);
			return;
		}

		int maxX = Math.min(WIDTH, px + step);
		int maxY = Math.min(HEIGHT, py + step);

		for (int yy = py; yy < maxY; yy++)
		{
			for (int xx = px; xx < maxX; xx++)
			{
				image.setPixel(xx, yy, color);
			}
		}
	}

	private static int trace(Model model, double originX, double originY, double originZ,
			double dirX, double dirY, double dirZ, double planeDistance, int layerCeiling, int lod,
			ShapeTracer.Hit hit)
	{
		double accR = 0.0D;
		double accG = 0.0D;
		double accB = 0.0D;
		double trans = 1.0D;

		double near = 0.0D;
		double far = Double.MAX_VALUE;
		int entryAxis = -1;

		for (int axis = 0; axis < 3; axis++)
		{
			double origin = axis == 0 ? originX : axis == 1 ? originY : originZ;
			double direction = axis == 0 ? dirX : axis == 1 ? dirY : dirZ;
			int bound = axis == 0 ? model.sizeX() : axis == 1 ? model.sizeY() : model.sizeZ();

			if (Math.abs(direction) < 1.0E-9D)
			{
				if (origin < 0.0D || origin > bound)
				{
					return composite(accR, accG, accB, trans, BACKGROUND);
				}

				continue;
			}

			double first = (0.0D - origin) / direction;
			double second = (bound - origin) / direction;

			if (first > second)
			{
				double swap = first;
				first = second;
				second = swap;
			}

			if (first > near)
			{
				near = first;
				entryAxis = axis;
			}

			far = Math.min(far, second);
		}

		if (near > far || far < 0.0D)
		{
			return composite(accR, accG, accB, trans, BACKGROUND);
		}

		double travelled = Math.max(near, 0.0D) + 1.0E-4D;
		double pointX = originX + dirX * travelled;
		double pointY = originY + dirY * travelled;
		double pointZ = originZ + dirZ * travelled;

		int x = clamp((int) Math.floor(pointX), model.sizeX());
		int y = clamp((int) Math.floor(pointY), model.sizeY());
		int z = clamp((int) Math.floor(pointZ), model.sizeZ());

		int stepX = dirX > 0 ? 1 : -1;
		int stepY = dirY > 0 ? 1 : -1;
		int stepZ = dirZ > 0 ? 1 : -1;

		double deltaX = Math.abs(1.0D / dirX);
		double deltaY = Math.abs(1.0D / dirY);
		double deltaZ = Math.abs(1.0D / dirZ);

		double nextX = travelled + boundary(pointX, x, dirX, stepX);
		double nextY = travelled + boundary(pointY, y, dirY, stepY);
		double nextZ = travelled + boundary(pointZ, z, dirZ, stepZ);

		int face = switch (entryAxis)
		{
			case 0 -> dirX > 0 ? Face.WEST : Face.EAST;
			case 1 -> dirY > 0 ? Face.DOWN : Face.UP;
			case 2 -> dirZ > 0 ? Face.NORTH : Face.SOUTH;

			default -> dirY < 0 ? Face.UP : Face.DOWN;
		};
		int guard = (model.sizeX() + model.sizeY() + model.sizeZ()) * 3;

		int insideX = (int) Math.floor(originX);
		int insideY = (int) Math.floor(originY);
		int insideZ = (int) Math.floor(originZ);

		for (int i = 0; i < guard; i++)
		{
			int cell = (y >= layerCeiling || (x == insideX && y == insideY && z == insideZ))
					? 0 : model.at(x, y, z);

			if (cell != 0)
			{
				BlockShapes.Shape shape = model.palette()[cell - 1];

				if (travelled < 2.0D && behindCamera(x, y, z, dirX, dirY, dirZ, planeDistance))
				{
					shape = null;
				}

				if (shape != null && shape.invisible())
				{
					shape = null;
				}

				if (shape != null)
				{
					if (shape.waterlogged())
					{
						// Before the fullCube branch: its opaque return would leave a waterlogged cube dry
						double wt = fluidEntry(originX, originY, originZ, dirX, dirY, dirZ, x, y, z,
								shape.fluidTop(), hit);

						if (!Double.isNaN(wt))
						{
							int waterColor = light(BlockTextures.defaultWaterTint(), hit.face);
							double a = WATER_ALPHA;
							accR += trans * a * ((waterColor >> 16) & 0xFF);
							accG += trans * a * ((waterColor >> 8) & 0xFF);
							accB += trans * a * (waterColor & 0xFF);
							trans *= 1.0D - a;

							if (trans < 0.02D)
							{
								return composite(accR, accG, accB, trans, BACKGROUND);
							}
						}
					}

					if (shape.fluid() != BlockShapes.FLUID_NONE)
					{
						// NaN means the ray grazed the empty sliver above the surface, so the DDA advances
						double ft = fluidEntry(originX, originY, originZ, dirX, dirY, dirZ, x, y, z,
								shape.fluidTop(), hit);

						if (!Double.isNaN(ft))
						{
							int fFace = hit.face;
							double hx = originX + dirX * ft;
							double hy = originY + dirY * ft;
							double hz = originZ + dirZ * ft;

							if (shape.fluid() == BlockShapes.FLUID_LAVA)
							{
								int texel = sampleFace(shape.faces(), fFace, hx, hy, hz, lod);

								if ((texel >>> 24) >= 16)
								{
									return composite(accR, accG, accB, trans,
											scaleRgb(0xFF000000 | (texel & 0xFFFFFF), LAVA_EMISSIVE));
								}
							}
							else
							{
								int fluidColor = shade(shape.faces(), fFace, hx, hy, hz, lod);

								if (fluidColor != 0)
								{
									double a = WATER_ALPHA;
									accR += trans * a * ((fluidColor >> 16) & 0xFF);
									accG += trans * a * ((fluidColor >> 8) & 0xFF);
									accB += trans * a * (fluidColor & 0xFF);
									trans *= 1.0D - a;

									if (trans < 0.02D)
									{
										return composite(accR, accG, accB, trans, BACKGROUND);
									}

									shape = null;
								}
							}
						}
					}
					else if (shape.fullCube())
					{
						double hitX = originX + dirX * travelled;
						double hitY = originY + dirY * travelled;
						double hitZ = originZ + dirZ * travelled;
						int faceColor = shade(shape.faces(), face, hitX, hitY, hitZ, lod);

						if (faceColor == 0)
						{
							// A see-through texel must not occlude, so the ray continues through it
							shape = null;
						}
						else
						{
							double fu;
							double fv;

							switch (face)
							{
								case Face.UP, Face.DOWN -> {
									fu = frac(hitX);
									fv = frac(hitZ);
								}
								case Face.NORTH, Face.SOUTH -> {
									fu = frac(hitX);
									fv = frac(hitY);
								}
								default -> {
									fu = frac(hitZ);
									fv = frac(hitY);
								}
							}

							double ao = faceAO(model, x, y, z, face, fu, fv, layerCeiling);

							if (ao < 1.0D)
							{
								faceColor = scaleRgb(faceColor, ao);
							}

							if (shape.translucency() > 0.0F)
							{
								// `trans` is the remaining front-to-back visibility; once spent the ray stops
								double a = shape.translucency();
								accR += trans * a * ((faceColor >> 16) & 0xFF);
								accG += trans * a * ((faceColor >> 8) & 0xFF);
								accB += trans * a * (faceColor & 0xFF);
								trans *= 1.0D - a;

								if (trans < 0.02D)
								{
									return composite(accR, accG, accB, trans, BACKGROUND);
								}

								shape = null;
							}
							else
							{
								return composite(accR, accG, accB, trans, faceColor);
							}
						}
					}

					if (shape != null
							&& ShapeTracer.trace(shape, originX - x, originY - y, originZ - z, dirX, dirY, dirZ, lod, hit))
					{
						int color = applyQuadAo(model, x, y, z, hit.face, layerCeiling,
								light(hit.color, hit.face));
						return composite(accR, accG, accB, trans, color);
					}
				}
			}

			if (nextX < nextY && nextX < nextZ)
			{
				travelled = nextX;
				x += stepX;
				nextX += deltaX;
				face = stepX > 0 ? Face.WEST : Face.EAST;

				if (x < 0 || x >= model.sizeX())
				{
					return composite(accR, accG, accB, trans, BACKGROUND);
				}
			}
			else if (nextY < nextZ)
			{
				travelled = nextY;
				y += stepY;
				nextY += deltaY;
				face = stepY > 0 ? Face.DOWN : Face.UP;

				if (y < 0 || y >= model.sizeY())
				{
					return composite(accR, accG, accB, trans, BACKGROUND);
				}
			}
			else
			{
				travelled = nextZ;
				z += stepZ;
				nextZ += deltaZ;
				face = stepZ > 0 ? Face.NORTH : Face.SOUTH;

				if (z < 0 || z >= model.sizeZ())
				{
					return composite(accR, accG, accB, trans, BACKGROUND);
				}
			}
		}

		return composite(accR, accG, accB, trans, BACKGROUND);
	}

	private static final class Face
	{
		private static final int DOWN = 0;
		private static final int UP = 1;
		private static final int NORTH = 2;
		private static final int SOUTH = 3;
		private static final int WEST = 4;
		private static final int EAST = 5;

		private Face()
		{
		}
	}

	private static boolean behindCamera(int x, int y, int z, double dirX, double dirY, double dirZ,
			double planeDistance)
	{
		double cornerX = dirX > 0 ? x : x + 1;
		double cornerY = dirY > 0 ? y : y + 1;
		double cornerZ = dirZ > 0 ? z : z + 1;
		return cornerX * dirX + cornerY * dirY + cornerZ * dirZ < planeDistance;
	}

	private static double boundary(double point, int cell, double direction, int step)
	{
		if (Math.abs(direction) < 1.0E-9D)
		{
			return Double.MAX_VALUE;
		}

		double edge = step > 0 ? cell + 1 : cell;
		return (edge - point) / direction;
	}

	private static int clamp(int value, int size)
	{
		return Math.max(0, Math.min(size - 1, value));
	}

	private static int sampleFace(BlockTextures.Faces faces, int face, double hitX, double hitY, double hitZ,
			int lod)
	{
		double u;
		double v;

		switch (face)
		{
			case Face.UP, Face.DOWN -> {
				u = frac(hitX);
				v = frac(hitZ);
			}
			case Face.WEST, Face.EAST -> {
				u = frac(hitZ);
				v = 1.0D - frac(hitY);
			}
			default -> {
				u = frac(hitX);
				v = 1.0D - frac(hitY);
			}
		}

		return faces.sampleARGB(face, u, v, lod);
	}

	// Returns 0 as a sentinel for a see-through texel; a real shaded colour is always alpha 0xFF
	private static int shade(BlockTextures.Faces faces, int face, double hitX, double hitY, double hitZ, int lod)
	{
		int argb = sampleFace(faces, face, hitX, hitY, hitZ, lod);

		if ((argb >>> 24) < 16)
		{
			return 0;
		}

		return light(argb, face);
	}

	private static final double LOG2 = Math.log(2.0D);

	private static int lodFor(double pixelsPerBlock)
	{
		double texelsPerPixel = 16.0D / Math.max(1.0D, pixelsPerBlock);

		if (texelsPerPixel <= 1.0D)
		{
			return 0;
		}

		return Math.max(0, Math.min(4, (int) Math.round(Math.log(texelsPerPixel) / LOG2)));
	}

	private static int light(int color, int face)
	{
		// The exact vanilla multipliers; matching them is what makes the preview read as Minecraft
		double brightness = switch (face)
		{
			case Face.UP -> 1.0D;
			case Face.DOWN -> 0.5D;
			case Face.NORTH, Face.SOUTH -> 0.8D;
			case Face.WEST, Face.EAST -> 0.6D;

			default -> 0.9D;
		};

		int red = (int) Math.min(255.0D, ((color >> 16) & 0xFF) * brightness);
		int green = (int) Math.min(255.0D, ((color >> 8) & 0xFF) * brightness);
		int blue = (int) Math.min(255.0D, (color & 0xFF) * brightness);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	// Indexed by occlusion level. The 0.5 floor is softer than vanilla's ~0.2, which blackens corners
	private static final double[] AO_LEVELS = {0.5D, 0.7D, 0.85D, 1.0D};

	private static boolean isOccluder(Model model, int x, int y, int z, int layerCeiling)
	{
		if (y >= layerCeiling)
		{
			return false;
		}

		int cell = model.at(x, y, z);

		if (cell == 0)
		{
			return false;
		}

		BlockShapes.Shape shape = model.palette()[cell - 1];
		return shape != null && shape.fullCube() && shape.translucency() == 0.0F;
	}

	// The standard voxel AO rule: the level counts how many of the two edges and the diagonal are open
	private static double cornerAO(Model model, int ox, int oy, int oz,
			int ax, int ay, int az, int sa, int bx, int by, int bz, int sb, int layerCeiling)
	{
		boolean side1 = isOccluder(model, ox + ax * sa, oy + ay * sa, oz + az * sa, layerCeiling);
		boolean side2 = isOccluder(model, ox + bx * sb, oy + by * sb, oz + bz * sb, layerCeiling);
		int level;

		if (side1 && side2)
		{
			level = 0;
		}
		else
		{
			boolean corner = isOccluder(model,
					ox + ax * sa + bx * sb, oy + ay * sa + by * sb, oz + az * sa + bz * sb, layerCeiling);
			level = 3 - ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0));
		}

		return AO_LEVELS[level];
	}

	// Occluders are sampled outside the face, which is why a flat wall stays lit and only corners darken
	private static double faceAO(Model model, int x, int y, int z, int face, double fu, double fv,
			int layerCeiling)
	{
		int nx = 0;
		int ny = 0;
		int nz = 0;

		switch (face)
		{
			case Face.UP -> ny = 1;
			case Face.DOWN -> ny = -1;
			case Face.NORTH -> nz = -1;
			case Face.SOUTH -> nz = 1;
			case Face.WEST -> nx = -1;
			case Face.EAST -> nx = 1;

			default -> {
				// No offset, so AO degrades to a no-op rather than misreading neighbours
			}
		}

		int ox = x + nx;
		int oy = y + ny;
		int oz = z + nz;

		int ax;
		int ay;
		int az;
		int bx;
		int by;
		int bz;

		switch (face)
		{
			case Face.UP, Face.DOWN -> {
				ax = 1;
				ay = 0;
				az = 0;
				bx = 0;
				by = 0;
				bz = 1;
			}
			case Face.NORTH, Face.SOUTH -> {
				ax = 1;
				ay = 0;
				az = 0;
				bx = 0;
				by = 1;
				bz = 0;
			}
			default -> {
				ax = 0;
				ay = 0;
				az = 1;
				bx = 0;
				by = 1;
				bz = 0;
			}
		}

		double c00 = cornerAO(model, ox, oy, oz, ax, ay, az, -1, bx, by, bz, -1, layerCeiling);
		double c10 = cornerAO(model, ox, oy, oz, ax, ay, az, 1, bx, by, bz, -1, layerCeiling);
		double c01 = cornerAO(model, ox, oy, oz, ax, ay, az, -1, bx, by, bz, 1, layerCeiling);
		double c11 = cornerAO(model, ox, oy, oz, ax, ay, az, 1, bx, by, bz, 1, layerCeiling);

		double bottom = c00 * (1.0D - fu) + c10 * fu;
		double top = c01 * (1.0D - fu) + c11 * fu;
		return bottom * (1.0D - fv) + top * fv;
	}

	private static int scaleRgb(int color, double factor)
	{
		int red = (int) Math.min(255.0D, ((color >> 16) & 0xFF) * factor);
		int green = (int) Math.min(255.0D, ((color >> 8) & 0xFF) * factor);
		int blue = (int) Math.min(255.0D, (color & 0xFF) * factor);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	// The volume stops at `top` in Y, so the surface sits below the block top as it does in vanilla
	private static double fluidEntry(double ox, double oy, double oz, double dx, double dy, double dz,
			int cx, int cy, int cz, double top, ShapeTracer.Hit hit)
	{
		double minX = cx;
		double maxX = cx + 1;
		double minY = cy;
		double maxY = cy + top;
		double minZ = cz;
		double maxZ = cz + 1;

		double tMin = Double.NEGATIVE_INFINITY;
		double tMax = Double.POSITIVE_INFINITY;
		int enterAxis = -1;

		if (Math.abs(dx) < 1.0E-9D)
		{
			if (ox < minX || ox > maxX)
			{
				return Double.NaN;
			}
		}
		else
		{
			double t1 = (minX - ox) / dx;
			double t2 = (maxX - ox) / dx;

			if (t1 > t2)
			{
				double swap = t1;
				t1 = t2;
				t2 = swap;
			}

			if (t1 > tMin)
			{
				tMin = t1;
				enterAxis = 0;
			}

			tMax = Math.min(tMax, t2);
		}

		if (Math.abs(dy) < 1.0E-9D)
		{
			if (oy < minY || oy > maxY)
			{
				return Double.NaN;
			}
		}
		else
		{
			double t1 = (minY - oy) / dy;
			double t2 = (maxY - oy) / dy;

			if (t1 > t2)
			{
				double swap = t1;
				t1 = t2;
				t2 = swap;
			}

			if (t1 > tMin)
			{
				tMin = t1;
				enterAxis = 1;
			}

			tMax = Math.min(tMax, t2);
		}

		if (Math.abs(dz) < 1.0E-9D)
		{
			if (oz < minZ || oz > maxZ)
			{
				return Double.NaN;
			}
		}
		else
		{
			double t1 = (minZ - oz) / dz;
			double t2 = (maxZ - oz) / dz;

			if (t1 > t2)
			{
				double swap = t1;
				t1 = t2;
				t2 = swap;
			}

			if (t1 > tMin)
			{
				tMin = t1;
				enterAxis = 2;
			}

			tMax = Math.min(tMax, t2);
		}

		if (tMax < tMin || tMax < 0.0D)
		{
			return Double.NaN;
		}

		hit.face = switch (enterAxis)
		{
			case 0 -> dx > 0 ? Face.WEST : Face.EAST;
			case 1 -> dy > 0 ? Face.DOWN : Face.UP;
			case 2 -> dz > 0 ? Face.NORTH : Face.SOUTH;

			default -> dy < 0 ? Face.UP : Face.DOWN;
		};

		return Math.max(tMin, 0.0D) + 1.0E-4D;
	}

	// The full-cube AO sampled at the face centre, so stairs and fences do not look flat beside cubes
	private static int applyQuadAo(Model model, int x, int y, int z, int face, int layerCeiling, int color)
	{
		if (face < 0 || face > 5)
		{
			return color;
		}

		double ao = faceAO(model, x, y, z, face, 0.5D, 0.5D, layerCeiling);
		return ao < 1.0D ? scaleRgb(color, ao) : color;
	}

	private static int composite(double accR, double accG, double accB, double trans, int color)
	{
		if (trans >= 0.999D)
		{
			return color;
		}

		int red = (int) Math.min(255.0D, accR + trans * ((color >> 16) & 0xFF));
		int green = (int) Math.min(255.0D, accG + trans * ((color >> 8) & 0xFF));
		int blue = (int) Math.min(255.0D, accB + trans * (color & 0xFF));
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	private static double frac(double value)
	{
		double fraction = value - Math.floor(value);
		return fraction < 0.0D ? fraction + 1.0D : fraction;
	}

	public static void uploadPending()
	{
		Runnable job = frameRenderJob;

		if (job != null)
		{
			frameRenderJob = null;
			job.run();
		}

		Frame frame = PENDING.poll();

		if (frame == null)
		{
			return;
		}

		rendering = false;

		if (frame.image() != null)
		{
			Minecraft client = Minecraft.getInstance();

			if (viewTexture == null)
			{
				viewId = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "preview");
				viewTexture = new DynamicTexture(() -> "schematicindex-preview", frame.image());
				client.getTextureManager().register(viewId, viewTexture);
			}
			else
			{
				NativeImage previous = viewTexture.getPixels();
				viewTexture.setPixels(frame.image());
				viewTexture.upload();

				// setPixels already closed the previous image, so it must not be recycled as a scratch buffer
				if (previous != null && previous != frame.image())
				{
					previous.close();
				}
			}

			shown = frame.view();
		}

		startNext();
	}

	public static @Nullable Identifier texture(int slot)
	{
		if (shown == null)
		{
			return null;
		}

		return shown.slot() == normalise(slot) ? viewId : null;
	}

	public static int renderWidth()
	{
		return renderWidth;
	}

	public static int renderHeight()
	{
		return renderHeight;
	}

	// A framebuffer's colour texture is bottom-left origin; the CPU NativeImage path is top-left
	public static boolean textureFlippedV()
	{
		return renderer.rendersToTexture();
	}

	// Renders a fresh frame at the shown View. Client thread only; sink gets null when nothing is shown
	// or the render fails, and otherwise runs on the Net pool once the frame is read back and encoded
	public static void capturePng(int slot, Consumer<byte[]> sink)
	{
		View view = shown;
		Model model = view != null && view.slot() == normalise(slot) ? MODELS.get(view.slot()) : null;

		if (model == null)
		{
			sink.accept(null);
			return;
		}

		try
		{
			renderer.renderAsync(model, view, null, image -> encodePng(image, sink));
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Could not capture preview", e);
			sink.accept(null);
		}
	}

	private static void encodePng(@Nullable NativeImage image, Consumer<byte[]> sink)
	{
		if (image == null)
		{
			sink.accept(null);
			return;
		}

		Net.submit(() -> {
			try
			{
				sink.accept(pngBytes(image));
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Could not encode preview screenshot", e);
				sink.accept(null);
			}
			finally
			{
				image.close();
			}
		});
	}

	// NativeImage exposes no PNG-bytes method, so encoding goes through a temp file
	private static byte[] pngBytes(NativeImage image) throws IOException
	{
		Path tmp = Files.createTempFile("schematicindex-capture", ".png");

		try
		{
			image.writeToFile(tmp);
			return Files.readAllBytes(tmp);
		}
		finally
		{
			Files.deleteIfExists(tmp);
		}
	}

	// Never blocks, and completes on whichever pipeline thread finished the work
	public static CompletableFuture<byte[]> renderThumbnail(Path file)
	{
		return renderThumbnail(register(file));
	}

	public static CompletableFuture<byte[]> renderThumbnail(int slot)
	{
		CompletableFuture<byte[]> future = new CompletableFuture<>();
		int index = normalise(slot);

		if (index < 0 || !hasSource(index))
		{
			future.complete(null);
			return future;
		}

		long deadline = System.currentTimeMillis() + THUMBNAIL_TIMEOUT_MS;
		Minecraft.getInstance().execute(() -> awaitThumbnail(index, future, deadline));
		return future;
	}

	private static void awaitThumbnail(int index, CompletableFuture<byte[]> future, long deadline)
	{
		Model model = MODELS.get(index);

		if (model != null)
		{
			View view = new View(index, THUMBNAIL_YAW, THUMBNAIL_PITCH, clampZoom(index, 1.0F), false, false,
					0.0D, 0.0D, 0.0D, 1.0F, THUMBNAIL_FOV);
			Consumer<NativeImage> sink = full -> encodeThumbnail(full, future);

			// awaitThumbnail already runs on the client thread, which a GPU renderer draws from; its
			// fence-deferred readback hands the frame to sink on a later frame
			if (renderer.requiresRenderThread())
			{
				try
				{
					renderer.renderAsync(model, view, null, sink);
				}
				catch (Exception e)
				{
					SchematicIndexMod.LOGGER.warn("Could not render thumbnail", e);
					future.complete(null);
				}

				return;
			}

			Net.submit(() -> {
				try
				{
					renderer.renderAsync(model, view, null, sink);
				}
				catch (Throwable e)
				{
					SchematicIndexMod.LOGGER.warn("Could not render thumbnail", e);
					future.complete(null);
				}
			});
			return;
		}

		if (FAILED.containsKey(index))
		{
			future.complete(null);
			return;
		}

		if (System.currentTimeMillis() > deadline)
		{
			future.complete(null);
			return;
		}

		if (!LOADING.contains(index))
		{
			load(index);
		}

		Minecraft.getInstance().execute(() -> awaitThumbnail(index, future, deadline));
	}

	// Owns full; the downscale and encode run on the Net pool whichever thread delivered the frame
	private static void encodeThumbnail(@Nullable NativeImage full, CompletableFuture<byte[]> future)
	{
		if (full == null)
		{
			future.complete(null);
			return;
		}

		Net.submit(() -> {
			NativeImage thumb = null;

			try
			{
				thumb = downscale(full, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
				future.complete(pngBytes(thumb));
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Could not render thumbnail", e);
				future.complete(null);
			}
			finally
			{
				full.close();

				if (thumb != null)
				{
					thumb.close();
				}
			}
		});
	}

	// Box-averaged rather than nearest-neighbour, so thumbnails are smooth instead of aliased
	private static NativeImage downscale(NativeImage source, int outWidth, int outHeight)
	{
		NativeImage out = new NativeImage(source.format(), outWidth, outHeight, false);
		int inWidth = source.getWidth();
		int inHeight = source.getHeight();

		for (int y = 0; y < outHeight; y++)
		{
			int sy0 = y * inHeight / outHeight;
			int sy1 = Math.max(sy0 + 1, (y + 1) * inHeight / outHeight);

			for (int x = 0; x < outWidth; x++)
			{
				int sx0 = x * inWidth / outWidth;
				int sx1 = Math.max(sx0 + 1, (x + 1) * inWidth / outWidth);

				long a = 0L;
				long b = 0L;
				long c = 0L;
				long d = 0L;
				int count = 0;

				for (int sy = sy0; sy < sy1; sy++)
				{
					for (int sx = sx0; sx < sx1; sx++)
					{
						int pixel = source.getPixel(sx, sy);
						a += (pixel >> 24) & 0xFF;
						b += (pixel >> 16) & 0xFF;
						c += (pixel >> 8) & 0xFF;
						d += pixel & 0xFF;
						count++;
					}
				}

				int averaged = (int) ((a / count) << 24) | (int) ((b / count) << 16)
						| (int) ((c / count) << 8) | (int) (d / count);
				out.setPixel(x, y, averaged);
			}
		}

		return out;
	}

	public static void clearCache()
	{
		MODELS.clear();
		MODEL_ORDER.clear();
		LOADING.clear();
		FAILED.clear();
		lastSlot = Integer.MIN_VALUE;
		STAGE.set("");

		// Without dropping these, texture() would flash a frame for an evicted model
		shown = null;
		queued = null;

		clearDiskCache();
		BlockTextures.clear();
	}

	// Live URL_FILES paths are left pointing at now-missing files; ensureLocal re-downloads on demand
	public static void clearDiskCache()
	{
		Path directory = schcacheDir();

		if (!Files.isDirectory(directory))
		{
			return;
		}

		try (java.util.stream.Stream<Path> stream = Files.list(directory))
		{
			stream.filter(Files::isRegularFile).forEach(path -> {
				try
				{
					Files.deleteIfExists(path);
				}
				catch (Exception ignored)
				{
				}
			});
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not clear schematic cache", e);
		}
	}

	public static void releaseAll()
	{
		if (viewId != null)
		{
			Minecraft.getInstance().getTextureManager().release(viewId);
		}

		if (scratch != null)
		{
			scratch.close();
			scratch = null;
		}

		viewId = null;
		viewTexture = null;
		shown = null;
		queued = null;
		rendering = false;
		lastSlot = Integer.MIN_VALUE;

		// registerModel checks LOADING membership, so clearing it makes a late parse result a no-op
		LOADING.clear();
		STAGE.set("");

		Frame pending;

		while ((pending = PENDING.poll()) != null)
		{
			if (pending.image() != null)
			{
				pending.image().close();
			}
		}
	}
}

package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Net;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public final class ImageStore
{
	// Sized above the on-screen rects at common GUI scales so the GPU's nearest sampling never
	// upscales them into a grainy blur
	private static final int TARGET_WIDTH = 1024;
	private static final int TARGET_HEIGHT = 576;
	private static final int THUMB_WIDTH = 384;
	private static final int THUMB_HEIGHT = 216;
	private static final int AVATAR_SIZE = 64;
	// One budget per image class, so a post's 2.4 MB gallery frames cannot evict the grid's thumbnails
	// behind it. The entry cap is only a secondary guard on live texture handles
	private static final long MAX_GALLERY_BYTES = 24L * 1024 * 1024;
	private static final long MAX_THUMBNAIL_BYTES = 16L * 1024 * 1024;
	private static final long MAX_AVATAR_BYTES = 1L * 1024 * 1024;
	private static final int MAX_READY_ENTRIES = 160;
	private static final int UPLOADS_PER_FRAME = 2;
	private static final long FAILED_BACKOFF_MS = 30_000L;

	private static final int PICKED_BASE = 1_000_000;
	private static final List<Path> PICKED = new ArrayList<>();

	// Render thread only: get() mutates the access order. Decode threads use PENDING/REQUESTED/FAILED
	private static final Map<String, Identifier> READY = new LinkedHashMap<>(16, 0.75F, true);

	private static final long[] READY_BYTES = new long[ImageClass.values().length];

	private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
	private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
	private static final Queue<Decoded> PENDING = new ConcurrentLinkedQueue<>();

	private static final ExecutorService DECODER = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "schematicindex-image");
		thread.setDaemon(true);
		return thread;
	});

	private static int uploadCounter;

	private record Decoded(String key, NativeImage image)
	{
	}

	@FunctionalInterface
	private interface ImageLoader
	{
		@Nullable
		NativeImage load() throws Exception;
	}

	private ImageStore()
	{
	}

	// A no-op: images are addressed only by picked-file slots and URL refs, so there is nothing to find
	public static void discover()
	{
	}

	public static int register(List<Path> paths)
	{
		int start = PICKED_BASE + PICKED.size();
		PICKED.addAll(paths);
		return start;
	}

	private static @Nullable Path fileFor(int index)
	{
		if (index >= PICKED_BASE)
		{
			int picked = index - PICKED_BASE;
			return picked < PICKED.size() ? PICKED.get(picked) : null;
		}

		return null;
	}

	public static @Nullable Identifier texture(int index)
	{
		return textureAt(index, TARGET_WIDTH, TARGET_HEIGHT, "f:");
	}

	public static @Nullable Identifier thumbnail(int index)
	{
		return textureAt(index, THUMB_WIDTH, THUMB_HEIGHT, "t:");
	}

	private static @Nullable Identifier textureAt(int index, int width, int height, String prefix)
	{
		int slot = index >= PICKED_BASE ? index : -1;
		Path file = fileFor(slot);

		if (slot < 0 || file == null)
		{
			return null;
		}

		return request(prefix + "local:" + slot, () -> decodeStream(Files.newInputStream(file), width, height));
	}

	public static @Nullable Identifier texture(String ref)
	{
		return textureAt(ref, TARGET_WIDTH, TARGET_HEIGHT, "f:");
	}

	public static @Nullable Identifier avatar(String ref)
	{
		if (ref == null || ref.isBlank())
		{
			return null;
		}

		return request("a:" + ref, () -> loadSquare(ref, AVATAR_SIZE));
	}

	public static @Nullable Identifier thumbnail(String ref)
	{
		return textureAt(ref, THUMB_WIDTH, THUMB_HEIGHT, "t:");
	}

	private static @Nullable Identifier textureAt(String ref, int width, int height, String prefix)
	{
		if (ref == null || ref.isBlank())
		{
			return null;
		}

		return request(prefix + ref, () -> loadReference(ref, width, height));
	}

	private static @Nullable Identifier request(String key, ImageLoader loader)
	{
		Identifier ready = READY.get(key);

		if (ready != null)
		{
			return ready;
		}

		Long failedAt = FAILED.get(key);

		if (failedAt != null)
		{
			if (System.currentTimeMillis() - failedAt < FAILED_BACKOFF_MS)
			{
				return null;
			}

			FAILED.remove(key);
		}

		if (REQUESTED.add(key))
		{
			DECODER.execute(() -> {
				try
				{
					NativeImage image = loader.load();

					if (image != null)
					{
						FAILED.remove(key);
						PENDING.add(new Decoded(key, image));
					}
					else
					{
						REQUESTED.remove(key);
						FAILED.put(key, System.currentTimeMillis());
					}
				}
				catch (Exception e)
				{
					REQUESTED.remove(key);
					FAILED.put(key, System.currentTimeMillis());
					// Deliberately not reported as SI-IM01: image loads fail routinely while browsing, and
					// the FAILED backoff retries behind a placeholder rather than opening the modal
					SchematicIndexMod.LOGGER.debug("Could not load image {}", key, e);
				}
			});
		}

		return null;
	}

	private static @Nullable NativeImage loadReference(String ref, int width, int height) throws Exception
	{
		byte[] bytes;

		if (ref.startsWith("http://") || ref.startsWith("https://"))
		{
			bytes = fetchCached(ref);
		}
		else
		{
			bytes = Files.readAllBytes(Path.of(ref));
		}

		return decodeStream(new ByteArrayInputStream(bytes), width, height);
	}

	private static @Nullable NativeImage loadSquare(String ref, int size) throws Exception
	{
		byte[] bytes;

		if (ref.startsWith("http://") || ref.startsWith("https://"))
		{
			bytes = fetch(ref);
		}
		else
		{
			bytes = Files.readAllBytes(Path.of(ref));
		}

		return decodeSquare(new ByteArrayInputStream(bytes), size);
	}

	// Avatars change whenever a player updates their skin, so they never go into the on-disk cache
	private static byte[] fetch(String url) throws Exception
	{
		// The shared Net client bounds a slow host by its connect timeout, instead of hanging until the
		// request timeout and parking the key in backoff
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
		HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());

		if (response.statusCode() >= 400)
		{
			throw new IOException("HTTP " + response.statusCode() + " for " + url);
		}

		return response.body();
	}

	// NativeImage only reads PNG, so the server's JPEG derivatives go through the JDK decoder and are
	// copied into an RGBA image the texture path already expects
	public static NativeImage readImage(InputStream input) throws IOException
	{
		byte[] bytes = input.readAllBytes();

		if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
		{
			return NativeImage.read(bytes);
		}

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));

		if (decoded == null)
		{
			throw new IOException("Unsupported image format");
		}

		int width = decoded.getWidth();
		int height = decoded.getHeight();
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
		int[] row = new int[width];

		for (int y = 0; y < height; y++)
		{
			decoded.getRGB(0, y, width, 1, row, 0, width);

			for (int x = 0; x < width; x++)
			{
				int argb = row[x];
				image.setPixelABGR(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
			}
		}

		return image;
	}

	private static NativeImage decodeSquare(InputStream input, int size) throws IOException
	{
		try (input; NativeImage source = readImage(input))
		{
			NativeImage target = new NativeImage(source.format(), size, size, false);

			try
			{
				int side = Math.min(source.getWidth(), source.getHeight());
				int cropX = (source.getWidth() - side) / 2;
				int cropY = (source.getHeight() - side) / 2;

				source.resizeSubRectTo(cropX, cropY, side, side, target);
				return target;
			}
			catch (Throwable t)
			{
				target.close();
				throw t;
			}
		}
	}

	private static NativeImage decodeStream(InputStream input, int targetWidth, int targetHeight) throws IOException
	{
		try (input; NativeImage source = readImage(input))
		{
			NativeImage target = new NativeImage(source.format(), targetWidth, targetHeight, false);

			try
			{
				int cropWidth = source.getWidth();
				int cropHeight = Math.round(cropWidth * 9.0F / 16.0F);

				if (cropHeight > source.getHeight())
				{
					cropHeight = source.getHeight();
					cropWidth = Math.round(cropHeight * 16.0F / 9.0F);
				}

				int cropX = (source.getWidth() - cropWidth) / 2;
				int cropY = (source.getHeight() - cropHeight) / 2;

				source.resizeSubRectTo(cropX, cropY, cropWidth, cropHeight, target);
				return target;
			}
			catch (Throwable t)
			{
				target.close();
				throw t;
			}
		}
	}

	private static byte[] fetchCached(String url) throws Exception
	{
		Path cache = cacheFile(url);

		if (cache != null && Files.exists(cache))
		{
			return Files.readAllBytes(cache);
		}

		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
		HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());

		if (response.statusCode() >= 400)
		{
			throw new IOException("HTTP " + response.statusCode() + " for " + url);
		}

		byte[] body = response.body();

		if (cache != null)
		{
			try
			{
				Files.createDirectories(cache.getParent());
				Files.write(cache, body);
			}
			catch (IOException e)
			{
				SchematicIndexMod.LOGGER.debug("Could not cache image {}", url, e);
			}
		}

		return body;
	}

	// May block on the network, so callers must run it off the render thread
	public static byte @Nullable [] cachedBytes(String ref) throws Exception
	{
		if (ref == null || ref.isBlank())
		{
			return null;
		}

		if (ref.startsWith("http://") || ref.startsWith("https://"))
		{
			return fetchCached(ref);
		}

		return Files.readAllBytes(Path.of(ref));
	}

	private static @Nullable Path cacheFile(String url)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();

			for (int i = 0; i < 20; i++)
			{
				hex.append(String.format("%02x", hash[i]));
			}

			return cacheDirectory().resolve(hex + ".img");
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static Path cacheDirectory()
	{
		return FabricLoader.getInstance().getGameDir().resolve(SchematicIndexMod.MOD_ID).resolve("imagecache");
	}

	public static void uploadPending()
	{
		Minecraft client = Minecraft.getInstance();

		for (int i = 0; i < UPLOADS_PER_FRAME; i++)
		{
			Decoded decoded = PENDING.poll();

			if (decoded == null)
			{
				return;
			}

			Identifier id = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "cache/" + (uploadCounter++));
			client.getTextureManager().register(id, new DynamicTexture(() -> "schematicindex-image", decoded.image()));
			Identifier previous = READY.put(decoded.key(), id);

			if (previous != null)
			{
				client.getTextureManager().release(previous);
			}
			else
			{
				ImageClass cls = ImageClass.of(decoded.key());
				READY_BYTES[cls.ordinal()] += cls.bytes;
			}

			evictOverBudget(client);
		}
	}

	// READY is access-ordered, so the iterator yields the least recently used entry first; an entry
	// whose own class is within budget is skipped unless the handle cap is what tripped
	private static void evictOverBudget(Minecraft client)
	{
		Iterator<Map.Entry<String, Identifier>> it = READY.entrySet().iterator();

		while (overBudget() && it.hasNext())
		{
			Map.Entry<String, Identifier> eldest = it.next();
			ImageClass cls = ImageClass.of(eldest.getKey());

			if (READY.size() <= MAX_READY_ENTRIES && READY_BYTES[cls.ordinal()] <= cls.budget)
			{
				continue;
			}

			client.getTextureManager().release(eldest.getValue());
			REQUESTED.remove(eldest.getKey());
			READY_BYTES[cls.ordinal()] -= cls.bytes;
			it.remove();
		}
	}

	private static boolean overBudget()
	{
		if (READY.size() > MAX_READY_ENTRIES)
		{
			return true;
		}

		for (ImageClass cls : ImageClass.values())
		{
			if (READY_BYTES[cls.ordinal()] > cls.budget)
			{
				return true;
			}
		}

		return false;
	}

	public static void releaseAll()
	{
		Minecraft client = Minecraft.getInstance();

		for (Identifier id : READY.values())
		{
			client.getTextureManager().release(id);
		}

		READY.clear();
		Arrays.fill(READY_BYTES, 0L);
		REQUESTED.clear();
		FAILED.clear();

		Decoded pending;

		while ((pending = PENDING.poll()) != null)
		{
			pending.image().close();
		}
	}

	public static void clearDiskCache()
	{
		Path directory = cacheDirectory();

		if (!Files.isDirectory(directory))
		{
			return;
		}

		try (Stream<Path> stream = Files.walk(directory))
		{
			stream.sorted(Comparator.reverseOrder()).forEach(path -> {
				try
				{
					Files.deleteIfExists(path);
				}
				catch (IOException ignored)
				{
				}
			});
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.debug("Could not clear image cache", e);
		}
	}

	// Approximate per-texture cost at 4 bytes per pixel, inferred from the key prefix
	private enum ImageClass
	{
		GALLERY((long) TARGET_WIDTH * TARGET_HEIGHT * 4, MAX_GALLERY_BYTES),
		THUMBNAIL((long) THUMB_WIDTH * THUMB_HEIGHT * 4, MAX_THUMBNAIL_BYTES),
		AVATAR((long) AVATAR_SIZE * AVATAR_SIZE * 4, MAX_AVATAR_BYTES);

		final long bytes;
		final long budget;

		ImageClass(long bytes, long budget)
		{
			this.bytes = bytes;
			this.budget = budget;
		}

		static ImageClass of(String key)
		{
			if (key.startsWith("a:"))
			{
				return AVATAR;
			}

			return key.startsWith("t:") ? THUMBNAIL : GALLERY;
		}
	}
}

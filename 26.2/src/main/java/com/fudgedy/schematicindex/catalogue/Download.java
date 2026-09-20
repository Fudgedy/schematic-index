package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class Download
{
	public enum State
	{
		RUNNING,
		DONE,
		FAILED
	}

	public record Progress(State state, float fraction, @Nullable String message)
	{
	}

	private static final Map<String, Progress> BY_POST = new ConcurrentHashMap<>();

	// Four at a time, or a fifty-post collection opens fifty streams at once
	private static final Semaphore TRANSFERS = new Semaphore(4);
	// A terminal entry lingers only long enough for the menu to show it, then progress() evicts it
	private static final Map<String, Long> TERMINAL_SINCE = new ConcurrentHashMap<>();
	private static final long TERMINAL_TTL_MS = 120_000L;

	private Download()
	{
	}

	public static @Nullable Progress progress(String postId)
	{
		Progress current = BY_POST.get(postId);

		if (current != null && current.state() != State.RUNNING)
		{
			Long since = TERMINAL_SINCE.get(postId);

			if (since != null && System.currentTimeMillis() - since > TERMINAL_TTL_MS)
			{
				forget(postId);
				return null;
			}
		}

		return current;
	}

	public static void forget(String postId)
	{
		BY_POST.remove(postId);
		TERMINAL_SINCE.remove(postId);
	}

	public static Path resolveTarget(String fileName)
	{
		return Settings.downloadDirectory().resolve(safeName(fileName));
	}

	public static void start(String postId, String fileName, @Nullable String url, @Nullable Path source)
	{
		start(postId, fileName, url, source, Backend.expectedHash(postId));
	}

	public static void start(String postId, String fileName, @Nullable String url, @Nullable Path source,
			@Nullable String expectedHash)
	{
		Progress current = BY_POST.get(postId);

		if (current != null && current.state() == State.RUNNING)
		{
			return;
		}

		TERMINAL_SINCE.remove(postId);
		BY_POST.put(postId, new Progress(State.RUNNING, 0.0F, null));

		Net.submit(() -> {
			try
			{
				TRANSFERS.acquire();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				BY_POST.put(postId, new Progress(State.FAILED, 0.0F, Errors.DOWNLOAD));
				return;
			}

			Path target = Settings.downloadDirectory().resolve(safeName(fileName));
			// The post id keeps two same-titled posts from writing to the same .part file
			Path temporary = target.resolveSibling(target.getFileName() + "." + partSuffix(postId) + ".part");

			try
			{
				Files.createDirectories(target.getParent());

				try (Source input = open(url, source, postId);
						OutputStream output = Files.newOutputStream(temporary))
						{
					copy(postId, input.stream(), output, input.length());
				}

				if (!hashMatches(temporary, expectedHash, postId))
				{
					Files.deleteIfExists(temporary);
					markTerminal(postId, new Progress(State.FAILED, 0.0F, "hash mismatch"));
					Errors.report(Errors.DOWNLOAD);
					return;
				}

				try
				{
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				}
				catch (IOException moveFailed)
				{
					// The bytes arrived fine, so this is a local write problem rather than a network one
					Files.deleteIfExists(temporary);
					markTerminal(postId, new Progress(State.FAILED, 0.0F, moveFailed.getClass().getSimpleName()));
					Errors.report(Errors.DOWNLOAD_WRITE);
					return;
				}

				markTerminal(postId, new Progress(State.DONE, 1.0F, target.getFileName().toString()));
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Download failed for {}", postId, e);

				try
				{
					Files.deleteIfExists(temporary);
				}
				catch (IOException ignored)
				{
				}

				markTerminal(postId, new Progress(State.FAILED, 0.0F, e.getClass().getSimpleName()));
				Errors.report(Errors.DOWNLOAD);
			}
			finally
			{
				TRANSFERS.release();
			}
		});
	}

	private static String partSuffix(String postId)
	{
		String cleaned = postId == null ? "" : postId.replaceAll("[^A-Za-z0-9_-]", "");
		return cleaned.isBlank() ? "dl" : cleaned;
	}

	// A post with no hash skips verification rather than failing an otherwise-good download
	private static boolean hashMatches(Path file, @Nullable String expectedHash, String postId) throws IOException
	{
		String expected = normalizeHash(expectedHash);

		if (expected == null)
		{
			SchematicIndexMod.LOGGER.debug("No file hash for {}; skipping integrity verification", postId);
			return true;
		}

		String actual = sha256Hex(file);
		boolean ok = actual.equalsIgnoreCase(expected);

		if (!ok)
		{
			SchematicIndexMod.LOGGER.warn("Hash mismatch for {}: expected {} but computed {}", postId, expected, actual);
		}

		return ok;
	}

	private static @Nullable String normalizeHash(@Nullable String hash)
	{
		if (hash == null)
		{
			return null;
		}

		String trimmed = hash.trim();

		if (trimmed.isEmpty())
		{
			return null;
		}

		int colon = trimmed.indexOf(':');

		if (colon >= 0)
		{
			String algorithm = trimmed.substring(0, colon).trim();

			if (algorithm.equalsIgnoreCase("sha256") || algorithm.equalsIgnoreCase("sha-256"))
			{
				trimmed = trimmed.substring(colon + 1).trim();
			}
		}

		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String sha256Hex(Path file) throws IOException
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			try (InputStream in = Files.newInputStream(file))
			{
				byte[] buffer = new byte[16 * 1024];
				int read;

				while ((read = in.read(buffer)) > 0)
				{
					digest.update(buffer, 0, read);
				}
			}

			return toHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e)
		{
			// Guaranteed present on every JVM, but its absence must fail the download, not skip the check
			throw new IOException("SHA-256 is unavailable", e);
		}
	}

	private static String toHex(byte[] bytes)
	{
		StringBuilder builder = new StringBuilder(bytes.length * 2);

		for (byte value : bytes)
		{
			builder.append(Character.forDigit((value >> 4) & 0xF, 16));
			builder.append(Character.forDigit(value & 0xF, 16));
		}

		return builder.toString();
	}

	private static void markTerminal(String postId, Progress progress)
	{
		BY_POST.put(postId, progress);
		TERMINAL_SINCE.put(postId, System.currentTimeMillis());
	}

	// length is the Content-Length or the local file size, and -1 when neither is known
	private record Source(InputStream stream, long length) implements AutoCloseable
	{
		@Override
		public void close() throws IOException
		{
			stream.close();
		}
	}

	private static Source open(@Nullable String url, @Nullable Path source, String postId) throws Exception
	{
		if (url != null && !url.isBlank())
		{
			if (!Backend.isAllowedFileUrl(url))
			{
				throw new IllegalStateException("Refusing to download from an untrusted host: " + url);
			}

			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build();
			HttpResponse<InputStream> response = Net.client().send(request, HttpResponse.BodyHandlers.ofInputStream());

			if (response.statusCode() >= 400)
			{
				response.body().close();
				throw new IllegalStateException("HTTP " + response.statusCode());
			}

			long length = response.headers().firstValueAsLong("content-length").orElse(-1L);
			return new Source(response.body(), length);
		}

		if (source == null)
		{
			throw new IllegalStateException("Nothing to download for " + postId);
		}

		// Sized first, so a file vanishing between the two calls cannot strand an open stream
		long length = Files.size(source);
		return new Source(Files.newInputStream(source), length);
	}

	private static void copy(String postId, InputStream input, OutputStream output, long total) throws Exception
	{
		byte[] buffer = new byte[16 * 1024];
		long written = 0L;
		int read;
		int lastPercent = -1;

		while ((read = input.read(buffer)) > 0)
		{
			output.write(buffer, 0, read);
			written += read;

			float fraction = total > 0
					? Math.min(1.0F, (float) written / total)
					: 1.0F - 1.0F / (1.0F + written / 65_536.0F);
			int percent = (int) (fraction * 100.0F);

			if (percent != lastPercent)
			{
				lastPercent = percent;
				BY_POST.put(postId, new Progress(State.RUNNING, fraction, null));
			}
		}
	}

	public static String safeName(String fileName)
	{
		String cleaned = fileName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();

		if (cleaned.isBlank())
		{
			cleaned = "schematic";
		}

		if (cleaned.length() > 120)
		{
			cleaned = cleaned.substring(0, 120);
		}

		cleaned = deviceSafe(cleaned.endsWith(".litematic") ? cleaned.substring(0, cleaned.length() - 10) : cleaned);
		return cleaned + ".litematic";
	}

	// The title comes from the server, so a name Windows refuses (CON, NUL, COM1, a lone dot, a
	// trailing dot or space) would otherwise fail every download of that post
	public static String deviceSafe(String stem)
	{
		String trimmed = stem.replaceAll("[. ]+$", "");

		if (trimmed.isEmpty() || trimmed.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))
		{
			return "schematic_" + (trimmed.isEmpty() ? "file" : trimmed);
		}

		return trimmed;
	}
}

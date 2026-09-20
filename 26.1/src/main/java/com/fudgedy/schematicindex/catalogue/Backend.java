package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.gui.SchematicPreview;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class Backend
{
	private static final long[] RETRY_BACKOFF_MS = { 500L, 1500L };
	private static final int ETAG_LIMIT = 128;
	private static final int FILE_HASH_LIMIT = 1024;

	// Identity sentinel for a 304: the caller already holds this body and must not treat it as a fresh one
	public static final JsonObject NOT_MODIFIED = new JsonObject();
	public static final String STALE_SESSION = "stale_session";

	// Bounded so a walk through many cursor pages cannot grow it forever
	private static final Map<String, String> ETAG_BY_PATH = new LinkedHashMap<>(32, 0.75F, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest)
		{
			return this.size() > ETAG_LIMIT;
		}
	};

	private Backend()
	{
	}

	public static @Nullable String rememberedEtag(String path)
	{
		synchronized (ETAG_BY_PATH)
		{
			return ETAG_BY_PATH.get(path);
		}
	}

	private static void rememberEtag(String path, @Nullable String etag)
	{
		synchronized (ETAG_BY_PATH)
		{
			if (etag == null || etag.isBlank())
			{
				ETAG_BY_PATH.remove(path);
				return;
			}

			ETAG_BY_PATH.put(path, etag);
		}
	}

	// Nothing contacts the server until the terms are accepted, and revoking consent turns it back off
	public static boolean configured()
	{
		return Settings.hasApiBaseUrl() && Settings.termsAccepted();
	}

	private static String base()
	{
		String url = Settings.apiBaseUrl();
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	// The public share page for a post, which unfurls in Discord unlike a bare image URL
	public static String shareUrl(String postId)
	{
		return base() + "/share/" + encode(postId);
	}

	// Captured as posts are parsed so the download flow can verify fetched bytes; bounded like the etags
	private static final Map<String, String> FILE_HASH_BY_POST = new LinkedHashMap<>(64, 0.75F, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest)
		{
			return this.size() > FILE_HASH_LIMIT;
		}
	};

	private static void rememberHash(@Nullable String postId, @Nullable String fileHash)
	{
		if (postId == null || postId.isBlank())
		{
			return;
		}

		synchronized (FILE_HASH_BY_POST)
		{
			if (fileHash == null || fileHash.isBlank())
			{
				// A post that stopped publishing a hash must not keep the one from an earlier parse
				FILE_HASH_BY_POST.remove(postId);
				return;
			}

			FILE_HASH_BY_POST.put(postId, fileHash);
		}
	}

	public static @Nullable String expectedHash(@Nullable String postId)
	{
		if (postId == null)
		{
			return null;
		}

		synchronized (FILE_HASH_BY_POST)
		{
			return FILE_HASH_BY_POST.get(postId);
		}
	}

	public static @Nullable String baseHost()
	{
		try
		{
			return URI.create(base()).getHost();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	// Files are served from the bare domain while the API lives on api.
	private static final List<String> OFFICIAL_FILE_HOSTS = List.of("schematicindex.com", "api.schematicindex.com");

	private static List<String> allowedFileHosts()
	{
		List<String> hosts = new ArrayList<>(OFFICIAL_FILE_HOSTS);
		String baseHost = baseHost();

		if (baseHost != null && !baseHost.isBlank())
		{
			hosts.add(baseHost);
		}

		String extra = System.getProperty("schematicindex.filehosts", "");

		for (String part : extra.split(","))
		{
			String host = part.trim();

			if (!host.isBlank())
			{
				hosts.add(host);
			}
		}

		return hosts;
	}

	// Stops a tampered catalogue row from pointing the client at an arbitrary server
	public static boolean isAllowedFileUrl(@Nullable String url)
	{
		if (url == null || url.isBlank())
		{
			return false;
		}

		URI uri;

		try
		{
			uri = URI.create(url.trim());
		}
		catch (Exception e)
		{
			return false;
		}

		String scheme = uri.getScheme();
		String host = uri.getHost();

		if (scheme == null || host == null || host.isBlank())
		{
			return false;
		}

		scheme = scheme.toLowerCase();

		// Mirrors the backend's scheme, so a dev server on http works while an https backend refuses it
		String baseScheme;

		try
		{
			String parsed = URI.create(base()).getScheme();
			baseScheme = parsed == null ? "https" : parsed.toLowerCase();
		}
		catch (Exception e)
		{
			baseScheme = "https";
		}

		boolean schemeOk = scheme.equals("https") || (baseScheme.equals("http") && scheme.equals("http"));

		if (!schemeOk)
		{
			return false;
		}

		for (String allowed : allowedFileHosts())
		{
			if (host.equalsIgnoreCase(allowed))
			{
				return true;
			}
		}

		return false;
	}

	public static String encode(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	public static @Nullable JsonObject getJson(String path)
	{
		return get(path, true, false);
	}

	public static @Nullable JsonObject getJsonConditional(String path)
	{
		return get(path, true, true);
	}

	public static @Nullable JsonObject getJsonAnon(String path)
	{
		return get(path, false, false);
	}

	private static @Nullable JsonObject get(String path, boolean sessioned, boolean conditional)
	{
		if (!configured())
		{
			return null;
		}

		boolean recovered = false;

		// A 4xx is authoritative and never retried; a blip or a 5xx is, so one hiccup cannot flip to OFFLINE
		for (int attempt = 0; ; attempt++)
		{
			try
			{
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
						.timeout(Duration.ofSeconds(12))
						.header("Accept-Encoding", "gzip");
				String session = sessioned ? attachSession(builder) : null;
				String etag = conditional ? rememberedEtag(path) : null;

				if (etag != null)
				{
					builder.header("If-None-Match", etag);
				}

				HttpRequest request = builder.GET().build();
				HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
				int status = response.statusCode();

				if (status == 304 && etag != null)
				{
					return NOT_MODIFIED;
				}

				if (status >= 500 && attempt < RETRY_BACKOFF_MS.length)
				{
					backoff(RETRY_BACKOFF_MS[attempt]);
					continue;
				}

				if (!recovered && recoverSession(status, session))
				{
					recovered = true;
					continue;
				}

				if (status >= 400)
				{
					return null;
				}

				if (conditional)
				{
					rememberEtag(path, response.headers().firstValue("ETag").orElse(null));
				}

				return JsonParser.parseString(readBody(response)).getAsJsonObject();
			}
			catch (IOException e)
			{
				if (attempt < RETRY_BACKOFF_MS.length)
				{
					SchematicIndexMod.LOGGER.debug("GET {} attempt {} failed, retrying", path, attempt + 1, e);
					backoff(RETRY_BACKOFF_MS[attempt]);
					continue;
				}

				SchematicIndexMod.LOGGER.debug("GET {} failed", path, e);
				return null;
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.debug("GET {} failed", path, e);
				return null;
			}
		}
	}

	private static void backoff(long millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	public static int postJson(String path, String jsonBody)
	{
		return postForApiResult(path, jsonBody).status();
	}

	// Blocks, so call it off the main thread; game version and language ride along for the presence stats
	public static void heartbeat(String version)
	{
		if (!configured())
		{
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("version", version == null ? "" : version);
		body.addProperty("mcVersion", FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(""));
		body.addProperty("locale", Minecraft.getInstance().getLanguageManager().getSelected());
		int status = postJson("/presence", body.toString());
		SchematicIndexMod.LOGGER.debug("Presence heartbeat -> {}", status);
	}

	public static void likeAsync(String postId, boolean like)
	{
		fireAndForget(like ? "/like" : "/unlike", one("postId", postId));
	}

	public static ApiResult like(String postId, boolean like)
	{
		return postForApiResult(like ? "/like" : "/unlike", one("postId", postId));
	}

	public static void downloadAsync(String postId)
	{
		fireAndForget("/download", one("postId", postId));
	}

	public static void viewAsync(String postId)
	{
		fireAndForget("/view", one("postId", postId));
	}

	public static void postEvent(String kind)
	{
		fireAndForget("/me/events", one("kind", kind));
	}

	// Feature usage the player can opt out of on the terms card or in Settings, unlike quest events
	public static void postUsage(String kind)
	{
		if (!Settings.usageData() || !McAuth.verified())
		{
			return;
		}

		fireAndForget("/me/events", one("kind", kind));
	}

	public static @Nullable JsonObject premium()
	{
		return getJsonAnon("/premium");
	}

	public static void premiumViewAsync(String postId)
	{
		fireAndForget("/premium/" + encode(postId) + "/view", "{}");
	}

	public static void premiumBuyAsync(String postId)
	{
		fireAndForget("/premium/" + encode(postId) + "/buy", "{}");
	}

	public static @Nullable JsonObject ownedPremium()
	{
		return get("/me/premium/owned", true, false);
	}

	public static @Nullable JsonObject modUsers(List<String> uuids)
	{
		JsonArray array = new JsonArray();

		for (String uuid : uuids)
		{
			array.add(uuid);
		}

		JsonObject body = new JsonObject();
		body.add("uuids", array);
		ApiResult result = postForApiResult("/mod-users/check", body.toString());
		return result.ok() ? result.body() : null;
	}

	// Spends shards on a shard listing; the body carries the fresh wallet panel, or a 402 on shortfall
	public static ApiResult buyShardSchematic(String postId)
	{
		return postForApiResult("/me/premium/" + encode(postId) + "/buy", "{}");
	}

	public static ApiResult follow(String postId, String poster)
	{
		JsonObject body = new JsonObject();
		body.addProperty("postId", postId == null ? "" : postId);
		body.addProperty("poster", poster);
		return postForApiResult("/follow", body.toString());
	}

	public static ApiResult unfollow(String poster)
	{
		return postForApiResult("/unfollow", one("poster", poster));
	}

	public static void reportAsync(String postId, String reason, String note)
	{
		JsonObject body = new JsonObject();
		body.addProperty("postId", postId);
		body.addProperty("reason", reason);
		body.addProperty("note", note);
		fireAndForget("/report", body.toString());
	}

	public static boolean report(String postId, String reason, String note)
	{
		if (!configured())
		{
			return false;
		}

		JsonObject body = new JsonObject();
		body.addProperty("postId", postId);
		body.addProperty("reason", reason);
		body.addProperty("note", note);
		int status = postJson("/report", body.toString());
		return status / 100 == 2;
	}

	// value is in half-stars, 1 to 10, and 0 clears the rating; { starAvg, starCount, myStars } on success
	public static ApiResult rate(String postId, int value)
	{
		JsonObject body = new JsonObject();
		body.addProperty("postId", postId);
		body.addProperty("value", value);
		return postForApiResult("/rate", body.toString());
	}

	private static @Nullable JsonObject postJsonForResult(String path, String jsonBody)
	{
		ApiResult result = postForApiResult(path, jsonBody);
		return result.ok() ? result.body() : null;
	}

	// Keeps the body on a 4xx so "error" can disambiguate it; status -1 means the request never reached the server
	public record ApiResult(int status, @Nullable JsonObject body)
	{
		public boolean ok()
		{
			return this.status / 100 == 2;
		}

		public @Nullable String error()
		{
			return Json.stringOf(this.body, "error", null);
		}

		public @Nullable String message()
		{
			return Json.stringOf(this.body, "message", null);
		}

		// A 401 that outlived the session retry: this install no longer holds a verified account
		public boolean unverified()
		{
			return this.status == 401;
		}

		public boolean is(String code)
		{
			return code.equals(this.error());
		}
	}

	// Java's HttpClient neither requests nor undoes compression, so every JSON path has to do both itself
	private static String readBody(HttpResponse<byte[]> response) throws IOException
	{
		byte[] body = response.body();

		if (response.headers().firstValue("Content-Encoding").orElse("").toLowerCase().contains("gzip"))
		{
			try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(body)))
			{
				body = gz.readAllBytes();
			}
		}

		return new String(body, StandardCharsets.UTF_8);
	}

	private static ApiResult postForApiResult(String path, String jsonBody)
	{
		return sendForApiResult("POST", path, jsonBody);
	}

	// A GET whose status the caller needs, where getSessioned would fold a 401 into null
	public static ApiResult getForApiResult(String path)
	{
		return sendForApiResult("GET", path, null);
	}

	// Every verb carries the session the same way. A 401 on a request that carried one means the server
	// dropped that session (another install verifying, a staff token past its day): one handshake, one retry
	public static ApiResult sendForApiResult(String method, String path, @Nullable String jsonBody)
	{
		String session = McAuth.sessionToken();
		ApiResult result = send(method, path, jsonBody, session);

		if (!recoverSession(result.status(), session))
		{
			return result;
		}

		return send(method, path, jsonBody, McAuth.sessionToken());
	}

	private static boolean recoverSession(int status, @Nullable String session)
	{
		if (status != 401 || session == null || session.isBlank())
		{
			return false;
		}

		return McAuth.recover(session);
	}

	// Reads the account behind a stored token before McAuth adopts it, so a dead one cannot start a recovery
	public static ApiResult accountFor(String session)
	{
		return send("GET", "/me/account", null, session);
	}

	// The staff routes refuse a session older than the server allows; only a fresh handshake clears it
	public static boolean isStaleSession(ApiResult result)
	{
		return (result.status() == 401 || result.status() == 403) && STALE_SESSION.equals(result.error());
	}

	// Takes the token explicitly because the caller has already dropped it from McAuth
	public static ApiResult logout(String session)
	{
		return send("POST", "/auth/logout", "{}", session);
	}

	private static ApiResult send(String method, String path, @Nullable String jsonBody, @Nullable String session)
	{
		if (!configured())
		{
			return new ApiResult(-1, null);
		}

		try
		{
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
					.timeout(Duration.ofSeconds(12))
					.header("Accept-Encoding", "gzip");

			if (session != null && !session.isBlank())
			{
				builder.header("X-Session", session);
			}

			if (jsonBody != null)
			{
				builder.header("Content-Type", "application/json");
			}

			HttpRequest request = builder
					.method(method, jsonBody == null
							? HttpRequest.BodyPublishers.noBody()
							: HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
			JsonObject body = null;

			try
			{
				body = JsonParser.parseString(readBody(response)).getAsJsonObject();
			}
			catch (Exception ignored)
			{
			}

			return new ApiResult(response.statusCode(), body);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("{} {} failed", method, path, e);
			return new ApiResult(-1, null);
		}
	}

	// The verified session is the only identity the server knows; without it every write answers 401
	private static @Nullable String attachSession(HttpRequest.Builder builder)
	{
		String session = McAuth.sessionToken();

		if (session == null || session.isBlank())
		{
			return null;
		}

		builder.header("X-Session", session);
		return session;
	}

	public static ApiResult authChallenge()
	{
		return postForApiResult("/auth/challenge", "{}");
	}

	// The username must match the live game session, since the server checks it against hasJoined
	public static ApiResult authVerify(String username, String serverId)
	{
		JsonObject body = new JsonObject();
		body.addProperty("username", username);
		body.addProperty("serverId", serverId);
		return postForApiResult("/auth/verify", body.toString());
	}

	public static @Nullable JsonObject getSessioned(String path)
	{
		return get(path, true, false);
	}

	public static @Nullable JsonObject myAccount()
	{
		return get("/me/account", true, false);
	}

	public static @Nullable JsonObject myShards()
	{
		return get("/me/shards", true, false);
	}

	public static @Nullable JsonObject myCosmetics()
	{
		return get("/me/cosmetics", true, false);
	}

	// Fixes a picked hex into a palette slot; the body carries the new palette and balance, or 402 on shortfall
	public static ApiResult buyColor(int hex)
	{
		JsonObject body = new JsonObject();
		body.addProperty("hex", hex & 0xFFFFFF);
		return postForApiResult("/me/cosmetics/colors/buy", body.toString());
	}

	public static ApiResult refundColor(int id)
	{
		return postForApiResult("/me/cosmetics/colors/" + id + "/refund", "{}");
	}

	// Buys a preset palette by name; the server holds the colour list so a client cannot price its own
	public static ApiResult buyPreset(String name)
	{
		JsonObject body = new JsonObject();
		body.addProperty("preset", name);
		return postForApiResult("/me/cosmetics/presets/buy", body.toString());
	}

	// Publishes the gradient this client wears, as hex the other clients can render without its palette
	public static ApiResult publishLoadout(int[] stops)
	{
		return publishLoadout(stops, Set.of());
	}

	// Effects travel by id; the server refuses one the account does not own with 400 not_owned
	public static ApiResult publishLoadout(int[] stops, Set<String> effects)
	{
		JsonArray array = new JsonArray();

		for (int stop : stops)
		{
			array.add(stop & 0xFFFFFF);
		}

		JsonArray effectArray = new JsonArray();

		for (String effect : effects)
		{
			effectArray.add(effect);
		}

		JsonObject body = new JsonObject();
		body.add("stops", array);
		body.add("effects", effectArray);
		return postForApiResult("/me/cosmetics/loadout", body.toString());
	}

	// Buys a nametag effect; 402 insufficient, 409 already_owned, 404 unknown_effect. Not refundable
	public static ApiResult buyEffect(String effect)
	{
		JsonObject body = new JsonObject();
		body.addProperty("effect", effect);
		return postForApiResult("/me/cosmetics/effects/buy", body.toString());
	}

	public static ApiResult myLibrary()
	{
		return getForApiResult("/me/library");
	}

	// Replaces the account copy when baseUpdatedAt still matches, else 409 stale with the current library
	public static ApiResult putLibrary(String jsonBody)
	{
		return sendForApiResult("PUT", "/me/library", jsonBody);
	}

	public static @Nullable JsonObject myTags()
	{
		return get("/me/tags", true, false);
	}

	// Buys a purchasable tag; the server prices it from its own row, so only the id travels
	public static ApiResult buyTag(int id)
	{
		return postForApiResult("/me/tags/" + id + "/buy", "{}");
	}

	public static ApiResult equipTag(int id)
	{
		JsonObject body = new JsonObject();

		if (id >= 0)
		{
			body.addProperty("id", id);
		}

		return postForApiResult("/me/tags/equip", body.toString());
	}

	public static @Nullable JsonObject claimDaily()
	{
		ApiResult result = postForApiResult("/me/daily/claim", "{}");
		return result.ok() ? result.body() : null;
	}

	// The server credits the welcome shards here rather than on verify
	public static ApiResult claimWelcome()
	{
		return postForApiResult("/me/welcome/claim", "{}");
	}

	// The invite card; the server mints this account's code on the first read
	public static @Nullable JsonObject referral()
	{
		return get("/me/referral", true, false);
	}

	// 404 unknown_code, 400 own_code, 409 already_redeemed, 403 too_old / streak_required / inviter_streak,
	// 429 rate_limited; both sides are paid on success
	public static ApiResult redeemReferral(String code)
	{
		return postForApiResult("/me/referral/redeem", one("code", code));
	}

	public static @Nullable JsonObject startQuest(String questId)
	{
		ApiResult result = postForApiResult("/me/quests/" + encode(questId) + "/start", "{}");
		return result.ok() ? result.body() : null;
	}

	public static @Nullable JsonObject claimQuest(String questId)
	{
		ApiResult result = postForApiResult("/me/quests/" + encode(questId) + "/claim", "{}");
		return result.ok() ? result.body() : null;
	}

	// 201 created, 401/403 not_verified, 409 duplicate_pending or already_credited_to_you, 429 rate_limited
	public static ApiResult submitClaim(String postId, String note)
	{
		return postForApiResult("/post/" + encode(postId) + "/claim", one("note", note == null ? "" : note));
	}

	public static @Nullable JsonObject myClaims()
	{
		return get("/me/claims", true, false);
	}

	private static void fireAndForget(String path, String jsonBody)
	{
		if (!configured())
		{
			return;
		}

		Net.submit(() -> postJson(path, jsonBody));
	}

	private static String one(String key, String value)
	{
		JsonObject o = new JsonObject();
		o.addProperty(key, value);
		return o.toString();
	}

	// Every server-supplied url funnels through here, so a tampered row cannot steer the client elsewhere
	public static boolean download(String url, Path target)
	{
		if (!configured())
		{
			return false;
		}

		if (!isAllowedFileUrl(url))
		{
			SchematicIndexMod.LOGGER.warn("Refusing to download from an untrusted host: {}", url);
			return false;
		}

		return fetchToFile(url, target);
	}

	// For a link the user pasted themselves: any public https host, since the choice was theirs
	public static boolean downloadUserLink(String url, Path target)
	{
		if (!configured() || url == null || !url.trim().toLowerCase().startsWith("https://"))
		{
			return false;
		}

		return isPublicHost(url) && fetchToFile(url, target);
	}

	// A pasted link must not turn the client into a probe of its own machine or LAN
	private static boolean isPublicHost(String url)
	{
		try
		{
			String host = URI.create(url.trim()).getHost();

			if (host == null || host.isBlank())
			{
				return false;
			}

			for (InetAddress address : InetAddress.getAllByName(host))
			{
				if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
						|| address.isAnyLocalAddress() || address.isMulticastAddress())
				{
					return false;
				}
			}

			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	private static boolean fetchToFile(String url, Path target)
	{
		try
		{
			Files.createDirectories(target.getParent());
			Path temporary = target.resolveSibling(target.getFileName() + ".part");
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(60))
					.header("User-Agent", "SchematicIndex")
					.GET()
					.build();
			HttpResponse<Path> response = Net.client().send(request, HttpResponse.BodyHandlers.ofFile(temporary));

			if (response.statusCode() >= 400)
			{
				Files.deleteIfExists(temporary);
				return false;
			}

			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			return true;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("download {} failed", url, e);
			return false;
		}
	}

	// A bought shard schematic streams from a session-gated route, so it needs the account's headers
	public static boolean downloadShardFile(String postId, Path target)
	{
		if (!configured())
		{
			return false;
		}

		try
		{
			Files.createDirectories(target.getParent());
			Path temporary = target.resolveSibling(target.getFileName() + ".part");
			boolean recovered = false;

			while (true)
			{
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + "/me/premium/" + encode(postId) + "/file"))
						.timeout(Duration.ofSeconds(60))
						.header("User-Agent", "SchematicIndex");
				String session = attachSession(builder);
				HttpResponse<Path> response = Net.client().send(builder.GET().build(), HttpResponse.BodyHandlers.ofFile(temporary));
				int status = response.statusCode();

				if (!recovered && recoverSession(status, session))
				{
					recovered = true;
					continue;
				}

				if (status >= 400)
				{
					Files.deleteIfExists(temporary);
					return false;
				}

				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				return true;
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("shard download {} failed", postId, e);
			return false;
		}
	}

	public static @Nullable JsonObject myStats(String code, int days)
	{
		if (!configured())
		{
			return null;
		}

		try
		{
			HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/me/stats?days=" + days))
					.timeout(Duration.ofSeconds(12))
					.header("Accept-Encoding", "gzip")
					.header("X-Upload-Code", code)
					.GET()
					.build();
			HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() >= 400)
			{
				return null;
			}

			return JsonParser.parseString(readBody(response)).getAsJsonObject();
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("me/stats failed", e);
			return null;
		}
	}

	public static @Nullable JsonObject uploaderInfo(String code)
	{
		if (!configured())
		{
			return null;
		}

		try
		{
			HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/uploader"))
					.timeout(Duration.ofSeconds(10))
					.header("Accept-Encoding", "gzip")
					.header("X-Upload-Code", code)
					.GET()
					.build();
			HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() >= 400)
			{
				return null;
			}

			JsonObject body = JsonParser.parseString(readBody(response)).getAsJsonObject();
			return Json.boolOf(body, "valid", false) ? body : null;
		}
		catch (Exception e)
		{
			return null;
		}
	}

	public static @Nullable String shareCollection(String name, List<String> postIds)
	{
		if (!configured())
		{
			return null;
		}

		JsonArray ids = new JsonArray();

		for (String id : postIds)
		{
			ids.add(id);
		}

		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.add("postIds", ids);
		JsonObject result = postJsonForResult("/collections/share", body.toString());
		return Json.stringOf(result, "code", null);
	}

	public static @Nullable JsonObject loadCollectionCode(String code)
	{
		JsonObject data = getJson("/collections/" + encode(code));

		if (data == null || !data.has("postIds") || !data.get("postIds").isJsonArray())
		{
			return null;
		}

		return data;
	}

	// Either identity alone is enough, so a verified-but-not-uploader user still receives claim outcomes;
	// the first call per launch pulls the whole inbox; later ones send ?since= and merge into it
	public static @Nullable JsonObject notifications(@Nullable String code)
	{
		if (!configured())
		{
			return null;
		}

		String key = code == null ? "" : code;
		long since;

		synchronized (NOTIFICATIONS_HELD)
		{
			since = key.equals(notificationsHeldFor) ? Math.max(notificationsNewestAt, Settings.notificationsSeenAt()) : 0L;
		}

		try
		{
			String path = "/me/notifications" + (since > 0L ? "?since=" + since : "");
			boolean recovered = false;

			while (true)
			{
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
						.timeout(Duration.ofSeconds(10))
						.header("Accept-Encoding", "gzip");
				String session = attachSession(builder);

				if (!key.isBlank())
				{
					builder.header("X-Upload-Code", key);
				}

				HttpRequest request = builder.GET().build();
				HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
				int status = response.statusCode();

				if (!recovered && recoverSession(status, session))
				{
					recovered = true;
					continue;
				}

				if (status >= 400)
				{
					return null;
				}

				JsonObject body = JsonParser.parseString(readBody(response)).getAsJsonObject();
				return mergeNotifications(body, key, since > 0L);
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("me/notifications failed", e);
			return null;
		}
	}

	private static final int NOTIFICATIONS_LIMIT = 200;
	private static final List<JsonObject> NOTIFICATIONS_HELD = new ArrayList<>();
	private static String notificationsHeldFor;
	private static long notificationsNewestAt;

	private static JsonObject mergeNotifications(JsonObject body, String key, boolean partial)
	{
		if (!body.has("items") || !body.get("items").isJsonArray())
		{
			return body;
		}

		synchronized (NOTIFICATIONS_HELD)
		{
			if (!partial)
			{
				NOTIFICATIONS_HELD.clear();
				notificationsNewestAt = 0L;
			}

			for (JsonElement element : body.getAsJsonArray("items"))
			{
				if (element == null || !element.isJsonObject() || NOTIFICATIONS_HELD.contains(element.getAsJsonObject()))
				{
					continue;
				}

				JsonObject item = element.getAsJsonObject();
				NOTIFICATIONS_HELD.add(item);
				notificationsNewestAt = Math.max(notificationsNewestAt, longOf(item, "at"));
			}

			NOTIFICATIONS_HELD.sort((a, b) -> Long.compare(longOf(b, "at"), longOf(a, "at")));

			while (NOTIFICATIONS_HELD.size() > NOTIFICATIONS_LIMIT)
			{
				NOTIFICATIONS_HELD.remove(NOTIFICATIONS_HELD.size() - 1);
			}

			notificationsHeldFor = key;
			JsonArray items = new JsonArray();

			for (JsonObject item : NOTIFICATIONS_HELD)
			{
				items.add(item);
			}

			JsonObject merged = body.deepCopy();
			merged.add("items", items);
			return merged;
		}
	}

	// value is in half-stars, matching rate()
	public record StarredPost(String postId, int value)
	{
	}

	// Every list is non-null, empty when the server omitted the field, so re-hydration never null-checks
	public record AccountState(List<String> likedPostIds, List<StarredPost> starredPosts,
			List<String> followedPosters)
	{
	}

	// Field names are read leniently, so a server-side rename cannot silently drop a category of state
	public static @Nullable AccountState myState()
	{
		JsonObject body = getJson("/me/state");

		if (body == null)
		{
			return null;
		}

		List<String> liked = strArray(body, "liked", "likes", "likedPostIds");
		List<String> follows = strArray(body, "follows", "following", "followedPosters");
		List<StarredPost> starred = new ArrayList<>();
		JsonArray stars = firstArray(body, "stars", "starred", "starredPosts");

		if (stars != null)
		{
			for (JsonElement element : stars)
			{
				if (element == null || !element.isJsonObject())
				{
					continue;
				}

				JsonObject row = element.getAsJsonObject();
				String postId = str(row, "postId");

				if (postId == null || postId.isBlank())
				{
					postId = str(row, "id");
				}

				if (postId == null || postId.isBlank())
				{
					continue;
				}

				// "value" is what rate() posts, "myStars" what parsePost reads
				int value = row.has("value") ? intOf(row, "value") : intOf(row, "myStars");
				starred.add(new StarredPost(postId, value));
			}
		}

		return new AccountState(List.copyOf(liked), List.copyOf(starred), List.copyOf(follows));
	}

	private static @Nullable JsonArray firstArray(JsonObject o, String... keys)
	{
		for (String key : keys)
		{
			if (o.has(key) && o.get(key).isJsonArray())
			{
				return o.getAsJsonArray(key);
			}
		}

		return null;
	}

	private static List<String> strArray(JsonObject o, String... keys)
	{
		JsonArray array = firstArray(o, keys);

		if (array == null)
		{
			return List.of();
		}

		List<String> values = new ArrayList<>();

		for (JsonElement element : array)
		{
			String value = Json.asString(element, null);

			if (value != null && !value.isBlank())
			{
				values.add(value.trim());
			}
		}

		return values;
	}

	public static boolean isDownloadStale(@Nullable SchematicEntry entry)
	{
		if (entry == null || entry.title() == null)
		{
			return false;
		}

		Path local = Download.resolveTarget(entry.title() + ".litematic");

		if (!Files.exists(local))
		{
			return false;
		}

		String expected = normalizeHash(entry.fileHash());

		if (expected == null)
		{
			expected = normalizeHash(expectedHash(entry.id()));
		}

		if (expected == null)
		{
			return false;
		}

		String actual = sha256Hex(local);
		return actual != null && !actual.equalsIgnoreCase(expected);
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

	// Streamed, so a large schematic is never buffered whole just to be hashed
	private static @Nullable String sha256Hex(Path file)
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

			byte[] bytes = digest.digest();
			StringBuilder builder = new StringBuilder(bytes.length * 2);

			for (byte value : bytes)
			{
				builder.append(Character.forDigit((value >> 4) & 0xF, 16));
				builder.append(Character.forDigit(value & 0xF, 16));
			}

			return builder.toString();
		}
		catch (NoSuchAlgorithmException | IOException e)
		{
			SchematicIndexMod.LOGGER.debug("Could not hash {} for staleness check", file, e);
			return null;
		}
	}

	public static ApiResult editPost(String code, String postId, String title, String thumbnailName, String designer,
			String description, String category)
	{
		JsonObject body = new JsonObject();
		body.addProperty("title", title);
		body.addProperty("thumbnailName", thumbnailName);
		body.addProperty("designer", designer);
		body.addProperty("description", description);
		body.addProperty("category", category);
		return postWithCode("/me/posts/" + encode(postId) + "/edit", code, body.toString());
	}

	public static ApiResult unpublishPost(String code, String postId)
	{
		return postWithCode("/me/posts/" + encode(postId) + "/unpublish", code, "{}");
	}

	private static ApiResult postWithCode(String path, String code, String jsonBody)
	{
		if (!configured())
		{
			return new ApiResult(-1, null);
		}

		try
		{
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
					.timeout(Duration.ofSeconds(12))
					.header("Content-Type", "application/json")
					.header("Accept-Encoding", "gzip")
					.header("X-Upload-Code", code);
			attachSession(builder);
			HttpRequest request = builder
					.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> response = Net.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
			JsonObject body = null;

			try
			{
				body = JsonParser.parseString(readBody(response)).getAsJsonObject();
			}
			catch (Exception ignored)
			{
			}

			return new ApiResult(response.statusCode(), body);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("POST {} failed", path, e);
			return new ApiResult(-1, null);
		}
	}

	public record UploadResult(int status, @Nullable String message)
	{
	}

	private static volatile double uploadFraction;

	public static double uploadFraction()
	{
		return uploadFraction;
	}

	public static UploadResult upload(String code, String metaJson, Path schematic, List<Path> images)
	{
		if (!configured())
		{
			return new UploadResult(-1, null);
		}

		try
		{
			uploadFraction = 0.0;
			String boundary = "----schematicindex" + System.nanoTime();

			// File parts stream from disk at send time, so no upload is ever copied whole into memory
			List<BodyPart> parts = multipartParts(boundary, metaJson, schematic, images);
			long total = 0;

			for (BodyPart part : parts)
			{
				total += part.length();
			}

			final long bodyLength = total;
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + "/upload"))
					.timeout(Duration.ofMinutes(10))
					.header("X-Upload-Code", code)
					.header("Content-Type", "multipart/form-data; boundary=" + boundary);
			attachSession(builder);
			HttpRequest request = builder
					.POST(HttpRequest.BodyPublishers.ofInputStream(() -> {
						// Fresh streams per subscription, so a retry or redirect re-sends from the beginning
						try
						{
							return new CountingStream(openBody(parts), bodyLength);
						}
						catch (IOException e)
						{
							throw new UncheckedIOException(e);
						}
					}))
					.build();
			HttpResponse<String> response = Net.client().send(request, HttpResponse.BodyHandlers.ofString());
			uploadFraction = 1.0;
			return new UploadResult(response.statusCode(), messageOf(response.body()));
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Upload failed", e);
			return new UploadResult(-1, null);
		}
	}

	private static final class CountingStream extends FilterInputStream
	{
		private final long total;
		private long count;

		CountingStream(InputStream in, long total)
		{
			super(in);
			this.total = total;
		}

		@Override
		public int read() throws IOException
		{
			int value = super.read();

			if (value >= 0)
			{
				count++;
				update();
			}

			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException
		{
			int read = super.read(buffer, offset, length);

			if (read > 0)
			{
				count += read;
				update();
			}

			return read;
		}

		private void update()
		{
			uploadFraction = total > 0 ? Math.min(1.0, (double) count / total) : 0.0;
		}
	}

	private static @Nullable String messageOf(String body)
	{
		try
		{
			JsonObject object = JsonParser.parseString(body).getAsJsonObject();
			return Json.stringOf(object, "message", null);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	// Exactly one of an in-memory chunk or a file to stream. Files stay paths, never bytes
	private record BodyPart(byte[] bytes, Path file)
	{
		static BodyPart of(String text)
		{
			return new BodyPart(text.getBytes(StandardCharsets.UTF_8), null);
		}

		static BodyPart of(Path file)
		{
			return new BodyPart(null, file);
		}

		long length() throws IOException
		{
			return this.bytes != null ? this.bytes.length : Files.size(this.file);
		}

		InputStream open() throws IOException
		{
			return this.bytes != null ? new ByteArrayInputStream(this.bytes) : Files.newInputStream(this.file);
		}
	}

	private static List<BodyPart> multipartParts(String boundary, String metaJson, Path schematic, List<Path> images)
	{
		List<BodyPart> parts = new ArrayList<>();
		String crlf = "\r\n";

		parts.add(BodyPart.of("--" + boundary + crlf
				+ "Content-Disposition: form-data; name=\"meta\"" + crlf + crlf
				+ metaJson + crlf));

		parts.add(BodyPart.of(filePartHeader(boundary, "schematic", schematic.getFileName().toString(),
				"application/octet-stream")));
		parts.add(BodyPart.of(schematic));
		parts.add(BodyPart.of(crlf));

		for (Path image : images)
		{
			String name = image.getFileName().toString().toLowerCase();
			String type = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg" : "image/png";
			parts.add(BodyPart.of(filePartHeader(boundary, "images", image.getFileName().toString(), type)));
			parts.add(BodyPart.of(image));
			parts.add(BodyPart.of(crlf));
		}

		parts.add(BodyPart.of("--" + boundary + "--" + crlf));
		return parts;
	}

	private static String filePartHeader(String boundary, String field, String filename, String contentType)
	{
		return "--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
				+ "Content-Type: " + contentType + "\r\n\r\n";
	}

	// Opened up front, so a file that cannot be opened fails the send instead of truncating the body
	private static InputStream openBody(List<BodyPart> parts) throws IOException
	{
		List<InputStream> streams = new ArrayList<>(parts.size());

		try
		{
			for (BodyPart part : parts)
			{
				streams.add(part.open());
			}
		}
		catch (IOException e)
		{
			for (InputStream stream : streams)
			{
				try
				{
					stream.close();
				}
				catch (IOException ignored)
				{
				}
			}

			throw e;
		}

		return new SequenceInputStream(Collections.enumeration(streams));
	}

	public static SchematicEntry parsePost(JsonObject o)
	{
		JsonObject size = Json.objectOf(o, "size");
		List<String> imageUrls = stringList(o, "imageUrls");
		List<String> originalUrls = stringList(o, "originalUrls");

		String fileUrl = str(o, "fileUrl");
		int slot = fileUrl != null ? SchematicPreview.registerUrl(fileUrl) : -1;

		String id = str(o, "id");
		String fileHash = str(o, "fileHash");
		rememberHash(id, fileHash);

		return new SchematicEntry(
				id, str(o, "title"), str(o, "thumbnailName"), str(o, "poster"), str(o, "designer"),
				Category.fromName(str(o, "category")),
				intOf(size, "x"), intOf(size, "y"), intOf(size, "z"),
				intOf(o, "blockCount"), intOf(o, "downloads"), intOf(o, "likes"), longOf(o, "postedAt"),
				str(o, "description"), imageUrls.size(), 0, slot, false,
				str(o, "thumbnailUrl"), imageUrls, originalUrls, fileUrl, fileHash, longOf(o, "fileSize"),
				boolOf(o, "liked"), doubleOf(o, "trendScore"), intOf(o, "views"),
				doubleOf(o, "starAvg"), intOf(o, "starCount"), intOf(o, "myStars"),
				parseMaterials(o), o.has("materials") && o.get("materials").isJsonArray(),
				parseStops(o, "posterStops"));
	}

	public static int[] parseStops(JsonObject o, String key)
	{
		JsonArray raw = Json.arrayOf(o, key);
		int[] stops = new int[raw.size()];
		int count = 0;

		for (JsonElement element : raw)
		{
			int stop = parseStop(element);

			if (stop >= 0)
			{
				stops[count++] = stop;
			}
		}

		return count == 0 ? SchematicEntry.NO_STOPS : Arrays.copyOf(stops, count);
	}

	private static int parseStop(JsonElement element)
	{
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
		{
			return Json.asInt(element, -1) & 0xFFFFFF;
		}

		String text = Json.asString(element, "").trim();

		if (text.startsWith("#"))
		{
			text = text.substring(1);
		}

		try
		{
			return (int) Long.parseLong(text, 16) & 0xFFFFFF;
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	public static @Nullable SchematicEntry post(String postId)
	{
		JsonObject body = getJson("/post/" + encode(postId));

		if (body == null)
		{
			return null;
		}

		JsonObject post = body.has("post") && body.get("post").isJsonObject() ? body.getAsJsonObject("post") : body;
		return post.has("id") ? parsePost(post) : null;
	}

	private static List<String> stringList(JsonObject o, String field)
	{
		if (!o.has(field) || !o.get(field).isJsonArray())
		{
			return List.of();
		}

		List<String> values = new ArrayList<>();

		for (JsonElement element : o.getAsJsonArray(field))
		{
			values.add(Json.asString(element, ""));
		}

		return values;
	}

	// The server already sorts these by count descending
	private static List<SchematicEntry.Material> parseMaterials(JsonObject o)
	{
		if (o == null || !o.has("materials") || !o.get("materials").isJsonArray())
		{
			return List.of();
		}

		List<SchematicEntry.Material> materials = new ArrayList<>();

		for (JsonElement element : o.getAsJsonArray("materials"))
		{
			if (element == null || !element.isJsonObject())
			{
				continue;
			}

			JsonObject row = element.getAsJsonObject();
			String name = str(row, "name");

			if (name == null || name.isBlank())
			{
				continue;
			}

			materials.add(new SchematicEntry.Material(name, intOf(row, "count")));
		}

		return List.copyOf(materials);
	}

	public static NewsFeed.Entry parseNews(JsonObject o)
	{
		List<String> lines = new ArrayList<>();

		if (o.has("lines") && o.get("lines").isJsonArray())
		{
			for (JsonElement element : o.getAsJsonArray("lines"))
			{
				lines.add(Json.asString(element, ""));
			}
		}

		return new NewsFeed.Entry(str(o, "badge"), str(o, "title"), str(o, "when"), lines, boolOf(o, "highlight"));
	}

	private static @Nullable String str(JsonObject o, String key)
	{
		return Json.stringOf(o, key, null);
	}

	private static int intOf(@Nullable JsonObject o, String key)
	{
		return Json.intOf(o, key, 0);
	}

	private static long longOf(JsonObject o, String key)
	{
		return Json.longOf(o, key, 0L);
	}

	private static double doubleOf(JsonObject o, String key)
	{
		return Json.doubleOf(o, key, 0.0);
	}

	private static boolean boolOf(JsonObject o, String key)
	{
		return Json.boolOf(o, key, false);
	}
}

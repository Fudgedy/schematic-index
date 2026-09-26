package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.CosmeticColors;
import com.fudgedy.schematicindex.catalogue.CosmeticTags;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.McAuth;
import com.fudgedy.schematicindex.catalogue.Net;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

// Badges other mod users' nametags with a bitmap-font glyph, so it rides the name Component instead of a bespoke draw
public final class ModTags
{
	public static final FontDescription FONT = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "nametag"));
	// Plane 16 private use, clear of the BMP glyphs server packs put in the default font. The font sits only on
	// this leaf: on the root, every character of the appended name would render as a missing-glyph box. The
	// second glyph is a 2px space in the same font, tucking the badge nearer the name
	private static final int FIRST_GLYPH = 0x10FF00;
	private static final int LAST_GLYPH = 0x10FF02;
	private static final String ICON_GLYPHS = "\uDBFF\uDF00\uDBFF\uDF01";
	private static final Component ICON = Component.literal(ICON_GLYPHS).withStyle(Style.EMPTY.withFont(FONT));
	// A zero-width glyph opens every decorated name, so a second pass over the same text can be told apart
	// from fresh text whatever the badge setting is
	private static final String MARKER = "\uDBFF\uDF02";
	private static final Component MARK = Component.literal(MARKER).withStyle(Style.EMPTY.withFont(FONT));
	private static final int LOGGED_FONTS_LIMIT = 64;
	private static final int INTERVAL_SECONDS = 5;
	private static final int FIRST_DELAY_SECONDS = 2;
	private static final int DECORATED_CACHE_LIMIT = 256;
	// Ceilings on what one peers response can allocate, so an oversized or repeated field stays bounded
	private static final int MAX_PEERS = 256;
	private static final int MAX_STOPS = 16;
	private static final int MAX_EFFECTS = 16;
	private static final char WALL = '\u0000';
	// Hostname labels players prepend to the same server, so "play.x.net" and "x.net" share one room
	private static final List<String> ALIAS_PREFIXES = List.of("play.", "mc.", "www.", "join.");
	private static final String DEFAULT_PORT = ":25565";

	private static volatile Set<UUID> modUsers = Set.of();
	private static volatile Map<UUID, Cosmetics.Worn> worn = Map.of();
	private static boolean started;
	private static boolean fedBackLogged;
	private static final PollGate GATE = new PollGate();
	private static final VerifyGate VERIFY = new VerifyGate();
	// The room version the last answer carried; the server answers 204 while it still matches
	private static long roomVersion;

	// Render-thread only: rebuilding a per-glyph gradient every frame for every visible player adds up
	private static final Map<DecorateKey, Component> DECORATED = new HashMap<>();
	private static Map<UUID, Cosmetics.Worn> decoratedFor = worn;
	private static final Set<FontDescription> LOGGED_FONTS = new HashSet<>();
	// A text_display reads its text only when the server changes it, so it rebuilds when this moves
	private static final AtomicInteger GENERATION = new AtomicInteger();
	private static int localLoadout;

	private ModTags()
	{
	}

	public static boolean isModUser(UUID id)
	{
		return Settings.modTags() && modUsers.contains(id);
	}

	public static int generation()
	{
		return GENERATION.get();
	}

	public static boolean owns(int codepoint)
	{
		return codepoint >= FIRST_GLYPH && codepoint <= LAST_GLYPH;
	}

	public static boolean isLocalPreview(UUID id)
	{
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getUUID().equals(id) && (Settings.modTags() || Cosmetics.isWorn());
	}

	// The gradient touches only the name's glyphs; vanilla's team formatting wraps it, so a server's prefix/suffix survive
	public static Component decorate(String name, Team team, UUID uuid)
	{
		Minecraft mc = Minecraft.getInstance();

		if (mc.player != null && mc.player.getUUID().equals(uuid))
		{
			return compose(Cosmetics.apply(name), team, Cosmetics.tag());
		}

		return cached(uuid, name, null, teamHash(team), null, true, other -> decorateOther(name, team, other));
	}

	// Restyles the name inside what vanilla and other mods already built, so their icons, fonts and hover
	// events keep their place; the badge and tag lead. A nickname that hides the IGN falls back to the rebuild
	public static Component decorateShown(Component shown, String name, Team team, UUID uuid)
	{
		Minecraft mc = Minecraft.getInstance();

		if (shown.getString().contains(MARKER))
		{
			return shown;
		}

		if (mc.player != null && mc.player.getUUID().equals(uuid))
		{
			Component restyled = restyled(shown, name, (style, original) -> dressOr(original, Cosmetics.wornStops(),
					Cosmetics.effect(), () -> Cosmetics.apply(name, style, true)));
			return restyled == null ? decorate(name, team, uuid) : lead(Cosmetics.tag()).append(restyled);
		}

		return cached(uuid, name, null, 0, shown, true, other -> shownOther(shown, name, team, other));
	}

	private static Component shownOther(Component shown, String name, Team team, Cosmetics.Worn other)
	{
		Component restyled = restyled(shown, name, (style, original) -> other == null ? original
				: dressOr(original, other.stops(), other.effect(), () -> Cosmetics.applyWorn(name, other, style, true)));

		if (restyled == null)
		{
			return decorateOther(name, team, other);
		}

		return lead(other == null || other.tag() == null ? null : Cosmetics.tagOf(other.tag())).append(restyled);
	}

	private static Component restyled(Component shown, String name, BiFunction<Style, Component, Component> dress)
	{
		List<Run> runs = runsOf(shown);
		return dressFirst(runs, name, dress) ? join(runs) : null;
	}

	// A player wearing no colour or effect keeps the server's own styling, per-letter gradients included
	private static Component dressOr(Component original, int[] stops, String effect, Supplier<Component> dressed)
	{
		return stops.length == 0 && effect == null ? original : dressed.get();
	}

	// The local player is never cached: their loadout is edited live and Cosmetics reads it directly
	private static Component cached(UUID uuid, String name, Style base, int teamHash, Component source,
			boolean floating, Function<Cosmetics.Worn, Component> build)
	{
		Map<UUID, Cosmetics.Worn> current = worn;

		if (current != decoratedFor || DECORATED.size() > DECORATED_CACHE_LIMIT)
		{
			DECORATED.clear();
			decoratedFor = current;
		}

		Cosmetics.Worn other = current.get(uuid);
		DecorateKey key = new DecorateKey(uuid, name, other, Settings.modTags(), base, teamHash, source, floating);
		Component cached = DECORATED.get(key);

		if (cached != null)
		{
			return cached;
		}

		Component decorated = build.apply(other);
		DECORATED.put(key, decorated);
		return decorated;
	}

	// A server that hides nametags and draws a text_display never calls getNameTag, so its text is restyled instead
	public static Component decorateServerTag(Display.TextDisplay display, Component text)
	{
		Minecraft mc = Minecraft.getInstance();

		if (text == null || mc.level == null || mc.player == null)
		{
			return null;
		}

		AbstractClientPlayer player = display.getVehicle() instanceof AbstractClientPlayer rider ? rider : null;
		String flat = text.getString();

		if (player == null)
		{
			player = playerNamedIn(mc, flat);
		}

		if (player == null || !(isModUser(player.getUUID()) || isLocalPreview(player.getUUID())))
		{
			return null;
		}

		// Some clients hand the decorated text back as the next input; a plain name would be wrapped again
		// on every pass, stacking one badge and tag per frame
		if (flat.contains(MARKER) || flat.contains(ICON_GLYPHS))
		{
			if (!fedBackLogged)
			{
				fedBackLogged = true;
				SchematicIndexMod.LOGGER.debug("text_display already decorated, left alone: {}", flat);
			}

			return null;
		}

		List<Run> runs = runsOf(text);
		return replaceFirst(runs, player.getPlainTextName(), player.getUUID(), true) ? join(runs) : null;
	}

	// Chat lines and tab entries: only the earliest player name is restyled, in place, so a server's own prefixes
	// and hover events around it survive. That name is the sender or the tab entry's player; a name typed later in
	// a message never borrows its owner's tag, so staff tags can't be faked. Null when nothing was restyled
	public static Component decorateNamesIn(Component text)
	{
		Minecraft mc = Minecraft.getInstance();

		if (text == null || mc.getConnection() == null || mc.player == null || text.getString().contains(MARKER))
		{
			return null;
		}

		String flat = text.getString();
		PlayerInfo earliest = null;
		int earliestAt = Integer.MAX_VALUE;

		for (PlayerInfo info : mc.getConnection().getOnlinePlayers())
		{
			String name = info.getProfile().name();
			int at = wholeWord(flat, name);

			if (at >= 0 && (at < earliestAt || at == earliestAt && name.length() > earliest.getProfile().name().length()))
			{
				earliest = info;
				earliestAt = at;
			}
		}

		if (earliest == null)
		{
			return null;
		}

		UUID id = earliest.getProfile().id();

		if (!isModUser(id) && !isLocalPreview(id))
		{
			return null;
		}

		List<Run> runs = runsOf(text);
		return replaceFirst(runs, earliest.getProfile().name(), id, false) ? join(runs) : null;
	}

	private static List<Run> runsOf(Component text)
	{
		List<Run> runs = new ArrayList<>();
		text.visit((style, part) ->
		{
			runs.add(new Run(part, style, null));
			return Optional.empty();
		}, Style.EMPTY);
		return runs;
	}

	// A name floating on a text_display takes the hung effect tiles, one inline in chat or the tab list does not
	private static boolean replaceFirst(List<Run> runs, String name, UUID uuid, boolean floating)
	{
		return dressFirst(runs, name, (style, original) -> nameOnly(name, style, original, uuid, floating));
	}

	// The name is matched across runs, since servers that colour a name letter by letter send one run per
	// glyph. A run that already carries a decorated name is a wall, so one player's tag never lands inside another's
	private static boolean dressFirst(List<Run> runs, String name, BiFunction<Style, Component, Component> dress)
	{
		StringBuilder joined = new StringBuilder();
		int[] starts = new int[runs.size()];

		for (int i = 0; i < runs.size(); i++)
		{
			Run run = runs.get(i);
			starts[i] = joined.length();
			joined.append(run.decorated() == null ? run.text() : String.valueOf(WALL).repeat(run.text().length()));
		}

		int at = wholeWord(joined.toString(), name);

		if (at < 0)
		{
			return false;
		}

		int end = at + name.length();
		int first = runAt(runs, starts, at);
		int last = runAt(runs, starts, end - 1);
		Run head = runs.get(first);
		Run tail = runs.get(last);
		Component original = first == last ? Component.literal(name).withStyle(head.style())
				: spanOf(runs, starts, first, last, at, end);
		Run before = new Run(head.text().substring(0, at - starts[first]), head.style(), null);
		Run dressed = new Run(name, head.style(), dress.apply(head.style(), original));
		Run after = new Run(tail.text().substring(end - starts[last]), tail.style(), null);
		runs.subList(first, last + 1).clear();
		runs.addAll(first, List.of(before, dressed, after));
		return true;
	}

	private static int runAt(List<Run> runs, int[] starts, int index)
	{
		for (int i = 0; i < runs.size(); i++)
		{
			if (index < starts[i] + runs.get(i).text().length())
			{
				return i;
			}
		}

		return runs.size() - 1;
	}

	private static Component spanOf(List<Run> runs, int[] starts, int first, int last, int at, int end)
	{
		MutableComponent span = Component.empty();

		for (int i = first; i <= last; i++)
		{
			Run run = runs.get(i);
			int from = Math.max(at - starts[i], 0);
			int to = Math.min(end - starts[i], run.text().length());

			if (from < to)
			{
				span.append(Component.literal(run.text().substring(from, to)).withStyle(run.style()));
			}
		}

		return span;
	}

	// Each untouched run is rebuilt from its fully resolved style, so its font, hover and click survive as sent
	private static Component join(List<Run> runs)
	{
		MutableComponent out = Component.empty();

		for (Run run : runs)
		{
			if (run.decorated() != null)
			{
				out.append(run.decorated());
				continue;
			}

			if (run.text().isEmpty())
			{
				continue;
			}

			logKeptFont(run);
			out.append(Component.literal(run.text()).withStyle(run.style()));
		}

		return out;
	}

	private static void logKeptFont(Run run)
	{
		FontDescription font = run.style().getFont();

		if (FontDescription.DEFAULT.equals(font) || LOGGED_FONTS.size() >= LOGGED_FONTS_LIMIT || !LOGGED_FONTS.add(font))
		{
			return;
		}

		SchematicIndexMod.LOGGER.debug("Name styling kept a run in font {}: {}", font, run.text());
	}

	// Some servers park the display beside the player rather than mounting it, so the text is matched instead
	private static AbstractClientPlayer playerNamedIn(Minecraft mc, String flat)
	{
		for (AbstractClientPlayer candidate : mc.level.players())
		{
			UUID id = candidate.getUUID();

			if ((isModUser(id) || isLocalPreview(id)) && wholeWord(flat, candidate.getPlainTextName()) >= 0)
			{
				return candidate;
			}
		}

		return null;
	}

	private static int wholeWord(String text, String name)
	{
		int from = 0;

		while (true)
		{
			int at = text.indexOf(name, from);

			if (at < 0)
			{
				return -1;
			}

			int end = at + name.length();
			boolean startsClean = at == 0 || !isNameChar(text.charAt(at - 1));
			boolean endsClean = end == text.length() || !isNameChar(text.charAt(end));

			if (startsClean && endsClean)
			{
				return at;
			}

			from = end;
		}
	}

	private static boolean isNameChar(char c)
	{
		return Character.isLetterOrDigit(c) || c == '_';
	}

	private static Component nameOnly(String name, Style base, Component original, UUID uuid, boolean floating)
	{
		Minecraft mc = Minecraft.getInstance();

		if (mc.player != null && mc.player.getUUID().equals(uuid))
		{
			return compose(dressOr(original, Cosmetics.wornStops(), Cosmetics.effect(),
					() -> Cosmetics.apply(name, base, floating)), null, Cosmetics.tag());
		}

		return cached(uuid, name, base, 0, original, floating, other -> nameOnlyOther(name, base, original, other, floating));
	}

	private static Component nameOnlyOther(String name, Style base, Component original, Cosmetics.Worn other,
			boolean floating)
	{
		if (other == null)
		{
			return compose(original, null, null);
		}

		return compose(dressOr(original, other.stops(), other.effect(), () -> Cosmetics.applyWorn(name, other, base, floating)),
				null, other.tag() == null ? null : Cosmetics.tagOf(other.tag()));
	}

	private static Component decorateOther(String name, Team team, Cosmetics.Worn other)
	{
		if (other == null)
		{
			return compose(Component.literal(name), team, null);
		}

		return compose(Cosmetics.applyWorn(name, other), team,
				other.tag() == null ? null : Cosmetics.tagOf(other.tag()));
	}

	// Teams mutate in place, so the key hashes what formatNameForTeam reads rather than the object
	private static int teamHash(Team team)
	{
		if (!(team instanceof PlayerTeam playerTeam))
		{
			return team == null ? 0 : team.hashCode();
		}

		return Objects.hash(playerTeam.getPlayerPrefix(), playerTeam.getPlayerSuffix(), playerTeam.getColor());
	}

	public static Component compose(Component styled, Team team, Component tag)
	{
		return lead(tag).append(PlayerTeam.formatNameForTeam(team, styled));
	}

	private static MutableComponent lead(Component tag)
	{
		MutableComponent out = Component.empty().append(MARK);

		if (Settings.modTags())
		{
			out.append(ICON);
		}

		if (tag != null)
		{
			out.append(tag).append(Component.literal(" "));
		}

		return out;
	}

	public static Component creator(String name, int[] stops, Style base)
	{
		return Cosmetics.preview(name, stops, base, false, false);
	}

	public static synchronized void start()
	{
		if (started)
		{
			return;
		}

		started = true;
		Net.scheduler().scheduleAtFixedRate(ModTags::poll, FIRST_DELAY_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	private static void poll()
	{
		Minecraft mc = Minecraft.getInstance();
		// The connection is only safe to read on the render thread; snapshot there, then query off-thread
		mc.execute(() -> {
			String room = mc.getConnection() == null ? null : roomOf(mc.getCurrentServer());

			if (room != null)
			{
				NametagDiagnostic.probe(mc);
			}

			// A handshake that failed at launch is retried on a widening schedule rather than left for the
			// catalogue to trigger, since nothing is worn or seen until it succeeds
			if (room != null && !McAuth.verified() && !McAuth.working() && Settings.termsAccepted()
					&& VERIFY.due(System.currentTimeMillis()))
			{
				McAuth.ensureVerified(() ->
				{
				});
			}

			CosmeticColors.publishLoadout();
			noteLocalLoadout();
			Net.submit(() -> query(room));
		});
	}

	private static void noteLocalLoadout()
	{
		int loadout = Objects.hash(Settings.modTags(), Arrays.hashCode(Cosmetics.wornStops()), Cosmetics.effect(),
				CosmeticTags.equippedTag());

		if (loadout != localLoadout)
		{
			localLoadout = loadout;
			GENERATION.incrementAndGet();
		}
	}

	// The server only ever sees a hash of the address, never the address itself
	static @Nullable String roomOf(@Nullable ServerData server)
	{
		if (server == null || server.ip == null || server.ip.isBlank())
		{
			return null;
		}

		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(canonicalAddress(server.ip).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e)
		{
			return null;
		}
	}

	// An address already in canonical form hashes exactly as before, so older clients keep sharing its room
	static String canonicalAddress(String ip)
	{
		String address = ip.trim().toLowerCase();

		if (address.endsWith(DEFAULT_PORT))
		{
			address = address.substring(0, address.length() - DEFAULT_PORT.length());
		}

		if (address.endsWith("."))
		{
			address = address.substring(0, address.length() - 1);
		}

		for (String prefix : ALIAS_PREFIXES)
		{
			if (address.startsWith(prefix) && address.indexOf('.', prefix.length()) > 0)
			{
				return address.substring(prefix.length());
			}
		}

		return address;
	}

	private static void query(@Nullable String room)
	{
		long now = System.currentTimeMillis();

		if (!McAuth.verified() || !GATE.shouldSend(room, now))
		{
			return;
		}

		boolean left = room == null && GATE.room() != null;
		GATE.sent(room, now);

		if (room == null)
		{
			modUsers = Set.of();
			worn = Map.of();
			roomVersion = 0L;

			// Leaving tells the room now rather than after the server's silence timeout
			if (left)
			{
				Backend.peers(null, 0L);
			}

			return;
		}

		Backend.ApiResult result = Backend.peers(room, roomVersion);

		if (!result.ok())
		{
			GATE.failed();
			SchematicIndexMod.LOGGER.debug("peers poll -> {}, next attempt in {} ms", result.status(), GATE.delayMs());
			return;
		}

		GATE.succeeded();

		if (result.status() == 204 || result.body() == null)
		{
			return;
		}

		roomVersion = Json.longOf(result.body(), "version", 0L);
		readPeers(result.body());
	}

	// The answer is the whole room, so both views are replaced rather than merged
	private static void readPeers(JsonObject response)
	{
		Set<UUID> users = new HashSet<>();
		Map<UUID, Cosmetics.Worn> next = new HashMap<>();

		if (response.has("peers") && response.get("peers").isJsonArray())
		{
			for (JsonElement element : response.getAsJsonArray("peers"))
			{
				if (users.size() >= MAX_PEERS)
				{
					break;
				}

				if (!element.isJsonObject())
				{
					continue;
				}

				JsonObject row = element.getAsJsonObject();

				try
				{
					UUID uuid = UUID.fromString(Json.stringOf(row, "uuid", ""));
					Cosmetics.Worn wornRow = new Cosmetics.Worn(stops(row), CosmeticTags.readTag(row.get("tag")), effects(row));
					users.add(uuid);

					if (wornRow.stops().length > 0 || wornRow.tag() != null || !wornRow.effects().isEmpty())
					{
						next.put(uuid, wornRow);
					}
				}
				catch (IllegalArgumentException ignored)
				{
				}
			}
		}

		modUsers = Set.copyOf(users);
		worn = Map.copyOf(next);
		GENERATION.incrementAndGet();
	}

	private static int[] stops(JsonObject row)
	{
		if (!row.has("stops") || !row.get("stops").isJsonArray())
		{
			return new int[0];
		}

		List<JsonElement> raw = row.getAsJsonArray("stops").asList();
		int[] stops = new int[Math.min(raw.size(), MAX_STOPS)];

		for (int i = 0; i < stops.length; i++)
		{
			stops[i] = Json.asInt(raw.get(i), 0xFFFFFF) & 0xFFFFFF;
		}

		return stops;
	}

	private static Set<String> effects(JsonObject row)
	{
		if (!row.has("effects") || !row.get("effects").isJsonArray())
		{
			return Set.of();
		}

		List<String> list = Json.listOf(row, "effects");
		return Set.copyOf(list.size() > MAX_EFFECTS ? list.subList(0, MAX_EFFECTS) : list);
	}

	private record DecorateKey(UUID uuid, String name, Cosmetics.Worn worn, boolean modTags, Style base, int teamHash,
			Component source, boolean floating)
	{
	}

	private record Run(String text, Style style, Component decorated)
	{
	}

	// Decides which ticks reach the server; pure, so the schedule can be checked headless. A room change
	// goes out at once, a stable room every STABLE_INTERVAL_MS, well inside the 45 s the server waits
	// before it drops a silent member
	static final class PollGate
	{
		static final long STABLE_INTERVAL_MS = 10_000L;
		static final long[] BACKOFF_MS = { 5_000L, 10_000L, 20_000L, 60_000L };

		private @Nullable String lastRoom;
		private long lastSentAt = Long.MIN_VALUE;
		private int failures;

		boolean shouldSend(@Nullable String room, long now)
		{
			if (this.failures > 0)
			{
				return now - this.lastSentAt >= this.delayMs();
			}

			if (!Objects.equals(room, this.lastRoom))
			{
				return true;
			}

			return room != null && now - this.lastSentAt >= STABLE_INTERVAL_MS;
		}

		void sent(@Nullable String room, long now)
		{
			this.lastRoom = room;
			this.lastSentAt = now;
		}

		@Nullable String room()
		{
			return this.lastRoom;
		}

		void succeeded()
		{
			this.failures = 0;
		}

		void failed()
		{
			this.failures = Math.min(this.failures + 1, BACKOFF_MS.length);
		}

		long delayMs()
		{
			return this.failures == 0 ? 0L : BACKOFF_MS[this.failures - 1];
		}
	}

	// Spaces the launch handshake retries so a Mojang hiccup costs seconds, not the session
	static final class VerifyGate
	{
		static final long[] DELAY_MS = { 0L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L };

		private long lastTriedAt = Long.MIN_VALUE;
		private int attempts;

		boolean due(long now)
		{
			long delay = DELAY_MS[Math.min(this.attempts, DELAY_MS.length - 1)];

			if (this.lastTriedAt != Long.MIN_VALUE && now - this.lastTriedAt < delay)
			{
				return false;
			}

			this.lastTriedAt = now;
			this.attempts++;
			return true;
		}
	}
}

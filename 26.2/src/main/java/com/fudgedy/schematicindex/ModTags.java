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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Display;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// Badges other mod users' nametags with a bitmap-font glyph, so it rides the name Component instead of a bespoke draw
public final class ModTags
{
	private static final Identifier FONT = Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "nametag");
	// The font sits only on this leaf glyph: on the root, every character of the appended name would render as
	// a missing-glyph box. The trailing \uE001 is a 2px space in the same font, tucking the badge nearer the name
	private static final Component ICON = Component.literal("\uE000\uE001")
			.withStyle(Style.EMPTY.withFont(new FontDescription.Resource(FONT)));
	private static final int INTERVAL_SECONDS = 5;
	private static final int FIRST_DELAY_SECONDS = 2;
	private static final int DECORATED_CACHE_LIMIT = 256;
	private static final long EFFECT_BUCKET_MS = 50L;

	private static volatile Set<UUID> modUsers = Set.of();
	private static volatile Map<UUID, Cosmetics.Worn> worn = Map.of();
	private static boolean started;
	// One attempt per session: a failed handshake must not be retried on every poll
	private static boolean startupVerifyTried;
	private static final PollGate GATE = new PollGate();

	// Render-thread only: rebuilding a per-glyph gradient every frame for every visible player adds up
	private static final Map<DecorateKey, Component> DECORATED = new HashMap<>();
	private static Map<UUID, Cosmetics.Worn> decoratedFor = worn;

	private ModTags()
	{
	}

	public static boolean isModUser(UUID id)
	{
		return Settings.modTags() && modUsers.contains(id);
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

		return cached(uuid, name, null, teamHash(team), other -> decorateOther(name, team, other));
	}

	// The local player is never cached: their loadout is edited live and Cosmetics reads it directly
	private static Component cached(UUID uuid, String name, Style base, int teamHash,
			Function<Cosmetics.Worn, Component> build)
	{
		Map<UUID, Cosmetics.Worn> current = worn;

		if (current != decoratedFor || DECORATED.size() > DECORATED_CACHE_LIMIT)
		{
			DECORATED.clear();
			decoratedFor = current;
		}

		Cosmetics.Worn other = current.get(uuid);
		long bucket = other != null && !other.effects().isEmpty() ? Util.getMillis() / EFFECT_BUCKET_MS : 0L;
		DecorateKey key = new DecorateKey(uuid, name, other, Settings.modTags(), base, teamHash, bucket);
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

		String name = player.getPlainTextName();
		List<Run> runs = new ArrayList<>();
		text.visit((style, part) ->
		{
			runs.add(new Run(part, style));
			return Optional.empty();
		}, Style.EMPTY);

		MutableComponent out = Component.empty();
		boolean replaced = false;

		for (Run run : runs)
		{
			int at = replaced ? -1 : wholeWord(run.text(), name);

			if (at < 0)
			{
				out.append(Component.literal(run.text()).withStyle(run.style()));
				continue;
			}

			replaced = true;
			out.append(Component.literal(run.text().substring(0, at)).withStyle(run.style()));
			out.append(nameOnly(name, run.style(), player.getUUID()));
			out.append(Component.literal(run.text().substring(at + name.length())).withStyle(run.style()));
		}

		return replaced ? out : null;
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

	private static Component nameOnly(String name, Style base, UUID uuid)
	{
		Minecraft mc = Minecraft.getInstance();

		if (mc.player != null && mc.player.getUUID().equals(uuid))
		{
			return compose(Cosmetics.apply(name, base), null, Cosmetics.tag());
		}

		return cached(uuid, name, base, 0, other -> nameOnlyOther(name, base, other));
	}

	private static Component nameOnlyOther(String name, Style base, Cosmetics.Worn other)
	{
		if (other == null)
		{
			return compose(Component.literal(name).withStyle(base), null, null);
		}

		return compose(Cosmetics.applyWorn(name, other, base), null,
				other.tag() == null ? null : Cosmetics.tagOf(other.tag()));
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

	static Component compose(Component styled, Team team, Component tag)
	{
		MutableComponent out = Component.empty();

		if (Settings.modTags())
		{
			out.append(ICON);
		}

		if (tag != null)
		{
			out.append(tag).append(Component.literal(" "));
		}

		return out.append(PlayerTeam.formatNameForTeam(team, styled));
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
		// The tab list is only safe to touch on the render thread; snapshot there, then query off-thread
		mc.execute(() -> {
			if (mc.getConnection() == null)
			{
				modUsers = Set.of();
				return;
			}

			NametagDiagnostic.probe(mc);

			// Verification otherwise waits for the catalogue to be opened, leaving a saved loadout unworn
			if (!startupVerifyTried && !McAuth.verified() && Settings.termsAccepted())
			{
				startupVerifyTried = true;
				McAuth.ensureVerified(() ->
				{
				});
			}

			CosmeticColors.publishLoadout();

			Set<String> ids = new HashSet<>();
			for (PlayerInfo info : mc.getConnection().getOnlinePlayers())
			{
				ids.add(info.getProfile().id().toString());
			}
			Net.submit(() -> query(Set.copyOf(ids)));
		});
	}

	private static void query(Set<String> ids)
	{
		if (ids.isEmpty())
		{
			modUsers = Set.of();
			GATE.clear();
			return;
		}

		long now = System.currentTimeMillis();

		if (!GATE.shouldSend(ids, now))
		{
			return;
		}

		GATE.sent(ids, now);
		JsonObject response = Backend.modUsers(new ArrayList<>(ids));

		if (response == null)
		{
			GATE.failed();
			SchematicIndexMod.LOGGER.debug("mod-users check failed, next attempt in {} ms", GATE.delayMs());
			return;
		}

		GATE.succeeded();

		Set<UUID> next = new HashSet<>();

		if (response.has("users") && response.get("users").isJsonArray())
		{
			for (JsonElement element : response.getAsJsonArray("users"))
			{
				try
				{
					next.add(UUID.fromString(Json.asString(element, "")));
				}
				catch (IllegalArgumentException ignored)
				{
				}
			}
		}

		modUsers = Set.copyOf(next);
		worn = readCosmetics(response);
	}

	private static Map<UUID, Cosmetics.Worn> readCosmetics(JsonObject response)
	{
		if (!response.has("cosmetics") || !response.get("cosmetics").isJsonArray())
		{
			return Map.of();
		}

		Map<UUID, Cosmetics.Worn> next = new HashMap<>();

		for (JsonElement element : response.getAsJsonArray("cosmetics"))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject row = element.getAsJsonObject();

			try
			{
				next.put(UUID.fromString(Json.stringOf(row, "uuid", "")),
						new Cosmetics.Worn(stops(row), CosmeticTags.readTag(row.get("tag")), effects(row)));
			}
			catch (IllegalArgumentException ignored)
			{
			}
		}

		return Map.copyOf(next);
	}

	private static int[] stops(JsonObject row)
	{
		if (!row.has("stops") || !row.get("stops").isJsonArray())
		{
			return new int[0];
		}

		List<JsonElement> raw = row.getAsJsonArray("stops").asList();
		int[] stops = new int[raw.size()];

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

		return Set.copyOf(Json.listOf(row, "effects"));
	}

	private record DecorateKey(UUID uuid, String name, Cosmetics.Worn worn, boolean modTags, Style base, int teamHash,
			long bucket)
	{
	}

	private record Run(String text, Style style)
	{
	}

	// Decides which ticks reach the server; pure, so the schedule can be checked headless
	static final class PollGate
	{
		static final long STABLE_INTERVAL_MS = 30_000L;
		static final long[] BACKOFF_MS = { 5_000L, 10_000L, 20_000L, 60_000L };

		private Set<String> lastSent = Set.of();
		private long lastSentAt = Long.MIN_VALUE;
		private int failures;

		boolean shouldSend(Set<String> ids, long now)
		{
			if (this.failures > 0)
			{
				return now - this.lastSentAt >= this.delayMs();
			}

			if (!ids.equals(this.lastSent))
			{
				return true;
			}

			return now - this.lastSentAt >= STABLE_INTERVAL_MS;
		}

		void sent(Set<String> ids, long now)
		{
			this.lastSent = ids;
			this.lastSentAt = now;
		}

		void succeeded()
		{
			this.failures = 0;
		}

		void failed()
		{
			this.failures = Math.min(this.failures + 1, BACKOFF_MS.length);
		}

		void clear()
		{
			this.lastSent = Set.of();
		}

		long delayMs()
		{
			return this.failures == 0 ? 0L : BACKOFF_MS[this.failures - 1];
		}
	}
}

package com.fudgedy.schematicindex.fx;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Every nametag effect this client can draw: the bundled set, then whatever the server adds or retunes. A server
// definition wins by id, and one needing a kind this build lacks is skipped rather than drawn wrong
public final class Effects
{
	// Bumped when a kind is added; the server can hold back definitions an older client cannot draw
	public static final int ENGINE = 1;
	private static final String BUILT_IN = "/assets/schematicindex/effects.json";
	private static final String ROUTE = "/cosmetics/effects";
	private static final Logger LOGGER = LoggerFactory.getLogger("SchematicIndex");
	private static final int MAX_EFFECTS = 64;
	private static final Map<String, Kind> KINDS = Map.of(
			"shine", new ColourKinds.Shine(),
			"flow", new ColourKinds.Flow(),
			"wave", new ColourKinds.Wave(),
			"pulse", new ColourKinds.Pulse(),
			"neon", new SignKinds.Neon(),
			"glitch", new SignKinds.Glitch(),
			"frozen", new DressKinds.Frozen(),
			"molten", new DressKinds.Molten(),
			"toxic", new DressKinds.Toxic());

	private static final List<Effect> BUNDLED = readBundled();
	private static volatile List<Effect> all = BUNDLED;
	private static volatile boolean loading;

	private Effects()
	{
	}

	public static List<Effect> all()
	{
		return all;
	}

	public static @Nullable Effect get(@Nullable String id)
	{
		if (id == null)
		{
			return null;
		}

		for (Effect effect : all)
		{
			if (effect.id().equals(id))
			{
				return effect;
			}
		}

		return null;
	}

	public static boolean has(@Nullable String id)
	{
		return get(id) != null;
	}

	public static void refresh()
	{
		if (loading)
		{
			return;
		}

		loading = true;
		Net.submit(() ->
		{
			try
			{
				JsonObject served = Backend.getJsonAnon(ROUTE + "?engine=" + ENGINE);
				Minecraft.getInstance().execute(() ->
				{
					if (served != null)
					{
						apply(served);
					}

					loading = false;
				});
			}
			catch (Exception e)
			{
				LOGGER.debug("GET {} failed", ROUTE, e);
				loading = false;
			}
		});
	}

	// The served list sets the order; bundled effects it leaves out keep their place after it, and "hidden" drops one
	static void apply(JsonObject served)
	{
		Map<String, Effect> merged = new LinkedHashMap<>();
		Set<String> hidden = new HashSet<>();

		for (JsonElement element : Json.arrayOf(served, "effects"))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject json = element.getAsJsonObject();
			String id = Json.stringOf(json, "id", "");

			if (Json.boolOf(json, "hidden", false))
			{
				hidden.add(id);
				continue;
			}

			Effect effect = Effect.parse(json, KINDS);

			if (effect == null)
			{
				LOGGER.debug("Skipped effect {}: unknown kind or bad layers", id);
				continue;
			}

			if (merged.size() < MAX_EFFECTS)
			{
				merged.put(effect.id(), effect);
			}
		}

		for (Effect effect : BUNDLED)
		{
			if (!merged.containsKey(effect.id()) && !hidden.contains(effect.id()))
			{
				merged.put(effect.id(), effect);
			}
		}

		all = List.copyOf(merged.values());
		EffectGlyphs.invalidate();
	}

	private static List<Effect> readBundled()
	{
		List<Effect> out = new ArrayList<>();

		try (InputStream in = Effects.class.getResourceAsStream(BUILT_IN))
		{
			if (in == null)
			{
				LOGGER.warn("Missing bundled effects {}", BUILT_IN);
				return List.of();
			}

			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

			for (JsonElement element : Json.arrayOf(json, "effects"))
			{
				Effect effect = element.isJsonObject() ? Effect.parse(element.getAsJsonObject(), KINDS) : null;

				if (effect != null)
				{
					out.add(effect);
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to read bundled effects", e);
		}

		return List.copyOf(out);
	}
}

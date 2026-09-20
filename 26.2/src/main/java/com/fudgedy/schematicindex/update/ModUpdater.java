package com.fudgedy.schematicindex.update;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class ModUpdater
{
	private static final String FALLBACK_GAME_VERSION = "1.21.11";
	private static final String GAME_VERSION = detectGameVersion();
	private static final String LOADER = "fabric";
	private static final String USER_AGENT = "fudgedy/schematicindex (update-check)";

	public record Release(String version)
	{
	}

	private ModUpdater()
	{
	}

	// Read at runtime, so the Modrinth query matches this launch rather than a compile-time constant
	private static String detectGameVersion()
	{
		try
		{
			return FabricLoader.getInstance().getModContainer("minecraft")
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse(FALLBACK_GAME_VERSION);
		}
		catch (Exception e)
		{
			return FALLBACK_GAME_VERSION;
		}
	}

	public static @Nullable Release resolveLatest(String project)
	{
		if (project == null || project.isBlank())
		{
			return null;
		}

		try
		{
			String url = "https://api.modrinth.com/v2/project/" + project + "/version"
					+ "?loaders=" + enc("[\"" + LOADER + "\"]")
					+ "&game_versions=" + enc("[\"" + GAME_VERSION + "\"]");
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(12))
					.header("User-Agent", USER_AGENT)
					.GET()
					.build();
			HttpResponse<String> response = Net.client().send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() >= 400)
			{
				return null;
			}

			JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
			JsonObject best = null;
			String bestDate = "";

			for (JsonElement element : versions)
			{
				JsonObject version = element.getAsJsonObject();

				// An alpha or beta upload is never announced. A missing type counts as a release, since
				// older entries may not carry one
				String type = str(version, "version_type");

				if (type != null && !type.equalsIgnoreCase("release"))
				{
					continue;
				}

				String date = orEmpty(str(version, "date_published"));

				if (best == null || date.compareTo(bestDate) > 0)
				{
					best = version;
					bestDate = date;
				}
			}

			if (best == null)
			{
				return null;
			}

			String version = str(best, "version_number");
			return version == null ? null : new Release(version);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Modrinth resolve failed", e);
			return null;
		}
	}

	// Build metadata (+26.2) is ignored, so only the mod's own number decides
	public static boolean isNewer(String candidate, String current)
	{
		String[] left = numbers(candidate);
		String[] right = numbers(current);
		int count = Math.max(left.length, right.length);

		for (int i = 0; i < count; i++)
		{
			int order = comparePart(i < left.length ? left[i] : "0", i < right.length ? right[i] : "0");

			if (order != 0)
			{
				return order > 0;
			}
		}

		return false;
	}

	private static String[] numbers(String version)
	{
		int plus = version.indexOf('+');
		String core = plus < 0 ? version : version.substring(0, plus);
		return core.trim().split("[.-]");
	}

	private static int comparePart(String left, String right)
	{
		try
		{
			return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
		}
		catch (NumberFormatException e)
		{
			return left.compareTo(right);
		}
	}

	private static String enc(String value)
	{
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static @Nullable String str(JsonObject object, String key)
	{
		return Json.stringOf(object, key, null);
	}

	private static String orEmpty(@Nullable String value)
	{
		return value == null ? "" : value;
	}
}

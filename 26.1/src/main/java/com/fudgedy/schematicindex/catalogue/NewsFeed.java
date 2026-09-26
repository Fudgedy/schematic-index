package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class NewsFeed
{
	public record Item(String icon, String text)
	{
	}

	public record Entry(String badge, String title, String when, List<String> lines, boolean highlight,
			@Nullable String version, List<Item> items)
	{
	}

	private static volatile List<Entry> entries = List.of();
	private static volatile boolean loaded;

	private NewsFeed()
	{
	}

	public static List<Entry> entries()
	{
		return entries;
	}

	public static boolean loaded()
	{
		return loaded;
	}

	public static @Nullable Entry forVersion(String version)
	{
		for (Entry entry : entries)
		{
			if (entry.version() != null && sameVersion(entry.version(), version))
			{
				return entry;
			}
		}

		return null;
	}

	// The first highlight row of that version's entry, or null when the news has none
	public static @Nullable String highlightFor(String version)
	{
		Entry entry = forVersion(version);

		if (entry == null || entry.items().isEmpty())
		{
			return null;
		}

		String text = entry.items().get(0).text();
		return text.isBlank() ? null : text;
	}

	public static void refresh()
	{
		JsonObject body = Backend.getJson("/news");

		if (body == null)
		{
			SchematicIndexMod.LOGGER.debug("News load failed ({})", Errors.NEWS_LOAD);
			return;
		}

		try
		{
			List<Entry> list = new ArrayList<>();

			for (JsonElement element : Json.arrayOf(body, "news"))
			{
				if (element.isJsonObject())
				{
					list.add(Backend.parseNews(element.getAsJsonObject()));
				}
			}

			entries = list;
			loaded = true;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("News parse failed", e);
			Errors.report(Errors.PARSE);
		}
	}

	// Build metadata such as +26.2 never takes part in the match
	private static boolean sameVersion(String left, String right)
	{
		return core(left).equals(core(right));
	}

	private static String core(String version)
	{
		int plus = version.indexOf('+');
		return (plus < 0 ? version : version.substring(0, plus)).trim();
	}
}

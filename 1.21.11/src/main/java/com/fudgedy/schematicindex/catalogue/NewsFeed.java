package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class NewsFeed
{
	public record Entry(String badge, String title, String when, List<String> lines, boolean highlight)
	{
	}

	private static volatile List<Entry> entries = List.of();

	private NewsFeed()
	{
	}

	public static List<Entry> entries()
	{
		return entries;
	}

	public static void refresh()
	{
		JsonObject body = Backend.getJson("/news");

		if (body == null)
		{
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
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("News parse failed", e);
			Errors.report(Errors.PARSE);
		}
	}
}

package com.fudgedy.schematicindex.catalogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Tolerant readers for server JSON: a missing, null or wrongly typed field yields the fallback
// instead of a Gson exception, so an older client survives a newer payload
public final class Json
{
	private Json()
	{
	}

	public static int intOf(@Nullable JsonObject o, String key, int fallback)
	{
		return asInt(o == null ? null : o.get(key), fallback);
	}

	public static long longOf(@Nullable JsonObject o, String key, long fallback)
	{
		return asLong(o == null ? null : o.get(key), fallback);
	}

	public static double doubleOf(@Nullable JsonObject o, String key, double fallback)
	{
		return asDouble(o == null ? null : o.get(key), fallback);
	}

	public static boolean boolOf(@Nullable JsonObject o, String key, boolean fallback)
	{
		return asBool(o == null ? null : o.get(key), fallback);
	}

	public static @Nullable String stringOf(@Nullable JsonObject o, String key, @Nullable String fallback)
	{
		return asString(o == null ? null : o.get(key), fallback);
	}

	public static @Nullable JsonObject objectOf(@Nullable JsonObject o, String key)
	{
		JsonElement element = o == null ? null : o.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	public static JsonArray arrayOf(@Nullable JsonObject o, String key)
	{
		JsonElement element = o == null ? null : o.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	// Primitive elements as strings; nested objects, arrays and nulls are skipped
	public static List<String> listOf(@Nullable JsonObject o, String key)
	{
		List<String> out = new ArrayList<>();

		for (JsonElement element : arrayOf(o, key))
		{
			String value = asString(element, null);

			if (value != null)
			{
				out.add(value);
			}
		}

		return out;
	}

	public static int asInt(@Nullable JsonElement element, int fallback)
	{
		JsonPrimitive value = primitive(element);

		if (value == null)
		{
			return fallback;
		}

		try
		{
			return value.isNumber() ? value.getAsInt() : Integer.parseInt(value.getAsString().trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	public static long asLong(@Nullable JsonElement element, long fallback)
	{
		JsonPrimitive value = primitive(element);

		if (value == null)
		{
			return fallback;
		}

		try
		{
			return value.isNumber() ? value.getAsLong() : Long.parseLong(value.getAsString().trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	public static double asDouble(@Nullable JsonElement element, double fallback)
	{
		JsonPrimitive value = primitive(element);

		if (value == null)
		{
			return fallback;
		}

		try
		{
			return value.isNumber() ? value.getAsDouble() : Double.parseDouble(value.getAsString().trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	// A flag the server may send as true/false, 0/1 or "true"
	public static boolean asBool(@Nullable JsonElement element, boolean fallback)
	{
		JsonPrimitive value = primitive(element);

		if (value == null)
		{
			return fallback;
		}

		if (value.isBoolean())
		{
			return value.getAsBoolean();
		}

		if (value.isNumber())
		{
			return value.getAsInt() != 0;
		}

		String text = value.getAsString().trim();
		return text.equalsIgnoreCase("true") || (!text.equalsIgnoreCase("false") && fallback);
	}

	public static @Nullable String asString(@Nullable JsonElement element, @Nullable String fallback)
	{
		JsonPrimitive value = primitive(element);
		return value == null ? fallback : value.getAsString();
	}

	private static @Nullable JsonPrimitive primitive(@Nullable JsonElement element)
	{
		return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
	}
}

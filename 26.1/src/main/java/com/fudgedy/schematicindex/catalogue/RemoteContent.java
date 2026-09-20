package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Pulled from the server at runtime, so this content can change without a new release
public final class RemoteContent
{
	public record Credit(String username, String nickname, String role, String description, String avatarUrl)
	{
		public String displayName()
		{
			return nickname != null && !nickname.isBlank() ? nickname : username;
		}
	}

	public record Announcement(String id, String title, String tag, String description, String color)
	{
	}

	public record Terms(int version, String body)
	{
	}

	public record Link(String url, String iconUrl, String label)
	{
	}

	public record Partner(String url, String iconUrl, String name)
	{
	}

	private static volatile List<Credit> credits = List.of();
	private static volatile @Nullable Announcement announcement;
	private static volatile @Nullable Terms terms;
	private static volatile List<Link> links = List.of();
	private static volatile List<Partner> partners = List.of();
	private static volatile String discord = "";
	private static volatile boolean loaded;

	// Offline and unloaded installs still get a working invite; the server value, when set, wins
	private static final String DISCORD_FALLBACK = "https://discord.gg/schematicindex";

	private static final AtomicBoolean POLLING = new AtomicBoolean();

	private RemoteContent()
	{
	}

	public static List<Credit> credits()
	{
		return credits;
	}

	public static @Nullable Announcement announcement()
	{
		return announcement;
	}

	public static @Nullable Terms terms()
	{
		return terms;
	}

	public static List<Link> links()
	{
		return links;
	}

	public static List<Partner> partners()
	{
		return partners;
	}

	public static String discord()
	{
		String value = discord;
		return value != null && !value.isBlank() ? value : DISCORD_FALLBACK;
	}

	public static boolean loaded()
	{
		return loaded;
	}

	// Polled while a menu is open, so an announcement appears without waiting for a catalogue reload
	public static void pollAsync()
	{
		if (!POLLING.compareAndSet(false, true))
		{
			return;
		}

		Net.submit(() -> {
			try
			{
				refresh();
			}
			finally
			{
				POLLING.set(false);
			}
		});
	}

	public static void refresh()
	{
		JsonObject body = Backend.getJson("/content");

		if (body == null)
		{
			return;
		}

		try
		{
			List<Credit> list = new ArrayList<>();

			if (body.has("credits") && body.get("credits").isJsonArray())
			{
				for (JsonElement element : body.getAsJsonArray("credits"))
				{
					if (!element.isJsonObject())
					{
						continue;
					}

					JsonObject o = element.getAsJsonObject();
					list.add(new Credit(s(o, "username"), s(o, "nickname"), s(o, "role"), s(o, "description"), s(o, "avatarUrl")));
				}
			}

			credits = list;

			if (body.has("announcement") && body.get("announcement").isJsonObject())
			{
				JsonObject a = body.getAsJsonObject("announcement");
				announcement = new Announcement(s(a, "id"), s(a, "title"), s(a, "tag"), s(a, "description"), s(a, "color"));
			}
			else
			{
				announcement = null;
			}

			if (body.has("terms") && body.get("terms").isJsonObject())
			{
				JsonObject t = body.getAsJsonObject("terms");
				int version = Json.intOf(t, "version", 1);
				String termsBody = s(t, "body");

				if (!termsBody.isBlank())
				{
					terms = new Terms(version, termsBody);
					Settings.cacheTerms(version, termsBody);
				}
			}

			if (body.has("links") && body.get("links").isJsonArray())
			{
				List<Link> linkList = new ArrayList<>();

				for (JsonElement element : body.getAsJsonArray("links"))
				{
					if (!element.isJsonObject())
					{
						continue;
					}

					JsonObject o = element.getAsJsonObject();
					String url = s(o, "url");

					if (!url.isBlank())
					{
						linkList.add(new Link(url, s(o, "iconUrl"), s(o, "label")));
					}
				}

				links = linkList;
			}

			if (body.has("partners") && body.get("partners").isJsonArray())
			{
				List<Partner> partnerList = new ArrayList<>();

				for (JsonElement element : body.getAsJsonArray("partners"))
				{
					if (!element.isJsonObject())
					{
						continue;
					}

					JsonObject o = element.getAsJsonObject();
					String url = s(o, "url");

					if (!url.isBlank())
					{
						partnerList.add(new Partner(url, s(o, "iconUrl"), s(o, "name")));
					}
				}

				partners = partnerList;
			}

			discord = s(body, "discord");
			loaded = true;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Content parse failed", e);
			Errors.report(Errors.CONTENT);
		}
	}

	private static String s(JsonObject o, String key)
	{
		return Json.stringOf(o, key, "");
	}
}

package com.fudgedy.schematicindex.catalogue;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

// Inbox row and toast copy per notice type; a type this build does not know degrades to a generic line
final class NoticeText
{
	private NoticeText()
	{
	}

	static String row(JsonObject item, String type)
	{
		String title = Json.stringOf(item, "postTitle", "your post");
		JsonObject data = Json.objectOf(item, "data");

		return switch (type)
		{
			case "follow" -> {
				String follower = Json.stringOf(item, "follower", Json.stringOf(item, "name", ""));
				yield (follower.isBlank() ? "Someone" : follower) + " followed you.";
			}
			case "like" -> "Someone liked " + title + ".";
			case "post", "new_post", "newpost" -> "New post: " + title + ".";
			case "claim" -> {
				String status = Json.stringOf(item, "status", "");
				String verdict = "approved".equalsIgnoreCase(status) ? "approved"
						: "denied".equalsIgnoreCase(status) ? "denied" : "updated";
				yield "Your credit claim for \"" + title + "\" was " + verdict + ".";
			}
			case "link_request" -> "Link request from @" + Json.stringOf(data, "username", "");
			case "link_done" -> "Discord linked: @" + Json.stringOf(data, "username", "");
			case "link_removed" -> "Discord unlinked (@" + Json.stringOf(data, "username", "") + ")";
			case "perk_removed" -> perkRemoved(data);
			case "achievement" -> achievement(data);
			case "featured" -> "\"" + Json.stringOf(item, "postTitle", "Your build") + "\" is Build of the Day!";
			case "featured_reward" -> featuredReward(item, data);
			default -> "New activity on " + title + ".";
		};
	}

	static String linkDoneToast(@Nullable JsonObject data)
	{
		return Json.boolOf(data, "bonus", false)
				? "{Discord} and 100 Shards are waiting. Claim the Shards in your quests."
				: "{Discord} is unlocked.";
	}

	static String featuredToast(JsonObject item, @Nullable JsonObject data)
	{
		return "\"" + Json.stringOf(item, "postTitle", "Your build") + "\" was picked with "
				+ Json.intOf(data, "downloads", 0) + " downloads yesterday.";
	}

	private static String featuredReward(JsonObject item, @Nullable JsonObject data)
	{
		String title = Json.stringOf(item, "postTitle", "Your build");
		int shards = Json.intOf(data, "shards", 0);
		return title + " won Build of the Day!" + (shards > 0 ? " +" + shards + " Shards" : "");
	}

	private static String perkRemoved(@Nullable JsonObject data)
	{
		return switch (Json.stringOf(data, "reason", ""))
		{
			case "left_guild" -> "You left the Discord, so {Discord} was removed. Rejoin to get it back.";
			case "stopped_boosting" -> "Your boost ended, so {Booster} was removed.";
			case "month_ended" -> "{Top 3} moved on to this month's leaders.";
			case "unlinked" -> "Unlinking removed {Discord}.";
			default -> "{" + Json.stringOf(data, "tag", "A perk") + "} was removed.";
		};
	}

	private static String achievement(@Nullable JsonObject data)
	{
		String message = Json.stringOf(data, "message", "");

		if (message != null && !message.isBlank())
		{
			return message;
		}

		JsonObject tag = Json.objectOf(data, "tag");
		return "Achievement unlocked: " + Json.stringOf(tag, "bracketOpen", "{") + Json.stringOf(tag, "label", "")
				+ Json.stringOf(tag, "bracketClose", "}");
	}

	// The reason may ride in status or data.reason; a bare verdict word is not a reason
	private static String postDenied(JsonObject item, @Nullable JsonObject data)
	{
		String text = "Your build " + Json.stringOf(item, "postTitle", "") + " was not accepted.";
		String reason = Json.stringOf(data, "reason", "");

		if (reason == null || reason.isBlank())
		{
			reason = Json.stringOf(item, "status", "");
		}

		if (reason == null || reason.isBlank() || reason.equalsIgnoreCase("denied") || reason.equalsIgnoreCase("rejected"))
		{
			return text;
		}

		return text + " " + reason.trim();
	}
}

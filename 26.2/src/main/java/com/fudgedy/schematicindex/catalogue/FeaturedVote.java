package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Tomorrow's Build of the Day shortlist and this player's vote on it; read with the session when there is
// one, so the server can answer with myVote and the counts
public final class FeaturedVote
{
	private static final long STALE_MS = 60_000L;
	private static final AtomicBoolean LOADING = new AtomicBoolean();

	private static volatile @Nullable Ballot ballot;
	private static volatile long fetchedAt;
	private static volatile boolean voting;

	// votes is -1 while the server keeps the counts hidden from someone who has not voted
	public record Candidate(String postId, String title, String designer, @Nullable String thumbnailUrl, int votes)
	{
	}

	public record Ballot(String day, long closesAt, List<Candidate> candidates, @Nullable String myVote)
	{
		public boolean open()
		{
			return !this.candidates.isEmpty() && this.closesAt > System.currentTimeMillis();
		}
	}

	private FeaturedVote()
	{
	}

	public static @Nullable Ballot ballot()
	{
		return RemoteContent.feature("buildOfTheDay") ? ballot : null;
	}

	public static boolean isVoting()
	{
		return voting;
	}

	public static void refreshIfStale()
	{
		Ballot current = ballot;
		boolean expired = current != null && current.closesAt() <= System.currentTimeMillis();

		if (!RemoteContent.feature("buildOfTheDay") || (!expired && System.currentTimeMillis() - fetchedAt < STALE_MS)
				|| !LOADING.compareAndSet(false, true))
		{
			return;
		}

		Net.submit(() -> {
			try
			{
				fetch();
			}
			finally
			{
				fetchedAt = System.currentTimeMillis();
				LOADING.set(false);
			}
		});
	}

	public static void vote(String postId)
	{
		if (voting)
		{
			return;
		}

		if (!McAuth.verified())
		{
			Toasts.push("Verify your account first", "Voting needs a verified account.", new ItemStack(Items.NAME_TAG));
			return;
		}

		voting = true;
		JsonObject body = new JsonObject();
		body.addProperty("postId", postId);
		Net.submit(() -> {
			try
			{
				Backend.ApiResult result = Backend.sendForApiResult("POST", "/me/featured/vote", body.toString());
				SchematicIndexMod.LOGGER.debug("POST /me/featured/vote -> {} {}", result.status(), result.error());

				if (result.ok())
				{
					Ballot current = ballot;

					if (current != null)
					{
						ballot = new Ballot(current.day(), current.closesAt(), current.candidates(),
								Json.stringOf(result.body(), "myVote", postId));
					}

					fetch();
					Minecraft.getInstance().execute(() -> Toasts.push("Vote counted",
							"You can change it until voting closes.", new ItemStack(Items.BELL)));
					return;
				}

				if (result.is("closed") || result.is("not_candidate"))
				{
					fetch();
				}

				Minecraft.getInstance().execute(() -> refused(result));
			}
			finally
			{
				voting = false;
			}
		});
	}

	private static void refused(Backend.ApiResult result)
	{
		if (result.unverified() || result.is("not_verified"))
		{
			Toasts.refusal(result, Errors.FEATURED_VOTE);
			return;
		}

		String reason = switch (result.error() == null ? "" : result.error())
		{
			case "too_new" -> "Your account needs to be 3 days old to vote";
			case "closed" -> "Voting for tomorrow's build has closed";
			case "not_candidate" -> "That build isn't up for the vote any more";
			default -> "Try again in a moment";
		};

		Toasts.push("Couldn't vote", reason + ". (" + Errors.FEATURED_VOTE + ")", new ItemStack(Items.BARRIER));
	}

	private static void fetch()
	{
		Backend.ApiResult result = Backend.sendForApiResult("GET", "/featured/vote", null);
		JsonObject body = result.body();

		if (!result.ok() || body == null)
		{
			SchematicIndexMod.LOGGER.debug("GET /featured/vote failed with status {}", result.status());
			return;
		}

		List<Candidate> candidates = new ArrayList<>();

		for (JsonElement element : Json.arrayOf(body, "candidates"))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject o = element.getAsJsonObject();
			String postId = Json.stringOf(o, "postId", "");

			if (!postId.isBlank())
			{
				JsonElement votes = o.get("votes");
				int count = votes != null && votes.isJsonPrimitive() ? Json.asInt(votes, -1) : -1;
				candidates.add(new Candidate(postId, Json.stringOf(o, "title", ""), Json.stringOf(o, "designer", ""),
						Json.stringOf(o, "thumbnailUrl", null), count));
			}
		}

		ballot = new Ballot(Json.stringOf(body, "day", ""), Json.longOf(body, "closesAt", 0L), List.copyOf(candidates),
				Json.stringOf(body, "myVote", null));
	}
}

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Client mirror of the account's Discord link (/me/link): the linked Discord, requests waiting on the
// player, and the live link code
public final class DiscordLink
{
	public record Discord(String id, String username, @Nullable String globalName, @Nullable String avatarUrl,
			boolean inGuild, boolean boosting, long linkedAt)
	{
	}

	public record Request(int id, String discordId, String username, @Nullable String globalName,
			@Nullable String avatarUrl, long discordCreatedAt, long createdAt, long expiresAt)
	{
		public String displayName()
		{
			return this.globalName == null || this.globalName.isBlank() ? this.username : this.globalName;
		}
	}

	public record Code(String code, long expiresAt)
	{
	}

	public record State(boolean linked, @Nullable Discord discord, List<Request> pending, @Nullable Code code,
			long relinkAvailableAt, boolean bonusAvailable, boolean bonusClaimed, String inviteUrl)
	{
	}

	private static final State EMPTY = new State(false, null, List.of(), null, 0L, false, false, "");
	// A resize or a return from the link confirm screen re-runs init, which must not re-read each time
	private static final long OPEN_REFRESH_MS = 30000L;
	// Avatars come from the bot and are drawn by ImageStore, which would also read a local path
	private static final String AVATAR_HOST = "https://cdn.discordapp.com/";
	private static final Set<Integer> TOASTED = ConcurrentHashMap.newKeySet();

	private static volatile State state = EMPTY;
	// The account the state was read for, so a sign-out or account switch never shows the last one's Discord
	private static volatile @Nullable String owner;
	private static volatile long lastOpenRefresh;

	private DiscordLink()
	{
	}

	public static State state()
	{
		return loaded() ? state : EMPTY;
	}

	public static boolean loaded()
	{
		return owner != null && owner.equals(McAuth.verifiedUuid());
	}

	public static boolean enabled()
	{
		return RemoteContent.feature("discordLink");
	}

	public static void refreshOnOpen()
	{
		long now = System.currentTimeMillis();

		if (!McAuth.verified() || !enabled() || now - lastOpenRefresh < OPEN_REFRESH_MS)
		{
			return;
		}

		lastOpenRefresh = now;
		status(null);
	}

	// done receives whether the read succeeded, on the client thread
	public static void status(@Nullable Consumer<Boolean> done)
	{
		if (!McAuth.verified())
		{
			return;
		}

		Net.submit(() -> {
			Backend.ApiResult result = Backend.linkState();
			SchematicIndexMod.LOGGER.debug("GET /me/link -> {}", result.status());

			if (result.ok())
			{
				apply(result.body());
			}

			if (done != null)
			{
				Minecraft.getInstance().execute(() -> done.accept(result.ok()));
			}
		});
	}

	public static void startCode(Consumer<Code> onCode, Runnable onLinked, Runnable onFailed)
	{
		Net.submit(() -> {
			Backend.ApiResult result = Backend.linkCode();
			SchematicIndexMod.LOGGER.debug("POST /me/link/code -> {} {}", result.status(), result.error());
			JsonObject body = result.body();

			if (result.ok() && body != null && body.has("code"))
			{
				Code code = new Code(Json.stringOf(body, "code", ""), Json.longOf(body, "expiresAt", 0L));
				Minecraft.getInstance().execute(() -> onCode.accept(code));
				return;
			}

			if (result.is("already_linked"))
			{
				status(ok -> onLinked.run());
				return;
			}

			Minecraft.getInstance().execute(() -> {
				Toasts.push("Couldn't get a link code", "Try again in a moment. (" + Errors.DISCORD_CODE + ")",
						new ItemStack(Items.BARRIER));
				onFailed.run();
			});
		});
	}

	public static void accept(int requestId, Runnable onDone)
	{
		Net.submit(() -> respond(requestId, Backend.linkAccept(requestId), onDone));
	}

	public static void deny(int requestId, boolean block, Runnable onDone)
	{
		Net.submit(() -> respond(requestId, Backend.linkDeny(requestId, block), onDone));
	}

	public static void unlink(Runnable onDone)
	{
		Net.submit(() -> {
			Backend.ApiResult result = Backend.unlinkDiscord();
			SchematicIndexMod.LOGGER.debug("DELETE /me/link -> {} {}", result.status(), result.error());

			if (result.ok() || result.is("not_linked"))
			{
				apply(result.ok() ? result.body() : null);
				Minecraft.getInstance().execute(onDone);

				if (!result.ok())
				{
					status(null);
				}

				return;
			}

			Minecraft.getInstance().execute(() -> Toasts.push("Couldn't unlink Discord",
					"Try again in a moment. (" + Errors.DISCORD_UNLINK + ")", new ItemStack(Items.BARRIER)));
		});
	}

	public static void onShardsPanel(JsonObject panel)
	{
		JsonObject pending = Json.objectOf(panel, "pendingLink");

		if (pending == null)
		{
			return;
		}

		int id = Json.intOf(pending, "id", -1);
		String username = Json.stringOf(pending, "username", "");
		long expiresAt = Json.longOf(pending, "expiresAt", 0L);
		Minecraft.getInstance().execute(() -> toastRequest(id, username, expiresAt));
	}

	public static void onNudge()
	{
		status(ok -> {
			for (Request request : state().pending())
			{
				toastRequest(request.id(), request.username(), request.expiresAt());
			}
		});
	}

	// One toast per request per session, whichever path saw it first; an expired one stays quiet
	public static boolean toastRequest(int requestId, String username, long expiresAt)
	{
		if (requestId < 0 || !enabled() || expiresAt <= System.currentTimeMillis() || !TOASTED.add(requestId))
		{
			return false;
		}

		Toasts.pushAction("Discord link request", "@" + username + " wants to link to your account.",
				new ItemStack(Items.NAME_TAG), "Open",
				() -> Notices.withIndex(screen -> screen.linkRequestModal.open(requestId)));
		return true;
	}

	public static @Nullable Request request(int requestId)
	{
		for (Request request : state().pending())
		{
			if (request.id() == requestId)
			{
				return request;
			}
		}

		return null;
	}

	public static @Nullable String safeAvatar(@Nullable String url)
	{
		return url != null && url.startsWith(AVATAR_HOST) ? url : null;
	}

	public static String monthYear(long millis)
	{
		return new SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(new Date(millis));
	}

	private static void respond(int requestId, Backend.ApiResult result, Runnable onDone)
	{
		SchematicIndexMod.LOGGER.debug("Link request {} answer -> {} {}", requestId, result.status(), result.error());

		if (result.ok())
		{
			apply(result.body());
			Minecraft.getInstance().execute(() -> {
				onDone.run();
				Notices.pollNow();
			});
			return;
		}

		String reason = switch (result.error() == null ? "" : result.error())
		{
			case "request_expired", "no_request" -> "That request expired. Ask for a new one in Discord.";
			case "discord_taken" -> "That Discord is already linked to another account.";
			case "already_linked" -> "You already have a Discord linked. Unlink it first.";
			case "relink_cooldown" -> "You can link a different Discord on "
					+ new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(
							new Date(Json.longOf(result.body(), "availableAt", 0L))) + ".";
			default -> "Couldn't answer the link request.";
		};

		status(null);
		Minecraft.getInstance().execute(() -> {
			Toasts.push("Discord link", reason + " (" + Errors.DISCORD_RESPOND + ")", new ItemStack(Items.BARRIER));
			onDone.run();
		});
	}

	private static void apply(@Nullable JsonObject o)
	{
		if (o == null || !o.has("linked"))
		{
			return;
		}

		JsonObject discord = Json.objectOf(o, "discord");
		JsonObject code = Json.objectOf(o, "code");
		JsonObject bonus = Json.objectOf(o, "bonus");
		boolean linked = Json.boolOf(o, "linked", false);
		state = new State(linked, discord == null ? null : readDiscord(discord), readPending(o),
				code == null ? null : new Code(Json.stringOf(code, "code", ""), Json.longOf(code, "expiresAt", 0L)),
				Json.longOf(o, "relinkAvailableAt", 0L), Json.boolOf(bonus, "available", false),
				Json.boolOf(bonus, "claimed", false), Json.stringOf(o, "inviteUrl", ""));
		owner = McAuth.verifiedUuid();
		AccountFlags.discordLinked = linked;
	}

	private static Discord readDiscord(JsonObject o)
	{
		return new Discord(Json.stringOf(o, "id", ""), Json.stringOf(o, "username", ""),
				Json.stringOf(o, "globalName", null), safeAvatar(Json.stringOf(o, "avatarUrl", null)),
				Json.boolOf(o, "inGuild", false), Json.boolOf(o, "boosting", false), Json.longOf(o, "linkedAt", 0L));
	}

	private static List<Request> readPending(JsonObject o)
	{
		List<Request> pending = new ArrayList<>();

		for (JsonElement element : Json.arrayOf(o, "pending"))
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject row = element.getAsJsonObject();
			pending.add(new Request(Json.intOf(row, "id", -1), Json.stringOf(row, "discordId", ""),
					Json.stringOf(row, "username", ""), Json.stringOf(row, "globalName", null),
					safeAvatar(Json.stringOf(row, "avatarUrl", null)), Json.longOf(row, "discordCreatedAt", 0L),
					Json.longOf(row, "createdAt", 0L), Json.longOf(row, "expiresAt", 0L)));
		}

		return List.copyOf(pending);
	}
}

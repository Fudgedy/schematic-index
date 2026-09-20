package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.Presence;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The only class allowed to touch the session API, so the version ports diverge in just this file
public final class McAuth
{
	public static final String ROLE_USER = "user";
	public static final String ROLE_STAFF = "staff";
	public static final String ROLE_OWNER = "owner";
	private static final List<Runnable> WAITING = new ArrayList<>();
	private static final long RECOVER_WAIT_MS = 20_000L;

	private static volatile State state = State.UNTRIED;
	private static volatile @Nullable Thread handshakeThread;
	private static volatile @Nullable String token;
	private static volatile @Nullable String name;
	private static volatile @Nullable String uuid;
	private static volatile @Nullable String failure;
	private static volatile int shards;
	private static volatile boolean welcomePending;
	private static volatile String role = ROLE_USER;

	private McAuth()
	{
	}

	public static boolean verified()
	{
		return state == State.VERIFIED;
	}

	public static boolean working()
	{
		return state == State.WORKING;
	}

	public static @Nullable String sessionToken()
	{
		return token;
	}

	public static @Nullable String verifiedName()
	{
		return name;
	}

	public static @Nullable String verifiedUuid()
	{
		return uuid;
	}

	public static int shards()
	{
		return shards;
	}

	public static void setShards(int value)
	{
		shards = value;
	}

	public static boolean welcomePending()
	{
		return welcomePending;
	}

	public static void setWelcomePending(boolean value)
	{
		welcomePending = value;
	}

	public static String role()
	{
		return role;
	}

	// The server decides both; the mod only mirrors what /me/account said for this session
	public static boolean isStaff()
	{
		return state == State.VERIFIED && (ROLE_STAFF.equals(role) || ROLE_OWNER.equals(role));
	}

	public static boolean isOwner()
	{
		return state == State.VERIFIED && ROLE_OWNER.equals(role);
	}

	public static void refreshRole()
	{
		if (state != State.VERIFIED)
		{
			return;
		}

		JsonObject account = Backend.myAccount();

		String refreshed = Json.stringOf(account, "role", null);

		if (refreshed == null)
		{
			return;
		}

		role = refreshed;
		SchematicIndexMod.LOGGER.debug("auth: role refreshed to {}", role);
	}

	public static String failureCode()
	{
		String code = failure;
		return code == null ? Errors.AUTH_VERIFY : code;
	}

	public static void invalidate()
	{
		synchronized (WAITING)
		{
			if (state == State.VERIFIED)
			{
				dropSession();
			}
		}
	}

	private static void dropSession()
	{
		state = State.UNTRIED;
		token = null;
		name = null;
		uuid = null;
		role = ROLE_USER;
		welcomePending = false;
		Settings.setSessionToken("");
	}

	// Drops the session locally first so the UI reads signed out at once; the server call only retires the token
	public static void signOut()
	{
		String session = token;

		if (state != State.VERIFIED || session == null)
		{
			return;
		}

		invalidate();
		shards = 0;
		CosmeticColors.clear();
		CosmeticTags.clear();
		Net.submit(() -> SchematicIndexMod.LOGGER.debug("auth/logout -> {}", Backend.logout(session).status()));
	}

	// onDone runs on the main thread either way; a call during an in-flight handshake queues behind it
	public static void ensureVerified(Runnable onDone)
	{
		if (state == State.VERIFIED)
		{
			onDone.run();
			return;
		}

		synchronized (WAITING)
		{
			WAITING.add(onDone);

			if (state == State.WORKING)
			{
				return;
			}

			state = State.WORKING;
			failure = null;
		}

		Thread worker = new Thread(() -> finish(runHandshake(), true), "schematicindex-mcauth");
		worker.setDaemon(true);
		worker.start();
	}

	// Runs the handshake on the calling thread so the rejected request can retry once; a handshake already
	// in flight is waited for, and a request the handshake itself sends must never recurse into another
	public static boolean recover(String rejected)
	{
		if (Thread.currentThread() == handshakeThread)
		{
			return false;
		}

		synchronized (WAITING)
		{
			if (state == State.WORKING)
			{
				return awaitHandshake();
			}

			// Another request already replaced the dead token, or a sign-out dropped it on purpose
			if (!rejected.equals(token))
			{
				return state == State.VERIFIED;
			}

			dropSession();
			state = State.WORKING;
			failure = null;
		}

		SchematicIndexMod.LOGGER.debug("auth: session rejected, re-verifying");
		return finish(runHandshake(), false);
	}

	private static boolean awaitHandshake()
	{
		long deadline = System.currentTimeMillis() + RECOVER_WAIT_MS;

		while (state == State.WORKING)
		{
			long left = deadline - System.currentTimeMillis();

			if (left <= 0L)
			{
				return false;
			}

			try
			{
				WAITING.wait(left);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return false;
			}
		}

		return state == State.VERIFIED;
	}

	private static @Nullable String runHandshake()
	{
		handshakeThread = Thread.currentThread();

		try
		{
			return handshake();
		}
		finally
		{
			handshakeThread = null;
		}
	}

	private static boolean finish(@Nullable String code, boolean fresh)
	{
		List<Runnable> waiting;

		synchronized (WAITING)
		{
			failure = code;
			state = code == null ? State.VERIFIED : State.FAILED;
			waiting = List.copyOf(WAITING);
			WAITING.clear();
			WAITING.notifyAll();
		}

		// The nametag must wear the saved loadout without the catalogue ever being opened
		if (fresh && state == State.VERIFIED)
		{
			CosmeticColors.refresh();
			CosmeticTags.refresh();
			Library.sync();
			Presence.beatNow();
			Usage.flush();
		}

		Minecraft.getInstance().execute(() -> {
			for (Runnable done : waiting)
			{
				done.run();
			}
		});
		return code == null;
	}

	private static @Nullable String handshake()
	{
		if (resume())
		{
			return null;
		}

		Backend.ApiResult challenge = Backend.authChallenge();
		String serverId = Json.stringOf(challenge.body(), "serverId", null);
		SchematicIndexMod.LOGGER.debug("auth/challenge -> {} serverId? {}", challenge.status(), serverId != null);

		if (!challenge.ok() || serverId == null || serverId.isBlank())
		{
			return Errors.AUTH_CHALLENGE;
		}

		User user = Minecraft.getInstance().getUser();
		UUID profileId = user.getProfileId();

		if (profileId == null)
		{
			SchematicIndexMod.LOGGER.debug("auth: no profile id, offline session");
			return Errors.AUTH_SESSION;
		}

		try
		{
			Minecraft.getInstance().services().sessionService()
					.joinServer(profileId, user.getAccessToken(), serverId);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("auth: joinServer rejected", e);
			return Errors.AUTH_SESSION;
		}

		SchematicIndexMod.LOGGER.debug("auth: joinServer accepted for {}", user.getName());
		Backend.ApiResult verify = Backend.authVerify(user.getName(), serverId);
		SchematicIndexMod.LOGGER.debug("auth/verify -> {} {}", verify.status(), verify.error());
		String session = field(verify, "token");

		if (!verify.ok() || session == null || session.isBlank())
		{
			return Errors.AUTH_VERIFY;
		}

		token = session;
		name = field(verify, "name");
		uuid = field(verify, "uuid");
		shards = intField(verify, "shards");
		welcomePending = boolField(verify, "welcomePending");
		String verifiedRole = field(verify, "role");
		role = verifiedRole == null ? ROLE_USER : verifiedRole;
		Settings.setSessionToken(session);
		SchematicIndexMod.LOGGER.debug("auth: verified as {} ({} shards, role {}, welcome pending? {})", name, shards,
				role, welcomePending);
		return null;
	}

	// A session kept from the last launch skips the Mojang round-trip, which rate-limits a launch spike for everyone
	private static boolean resume()
	{
		String stored = Settings.sessionToken();

		if (stored.isBlank())
		{
			return false;
		}

		Backend.ApiResult account = Backend.accountFor(stored);
		boolean verified = account.ok() && Json.boolOf(account.body(), "verified", false);
		SchematicIndexMod.LOGGER.debug("auth: stored session -> {} verified? {}", account.status(), verified);

		if (!verified)
		{
			if (account.status() != -1 && account.status() < 500)
			{
				Settings.setSessionToken("");
			}

			return false;
		}

		String accountUuid = field(account, "uuid");
		UUID profileId = Minecraft.getInstance().getUser().getProfileId();

		// Another account on the same install must not inherit the stored session
		if (profileId == null || accountUuid == null || !sameUuid(profileId, accountUuid))
		{
			SchematicIndexMod.LOGGER.debug("auth: stored session belongs to another account, dropping");
			Settings.setSessionToken("");
			return false;
		}

		token = stored;
		name = field(account, "name");
		uuid = accountUuid;
		String storedRole = field(account, "role");
		role = storedRole == null ? ROLE_USER : storedRole;
		welcomePending = boolField(account, "welcomePending");
		SchematicIndexMod.LOGGER.debug("auth: resumed as {} (role {}, welcome pending? {})", name, role, welcomePending);
		return true;
	}

	private static boolean sameUuid(UUID profileId, String other)
	{
		return profileId.toString().replace("-", "").equalsIgnoreCase(other.replace("-", ""));
	}

	private static @Nullable String field(Backend.ApiResult result, String key)
	{
		return Json.stringOf(result.body(), key, null);
	}

	private static int intField(Backend.ApiResult result, String key)
	{
		return Json.intOf(result.body(), key, 0);
	}

	private static boolean boolField(Backend.ApiResult result, String key)
	{
		return Json.boolOf(result.body(), key, false);
	}

	private enum State
	{
		UNTRIED, WORKING, VERIFIED, FAILED
	}
}

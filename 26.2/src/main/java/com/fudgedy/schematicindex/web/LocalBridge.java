package com.fudgedy.schematicindex.web;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.Keybinds;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.fudgedy.schematicindex.SettingsKeys;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.catalogue.RemoteContent;
import com.fudgedy.schematicindex.catalogue.Usage;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

// Lets schematicindex.com hand a post id to the running game. Loopback only, and nothing but ping and open
public final class LocalBridge
{
	private static final int[] PORTS = {47615, 47616, 47617, 47618, 47619};
	private static final String SITE_ORIGIN = "https://schematicindex.com";
	private static final int TIMEOUT_MS = 2000;
	private static final long OPEN_INTERVAL_MS = 2000L;
	private static final int TITLE_MAX = 40;

	private static @Nullable ServerSocket server;
	private static long lastOpenAt;

	private LocalBridge()
	{
	}

	public static synchronized void start()
	{
		if (server != null || !Settings.flag(SettingsKeys.WEB_OPEN, true))
		{
			return;
		}

		ServerSocket bound = bind();

		if (bound == null)
		{
			return;
		}

		server = bound;
		Thread worker = new Thread(() -> serve(bound), "schematicindex-bridge");
		worker.setDaemon(true);
		worker.start();
		Runtime.getRuntime().addShutdownHook(new Thread(LocalBridge::stop, "schematicindex-bridge-stop"));
		SchematicIndexMod.LOGGER.debug("Local bridge listening on 127.0.0.1:{}", bound.getLocalPort());
	}

	public static synchronized void stop()
	{
		if (server == null)
		{
			return;
		}

		try
		{
			server.close();
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.debug("Local bridge close failed", e);
		}

		server = null;
	}

	private static @Nullable ServerSocket bind()
	{
		IOException last = null;

		for (int port : PORTS)
		{
			ServerSocket socket = null;

			try
			{
				socket = new ServerSocket();
				socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port), 8);
				return socket;
			}
			catch (IOException e)
			{
				last = e;
				closeQuietly(socket);
			}
		}

		SchematicIndexMod.LOGGER.warn("Local bridge could not start on ports 47615-47619 ({})", Errors.OPEN_BRIDGE, last);
		return null;
	}

	private static void serve(ServerSocket socket)
	{
		while (!socket.isClosed())
		{
			try (Socket client = socket.accept())
			{
				client.setSoTimeout(TIMEOUT_MS);
				answer(client);
			}
			catch (Throwable t)
			{
				if (!socket.isClosed())
				{
					SchematicIndexMod.LOGGER.debug("Local bridge connection failed", t);
				}
			}
		}
	}

	// One connection at a time on this thread; the 2 s deadline keeps a stalled client from blocking the next
	private static void answer(Socket client) throws IOException
	{
		BridgeHttp.Request request = BridgeHttp.read(new BufferedInputStream(client.getInputStream()),
				System.currentTimeMillis() + TIMEOUT_MS);

		if (request == null)
		{
			return;
		}

		boolean enabled = Settings.flag(SettingsKeys.WEB_OPEN, true) && RemoteContent.feature("openInGame");
		boolean inWorld = Minecraft.getInstance().level != null;
		BridgeHttp.Response response = BridgeHttp.respond(request, allowedOrigins(), enabled, pingJson(), inWorld);

		if (response.postId() != null && !takeOpenSlot())
		{
			response = BridgeHttp.error(429, "rate_limited").withOrigin(response.allowOrigin());
		}

		SchematicIndexMod.LOGGER.debug("Local bridge {} {} -> {}", request.method(), request.path(), response.status());
		BridgeHttp.write(client.getOutputStream(), response);

		if (response.postId() != null)
		{
			open(response.postId(), inWorld);
		}
	}

	private static synchronized boolean takeOpenSlot()
	{
		long now = System.currentTimeMillis();

		if (now - lastOpenAt < OPEN_INTERVAL_MS)
		{
			return false;
		}

		lastOpenAt = now;
		return true;
	}

	private static Set<String> allowedOrigins()
	{
		Set<String> origins = new HashSet<>();
		origins.add(SITE_ORIGIN);
		String dev = Settings.text(SettingsKeys.BRIDGE_DEV_ORIGIN, "").trim();

		if (!dev.isEmpty())
		{
			origins.add(dev);
		}

		return origins;
	}

	private static String pingJson()
	{
		JsonObject ping = new JsonObject();
		ping.addProperty("app", "schematicindex");
		ping.addProperty("version", SchematicIndexMod.currentVersion());
		ping.addProperty("mc", FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(""));
		return ping.toString();
	}

	private static void open(String postId, boolean inWorld)
	{
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			Usage.once("open_in_game");
			GLFW.glfwRequestWindowAttention(mc.getWindow().handle());
			Screen current = Keybinds.currentScreen();

			if (current instanceof IndexScreen index)
			{
				index.openPostById(postId);
				return;
			}

			IndexScreen.queueOpenPost(postId);

			if (!inWorld || mc.level == null)
			{
				mc.setScreenAndShow(new IndexScreen(current));
				return;
			}

			announce(postId);
		});
	}

	// In a world the Index waits for the player; the toast names the build and the key that opens it
	private static void announce(String postId)
	{
		Net.submit(() -> {
			Backend.ApiResult result = Backend.getForApiResult("/post/" + Backend.encode(postId));
			JsonObject body = result.body();
			JsonObject post = body != null && body.has("post") && body.get("post").isJsonObject() ? body.getAsJsonObject("post") : body;
			String title = result.ok() ? Json.stringOf(post, "title", "") : "";

			Minecraft.getInstance().execute(() -> {
				if (result.ok() && title != null && !title.isBlank())
				{
					String name = title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX - 3) + "..." : title;
					Toasts.push(name + " is ready", "Press " + Keybinds.openKeyName() + " to open it.",
							new ItemStack(Items.SPYGLASS));
					return;
				}

				SchematicIndexMod.LOGGER.debug("Bridge open {} failed with status {}", postId, result.status());
				boolean gone = result.status() == 404;

				if (gone)
				{
					IndexScreen.queueOpenPost(null);
				}

				String text = gone ? "That build isn't in the catalogue any more. (" + Errors.OPEN_NOT_FOUND + ")"
						: "Couldn't load that build. Try again in a moment. (" + Errors.OPEN_FAILED + ")";
				Toasts.push("Couldn't open build", text, new ItemStack(Items.BARRIER));
			});
		});
	}

	private static void closeQuietly(@Nullable ServerSocket socket)
	{
		if (socket == null)
		{
			return;
		}

		try
		{
			socket.close();
		}
		catch (IOException e)
		{
			SchematicIndexMod.LOGGER.debug("Local bridge socket close failed", e);
		}
	}
}

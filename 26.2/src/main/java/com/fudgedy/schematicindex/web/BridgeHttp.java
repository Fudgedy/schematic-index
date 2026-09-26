package com.fudgedy.schematicindex.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// The wire half of the local bridge: no game state, so every rule here can be exercised on its own
public final class BridgeHttp
{
	public static final int HEAD_LIMIT = 8192;
	public static final int BODY_LIMIT = 1024;
	private static final Pattern POST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

	private BridgeHttp()
	{
	}

	public record Request(String method, String path, Map<String, String> headers, byte[] body, boolean bodyTooLarge)
	{
		public @Nullable String header(String name)
		{
			return this.headers.get(name.toLowerCase(Locale.ROOT));
		}
	}

	// postId is set only for an open that passed every check but the rate limit, which needs the clock
	public record Response(int status, String json, @Nullable String allowOrigin, @Nullable String postId)
	{
		public Response withOrigin(@Nullable String origin)
		{
			return new Response(this.status, this.json, origin, this.postId);
		}
	}

	// Null for a connection that never produced a request worth answering: oversized head, garbage, timeout
	public static @Nullable Request read(InputStream in, long deadline) throws IOException
	{
		byte[] head = readHead(in, deadline);

		if (head == null)
		{
			return null;
		}

		String[] lines = new String(head, StandardCharsets.ISO_8859_1).split("\r\n");
		String[] requestLine = lines[0].split(" ");

		if (requestLine.length != 3 || !requestLine[2].startsWith("HTTP/1."))
		{
			return null;
		}

		Map<String, String> headers = new HashMap<>();

		for (int i = 1; i < lines.length; i++)
		{
			int colon = lines[i].indexOf(':');

			if (colon <= 0)
			{
				continue;
			}

			headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
		}

		String path = requestLine[1];
		int query = path.indexOf('?');
		path = query >= 0 ? path.substring(0, query) : path;
		long length = contentLength(headers.get("content-length"));

		if (length < 0L)
		{
			return null;
		}

		if (length > BODY_LIMIT)
		{
			return new Request(requestLine[0], path, headers, new byte[0], true);
		}

		byte[] body = readBody(in, (int) length, deadline);
		return body == null ? null : new Request(requestLine[0], path, headers, body, false);
	}

	public static Response respond(Request request, Set<String> allowedOrigins, boolean enabled, String pingJson,
			boolean inWorld)
	{
		String origin = request.header("origin");

		if (origin == null || !allowedOrigins.contains(origin))
		{
			return error(403, "origin");
		}

		return route(request, enabled, pingJson, inWorld).withOrigin(origin);
	}

	public static Response openedResponse(String postId, boolean inWorld)
	{
		return new Response(202, "{\"status\":\"" + (inWorld ? "queued" : "opened") + "\"}", null, postId);
	}

	public static Response error(int status, String code)
	{
		return new Response(status, "{\"error\":\"" + code + "\"}", null, null);
	}

	public static void write(OutputStream out, Response response) throws IOException
	{
		byte[] body = response.status() == 204 ? new byte[0] : response.json().getBytes(StandardCharsets.UTF_8);
		StringBuilder head = new StringBuilder();
		head.append("HTTP/1.1 ").append(response.status()).append(' ').append(reason(response.status())).append("\r\n");

		if (response.allowOrigin() != null)
		{
			head.append("Access-Control-Allow-Origin: ").append(response.allowOrigin()).append("\r\n");
		}

		head.append("Vary: Origin\r\n")
				.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
				.append("Access-Control-Allow-Headers: Content-Type\r\n")
				.append("Access-Control-Allow-Private-Network: true\r\n")
				.append("Access-Control-Max-Age: 600\r\n")
				.append("Content-Type: application/json\r\n")
				.append("Cache-Control: no-store\r\n")
				.append("Connection: close\r\n")
				.append("Content-Length: ").append(body.length).append("\r\n\r\n");
		out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
		out.write(body);
		out.flush();
	}

	private static Response route(Request request, boolean enabled, String pingJson, boolean inWorld)
	{
		if (request.method().equals("OPTIONS"))
		{
			return new Response(204, "", null, null);
		}

		if (!enabled)
		{
			return error(503, "disabled");
		}

		if (request.method().equals("GET") && request.path().equals("/v1/ping"))
		{
			return new Response(200, pingJson, null, null);
		}

		if (!request.method().equals("POST") || !request.path().equals("/v1/open"))
		{
			return error(404, "not_found");
		}

		if (request.bodyTooLarge())
		{
			return error(413, "too_large");
		}

		String postId = postIdOf(request.body());
		return postId == null ? error(400, "bad_post") : openedResponse(postId, inWorld);
	}

	private static @Nullable String postIdOf(byte[] body)
	{
		try
		{
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));

			if (!parsed.isJsonObject())
			{
				return null;
			}

			JsonObject object = parsed.getAsJsonObject();
			JsonElement post = object.get("post");

			if (post == null || !post.isJsonPrimitive() || !post.getAsJsonPrimitive().isString())
			{
				return null;
			}

			String id = post.getAsString();
			return POST_ID.matcher(id).matches() ? id : null;
		}
		catch (Exception e)
		{
			return null;
		}
	}

	// The socket timeout bounds each read, the deadline the whole head, so a trickling client cannot hold the thread
	private static byte[] readHead(InputStream in, long deadline) throws IOException
	{
		ByteArrayOutputStream head = new ByteArrayOutputStream();
		int matched = 0;

		while (head.size() < HEAD_LIMIT)
		{
			if (System.currentTimeMillis() > deadline)
			{
				return null;
			}

			int value = readByte(in);

			if (value < 0)
			{
				return null;
			}

			head.write(value);
			matched = value == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : (value == '\r' ? 1 : 0);

			if (matched == 4)
			{
				byte[] bytes = head.toByteArray();
				return Arrays.copyOf(bytes, bytes.length - 4);
			}
		}

		return null;
	}

	private static byte[] readBody(InputStream in, int length, long deadline) throws IOException
	{
		byte[] body = new byte[length];
		int read = 0;

		while (read < length)
		{
			if (System.currentTimeMillis() > deadline)
			{
				return null;
			}

			int count;

			try
			{
				count = in.read(body, read, length - read);
			}
			catch (SocketTimeoutException e)
			{
				return null;
			}

			if (count < 0)
			{
				return null;
			}

			read += count;
		}

		return body;
	}

	private static int readByte(InputStream in) throws IOException
	{
		try
		{
			return in.read();
		}
		catch (SocketTimeoutException e)
		{
			return -1;
		}
	}

	private static long contentLength(@Nullable String value)
	{
		if (value == null)
		{
			return 0L;
		}

		try
		{
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException e)
		{
			return -1L;
		}
	}

	private static String reason(int status)
	{
		return switch (status)
		{
			case 200 -> "OK";
			case 202 -> "Accepted";
			case 204 -> "No Content";
			case 400 -> "Bad Request";
			case 403 -> "Forbidden";
			case 404 -> "Not Found";
			case 413 -> "Payload Too Large";
			case 429 -> "Too Many Requests";
			default -> "Service Unavailable";
		};
	}
}

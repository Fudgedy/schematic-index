package com.fudgedy.schematicindex.rpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// One connection to the local Discord client; every frame is a little-endian opcode and length, then UTF-8 JSON
final class DiscordIpc implements Closeable
{
	private static final int HANDSHAKE = 0;
	private static final int FRAME = 1;
	private static final int CLOSE = 2;
	private static final int PING = 3;
	private static final int PONG = 4;
	private static final int SLOTS = 10;
	private static final int MAX_FRAME = 64 * 1024;
	// Answers to a request can trail pings and dispatches, but never this many
	private static final int MAX_SKIPPED = 16;
	private static final String[] UNIX_ROOTS = {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
	private static final String[] UNIX_SUBDIRS = {"", "app/com.discordapp.Discord", "snap.discord"};

	private final Transport transport;

	private DiscordIpc(Transport transport)
	{
		this.transport = transport;
	}

	static DiscordIpc open(String clientId) throws IOException
	{
		DiscordIpc ipc = new DiscordIpc(connect());

		try
		{
			ipc.handshake(clientId);
			return ipc;
		}
		catch (IOException e)
		{
			ipc.close();
			throw e;
		}
	}

	JsonObject request(String cmd, JsonObject args) throws IOException
	{
		String nonce = UUID.randomUUID().toString();
		JsonObject payload = new JsonObject();
		payload.addProperty("cmd", cmd);
		payload.add("args", args);
		payload.addProperty("nonce", nonce);
		this.write(FRAME, payload);

		for (int i = 0; i < MAX_SKIPPED; i++)
		{
			JsonObject reply = this.readReply();

			if (reply == null || !nonce.equals(stringOf(reply, "nonce")))
			{
				continue;
			}

			if ("ERROR".equals(stringOf(reply, "evt")))
			{
				throw new ProtocolException(cmd + " refused: " + reply.get("data"));
			}

			return reply;
		}

		throw new ProtocolException("No answer to " + cmd);
	}

	@Override
	public void close()
	{
		try
		{
			this.transport.close();
		}
		catch (IOException ignored)
		{
			// Already broken; nothing is left to release
		}
	}

	private void handshake(String clientId) throws IOException
	{
		JsonObject hello = new JsonObject();
		hello.addProperty("v", 1);
		hello.addProperty("client_id", clientId);
		this.write(HANDSHAKE, hello);

		for (int i = 0; i < MAX_SKIPPED; i++)
		{
			JsonObject reply = this.readReply();

			if (reply != null && "READY".equals(stringOf(reply, "evt")))
			{
				return;
			}
		}

		throw new ProtocolException("No READY after the handshake");
	}

	// Null for a frame that is not an answer (a ping already ponged); a close frame throws
	private @Nullable JsonObject readReply() throws IOException
	{
		byte[] header = new byte[8];
		this.transport.readFully(header);
		ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
		int opcode = buffer.getInt();
		int length = buffer.getInt();

		if (length < 0 || length > MAX_FRAME)
		{
			throw new ProtocolException("Frame length " + length);
		}

		byte[] body = new byte[length];
		this.transport.readFully(body);
		String json = new String(body, StandardCharsets.UTF_8);

		if (opcode == CLOSE)
		{
			throw new ProtocolException("Discord closed the connection: " + json);
		}

		if (opcode == PING)
		{
			this.writeRaw(PONG, body);
			return null;
		}

		if (opcode != FRAME)
		{
			return null;
		}

		JsonElement parsed = parse(json);
		return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
	}

	private void write(int opcode, JsonObject payload) throws IOException
	{
		this.writeRaw(opcode, payload.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void writeRaw(int opcode, byte[] body) throws IOException
	{
		ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		frame.putInt(opcode);
		frame.putInt(body.length);
		frame.put(body);
		this.transport.write(frame.array());
	}

	private static Transport connect() throws IOException
	{
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		return windows ? connectPipe() : connectSocket();
	}

	private static Transport connectPipe() throws IOException
	{
		for (int i = 0; i < SLOTS; i++)
		{
			try
			{
				return new PipeTransport(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
			}
			catch (FileNotFoundException ignored)
			{
				// Discord takes the first free slot, so a gap only means another client owns it
			}
		}

		throw new IOException("No Discord pipe open");
	}

	private static Transport connectSocket() throws IOException
	{
		for (Path candidate : socketCandidates())
		{
			if (!Files.exists(candidate))
			{
				continue;
			}

			SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);

			try
			{
				channel.connect(UnixDomainSocketAddress.of(candidate));
				return new SocketTransport(channel);
			}
			catch (IOException e)
			{
				channel.close();
			}
		}

		throw new IOException("No Discord socket open");
	}

	private static List<Path> socketCandidates()
	{
		List<String> roots = new ArrayList<>();

		for (String name : UNIX_ROOTS)
		{
			String value = System.getenv(name);

			if (value != null && !value.isBlank() && !roots.contains(value))
			{
				roots.add(value);
			}
		}

		roots.add("/tmp");
		List<Path> candidates = new ArrayList<>();

		for (String root : roots)
		{
			for (String subdir : UNIX_SUBDIRS)
			{
				for (int i = 0; i < SLOTS; i++)
				{
					candidates.add(Path.of(root, subdir, "discord-ipc-" + i));
				}
			}
		}

		return candidates;
	}

	private static @Nullable JsonElement parse(String json)
	{
		try
		{
			return JsonParser.parseString(json);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static @Nullable String stringOf(JsonObject o, String key)
	{
		JsonElement value = o.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	// Discord answered, but not with what the protocol promises; kept apart from a failure to connect
	static final class ProtocolException extends IOException
	{
		ProtocolException(String message)
		{
			super(message);
		}
	}

	private interface Transport extends Closeable
	{
		void write(byte[] bytes) throws IOException;

		void readFully(byte[] into) throws IOException;
	}

	private record PipeTransport(RandomAccessFile file) implements Transport
	{
		@Override
		public void write(byte[] bytes) throws IOException
		{
			this.file.write(bytes);
		}

		@Override
		public void readFully(byte[] into) throws IOException
		{
			this.file.readFully(into);
		}

		@Override
		public void close() throws IOException
		{
			this.file.close();
		}
	}

	private record SocketTransport(SocketChannel channel) implements Transport
	{
		@Override
		public void write(byte[] bytes) throws IOException
		{
			ByteBuffer buffer = ByteBuffer.wrap(bytes);

			while (buffer.hasRemaining())
			{
				this.channel.write(buffer);
			}
		}

		@Override
		public void readFully(byte[] into) throws IOException
		{
			ByteBuffer buffer = ByteBuffer.wrap(into);

			while (buffer.hasRemaining())
			{
				if (this.channel.read(buffer) < 0)
				{
					throw new EOFException("Discord socket closed");
				}
			}
		}

		@Override
		public void close() throws IOException
		{
			this.channel.close();
		}
	}
}

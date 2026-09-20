package com.fudgedy.schematicindex.catalogue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

// One client, one pool and one timer for the whole mod, all daemon-backed so none keeps the game alive
public final class Net
{
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.build();

	private static final Executor EXECUTOR = Executors.newCachedThreadPool(daemonFactory("schematicindex-net-"));
	// A tick that may block on the network hands the work to submit() so it cannot hold up the other timers
	private static final ScheduledExecutorService SCHEDULER =
			Executors.newScheduledThreadPool(2, daemonFactory("schematicindex-timer-"));

	private Net()
	{
	}

	public static HttpClient client()
	{
		return CLIENT;
	}

	public static void submit(Runnable task)
	{
		EXECUTOR.execute(task);
	}

	public static ScheduledExecutorService scheduler()
	{
		return SCHEDULER;
	}

	private static ThreadFactory daemonFactory(String prefix)
	{
		return new ThreadFactory()
		{
			private final AtomicInteger counter = new AtomicInteger();

			@Override
			public Thread newThread(Runnable runnable)
			{
				Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}
		};
	}
}

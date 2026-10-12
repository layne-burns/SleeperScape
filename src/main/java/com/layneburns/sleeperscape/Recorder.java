package com.layneburns.sleeperscape;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Saves every response as one gzip JSON line ({@code t, url, code, body}) so a game day can be replayed and
 * tested later. A body identical to the last one from the same URL is written as {@code same: true} to keep
 * the file small; the (huge, cached) player list is skipped. The gzip and the write happen on one writer
 * thread of their own, never on the OkHttp pool that delivered the body. One file per day (and per start);
 * once a day's files reach {@link #DAY_CAP_BYTES} nothing more is written until the next day, so a toggle
 * left on can't fill the disk.
 */
@Slf4j
class Recorder
{
	interface Folder
	{
		Filepath get() throws IOException;
	}

	/** Compressed bytes per day, over every file of that day. A live Sunday runs to a few hundred MB. */
	static final long DAY_CAP_BYTES = 1024L * 1024 * 1024;
	/** Bodies waiting for the writer; past this they are dropped rather than piled up in memory. */
	private static final int MAX_QUEUED = 16;
	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat FILE = new SimpleDateFormat("yyyy-MM-dd_HHmmss");

	/** Counts what reaches the file, after gzip. */
	private static final class Counting extends FilterOutputStream
	{
		long count;

		Counting(OutputStream out)
		{
			super(out);
		}

		@Override
		public void write(int b) throws IOException
		{
			out.write(b);
			count++;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException
		{
			out.write(b, off, len);
			count += len;
		}
	}

	private final Gson gson;
	private final Folder folder;
	private final BooleanSupplier enabled;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "sleeperscape-recorder");
		t.setDaemon(true);
		return t;
	});
	private final AtomicInteger queued = new AtomicInteger();
	private volatile boolean failed;

	// Touched on the writer thread only.
	private final Map<String, Integer> lastHash = new HashMap<>();
	private Writer out;
	private Counting counting;
	private String day;
	private long dayBytes;
	private boolean capped;

	Recorder(Gson gson, Folder folder, BooleanSupplier enabled)
	{
		this.gson = gson;
		this.folder = folder;
		this.enabled = enabled;
	}

	/** Called on the OkHttp threads: queues the body for the writer and returns at once. */
	void record(String url, int code, String body)
	{
		if (!enabled.getAsBoolean() || failed || url.endsWith("/players/nfl"))
		{
			return;
		}
		if (queued.incrementAndGet() > MAX_QUEUED)
		{
			queued.decrementAndGet();
			log.debug("recorder behind; dropping {}", url);
			return;
		}
		long t = System.currentTimeMillis();
		try
		{
			writer.execute(() ->
			{
				try
				{
					write(t, url, code, body);
				}
				finally
				{
					queued.decrementAndGet();
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			queued.decrementAndGet();
		}
	}

	private void write(long t, String url, int code, String body)
	{
		if (failed)
		{
			return;
		}
		try
		{
			String today = DAY.format(new Date(t));
			if (!today.equals(day))
			{
				closeFile();
				day = today;
				capped = false;
				dayBytes = 0;
			}
			if (capped)
			{
				return;
			}
			if (out == null)
			{
				openFile(t, today);
				if (capped)
				{
					return;
				}
			}
			int hash = body == null ? 0 : body.hashCode();
			Integer previous = lastHash.put(url, hash);
			// Streamed straight into the gzip: no second copy of a multi-MB body as an escaped string.
			JsonWriter w = gson.newJsonWriter(out);
			w.beginObject().name("t").value(t).name("url").value(url).name("code").value(code);
			if (body != null && previous != null && previous == hash)
			{
				w.name("same").value(true);
			}
			else
			{
				w.name("body").value(body);
			}
			w.endObject();
			w.flush();
			out.write('\n');
			out.flush();
			if (dayBytes + counting.count >= DAY_CAP_BYTES)
			{
				log.info("recordings for {} reached {} MB; recording stops until tomorrow", day, DAY_CAP_BYTES >> 20);
				capped = true;
				closeFile();
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("recording failed; stopping it for this session", e);
			failed = true;
		}
	}

	private void openFile(long t, String today) throws IOException
	{
		Filepath dir = folder.get();
		dir.createDirectories();
		// Earlier files of the day (a restart) count against the cap too.
		dayBytes = 0;
		try (Stream<Filepath> files = dir.walk(1))
		{
			for (Filepath f : (Iterable<Filepath>) files::iterator)
			{
				if (f.isFile() && f.getFileName().startsWith(today))
				{
					dayBytes += f.size();
				}
			}
		}
		if (dayBytes >= DAY_CAP_BYTES)
		{
			log.info("recordings for {} already at {} MB; not recording today", today, dayBytes >> 20);
			capped = true;
			return;
		}
		String name = FILE.format(new Date(t)) + ".jsonl.gz";
		counting = new Counting(dir.join(name).openOutputStream(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
		out = new OutputStreamWriter(new GZIPOutputStream(counting, true), StandardCharsets.UTF_8);
		lastHash.clear();
		log.debug("recording to {}", name);
	}

	private void closeFile()
	{
		if (out != null)
		{
			try
			{
				out.close();
			}
			catch (IOException e)
			{
				log.debug("closing recording", e);
			}
			out = null;
			counting = null;
		}
	}

	/** Finishes the file on the writer thread and lets it exit; never blocks the caller. */
	void close()
	{
		try
		{
			writer.execute(this::closeFile);
		}
		catch (RejectedExecutionException ignored)
		{
			// Already closed.
		}
		writer.shutdown();
	}
}

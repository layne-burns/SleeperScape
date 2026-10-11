package com.layneburns.sleeperscape;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Saves every response as one gzip JSON line ({@code t, url, code, body}) so a game day can be replayed and
 * tested later. A body identical to the last one from the same URL is written as {@code same: true} to keep
 * the file small; the (huge, cached) player list is skipped.
 */
@Slf4j
class Recorder
{
	interface Folder
	{
		Filepath get() throws IOException;
	}

	private final Gson gson;
	private final Folder folder;
	private final BooleanSupplier enabled;
	private final Map<String, Integer> lastHash = new HashMap<>();
	private Writer out;
	private boolean failed;

	Recorder(Gson gson, Folder folder, BooleanSupplier enabled)
	{
		this.gson = gson;
		this.folder = folder;
		this.enabled = enabled;
	}

	synchronized void record(String url, int code, String body)
	{
		if (!enabled.getAsBoolean() || failed || url.endsWith("/players/nfl"))
		{
			return;
		}
		try
		{
			if (out == null)
			{
				Filepath dir = folder.get();
				dir.createDirectories();
				String name = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + ".jsonl.gz";
				OutputStream file = dir.join(name).openOutputStream(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				out = new OutputStreamWriter(new GZIPOutputStream(file, true), StandardCharsets.UTF_8);
				log.debug("recording to {}", name);
			}
			Map<String, Object> line = new LinkedHashMap<>();
			line.put("t", System.currentTimeMillis());
			line.put("url", url);
			line.put("code", code);
			int hash = body == null ? 0 : body.hashCode();
			Integer previous = lastHash.put(url, hash);
			if (body != null && previous != null && previous == hash)
			{
				line.put("same", true);
			}
			else
			{
				line.put("body", body);
			}
			out.write(gson.toJson(line));
			out.write('\n');
			out.flush();
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("recording failed; stopping it for this session", e);
			failed = true;
		}
	}

	synchronized void close()
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
		}
	}
}

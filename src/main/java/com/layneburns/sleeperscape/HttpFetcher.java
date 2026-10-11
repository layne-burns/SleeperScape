package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Fetcher;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** The injected OkHttpClient, enqueued (never on the client thread), with every body offered to the recorder. */
class HttpFetcher implements Fetcher
{
	private final OkHttpClient http;
	private final Recorder recorder;

	HttpFetcher(OkHttpClient http, Recorder recorder)
	{
		this.http = http;
		this.recorder = recorder;
	}

	@Override
	public CompletableFuture<String> get(String url)
	{
		CompletableFuture<String> future = new CompletableFuture<>();
		Request request = new Request.Builder().url(url).header("Accept", "application/json").build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				recorder.record(url, -1, null);
				future.completeExceptionally(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					String text = body == null ? "" : body.string();
					recorder.record(url, response.code(), text);
					if (!response.isSuccessful())
					{
						future.completeExceptionally(new IOException("HTTP " + response.code() + " from " + url));
						return;
					}
					future.complete(text);
				}
				catch (IOException | RuntimeException e)
				{
					future.completeExceptionally(e);
				}
			}
		});
		return future;
	}
}

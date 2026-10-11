package com.layneburns.sleeperscape.core;

import java.util.concurrent.CompletableFuture;

/** GETs a URL and completes with the body, or exceptionally on a network error or non-2xx status. */
public interface Fetcher
{
	CompletableFuture<String> get(String url);
}

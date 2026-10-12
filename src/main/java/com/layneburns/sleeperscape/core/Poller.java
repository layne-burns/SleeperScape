package com.layneburns.sleeperscape.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Drives the network side: discovers leagues once, then polls each cycle and feeds the {@link Engine}.
 * Everything runs on the single engine executor; HTTP completes on the fetcher's threads and hops back.
 */
public final class Poller
{
	public interface Config
	{
		String username();

		List<String> extraLeagueIds();

		int pollSeconds();

		boolean useEspn();

		int weekOverride();
	}

	/** Disk cache for the (large) Sleeper player list. */
	public interface PlayersCache
	{
		/** The cached JSON if younger than maxAgeMs, else null. */
		String read(long maxAgeMs);

		void write(String json);
	}

	public interface Status
	{
		void status(String text);

		void leaguesLoaded(List<LeagueContext> leagues);

		void view(View view);

		/** The NFL week (or season) changed under a running poller: restart polling from discovery. */
		void weekChanged(int week);
	}

	static final String BASE = "https://api.sleeper.app/v1/";
	private static final long PLAYERS_MAX_AGE_MS = 24 * 3600_000L;
	private static final int IDLE_POLL_SECONDS = 60;
	private static final long RETRY_INIT_SECONDS = 60;
	private static final long PROJECTIONS_EVERY_MS = 10 * 60_000L;
	/** Projections only move the win % while games are on; between game days a refresh this often will do. */
	private static final long PROJECTIONS_IDLE_MS = 6 * 3600_000L;
	private static final long ROSTERS_EVERY_MS = 30 * 60_000L;
	/** How often to re-read the NFL state (SPEC: hourly), so a client left open picks up the next week. */
	private static final long STATE_EVERY_MS = 3600_000L;
	/** After a stats URL fails, how long to stay on the next one before trying the preferred one again. */
	private static final long STAT_URL_RETRY_MS = 5 * 60_000L;
	// All three answer (confirmed 2026-10-11), but during live games the lean v1 one (~270 KB keyed by player)
	// left almost every play without a stat line, so it looks cached; the ~1.3 MB arrays kept up. Fresh first.
	private static final String[] STAT_URLS = {
		"https://api.sleeper.com/stats/nfl/%s/%d?season_type=%s",
		"https://api.sleeper.app/stats/nfl/%s/%d?season_type=%s",
		"https://api.sleeper.app/v1/stats/nfl/%3$s/%1$s/%2$d",
	};
	private static final String[] PROJECTION_URLS = {
		"https://api.sleeper.com/projections/nfl/%s/%d?season_type=%s",
		"https://api.sleeper.app/projections/nfl/%s/%d?season_type=%s",
		"https://api.sleeper.app/v1/projections/nfl/%3$s/%1$s/%2$d",
	};
	private static final Type PLAYERS = new TypeToken<Map<String, Sleeper.Player>>()
	{
	}.getType();

	private final Fetcher http;
	private final Gson gson;
	private final ScheduledExecutorService exec;
	private final Engine engine;
	private final Config config;
	private final PlayersCache playersCache;
	private final Status status;

	private final List<ScheduledFuture<?>> scheduled = new ArrayList<>();
	private volatile boolean stopped;
	private String season;
	private String seasonType = "regular";
	private int week;
	private String userId;
	private int statUrl;
	private long statUrlFailedAt;
	private int projectionUrl;
	private long projectionsAt;
	private long rostersAt;
	private long stateAt;

	public Poller(Fetcher http, Gson gson, ScheduledExecutorService exec, Engine engine, Config config,
		PlayersCache playersCache, Status status)
	{
		this.http = http;
		this.gson = gson;
		this.exec = exec;
		this.engine = engine;
		this.config = config;
		this.playersCache = playersCache;
		this.status = status;
	}

	public void start()
	{
		exec.execute(this::init);
		schedule(exec.scheduleAtFixedRate(this::tick, 250, 250, TimeUnit.MILLISECONDS));
	}

	public void stop()
	{
		stopped = true;
		synchronized (scheduled)
		{
			for (ScheduledFuture<?> f : scheduled)
			{
				f.cancel(false);
			}
			scheduled.clear();
		}
	}

	private void schedule(ScheduledFuture<?> f)
	{
		synchronized (scheduled)
		{
			scheduled.removeIf(ScheduledFuture::isDone);
			scheduled.add(f);
		}
	}

	private void later(Runnable r, long delay, TimeUnit unit)
	{
		if (!stopped)
		{
			schedule(exec.schedule(r, delay, unit));
		}
	}

	private void tick()
	{
		try
		{
			status.view(engine.tick(System.currentTimeMillis()));
		}
		catch (RuntimeException e)
		{
			// A bug in one tick must not kill the scheduled task (which would freeze everything).
			status.status("Internal error: " + e);
		}
	}

	// ---- discovery ----

	private void init()
	{
		String username = config.username() == null ? "" : config.username().trim();
		if (username.isEmpty() && config.extraLeagueIds().isEmpty())
		{
			status.status("Set your Sleeper username in the plugin config.");
			return;
		}
		status.status("Loading Sleeper…");
		get(BASE + "state/nfl", Sleeper.State.class)
			.thenCompose(state ->
			{
				season = state.league_season != null ? state.league_season : state.season;
				seasonType = state.season_type == null || state.season_type.isEmpty() ? "regular" : state.season_type;
				week = config.weekOverride() > 0 ? config.weekOverride() : Math.max(1, state.week);
				if (username.isEmpty())
				{
					return CompletableFuture.completedFuture((Sleeper.User) null);
				}
				return get(BASE + "user/" + enc(username), Sleeper.User.class);
			})
			.thenCompose(user ->
			{
				if (!username.isEmpty() && (user == null || user.user_id == null))
				{
					throw new IllegalStateException("No Sleeper user named \"" + username + "\"");
				}
				userId = user == null ? null : user.user_id;
				CompletableFuture<List<Sleeper.League>> mine = userId == null
					? CompletableFuture.completedFuture(new ArrayList<>())
					: get(BASE + "user/" + userId + "/leagues/nfl/" + season, new TypeToken<List<Sleeper.League>>()
				{
				}.getType());
				return mine.thenCompose(this::addExtraLeagues);
			})
			.thenCompose(this::loadContexts)
			.thenCompose(contexts -> loadPlayers().thenApply(p ->
			{
				engine.setPlayers(p);
				engine.setLeagues(contexts);
				return contexts;
			}))
			.whenCompleteAsync((contexts, err) ->
			{
				if (stopped)
				{
					return;
				}
				if (err != null)
				{
					status.status("Couldn't load Sleeper (" + message(err) + "); retrying in a minute.");
					later(this::init, RETRY_INIT_SECONDS, TimeUnit.SECONDS);
					return;
				}
				rostersAt = System.currentTimeMillis();
				stateAt = rostersAt;
				status.leaguesLoaded(contexts);
				status.status(contexts.size() + " leagues, week " + week);
				cycle();
			}, exec);
	}

	private CompletableFuture<List<Sleeper.League>> addExtraLeagues(List<Sleeper.League> mine)
	{
		List<CompletableFuture<Sleeper.League>> extra = new ArrayList<>();
		for (String id : config.extraLeagueIds())
		{
			boolean have = mine.stream().anyMatch(l -> id.equals(l.league_id));
			if (!have)
			{
				extra.add(get(BASE + "league/" + enc(id), Sleeper.League.class));
			}
		}
		return CompletableFuture.allOf(extra.toArray(new CompletableFuture[0])).thenApply(v ->
		{
			List<Sleeper.League> all = new ArrayList<>(mine);
			for (CompletableFuture<Sleeper.League> f : extra)
			{
				Sleeper.League l = f.join();
				if (l != null && l.league_id != null)
				{
					all.add(l);
				}
			}
			return all;
		});
	}

	private CompletableFuture<List<LeagueContext>> loadContexts(List<Sleeper.League> leagues)
	{
		List<CompletableFuture<LeagueContext>> futures = new ArrayList<>();
		for (Sleeper.League l : leagues)
		{
			CompletableFuture<List<Sleeper.Roster>> rosters = get(BASE + "league/" + l.league_id + "/rosters",
				new TypeToken<List<Sleeper.Roster>>()
				{
				}.getType());
			CompletableFuture<List<Sleeper.LeagueUser>> users = get(BASE + "league/" + l.league_id + "/users",
				new TypeToken<List<Sleeper.LeagueUser>>()
				{
				}.getType());
			futures.add(rosters.thenCombine(users, (r, u) -> new LeagueContext(l, r, u, userId)));
		}
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v ->
		{
			List<LeagueContext> out = new ArrayList<>();
			for (CompletableFuture<LeagueContext> f : futures)
			{
				out.add(f.join());
			}
			return out;
		});
	}

	private CompletableFuture<Map<String, Sleeper.Player>> loadPlayers()
	{
		String cached = playersCache.read(PLAYERS_MAX_AGE_MS);
		if (cached != null)
		{
			try
			{
				Map<String, Sleeper.Player> p = gson.fromJson(cached, PLAYERS);
				if (p != null && !p.isEmpty())
				{
					return CompletableFuture.completedFuture(p);
				}
			}
			catch (RuntimeException ignored)
			{
				// Corrupt cache: fall through and download again.
			}
		}
		status.status("Downloading the NFL player list (once a day)…");
		return http.get(BASE + "players/nfl").thenApplyAsync(json ->
		{
			Map<String, Sleeper.Player> p = gson.fromJson(json, PLAYERS);
			playersCache.write(json);
			return p;
		}, exec);
	}

	private void refreshRosters()
	{
		List<Sleeper.League> leagues = new ArrayList<>();
		for (LeagueContext c : engine.leagues())
		{
			leagues.add(c.league);
		}
		loadContexts(leagues).whenCompleteAsync((contexts, err) ->
		{
			if (err == null && !stopped)
			{
				engine.setLeagues(contexts);
			}
		}, exec);
	}

	// ---- polling ----

	private void cycle()
	{
		if (stopped)
		{
			return;
		}
		long now = System.currentTimeMillis();
		// Live or idle, from the previous cycle's data. Idle cycles (no game on) fetch only what can change:
		// the matchups, and the ESPN scoreboard that says when a game starts. The ~1.3 MB stats array waits
		// for the first point change, and its stat lines land within the detail grace of that first play.
		boolean live = engine.anyGameLive(now);
		if (now - rostersAt > ROSTERS_EVERY_MS)
		{
			rostersAt = now;
			refreshRosters();
		}
		if (now - stateAt > STATE_EVERY_MS)
		{
			stateAt = now;
			refreshState();
		}
		if (statUrl != 0 && now - statUrlFailedAt > STAT_URL_RETRY_MS)
		{
			// A failure moved us to a fallback (the last one lags the live scores): go back to the preferred one.
			statUrl = 0;
		}

		Engine.Cycle c = new Engine.Cycle(now);
		List<CompletableFuture<?>> parts = new ArrayList<>();
		for (LeagueContext l : engine.leagues())
		{
			CompletableFuture<List<Sleeper.Matchup>> f = this.<List<Sleeper.Matchup>>get(
				BASE + "league/" + l.id() + "/matchups/" + week, new TypeToken<List<Sleeper.Matchup>>()
				{
				}.getType());
			parts.add(f.handleAsync((m, err) ->
			{
				if (err == null && m != null)
				{
					c.matchups.put(l.id(), m);
				}
				return null;
			}, exec));
		}
		if (live)
		{
			String statsUrl = String.format(STAT_URLS[statUrl], season, week, seasonType);
			parts.add(http.get(statsUrl).handleAsync((json, err) ->
			{
				if (err != null)
				{
					nextStatUrl();
					return null;
				}
				try
				{
					c.stats = Stats.parse(json, engine.statFilter());
				}
				catch (RuntimeException e)
				{
					nextStatUrl();
				}
				return null;
			}, exec));
		}
		if (config.useEspn())
		{
			parts.add(http.get(Espn.SCOREBOARD).handleAsync((json, err) ->
			{
				if (err == null)
				{
					try
					{
						c.games = Espn.parseScoreboard(gson, json);
					}
					catch (RuntimeException ignored)
					{
						// Leave games null this cycle.
					}
				}
				return null;
			}, exec));
		}
		CompletableFuture.allOf(parts.toArray(new CompletableFuture[0])).whenCompleteAsync((v, err) ->
		{
			if (stopped)
			{
				return;
			}
			try
			{
				if (c.matchups.isEmpty() && !engine.leagues().isEmpty())
				{
					status.status("Sleeper didn't answer; retrying.");
				}
				else
				{
					status.status(null);
				}
				engine.ingest(c);
				if (config.useEspn())
				{
					fetchPlays(now);
				}
				// After the ingest, so the first fetch already knows which players' projections to keep; a roster
				// change that adds a player refreshes early (at most once a minute).
				long projectionsEvery = live ? PROJECTIONS_EVERY_MS : PROJECTIONS_IDLE_MS;
				if (now - projectionsAt > projectionsEvery || (engine.projectionsStale() && now - projectionsAt > 60_000))
				{
					projectionsAt = now;
					fetchProjections();
				}
			}
			catch (RuntimeException e)
			{
				status.status("Internal error: " + e);
			}
			int next = engine.anyGameLive(System.currentTimeMillis()) ? Math.max(5, config.pollSeconds()) : IDLE_POLL_SECONDS;
			later(this::cycle, next, TimeUnit.SECONDS);
		}, exec);
	}

	private void nextStatUrl()
	{
		statUrl = (statUrl + 1) % STAT_URLS.length;
		statUrlFailedAt = System.currentTimeMillis();
	}

	/**
	 * Re-reads the NFL state. A new week (or season type) means new matchups, so everything is rediscovered
	 * from scratch rather than diffed against the old week's points. The week override wins as at init.
	 */
	private void refreshState()
	{
		get(BASE + "state/nfl", Sleeper.State.class).whenCompleteAsync((state, err) ->
		{
			if (stopped || err != null || state == null)
			{
				return;
			}
			String newSeason = state.league_season != null ? state.league_season : state.season;
			String newType = state.season_type == null || state.season_type.isEmpty() ? "regular" : state.season_type;
			int newWeek = config.weekOverride() > 0 ? config.weekOverride() : Math.max(1, state.week);
			if (newWeek != week || !Objects.equals(newSeason, season) || !newType.equals(seasonType))
			{
				status.weekChanged(newWeek);
			}
		}, exec);
	}

	private void fetchPlays(long now)
	{
		for (String gameId : engine.gamesWanted(now))
		{
			http.get(Espn.summaryUrl(gameId)).thenAcceptAsync(json ->
			{
				if (!stopped)
				{
					engine.ingestPlays(gameId, Espn.parsePlays(gson, json));
				}
			}, exec);
		}
	}

	private void fetchProjections()
	{
		String url = String.format(PROJECTION_URLS[projectionUrl], season, week, seasonType);
		http.get(url).whenCompleteAsync((json, err) ->
		{
			if (err != null)
			{
				// Try the next URL in a minute rather than waiting the full refresh interval.
				projectionUrl = (projectionUrl + 1) % PROJECTION_URLS.length;
				projectionsAt = System.currentTimeMillis() - PROJECTIONS_EVERY_MS + 60_000;
				return;
			}
			try
			{
				Set<String> only = engine.statFilter();
				engine.setProjections(Stats.parse(json, only), only);
			}
			catch (RuntimeException e)
			{
				projectionUrl = (projectionUrl + 1) % PROJECTION_URLS.length;
			}
		}, exec);
	}

	// ---- helpers ----

	private <T> CompletableFuture<T> get(String url, Type type)
	{
		return http.get(url).thenApplyAsync((Function<String, T>) json -> gson.fromJson(json, type), exec);
	}

	private <T> CompletableFuture<T> get(String url, Class<T> type)
	{
		return http.get(url).thenApplyAsync(json -> gson.fromJson(json, type), exec);
	}

	private static String enc(String s)
	{
		try
		{
			return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
		}
		catch (java.io.UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String message(Throwable t)
	{
		Throwable root = t;
		while ((root instanceof CompletionException || root.getCause() != null) && root.getCause() != null)
		{
			root = root.getCause();
		}
		return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
	}
}

package com.layneburns.sleeperscape.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The heart of the plugin, single-threaded (every call on one executor). Each poll cycle is stamped with
 * when it was seen; point changes become {@link PlayEvent}s held until {@code observedAt + delay}, and the
 * scoreboard shows the newest frame at least {@code delay} old. Only the red zone skips the delay.
 */
public final class Engine
{
	public interface Settings
	{
		int delaySeconds();

		boolean startersOnly();

		boolean includeOpponent();

		int bigPlayYards();

		boolean leagueEnabled(String leagueId);

		String leagueAlias(String leagueId);
	}

	public interface Listener
	{
		/** A play reached the presentation time and passed the filters. */
		void released(PlayEvent event);

		/** A released play got its ESPN text late. */
		void updated(PlayEvent event);

		/** Current red-zone alerts, and those new since the last call. */
		void redZone(List<RedZoneAlert> active, List<RedZoneAlert> entered);

		/** Live games where my starters' side of the ball is on the field (with each ESPN update). */
		default void sideOfBall(List<SideOfBall> games)
		{
		}
	}

	/** One poll cycle's results. Missing pieces (a failed request) are null. */
	public static final class Cycle
	{
		public final long observedAt;
		public final Map<String, List<Sleeper.Matchup>> matchups = new HashMap<>();
		public Map<String, Map<String, Double>> stats;
		public Map<String, Espn.Game> games;

		public Cycle(long observedAt)
		{
			this.observedAt = observedAt;
		}
	}

	/** Scores as of one cycle. */
	static final class LeagueFrame
	{
		double my;
		Double opp;
		int oppRosterId = -1;
		List<String> myStarters = Collections.emptyList();
		List<String> oppStarters = Collections.emptyList();
		Map<String, Double> myPoints = Collections.emptyMap();
		Map<String, Double> oppPoints = Collections.emptyMap();
	}

	static final class Frame
	{
		final long t;
		final Map<String, LeagueFrame> leagues;
		final Map<String, Espn.Game> games;

		Frame(long t, Map<String, LeagueFrame> leagues, Map<String, Espn.Game> games)
		{
			this.t = t;
			this.leagues = leagues;
			this.games = games;
		}
	}

	/** A game whose team with the ball is inside the 20. */
	private static final class RedZoneHold
	{
		String team;
		String label;
		String score;
		long since;
		long lastWithPossession;
	}

	private static final class StatSnap
	{
		final long t;
		final Map<String, Double> stats;

		StatSnap(long t, Map<String, Double> stats)
		{
			this.t = t;
			this.stats = stats;
		}
	}

	/** A gap longer than this between polls of a league (sleep, network loss) re-baselines it. */
	static final long GAP_MS = 120_000;
	/** Without ESPN, games count as live while any league's points moved within this long. */
	static final long LIVE_WINDOW_MS = 5 * 60_000;
	/** A league that reports the same play a poll late joins the earlier event within this window. */
	static final long MERGE_MS = 30_000;
	/** Frames and stat history older than the longest delay plus this are dropped. */
	static final long KEEP_MS = 12 * 60_000;
	/** Keep a red-zone alert this long while ESPN omits possession (between snaps), unless the score changes. */
	static final long REDZONE_HOLD_MS = 90_000;
	/** The same team's red-zone trip is announced in chat once, not again for this long. */
	static final long REDZONE_REANNOUNCE_MS = 180_000;
	/**
	 * The stat feed and the scores don't always land in the same poll. A play whose points no stat snapshot
	 * explains yet waits this long (from when it was seen) for the stats, even with a shorter delay.
	 */
	static final long DETAIL_GRACE_MS = 15_000;
	/** A change this big with no stat line to explain it is shown as a significant BIG SWING. */
	static final double BIG_SWING_POINTS = 4.0;
	static final Set<String> OFFENSE = Set.of("QB", "RB", "WR", "TE", "K", "FB");

	private final Settings settings;
	private final Listener listener;

	private Map<String, Sleeper.Player> players = Collections.emptyMap();
	private final Map<String, LeagueContext> leagues = new LinkedHashMap<>();
	private final Map<String, Map<String, Double>> projections = new HashMap<>();

	private final Map<String, Map<String, Double>> lastPoints = new HashMap<>();
	private final Map<String, Long> lastSeenAt = new HashMap<>();
	private final Map<String, Integer> leadSign = new HashMap<>();
	private final Set<String> finalSeen = new HashSet<>();

	private final Map<String, ArrayDeque<StatSnap>> statHistory = new HashMap<>();
	private final Map<String, Map<String, Double>> latestStats = new HashMap<>();
	private final Map<String, Map<String, Double>> statCursor = new HashMap<>();
	private final Map<String, Long> statCursorAt = new HashMap<>();
	private boolean statsBaselined;

	private final List<PlayEvent> pending = new ArrayList<>();
	private final ArrayDeque<PlayEvent> recent = new ArrayDeque<>();
	private final ArrayDeque<Frame> frames = new ArrayDeque<>();
	private Map<String, Espn.Game> liveGames;
	/** When any league's points last changed between polls; 0 until the first poll. */
	private long lastPointsChangeAt;

	private final Map<String, List<Espn.Play>> playsByGame = new HashMap<>();
	private final Map<String, Long> lastMatchedSeq = new HashMap<>();
	private final Map<String, RedZoneHold> redZoneHolds = new LinkedHashMap<>();
	/** Last team seen with the ball, per live game (ESPN omits it between snaps). */
	private final Map<String, String> possessionHold = new HashMap<>();
	/** Which side was last projected to win, per league: 1 me, -1 my opponent. */
	private final Map<String, Integer> projectedSide = new HashMap<>();
	private final Map<String, Long> redZoneAnnounced = new HashMap<>();

	public Engine(Settings settings, Listener listener)
	{
		this.settings = settings;
		this.listener = listener;
	}

	public void setPlayers(Map<String, Sleeper.Player> players)
	{
		this.players = players == null ? Collections.emptyMap() : players;
	}

	public void setLeagues(List<LeagueContext> list)
	{
		leagues.clear();
		for (LeagueContext l : list)
		{
			leagues.put(l.id(), l);
		}
	}

	public List<LeagueContext> leagues()
	{
		return new ArrayList<>(leagues.values());
	}

	public void setProjections(Map<String, Map<String, Double>> p)
	{
		projections.clear();
		if (p != null)
		{
			projections.putAll(p);
		}
	}

	/**
	 * Whether to poll at the live rate. With ESPN data, whether any game is in progress; without it, whether
	 * Sleeper's points moved within {@link #LIVE_WINDOW_MS} (so a client left open all week idles, and the
	 * first scoring play of a game day brings the rate back up). Before the first poll, true.
	 */
	public boolean anyGameLive(long now)
	{
		if (liveGames != null)
		{
			for (Espn.Game g : liveGames.values())
			{
				if (g.live())
				{
					return true;
				}
			}
			return false;
		}
		return lastPointsChangeAt == 0 || now - lastPointsChangeAt < LIVE_WINDOW_MS;
	}

	// ---- ingest ----

	public void ingest(Cycle c)
	{
		long t = c.observedAt;
		if (c.stats != null)
		{
			ingestStats(t, c.stats);
		}
		if (c.games != null)
		{
			liveGames = c.games;
		}

		Map<String, List<PlayEvent.Impact>> deltas = new LinkedHashMap<>();
		Map<String, Boolean> offline = new HashMap<>();
		Map<String, LeagueFrame> frameLeagues = new HashMap<>();
		Frame previous = frames.peekLast();
		if (previous != null)
		{
			frameLeagues.putAll(previous.leagues);
		}
		List<PlayEvent> matchupEvents = new ArrayList<>();

		for (Map.Entry<String, List<Sleeper.Matchup>> e : c.matchups.entrySet())
		{
			LeagueContext ctx = leagues.get(e.getKey());
			if (ctx == null || e.getValue() == null)
			{
				continue;
			}
			String leagueId = ctx.id();
			LeagueFrame lf = leagueFrame(ctx, e.getValue());
			frameLeagues.put(leagueId, lf);

			Map<String, Double> prev = lastPoints.get(leagueId);
			Long seen = lastSeenAt.get(leagueId);
			boolean gap = seen != null && t - seen > GAP_MS;
			Map<String, Double> cur = new HashMap<>();
			for (Sleeper.Matchup row : e.getValue())
			{
				if (row.players_points == null)
				{
					continue;
				}
				Owner owner = ctx.owner(row.roster_id, lf.oppRosterId);
				for (Map.Entry<String, Double> p : row.players_points.entrySet())
				{
					double pts = p.getValue() == null ? 0 : p.getValue();
					cur.put(p.getKey(), pts);
					Double before = prev == null ? null : prev.get(p.getKey());
					if (before == null || Math.abs(pts - before) < 0.005)
					{
						continue;
					}
					// Any roster's points moving means games are on, whether or not the play is shown.
					lastPointsChangeAt = t;
					if (owner == Owner.OTHER)
					{
						continue;
					}
					boolean starter = row.starters != null && row.starters.contains(p.getKey());
					deltas.computeIfAbsent(p.getKey(), k -> new ArrayList<>())
						.add(new PlayEvent.Impact(leagueId, owner, ctx.ownerName(row.roster_id), starter, pts - before));
					offline.merge(p.getKey(), gap, Boolean::logicalOr);
				}
			}
			if (prev == null)
			{
				// First poll of this league: poll at the live rate until the points prove quiet.
				lastPointsChangeAt = t;
			}
			lastPoints.put(leagueId, cur);
			lastSeenAt.put(leagueId, t);

			if (lf.opp != null && ctx.myRosterId >= 0)
			{
				int sign = (int) Math.signum(Math.round((lf.my - lf.opp) * 100));
				Integer before = leadSign.get(leagueId);
				if (prev != null && !gap && before != null && sign != 0 && sign != before)
				{
					matchupEvents.add(leadChange(ctx, lf, t, sign > 0));
				}
				if (sign != 0)
				{
					leadSign.put(leagueId, sign);
				}
			}
		}

		for (Map.Entry<String, List<PlayEvent.Impact>> d : deltas.entrySet())
		{
			addDelta(d.getKey(), d.getValue(), t, offline.getOrDefault(d.getKey(), false));
		}

		Frame frame = new Frame(t, frameLeagues, liveGames);
		frames.addLast(frame);
		while (frames.size() > 1 && frames.peekFirst().t < t - KEEP_MS)
		{
			frames.pollFirst();
		}

		if (liveGames != null)
		{
			matchupEvents.addAll(finals(frame, previous == null));
			matchupEvents.addAll(projectedLeadChanges(frame));
			if (c.games != null)
			{
				updateRedZone(frame);
				updateSideOfBall(frame);
			}
		}
		pending.addAll(matchupEvents);
		pending.sort((a, b) -> Long.compare(a.observedAt, b.observedAt));
	}

	private void ingestStats(long t, Map<String, Map<String, Double>> stats)
	{
		for (Map.Entry<String, Map<String, Double>> e : stats.entrySet())
		{
			String pid = e.getKey();
			Map<String, Double> s = e.getValue();
			if (!statsBaselined)
			{
				statCursor.put(pid, s);
				statCursorAt.put(pid, t);
			}
			if (!Objects.equals(latestStats.get(pid), s))
			{
				latestStats.put(pid, s);
				ArrayDeque<StatSnap> h = statHistory.computeIfAbsent(pid, k -> new ArrayDeque<>());
				h.addLast(new StatSnap(t, s));
				while (h.size() > 1 && h.peekFirst().t < t - KEEP_MS)
				{
					h.pollFirst();
				}
			}
		}
		statsBaselined = true;
	}

	private LeagueFrame leagueFrame(LeagueContext ctx, List<Sleeper.Matchup> rows)
	{
		LeagueFrame lf = new LeagueFrame();
		Sleeper.Matchup mine = null;
		for (Sleeper.Matchup r : rows)
		{
			if (r.roster_id == ctx.myRosterId)
			{
				mine = r;
			}
		}
		if (mine == null)
		{
			return lf;
		}
		lf.my = mine.total();
		lf.myStarters = starters(mine);
		lf.myPoints = mine.players_points == null ? Collections.emptyMap() : new HashMap<>(mine.players_points);
		for (Sleeper.Matchup r : rows)
		{
			if (r != mine && mine.matchup_id != null && mine.matchup_id.equals(r.matchup_id))
			{
				lf.opp = r.total();
				lf.oppRosterId = r.roster_id;
				lf.oppStarters = starters(r);
				lf.oppPoints = r.players_points == null ? Collections.emptyMap() : new HashMap<>(r.players_points);
			}
		}
		return lf;
	}

	private static List<String> starters(Sleeper.Matchup m)
	{
		List<String> out = new ArrayList<>();
		if (m.starters != null)
		{
			for (String s : m.starters)
			{
				if (s != null && !s.equals("0"))
				{
					out.add(s);
				}
			}
		}
		return out;
	}

	private void addDelta(String pid, List<PlayEvent.Impact> impacts, long t, boolean offline)
	{
		PlayEvent last = null;
		for (int i = pending.size() - 1; i >= 0; i--)
		{
			PlayEvent p = pending.get(i);
			if (pid.equals(p.playerId))
			{
				last = p;
				break;
			}
		}
		if (last != null)
		{
			Set<String> had = new HashSet<>();
			for (PlayEvent.Impact i : last.impacts)
			{
				had.add(i.leagueId);
			}
			Set<String> now = new HashSet<>();
			for (PlayEvent.Impact i : impacts)
			{
				now.add(i.leagueId);
			}
			if (t - last.observedAt <= MERGE_MS && Collections.disjoint(had, now))
			{
				// The same play, reported a poll later by another league.
				last.impacts.addAll(impacts);
				return;
			}
			if (had.equals(now) && reverses(last, impacts))
			{
				// Taken back before anyone saw it (overturned on review, or a stat flicker): show neither.
				pending.remove(last);
				return;
			}
		}
		PlayEvent e = new PlayEvent(pid, players.get(pid), t, offline);
		e.impacts.addAll(impacts);
		pending.add(e);
	}

	private static boolean reverses(PlayEvent earlier, List<PlayEvent.Impact> later)
	{
		for (PlayEvent.Impact i : later)
		{
			boolean matched = false;
			for (PlayEvent.Impact j : earlier.impacts)
			{
				if (j.leagueId.equals(i.leagueId) && Math.abs(j.delta + i.delta) < 0.01)
				{
					matched = true;
				}
			}
			if (!matched)
			{
				return false;
			}
		}
		return true;
	}

	private PlayEvent leadChange(LeagueContext ctx, LeagueFrame lf, long t, boolean mineNow)
	{
		PlayEvent e = new PlayEvent(null, null, t, false);
		e.categories = EnumSet.of(Category.LEAD_CHANGE);
		PlayEvent.Impact i = new PlayEvent.Impact(ctx.id(), Owner.MINE, ctx.ownerName(ctx.myRosterId), true, lf.my - lf.opp);
		i.tier = mineNow ? Tier.VERY_GOOD : Tier.VERY_BAD;
		e.impacts.add(i);
		e.message = (mineNow ? "You took the lead " : ctx.ownerName(lf.oppRosterId) + " took the lead ")
			+ Format.score(lf.my) + " - " + Format.score(lf.opp);
		return e;
	}

	/** Matchups whose every starter (both sides) has finished, the first time that is seen. */
	private List<PlayEvent> finals(Frame frame, boolean firstFrame)
	{
		List<PlayEvent> out = new ArrayList<>();
		for (Map.Entry<String, LeagueFrame> e : frame.leagues.entrySet())
		{
			LeagueContext ctx = leagues.get(e.getKey());
			LeagueFrame lf = e.getValue();
			if (ctx == null || lf.opp == null || finalSeen.contains(ctx.id()))
			{
				continue;
			}
			Integer left = startersLeft(lf.myStarters, frame.games);
			Integer oppLeft = startersLeft(lf.oppStarters, frame.games);
			if (left == null || oppLeft == null || left > 0 || oppLeft > 0 || lf.myStarters.isEmpty())
			{
				continue;
			}
			finalSeen.add(ctx.id());
			if (firstFrame)
			{
				continue;
			}
			PlayEvent ev = new PlayEvent(null, null, frame.t, false);
			ev.categories = EnumSet.of(Category.MATCHUP_FINAL);
			double margin = lf.my - lf.opp;
			PlayEvent.Impact i = new PlayEvent.Impact(ctx.id(), Owner.MINE, ctx.ownerName(ctx.myRosterId), true, margin);
			i.tier = margin > 0.005 ? Tier.SCORED : margin < -0.005 ? Tier.SCORED_ON : Tier.CONFLICTED;
			ev.impacts.add(i);
			ev.message = (margin > 0.005 ? "W " : margin < -0.005 ? "L " : "T ") + Format.score(lf.my) + " - "
				+ Format.score(lf.opp) + " vs " + ctx.ownerName(lf.oppRosterId);
			out.add(ev);
		}
		return out;
	}

	// ---- red zone (live) ----

	private void updateRedZone(Frame frame)
	{
		long t = frame.t;
		Set<String> seenGames = new HashSet<>();
		for (Espn.Game g : frame.games.values())
		{
			if (!seenGames.add(g.id))
			{
				continue;
			}
			RedZoneHold held = redZoneHolds.get(g.id);
			if (!g.live() || g.halftime || !g.redZone)
			{
				redZoneHolds.remove(g.id);
			}
			else if (g.possession != null)
			{
				RedZoneHold h = new RedZoneHold();
				h.team = g.possession;
				h.label = g.label();
				h.score = g.score;
				h.lastWithPossession = t;
				h.since = held != null && held.team.equals(g.possession) ? held.since : t;
				redZoneHolds.put(g.id, h);
			}
			else if (held != null && (!held.score.equals(g.score) || t - held.lastWithPossession > REDZONE_HOLD_MS))
			{
				// ESPN drops possession between snaps; hold the alert through that, but not past a score.
				redZoneHolds.remove(g.id);
			}
		}
		redZoneHolds.keySet().retainAll(seenGames);

		List<RedZoneAlert> active = new ArrayList<>();
		List<RedZoneAlert> entered = new ArrayList<>();
		for (Map.Entry<String, RedZoneHold> e : redZoneHolds.entrySet())
		{
			RedZoneHold h = e.getValue();
			Map<String, RedZoneAlert.Entry> entries = new LinkedHashMap<>();
			for (Map.Entry<String, LeagueFrame> lfe : frame.leagues.entrySet())
			{
				String leagueId = lfe.getKey();
				if (!settings.leagueEnabled(leagueId))
				{
					continue;
				}
				String alias = settings.leagueAlias(leagueId);
				collectRedZone(entries, lfe.getValue().myStarters, h.team, true, alias);
				if (settings.includeOpponent())
				{
					collectRedZone(entries, lfe.getValue().oppStarters, h.team, false, alias);
				}
			}
			if (entries.isEmpty())
			{
				continue;
			}
			String key = e.getKey() + ":" + h.team;
			RedZoneAlert a = new RedZoneAlert(key, h.team, h.label);
			a.entries.addAll(entries.values());
			active.add(a);
			Long announced = redZoneAnnounced.get(key);
			if (announced == null || announced < h.since - REDZONE_REANNOUNCE_MS)
			{
				entered.add(a);
			}
			redZoneAnnounced.put(key, h.since);
		}
		listener.redZone(active, entered);
	}

	static final Set<String> DEFENSE = Set.of("DEF", "DL", "DE", "DT", "NT", "LB", "ILB", "OLB", "MLB", "EDGE", "DB",
		"CB", "S", "SS", "FS");

	/** Live games where my starters' side of the ball is on the field: offense with the ball, IDP/DEF without. */
	private void updateSideOfBall(Frame frame)
	{
		Set<String> seen = new HashSet<>();
		List<SideOfBall> out = new ArrayList<>();
		for (Espn.Game g : frame.games.values())
		{
			if (!seen.add(g.id))
			{
				continue;
			}
			if (!g.live() || g.halftime)
			{
				possessionHold.remove(g.id);
				continue;
			}
			if (g.possession != null)
			{
				possessionHold.put(g.id, g.possession);
			}
			String ball = possessionHold.get(g.id);
			if (ball == null)
			{
				continue;
			}
			String defending = ball.equals(g.home) ? g.away : g.home;
			SideOfBall side = new SideOfBall(g.label(), ball, redZoneHolds.containsKey(g.id));
			Map<String, SideOfBall.Entry> entries = new LinkedHashMap<>();
			for (Map.Entry<String, LeagueFrame> lfe : frame.leagues.entrySet())
			{
				if (!settings.leagueEnabled(lfe.getKey()))
				{
					continue;
				}
				String alias = settings.leagueAlias(lfe.getKey());
				for (String pid : lfe.getValue().myStarters)
				{
					Sleeper.Player p = players.get(pid);
					if (p == null || p.team == null || p.position == null)
					{
						continue;
					}
					boolean offense = p.team.equals(ball) && OFFENSE.contains(p.position);
					boolean defense = p.team.equals(defending) && isDefender(p);
					if (!offense && !defense)
					{
						continue;
					}
					SideOfBall.Entry entry = entries.computeIfAbsent(pid, k -> new SideOfBall.Entry(p.displayName(), defense));
					if (!entry.leagues.contains(alias))
					{
						entry.leagues.add(alias);
					}
				}
			}
			if (!entries.isEmpty())
			{
				side.entries.addAll(entries.values());
				out.add(side);
			}
		}
		possessionHold.keySet().retainAll(seen);
		listener.sideOfBall(out);
	}

	private static boolean isDefender(Sleeper.Player p)
	{
		if (DEFENSE.contains(p.position))
		{
			return true;
		}
		if (p.fantasy_positions != null)
		{
			for (String fp : p.fantasy_positions)
			{
				if (DEFENSE.contains(fp))
				{
					return true;
				}
			}
		}
		return false;
	}

	private void collectRedZone(Map<String, RedZoneAlert.Entry> entries, List<String> starters, String team,
		boolean mine, String alias)
	{
		for (String pid : starters)
		{
			Sleeper.Player p = players.get(pid);
			if (p == null || !team.equals(p.team) || p.position == null || !OFFENSE.contains(p.position))
			{
				continue;
			}
			RedZoneAlert.Entry entry = entries.computeIfAbsent((mine ? "m:" : "o:") + pid,
				k -> new RedZoneAlert.Entry(p.displayName(), mine));
			if (!entry.leagues.contains(alias))
			{
				entry.leagues.add(alias);
			}
		}
	}

	// ---- ESPN play text (level 3) ----

	/** Game ids whose play-by-play would describe a waiting or just-released play. */
	public Set<String> gamesWanted(long now)
	{
		Set<String> ids = new HashSet<>();
		if (liveGames == null)
		{
			return ids;
		}
		for (PlayEvent e : pending)
		{
			addGame(ids, e);
		}
		for (PlayEvent e : recent)
		{
			if (e.playText == null && now - e.observedAt < 180_000)
			{
				addGame(ids, e);
			}
		}
		return ids;
	}

	private void addGame(Set<String> ids, PlayEvent e)
	{
		if (e.player == null || e.player.team == null || Espn.nameKey(e.player) == null)
		{
			return;
		}
		Espn.Game g = liveGames.get(e.player.team);
		if (g != null && g.id != null)
		{
			ids.add(g.id);
		}
	}

	public void ingestPlays(String gameId, List<Espn.Play> plays)
	{
		playsByGame.put(gameId, plays);
		for (PlayEvent e : recent)
		{
			if (e.playText == null && matchPlay(e))
			{
				listener.updated(e);
			}
		}
	}

	private boolean matchPlay(PlayEvent e)
	{
		if (liveGames == null || e.player == null || e.player.team == null)
		{
			return false;
		}
		String key = Espn.nameKey(e.player);
		Espn.Game g = liveGames.get(e.player.team);
		List<Espn.Play> plays = g == null ? null : playsByGame.get(g.id);
		if (key == null || plays == null)
		{
			return false;
		}
		long after = lastMatchedSeq.getOrDefault(e.playerId, -1L);
		List<Espn.Play> candidates = new ArrayList<>();
		for (Espn.Play p : plays)
		{
			if (p.sequence > after && Espn.squash(p.text).contains(key))
			{
				candidates.add(p);
			}
		}
		if (candidates.isEmpty())
		{
			return false;
		}
		boolean scoringEvent = e.categories.contains(Category.TOUCHDOWN) || e.categories.contains(Category.FIELD_GOAL)
			|| e.categories.contains(Category.TWO_POINT);
		Espn.Play chosen = null;
		for (Espn.Play p : candidates)
		{
			if (scoringEvent && p.scoring)
			{
				chosen = p;
			}
		}
		if (chosen == null)
		{
			for (Espn.Play p : candidates)
			{
				if (chosen == null || Math.abs(p.yards) >= Math.abs(chosen.yards))
				{
					chosen = p;
				}
			}
		}
		lastMatchedSeq.put(e.playerId, candidates.get(candidates.size() - 1).sequence);
		e.playText = chosen.text;
		if (!e.released && OFFENSE.contains(e.player.position) && !"K".equals(e.player.position)
			&& chosen.yards >= settings.bigPlayYards())
		{
			e.categories.add(Category.BIG_PLAY);
			e.categories.remove(Category.PLAY);
		}
		return true;
	}

	// ---- release ----

	/** Releases due plays and returns the scoreboard view at {@code now - delay}. */
	public View tick(long now)
	{
		long presentation = now - settings.delaySeconds() * 1000L;
		// One at a time, in order: releasing a play moves its player's stat cursor, which the next play's
		// "explained yet?" check depends on.
		while (!pending.isEmpty())
		{
			PlayEvent e = pending.get(0);
			if (e.observedAt > presentation)
			{
				break;
			}
			if (!e.isMatchupEvent() && statsBaselined && now - e.observedAt < DETAIL_GRACE_MS && explaining(e) == null)
			{
				// Keep the order: later plays wait behind this one.
				break;
			}
			pending.remove(0);
			release(e);
		}
		while (!recent.isEmpty() && recent.peekFirst().observedAt < now - KEEP_MS)
		{
			recent.pollFirst();
		}
		return view(presentation);
	}

	private void release(PlayEvent e)
	{
		e.released = true;
		if (!e.isMatchupEvent())
		{
			finalise(e);
		}
		List<PlayEvent.Impact> visible = new ArrayList<>();
		List<Tier> tiers = new ArrayList<>();
		double netForMe = 0;
		for (PlayEvent.Impact i : e.impacts)
		{
			i.alias = settings.leagueAlias(i.leagueId);
			boolean ownerOk = i.owner == Owner.MINE || (i.owner == Owner.OPPONENT && settings.includeOpponent());
			if (settings.leagueEnabled(i.leagueId) && ownerOk && (i.starter || !settings.startersOnly()))
			{
				visible.add(i);
				tiers.add(i.tier);
				netForMe += i.owner == Owner.MINE ? i.delta : -i.delta;
			}
		}
		// Mine first, then by size of the swing.
		visible.sort((a, b) -> a.owner != b.owner ? a.owner.compareTo(b.owner) : Double.compare(Math.abs(b.delta), Math.abs(a.delta)));
		e.visible = visible;
		e.tier = Tier.combine(tiers, netForMe);
		e.netForMe = netForMe;
		if (!visible.isEmpty())
		{
			recent.addLast(e);
			listener.released(e);
		}
	}

	/** The impact whose league's scoring describes the play: my first enabled league, else the first. */
	private PlayEvent.Impact primary(PlayEvent e)
	{
		for (PlayEvent.Impact i : e.impacts)
		{
			if (i.owner == Owner.MINE && settings.leagueEnabled(i.leagueId))
			{
				return i;
			}
		}
		return e.impacts.get(0);
	}

	private Map<String, Double> scoringOf(PlayEvent.Impact i)
	{
		LeagueContext ctx = leagues.get(i.leagueId);
		return ctx == null ? null : ctx.scoring();
	}

	/**
	 * The earliest stat snapshot since the player's last released play whose change is worth exactly the
	 * play's points, or null. Picks the right snapshot when the stats arrive a poll before or after the scores.
	 */
	private StatSnap explaining(PlayEvent e)
	{
		PlayEvent.Impact primary = primary(e);
		Map<String, Double> scoring = scoringOf(primary);
		ArrayDeque<StatSnap> h = statHistory.get(e.playerId);
		if (scoring == null || h == null)
		{
			return null;
		}
		Map<String, Double> start = statCursor.getOrDefault(e.playerId, Collections.emptyMap());
		long after = statCursorAt.getOrDefault(e.playerId, Long.MIN_VALUE);
		for (StatSnap snap : h)
		{
			if (snap.t > after && Math.abs(Stats.points(Stats.diff(start, snap.stats), scoring) - primary.delta) < 0.011)
			{
				return snap;
			}
		}
		return null;
	}

	private void finalise(PlayEvent e)
	{
		String pid = e.playerId;
		Map<String, Double> start = statCursor.getOrDefault(pid, Collections.emptyMap());
		StatSnap match = explaining(e);
		Map<String, Double> end = start;
		long endAt = statCursorAt.getOrDefault(pid, Long.MIN_VALUE);
		if (match != null)
		{
			end = match.stats;
			endAt = match.t;
		}
		else
		{
			// Nothing explains it exactly: take what arrived before this player's next play.
			long bound = Long.MAX_VALUE;
			for (PlayEvent p : pending)
			{
				if (pid.equals(p.playerId))
				{
					bound = p.observedAt - 1;
					break;
				}
			}
			ArrayDeque<StatSnap> h = statHistory.get(pid);
			if (h != null)
			{
				for (StatSnap s : h)
				{
					if (s.t <= bound && s.t > endAt)
					{
						end = s.stats;
						endAt = s.t;
					}
				}
			}
		}
		if (statsBaselined)
		{
			statCursor.put(pid, end);
			statCursorAt.put(pid, endAt);
		}
		Map<String, Double> diff = Stats.diff(start, end);

		PlayEvent.Impact primary = primary(e);
		Map<String, Double> scoring = scoringOf(primary);
		boolean def = e.player != null && e.player.isTeamDefense();
		e.categories = Classifier.categories(diff, end, def, primary.delta, settings.bigPlayYards(), scoring);
		e.milestone = Classifier.milestone(diff, scoring);
		if (diff.isEmpty() && Math.abs(primary.delta) >= BIG_SWING_POINTS)
		{
			// No stat line yet (the stat feed lagging): don't let a likely touchdown pass as routine.
			e.categories = EnumSet.of(Category.BIG_SWING);
		}
		List<Classifier.Component> comps = Classifier.components(diff, scoring);
		double explained = 0;
		for (Classifier.Component c : comps)
		{
			explained += c.points;
		}
		if (!comps.isEmpty() && Math.abs(primary.delta - explained) >= 0.05)
		{
			// The stat feed and the scores disagree (one is a poll behind): keep the numbers honest.
			comps.add(new Classifier.Component("other", primary.delta - explained));
			comps.sort((a, b) -> Double.compare(b.points, a.points));
		}
		e.components = comps;
		matchPlay(e);

		Category top = e.top();
		for (PlayEvent.Impact i : e.impacts)
		{
			Tier own = top.ownerTier(i.delta);
			i.tier = i.owner == Owner.OPPONENT ? own.mirror() : own;
		}
	}

	// ---- scoreboard ----

	private View view(long presentation)
	{
		Frame frame = null;
		for (Iterator<Frame> it = frames.descendingIterator(); it.hasNext(); )
		{
			Frame f = it.next();
			if (f.t <= presentation)
			{
				frame = f;
				break;
			}
		}
		if (frame == null)
		{
			Frame first = frames.peekFirst();
			return new View(Collections.emptyList(), first == null ? 0 : Math.max(1, first.t - presentation), null, 0);
		}
		List<View.Line> lines = new ArrayList<>();
		for (LeagueContext ctx : leagues.values())
		{
			LeagueFrame lf = frame.leagues.get(ctx.id());
			if (lf == null || !settings.leagueEnabled(ctx.id()) || ctx.myRosterId < 0)
			{
				continue;
			}
			View.Line line = new View.Line();
			line.leagueId = ctx.id();
			line.alias = settings.leagueAlias(ctx.id());
			line.myOwner = ctx.ownerName(ctx.myRosterId);
			line.my = lf.my;
			if (lf.opp != null)
			{
				line.opp = lf.opp;
				line.oppOwner = ctx.ownerName(lf.oppRosterId);
			}
			line.myStarters = starterLines(lf.myStarters, lf.myPoints, ctx, frame.games);
			line.oppStarters = starterLines(lf.oppStarters, lf.oppPoints, ctx, frame.games);
			line.myLeft = startersLeft(lf.myStarters, frame.games);
			line.oppLeft = lf.opp == null ? null : startersLeft(lf.oppStarters, frame.games);
			line.leagueName = ctx.name();
			WinProbability outlook = outlook(ctx, lf, frame.games);
			if (outlook != null)
			{
				line.winProbability = outlook.probability();
				line.myProjected = outlook.myProjected();
				line.oppProjected = outlook.oppProjected();
			}
			lines.add(line);
		}
		return new View(lines, 0, null, frame.t);
	}

	/** Each starter with their points and where their game stands (from the same delayed frame). */
	private List<View.Starter> starterLines(List<String> starters, Map<String, Double> points, LeagueContext ctx,
		Map<String, Espn.Game> games)
	{
		List<View.Starter> out = new ArrayList<>();
		for (String pid : starters)
		{
			Sleeper.Player p = players.get(pid);
			View.Starter s = new View.Starter();
			s.playerId = pid;
			s.name = p == null ? pid : p.displayName();
			s.position = p == null || p.position == null ? "" : p.position;
			s.team = p == null ? null : p.team;
			Double pts = points.get(pid);
			s.points = pts == null ? 0 : pts;
			Map<String, Double> proj = projections.get(pid);
			s.projected = proj == null || ctx.scoring() == null ? null : Stats.points(proj, ctx.scoring());
			if (games != null)
			{
				Espn.Game g = gameOf(pid, games);
				if (g == null)
				{
					s.status = View.Starter.Status.DONE;
					s.statusText = "Bye";
				}
				else
				{
					s.status = g.live() ? View.Starter.Status.PLAYING
						: g.over() ? View.Starter.Status.DONE : View.Starter.Status.YET_TO_PLAY;
					s.statusText = g.clockText();
					s.kickoff = g.kickoff;
				}
			}
			out.add(s);
		}
		return out;
	}

	private Integer startersLeft(List<String> starters, Map<String, Espn.Game> games)
	{
		if (games == null)
		{
			return null;
		}
		int left = 0;
		for (String pid : starters)
		{
			Espn.Game g = gameOf(pid, games);
			if (g != null && !g.over())
			{
				left++;
			}
		}
		return left;
	}

	private Espn.Game gameOf(String pid, Map<String, Espn.Game> games)
	{
		Sleeper.Player p = players.get(pid);
		String team = p == null ? null : p.team;
		return team == null ? null : games.get(team);
	}

	/** Projected finals and win probability for a matchup, or null without games or projections. */
	private WinProbability outlook(LeagueContext ctx, LeagueFrame lf, Map<String, Espn.Game> games)
	{
		if (lf.opp == null || games == null || projections.isEmpty() || ctx.scoring() == null)
		{
			return null;
		}
		WinProbability wp = new WinProbability(lf.my, lf.opp);
		addProjected(wp, true, lf.myStarters, ctx, games);
		addProjected(wp, false, lf.oppStarters, ctx, games);
		return wp;
	}

	/**
	 * The side projected to win flipped. Hysteresis: it only counts once the probability is past 52% the other
	 * way, so a matchup hovering at 50% doesn't announce every poll. The first reading is silent.
	 */
	private List<PlayEvent> projectedLeadChanges(Frame frame)
	{
		List<PlayEvent> out = new ArrayList<>();
		for (Map.Entry<String, LeagueFrame> e : frame.leagues.entrySet())
		{
			LeagueContext ctx = leagues.get(e.getKey());
			LeagueFrame lf = e.getValue();
			if (ctx == null || finalSeen.contains(e.getKey()))
			{
				continue;
			}
			WinProbability o = outlook(ctx, lf, frame.games);
			if (o == null)
			{
				continue;
			}
			double p = o.probability();
			int side = p >= 0.52 ? 1 : p <= 0.48 ? -1 : 0;
			Integer before = projectedSide.get(ctx.id());
			if (side == 0)
			{
				continue;
			}
			projectedSide.put(ctx.id(), side);
			if (before == null || before == side)
			{
				continue;
			}
			PlayEvent ev = new PlayEvent(null, null, frame.t, false);
			ev.categories = EnumSet.of(Category.PROJECTED_LEAD);
			PlayEvent.Impact i = new PlayEvent.Impact(ctx.id(), Owner.MINE, ctx.ownerName(ctx.myRosterId), true,
				o.myProjected() - o.oppProjected());
			i.tier = side > 0 ? Tier.VERY_GOOD : Tier.VERY_BAD;
			ev.impacts.add(i);
			ev.message = (side > 0 ? "Now projected to win " : "Now projected to lose ") + Format.score(o.myProjected())
				+ " - " + Format.score(o.oppProjected()) + " (" + Math.round(p * 100) + "%)";
			out.add(ev);
		}
		return out;
	}

	private void addProjected(WinProbability wp, boolean mine, List<String> starters, LeagueContext ctx,
		Map<String, Espn.Game> games)
	{
		for (String pid : starters)
		{
			Espn.Game g = gameOf(pid, games);
			Map<String, Double> proj = projections.get(pid);
			if (g == null || proj == null)
			{
				continue;
			}
			wp.add(mine, Stats.points(proj, ctx.scoring()), g.fractionLeft());
		}
	}
}

package com.layneburns.sleeperscape.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** Engine behaviour on hand-built polls: two leagues, me (roster 1) against roster 2. */
public class EngineTest
{
	private static final String RD = "L1";
	private static final String DYN = "L2";
	private static final String HURTS = "6904";
	private static final String KELCE = "1466";

	private final List<PlayEvent> released = new ArrayList<>();
	private final List<RedZoneAlert> entered = new ArrayList<>();
	private final List<List<SideOfBall>> sides = new ArrayList<>();
	private int delay;
	private boolean startersOnly = true;
	private Engine engine;
	private Map<String, Map<String, Double>> stats;

	@Before
	public void setUp()
	{
		engine = new Engine(new Engine.Settings()
		{
			public int delaySeconds()
			{
				return delay;
			}

			public boolean startersOnly()
			{
				return startersOnly;
			}

			public boolean includeOpponent()
			{
				return true;
			}

			public int bigPlayYards()
			{
				return 20;
			}

			public boolean leagueEnabled(String id)
			{
				return true;
			}

			public String leagueAlias(String id)
			{
				return id.equals(RD) ? "RD" : "DYN";
			}
		}, new Engine.Listener()
		{
			public void released(PlayEvent e)
			{
				released.add(e);
			}

			public void updated(PlayEvent e)
			{
			}

			public void redZone(List<RedZoneAlert> active, List<RedZoneAlert> enteredNow)
			{
				entered.addAll(enteredNow);
			}

			public void sideOfBall(List<SideOfBall> games)
			{
				sides.add(games);
			}
		});
		setUpEngine();
	}

	private void setUpEngine()
	{
		Map<String, Sleeper.Player> players = new HashMap<>();
		players.put(HURTS, player(HURTS, "Jalen", "Hurts", "QB", "PHI"));
		players.put(KELCE, player(KELCE, "Travis", "Kelce", "TE", "KC"));
		engine.setPlayers(players);
		Map<String, Double> scoring = new HashMap<>();
		scoring.put("pass_yd", 0.04);
		scoring.put("pass_td", 4.0);
		scoring.put("rush_yd", 0.1);
		scoring.put("rush_td", 6.0);
		scoring.put("rec", 1.0);
		scoring.put("rec_yd", 0.1);
		scoring.put("pass_int", -2.0);
		engine.setLeagues(Arrays.asList(league(RD, scoring), league(DYN, scoring)));
		stats = new HashMap<>();
	}

	private static Sleeper.Player player(String id, String first, String last, String pos, String team)
	{
		Sleeper.Player p = new Sleeper.Player();
		p.player_id = id;
		p.first_name = first;
		p.last_name = last;
		p.position = pos;
		p.team = team;
		return p;
	}

	private static LeagueContext league(String id, Map<String, Double> scoring)
	{
		Sleeper.League l = new Sleeper.League();
		l.league_id = id;
		l.name = id;
		l.scoring_settings = scoring;
		Sleeper.Roster me = new Sleeper.Roster();
		me.roster_id = 1;
		me.owner_id = "me";
		Sleeper.Roster them = new Sleeper.Roster();
		them.roster_id = 2;
		them.owner_id = "them";
		return new LeagueContext(l, Arrays.asList(me, them), new ArrayList<>(), "me");
	}

	private static Sleeper.Matchup row(int roster, String starter, double pts, double total)
	{
		Sleeper.Matchup m = new Sleeper.Matchup();
		m.roster_id = roster;
		m.matchup_id = 1;
		m.points = total;
		m.starters = Arrays.asList(starter);
		m.players_points = new HashMap<>();
		m.players_points.put(starter, pts);
		return m;
	}

	/** RD: Hurts is mine, Kelce theirs. DYN: Hurts is theirs, Kelce mine. */
	private Engine.Cycle cycle(long t, double hurts, double kelce)
	{
		Engine.Cycle c = new Engine.Cycle(t);
		c.matchups.put(RD, Arrays.asList(row(1, HURTS, hurts, hurts), row(2, KELCE, kelce, kelce)));
		c.matchups.put(DYN, Arrays.asList(row(1, KELCE, kelce, kelce), row(2, HURTS, hurts, hurts)));
		Map<String, Map<String, Double>> copy = new HashMap<>();
		stats.forEach((k, v) -> copy.put(k, new HashMap<>(v)));
		c.stats = copy;
		return c;
	}

	private void stat(String pid, String key, double value)
	{
		stats.computeIfAbsent(pid, k -> new HashMap<>()).put(key, value);
	}

	@Test
	public void firstPollIsASilentBaseline()
	{
		engine.ingest(cycle(1000, 10, 5));
		engine.tick(100_000);
		assertTrue(released.isEmpty());
	}

	@Test
	public void touchdownIsOneEventAcrossLeaguesWithStatBreakdownAndMirroredTiers()
	{
		stat(HURTS, "rush_yd", 20);
		engine.ingest(cycle(1000, 10, 5));
		stat(HURTS, "rush_yd", 32);
		stat(HURTS, "rush_td", 1);
		engine.ingest(cycle(11_000, 17.2, 5));
		engine.tick(11_000);

		assertEquals(1, released.size());
		PlayEvent e = released.get(0);
		assertEquals(Category.TOUCHDOWN, e.top());
		assertTrue(e.significant());
		assertEquals(2, e.visible.size());
		// Mine first.
		assertEquals(Owner.MINE, e.visible.get(0).owner);
		assertEquals(Tier.SCORED, e.visible.get(0).tier);
		assertEquals(Tier.SCORED_ON, e.visible.get(1).tier);
		// Scored in one league, scored on in the other: a shrug.
		assertEquals(Tier.CONFLICTED, e.tier);
		assertEquals("rush TD +6 · 12 rush yd +1.2", e.detail());
	}

	@Test
	public void delayHoldsPlaysAndScores()
	{
		delay = 60;
		engine.ingest(cycle(1000, 10, 5));
		engine.ingest(cycle(11_000, 12, 5));
		View v = engine.tick(30_000);
		assertTrue(released.isEmpty());
		assertTrue(v.lines.isEmpty());
		assertTrue(v.bufferingMs > 0);

		v = engine.tick(62_000);
		assertTrue(released.isEmpty());
		assertEquals(10, v.lines.get(0).my, 1e-9);

		v = engine.tick(71_000);
		assertEquals(1, released.size());
		assertEquals(12, v.lines.get(0).my, 1e-9);
	}

	@Test
	public void reversalInsideTheDelayIsNeverShown()
	{
		delay = 60;
		engine.ingest(cycle(1000, 10, 5));
		engine.ingest(cycle(11_000, 16, 5));
		engine.ingest(cycle(21_000, 10, 5));
		engine.tick(200_000);
		assertTrue(released.isEmpty());
	}

	@Test
	public void leagueReportingAPollLateJoinsTheSameEvent()
	{
		engine.ingest(cycle(1000, 10, 5));
		Engine.Cycle c = cycle(11_000, 16, 5);
		c.matchups.remove(DYN);
		engine.ingest(c);
		engine.ingest(cycle(21_000, 16, 5));
		// No stat line explains the points here, so the play waits out the detail grace.
		engine.tick(11_000 + Engine.DETAIL_GRACE_MS);
		assertEquals(1, released.size());
		assertEquals(2, released.get(0).visible.size());
	}

	@Test
	public void benchPlaysAreFilteredWhenStartersOnly()
	{
		engine.ingest(cycle(1000, 10, 5));
		Engine.Cycle c = cycle(11_000, 10, 5);
		Sleeper.Matchup mine = c.matchups.get(RD).get(0);
		mine.players_points.put("bench1", 3.0);
		engine.ingest(c);
		Engine.Cycle c2 = cycle(21_000, 10, 5);
		c2.matchups.get(RD).get(0).players_points.put("bench1", 9.0);
		engine.ingest(c2);
		engine.tick(30_000);
		assertTrue(released.isEmpty());
	}

	@Test
	public void leadChangeIsAnnounced()
	{
		engine.ingest(cycle(1000, 10, 12));
		engine.ingest(cycle(11_000, 14, 12));
		engine.tick(11_000 + Engine.DETAIL_GRACE_MS);
		boolean lead = false;
		for (PlayEvent e : released)
		{
			if (e.top() == Category.LEAD_CHANGE && RD.equals(e.visible.get(0).leagueId))
			{
				lead = true;
				assertEquals(Tier.VERY_GOOD, e.tier);
			}
		}
		assertTrue(lead);
	}

	@Test
	public void redZoneIsLiveAndListsMineAndTheirs()
	{
		delay = 120;
		engine.ingest(cycle(1000, 10, 5));
		Engine.Cycle c = cycle(11_000, 10, 5);
		Espn.Game g = new Espn.Game();
		g.id = "401";
		g.state = "in";
		g.home = "PHI";
		g.away = "DAL";
		g.possession = "PHI";
		g.redZone = true;
		c.games = new HashMap<>();
		c.games.put("PHI", g);
		c.games.put("DAL", g);
		engine.ingest(c);
		assertEquals(1, entered.size());
		RedZoneAlert a = entered.get(0);
		assertEquals("PHI", a.team);
		assertEquals(2, a.entries.size());
		assertTrue(a.entries.get(0).mine);
		assertEquals(Arrays.asList("RD"), a.entries.get(0).leagues);
		assertEquals(Arrays.asList("DYN"), a.entries.get(1).leagues);
		// Still in the red zone next poll: not announced again.
		engine.ingest(cycle(21_000, 10, 5));
		Engine.Cycle c3 = cycle(31_000, 10, 5);
		c3.games = c.games;
		engine.ingest(c3);
		assertEquals(1, entered.size());
	}

	@Test
	public void winProbabilityNeedsGamesAndProjections()
	{
		engine.ingest(cycle(1000, 10, 5));
		assertNull(engine.tick(1000).lines.get(0).winProbability);

		Map<String, Map<String, Double>> proj = new HashMap<>();
		Map<String, Double> p = new HashMap<>();
		p.put("pass_yd", 250.0);
		p.put("pass_td", 2.0);
		proj.put(HURTS, p);
		proj.put(KELCE, p);
		engine.setProjections(proj);
		Engine.Cycle c = cycle(11_000, 10, 5);
		Espn.Game phi = new Espn.Game();
		phi.id = "1";
		phi.state = "post";
		Espn.Game kc = new Espn.Game();
		kc.id = "2";
		kc.state = "post";
		c.games = new HashMap<>();
		c.games.put("PHI", phi);
		c.games.put("KC", kc);
		engine.ingest(c);
		View v = engine.tick(11_000);
		Double wp = v.lines.get(0).winProbability;
		assertNotNull(wp);
		assertEquals(1.0, wp, 1e-9);
	}

	private static Espn.Game game(boolean redZone, String possession, String score)
	{
		Espn.Game g = new Espn.Game();
		g.id = "401";
		g.state = "in";
		g.home = "PHI";
		g.away = "DAL";
		g.possession = possession;
		g.redZone = redZone;
		g.score = score;
		return g;
	}

	private Engine.Cycle withGame(Engine.Cycle c, Espn.Game g)
	{
		c.games = new HashMap<>();
		c.games.put("PHI", g);
		c.games.put("DAL", g);
		return c;
	}

	@Test
	public void redZoneHoldsWhilePossessionIsMissingButNotPastAScore()
	{
		engine.ingest(cycle(1000, 10, 5));
		engine.ingest(withGame(cycle(11_000, 10, 5), game(true, "PHI", "0-7")));
		assertEquals(1, entered.size());
		// Dead ball: ESPN drops possession, isRedZone stays true. Still one alert, not re-announced.
		engine.ingest(withGame(cycle(21_000, 10, 5), game(true, null, "0-7")));
		engine.ingest(withGame(cycle(31_000, 10, 5), game(true, "PHI", "0-7")));
		assertEquals(1, entered.size());
		// Touchdown: score changes while possession is missing, so the alert clears.
		engine.ingest(withGame(cycle(41_000, 16, 5), game(true, null, "0-13")));
		// A new trip right away is not announced again (within the re-announce window).
		engine.ingest(withGame(cycle(51_000, 16, 5), game(true, "PHI", "0-14")));
		assertEquals(1, entered.size());
	}

	@Test
	public void halftimeClearsTheRedZone()
	{
		final List<List<RedZoneAlert>> actives = new ArrayList<>();
		engine = new Engine(new Engine.Settings()
		{
			public int delaySeconds()
			{
				return 0;
			}

			public boolean startersOnly()
			{
				return true;
			}

			public boolean includeOpponent()
			{
				return true;
			}

			public int bigPlayYards()
			{
				return 20;
			}

			public boolean leagueEnabled(String id)
			{
				return true;
			}

			public String leagueAlias(String id)
			{
				return id;
			}
		}, new Engine.Listener()
		{
			public void released(PlayEvent e)
			{
			}

			public void updated(PlayEvent e)
			{
			}

			public void redZone(List<RedZoneAlert> active, List<RedZoneAlert> enteredNow)
			{
				actives.add(active);
			}
		});
		setUpEngine();
		engine.ingest(withGame(cycle(1000, 10, 5), game(true, "PHI", "0-0")));
		assertEquals(1, actives.get(actives.size() - 1).size());
		Espn.Game half = game(true, null, "0-0");
		half.halftime = true;
		engine.ingest(withGame(cycle(11_000, 10, 5), half));
		assertTrue(actives.get(actives.size() - 1).isEmpty());
	}

	@Test
	public void statsArrivingAPollLateAreStillAttributedToTheRightPlay()
	{
		stat(HURTS, "pass_yd", 100);
		engine.ingest(cycle(1000, 10, 5));
		// Points move (a 10-yd completion, +0.4) but the stat feed still shows the old line.
		engine.ingest(cycle(11_000, 10.4, 5));
		engine.tick(11_500);
		assertTrue("held for the stats", released.isEmpty());
		// Next poll: the stats catch up, and the next play (+0.8) is already in the scores.
		stat(HURTS, "pass_yd", 110);
		engine.ingest(cycle(21_000, 11.2, 5));
		stat(HURTS, "pass_yd", 130);
		engine.ingest(cycle(31_000, 11.2, 5));
		engine.tick(31_000);
		assertEquals(2, released.size());
		assertEquals("10 pass yd +0.4", released.get(0).detail());
		assertEquals("20 pass yd +0.8", released.get(1).detail());
	}

	@Test
	public void unexplainedPlaysAreReleasedAfterTheGrace()
	{
		engine.ingest(cycle(1000, 10, 5));
		engine.ingest(cycle(11_000, 10.4, 5));
		engine.tick(20_000);
		assertTrue(released.isEmpty());
		engine.tick(11_000 + Engine.DETAIL_GRACE_MS);
		assertEquals(1, released.size());
	}

	@Test
	public void bigUnexplainedSwingIsSignificantAndNamesOwners()
	{
		engine.ingest(cycle(1000, 10, 5));
		// +6.3 with no stat line at all (the stat feed lagging past the grace).
		engine.ingest(cycle(11_000, 16.3, 5));
		engine.tick(11_000 + Engine.DETAIL_GRACE_MS);
		assertEquals(1, released.size());
		PlayEvent e = released.get(0);
		assertEquals(Category.BIG_SWING, e.top());
		assertTrue(e.significant());
		assertEquals(Tier.VERY_GOOD, e.visible.get(0).tier);
		assertEquals(Tier.VERY_BAD, e.visible.get(1).tier);
		assertEquals("Team 2", e.visible.get(1).ownerName);
	}

	private static Espn.Game espn(String id, String state, String home, String away)
	{
		Espn.Game g = new Espn.Game();
		g.id = id;
		g.state = state;
		g.home = home;
		g.away = away;
		return g;
	}

	@Test
	public void projectedLeadChangeIsAnnouncedOnceTheFavouriteFlips()
	{
		Map<String, Map<String, Double>> proj = new HashMap<>();
		Map<String, Double> hurts = new HashMap<>();
		hurts.put("pass_yd", 250.0);
		Map<String, Double> kelce = new HashMap<>();
		kelce.put("rec", 10.0);
		kelce.put("rec_yd", 200.0);
		proj.put(HURTS, hurts);
		proj.put(KELCE, kelce);
		engine.setProjections(proj);

		Engine.Cycle c1 = cycle(1000, 10, 5);
		c1.games = new HashMap<>();
		c1.games.put("PHI", espn("1", "pre", "PHI", "DAL"));
		c1.games.put("KC", espn("2", "pre", "KC", "LV"));
		engine.ingest(c1);
		// Hurts goes off and his game ends: RD (mine) flips to me, DYN (his owner's) flips away from me.
		Engine.Cycle c2 = cycle(11_000, 40, 5);
		c2.games = new HashMap<>();
		c2.games.put("PHI", espn("1", "post", "PHI", "DAL"));
		c2.games.put("KC", espn("2", "pre", "KC", "LV"));
		engine.ingest(c2);
		View v = engine.tick(11_000 + Engine.DETAIL_GRACE_MS);

		Tier rd = null;
		Tier dyn = null;
		for (PlayEvent e : released)
		{
			if (e.top() == Category.PROJECTED_LEAD)
			{
				if (RD.equals(e.visible.get(0).leagueId))
				{
					rd = e.tier;
				}
				else
				{
					dyn = e.tier;
				}
			}
		}
		assertEquals(Tier.VERY_GOOD, rd);
		assertEquals(Tier.VERY_BAD, dyn);
		View.Line line = v.lines.get(0);
		assertEquals(40, line.myProjected, 1e-9);
		assertEquals(35, line.oppProjected, 1e-9);
		assertEquals(RD, line.leagueName);
	}

	@Test
	public void sideOfBallListsMyOffenceWithTheBallAndMyDefenceWithout()
	{
		Sleeper.Player dal = new Sleeper.Player();
		dal.player_id = "DAL";
		dal.position = "DEF";
		dal.team = "DAL";
		Map<String, Sleeper.Player> players = new HashMap<>();
		players.put(HURTS, player(HURTS, "Jalen", "Hurts", "QB", "PHI"));
		players.put(KELCE, player(KELCE, "Travis", "Kelce", "TE", "KC"));
		players.put("DAL", dal);
		engine.setPlayers(players);

		Engine.Cycle c = cycle(1000, 10, 5);
		Sleeper.Matchup mine = c.matchups.get(RD).get(0);
		mine.starters = Arrays.asList(HURTS, "DAL");
		mine.players_points.put("DAL", 3.0);
		Espn.Game g = espn("9", "in", "PHI", "DAL");
		g.possession = "PHI";
		c.games = new HashMap<>();
		c.games.put("PHI", g);
		c.games.put("DAL", g);
		engine.ingest(c);

		List<SideOfBall> now = sides.get(sides.size() - 1);
		assertEquals(1, now.size());
		assertEquals("PHI", now.get(0).teamWithBall);
		assertEquals(2, now.get(0).entries.size());
		assertEquals("Jalen Hurts", now.get(0).entries.get(0).name);
		assertTrue(!now.get(0).entries.get(0).defense);
		assertTrue(now.get(0).entries.get(1).defense);

		// Between snaps ESPN drops possession: the last team with the ball is kept.
		Engine.Cycle c2 = cycle(11_000, 10, 5);
		c2.matchups.get(RD).get(0).starters = Arrays.asList(HURTS, "DAL");
		c2.matchups.get(RD).get(0).players_points.put("DAL", 3.0);
		Espn.Game g2 = espn("9", "in", "PHI", "DAL");
		c2.games = new HashMap<>();
		c2.games.put("PHI", g2);
		c2.games.put("DAL", g2);
		engine.ingest(c2);
		assertEquals("PHI", sides.get(sides.size() - 1).get(0).teamWithBall);
	}
}

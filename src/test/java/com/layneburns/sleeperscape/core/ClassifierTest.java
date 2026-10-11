package com.layneburns.sleeperscape.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ClassifierTest
{
	private static Map<String, Double> diff(Object... kv)
	{
		Map<String, Double> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
		}
		return m;
	}

	private static EnumSet<Category> cats(Map<String, Double> d, boolean def)
	{
		return Classifier.categories(d, null, def, 1, 20, null);
	}

	@Test
	public void offence()
	{
		assertEquals(Category.TOUCHDOWN, cats(diff("rec_td", 1, "rec", 1, "rec_yd", 12), false).iterator().next());
		assertEquals(Category.PICK_SIX, cats(diff("pass_int", 1, "pass_int_td", 1), false).iterator().next());
		assertEquals(Category.INTERCEPTION_THROWN, cats(diff("pass_int", 1), false).iterator().next());
		assertEquals(Category.FUMBLE_LOST, cats(diff("fum", 1, "fum_lost", 1), false).iterator().next());
		assertEquals(Category.TWO_POINT, cats(diff("rush_2pt", 1), false).iterator().next());
		Map<String, Double> bonus = new HashMap<>();
		bonus.put("bonus_rush_yd_100", 3.0);
		Map<String, Double> hundred = diff("bonus_rush_yd_100", 1, "rush_yd", 4, "rush_att", 1);
		assertEquals(Category.MILESTONE, Classifier.categories(hundred, null, false, 3.4, 20, bonus).iterator().next());
		// A bonus key the league doesn't pay for is not a milestone (the stat line has these ticking).
		assertFalse(cats(hundred, false).contains(Category.MILESTONE));
		assertTrue(cats(diff("rec", 1, "rec_yd", 25), false).contains(Category.BIG_PLAY));
		assertFalse(cats(diff("rec", 2, "rec_yd", 25), false).contains(Category.BIG_PLAY));
		assertEquals(Category.PLAY, cats(diff("rec", 1, "rec_yd", 8), false).iterator().next());
		assertEquals(Category.SACKED, cats(diff("pass_sack", 1), false).iterator().next());
	}

	@Test
	public void kickerDefenceAndIdp()
	{
		assertEquals(Category.FIELD_GOAL, cats(diff("fgm", 1, "fgm_40_49", 1, "fgm_yds", 45), false).iterator().next());
		assertEquals(Category.FG_MISSED, cats(diff("fgmiss", 1, "fgmiss_50p", 1), false).iterator().next());
		assertEquals(Category.TAKEAWAY, cats(diff("int", 1), true).iterator().next());
		// An offensive player recovering his own fumble is not a takeaway.
		assertFalse(cats(diff("fum_rec", 1), false).contains(Category.TAKEAWAY));
		assertEquals(Category.SACK, cats(diff("sack", 1), true).iterator().next());
		assertEquals(Category.TAKEAWAY, cats(diff("def_st_ff", 1, "def_st_fum_rec", 1), true).iterator().next());
		assertEquals(Category.TOUCHDOWN, cats(diff("def_td", 1, "int", 1), true).iterator().next());
		assertEquals(Category.POINTS_ALLOWED, cats(diff("pts_allow_7_13", -1, "pts_allow_14_20", 1), true).iterator().next());
		assertEquals(Category.SACK, cats(diff("idp_sack", 1, "idp_tkl_solo", 1), false).iterator().next());
		assertEquals(Category.TAKEAWAY, cats(diff("idp_int", 1), false).iterator().next());
		assertEquals(Category.PLAY, cats(diff("idp_tkl_solo", 1), false).iterator().next());
	}

	@Test
	public void corrections()
	{
		assertEquals(Category.TD_OVERTURNED, cats(diff("rush_td", -1, "rush_yd", -3), false).iterator().next());
		assertEquals(Category.CORRECTION, Classifier.categories(diff(), null, false, -2, 20, null).iterator().next());
		// Points up with no stats yet: the stat feed lagging, not a correction.
		assertEquals(Category.PLAY, Classifier.categories(diff(), null, false, 2, 20, null).iterator().next());
	}

	@Test
	public void componentsAreScoredAndSortedHighestFirst()
	{
		Map<String, Double> scoring = new HashMap<>();
		scoring.put("rush_td", 6.0);
		scoring.put("rush_yd", 0.1);
		scoring.put("fum_lost", -2.0);
		List<Classifier.Component> c = Classifier.components(diff("rush_yd", 12, "rush_td", 1, "fum_lost", 1, "rush_att", 1), scoring);
		assertEquals(3, c.size());
		assertEquals("rush TD +6", c.get(0).toString());
		assertEquals("12 rush yd +1.2", c.get(1).toString());
		assertEquals("fumble lost -2", c.get(2).toString());
	}

	@Test
	public void kicksAndTiersFoldIntoOneLine()
	{
		Map<String, Double> scoring = new HashMap<>();
		scoring.put("fgm_40_49", 4.0);
		scoring.put("pts_allow_7_13", 4.0);
		scoring.put("pts_allow_14_20", 1.0);
		List<Classifier.Component> fg = Classifier.components(diff("fgm", 1, "fgm_40_49", 1, "fgm_yds", 45), scoring);
		assertEquals("45-yd FG +4", fg.get(0).toString());
		List<Classifier.Component> pa = Classifier.components(diff("pts_allow_7_13", -1, "pts_allow_14_20", 1), scoring);
		assertEquals(1, pa.size());
		assertEquals("pts allowed -3", pa.get(0).toString());
	}

	@Test
	public void format()
	{
		assertEquals("+7.2", Format.points(7.2));
		assertEquals("-0.48", Format.points(-0.48));
		assertEquals("+6", Format.points(6));
		assertEquals("87.4", Format.score(87.36));
		assertEquals("-3 rush yd", Format.statLabel("rush_yd", -3));
		assertEquals("2x rec TD", Format.statLabel("rec_td", 2));
	}

	@Test
	public void statsParseBothShapes()
	{
		Gson gson = new Gson();
		Map<String, Map<String, Double>> a = Stats.parse(gson, "[{\"player_id\":\"6904\",\"stats\":{\"rush_yd\":12,\"gp\":1},\"player\":{\"x\":\"y\"}}]");
		assertEquals(12, a.get("6904").get("rush_yd"), 1e-9);
		Map<String, Map<String, Double>> b = Stats.parse(gson, "{\"6904\":{\"rush_yd\":12},\"1466\":{\"stats\":{\"rec\":3}}}");
		assertEquals(12, b.get("6904").get("rush_yd"), 1e-9);
		assertEquals(3, b.get("1466").get("rec"), 1e-9);
	}

	@Test
	public void espnScoreboardAndNameKeys()
	{
		String json = "{\"events\":[{\"id\":\"401\",\"date\":\"2026-10-11T17:00Z\",\"competitions\":[{\"status\":{\"clock\":420,\"period\":3,"
			+ "\"type\":{\"state\":\"in\"}},\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"id\":\"21\",\"abbreviation\":\"PHI\"}},"
			+ "{\"homeAway\":\"away\",\"team\":{\"id\":\"28\",\"abbreviation\":\"WSH\"}}],"
			+ "\"situation\":{\"possession\":\"28\",\"isRedZone\":true}}]}]}";
		Map<String, Espn.Game> games = Espn.parseScoreboard(new Gson(), json);
		Espn.Game g = games.get("WAS");
		assertEquals("401", g.id);
		assertEquals("WAS", g.possession);
		assertTrue(g.redZone);
		assertEquals((900 + 420) / 3600.0, g.fractionLeft(), 1e-9);
		assertEquals(java.time.OffsetDateTime.parse("2026-10-11T17:00Z").toInstant().toEpochMilli(), g.kickoff);
		assertEquals("Q3 7:00", g.clockText());

		Sleeper.Player p = new Sleeper.Player();
		p.first_name = "Amon-Ra";
		p.last_name = "St. Brown";
		assertEquals("a.st.brown", Espn.nameKey(p));
		assertTrue(Espn.squash("(Shotgun) J.Goff pass short right to A.St. Brown for 12 yards").contains(Espn.nameKey(p)));
	}

	@Test
	public void winProbability()
	{
		WinProbability ahead = new WinProbability(100, 80);
		ahead.add(true, 10, 0.5);
		ahead.add(false, 10, 0.5);
		assertTrue(ahead.probability() > 0.9);
		assertEquals(0.5, WinProbability.phi(0), 1e-6);
		assertEquals(0.8413, WinProbability.phi(1), 1e-3);
	}

	@Test
	public void mixedPlaysResolveByNetPointsForMe()
	{
		List<Tier> pick = Arrays.asList(Tier.VERY_BAD, Tier.VERY_GOOD);
		// Same pick, my QB in one league (-2) and my opponent's in another (-1 there, so +1 for me): net -1.
		assertEquals(Tier.VERY_BAD, Tier.combine(pick, -1));
		assertEquals(Tier.VERY_GOOD, Tier.combine(pick, 1));
		assertEquals(Tier.CONFLICTED, Tier.combine(pick, 0));
		// The stronger side wins outright.
		assertEquals(Tier.SCORED, Tier.combine(Arrays.asList(Tier.SCORED, Tier.BAD), -5));
	}

	@Test
	public void milestonesAreNamed()
	{
		assertEquals("100 scrimmage yd", Format.milestone("bonus_rush_rec_yd_100"));
		assertEquals("10+ tackles", Format.milestone("bonus_tkl_10p"));
		assertEquals("25 completions", Format.milestone("bonus_pass_cmp_25"));
		assertEquals("300-yd passing", Format.milestone("bonus_pass_yd_300"));
		Map<String, Double> scoring = new HashMap<>();
		scoring.put("bonus_rush_rec_yd_100", 2.0);
		assertEquals("100 scrimmage yd", Classifier.milestone(diff("bonus_rush_rec_yd_100", 1, "rush_yd", 6), scoring));
		// Counters like bonus_fd_rb tick every first down and are never milestones.
		assertEquals(null, Classifier.milestone(diff("bonus_fd_rb", 1), scoring));
		assertEquals("100 scrimmage yd +2", Classifier.components(diff("bonus_rush_rec_yd_100", 1), scoring).get(0).toString());
	}

	@Test
	public void newLongestGainIsABigPlayEvenWhenPlaysMerge()
	{
		Map<String, Double> end = new HashMap<>();
		end.put("rush_lng", 34.0);
		// Two carries merged into one poll: the per-carry rule can't tell, the longest-gain key can.
		Map<String, Double> d = diff("rush_att", 2, "rush_yd", 38, "rush_lng", 15);
		assertTrue(Classifier.categories(d, end, false, 3.8, 20, null).contains(Category.BIG_PLAY));
		end.put("rush_lng", 12.0);
		assertFalse(Classifier.categories(diff("rush_att", 2, "rush_yd", 14, "rush_lng", 3), end, false, 1.4, 20, null)
			.contains(Category.BIG_PLAY));
	}
}

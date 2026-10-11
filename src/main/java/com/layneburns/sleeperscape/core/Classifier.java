package com.layneburns.sleeperscape.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a player's stat change between two polls into categories (what kind of play) and components
 * (each scored stat with the points it was worth, for the "rush TD +6 · 12 rush yd +1.2" line).
 */
public final class Classifier
{
	/** One scored piece of a play. */
	public static final class Component
	{
		public final String label;
		public final double points;

		Component(String label, double points)
		{
			this.label = label;
			this.points = points;
		}

		@Override
		public String toString()
		{
			return label + " " + Format.points(points);
		}
	}

	// Milestone bonuses carry their threshold last: bonus_rush_yd_100, bonus_pass_yd_300, bonus_pass_cmp_25 ...
	private static final Pattern MILESTONE = Pattern.compile("bonus_.*_\\d+p?$");
	// Distance buckets for long gains, where the stat feed reports them.
	private static final Set<String> BIG_PLAY_KEYS = Set.of("rec_20_29", "rec_30_39", "rec_40p", "rush_40p",
		"pass_cmp_40p", "rush_20_29", "rush_30_39");

	private Classifier()
	{
	}

	/**
	 * Every category the change holds, in priority order.
	 *
	 * @param diff         stat change (all keys, scored or not)
	 * @param teamDefense  whether the player is a team DEF (whose int/fum_rec/sack are takeaways)
	 * @param pointsDelta  the points the change was worth (any league), for an unexplained correction
	 * @param bigPlayYards a single gain at least this long is a big play
	 * @param scoring      the league's scoring, or null: a milestone counts only when the league scores it
	 */
	public static EnumSet<Category> categories(Map<String, Double> diff, boolean teamDefense, double pointsDelta,
		int bigPlayYards, Map<String, Double> scoring)
	{
		EnumSet<Category> cats = EnumSet.noneOf(Category.class);
		for (Map.Entry<String, Double> e : diff.entrySet())
		{
			String k = e.getKey();
			double d = e.getValue();
			if (k.startsWith("pts_allow") || k.startsWith("yds_allow"))
			{
				if (k.startsWith("pts_allow"))
				{
					cats.add(Category.POINTS_ALLOWED);
				}
				continue;
			}
			if (isTouchdownKey(k))
			{
				if (d < 0)
				{
					cats.add(Category.TD_OVERTURNED);
				}
				else if (k.equals("pass_int_td"))
				{
					cats.add(Category.PICK_SIX);
				}
				else
				{
					cats.add(Category.TOUCHDOWN);
				}
				continue;
			}
			if (d <= 0)
			{
				continue;
			}
			if (k.endsWith("_2pt"))
			{
				cats.add(Category.TWO_POINT);
			}
			else if (k.startsWith("fgmiss"))
			{
				cats.add(Category.FG_MISSED);
			}
			else if (k.startsWith("fgm"))
			{
				cats.add(Category.FIELD_GOAL);
			}
			else if (k.equals("xpmiss"))
			{
				cats.add(Category.XP_MISSED);
			}
			else if (k.equals("pass_int"))
			{
				cats.add(Category.INTERCEPTION_THROWN);
			}
			else if (k.equals("fum_lost"))
			{
				cats.add(Category.FUMBLE_LOST);
			}
			else if (k.equals("fum"))
			{
				cats.add(Category.FUMBLE);
			}
			else if (k.equals("pass_sack"))
			{
				cats.add(Category.SACKED);
			}
			else if (k.equals("idp_int") || k.equals("idp_fum_rec") || k.equals("idp_ff") || isSpecialTeamsTakeaway(k)
				|| (teamDefense && (k.equals("int") || k.equals("fum_rec") || k.equals("ff"))))
			{
				cats.add(Category.TAKEAWAY);
			}
			else if (k.equals("safe") || k.equals("idp_safe"))
			{
				cats.add(Category.SAFETY);
			}
			else if (k.equals("blk_kick") || k.equals("idp_blk_kick"))
			{
				cats.add(Category.BLOCKED_KICK);
			}
			else if (k.equals("idp_sack") || (teamDefense && k.equals("sack")))
			{
				cats.add(Category.SACK);
			}
			else if (BIG_PLAY_KEYS.contains(k))
			{
				cats.add(Category.BIG_PLAY);
			}
			else if (MILESTONE.matcher(k).matches() && scores(scoring, k))
			{
				// Only bonuses the league pays for: the stat line carries bonus_* keys that tick on ordinary
				// plays. Per-reception premiums (bonus_rec_te) carry no threshold, so they don't match.
				cats.add(Category.MILESTONE);
			}
		}
		// Without distance buckets: one carry, catch or completion that gained bigPlayYards or more.
		if (single(diff, "rush_att", "rush_yd", bigPlayYards) || single(diff, "rec", "rec_yd", bigPlayYards)
			|| single(diff, "pass_cmp", "pass_yd", bigPlayYards))
		{
			cats.add(Category.BIG_PLAY);
		}
		if (cats.isEmpty())
		{
			// Points that went down with no stat to explain them are a correction. Points that went up with no stat
			// are almost always the stat feed lagging the scores by a poll, so they stay a plain play.
			cats.add(diff.isEmpty() && pointsDelta < 0 ? Category.CORRECTION : Category.PLAY);
		}
		return cats;
	}

	private static boolean scores(Map<String, Double> scoring, String key)
	{
		Double w = scoring == null ? null : scoring.get(key);
		return w != null && Math.abs(w) > 1e-9;
	}

	/** Takeaways on kicks and punts: def_st_ff, def_st_fum_rec (team DEF), st_ff, st_fum_rec (players). */
	static boolean isSpecialTeamsTakeaway(String k)
	{
		return (k.startsWith("def_st_") || k.startsWith("st_"))
			&& (k.endsWith("_ff") || k.endsWith("_fum_rec") || k.endsWith("_int"));
	}

	/** True for touchdown counters (rush_td, rec_td_40p, def_td, idp_int_ret_td ...), not e.g. pts_allow. */
	static boolean isTouchdownKey(String k)
	{
		return (k.endsWith("_td") || k.contains("_td_")) && !k.contains("allow") && !k.contains("_lng");
	}

	private static boolean single(Map<String, Double> diff, String countKey, String yardKey, int threshold)
	{
		Double n = diff.get(countKey);
		Double y = diff.get(yardKey);
		return n != null && y != null && Math.abs(n - 1) < 1e-9 && y >= threshold;
	}

	/**
	 * The scored pieces of a change under one league's settings, highest points first. Kicks, points-allowed
	 * and yards-allowed tiers are each folded into one line.
	 */
	public static List<Component> components(Map<String, Double> diff, Map<String, Double> scoring)
	{
		Map<String, double[]> groups = new LinkedHashMap<>();
		Map<String, String> labels = new LinkedHashMap<>();
		if (scoring == null)
		{
			return new ArrayList<>();
		}
		for (Map.Entry<String, Double> e : diff.entrySet())
		{
			String k = e.getKey();
			Double w = scoring.get(k);
			if (w == null || w == 0)
			{
				continue;
			}
			double pts = w * e.getValue();
			if (Math.abs(pts) < 0.005)
			{
				continue;
			}
			String group = groupOf(k);
			groups.computeIfAbsent(group, g -> new double[1])[0] += pts;
			if (!labels.containsKey(group))
			{
				labels.put(group, group.equals(k) ? Format.statLabel(k, e.getValue()) : groupLabel(group, diff));
			}
		}
		List<Component> out = new ArrayList<>();
		for (Map.Entry<String, double[]> g : groups.entrySet())
		{
			double pts = g.getValue()[0];
			if (Math.abs(pts) >= 0.005)
			{
				out.add(new Component(labels.get(g.getKey()), pts));
			}
		}
		out.sort((a, b) -> Double.compare(b.points, a.points));
		return out;
	}

	private static String groupOf(String k)
	{
		if (k.startsWith("fgmiss"))
		{
			return "#fgmiss";
		}
		if (k.startsWith("fgm"))
		{
			return "#fgm";
		}
		if (k.startsWith("pts_allow"))
		{
			return "#pts_allow";
		}
		if (k.startsWith("yds_allow"))
		{
			return "#yds_allow";
		}
		return k;
	}

	private static String groupLabel(String group, Map<String, Double> diff)
	{
		switch (group)
		{
			case "#fgm":
			{
				Double yds = diff.get("fgm_yds");
				Double n = diff.get("fgm");
				if (yds != null && n != null && Math.abs(n - 1) < 1e-9)
				{
					return Format.count(yds) + "-yd FG";
				}
				return "FG";
			}
			case "#fgmiss":
				return "missed FG";
			case "#pts_allow":
				return "pts allowed";
			default:
				return "yds allowed";
		}
	}
}

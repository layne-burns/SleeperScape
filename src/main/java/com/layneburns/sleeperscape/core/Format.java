package com.layneburns.sleeperscape.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Number and stat-label formatting shared by every surface. */
public final class Format
{
	// "{n}" is replaced by the amount; labels without it are prefixed with "2x " when the amount isn't 1.
	private static final Map<String, String> LABELS = new HashMap<>();

	static
	{
		LABELS.put("pass_yd", "{n} pass yd");
		LABELS.put("pass_td", "pass TD");
		LABELS.put("pass_int", "INT thrown");
		LABELS.put("pass_int_td", "pick-six");
		LABELS.put("pass_2pt", "2-pt pass");
		LABELS.put("pass_cmp", "{n} cmp");
		LABELS.put("pass_inc", "{n} inc");
		LABELS.put("pass_att", "{n} att");
		LABELS.put("pass_fd", "pass 1st down");
		LABELS.put("pass_sack", "sacked");
		LABELS.put("rush_yd", "{n} rush yd");
		LABELS.put("rush_td", "rush TD");
		LABELS.put("rush_att", "{n} carry");
		LABELS.put("rush_2pt", "2-pt run");
		LABELS.put("rush_fd", "rush 1st down");
		LABELS.put("rec", "{n} rec");
		LABELS.put("rec_yd", "{n} rec yd");
		LABELS.put("rec_td", "rec TD");
		LABELS.put("rec_2pt", "2-pt catch");
		LABELS.put("rec_fd", "rec 1st down");
		LABELS.put("bonus_rec_te", "TE rec bonus");
		LABELS.put("bonus_rec_rb", "RB rec bonus");
		LABELS.put("bonus_rec_wr", "WR rec bonus");
		LABELS.put("fum", "fumble");
		LABELS.put("fum_lost", "fumble lost");
		LABELS.put("fum_rec_td", "fumble-recovery TD");
		LABELS.put("xpm", "XP");
		LABELS.put("xpmiss", "missed XP");
		LABELS.put("int", "INT");
		LABELS.put("fum_rec", "fumble recovery");
		LABELS.put("ff", "forced fumble");
		LABELS.put("sack", "sack");
		LABELS.put("safe", "safety");
		LABELS.put("blk_kick", "blocked kick");
		LABELS.put("def_td", "DEF TD");
		LABELS.put("def_st_td", "ST TD");
		LABELS.put("st_td", "ST TD");
		LABELS.put("kr_td", "kick-return TD");
		LABELS.put("pr_td", "punt-return TD");
		LABELS.put("def_st_ff", "ST forced fumble");
		LABELS.put("def_st_fum_rec", "ST fumble recovery");
		LABELS.put("st_ff", "ST forced fumble");
		LABELS.put("st_fum_rec", "ST fumble recovery");
		LABELS.put("def_2pt", "DEF 2-pt return");
		LABELS.put("kr_yd", "{n} KR yd");
		LABELS.put("pr_yd", "{n} PR yd");
		LABELS.put("idp_tkl", "tackle");
		LABELS.put("idp_tkl_solo", "solo tackle");
		LABELS.put("idp_tkl_ast", "assisted tackle");
		LABELS.put("idp_tkl_loss", "TFL");
		LABELS.put("idp_sack", "sack");
		LABELS.put("idp_qb_hit", "QB hit");
		LABELS.put("idp_int", "INT");
		LABELS.put("idp_ff", "forced fumble");
		LABELS.put("idp_fum_rec", "fumble recovery");
		LABELS.put("idp_pass_def", "pass defended");
		LABELS.put("idp_safe", "safety");
		LABELS.put("idp_def_td", "DEF TD");
		LABELS.put("idp_blk_kick", "blocked kick");
	}

	private Format()
	{
	}

	// Milestone bonuses, by what comes between "bonus_" and the threshold: bonus_rush_yd_100 -> "100-yd rushing".
	private static final Map<String, String> MILESTONES = new HashMap<>();

	static
	{
		MILESTONES.put("rush_yd", "{n}-yd rushing");
		MILESTONES.put("rec_yd", "{n}-yd receiving");
		MILESTONES.put("pass_yd", "{n}-yd passing");
		MILESTONES.put("rush_rec_yd", "{n} scrimmage yd");
		MILESTONES.put("pass_cmp", "{n} completions");
		MILESTONES.put("rush_att", "{n} carries");
		MILESTONES.put("rec", "{n} catches");
		MILESTONES.put("tkl", "{n} tackles");
		MILESTONES.put("sack", "{n} sacks");
		MILESTONES.put("def_fum_td", "{n}-yd fumble-return TD");
		MILESTONES.put("def_int_td", "{n}-yd pick-six");
		MILESTONES.put("pass_td", "{n}-yd TD pass");
		MILESTONES.put("rush_td", "{n}-yd TD run");
		MILESTONES.put("rec_td", "{n}-yd TD catch");
		MILESTONES.put("fg", "{n}-yd FG");
	}

	/** "bonus_rush_rec_yd_100" -> "100 scrimmage yd", "bonus_tkl_10p" -> "10+ tackles". */
	public static String milestone(String key)
	{
		Matcher m = Pattern.compile("bonus_(.*)_(\\d+)(p?)$").matcher(key);
		if (!m.matches())
		{
			return key.replace('_', ' ');
		}
		String n = m.group(2) + (m.group(3).isEmpty() ? "" : "+");
		String label = MILESTONES.get(m.group(1));
		return label == null ? m.group(1).replace('_', ' ') + " " + n : label.replace("{n}", n);
	}

	/** "+7.2", "-0.48", "+6". */
	public static String points(double pts)
	{
		String s = plain(pts);
		return pts >= 0 ? "+" + s : s;
	}

	/** "87.4", "102", "-1.5": up to two decimals, no trailing zeros. */
	public static String plain(double v)
	{
		BigDecimal d = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
		if (d.scale() < 0)
		{
			d = d.setScale(0, RoundingMode.UNNECESSARY);
		}
		String s = d.toPlainString();
		return s.equals("-0") ? "0" : s;
	}

	/** A score with one decimal, as Sleeper's scoreboard shows it: "87.4". */
	public static String score(double v)
	{
		return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString();
	}

	public static String count(double v)
	{
		return plain(Math.abs(v));
	}

	/** "12 rush yd", "-3 rush yd", "rush TD", "2x rec TD", "corr. rush TD" for a counter that went down. */
	public static String statLabel(String key, double amount)
	{
		String label = LABELS.get(key);
		if (label == null && key.matches("bonus_.*_\\d+p?"))
		{
			return milestone(key);
		}
		if (label == null)
		{
			label = key.startsWith("bonus_") ? "bonus: " + key.substring(6).replace('_', ' ') : key.replace('_', ' ');
		}
		if (label.contains("{n}"))
		{
			// Yardage can legitimately go down (a loss on the play), so it keeps its sign.
			return label.replace("{n}", plain(amount));
		}
		String text = Math.abs(Math.abs(amount) - 1) < 1e-9 ? label : count(amount) + "x " + label;
		return amount < 0 ? "corr. " + text : text;
	}
}

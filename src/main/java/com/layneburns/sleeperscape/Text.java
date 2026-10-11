package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Category;
import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.RedZoneAlert;
import com.layneburns.sleeperscape.core.Tier;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.util.ColorUtil;

/** The words each surface shows for a play. */
final class Text
{
	private Text()
	{
	}

	/** "RD +7.2 · DYN1 -7.2", plain. */
	static String impactsPlain(PlayEvent e)
	{
		return e.visible.stream().map(i -> i.alias + " " + Format.points(i.delta)).collect(Collectors.joining(" · "));
	}

	/** Chat line with RuneLite colour tags: "[RD +7.2] TOUCHDOWN Jalen Hurts: 12-yd TD run". */
	static String chat(PlayEvent e)
	{
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		boolean first = true;
		for (PlayEvent.Impact i : e.visible)
		{
			if (!first)
			{
				sb.append(" | ");
			}
			first = false;
			String part = e.isMatchupEvent() ? i.alias : i.alias + " " + Format.points(i.delta);
			sb.append(ColorUtil.wrapWithColorTag(part, Colors.play(e, i.tier)));
		}
		sb.append("] ");
		if (e.significant() || e.isMatchupEvent())
		{
			sb.append(ColorUtil.wrapWithColorTag(e.headline(), Colors.play(e, e.tier))).append(' ');
		}
		if (!e.isMatchupEvent())
		{
			sb.append(ColorUtil.wrapWithColorTag(e.name(), Colors.play(e, e.tier)));
		}
		String detail = e.detail();
		if (!detail.isEmpty())
		{
			sb.append(e.isMatchupEvent() ? "" : ": ").append(detail);
		}
		return sb.toString();
	}

	/**
	 * Over my character with each reaction: the play, then the leagues it helps and hurts me in, e.g.
	 * "TOUCHDOWN Hurts! Helps RD, hurts DYN1" or "Kelce 8 rec yd... Hurts DYN1". Routine plays are plain yellow;
	 * significant ones coloured (green good, red bad, orange both); my scores wave and glow; being scored on
	 * shakes in red.
	 */
	static Speech speech(PlayEvent e, long now, long length)
	{
		Tier t = e.tier;
		boolean good = t != null && t.positive();
		Speech.Style style;
		if (t == Tier.SCORED)
		{
			style = Speech.Style.SCORED;
		}
		else if (t == Tier.SCORED_ON)
		{
			style = Speech.Style.SCORED_ON;
		}
		else if (e.significant() || e.isMatchupEvent())
		{
			style = Speech.Style.SIGNIFICANT;
		}
		else
		{
			style = Speech.Style.PLAIN;
		}
		Speech s = new Speech(style, now, now + length);
		boolean plain = style == Speech.Style.PLAIN;
		Color main = plain ? Colors.TEXT_YELLOW
			: t == null || t == Tier.CONFLICTED ? Colors.TEXT_ORANGE
			: good ? Colors.TEXT_GREEN : Colors.TEXT_RED;
		s.add(headline(e, good), main);
		if (e.isMatchupEvent())
		{
			return s;
		}
		List<String> helps = new ArrayList<>();
		List<String> hurts = new ArrayList<>();
		for (PlayEvent.Impact i : e.visible)
		{
			if (i.tier == null || i.tier == Tier.CONFLICTED)
			{
				continue;
			}
			(i.tier.positive() ? helps : hurts).add(i.alias);
		}
		if (!helps.isEmpty())
		{
			s.add(" Helps " + String.join(", ", helps), plain ? Colors.TEXT_YELLOW : Colors.TEXT_GREEN);
		}
		if (!hurts.isEmpty())
		{
			s.add((helps.isEmpty() ? " Hurts " : ", hurts ") + String.join(", ", hurts),
				plain ? Colors.TEXT_YELLOW : Colors.TEXT_RED);
		}
		return s;
	}

	/** The play itself: "TOUCHDOWN Hurts!", "Kelce 8 rec yd...", "Took the lead in RD!". */
	static String headline(PlayEvent e, boolean good)
	{
		Tier t = e.tier;
		String alias = e.visible.isEmpty() ? "" : e.visible.get(0).alias;
		if (e.top() == Category.MATCHUP_FINAL)
		{
			return t == Tier.SCORED ? "WON " + alias + "!" : t == Tier.SCORED_ON ? "Lost " + alias + "..." : "Tied " + alias + ".";
		}
		if (e.top() == Category.LEAD_CHANGE)
		{
			return good ? "Took the lead in " + alias + "!" : "Lost the lead in " + alias + "...";
		}
		if (e.top() == Category.PROJECTED_LEAD)
		{
			return good ? "Now projected to win " + alias + "!" : "Now projected to lose " + alias + "...";
		}
		String what;
		if (e.significant())
		{
			what = e.headline() + " " + e.shortName();
		}
		else if (!e.components.isEmpty())
		{
			what = e.shortName() + " " + e.components.get(0).label;
		}
		else
		{
			what = e.shortName() + " " + Format.points(e.visible.get(0).delta);
		}
		return what + (t == null || t == Tier.CONFLICTED ? "." : good ? "!" : "...");
	}

	/** "[RED ZONE] CIN inside the 20 (CIN @ MIA): Chase Brown (UND, KCL)", mine pink, my opponent's red. */
	static String redZoneChat(RedZoneAlert a)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(ColorUtil.wrapWithColorTag("[RED ZONE]", Colors.OPPONENT)).append(' ')
			.append(a.team).append(" inside the 20 (").append(a.game).append("): ");
		boolean first = true;
		for (RedZoneAlert.Entry entry : a.entries)
		{
			if (!first)
			{
				sb.append(", ");
			}
			first = false;
			sb.append(ColorUtil.wrapWithColorTag(entry.name, entry.mine ? Colors.MINE : Colors.OPPONENT))
				.append(" (").append(String.join(", ", entry.leagues)).append(')');
		}
		return sb.toString();
	}

	static String escapeHtml(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}

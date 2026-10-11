package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Category;
import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.RedZoneAlert;
import com.layneburns.sleeperscape.core.Tier;
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
			sb.append(ColorUtil.wrapWithColorTag(e.top().headline, Colors.play(e, e.tier))).append(' ');
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
	 * "TOUCHDOWN Hurts! Helps RD, hurts DYN1" or "Kelce 8 rec yd... Hurts DYN1".
	 */
	static String overhead(PlayEvent e)
	{
		Tier t = e.tier;
		boolean good = t != null && t.positive();
		String alias = e.visible.isEmpty() ? "" : e.visible.get(0).alias;
		if (e.top() == Category.MATCHUP_FINAL)
		{
			return t == Tier.SCORED ? "WON " + alias + "!" : t == Tier.SCORED_ON ? "Lost " + alias + "..." : "Tied " + alias + ".";
		}
		if (e.top() == Category.LEAD_CHANGE)
		{
			return good ? "Took the lead in " + alias + "!" : "Lost the lead in " + alias + "...";
		}
		String what;
		if (e.significant())
		{
			what = e.top().headline + " " + e.shortName();
		}
		else if (!e.components.isEmpty())
		{
			what = e.shortName() + " " + e.components.get(0).label;
		}
		else
		{
			what = e.shortName() + " " + Format.points(e.visible.get(0).delta);
		}
		String end = t == null || t == Tier.CONFLICTED ? "." : good ? "!" : "...";
		return what + end + effects(e);
	}

	/** " Helps RD, DYN1, hurts UND" from each league's tier for me. */
	private static String effects(PlayEvent e)
	{
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
		StringBuilder sb = new StringBuilder();
		if (!helps.isEmpty())
		{
			sb.append(" Helps ").append(String.join(", ", helps));
		}
		if (!hurts.isEmpty())
		{
			sb.append(helps.isEmpty() ? " Hurts " : ", hurts ").append(String.join(", ", hurts));
		}
		return sb.toString();
	}

	static String redZoneChat(RedZoneAlert a)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(ColorUtil.wrapWithColorTag("RED ZONE", Colors.OPPONENT)).append(' ').append(a.team).append(": ");
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

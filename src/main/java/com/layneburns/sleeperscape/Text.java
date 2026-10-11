package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Category;
import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.RedZoneAlert;
import com.layneburns.sleeperscape.core.Tier;
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
			sb.append(ColorUtil.wrapWithColorTag(part, Colors.owner(i.owner)));
		}
		sb.append("] ");
		if (e.significant() || e.isMatchupEvent())
		{
			sb.append(ColorUtil.wrapWithColorTag(e.top().headline, Colors.tier(e.tier))).append(' ');
		}
		if (!e.isMatchupEvent())
		{
			sb.append(ColorUtil.wrapWithColorTag(e.name(), Colors.owner(e.visible.get(0).owner)));
		}
		String detail = e.detail();
		if (!detail.isEmpty())
		{
			sb.append(e.isMatchupEvent() ? "" : ": ").append(detail);
		}
		return sb.toString();
	}

	/** "TOUCHDOWN Hurts!" over my character. */
	static String overhead(PlayEvent e)
	{
		Tier t = e.tier;
		boolean good = t != null && t.positive();
		if (e.top() == Category.MATCHUP_FINAL)
		{
			return t == Tier.SCORED ? "WE WON!" : t == Tier.SCORED_ON ? "We lost..." : "A tie?!";
		}
		if (e.top() == Category.LEAD_CHANGE)
		{
			return good ? "Took the lead!" : "Lost the lead...";
		}
		return e.top().headline + " " + e.shortName() + (good ? "!" : "...");
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

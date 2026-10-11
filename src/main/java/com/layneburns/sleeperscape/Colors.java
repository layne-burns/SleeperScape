package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.Tier;
import java.awt.Color;

/**
 * Pink is mine and the best (my scoring plays; my name), green is good for me, red is bad for me (and my
 * opponent's name).
 */
final class Colors
{
	static final Color MINE = new Color(255, 105, 180);
	static final Color OPPONENT = new Color(232, 52, 52);
	static final Color NEUTRAL = new Color(220, 220, 220);
	/** My score while I lead. */
	static final Color WINNING = new Color(80, 220, 100);
	static final Color MUTED = new Color(150, 150, 150);

	private Colors()
	{
	}

	/** Good for me is green (pink for my scoring plays), bad is red; a wash is neutral. */
	static Color tier(Tier tier)
	{
		if (tier == null || tier == Tier.CONFLICTED)
		{
			return NEUTRAL;
		}
		if (tier == Tier.SCORED)
		{
			return MINE;
		}
		return tier.positive() ? WINNING : OPPONENT;
	}

	/** Colour for a play (or one league's side of it) in the feed, chat and banner: routine plays stay neutral. */
	static Color play(PlayEvent e, Tier tier)
	{
		return e.significant() || e.isMatchupEvent() ? tier(tier) : NEUTRAL;
	}

	// Feed row backgrounds: how a play went for me, at a glance. Significant plays get the stronger tint.
	private static final Color BG_GOOD = new Color(30, 52, 34);
	private static final Color BG_GOOD_STRONG = new Color(34, 78, 42);
	private static final Color BG_BAD = new Color(56, 30, 30);
	private static final Color BG_BAD_STRONG = new Color(88, 34, 34);
	private static final Color BG_MIXED = new Color(62, 46, 22);
	private static final Color BG_MIXED_STRONG = new Color(96, 64, 20);

	/**
	 * Feed row background: green when the play only helps me, red when it only hurts, orange when it does both
	 * (my player in one league, my opponent's in another), else the panel colour.
	 */
	static Color background(PlayEvent e, Color plain)
	{
		boolean good = false;
		boolean bad = false;
		for (PlayEvent.Impact i : e.visible)
		{
			good |= i.tier != null && i.tier.positive();
			bad |= i.tier != null && i.tier.value < 0;
		}
		boolean strong = e.significant() || e.isMatchupEvent();
		if (good && bad)
		{
			return strong ? BG_MIXED_STRONG : BG_MIXED;
		}
		if (good)
		{
			return strong ? BG_GOOD_STRONG : BG_GOOD;
		}
		if (bad)
		{
			return strong ? BG_BAD_STRONG : BG_BAD;
		}
		return plain;
	}

	static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}

package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Owner;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.Tier;
import java.awt.Color;

/** Pink is mine (the top colour across these plugins); red is my opponent's, and bad; green is me winning. */
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

	static Color owner(Owner owner)
	{
		return owner == Owner.MINE ? MINE : OPPONENT;
	}

	/** Good for me reads pink, bad reads red. */
	static Color tier(Tier tier)
	{
		if (tier == null || tier == Tier.CONFLICTED)
		{
			return NEUTRAL;
		}
		return tier.positive() ? MINE : OPPONENT;
	}

	/**
	 * Side-panel feed colour for one side of a play: neutral unless the play is significant; then pink for my
	 * scoring play (TD, 2-pt, FG), green for my other significant plays, red for my opponent's. Matchup events
	 * (lead change, final) follow how they went for me.
	 */
	static Color feed(PlayEvent e, Owner owner)
	{
		if (e.isMatchupEvent())
		{
			if (e.tier == Tier.SCORED)
			{
				return MINE;
			}
			return e.tier == null || e.tier == Tier.CONFLICTED ? NEUTRAL : e.tier.positive() ? WINNING : OPPONENT;
		}
		if (!e.significant())
		{
			return NEUTRAL;
		}
		if (owner != Owner.MINE)
		{
			return OPPONENT;
		}
		return e.top().fixedTier == Tier.SCORED ? MINE : WINNING;
	}

	static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}

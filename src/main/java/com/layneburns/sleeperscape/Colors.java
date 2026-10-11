package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Owner;
import com.layneburns.sleeperscape.core.Tier;
import java.awt.Color;

/** Pink is mine (the top colour across these plugins); red is my opponent's, and bad. */
final class Colors
{
	static final Color MINE = new Color(255, 105, 180);
	static final Color OPPONENT = new Color(232, 52, 52);
	static final Color NEUTRAL = new Color(220, 220, 220);
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

	static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}

package com.layneburns.sleeperscape.core;

/** How a play feels from my side of the matchup; drives the avatar reaction. */
public enum Tier
{
	SCORED_ON(-3),
	VERY_BAD(-2),
	BAD(-1),
	CONFLICTED(0),
	GOOD(1),
	VERY_GOOD(2),
	SCORED(3);

	public final int value;

	Tier(int value)
	{
		this.value = value;
	}

	/** The same play seen from the other side: my opponent's touchdown is me being scored on. */
	public Tier mirror()
	{
		switch (this)
		{
			case SCORED:
				return SCORED_ON;
			case SCORED_ON:
				return SCORED;
			case VERY_GOOD:
				return VERY_BAD;
			case VERY_BAD:
				return VERY_GOOD;
			case GOOD:
				return BAD;
			case BAD:
				return GOOD;
			default:
				return this;
		}
	}

	public boolean positive()
	{
		return value > 0;
	}

	/**
	 * How a play went for me overall, from its per-league tiers: the strongest wins. When good and bad are
	 * equally strong (my QB throws a pick in one league while my opponent owns him in another), my net
	 * fantasy points across those leagues decide; an exact wash is {@link #CONFLICTED} (a shrug).
	 *
	 * @param netForMe sum of the points deltas, counted positive when they help me and negative when they help
	 *                 my opponent
	 */
	public static Tier combine(Iterable<Tier> tiers, double netForMe)
	{
		Tier best = null;
		Tier worst = null;
		for (Tier t : tiers)
		{
			if (best == null || t.value > best.value)
			{
				best = t;
			}
			if (worst == null || t.value < worst.value)
			{
				worst = t;
			}
		}
		if (best == null)
		{
			return null;
		}
		if (best.value > 0 && worst.value < 0 && best.value == -worst.value)
		{
			return netForMe > 0.005 ? best : netForMe < -0.005 ? worst : CONFLICTED;
		}
		return best.value >= -worst.value ? best : worst;
	}
}

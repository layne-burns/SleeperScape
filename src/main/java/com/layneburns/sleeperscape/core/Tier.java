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
	 * The reaction for several tiers at once: the strongest wins, and a tie between strong good and strong
	 * bad (my player scores in one league and is my opponent's in another) is a shrug.
	 */
	public static Tier combine(Iterable<Tier> tiers)
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
		if (best.value >= 2 && worst.value <= -2 && best.value == -worst.value)
		{
			return CONFLICTED;
		}
		return best.value >= -worst.value ? best : worst;
	}
}

package com.layneburns.sleeperscape.core;

/**
 * What kind of play a point change was. Declaration order is priority: when one change holds several
 * (a merged poll), the earliest is the headline. Tiers are from the owner's side; an opponent's play is
 * mirrored. A null tier means "by the sign of the points".
 */
public enum Category
{
	TD_OVERTURNED("TD OVERTURNED", true, Tier.VERY_BAD),
	TOUCHDOWN("TOUCHDOWN", true, Tier.SCORED),
	TWO_POINT("2-PT CONVERSION", true, Tier.SCORED),
	PICK_SIX("PICK-SIX", true, Tier.VERY_BAD),
	FIELD_GOAL("FIELD GOAL", true, Tier.SCORED),
	INTERCEPTION_THROWN("INTERCEPTION", true, Tier.VERY_BAD),
	FUMBLE_LOST("FUMBLE LOST", true, Tier.VERY_BAD),
	TAKEAWAY("TAKEAWAY", true, Tier.VERY_GOOD),
	SAFETY("SAFETY", true, Tier.VERY_GOOD),
	BLOCKED_KICK("BLOCKED KICK", true, Tier.VERY_GOOD),
	SACK("SACK", true, Tier.VERY_GOOD),
	MILESTONE("MILESTONE", true, Tier.VERY_GOOD),
	BIG_PLAY("BIG PLAY", true, Tier.VERY_GOOD),
	FG_MISSED("FG MISSED", true, Tier.BAD),
	POINTS_ALLOWED("POINTS ALLOWED", true, null),
	LEAD_CHANGE("LEAD CHANGE", true, null),
	MATCHUP_FINAL("FINAL", true, null),
	FUMBLE("FUMBLE", false, Tier.BAD),
	SACKED("SACKED", false, Tier.BAD),
	XP_MISSED("XP MISSED", false, Tier.BAD),
	CORRECTION("STAT CORRECTION", false, null),
	PLAY("PLAY", false, null);

	public final String headline;
	public final boolean significant;
	public final Tier fixedTier;

	Category(String headline, boolean significant, Tier fixedTier)
	{
		this.headline = headline;
		this.significant = significant;
		this.fixedTier = fixedTier;
	}

	/** The owner's tier for this category, given the points it was worth to them. */
	public Tier ownerTier(double points)
	{
		if (fixedTier != null)
		{
			return fixedTier;
		}
		return points >= 0 ? Tier.GOOD : Tier.BAD;
	}
}

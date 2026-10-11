package com.layneburns.sleeperscape.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One NFL stat change (or matchup moment) and what it did in each league. Filled in two steps: created when
 * the points move, and finalised (stat breakdown, categories, tiers) when it is released after the delay.
 */
public final class PlayEvent
{
	/** What the play did in one league. */
	public static final class Impact
	{
		public final String leagueId;
		public final Owner owner;
		public final boolean starter;
		public final double delta;
		/** The owner's Sleeper display name in that league. */
		public final String ownerName;
		public Tier tier;
		/** Set at release from the league alias. */
		public String alias;

		Impact(String leagueId, Owner owner, String ownerName, boolean starter, double delta)
		{
			this.leagueId = leagueId;
			this.owner = owner;
			this.ownerName = ownerName;
			this.starter = starter;
			this.delta = delta;
		}
	}

	private static long nextId;

	public final long id = ++nextId;
	/** Sleeper player id, or null for a matchup event (lead change, final). */
	public final String playerId;
	public final Sleeper.Player player;
	public final long observedAt;
	/** Seen across a polling gap: shown in the feed only. */
	public final boolean offline;
	public final List<Impact> impacts = new ArrayList<>();

	public EnumSet<Category> categories = EnumSet.of(Category.PLAY);
	public List<Classifier.Component> components = Collections.emptyList();
	/** ESPN's play text (level 3), once matched. */
	public volatile String playText;
	/** Text for matchup events. */
	public String message;
	/** Impacts that pass the filters, chosen at release. */
	public List<Impact> visible = Collections.emptyList();
	public Tier tier;
	public boolean released;

	PlayEvent(String playerId, Sleeper.Player player, long observedAt, boolean offline)
	{
		this.playerId = playerId;
		this.player = player;
		this.observedAt = observedAt;
		this.offline = offline;
	}

	public Category top()
	{
		return categories.iterator().next();
	}

	public boolean significant()
	{
		for (Category c : categories)
		{
			if (c.significant)
			{
				return true;
			}
		}
		return false;
	}

	public boolean isMatchupEvent()
	{
		return playerId == null;
	}

	/** "Jalen Hurts", "PHI DEF", or the matchup message's league. */
	public String name()
	{
		if (player != null)
		{
			return player.displayName();
		}
		return playerId == null ? "" : playerId;
	}

	public String shortName()
	{
		return player != null ? player.shortName() : name();
	}

	/** "QB PHI", or empty. */
	public String positionTeam()
	{
		if (player == null || player.isTeamDefense())
		{
			return "";
		}
		String pos = player.position == null ? "" : player.position;
		String team = player.team == null ? "FA" : player.team;
		return (pos + " " + team).trim();
	}

	/** The best description available: ESPN text, else the stat breakdown, else nothing (points only). */
	public String detail()
	{
		if (message != null)
		{
			return message;
		}
		String text = playText;
		if (text != null)
		{
			return text;
		}
		return components.stream().map(Classifier.Component::toString).collect(Collectors.joining(" · "));
	}

	/** The stat breakdown alone ("rush TD +6 · 12 rush yd +1.2"), even when ESPN text is shown. */
	public String breakdown()
	{
		return components.stream().map(Classifier.Component::toString).collect(Collectors.joining(" · "));
	}
}

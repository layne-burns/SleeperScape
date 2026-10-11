package com.layneburns.sleeperscape.core;

import java.util.ArrayList;
import java.util.List;

/** A live game where my starters are on the field: offense while their team has the ball, IDP/DEF otherwise. */
public final class SideOfBall
{
	public static final class Entry
	{
		public final String name;
		public final boolean defense;
		public final List<String> leagues = new ArrayList<>();

		Entry(String name, boolean defense)
		{
			this.name = name;
			this.defense = defense;
		}
	}

	public final String game;
	public final String teamWithBall;
	public final boolean redZone;
	public final List<Entry> entries = new ArrayList<>();

	SideOfBall(String game, String teamWithBall, boolean redZone)
	{
		this.game = game;
		this.teamWithBall = teamWithBall;
		this.redZone = redZone;
	}

	/** Identity for "has anything changed", so the panel only redraws on a change. */
	public String key()
	{
		StringBuilder sb = new StringBuilder(game).append('|').append(teamWithBall).append('|').append(redZone);
		for (Entry e : entries)
		{
			sb.append('|').append(e.name).append(e.defense).append(e.leagues);
		}
		return sb.toString();
	}
}

package com.layneburns.sleeperscape.core;

import java.util.ArrayList;
import java.util.List;

/** A team with one of my (or my opponent's) starters has the ball inside the 20. Live, never delayed. */
public final class RedZoneAlert
{
	public static final class Entry
	{
		public final String name;
		public final boolean mine;
		public final List<String> leagues = new ArrayList<>();

		Entry(String name, boolean mine)
		{
			this.name = name;
			this.mine = mine;
		}
	}

	public final String key;
	public final String team;
	public final String game;
	public final List<Entry> entries = new ArrayList<>();

	RedZoneAlert(String key, String team, String game)
	{
		this.key = key;
		this.team = team;
		this.game = game;
	}
}

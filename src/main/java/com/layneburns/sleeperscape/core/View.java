package com.layneburns.sleeperscape.core;

import java.util.Collections;
import java.util.List;

/** What the scoreboard shows at the delayed presentation time. */
public final class View
{
	/** One starter in a matchup, with where their game stands. */
	public static final class Starter
	{
		public enum Status
		{
			PLAYING,
			YET_TO_PLAY,
			DONE,
			/** No game data (ESPN off). */
			UNKNOWN
		}

		public String playerId;
		public String name;
		public String position;
		public String team;
		public double points;
		/** Full-game projection under the league's scoring, or null. */
		public Double projected;
		public Status status = Status.UNKNOWN;
		/** "Q3 7:12", "Half", "Final", "Bye"; empty before kickoff (see {@link #kickoff}). */
		public String statusText = "";
		/** Kickoff, epoch ms, or 0. */
		public long kickoff;
	}

	public static final class Line
	{
		public String leagueId;
		public String alias;
		/** The league's full name. */
		public String leagueName;
		public String myOwner;
		public double my;
		public String oppOwner;
		public Double opp;
		/** 0..1, or null when it can't be estimated (no ESPN clock or no projections yet). */
		public Double winProbability;
		/** Projected finals (current score plus what's still projected), or null like winProbability. */
		public Double myProjected;
		public Double oppProjected;
		/** Starters whose games aren't over, or null without ESPN. */
		public Integer myLeft;
		public Integer oppLeft;
		public List<Starter> myStarters = Collections.emptyList();
		public List<Starter> oppStarters = Collections.emptyList();
	}

	public static final View EMPTY = new View(Collections.emptyList(), 0, null, 0);

	public final List<Line> lines;
	/** Milliseconds until the first delayed frame is due, while still buffering; 0 otherwise. */
	public final long bufferingMs;
	/** A status line (loading, errors), or null. */
	public final String status;
	/** When the frame shown was polled (0 if none): the panel redraws only when this changes. */
	public final long frameAt;

	public View(List<Line> lines, long bufferingMs, String status, long frameAt)
	{
		this.lines = lines;
		this.bufferingMs = bufferingMs;
		this.status = status;
		this.frameAt = frameAt;
	}
}

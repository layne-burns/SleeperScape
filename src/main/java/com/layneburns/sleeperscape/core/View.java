package com.layneburns.sleeperscape.core;

import java.util.Collections;
import java.util.List;

/** What the scoreboard shows at the delayed presentation time. */
public final class View
{
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
	}

	public static final View EMPTY = new View(Collections.emptyList(), 0, null);

	public final List<Line> lines;
	/** Milliseconds until the first delayed frame is due, while still buffering; 0 otherwise. */
	public final long bufferingMs;
	/** A status line (loading, errors), or null. */
	public final String status;

	public View(List<Line> lines, long bufferingMs, String status)
	{
		this.lines = lines;
		this.bufferingMs = bufferingMs;
		this.status = status;
	}
}

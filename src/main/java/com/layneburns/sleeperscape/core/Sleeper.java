package com.layneburns.sleeperscape.core;

import java.util.List;
import java.util.Map;

/**
 * Sleeper API response shapes (read-only, no auth). Field names match the JSON, so Gson fills them
 * directly; anything Sleeper adds is ignored.
 */
public final class Sleeper
{
	private Sleeper()
	{
	}

	public static final class State
	{
		public String season;
		public String league_season;
		public String season_type;
		public int week;
		public int display_week;
	}

	public static final class User
	{
		public String user_id;
		public String username;
		public String display_name;
	}

	public static final class League
	{
		public String league_id;
		public String name;
		public String season;
		public String status;
		public Map<String, Double> scoring_settings;
		public List<String> roster_positions;
	}

	public static final class Roster
	{
		public int roster_id;
		public String owner_id;
		public List<String> co_owners;
		public List<String> players;
		public List<String> starters;
	}

	public static final class LeagueUser
	{
		public String user_id;
		public String display_name;
	}

	public static final class Matchup
	{
		public int roster_id;
		public Integer matchup_id;
		public Double points;
		public Double custom_points;
		public List<String> starters;
		public List<String> players;
		public Map<String, Double> players_points;

		public double total()
		{
			if (custom_points != null)
			{
				return custom_points;
			}
			return points == null ? 0 : points;
		}
	}

	public static final class Player
	{
		public String player_id;
		public String first_name;
		public String last_name;
		public String position;
		public String team;
		public String espn_id;
		public List<String> fantasy_positions;

		public boolean isTeamDefense()
		{
			return "DEF".equals(position);
		}

		/** "Jalen Hurts", or "PHI DEF" for a team defense. */
		public String displayName()
		{
			if (isTeamDefense())
			{
				return (team != null ? team : player_id) + " DEF";
			}
			String first = first_name == null ? "" : first_name;
			String last = last_name == null ? "" : last_name;
			String full = (first + " " + last).trim();
			return full.isEmpty() ? player_id : full;
		}

		/** "Hurts", or "PHI DEF". */
		public String shortName()
		{
			if (isTeamDefense() || last_name == null || last_name.isBlank())
			{
				return displayName();
			}
			return last_name;
		}
	}
}

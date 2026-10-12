package com.layneburns.sleeperscape.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One league as loaded at start: settings, rosters, owners, and which roster is mine. */
public final class LeagueContext
{
	public final Sleeper.League league;
	public final int myRosterId;
	private final Map<Integer, Sleeper.Roster> rosters = new HashMap<>();
	private final Map<String, Sleeper.LeagueUser> users = new HashMap<>();

	public LeagueContext(Sleeper.League league, List<Sleeper.Roster> rosters, List<Sleeper.LeagueUser> users,
		String myUserId)
	{
		this.league = league;
		int mine = -1;
		if (rosters != null)
		{
			for (Sleeper.Roster r : rosters)
			{
				this.rosters.put(r.roster_id, r);
				boolean owner = myUserId != null && myUserId.equals(r.owner_id);
				boolean coOwner = myUserId != null && r.co_owners != null && r.co_owners.contains(myUserId);
				if (mine < 0 && (owner || coOwner))
				{
					mine = r.roster_id;
				}
			}
		}
		if (users != null)
		{
			for (Sleeper.LeagueUser u : users)
			{
				this.users.put(u.user_id, u);
			}
		}
		this.myRosterId = mine;
	}

	public String id()
	{
		return league.league_id;
	}

	public String name()
	{
		return league.name == null ? league.league_id : league.name;
	}

	public Map<String, Double> scoring()
	{
		return league.scoring_settings;
	}

	/** Every player on any roster in this league (starters included; empty slots, "0", left out). */
	public Set<String> rosteredPlayers()
	{
		Set<String> ids = new HashSet<>();
		for (Sleeper.Roster r : rosters.values())
		{
			if (r.players != null)
			{
				ids.addAll(r.players);
			}
			if (r.starters != null)
			{
				ids.addAll(r.starters);
			}
		}
		ids.remove("0");
		ids.remove(null);
		return ids;
	}

	/** The owner's display name, e.g. "layneburns". */
	public String ownerName(int rosterId)
	{
		Sleeper.LeagueUser u = user(rosterId);
		return u == null || u.display_name == null ? "Team " + rosterId : u.display_name;
	}

	private Sleeper.LeagueUser user(int rosterId)
	{
		Sleeper.Roster r = rosters.get(rosterId);
		return r == null || r.owner_id == null ? null : users.get(r.owner_id);
	}

	/** Who owns a roster from my side of this week's matchup. */
	public Owner owner(int rosterId, int opponentRosterId)
	{
		if (rosterId == myRosterId)
		{
			return Owner.MINE;
		}
		return rosterId == opponentRosterId ? Owner.OPPONENT : Owner.OTHER;
	}
}

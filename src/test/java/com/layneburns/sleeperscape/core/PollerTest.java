package com.layneburns.sleeperscape.core;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class PollerTest
{
	private static Sleeper.Player player(String id, String team)
	{
		Sleeper.Player p = new Sleeper.Player();
		p.player_id = id;
		p.team = team;
		return p;
	}

	@Test
	public void trimKeepsPlayersOnATeamAndRosteredFreeAgents()
	{
		Map<String, Sleeper.Player> all = new HashMap<>();
		all.put("1", player("1", "PHI"));
		all.put("2", player("2", null));
		all.put("3", player("3", null));
		all.put("PHI", player("PHI", "PHI"));
		Map<String, Sleeper.Player> kept = Poller.trim(all, new HashSet<>(Arrays.asList("3", "0")));
		assertEquals(new HashSet<>(Arrays.asList("1", "3", "PHI")), kept.keySet());
	}

	@Test
	public void rosteredPlayersSpanEveryRosterWithoutEmptySlots()
	{
		Sleeper.Roster a = new Sleeper.Roster();
		a.roster_id = 1;
		a.players = Arrays.asList("1", "2");
		a.starters = Arrays.asList("1", "0");
		Sleeper.Roster b = new Sleeper.Roster();
		b.roster_id = 2;
		b.players = Arrays.asList("3");
		Set<String> ids = new LeagueContext(new Sleeper.League(), Arrays.asList(a, b), null, null).rosteredPlayers();
		assertEquals(new HashSet<>(Arrays.asList("1", "2", "3")), ids);
	}
}

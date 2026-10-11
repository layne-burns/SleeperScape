package com.layneburns.sleeperscape.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ESPN's unofficial public endpoints: the scoreboard (game state, clock, possession, red zone) and a game's
 * summary (play-by-play text). Parsed defensively; anything missing simply leaves a field empty.
 */
public final class Espn
{
	public static final String SCOREBOARD = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard";

	public static String summaryUrl(String eventId)
	{
		return "https://site.api.espn.com/apis/site/v2/sports/football/nfl/summary?event=" + eventId;
	}

	public static final class Game
	{
		public String id;
		/** "pre", "in" or "post". */
		public String state = "pre";
		public int period;
		public double clockSeconds;
		public String home;
		public String away;
		/**
		 * Sleeper abbreviation of the team with the ball. ESPN only sends it while a snap is pending: it is
		 * missing at halftime, at dead balls and right after a score.
		 */
		public String possession;
		/** ESPN keeps its last value through dead balls, so it only counts alongside {@link #possession}. */
		public boolean redZone;
		public boolean halftime;
		/** "away-home" score, to notice a score while possession is missing. */
		public String score = "";

		public boolean live()
		{
			return "in".equals(state);
		}

		public boolean over()
		{
			return "post".equals(state);
		}

		/** Share of the game still to play: 1 before kickoff, 0 when final, a sliver in overtime. */
		public double fractionLeft()
		{
			if (over())
			{
				return 0;
			}
			if (!live())
			{
				return 1;
			}
			if (period >= 5)
			{
				return 0.05;
			}
			double left = (4 - Math.max(1, period)) * 900 + Math.max(0, Math.min(900, clockSeconds));
			return Math.max(0, Math.min(1, left / 3600));
		}

		public String label()
		{
			return away + " @ " + home;
		}
	}

	public static final class Play
	{
		public String id;
		public long sequence;
		public String text;
		public boolean scoring;
		public int yards;
	}

	private Espn()
	{
	}

	/** Games keyed by each team's Sleeper abbreviation (both teams map to the same game). */
	public static Map<String, Game> parseScoreboard(Gson gson, String json)
	{
		Map<String, Game> byTeam = new HashMap<>();
		JsonObject root = obj(gson.fromJson(json, JsonElement.class));
		for (JsonElement ev : arr(root, "events"))
		{
			JsonObject event = obj(ev);
			JsonArray comps = arr(event, "competitions");
			if (event == null || comps.size() == 0)
			{
				continue;
			}
			JsonObject comp = obj(comps.get(0));
			Game g = new Game();
			g.id = str(event, "id");
			JsonObject status = obj(comp.get("status"));
			if (status == null)
			{
				status = obj(event.get("status"));
			}
			if (status != null)
			{
				g.period = (int) num(status, "period");
				g.clockSeconds = num(status, "clock");
				JsonObject type = obj(status.get("type"));
				if (type != null && str(type, "state") != null)
				{
					g.state = str(type, "state").toLowerCase(Locale.ROOT);
				}
				g.halftime = type != null && "STATUS_HALFTIME".equals(str(type, "name"));
			}
			Map<String, String> abbrById = new HashMap<>();
			String homeScore = null;
			String awayScore = null;
			for (JsonElement c : arr(comp, "competitors"))
			{
				JsonObject competitor = obj(c);
				JsonObject team = competitor == null ? null : obj(competitor.get("team"));
				if (team == null)
				{
					continue;
				}
				String abbr = Teams.fromEspn(str(team, "abbreviation"));
				String teamId = str(team, "id");
				if (teamId == null)
				{
					teamId = str(competitor, "id");
				}
				abbrById.put(teamId, abbr);
				String points = str(competitor, "score");
				if ("home".equals(str(competitor, "homeAway")))
				{
					g.home = abbr;
					homeScore = points;
				}
				else
				{
					g.away = abbr;
					awayScore = points;
				}
			}
			g.score = awayScore + "-" + homeScore;
			JsonObject situation = obj(comp.get("situation"));
			if (situation != null && g.live())
			{
				g.possession = abbrById.get(str(situation, "possession"));
				JsonElement rz = situation.get("isRedZone");
				g.redZone = rz != null && rz.isJsonPrimitive() && rz.getAsBoolean();
			}
			if (g.home != null)
			{
				byTeam.put(g.home, g);
			}
			if (g.away != null)
			{
				byTeam.put(g.away, g);
			}
		}
		return byTeam;
	}

	/** Every play in a game summary, oldest first, without duplicates. */
	public static List<Play> parsePlays(Gson gson, String json)
	{
		JsonObject root = obj(gson.fromJson(json, JsonElement.class));
		Map<String, Play> plays = new LinkedHashMap<>();
		JsonObject drives = root == null ? null : obj(root.get("drives"));
		List<JsonArray> lists = new ArrayList<>();
		if (drives != null)
		{
			for (JsonElement d : arr(drives, "previous"))
			{
				lists.add(arr(obj(d), "plays"));
			}
			lists.add(arr(obj(drives.get("current")), "plays"));
		}
		lists.add(arr(root, "scoringPlays"));
		long index = 0;
		for (JsonArray list : lists)
		{
			for (JsonElement p : list)
			{
				JsonObject o = obj(p);
				String id = str(o, "id");
				String text = str(o, "text");
				if (id == null || text == null)
				{
					continue;
				}
				Play play = plays.get(id);
				if (play == null)
				{
					play = new Play();
					play.id = id;
					play.text = text;
					String seq = str(o, "sequenceNumber");
					long parsed = parseLong(seq);
					play.sequence = parsed >= 0 ? parsed : 1_000_000_000L + index;
					plays.put(id, play);
				}
				index++;
				JsonElement scoring = o.get("scoringPlay");
				play.scoring |= scoring != null && scoring.isJsonPrimitive() && scoring.getAsBoolean();
				play.scoring |= list == lists.get(lists.size() - 1);
				play.yards = Math.max(play.yards, (int) num(o, "statYardage"));
			}
		}
		List<Play> out = new ArrayList<>(plays.values());
		out.sort((a, b) -> Long.compare(a.sequence, b.sequence));
		return out;
	}

	/**
	 * How ESPN abbreviates a player in play text, squashed for matching: "J.Hurts" and "A.St. Brown" become
	 * "j.hurts" and "a.st.brown". Match against {@link #squash(String)} of the text.
	 */
	public static String nameKey(Sleeper.Player p)
	{
		if (p == null || p.isTeamDefense() || p.first_name == null || p.first_name.isEmpty() || p.last_name == null)
		{
			return null;
		}
		String last = p.last_name.replaceAll("(?i)\\s+(jr\\.?|sr\\.?|ii|iii|iv|v)$", "");
		return squash(p.first_name.charAt(0) + "." + last);
	}

	public static String squash(String s)
	{
		return s.toLowerCase(Locale.ROOT).replace(" ", "").replace("'", "");
	}

	private static long parseLong(String s)
	{
		if (s == null)
		{
			return -1;
		}
		try
		{
			return Long.parseLong(s.trim());
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	private static JsonObject obj(JsonElement e)
	{
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static JsonArray arr(JsonObject o, String key)
	{
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
	}

	private static String str(JsonObject o, String key)
	{
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static double num(JsonObject o, String key)
	{
		JsonElement e = o == null ? null : o.get(key);
		if (e == null || !e.isJsonPrimitive())
		{
			return 0;
		}
		try
		{
			return e.getAsDouble();
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}
}

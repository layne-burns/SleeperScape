package com.layneburns.sleeperscape.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-player stat lines (from the unofficial stats and projections endpoints) and how a league scores them.
 * The endpoints have answered in two shapes over the years, so both are accepted: an array of
 * {@code {player_id, stats: {...}}}, or an object keyed by player id whose values are either the stat map
 * or {@code {stats: {...}}}.
 */
public final class Stats
{
	private Stats()
	{
	}

	public static Map<String, Map<String, Double>> parse(Gson gson, String json)
	{
		return parse(json, null);
	}

	/**
	 * Streams the JSON rather than building a tree: the stats array runs to ~1.3 MB and the projections to
	 * several MB, every poll, for thousands of players.
	 *
	 * @param only the player ids to keep, or null for every player; other players' lines are skipped unread
	 */
	public static Map<String, Map<String, Double>> parse(String json, Set<String> only)
	{
		Map<String, Map<String, Double>> out = new HashMap<>();
		if (json == null)
		{
			return out;
		}
		try (JsonReader r = new JsonReader(new StringReader(json)))
		{
			r.setLenient(true);
			JsonToken root = r.peek();
			if (root == JsonToken.BEGIN_ARRAY)
			{
				r.beginArray();
				while (r.hasNext())
				{
					if (r.peek() == JsonToken.BEGIN_OBJECT)
					{
						row(r, only, out);
					}
					else
					{
						r.skipValue();
					}
				}
				r.endArray();
			}
			else if (root == JsonToken.BEGIN_OBJECT)
			{
				r.beginObject();
				while (r.hasNext())
				{
					String pid = r.nextName();
					if (r.peek() == JsonToken.BEGIN_OBJECT && (only == null || only.contains(pid)))
					{
						out.put(pid, line(r));
					}
					else
					{
						r.skipValue();
					}
				}
				r.endObject();
			}
		}
		catch (EOFException e)
		{
			// An empty body: nothing, as before.
		}
		catch (IOException | IllegalStateException e)
		{
			throw new JsonSyntaxException(e);
		}
		return out;
	}

	/** One {@code {player_id, stats: {...}, ...}} row; the id may come after the stats, so both are read. */
	private static void row(JsonReader r, Set<String> only, Map<String, Map<String, Double>> out) throws IOException
	{
		String pid = null;
		Map<String, Double> stats = null;
		boolean skip = false;
		r.beginObject();
		while (r.hasNext())
		{
			String k = r.nextName();
			JsonToken t = r.peek();
			if (k.equals("player_id") && (t == JsonToken.STRING || t == JsonToken.NUMBER))
			{
				pid = r.nextString();
				skip = only != null && !only.contains(pid);
			}
			else if (k.equals("stats") && t == JsonToken.BEGIN_OBJECT && !skip)
			{
				stats = numbers(r);
			}
			else
			{
				r.skipValue();
			}
		}
		r.endObject();
		if (pid != null && stats != null && !skip)
		{
			out.put(pid, stats);
		}
	}

	/** A value of the keyed shape: the stat map itself, or {@code {stats: {...}}}. */
	private static Map<String, Double> line(JsonReader r) throws IOException
	{
		Map<String, Double> flat = new HashMap<>();
		Map<String, Double> nested = null;
		r.beginObject();
		while (r.hasNext())
		{
			String k = r.nextName();
			JsonToken t = r.peek();
			if (t == JsonToken.NUMBER)
			{
				flat.put(k, r.nextDouble());
			}
			else if (k.equals("stats") && t == JsonToken.BEGIN_OBJECT)
			{
				nested = numbers(r);
			}
			else
			{
				r.skipValue();
			}
		}
		r.endObject();
		return nested != null ? nested : flat;
	}

	private static Map<String, Double> numbers(JsonReader r) throws IOException
	{
		Map<String, Double> m = new HashMap<>();
		r.beginObject();
		while (r.hasNext())
		{
			String k = r.nextName();
			if (r.peek() == JsonToken.NUMBER)
			{
				m.put(k, r.nextDouble());
			}
			else
			{
				r.skipValue();
			}
		}
		r.endObject();
		return m;
	}

	/** Fantasy points of a stat line under a league's scoring settings (Sleeper scores linearly per stat key). */
	public static double points(Map<String, Double> stats, Map<String, Double> scoring)
	{
		if (stats == null || scoring == null)
		{
			return 0;
		}
		double total = 0;
		for (Map.Entry<String, Double> e : stats.entrySet())
		{
			Double weight = scoring.get(e.getKey());
			if (weight != null && e.getValue() != null)
			{
				total += weight * e.getValue();
			}
		}
		return total;
	}

	/** {@code to - from}, per key, leaving out keys that did not change. */
	public static Map<String, Double> diff(Map<String, Double> from, Map<String, Double> to)
	{
		Map<String, Double> a = from == null ? Collections.emptyMap() : from;
		Map<String, Double> b = to == null ? Collections.emptyMap() : to;
		Map<String, Double> d = new HashMap<>();
		for (Map.Entry<String, Double> e : b.entrySet())
		{
			double delta = val(e.getValue()) - val(a.get(e.getKey()));
			if (Math.abs(delta) > 1e-9)
			{
				d.put(e.getKey(), delta);
			}
		}
		for (Map.Entry<String, Double> e : a.entrySet())
		{
			if (!b.containsKey(e.getKey()) && Math.abs(val(e.getValue())) > 1e-9)
			{
				d.put(e.getKey(), -val(e.getValue()));
			}
		}
		return d;
	}

	private static double val(Double d)
	{
		return d == null ? 0 : d;
	}
}

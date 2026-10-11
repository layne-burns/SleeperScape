package com.layneburns.sleeperscape.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
		Map<String, Map<String, Double>> out = new HashMap<>();
		JsonElement root = gson.fromJson(json, JsonElement.class);
		if (root == null)
		{
			return out;
		}
		if (root.isJsonArray())
		{
			JsonArray rows = root.getAsJsonArray();
			for (JsonElement row : rows)
			{
				if (!row.isJsonObject())
				{
					continue;
				}
				JsonObject o = row.getAsJsonObject();
				JsonElement id = o.get("player_id");
				JsonElement stats = o.get("stats");
				if (id != null && id.isJsonPrimitive() && stats != null && stats.isJsonObject())
				{
					out.put(id.getAsString(), numbers(stats.getAsJsonObject()));
				}
			}
		}
		else if (root.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet())
			{
				if (!e.getValue().isJsonObject())
				{
					continue;
				}
				JsonObject o = e.getValue().getAsJsonObject();
				JsonElement stats = o.get("stats");
				out.put(e.getKey(), numbers(stats != null && stats.isJsonObject() ? stats.getAsJsonObject() : o));
			}
		}
		return out;
	}

	private static Map<String, Double> numbers(JsonObject o)
	{
		Map<String, Double> m = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : o.entrySet())
		{
			JsonElement v = e.getValue();
			if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber())
			{
				m.put(e.getKey(), v.getAsDouble());
			}
		}
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

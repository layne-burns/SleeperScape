package com.layneburns.sleeperscape.core;

/** ESPN and Sleeper mostly share NFL team abbreviations; this maps the exceptions to Sleeper's. */
public final class Teams
{
	private Teams()
	{
	}

	public static String fromEspn(String abbr)
	{
		if (abbr == null)
		{
			return null;
		}
		switch (abbr.toUpperCase())
		{
			case "WSH":
				return "WAS";
			case "LA":
				return "LAR";
			default:
				return abbr.toUpperCase();
		}
	}
}

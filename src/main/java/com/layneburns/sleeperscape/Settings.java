package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Engine;
import com.layneburns.sleeperscape.core.Poller;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/** Config as the core sees it, including the per-league toggles and aliases set from the side panel. */
class Settings implements Engine.Settings, Poller.Config
{
	private final SleeperScapeConfig config;
	private final ConfigManager configManager;
	private volatile Map<String, String> names = Collections.emptyMap();

	Settings(SleeperScapeConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	static String enabledKey(String leagueId)
	{
		return "league." + leagueId + ".enabled";
	}

	static String aliasKey(String leagueId)
	{
		return "league." + leagueId + ".alias";
	}

	@Override
	public int delaySeconds()
	{
		return config.delaySeconds();
	}

	@Override
	public boolean startersOnly()
	{
		return config.startersOnly();
	}

	@Override
	public boolean includeOpponent()
	{
		return config.includeOpponent();
	}

	@Override
	public int bigPlayYards()
	{
		return config.bigPlayYards();
	}

	@Override
	public boolean leagueEnabled(String leagueId)
	{
		return !"false".equals(configManager.getConfiguration(SleeperScapeConfig.GROUP, enabledKey(leagueId)));
	}

	void setLeagueEnabled(String leagueId, boolean enabled)
	{
		configManager.setConfiguration(SleeperScapeConfig.GROUP, enabledKey(leagueId), String.valueOf(enabled));
	}

	@Override
	public String leagueAlias(String leagueId)
	{
		String alias = configManager.getConfiguration(SleeperScapeConfig.GROUP, aliasKey(leagueId));
		return alias == null || alias.isBlank() ? defaultAlias(leagueId) : alias.trim();
	}

	void setLeagueAlias(String leagueId, String alias)
	{
		if (alias == null || alias.isBlank())
		{
			configManager.unsetConfiguration(SleeperScapeConfig.GROUP, aliasKey(leagueId));
		}
		else
		{
			configManager.setConfiguration(SleeperScapeConfig.GROUP, aliasKey(leagueId), alias.trim());
		}
	}

	/** League names, for default aliases. */
	void setLeagueNames(Map<String, String> names)
	{
		this.names = names;
	}

	/** "Dynasty Degenerates" -> "DD"; a single word -> its first four letters. */
	String defaultAlias(String leagueId)
	{
		String name = names.get(leagueId);
		if (name == null || name.isBlank())
		{
			return leagueId.length() > 4 ? leagueId.substring(leagueId.length() - 4) : leagueId;
		}
		StringBuilder initials = new StringBuilder();
		for (String word : name.trim().split("[\\s_\\-]+"))
		{
			if (!word.isEmpty())
			{
				initials.append(Character.isDigit(word.charAt(0)) ? word : word.substring(0, 1).toUpperCase());
			}
		}
		if (initials.length() >= 2)
		{
			return initials.length() > 5 ? initials.substring(0, 5) : initials.toString();
		}
		String word = name.trim();
		return (word.length() > 4 ? word.substring(0, 4) : word).toUpperCase();
	}

	@Override
	public String username()
	{
		return config.username();
	}

	@Override
	public List<String> extraLeagueIds()
	{
		List<String> ids = new ArrayList<>();
		for (String s : config.extraLeagueIds().split("[,\\s]+"))
		{
			if (!s.isBlank())
			{
				ids.add(s.trim());
			}
		}
		return ids;
	}

	@Override
	public int pollSeconds()
	{
		return config.pollSeconds();
	}

	@Override
	public boolean useEspn()
	{
		return config.useEspn();
	}

	@Override
	public int weekOverride()
	{
		return config.weekOverride();
	}
}

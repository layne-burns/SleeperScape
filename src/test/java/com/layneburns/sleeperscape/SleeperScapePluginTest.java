package com.layneburns.sleeperscape;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SleeperScapePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SleeperScapePlugin.class);
		RuneLite.main(args);
	}
}

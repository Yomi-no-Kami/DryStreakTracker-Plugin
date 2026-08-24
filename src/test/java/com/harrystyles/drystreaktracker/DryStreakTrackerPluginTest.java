package com.harrystyles.drystreaktracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DryStreakTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(DryStreakTrackerPlugin.class);
		RuneLite.main(args);
	}
}
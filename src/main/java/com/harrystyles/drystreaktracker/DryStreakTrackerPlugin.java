package com.harrystyles.drystreaktracker;

import com.harrystyles.drystreaktracker.detection.LootDetectionService;
import com.harrystyles.drystreaktracker.encounter.EncounterDefinitionLoader;
import com.harrystyles.drystreaktracker.encounter.EncounterRegistry;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;
import com.harrystyles.drystreaktracker.ui.DryStreakSidebarPanel;
import com.harrystyles.drystreaktracker.ui.notification.DryStreakNotificationManager;

import java.awt.image.BufferedImage;

import javax.inject.Inject;
import javax.swing.SwingUtilities;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Dry Streak Tracker",
		description = "Tracks your dry streaks at bosses and raids"
)
public class DryStreakTrackerPlugin extends Plugin
{
	@Inject
	private EncounterRegistry encounterRegistry;

	@Inject
	private EncounterDefinitionLoader definitionLoader;

	@Inject
	private EncounterTrackerManager trackerManager;

	private LootDetectionService lootDetectionService;

	@Inject
	private DryStreakNotificationManager notificationManager;

	@Inject
	private ClientToolbar clientToolbar;

	private DryStreakSidebarPanel sidebarPanel;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private NavigationButton navigationButton;

	@Provides
	DryStreakTrackerConfig provideConfig(
			ConfigManager configManager)
	{
		return configManager.getConfig(
				DryStreakTrackerConfig.class
		);
	}

	@Override
	protected void startUp()
	{
		log.info("Dry Streak Tracker starting...");

		/*
		 * Create Swing-dependent components after RuneLite's
		 * UI/look-and-feel has been initialized.
		 */
		sidebarPanel = injector.getInstance(DryStreakSidebarPanel.class);
		lootDetectionService = injector.getInstance(LootDetectionService.class);

		definitionLoader.loadInto(encounterRegistry);

		log.info(
				"Loaded {} encounter definitions",
				encounterRegistry.size()
		);

		trackerManager.start();

		BufferedImage icon =
				ImageUtil.loadImageResource(
						getClass(),
						"/icon.png"
				);

		navigationButton =
				NavigationButton.builder()
						.tooltip(
								"Dry Streak Tracker"
						)
						.icon(
								icon
						)
						.priority(
								5
						)
						.panel(
								sidebarPanel
						)
						.build();

		clientToolbar.addNavigation(
				navigationButton
		);

		notificationManager.start();

		sidebarPanel.setLoggedIn(
				false
		);

		if (client.getGameState()
				== GameState.LOGGED_IN)
		{
			startCurrentPlayer();
		}
		else
		{
			SwingUtilities.invokeLater(
					() ->
							sidebarPanel.setLoggedIn(
									false
							)
			);
		}

		log.info(
				"Dry Streak Tracker started"
		);
	}

	@Override
	protected void shutDown()
	{
		log.info(
				"Dry Streak Tracker shutting down..."
		);

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(
					navigationButton
			);

			navigationButton =
					null;
		}

		notificationManager.stop();

		trackerManager.stop();

		encounterRegistry.clear();

		log.info(
				"Dry Streak Tracker stopped"
		);
	}

	@Subscribe
	public void onGameStateChanged(
			GameStateChanged event)
	{
		if (event == null)
		{
			return;
		}

		GameState gameState =
				event.getGameState();

		if (gameState == GameState.LOGGED_IN)
		{
			startCurrentPlayer();

			return;
		}

		if (gameState == GameState.LOGIN_SCREEN)
		{
			trackerManager.stopForPlayer();

			SwingUtilities.invokeLater(
					() ->
							sidebarPanel.setLoggedIn(
									false
							)
			);
		}
	}

	private void startCurrentPlayer()
	{
		clientThread.invokeLater(
				() ->
				{
					if (client.getLocalPlayer()
							== null)
					{
						clientThread.invokeLater(
								this::startCurrentPlayer
						);

						return;
					}

					String playerName =
							client.getLocalPlayer()
									.getName();

					if (playerName == null
							|| playerName.trim()
							.isEmpty())
					{
						clientThread.invokeLater(
								this::startCurrentPlayer
						);

						return;
					}

					log.info(
							"Logged in as {}",
							playerName
					);

					trackerManager.startForPlayer(
							playerName
					);

					SwingUtilities.invokeLater(
							() ->
							{
								sidebarPanel.setLoggedIn(
										true
								);

								sidebarPanel.refresh();
							}
					);

					sidebarPanel.refreshItemDisplayData();
				}
		);
	}

	/**
	 * NPC loot.
	 */
	@Subscribe
	public void onNpcLootReceived(
			NpcLootReceived event)
	{
		lootDetectionService.handleNpcLootReceived(
				event
		);
	}

	/**
	 * Generic non-NPC loot.
	 *
	 * The detection service ignores LootRecordType.NPC
	 * to prevent duplicate processing.
	 */
	@Subscribe
	public void onLootReceived(
			LootReceived event)
	{
		lootDetectionService.handleLootReceived(
				event
		);
	}
}
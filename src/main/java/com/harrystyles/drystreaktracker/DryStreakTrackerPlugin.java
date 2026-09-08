package com.harrystyles.drystreaktracker;

import com.harrystyles.drystreaktracker.cosmetic.SmokeLootbeamManager;
import com.harrystyles.drystreaktracker.detection.LootDetectionService;
import com.harrystyles.drystreaktracker.encounter.*;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;
import com.harrystyles.drystreaktracker.ui.DryStreakSidebarPanel;
import com.harrystyles.drystreaktracker.ui.notification.DryStreakNotificationManager;

import java.awt.image.BufferedImage;

import javax.inject.Inject;
import javax.swing.*;

import com.google.inject.Provides;

import com.harrystyles.drystreaktracker.wiki.WikiDropService;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = "Dry Streak Tracker", description = "Tracks your dry streaks at Pvm/Skilling content."
)
public class DryStreakTrackerPlugin extends Plugin {
    @Inject
    private EncounterRegistry encounterRegistry;

    @Inject
    private EncounterDefinitionLoader definitionLoader;

    @Inject
    private EncounterTrackerManager trackerManager;

    private LootDetectionService lootDetectionService;

    @Inject
    private CustomNpcEncounterService customNpcEncounterService;

    @Inject
    private SmokeLootbeamManager smokeLootbeamManager;

    @Inject
    private DryStreakNotificationManager notificationManager;

    @Inject
    private DryStreakTrackerConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    private DryStreakSidebarPanel sidebarPanel;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private NavigationButton navigationButton;

    @Provides
    DryStreakTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DryStreakTrackerConfig.class);
    }

    @Override
    protected void startUp() {
        log.info("Dry Streak Tracker starting...");

        /*
         * Create Swing-dependent components after RuneLite's
         * UI/look-and-feel has been initialized.
         */
        sidebarPanel = injector.getInstance(DryStreakSidebarPanel.class);
        lootDetectionService = injector.getInstance(LootDetectionService.class);

        definitionLoader.loadInto(encounterRegistry);

        log.info("Loaded {} encounter definitions", encounterRegistry.size());

        trackerManager.start();

        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

        navigationButton = NavigationButton.builder()
                .tooltip("Dry Streak Tracker")
                .icon(icon)
                .priority(5)
                .panel(sidebarPanel)
                .build();

        clientToolbar.addNavigation(navigationButton);

        notificationManager.start();

        sidebarPanel.setLoggedIn(false
        );

        if (client.getGameState() == GameState.LOGGED_IN) {
            startCurrentPlayer();
        } else {
            SwingUtilities.invokeLater(() -> sidebarPanel.setLoggedIn(false));
        }

        log.info("Dry Streak Tracker started");
    }

    @Override
    protected void shutDown() {
        log.info("Dry Streak Tracker shutting down...");

        smokeLootbeamManager.clear();

        if (navigationButton != null) {
            clientToolbar.removeNavigation(navigationButton);

            navigationButton = null;
        }

        notificationManager.stop();

        trackerManager.stop();

        encounterRegistry.clear();

        log.info("Dry Streak Tracker stopped");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event == null) {
            return;
        }

        GameState gameState = event.getGameState();

        if (gameState == GameState.LOGGED_IN) {
            startCurrentPlayer();

            return;
        }

        if (gameState == GameState.LOGIN_SCREEN) {
            smokeLootbeamManager.clear();
            lootDetectionService.clearProcessedLootEvents();

            trackerManager.stopForPlayer();

            SwingUtilities.invokeLater(() -> sidebarPanel.setLoggedIn(false));
        }
    }

    private void startCurrentPlayer() {
        clientThread.invokeLater(() ->
                {
                    if (client.getLocalPlayer() == null) {
                        clientThread.invokeLater(this::startCurrentPlayer);

                        return;
                    }

                    String playerName = client.getLocalPlayer().getName();

                    if (playerName == null || playerName.trim().isEmpty()) {
                        clientThread.invokeLater(this::startCurrentPlayer);

                        return;
                    }

                    log.info("Logged in as {}", playerName);

                    trackerManager.startForPlayer(playerName);

                    SwingUtilities.invokeLater(() -> {
                        sidebarPanel.setLoggedIn(true);

                        sidebarPanel.refresh();
                    });

                    sidebarPanel.refreshItemDisplayData();
                }
        );
    }

    /**
     * NPC loot.
     */
    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event) {
        lootDetectionService.handleNpcLootReceived(event);
    }

    /**
     * Generic non-NPC loot.
     * <p>
     * The detection service ignores LootRecordType.NPC
     * to prevent duplicate processing.
     */
    @Subscribe
    public void onLootReceived(LootReceived event) {
        lootDetectionService.handleLootReceived(event);
    }

    /**
     * NPC deaths used to make GROUND_LOOT tracking independent
     * from whether the NPC actually produces loot.
     */
    @Subscribe
    public void onActorDeath(ActorDeath event) {
        lootDetectionService.handleActorDeath(event);
    }
    /**
     * Marks the end of a GROUND_LOOT NPC's death animation so
     * the no-loot fallback can safely begin.
     */
    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        lootDetectionService.handleNpcDespawned(event);
    }
    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        lootDetectionService.handleInteractingChanged(event);
    }


    /**
     * RuneScape game messages used for pet acquisition detection.
     */
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        lootDetectionService.handlePetAcquisitionMessage(event);
    }

    /**
     * Processes dry results waiting for the pet acquisition
     * matching window to expire.
     */

    @Subscribe
    public void onGameTick(GameTick event) {
        lootDetectionService.processPendingGroundLootDeaths();
        lootDetectionService.processPendingPetDryResult();
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!config.customNpcTracking() || !trackerManager.isActive()) {
            return;
        }

        if (event == null || event.getMenuEntry() == null) {
            return;
        }

        MenuEntry menuEntry = event.getMenuEntry();

        /*
         * EXAMINE_NPC occurs once for the NPC's menu.
         *
         * Using it as the hook prevents us from adding the custom
         * option once for Attack, Talk-to, Examine, etc.
         */
        if (menuEntry.getType() != MenuAction.EXAMINE_NPC) {
            return;
        }

        NPC npc = menuEntry.getNpc();

        if (npc == null) {
            return;
        }

        String npcName = npc.getName();
        int npcId = npc.getId();
        int combatLevel = npc.getCombatLevel();

        if (npcName == null || npcName.trim().isEmpty() || npcId <= 0) {
            return;
        }

        /*
         * Custom trackers are intended for killable NPCs with an Attack option
         */
        if (!customNpcEncounterService.canCreateCustomTracker(npc)) {
            return;
        }

        EncounterDefinition registeredEncounter = encounterRegistry.getByNpcId(npcId);

        /*
         * An exact NPC ID already belongs to a built-in encounter.
         *
         * Built-in encounters always take priority over custom ones.
         */
        if (registeredEncounter != null
                && !trackerManager.isCustomEncounter(registeredEncounter.getEncounterId())) {
            return;
        }

        /*
         * Custom encounters first match the exact NPC ID.
         *
         * If this particular visual/ID variant has not been seen before,
         * fall back to exact NPC name + exact combat level.
         */
        EncounterDefinition customEncounter = trackerManager.getCustomEncounterByNpcId(npcId);

        if (customEncounter == null) {
            customEncounter = trackerManager.getCustomEncounterByNpc(npcName, combatLevel);
        }

        boolean customEncounterExists = customEncounter != null;

        String option = customEncounterExists
                ? "Configure Dry Streak Tracker"
                : "Add to Dry Streak Tracker";

        /*
         * Do not hold the NPC reference after the menu closes.
         */
        String selectedNpcName = npcName;
        int selectedNpcId = npcId;
        int selectedCombatLevel = combatLevel;
        EncounterDefinition selectedCustomEncounter = customEncounter;

        client.createMenuEntry(-1)
                .setOption(option)
                .setTarget(menuEntry.getTarget())
                .setType(MenuAction.RUNELITE)
                .onClick(clickedEntry ->
                        customNpcEncounterService.openTracker(
                                sidebarPanel,
                                selectedNpcName,
                                selectedNpcId,
                                selectedCombatLevel,
                                selectedCustomEncounter
                        ));
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event) {
        smokeLootbeamManager.handleItemSpawned(event);
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        smokeLootbeamManager.handleItemDespawned(event);
    }

    @Subscribe
    public void onItemQuantityChanged(ItemQuantityChanged event) {
        smokeLootbeamManager.handleItemQuantityChanged(event);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        smokeLootbeamManager.handleConfigChanged(event);
    }
}
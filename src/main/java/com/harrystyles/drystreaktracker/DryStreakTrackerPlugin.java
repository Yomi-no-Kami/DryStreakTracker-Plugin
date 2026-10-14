package com.harrystyles.drystreaktracker;

import com.harrystyles.drystreaktracker.cosmetic.SmokeLootbeamManager;
import com.harrystyles.drystreaktracker.detection.LootDetectionService;
import com.harrystyles.drystreaktracker.encounter.*;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;
import com.harrystyles.drystreaktracker.ui.DryStreakSidebarPanel;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.swing.*;

import com.google.inject.Provides;

import com.harrystyles.drystreaktracker.wiki.WikiDropService;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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
import net.runelite.client.util.Text;

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
    private DryStreakTrackerConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    private DryStreakSidebarPanel sidebarPanel;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private NavigationButton navigationButton;

    private boolean playerInitializationPending;

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

        sidebarPanel.setLoggedIn(false
        );

        if (client.getGameState() == GameState.LOGGED_IN) {
            playerInitializationPending = true;
        } else {
            SwingUtilities.invokeLater(() -> sidebarPanel.setLoggedIn(false));
        }

        log.info("Dry Streak Tracker started");
    }

    @Override
    protected void shutDown() {
        log.debug("Dry Streak Tracker shutting down...");

        smokeLootbeamManager.clear();

        if (navigationButton != null) {
            clientToolbar.removeNavigation(navigationButton);

            navigationButton = null;
        }

        clearPendingDoomCompletion();
        previousDoomLoot.clear();

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
            playerInitializationPending = true;
            return;
        }

        if (gameState == GameState.LOGIN_SCREEN) {
            playerInitializationPending = false;

            smokeLootbeamManager.clear();
            lootDetectionService.clearProcessedLootEvents();
            trackerManager.stopForPlayer();

            clearPendingDoomCompletion();
            previousDoomLoot.clear();

            SwingUtilities.invokeLater(() -> sidebarPanel.setLoggedIn(false));
        }
    }

    private void startCurrentPlayer() {
        Player player = client.getLocalPlayer();

        if (player == null) {
            return;
        }

        String playerName = player.getName();

        long accountHash = client.getAccountHash();

        if (accountHash == -1L) {
            return;
        }

        if (playerName == null || playerName.trim().isEmpty()) {
            return;
        }

        playerInitializationPending = false;

        log.debug("Logged in as {}", playerName);

        trackerManager.startForPlayer(playerName, accountHash);

        if (!trackerManager.isActive()) {
            log.debug("Failed to initialize tracking for {}. Saved data will not be overwritten.", playerName);

            SwingUtilities.invokeLater(() -> sidebarPanel.setLoggedIn(false));
            return;
        }

        SwingUtilities.invokeLater(() -> {
            sidebarPanel.setLoggedIn(true);
            sidebarPanel.refresh();
        });

        sidebarPanel.refreshItemDisplayData();
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
        if (event != null && event.getName() != null
                && event.getName().equalsIgnoreCase("Doom of Mokhaiotl")) {
            return;
        }

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

        handleDoomCompletionMessage(event);
    }

    /**
     * Processes dry results waiting for the pet acquisition
     * matching window to expire.
     */

    @Subscribe
    public void onGameTick(GameTick event) {
        if (playerInitializationPending && client.getGameState() == GameState.LOGGED_IN) {
            startCurrentPlayer();
        }

        lootDetectionService.processPendingGroundLootDeaths();
        lootDetectionService.processPendingPetDryResult();

        processDoomRewardInterface();
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



    private static final Pattern DOOM_LEVEL_PATTERN = Pattern.compile("(?i)Level\\s+(\\d+)\\s+Complete!");
    private static final Pattern DOOM_COMPLETION_MESSAGE = Pattern.compile("(?i)Delve\\s+level:\\s*(\\d+)\\s+duration:");

    private int pendingDoomLevel = -1;
    private int pendingDoomTick = -1;
    private int lastRecordedDoomTick = -1;

    private final Map<Integer, Integer> previousDoomLoot = new HashMap<>();

    private void processDoomRewardInterface() {
        if (client.getGameState() != GameState.LOGGED_IN || !trackerManager.isActive()) {
            return;
        }
        if (pendingDoomLevel < 0 || pendingDoomTick < 0
                || pendingDoomTick == lastRecordedDoomTick) {
            return;
        }

        Widget titleWidget = client.getWidget(InterfaceID.DomEndLevelUi.FRAME);

        if (titleWidget == null) {
            return;
        }

        Widget[] titleChildren = titleWidget.getDynamicChildren();

        if (titleChildren == null) {
            return;
        }

        String title = null;

        for (Widget child : titleChildren) {
            if (child == null || child.getText() == null) {
                continue;
            }

            String childText = Text.removeTags(child.getText());

            if (childText != null && DOOM_LEVEL_PATTERN.matcher(childText).find()) {
                title = childText;
                break;
            }
        }

        if (title == null) {
            return;
        }

        Matcher matcher = DOOM_LEVEL_PATTERN.matcher(title);

        if (!matcher.find()) {
            return;
        }

        int displayedLevel = Integer.parseInt(matcher.group(1));

        if (displayedLevel != pendingDoomLevel) {
            return;
        }

        Widget lootWidget = client.getWidget(InterfaceID.DomEndLevelUi.LOOT_CONTENTS);

        if (lootWidget == null || lootWidget.isHidden()) {
            return;
        }

        Widget[] slots = lootWidget.getDynamicChildren();

        if (slots == null) {
            return;
        }

        Map<Integer, Integer> currentLoot = readDoomLoot(lootWidget);

        EncounterDefinition encounter = encounterRegistry.getByLootSourceName("Doom of Mokhaiotl");

        if (encounter == null) {
            return;
        }

        String encounterId = encounter.getEncounterId();
        Map<Integer, Integer> newTrackedDrops = new LinkedHashMap<>();

        for (Map.Entry<Integer, Integer> entry : currentLoot.entrySet()) {
            int itemId = entry.getKey();
            int gainedQuantity = entry.getValue() - previousDoomLoot.getOrDefault(itemId, 0);

            if (gainedQuantity > 0 && trackerManager.isDropEnabled(encounterId, itemId)) {
                newTrackedDrops.put(itemId, gainedQuantity);
            }
        }

        Integer firstDropId = null;
        int firstDropQuantity = 0;

        for (Map.Entry<Integer, Integer> entry : newTrackedDrops.entrySet()) {
            firstDropId = entry.getKey();
            firstDropQuantity = entry.getValue();
            break;
        }

        String eventKey = "doom-delve-" + pendingDoomTick + "-" + pendingDoomLevel;

        boolean recorded = trackerManager.recordKill(
                encounterId, eventKey, firstDropId, firstDropQuantity);

        if (!recorded) {
            return;
        }

        boolean first = true;

        for (Map.Entry<Integer, Integer> entry : newTrackedDrops.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();

            if (first) {
                first = false;
                lootDetectionService.processRecordedDrop(encounter, itemId, quantity, false);
                continue;
            }

            if (trackerManager.recordAdditionalDropForLastKill(encounterId, itemId, quantity)) {
                lootDetectionService.processRecordedDrop(encounter, itemId, quantity, false);
            }
        }

        log.debug("[DOOM] Recorded level={} result={} trackedDrops={}",
                pendingDoomLevel, newTrackedDrops.isEmpty() ? "DRY" : "DROP", newTrackedDrops);

        previousDoomLoot.clear();
        previousDoomLoot.putAll(currentLoot);

        /*
         * This completion has now been consumed.
         * Reopening the interface cannot record another KC.
         */
        lastRecordedDoomTick = pendingDoomTick;
        pendingDoomLevel = -1;
        pendingDoomTick = -1;

        SwingUtilities.invokeLater(() -> sidebarPanel.refresh());
    }

    private void clearPendingDoomCompletion() {
        pendingDoomLevel = -1;
        pendingDoomTick = -1;
        lastRecordedDoomTick = -1;
    }

    private Map<Integer, Integer> readDoomLoot(Widget lootWidget) {
        Map<Integer, Integer> items = new HashMap<>();

        Widget[] slots = lootWidget.getDynamicChildren();

        if (slots == null) {
            return items;
        }

        for (Widget slot : slots) {
            if (slot == null || slot.isHidden()) {
                continue;
            }

            int itemId = slot.getItemId();
            int quantity = slot.getItemQuantity();

            if (itemId <= 0 || quantity <= 0 || itemId == 6512) {
                continue;
            }

            items.merge(itemId, quantity, Integer::sum);
        }

        return items;
    }


    private void handleDoomCompletionMessage(ChatMessage event) {
        if (event == null || event.getType() != ChatMessageType.GAMEMESSAGE
                || !trackerManager.isActive()) {
            return;
        }

        String message = Text.removeTags(event.getMessage());

        if (message == null) {
            return;
        }

        Matcher matcher = DOOM_COMPLETION_MESSAGE.matcher(message);

        if (!matcher.find()) {
            return;
        }

        int level = Integer.parseInt(matcher.group(1));
        int tick = client.getTickCount();

        // Ignore duplicate messages for a completion already pending or recorded.
        if (tick == pendingDoomTick || tick == lastRecordedDoomTick) {
            return;
        }

        pendingDoomLevel = level;
        pendingDoomTick = tick;

        // A fresh level 1 completion starts a new loot accumulation.
        if (level == 1) {
            previousDoomLoot.clear();
        }
    }
}
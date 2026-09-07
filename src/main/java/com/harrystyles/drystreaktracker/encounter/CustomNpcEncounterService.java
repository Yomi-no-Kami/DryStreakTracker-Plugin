package com.harrystyles.drystreaktracker.encounter;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterDropDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterLootType;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;
import com.harrystyles.drystreaktracker.ui.CustomNpcTrackerDialog;
import com.harrystyles.drystreaktracker.ui.DryStreakSidebarPanel;
import com.harrystyles.drystreaktracker.wiki.WikiDropService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class CustomNpcEncounterService {
    private final EncounterTrackerManager trackerManager;
    private final WikiDropService wikiDropService;
    private final ItemManager itemManager;

    @Inject
    public CustomNpcEncounterService(EncounterTrackerManager trackerManager, WikiDropService wikiDropService, ItemManager itemManager) {
        this.trackerManager = trackerManager;
        this.wikiDropService = wikiDropService;
        this.itemManager = itemManager;
    }

    public void openTracker(DryStreakSidebarPanel sidebarPanel, String npcName, int npcId, int combatLevel, EncounterDefinition existingEncounter) {
        if (!trackerManager.isActive()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            CustomNpcTrackerDialog dialog = new CustomNpcTrackerDialog(
                    sidebarPanel,
                    npcName,
                    npcId,
                    existingEncounter,
                    wikiDropService,
                    itemManager,
                    selectedItemIds ->
                            saveTracker(sidebarPanel, npcName, npcId, combatLevel, existingEncounter, selectedItemIds),
                    () -> {
                        if (existingEncounter != null) {
                            deleteTracker(sidebarPanel, existingEncounter.getEncounterId());
                        }
                    }
            );

            dialog.show();
        });
    }

    private void saveTracker(DryStreakSidebarPanel sidebarPanel, String npcName, int npcId, int combatLevel, EncounterDefinition existingEncounter, Set<Integer> selectedItemIds) {
        if (!trackerManager.isActive() || selectedItemIds == null) {
            return;
        }

        EncounterDefinition encounter = new EncounterDefinition();

        encounter.setEncounterId(existingEncounter != null
                ? existingEncounter.getEncounterId()
                : createEncounterId(npcId));

        encounter.setDisplayName(npcName);
        encounter.setCombatLevel(combatLevel);
        encounter.setLootType(EncounterLootType.GROUND_LOOT);

        String imageFileName = wikiDropService.getCachedNpcImageFileName(npcId);

        if (imageFileName != null && !imageFileName.trim().isEmpty()) {
            encounter.setImageFileName(imageFileName);
        } else if (existingEncounter != null) {
            encounter.setImageFileName(existingEncounter.getImageFileName());
        }

        Set<Integer> npcIds = new HashSet<>();

        if (existingEncounter != null) {
            npcIds.addAll(existingEncounter.getNpcIds());
        }

        npcIds.add(npcId);

        encounter.setNpcIds(npcIds);

        List<EncounterDropDefinition> trackedDrops = new ArrayList<>();

        for (Integer itemId : selectedItemIds) {
            if (itemId == null || itemId <= 0) {
                continue;
            }

            EncounterDropDefinition drop = new EncounterDropDefinition();

            drop.setItemId(itemId);
            drop.setEnabledByDefault(true);

            trackedDrops.add(drop);
        }

        /*
         * An empty tracked-drop list is valid.
         *
         * This allows custom encounters to be used only
         * for tracking kills.
         */
        encounter.setTrackedDrops(trackedDrops);

        /*
         * Custom encounters intentionally do not use RuneLite
         * boss-KC data or normal pet tracking.
         */
        encounter.setKillcountNames(new HashSet<>());
        encounter.setLootSourceNames(new HashSet<>());
        encounter.setPetDropIds(new HashSet<>());

        if (!trackerManager.saveCustomEncounter(encounter)) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            sidebarPanel,
                            "Could not save the custom tracker for "
                                    + npcName
                                    + ".\n\nThis NPC may already belong to another encounter.",
                            "Custom NPC Tracker",
                            JOptionPane.ERROR_MESSAGE
                    )
            );

            return;
        }

        refreshSidebar(sidebarPanel);

        log.info(
                "Configured custom NPC tracker for {} ({}, combat {}) with {} tracked drops",
                npcName,
                npcId,
                combatLevel,
                trackedDrops.size()
        );
    }

    private void deleteTracker(DryStreakSidebarPanel sidebarPanel, String encounterId) {
        if (encounterId == null || encounterId.trim().isEmpty()) {
            return;
        }

        if (!trackerManager.removeCustomEncounter(encounterId)) {
            return;
        }

        refreshSidebar(sidebarPanel);
    }

    private void refreshSidebar(DryStreakSidebarPanel sidebarPanel) {
        if (sidebarPanel == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            sidebarPanel.refreshItemDisplayData();
            sidebarPanel.refresh();
        });
    }

    private String createEncounterId(int npcId) {
        return "custom_npc_" + npcId;
    }
}
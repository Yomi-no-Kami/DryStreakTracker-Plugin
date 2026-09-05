package com.harrystyles.drystreaktracker.encounter.tracking;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterRegistry;
import com.harrystyles.drystreaktracker.encounter.EncounterStats;
import com.harrystyles.drystreaktracker.storage.DryStreakStorage;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Handles all player encounter tracking.
 * <p>
 * Tracking data is loaded and saved per RuneScape account.
 *
 * @author Harry Styles
 */
@Slf4j
@Singleton
public class EncounterTrackerManager {
    /**
     * Maximum number of recently processed kill events
     * kept in memory to prevent duplicate event handling.
     */
    private static final int MAX_PROCESSED_KILL_EVENTS = 100;

    private final EncounterRegistry encounterRegistry;
    private final DryStreakStorage storage;

    private final ConfigManager configManager;

    /**
     * Tracking data for the currently logged-in player.
     */
    private PlayerTrackingData trackingData;

    /**
     * RuneScape character currently being tracked.
     */
    private String currentPlayerName;

    /**
     * Whether the tracker currently has an active
     * logged-in RuneScape character.
     */
    private boolean active;

    /**
     * Recently processed kill event fingerprints.
     */
    private final Set<String> processedKillEvents = new HashSet<>();

    @Inject
    public EncounterTrackerManager(EncounterRegistry encounterRegistry, DryStreakStorage storage, ConfigManager configManager) {
        this.encounterRegistry = encounterRegistry;
        this.storage = storage;
        this.configManager = configManager;
    }

    /**
     * Starts tracking for a specific RuneScape account.
     * <p>
     * If the same account is already active, nothing happens.
     *
     * @param playerName RuneScape character name.
     */
    public void startForPlayer(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            log.warn("Cannot start tracker: player name is empty");

            return;
        }

        String normalizedName = playerName.trim();

        /*
         * Avoid reloading the same account if RuneLite
         * sends LOGGED_IN more than once.
         */
        if (active && currentPlayerName != null && currentPlayerName.equalsIgnoreCase(normalizedName)) {
            log.debug("Tracker already active for {}", normalizedName);

            return;
        }

        /**
         * If another account was active, save that account
         * before switching.
         */
        if (active) {
            stopForPlayer();
        }

        currentPlayerName = normalizedName;

        trackingData = storage.load(currentPlayerName);

        if (trackingData == null) {
            trackingData = new PlayerTrackingData();
        }

        /*
         * Kill events from the previous account must never
         * carry over to the new account.
         */
        processedKillEvents.clear();

        active = true;

        log.info("Started tracking for account {}. " + "Loaded {} tracked encounters.", currentPlayerName, trackingData.getEncounters().size());
    }

    /**
     * Stops tracking the currently logged-in account.
     * <p>
     * The account's data is saved before the tracker
     * becomes inactive.
     */
    public void stopForPlayer() {
        if (!active) {
            return;
        }

        log.info("Stopping tracker for account {}", currentPlayerName);

        save();

        processedKillEvents.clear();

        trackingData = null;
        currentPlayerName = null;
        active = false;
    }

    /**
     * Called when the plugin itself starts.
     * <p>
     * Tracking is intentionally NOT started here because
     * the player may not be logged in yet.
     */
    public void start() {
        trackingData = null;
        currentPlayerName = null;
        active = false;

        processedKillEvents.clear();

        log.info("Encounter tracker initialized. " + "Waiting for player login.");
    }

    /**
     * Called when the plugin shuts down.
     */
    public void stop() {
        stopForPlayer();
        processedKillEvents.clear();
    }

    /**
     * Saves the currently active player's data.
     */
    public void save() {
        if (!isActive()) {
            return;
        }

        storage.save(currentPlayerName, trackingData);
    }

    /**
     * Records a completed encounter kill.
     * <p>
     * A kill may either have a tracked drop or be dry.
     *
     * @param encounterId  encounter being tracked
     * @param killEventKey unique fingerprint for this kill event
     * @param dropItemId   tracked drop item ID, or null if dry
     * @param dropQuantity quantity received from the drop
     * @return true if the kill was recorded
     */
    public boolean recordKill(String encounterId, String killEventKey, Integer dropItemId, int dropQuantity) {
        if (!isActive()) {
            log.debug("Ignoring kill because no player is logged in");

            return false;
        }

        if (encounterId == null || encounterId.trim().isEmpty()) {
            log.warn("Cannot record kill: encounter ID is null or empty");

            return false;
        }

        EncounterDefinition definition = encounterRegistry.getById(encounterId);

        if (definition == null) {
            log.warn("Cannot record kill: unknown encounter {}", encounterId);

            return false;
        }

        if (killEventKey != null && !killEventKey.isEmpty()) {
            if (!registerKillEvent(killEventKey)) {
                log.debug("Duplicate kill event ignored: {}", killEventKey);

                return false;
            }
        }

        EncounterStats stats = trackingData.getOrCreateEncounter(definition);

        int newKillcount = stats.getLastKnownKillcount() + 1;

        if (dropItemId != null) {
            int totalKillcount = getRuneLiteKillcount(definition);

            stats.recordDrop(newKillcount, totalKillcount, dropItemId, dropQuantity);

            log.info("{} kill #{} recorded with tracked drop. " + "Current dry streak: {}", definition.getDisplayName(), newKillcount, stats.getCurrentDryStreak());
        } else {
            stats.recordDryKill(newKillcount);

            log.debug("{} kill #{} recorded as dry. " + "Current dry streak: {}", definition.getDisplayName(), newKillcount, stats.getCurrentDryStreak());
        }

        save();

        return true;
    }

    /**
     * Records another tracked drop from an encounter kill which
     * has already been recorded.
     */
    public boolean recordAdditionalDropForLastKill(String encounterId, int itemId, int quantity) {
        if (!isActive()) {
            return false;
        }

        if (encounterId == null || encounterId.trim().isEmpty()) {
            return false;
        }

        EncounterDefinition definition = encounterRegistry.getById(encounterId);

        if (definition == null || !definition.isTrackedDrop(itemId)) {
            return false;
        }

        EncounterStats stats = trackingData.getEncounter(encounterId);

        if (stats == null || stats.getTotalKillsTracked() <= 0) {
            return false;
        }

        int totalKillcount = getRuneLiteKillcount(definition);

        stats.recordAdditionalDropOnLastKill(totalKillcount, itemId, quantity);

        save();

        log.info("{} additional tracked drop {} recorded on existing kill #{}", definition.getDisplayName(), itemId, stats.getLastKnownKillcount());

        return true;
    }

    /**
     * Records a pet against the most recently completed kill
     * for an encounter.
     *
     * The encounter kill has already been processed through
     * NpcLootReceived or LootReceived, so this method does not
     * create another kill.
     */
    public boolean recordPetForLastKill(String encounterId, int petItemId) {
        if (!isActive()) {
            return false;
        }

        if (encounterId == null || encounterId.trim().isEmpty()) {
            return false;
        }

        EncounterDefinition definition = encounterRegistry.getById(encounterId);

        if (definition == null) {
            return false;
        }

        if (!definition.isPetDrop(petItemId)) {
            log.warn("Item {} is not configured as a pet for {}", petItemId, encounterId);

            return false;
        }

        EncounterStats stats = trackingData.getEncounter(encounterId);

        if (stats == null || stats.getTotalKillsTracked() <= 0) {
            return false;
        }

        int totalKillcount = getRuneLiteKillcount(definition);

        stats.recordPetOnLastKill(totalKillcount, petItemId, 1);

        save();

        log.info("{} pet {} recorded on existing kill #{}", definition.getDisplayName(), petItemId, stats.getLastKnownKillcount());

        return true;
    }

    /**
     * Registers a kill event so the same event cannot
     * immediately be processed twice.
     * <p>
     * The set uses a rolling window. Once the maximum size
     * is reached, the oldest information is discarded by
     * clearing the set.
     */
    private boolean registerKillEvent(String killEventKey) {
        if (processedKillEvents.contains(killEventKey)) {
            return false;
        }

        /*
         * Keep the set bounded.
         *
         * Do this BEFORE adding the new event so the set
         * never grows beyond the configured limit.
         */
        if (processedKillEvents.size() >= MAX_PROCESSED_KILL_EVENTS) {
            processedKillEvents.clear();
        }

        processedKillEvents.add(killEventKey);

        return true;
    }

    /**
     * Gets statistics for an encounter.
     *
     * @param encounterId encounter ID
     * @return encounter statistics, or null if none exist
     */
    public EncounterStats getStats(String encounterId) {
        if (!isActive()) {
            return null;
        }

        return trackingData.getEncounter(
                encounterId
        );
    }

    /**
     * Gets all player tracking data.
     *
     * @return current player's tracking data, or null
     * if nobody is logged in
     */
    public PlayerTrackingData getTrackingData() {
        return trackingData;
    }

    /**
     * Returns the currently active RuneScape account.
     *
     * @return player name, or null if nobody is logged in
     */
    public String getCurrentPlayerName() {
        return currentPlayerName;
    }

    /**
     * Returns whether a player is currently being tracked.
     */
    public boolean isActive() {
        return active && currentPlayerName != null && trackingData != null;
    }

    /**
     * Clears duplicate-event tracking.
     * <p>
     * This does NOT clear any saved encounter statistics.
     */
    public void clearProcessedKillEvents() {
        processedKillEvents.clear();
    }

    /**
     * Clears all saved data for the currently logged-in
     * account.
     * <p>
     * The player remains logged in and tracking remains active,
     * but all encounter statistics are reset.
     */
    public void clearAllData() {
        if (!isActive()) {
            log.debug("Cannot clear account data: no player is logged in");

            return;
        }

        String playerName = currentPlayerName;

        /*
         * Replace the current in-memory data with a completely
         * fresh data object.
         */
        trackingData = new PlayerTrackingData();

        /*
         * Old kill event fingerprints must also be removed.
         */
        processedKillEvents.clear();

        /*
         * Remove the persisted account data.
         */
        storage.clear(playerName);

        /*
         * Save the fresh empty data immediately.
         */
        save();

        log.info("Cleared all tracking data for account {}", playerName);
    }

    /**
     * Manually synchronizes an encounter with the player's
     * existing total KC and current dry streak.
     */
    public boolean setEncounterKillcounts(String encounterId, int totalKillcount, int dryKillcount, int longestDryKillcount) {
        if (!isActive()) {
            return false;
        }

        if (encounterId == null || encounterId.trim().isEmpty()) {
            return false;
        }

        if (totalKillcount < 0 || dryKillcount < 0 || longestDryKillcount < 0) {
            return false;
        }

        if (dryKillcount > totalKillcount || longestDryKillcount < dryKillcount) {
            return false;
        }

        EncounterDefinition definition = encounterRegistry.getById(encounterId);

        if (definition == null) {
            return false;
        }

        EncounterStats stats = trackingData.getOrCreateEncounter(definition);

        if (stats == null) {
            return false;
        }

        stats.setTrackingBaseline(totalKillcount, dryKillcount, longestDryKillcount);

        save();

        log.info("Manually synchronized {} to total KC {}, dry KC {}, and longest dry KC {}", encounterId, totalKillcount, dryKillcount, longestDryKillcount);

        return true;
    }

    /**
     * Clears all saved tracking data for one encounter.
     * <p>
     * The rest of the player's tracked encounters remain
     * unchanged.
     *
     * @param encounterId encounter to clear
     */
    public void clearEncounterData(
            String encounterId) {
        if (!isActive()) {
            log.debug("Cannot clear encounter data: no player is logged in");

            return;
        }

        if (encounterId == null || encounterId.trim().isEmpty()) {
            return;
        }

        trackingData.removeEncounter(encounterId);

        /*
         * Save immediately so the cleared encounter does not
         * return after restarting RuneLite.
         */
        save();

        log.info("Cleared tracking data for encounter {}", encounterId);
    }

    public void recordRecentDrop(String encounterId, int itemId, int quantity, int geValue) {
        if (!isActive()) {
            return;
        }

        EncounterDefinition definition = encounterRegistry.getById(encounterId);

        EncounterStats stats = trackingData.getEncounter(encounterId);

        if (definition == null || stats == null) {
            return;
        }

        int totalKillcount = stats.getLastDropTotalKillcount();

        RecentDrop recentDrop = new RecentDrop(
                currentPlayerName,
                encounterId,
                definition.getDisplayName(),
                definition.getImageUrl(),
                itemId,
                quantity,
                stats.getLastCompletedDryStreak(),
                totalKillcount,
                geValue,
                System.currentTimeMillis()
        );

        trackingData.addRecentDrop(recentDrop);

        save();
    }

    public java.util.List<RecentDrop> getRecentDrops() {
        if (!isActive()) {
            return java.util.Collections.emptyList();
        }

        return trackingData.getRecentDrops();
    }

    public void clearRecentDrops() {
        if (!isActive()) {
            return;
        }

        trackingData.clearRecentDrops();

        save();

        log.info("Cleared recent drops for account {}", currentPlayerName);
    }

    public void removeRecentDrop(RecentDrop recentDrop) {
        if (!isActive() || recentDrop == null) {
            return;
        }

        trackingData.removeRecentDrop(recentDrop);

        save();

        log.info(
                "Removed recent drop {} from {}",
                recentDrop.getItemId(),
                recentDrop.getEncounterName()
        );
    }

    public void clearTrackerData() {
        if (!isActive()) {
            return;
        }

        trackingData.clearEncounters();

        processedKillEvents.clear();

        save();

        log.info("Cleared tracker data for account {}", currentPlayerName);
    }

    private int getRuneLiteKillcount(EncounterDefinition definition) {
        if (definition == null) {
            return 0;
        }

        Set<String> killcountNames = definition.getKillcountNames();

        if (killcountNames == null || killcountNames.isEmpty()) {
            return 0;
        }

        int totalKillcount = 0;

        for (String killcountName : killcountNames) {
            totalKillcount += getStoredKillcount(killcountName);
        }

        return totalKillcount;
    }

    private int getStoredKillcount(String bossName) {
        if (bossName == null || bossName.trim().isEmpty()) {
            return 0;
        }

        String bossKey = bossName.trim().replace(":", "").toLowerCase(Locale.ROOT);

        Integer killcount = configManager.getRSProfileConfiguration(
                "killcount",
                bossKey,
                int.class
        );

        if (killcount == null) {
            log.debug("No RuneLite Boss killcount found using key '{}'", bossKey);

            return 0;
        }

        log.debug("RuneLite Boss killcount using key '{}': {}", bossKey, killcount);

        return killcount;
    }
}
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
 *
 * Tracking data is loaded and saved per RuneScape account.
 *
 * @author Harry Styles
 */
@Slf4j
@Singleton
public class EncounterTrackerManager
{
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
    private final Set<String> processedKillEvents =
            new HashSet<>();

    @Inject
    public EncounterTrackerManager( EncounterRegistry encounterRegistry, DryStreakStorage storage, ConfigManager configManager) {
        this.encounterRegistry = encounterRegistry;
        this.storage = storage;
        this.configManager = configManager;
    }

    /**
     * Starts tracking for a specific RuneScape account.
     *
     * If the same account is already active, nothing happens.
     *
     * @param playerName RuneScape character name.
     */
    public void startForPlayer(String playerName)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            log.warn(
                    "Cannot start tracker: player name is empty"
            );

            return;
        }

        String normalizedName = playerName.trim();

        /*
         * Avoid reloading the same account if RuneLite
         * sends LOGGED_IN more than once.
         */
        if (active
                && currentPlayerName != null
                && currentPlayerName.equalsIgnoreCase(
                normalizedName))
        {
            log.debug(
                    "Tracker already active for {}",
                    normalizedName
            );

            return;
        }

        /*
         * If another account was active, save that account
         * before switching.
         */
        if (active)
        {
            stopForPlayer();
        }

        currentPlayerName = normalizedName;

        trackingData = storage.load(
                currentPlayerName
        );

        if (trackingData == null)
        {
            trackingData = new PlayerTrackingData();
        }

        /*
         * Kill events from the previous account must never
         * carry over to the new account.
         */
        processedKillEvents.clear();

        active = true;

        log.info(
                "Started tracking for account {}. " +
                        "Loaded {} tracked encounters.",
                currentPlayerName,
                trackingData.getEncounters().size()
        );
    }

    /**
     * Stops tracking the currently logged-in account.
     *
     * The account's data is saved before the tracker
     * becomes inactive.
     */
    public void stopForPlayer()
    {
        if (!active)
        {
            return;
        }

        log.info(
                "Stopping tracker for account {}",
                currentPlayerName
        );

        save();

        processedKillEvents.clear();

        trackingData = null;
        currentPlayerName = null;
        active = false;
    }

    /**
     * Called when the plugin itself starts.
     *
     * Tracking is intentionally NOT started here because
     * the player may not be logged in yet.
     */
    public void start()
    {
        trackingData = null;
        currentPlayerName = null;
        active = false;

        processedKillEvents.clear();

        log.info(
                "Encounter tracker initialized. " +
                        "Waiting for player login."
        );
    }

    /**
     * Called when the plugin shuts down.
     */
    public void stop()
    {
        stopForPlayer();
        processedKillEvents.clear();
    }

    /**
     * Saves the currently active player's data.
     */
    public void save()
    {
        if (!isActive())
        {
            return;
        }

        storage.save(
                currentPlayerName,
                trackingData
        );
    }

    /**
     * Records a completed encounter kill.
     *
     * A kill may either have a tracked drop or be dry.
     *
     * @param encounterId encounter being tracked
     * @param killEventKey unique fingerprint for this kill event
     * @param dropItemId tracked drop item ID, or null if dry
     * @param dropQuantity quantity received from the drop
     *
     * @return true if the kill was recorded
     */
    public boolean recordKill(
            String encounterId,
            String killEventKey,
            Integer dropItemId,
            int dropQuantity)
    {
        if (!isActive())
        {
            log.debug(
                    "Ignoring kill because no player is logged in"
            );

            return false;
        }

        if (encounterId == null
                || encounterId.trim().isEmpty())
        {
            log.warn(
                    "Cannot record kill: encounter ID is null or empty"
            );

            return false;
        }

        EncounterDefinition definition =
                encounterRegistry.getById(
                        encounterId
                );

        if (definition == null)
        {
            log.warn(
                    "Cannot record kill: unknown encounter {}",
                    encounterId
            );

            return false;
        }

        if (killEventKey != null
                && !killEventKey.isEmpty())
        {
            if (!registerKillEvent(killEventKey))
            {
                log.debug(
                        "Duplicate kill event ignored: {}",
                        killEventKey
                );

                return false;
            }
        }

        EncounterStats stats =
                trackingData.getOrCreateEncounter(
                        definition
                );

        int newKillcount =
                stats.getLastKnownKillcount() + 1;

        if (dropItemId != null)
        {
            int totalKillcount =
                    getRuneLiteKillcount(
                            definition
                    );

            stats.recordDrop(
                    newKillcount,
                    totalKillcount,
                    dropItemId,
                    dropQuantity
            );

            log.info(
                    "{} kill #{} recorded with tracked drop. " +
                            "Current dry streak: {}",
                    definition.getDisplayName(),
                    newKillcount,
                    stats.getCurrentDryStreak()
            );
        }
        else
        {
            stats.recordDryKill(
                    newKillcount
            );

            log.debug(
                    "{} kill #{} recorded as dry. " +
                            "Current dry streak: {}",
                    definition.getDisplayName(),
                    newKillcount,
                    stats.getCurrentDryStreak()
            );
        }

        save();

        return true;
    }

    /**
     * Registers a kill event so the same event cannot
     * immediately be processed twice.
     *
     * The set uses a rolling window. Once the maximum size
     * is reached, the oldest information is discarded by
     * clearing the set.
     */
    private boolean registerKillEvent(String killEventKey)
    {
        if (processedKillEvents.contains(killEventKey))
        {
            return false;
        }

        /*
         * Keep the set bounded.
         *
         * Do this BEFORE adding the new event so the set
         * never grows beyond the configured limit.
         */
        if (processedKillEvents.size()
                >= MAX_PROCESSED_KILL_EVENTS)
        {
            processedKillEvents.clear();
        }

        processedKillEvents.add(killEventKey);

        return true;
    }

    /**
     * Gets statistics for an encounter.
     *
     * @param encounterId encounter ID
     *
     * @return encounter statistics, or null if none exist
     */
    public EncounterStats getStats(String encounterId)
    {
        if (!isActive())
        {
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
    public PlayerTrackingData getTrackingData()
    {
        return trackingData;
    }

    /**
     * Returns the currently active RuneScape account.
     *
     * @return player name, or null if nobody is logged in
     */
    public String getCurrentPlayerName()
    {
        return currentPlayerName;
    }

    /**
     * Returns whether a player is currently being tracked.
     */
    public boolean isActive()
    {
        return active
                && currentPlayerName != null
                && trackingData != null;
    }

    /**
     * Clears duplicate-event tracking.
     *
     * This does NOT clear any saved encounter statistics.
     */
    public void clearProcessedKillEvents()
    {
        processedKillEvents.clear();
    }

    /**
     * Clears all saved data for the currently logged-in
     * account.
     *
     * The player remains logged in and tracking remains active,
     * but all encounter statistics are reset.
     */
    public void clearAllData()
    {
        if (!isActive())
        {
            log.debug(
                    "Cannot clear account data: no player is logged in"
            );

            return;
        }

        String playerName =
                currentPlayerName;

        /*
         * Replace the current in-memory data with a completely
         * fresh data object.
         */
        trackingData =
                new PlayerTrackingData();

        /*
         * Old kill event fingerprints must also be removed.
         */
        processedKillEvents.clear();

        /*
         * Remove the persisted account data.
         */
        storage.clear(
                playerName
        );

        /*
         * Save the fresh empty data immediately.
         */
        save();

        log.info(
                "Cleared all tracking data for account {}",
                playerName
        );
    }

    private int getRuneLiteKillcount(
            EncounterDefinition definition)
    {
        if (definition == null
                || definition.getDisplayName() == null)
        {
            return 0;
        }

        String bossKey =
                definition.getDisplayName()
                        .trim()
                        .replace(":", "")
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (bossKey.isEmpty())
        {
            return 0;
        }

        Integer killcount =
                configManager.getRSProfileConfiguration(
                        "killcount",
                        bossKey,
                        int.class
                );

        if (killcount == null)
        {
            log.debug(
                    "No RuneLite Boss killcount found for {} using key '{}'",
                    definition.getDisplayName(),
                    bossKey
            );

            return 0;
        }

        log.debug(
                "RuneLite Boss killcount for {} using key '{}': {}",
                definition.getDisplayName(),
                bossKey,
                killcount
        );

        return killcount;
    }
}
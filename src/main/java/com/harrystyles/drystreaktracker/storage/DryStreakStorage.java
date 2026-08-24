package com.harrystyles.drystreaktracker.storage;

import com.harrystyles.drystreaktracker.encounter.tracking.PlayerTrackingData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.config.ConfigManager;

/**
 * Handles persistent player tracking data.
 *
 * Tracking data is stored separately for each RuneScape
 * character based on the logged-in player's name.
 *
 * @author Harry Styles
 */
@Slf4j
@Singleton
public class DryStreakStorage
{
    private static final String CONFIG_GROUP =
            "drystreaktracker";

    private static final String DATA_KEY_PREFIX =
            "trackingData_";

    private final ConfigManager configManager;

    private final Gson gson;

    @Inject
    public DryStreakStorage(
            ConfigManager configManager)
    {
        this.configManager =
                configManager;

        this.gson =
                new GsonBuilder()
                        .create();
    }

    /**
     * Loads tracking data for a specific RuneScape account.
     *
     * @param playerName RuneScape character name.
     *
     * @return Saved tracking data, or an empty data object
     *         if no data exists.
     */
    public PlayerTrackingData load(
            String playerName)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            log.warn(
                    "Cannot load tracking data: player name is empty"
            );

            return new PlayerTrackingData();
        }

        String dataKey =
                createDataKey(
                        playerName
                );

        String json =
                configManager.getConfiguration(
                        CONFIG_GROUP,
                        dataKey
                );

        if (json == null
                || json.trim().isEmpty())
        {
            log.debug(
                    "No saved tracking data found for account {}",
                    playerName
            );

            return new PlayerTrackingData();
        }

        try
        {
            /*
             * Normal format:
             *
             * {
             *   "version": 1,
             *   "encounters": {}
             * }
             */
            if (json.trim().startsWith("{"))
            {
                PlayerTrackingData data =
                        gson.fromJson(
                                json,
                                PlayerTrackingData.class
                        );

                return data != null
                        ? data
                        : new PlayerTrackingData();
            }

            /*
             * Compatibility with old/double-encoded data.
             */
            String decoded =
                    gson.fromJson(
                            json,
                            String.class
                    );

            if (decoded != null
                    && decoded.trim().startsWith("{"))
            {
                PlayerTrackingData data =
                        gson.fromJson(
                                decoded,
                                PlayerTrackingData.class
                        );

                return data != null
                        ? data
                        : new PlayerTrackingData();
            }

            log.warn(
                    "Saved tracking data for {} has an unexpected format",
                    playerName
            );

            return new PlayerTrackingData();
        }
        catch (Exception e)
        {
            log.error(
                    "Failed to parse saved tracking data for account {}. " +
                            "Starting with empty tracking data.",
                    playerName,
                    e
            );

            return new PlayerTrackingData();
        }
    }

    /**
     * Saves tracking data for a specific RuneScape account.
     *
     * @param playerName RuneScape character name.
     * @param data Tracking data.
     */
    public void save(
            String playerName,
            PlayerTrackingData data)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            log.warn(
                    "Cannot save tracking data: player name is empty"
            );

            return;
        }

        if (data == null)
        {
            return;
        }

        try
        {
            String json =
                    gson.toJson(
                            data
                    );

            String dataKey =
                    createDataKey(
                            playerName
                    );

            configManager.setConfiguration(
                    CONFIG_GROUP,
                    dataKey,
                    json
            );

            log.debug(
                    "Saved tracking data for account {}",
                    playerName
            );
        }
        catch (Exception e)
        {
            log.error(
                    "Failed to save tracking data for account {}",
                    playerName,
                    e
            );
        }
    }

    /**
     * Clears all saved tracking data for a specific
     * RuneScape account.
     *
     * @param playerName RuneScape character name.
     */
    public void clear(
            String playerName)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            return;
        }

        String dataKey =
                createDataKey(
                        playerName
                );

        configManager.unsetConfiguration(
                CONFIG_GROUP,
                dataKey
        );

        log.info(
                "Cleared saved tracking data for account {}",
                playerName
        );
    }

    /**
     * Creates the ConfigManager key for an account.
     *
     * A SHA-256 hash is used instead of the raw player
     * name so ConfigManager never receives problematic
     * characters from a RuneScape username.
     */
    private String createDataKey(
            String playerName)
    {
        String normalizedName =
                playerName
                        .trim()
                        .toLowerCase();

        return DATA_KEY_PREFIX
                + sha256(
                normalizedName
        );
    }

    /**
     * Creates a SHA-256 hash.
     */
    private String sha256(
            String value)
    {
        try
        {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : hash)
            {
                result.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return result.toString();
        }
        catch (Exception e)
        {
            throw new IllegalStateException(
                    "Unable to generate account storage key",
                    e
            );
        }
    }
}
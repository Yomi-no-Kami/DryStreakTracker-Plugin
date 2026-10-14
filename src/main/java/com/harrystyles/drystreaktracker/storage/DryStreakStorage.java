package com.harrystyles.drystreaktracker.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.harrystyles.drystreaktracker.encounter.tracking.PlayerTrackingData;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.config.ConfigManager;

/**
 * Handles persistent player tracking data.
 * <p>
 * Tracking data is stored separately for each RuneScape
 * character based on the logged-in player's name.
 *
 * @author Harry Styles
 */
@Slf4j
@Singleton
public class DryStreakStorage {
    private static final String CONFIG_GROUP = "drystreaktracker";

    private static final String DATA_KEY_PREFIX = "trackingData_";

    private final ConfigManager configManager;

    private final Gson gson;

    @Inject
    public DryStreakStorage(ConfigManager configManager, Gson gson) {
        this.configManager = configManager;
        this.gson = gson;
    }

    /**
     * Loads tracking data for a specific RuneScape account.
     *
     * @param playerName RuneScape character name.
     * @return Saved tracking data, or an empty data object
     * if no data exists.
     */
    public PlayerTrackingData load(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            log.warn("Cannot load tracking data: player name is empty");

            return new PlayerTrackingData();
        }

        String dataKey = createDataKey(playerName);

        String json = configManager.getConfiguration(CONFIG_GROUP, dataKey);

        if (json == null || json.trim().isEmpty()) {
            log.debug("No saved tracking data found for account {}", playerName);

            return new PlayerTrackingData();
        }

        try {
            /**
             * Normal format:
             *
             * {
             *   "version": 1,
             *   "encounters": {}
             * }
             */
            if (json.trim().startsWith("{")) {
                PlayerTrackingData data = gson.fromJson(json, PlayerTrackingData.class);

                return data != null ? data : new PlayerTrackingData();
            }

            /**
             * Compatibility with old/double-encoded data.
             */
            String decoded = gson.fromJson(json, String.class);

            if (decoded != null && decoded.trim().startsWith("{")) {
                PlayerTrackingData data = gson.fromJson(decoded, PlayerTrackingData.class);

                return data != null ? data : new PlayerTrackingData();
            }

            log.warn("Saved tracking data for {} has an unexpected format", playerName
            );

            return new PlayerTrackingData();
        } catch (Exception e) {
            log.error("Failed to parse saved tracking data for account {}. " + "Starting with empty tracking data.", playerName, e);

            return new PlayerTrackingData();
        }
    }

    /**
     * Saves tracking data for a specific RuneScape account.
     *
     * @param playerName RuneScape character name.
     * @param data       Tracking data.
     */
    public void save(String playerName, PlayerTrackingData data) {
        if (playerName == null || playerName.trim().isEmpty()) {
            log.warn("Cannot save tracking data: player name is empty");

            return;
        }

        if (data == null) {
            return;
        }

        try {
            String json = gson.toJson(data);

            String dataKey = createDataKey(playerName);

            configManager.setConfiguration(CONFIG_GROUP, dataKey, json);

            log.debug("Saved tracking data for account {}", playerName);
        } catch (Exception e) {
            log.error("Failed to save tracking data for account {}", playerName, e);
        }
    }

    /**
     * Clears all saved tracking data for a specific
     * RuneScape account.
     *
     * @param playerName RuneScape character name.
     */
    public void clear(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return;
        }

        String dataKey = createDataKey(playerName);

        configManager.unsetConfiguration(CONFIG_GROUP, dataKey);

        log.debug("Cleared saved tracking data for account {}", playerName);
    }

    /**
     * Creates the ConfigManager key for an account.
     * <p>
     * A SHA-256 hash is used instead of the raw player
     * name so ConfigManager never receives problematic
     * characters from a RuneScape username.
     */
    private String createDataKey(String playerName) {
        String normalizedName = playerName.trim().toLowerCase();

        return DATA_KEY_PREFIX + sha256(normalizedName);
    }

    private String createAccountDataKey(long accountHash) {
        return DATA_KEY_PREFIX + "account_" + Long.toUnsignedString(accountHash);
    }

    /**
     * Creates a SHA-256 hash.
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();

            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }

            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate account storage key", e);
        }
    }

    public PlayerTrackingData load(String playerName, long accountHash) {
        if (playerName == null || playerName.trim().isEmpty() || accountHash == -1L) {
            log.warn("Cannot load tracking data: player name or account hash unavailable");
            return null;
        }

        try {
            String accountKey = createAccountDataKey(accountHash);
            String accountJson = configManager.getConfiguration(CONFIG_GROUP, accountKey);

            // An existing account save always takes priority over legacy data.
            if (accountJson != null) {
                return loadFromJson(accountJson, accountKey);
            }

            // Existing users: migrate their username-based save once.
            String oldKey = createDataKey(playerName);
            String oldJson = configManager.getConfiguration(CONFIG_GROUP, oldKey);

            if (oldJson != null) {
                PlayerTrackingData data = loadFromJson(oldJson, oldKey);

                if (data == null) {
                    return null;
                }

                // Preserve the original username-based save as a backup.
                if (!save(accountHash, data)) {
                    log.error("Failed to migrate tracking data for {}", playerName);
                    return null;
                }

                log.info("Migrated tracking data for {} to account-based storage", playerName);
                return data;
            }

            return new PlayerTrackingData();
        } catch (Exception e) {
            log.error("Failed to load tracking data for {}", playerName, e);
            return null;
        }
    }

    public boolean save(long accountHash, PlayerTrackingData data) {
        if (accountHash == -1L || data == null) {
            return false;
        }

        try {
            String dataKey = createAccountDataKey(accountHash);
            String json = gson.toJson(data);

            configManager.setConfiguration(CONFIG_GROUP, dataKey, json);

            String savedJson = configManager.getConfiguration(CONFIG_GROUP, dataKey);

            if (!json.equals(savedJson)) {
                log.error("Tracking data write verification failed for account hash {}", accountHash);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to save tracking data for account hash {}", accountHash, e);
            return false;
        }
    }

    public void clear(long accountHash) {
        if (accountHash == -1L) {
            return;
        }

        if (!save(accountHash, new PlayerTrackingData())) {
            log.error("Failed to clear tracking data for account hash {}", accountHash);
        }
    }

    private PlayerTrackingData loadFromJson(String json, String key) {
        try {
            if (json == null || json.trim().isEmpty()) {
                log.error("Tracking data is empty for {}", key);
                return null;
            }

            String decoded = json.trim().startsWith("{") ? json : gson.fromJson(json, String.class);

            if (decoded == null || !decoded.trim().startsWith("{")) {
                log.error("Invalid tracking data format for {}", key);
                return null;
            }

            JsonObject root = new JsonParser().parse(decoded).getAsJsonObject();

            if (!root.has("version") || !root.has("encounters")
                    || !root.get("encounters").isJsonObject()) {
                log.error("Tracking data is missing required fields for {}", key);
                return null;
            }

            PlayerTrackingData data = gson.fromJson(root, PlayerTrackingData.class);

            if (data == null || data.getVersion() != 1 || data.getEncounters() == null) {
                log.error("Unsupported or invalid tracking data for {}", key);
                return null;
            }

            return data;
        } catch (Exception e) {
            log.error("Failed to parse tracking data for {}", key, e);
            return null;
        }
    }

    public PlayerTrackingData loadLegacyData(String previousPlayerName) {
        if (previousPlayerName == null || previousPlayerName.trim().isEmpty()) {
            return null;
        }

        try {
            String oldKey = createDataKey(previousPlayerName);
            String oldJson = configManager.getConfiguration(CONFIG_GROUP, oldKey);

            if (oldJson == null) {
                return null;
            }

            return loadFromJson(oldJson, oldKey);
        } catch (Exception e) {
            log.error("Failed to load legacy tracking data for {}", previousPlayerName, e);
            return null;
        }
    }
}
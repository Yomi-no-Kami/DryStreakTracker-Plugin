package com.harrystyles.drystreaktracker;

import net.runelite.client.config.*;

@ConfigGroup("drystreaktracker")
public interface DryStreakTrackerConfig extends Config {

    /**
     * General group/settings
     */
    @ConfigSection(
            name = "General",
            description = "Configure Dry Streak Tracker chatbox settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "trackPets",
            name = "Track pet drops",
            description = "Allow pet drops to count as tracked drops and reset dry streaks",
            position = 0,
            section = generalSection
    )

    default boolean trackPets() {
        return false;
    }



    /**
     * Chatbox group/settings
     */
    @ConfigSection(
            name = "Chatbox",
            description = "Configure Dry Streak Tracker chatbox settings",
            position = 1
    )
    String chatboxSection = "chatbox";

    @ConfigItem(
            keyName = "showChatboxMessages",
            name = "Show Chatbox messages",
            description = "Show dry streak tracker messages in the chatbox",
            position = 0,
            section = chatboxSection
    )

    default boolean showChatboxMessages() {
        return true;
    }



    /**
     * Notification Group/Settings
     */
    @ConfigSection(
            name = "Notifications",
            description = "Configure Dry Streak Tracker pop-up notification settings",
            position = 2
    )
    String notificationSection = "notifications";

    @ConfigItem(
            keyName = "showNotifications",
            name = "Show notification pop-ups",
            description = "Show pop-up notifications for drops, pets, and dry streak records",
            position = 0,
            section = notificationSection
    )
    default boolean showNotifications() {
        return true;
    }

    @ConfigSection(
            name = "Discord Integration",
            description = "Discord integration settings",
            position = 3
    )
    String discordSection = "discord";

    @ConfigItem(
            keyName = "discordWebhookUrl",
            name = "Discord webhook URL",
            description = "Webhook used to upload tracked drops to Discord",
            position = 0,
            section = discordSection
    )
    default String discordWebhookUrl() {
        return "";
    }

    @Range(
            min = 0,
            max = Integer.MAX_VALUE
    )
    @ConfigItem(
            keyName = "discordMinimumGeValue",
            name = "Minimum GE value",
            description = "Minimum GE value required before a drop can be AUTOMATICALLY uploaded to Discord",
            position = 1,
            section = discordSection
    )
    default int discordMinimumGeValue() {
        return 10000000;
    }

    @ConfigItem(
            keyName = "discordAutomaticUploads",
            name = "Automatically upload drops",
            description = "Automatically send eligible tracked drops and a screenshot to the configured Discord webhook",
            position = 2,
            section = discordSection
    )
    default boolean discordAutomaticUploads() {
        return false;
    }

    @ConfigItem(
            keyName = "discordIncludeScreenshot",
            name = "Include screenshot for Auto-Upload",
            description = "Include a RuneLite screenshot with automatic Discord uploads",
            position = 3,
            section = discordSection
    )

    default boolean discordIncludeScreenshot() {
        return false;
    }

    @ConfigItem(
            keyName = "discordAlwaysUploadPets",
            name = "Always upload pets",
            description = "Always upload tracked pet drops regardless of the minimum GE value",
            position = 4,
            section = discordSection
    )
    default boolean discordAlwaysUploadPets() {
        return true;
    }




    /**
     * Debugging group/settings
     */
    @ConfigSection(
            name = "Debugging",
            description = "Configure Dry Streak Tracker debug settings",
            position = 4
    )
    String debuggingSection = "debugging";

    @ConfigItem(
            keyName = "showLootSourceDebug",
            name = "Show loot sources",
            description = "Show loot source names in the chatbox for reporting missing encounters.",
            position = 0,
            section = debuggingSection
    )
    default boolean showLootSourceDebug() {
        return false;
    }

}
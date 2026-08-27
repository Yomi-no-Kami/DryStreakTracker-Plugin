package com.harrystyles.drystreaktracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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


    /**
     * Debugging group/settings
     */
    @ConfigSection(
            name = "Debugging",
            description = "Configure Dry Streak Tracker debug settings",
            position = 3
    )
    String debuggingSection = "debugging";

    @ConfigItem(
            keyName = "showLootSourceDebug",
            name = "Show loot sources",
            description = "Show loot source names in the chatbox for reporting missing encounters. This does not debug regular NPC's that drop loot to the ground.",
            position = 0,
            section = debuggingSection
    )
    default boolean showLootSourceDebug() {
        return false;
    }

}
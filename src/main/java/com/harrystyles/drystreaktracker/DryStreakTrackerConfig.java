package com.harrystyles.drystreaktracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("drystreaktracker")
public interface DryStreakTrackerConfig extends Config {
    @ConfigItem(
            keyName = "showChatboxMessages",
            name = "Show Chatbox messages",
            description = "Show dry streak tracker messages in the chatbox",
            position = 1
    )
    default boolean showChatboxMessages() {
        return true;
    }

    @ConfigItem(
            keyName = "trackPets",
            name = "Track pet drops",
            description = "Allow pet drops to count as tracked drops and reset dry streaks",
            position = 2
    )
    default boolean trackPets() {
        return false;
    }
}
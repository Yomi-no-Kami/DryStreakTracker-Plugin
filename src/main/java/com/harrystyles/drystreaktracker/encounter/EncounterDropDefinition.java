package com.harrystyles.drystreaktracker.encounter;

/**
 * Defines one selectable tracked drop for an encounter.
 *
 * Drops may be enabled or disabled by default. Players can
 * override the default from the encounter right-click menu.
 */
public class EncounterDropDefinition {
    private int itemId;

    private boolean enabledByDefault = true;

    public EncounterDropDefinition() {
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public void setEnabledByDefault(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }
}
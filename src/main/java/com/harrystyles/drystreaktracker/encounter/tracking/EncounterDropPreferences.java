package com.harrystyles.drystreaktracker.encounter.tracking;

import java.util.HashSet;
import java.util.Set;

/**
 * Stores player-specific overrides for an encounter's
 * configurable tracked drops.
 *
 * Items not present in either set use the default supplied
 * by the encounter definition.
 */
public class EncounterDropPreferences {
    private Set<Integer> enabled = new HashSet<>();

    private Set<Integer> disabled = new HashSet<>();

    public EncounterDropPreferences() {
    }

    public Set<Integer> getEnabled() {
        if (enabled == null) {
            enabled = new HashSet<>();
        }

        return enabled;
    }

    public Set<Integer> getDisabled() {
        if (disabled == null) {
            disabled = new HashSet<>();
        }

        return disabled;
    }

    public void setEnabled(Set<Integer> enabled) {
        this.enabled = enabled == null ? new HashSet<>() : new HashSet<>(enabled);
    }

    public void setDisabled(Set<Integer> disabled) {
        this.disabled = disabled == null ? new HashSet<>() : new HashSet<>(disabled);
    }
}
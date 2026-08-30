package com.harrystyles.drystreaktracker.encounter.tracking;

import com.harrystyles.drystreaktracker.Constants;
import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterStats;

import java.util.*;

/**
 * Stores all player-specific dry streak data.
 *
 * @author Harry Styles
 */
public class PlayerTrackingData {
    /**
     * Saved data format version.
     */
    private int version = 1;

    /**
     * Encounter statistics indexed by encounter ID.
     */
    private Map<String, EncounterStats> encounters = new HashMap<>();

    private List<RecentDrop> recentDrops = new ArrayList<>();

    public PlayerTrackingData() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Map<String, EncounterStats> getEncounters() {
        return encounters;
    }

    public List<RecentDrop> getRecentDrops() {
        if (recentDrops == null) {
            recentDrops = new ArrayList<>();
        }

        return recentDrops;
    }

    public void setEncounters(Map<String, EncounterStats> encounters) {
        this.encounters = encounters == null ? new HashMap<>() : encounters;
    }

    public void setRecentDrops(List<RecentDrop> recentDrops) {
        this.recentDrops = recentDrops == null ? new ArrayList<>() : new ArrayList<>(recentDrops);
    }


    public EncounterStats getEncounter(String encounterId) {
        if (encounterId == null) {
            return null;
        }

        return encounters.get(encounterId);
    }

    public void putEncounter(EncounterStats encounter) {
        if (encounter == null || encounter.getEncounterId() == null) {
            return;
        }

        encounters.put(encounter.getEncounterId(), encounter);
    }

    /**
     * Removes all saved statistics for one encounter.
     *
     * @param encounterId encounter to remove
     */
    public void removeEncounter(String encounterId) {
        if (encounterId == null || encounters == null) {
            return;
        }

        encounters.remove(encounterId);
    }

    public Collection<EncounterStats> getAllEncounters() {
        return encounters.values();
    }

    public boolean hasEncounter(String encounterId) {
        return encounterId != null && encounters.containsKey(encounterId);
    }

    public EncounterStats getOrCreateEncounter(EncounterDefinition definition) {
        if (definition == null || definition.getEncounterId() == null) {
            return null;
        }

        if (encounters == null) {
            encounters = new HashMap<>();
        }

        EncounterStats stats = encounters.get(definition.getEncounterId());

        if (stats == null) {
            stats = new EncounterStats(definition.getEncounterId(), definition.getDisplayName(), definition.getNpcIds());

            encounters.put(definition.getEncounterId(), stats);
        }

        return stats;
    }

    public void addRecentDrop(RecentDrop recentDrop) {
        if (recentDrop == null) {
            return;
        }

        getRecentDrops().add(0, recentDrop);

        while (recentDrops.size() > Constants.RECENT_DROPS_HISTORY_MAX_AMOUNT) {
            recentDrops.remove(recentDrops.size() - 1);
        }
    }

    public void clearRecentDrops() {
        getRecentDrops().clear();
    }

    public void removeRecentDrop(RecentDrop recentDrop) {
        if (recentDrop == null) {
            return;
        }

        getRecentDrops().remove(recentDrop);
    }

    public void clearEncounters() {
        encounters.clear();
    }

    @Override
    public String toString() {
        return "PlayerTrackingData{" +
                "version=" + version +
                ", encounters=" + encounters.size() +
                '}';
    }
}
package com.harrystyles.drystreaktracker.encounter;

import java.util.HashSet;
import java.util.Set;

/**
 * Static definition of an encounter.
 * Definitions may be loaded from:
 * <p>
 * encounters.json
 * -> NpcLootReceived encounters
 * <p>
 * loot-received-encounters.json
 * -> LootReceived encounters
 * <p>
 * The JSON itself does not need to contain lootType.
 * EncounterDefinitionLoader assigns it automatically depending
 * on which resource the definition came from
 */
public class EncounterDefinition {
    private String encounterId;

    private String displayName;

    private String imageUrl;

    /**
     * Assigned internally by EncounterDefinitionLoader.
     * <p>
     * This does NOT need to appear in JSON.
     */
    private EncounterLootType lootType;

    /**
     * NPC IDs belonging to this encounter.
     * <p>
     * Used by encounters.json / NpcLootReceived.
     */
    private Set<Integer> npcIds = new HashSet<>();

    /**
     * Names supplied by LootReceived.getName().
     * <p>
     * Used by loot-received-encounters.json.
     */
    private Set<String> lootSourceNames = new HashSet<>();

    /**
     * RuneLite boss killcount names used when looking up
     * the player's actual KC.
     * <p>
     * If multiple names are supplied, their killcounts are
     * added together.
     * <p>
     * If no names are supplied, RuneLite KC is not tracked
     * for this encounter.
     */
    private Set<String> killcountNames = new HashSet<>();

    /**
     * Item IDs which reset the dry streak.
     */
    private Set<Integer> trackedDropIds = new HashSet<>();

    /**
     * Pet item IDs.
     * <p>
     * These count only when trackPets is enabled.
     */
    private Set<Integer> petDropIds = new HashSet<>();


    public EncounterDefinition() {
    }


    public String getEncounterId() {
        return encounterId;
    }


    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }


    public String getDisplayName() {
        return displayName;
    }


    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    public String getImageUrl() {
        return imageUrl;
    }


    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }


    public EncounterLootType getLootType() {
        return lootType;
    }

    public Set<String> getKillcountNames() {
        if (killcountNames == null) {
            killcountNames = new HashSet<>();
        }

        return killcountNames;
    }

    public void setKillcountNames(Set<String> killcountNames) {
        this.killcountNames = killcountNames == null ? new HashSet<>() : new HashSet<>(killcountNames);
    }


    public void setLootType(EncounterLootType lootType) {
        this.lootType = lootType;
    }


    public Set<Integer> getNpcIds() {
        if (npcIds == null) {
            npcIds = new HashSet<>();
        }

        return npcIds;
    }


    public void setNpcIds(Set<Integer> npcIds) {
        this.npcIds = npcIds == null ? new HashSet<>() : new HashSet<>(npcIds);
    }


    public Set<String> getLootSourceNames() {
        if (lootSourceNames == null) {
            lootSourceNames = new HashSet<>();
        }

        return lootSourceNames;
    }


    public void setLootSourceNames(Set<String> lootSourceNames) {
        this.lootSourceNames = lootSourceNames == null ? new HashSet<>() : new HashSet<>(lootSourceNames);
    }


    public Set<Integer> getTrackedDropIds() {
        if (trackedDropIds == null) {
            trackedDropIds = new HashSet<>();
        }

        return trackedDropIds;
    }


    public void setTrackedDropIds(Set<Integer> trackedDropIds) {
        this.trackedDropIds = trackedDropIds == null ? new HashSet<>() : new HashSet<>(trackedDropIds);
    }


    public Set<Integer> getPetDropIds() {
        if (petDropIds == null) {
            petDropIds = new HashSet<>();
        }

        return petDropIds;
    }


    public void setPetDropIds(
            Set<Integer> petDropIds) {
        this.petDropIds = petDropIds == null ? new HashSet<>() : new HashSet<>(petDropIds);
    }


    public boolean matchesNpc(int npcId) {
        return getNpcIds().contains(npcId);
    }


    public boolean matchesLootSource(String sourceName) {
        if (sourceName == null) {
            return false;
        }

        String normalized = sourceName.trim();

        for (String configuredSource : getLootSourceNames()) {
            if (configuredSource != null && configuredSource.trim().equalsIgnoreCase(normalized)) {
                return true;
            }
        }

        return false;
    }


    public boolean isTrackedDrop(int itemId) {
        return getTrackedDropIds().contains(itemId);
    }


    public boolean isPetDrop(int itemId) {
        return getPetDropIds().contains(itemId);
    }


    public boolean isQualifyingDrop(int itemId, boolean trackPets) {
        if (isTrackedDrop(itemId)) {
            return true;
        }

        return trackPets && isPetDrop(itemId);
    }


    @Override
    public String toString() {
        return "EncounterDefinition{" +
                "encounterId='" + encounterId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", lootType=" + lootType +
                ", npcIds=" + npcIds +
                ", lootSourceNames=" + lootSourceNames +
                ", killcountNames=" + killcountNames +
                ", trackedDropIds=" + trackedDropIds +
                ", petDropIds=" + petDropIds +
                '}';
    }
}
package com.harrystyles.drystreaktracker.encounter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

/**
 * Registry containing all encounter definitions.
 * <p>
 * Definitions can originate from multiple JSON resources:
 * <p>
 * encounters.json
 * loot-received-encounters.json
 */
@Singleton
public class EncounterRegistry {
    /**
     * Encounter ID -> encounter.
     */
    private final Map<String, EncounterDefinition> encountersById = new HashMap<>();

    /**
     * NPC ID -> encounter.
     * <p>
     * Used by NpcLootReceived.
     */
    private final Map<Integer, EncounterDefinition> encountersByNpcId = new HashMap<>();

    /**
     * LootReceived source name -> encounter.
     */
    private final Map<String, EncounterDefinition> encountersByLootSourceName = new HashMap<>();


    public void register(EncounterDefinition encounter) {
        if (encounter == null) {
            throw new IllegalArgumentException("Encounter cannot be null");
        }

        if (encounter.getEncounterId() == null || encounter.getEncounterId().trim().isEmpty()) {
            throw new IllegalArgumentException("Encounter ID cannot be null or empty");
        }

        String encounterId = encounter.getEncounterId().trim();

        if (encountersById.containsKey(encounterId)) {
            throw new IllegalStateException("Encounter ID already registered: " + encounterId);
        }

        /**
         * Validate NPC IDs before modifying the registry.
         */
        for (Integer npcId : encounter.getNpcIds()) {
            if (npcId == null) {
                continue;
            }

            EncounterDefinition existing = encountersByNpcId.get(npcId);

            if (existing != null) {
                throw new IllegalStateException("NPC ID " + npcId + " is already registered to " + existing.getEncounterId());
            }
        }

        /*
         * Validate LootReceived source names before modifying
         * the registry.
         */
        for (String lootSourceName : encounter.getLootSourceNames()) {
            if (lootSourceName == null || lootSourceName.trim().isEmpty()) {
                continue;
            }

            String normalizedName = normalizeLootSourceName(lootSourceName);

            EncounterDefinition existing = encountersByLootSourceName.get(normalizedName);

            if (existing != null) {
                throw new IllegalStateException("Loot source name '" + lootSourceName + "' is already registered to " + existing.getEncounterId()
                );
            }
        }

        /*
         * Register encounter by encounter ID.
         */
        encountersById.put(encounterId, encounter);

        /*
         * Register NPC IDs.
         */
        for (Integer npcId : encounter.getNpcIds()) {
            if (npcId != null) {
                encountersByNpcId.put(npcId, encounter);
            }
        }

        /*
         * Register LootReceived source names.
         */
        for (String lootSourceName : encounter.getLootSourceNames()) {
            if (lootSourceName != null && !lootSourceName.trim().isEmpty()) {
                encountersByLootSourceName.put(normalizeLootSourceName(lootSourceName), encounter);
            }
        }
    }


    public EncounterDefinition getById(String encounterId) {
        if (encounterId == null) {
            return null;
        }

        return encountersById.get(encounterId.trim());
    }


    public EncounterDefinition getByNpcId(int npcId) {
        return encountersByNpcId.get(npcId);
    }


    public EncounterDefinition getByLootSourceName(String sourceName) {
        if (sourceName == null || sourceName.trim().isEmpty()) {
            return null;
        }

        return encountersByLootSourceName.get(normalizeLootSourceName(sourceName));
    }


    public boolean containsNpcId(int npcId) {
        return encountersByNpcId.containsKey(npcId);
    }


    public boolean containsLootSourceName(String sourceName) {
        if (sourceName == null || sourceName.trim().isEmpty()) {
            return false;
        }

        return encountersByLootSourceName.containsKey(normalizeLootSourceName(sourceName));
    }


    public Collection<EncounterDefinition> getAll() {
        return Collections.unmodifiableCollection(encountersById.values());
    }


    public int size() {
        return encountersById.size();
    }


    public void clear() {
        encountersById.clear();
        encountersByNpcId.clear();
        encountersByLootSourceName.clear();
    }


    private String normalizeLootSourceName(String sourceName) {
        return sourceName.trim().toLowerCase();
    }
}
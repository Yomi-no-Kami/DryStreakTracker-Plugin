package com.harrystyles.drystreaktracker.encounter;

/**
 * Defines which RuneLite loot event is responsible for
 * completing an encounter.
 */
public enum EncounterLootType {
    /**
     * Normal NPC death loot.
     * <p>
     * Encounter is identified by NPC ID and handled through
     * NpcLootReceived.
     */
    GROUND_LOOT,

    /**
     * Loot generated through RuneLite's generic LootReceived
     * event.
     * <p>
     * Example:
     * pickpocketing
     * chests
     * objects
     * reward containers
     * raids/events
     */
    LOOT_RECEIVED
}
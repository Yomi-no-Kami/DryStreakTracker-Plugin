package com.harrystyles.drystreaktracker.encounter;

/**
 * Defines which RuneLite loot event is responsible for
 * completing an encounter.
 */
public enum EncounterLootType
{
    /**
     * Normal NPC death loot.
     *
     * Encounter is identified by NPC ID and handled through
     * NpcLootReceived.
     */
    GROUND_LOOT,

    /**
     * Loot generated through RuneLite's generic LootReceived
     * event.
     *
     * Examples:
     * - pickpocketing
     * - chests
     * - objects
     * - reward containers
     * - raids/events
     */
    LOOT_RECEIVED
}
package com.harrystyles.drystreaktracker.wiki;

/**
 * One selectable NPC drop returned by the OSRS Wiki.
 */
public class WikiDrop {
    private final int itemId;
    private final String itemName;

    public WikiDrop(int itemId, String itemName) {
        this.itemId = itemId;
        this.itemName = itemName;
    }

    public int getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }
}
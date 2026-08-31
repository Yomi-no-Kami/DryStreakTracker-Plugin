package com.harrystyles.drystreaktracker.encounter.tracking;

public class RecentDrop {
    private String encounterId;

    private String encounterName;

    private String encounterImageUrl;

    private String playerName;

    private int itemId;

    private int quantity;

    private int dropKillcount;

    private int totalKillcount;

    private int geValue;

    private long acquiredAt;

    public RecentDrop() {
    }

    public RecentDrop(String playerName, String encounterId, String encounterName, String encounterImageUrl, int itemId, int quantity, int dropKillcount, int totalKillcount, int geValue, long acquiredAt) {
        this.playerName = playerName;
        this.encounterId = encounterId;
        this.encounterName = encounterName;
        this.encounterImageUrl = encounterImageUrl;
        this.itemId = itemId;
        this.quantity = quantity;
        this.dropKillcount = dropKillcount;
        this.totalKillcount = totalKillcount;
        this.geValue = geValue;
        this.acquiredAt = acquiredAt;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getEncounterId() {
        return encounterId;
    }

    public String getEncounterName() {
        return encounterName;
    }

    public String getEncounterImageUrl() {
        return encounterImageUrl;
    }

    public int getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getDropKillcount() {
        return dropKillcount;
    }

    public int getTotalKillcount() {
        return totalKillcount;
    }

    public int getGeValue() {
        return geValue;
    }

    public long getAcquiredAt() {
        return acquiredAt;
    }
}
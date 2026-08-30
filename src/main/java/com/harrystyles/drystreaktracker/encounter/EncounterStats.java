package com.harrystyles.drystreaktracker.encounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Player-specific statistics for an encounter.
 *
 * @author Harry Styles
 */
public class EncounterStats {
    private String encounterId;

    private String displayName;

    private Set<Integer> npcIds = new HashSet<>();

    private int lastKnownKillcount;

    private int totalKillsTracked;

    private int currentDryStreak;

    private int totalTrackedDrops;

    /**
     * Dry Streak Tracker's kill number when the most recent
     * tracked drop was received.
     * <p>
     * This is based only on kills recorded while the plugin
     * is tracking the encounter, not the player's actual boss KC.
     * <p>
     * Example:
     * The plugin has tracked 10 General Graardor kills and a
     * unique is received on the 10th tracked kill.
     * lastDropKillcount = 10
     */
    private int lastDropKillcount;

    /**
     * Player's actual boss KC when the most recent tracked
     * drop was received.
     * <p>
     * This value is obtained from RuneLite's stored boss
     * killcount and is saved as a snapshot when the drop occurs.
     * It does not continue increasing as the player gets more kills.
     * <p>
     * Example:
     * The plugin's 10th tracked General Graardor kill occurs
     * at the player's actual KC of 2,500 and a unique is received.
     * lastDropTotalKillcount = 2500
     */
    private int lastDropTotalKillcount;

    /**
     * Length of the dry streak that ended when the most
     * recent tracked drop was received.
     * <p>
     * This is captured immediately before currentDryStreak
     * is reset to 0.
     * <p>
     * Example:
     * A tracked drop is received after 684 dry kills.
     * lastCompletedDryStreak = 684
     */
    private int lastCompletedDryStreak;

    private int longestDryStreak;

    /**
     * Prevents repeated record notifications during the same
     * dry streak.
     * <p>
     * Once the current streak surpasses the previous record,
     * the notification toast is only sent once. This resets when
     * a tracked drop is received.
     */
    private boolean dryRecordNotificationSent;

    /**
     * Indicates that the most recently recorded kill was the
     * kill that surpassed the player's previous dry streak record.
     */
    private transient boolean newDryRecordThisKill;

    /**
     * Time this encounter was most recently completed.
     * <p>
     * Used to sort the sidebar so the most recently
     * killed encounter appears at the top.
     */
    private long lastActivityTime;

    /**
     * Item ID -> total quantity received.
     */
    private Map<Integer, Integer> receivedDrops = new HashMap<>();

    public EncounterStats() {
    }

    public EncounterStats(String encounterId, String displayName, Set<Integer> npcIds) {
        this.encounterId = encounterId;
        this.displayName = displayName;

        if (npcIds != null) {
            this.npcIds = new HashSet<>(npcIds);
        }
    }

    public String getEncounterId() {
        return encounterId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Integer> getNpcIds() {
        return npcIds;
    }

    public int getLastKnownKillcount() {
        return lastKnownKillcount;
    }

    public int getTotalKillsTracked() {
        return totalKillsTracked;
    }

    public int getCurrentDryStreak() {
        return currentDryStreak;
    }

    public int getTotalTrackedDrops() {
        return totalTrackedDrops;
    }

    public int getLastDropKillcount() {
        return lastDropKillcount;
    }

    public int getLastDropTotalKillcount() {
        return lastDropTotalKillcount;
    }

    public int getLastCompletedDryStreak() {
        return lastCompletedDryStreak;
    }

    public int getLongestDryStreak() {
        return longestDryStreak;
    }

    public boolean isNewDryRecordThisKill() {
        return newDryRecordThisKill;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }


    public Map<Integer, Integer> getReceivedDrops() {
        if (receivedDrops == null) {
            receivedDrops = new HashMap<>();
        }

        return receivedDrops;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setNpcIds(Set<Integer> npcIds) {
        this.npcIds = npcIds == null ? new HashSet<>() : new HashSet<>(npcIds);
    }

    public void setLastKnownKillcount(int lastKnownKillcount) {
        this.lastKnownKillcount = lastKnownKillcount;
    }

    public void setTotalKillsTracked(int totalKillsTracked) {
        this.totalKillsTracked = totalKillsTracked;
    }

    public void setCurrentDryStreak(int currentDryStreak) {
        this.currentDryStreak = currentDryStreak;
    }

    public void setTotalTrackedDrops(int totalTrackedDrops) {
        this.totalTrackedDrops = totalTrackedDrops;
    }

    public void setLastDropKillcount(int lastDropKillcount) {
        this.lastDropKillcount = lastDropKillcount;
    }

    public void setLastDropTotalKillcount(int lastDropTotalKillcount) {
        this.lastDropTotalKillcount = lastDropTotalKillcount;
    }

    public void setLongestDryStreak(int longestDryStreak) {
        this.longestDryStreak = longestDryStreak;
    }

    public void setLastActivityTime(long lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public void setReceivedDrops(Map<Integer, Integer> receivedDrops) {
        this.receivedDrops = receivedDrops == null ? new HashMap<>() : new HashMap<>(receivedDrops);
    }

    public void recordDryKill(int currentKillcount) {

        /**
         * This flag only represents the kill currently
         * being processed.
         */
        newDryRecordThisKill = false;

        lastActivityTime = System.currentTimeMillis();

        lastKnownKillcount = currentKillcount;

        totalKillsTracked++;

        currentDryStreak++;

        /*
         * Check whether this kill beats the existing record
         * before updating longestDryStreak.
         *
         * Require at least one previous tracked drop so the
         * player's very first dry streak does not constantly
         * create a record with nothing meaningful to beat.
         */
        if (currentDryStreak > longestDryStreak) {
            if (!dryRecordNotificationSent && totalTrackedDrops > 0) {
                newDryRecordThisKill = true;

                dryRecordNotificationSent = true;
            }

            longestDryStreak = currentDryStreak;
        }
    }

    public void recordDrop(int currentKillcount, int totalKillcount, int itemId, int quantity) {
        lastActivityTime = System.currentTimeMillis();

        lastKnownKillcount = currentKillcount;

        totalKillsTracked++;
        totalTrackedDrops++;

        lastDropKillcount = currentKillcount;

        lastDropTotalKillcount = totalKillcount;

        /*
         * The drop itself happened on the next kill after
         * currentDryStreak.
         *
         * Example:
         * 19 dry kills followed by a unique means the unique
         * took 20 kills.
         */
        int completedDropStreak = currentDryStreak + 1;

        /*
         * Check whether the kill that produced this drop also
         * established a new dry streak record.
         *
         * Do this BEFORE resetting the streak.
         */
        newDryRecordThisKill = totalTrackedDrops > 1 && !dryRecordNotificationSent && completedDropStreak > longestDryStreak;

        if (completedDropStreak > longestDryStreak) {
            longestDryStreak = completedDropStreak;
        }

        /*
         * Save how many kills it took to receive this drop,
         * including the kill that actually produced the unique.
         */
        lastCompletedDryStreak = completedDropStreak;

        /*
         * The drop ends the current streak.
         */
        dryRecordNotificationSent = false;

        currentDryStreak = 0;

        receivedDrops.merge(itemId, quantity, Integer::sum);
    }
}
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
public class EncounterStats
{
    private String encounterId;

    private String displayName;

    private Set<Integer> npcIds =
            new HashSet<>();

    private int lastKnownKillcount;

    private int totalKillsTracked;

    private int currentDryStreak;

    private int totalTrackedDrops;

    /**
     * Dry Streak Tracker's kill number when the most recent
     * tracked drop was received.
     *
     * This is based only on kills recorded while the plugin
     * is tracking the encounter, not the player's actual boss KC.
     *
     * Example:
     * The plugin has tracked 10 General Graardor kills and a
     * unique is received on the 10th tracked kill.
     * lastDropKillcount = 10
     */
    private int lastDropKillcount;

    /**
     * Player's actual boss KC when the most recent tracked
     * drop was received.
     *
     * This value is obtained from RuneLite's stored boss
     * killcount and is saved as a snapshot when the drop occurs.
     * It does not continue increasing as the player gets more kills.
     *
     * Example:
     * The plugin's 10th tracked General Graardor kill occurs
     * at the player's actual KC of 2,500 and a unique is received.
     * lastDropTotalKillcount = 2500
     */
    private int lastDropTotalKillcount;

    private int longestDryStreak;

    /**
     * Time this encounter was most recently completed.
     *
     * Used to sort the sidebar so the most recently
     * killed encounter appears at the top.
     */
    private long lastActivityTime;

    /**
     * Item ID -> total quantity received.
     */
    private Map<Integer, Integer> receivedDrops =
            new HashMap<>();

    public EncounterStats()
    {
    }

    public EncounterStats(
            String encounterId,
            String displayName,
            Set<Integer> npcIds)
    {
        this.encounterId = encounterId;
        this.displayName = displayName;

        if (npcIds != null)
        {
            this.npcIds =
                    new HashSet<>(npcIds);
        }
    }

    public String getEncounterId()
    {
        return encounterId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public Set<Integer> getNpcIds()
    {
        return npcIds;
    }

    public int getLastKnownKillcount()
    {
        return lastKnownKillcount;
    }

    public int getTotalKillsTracked()
    {
        return totalKillsTracked;
    }

    public int getCurrentDryStreak()
    {
        return currentDryStreak;
    }

    public int getTotalTrackedDrops()
    {
        return totalTrackedDrops;
    }

    public int getLastDropKillcount()
    {
        return lastDropKillcount;
    }

    public int getLastDropTotalKillcount() { return lastDropTotalKillcount; }

    public int getLongestDryStreak()
    {
        return longestDryStreak;
    }

    public long getLastActivityTime() { return lastActivityTime; }


    public Map<Integer, Integer> getReceivedDrops()
    {
        if (receivedDrops == null)
        {
            receivedDrops =
                    new HashMap<>();
        }

        return receivedDrops;
    }

    public void setEncounterId(String encounterId)
    {
        this.encounterId = encounterId;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public void setNpcIds(Set<Integer> npcIds)
    {
        this.npcIds =
                npcIds == null
                        ? new HashSet<>()
                        : new HashSet<>(npcIds);
    }

    public void setLastKnownKillcount(
            int lastKnownKillcount)
    {
        this.lastKnownKillcount =
                lastKnownKillcount;
    }

    public void setTotalKillsTracked(
            int totalKillsTracked)
    {
        this.totalKillsTracked =
                totalKillsTracked;
    }

    public void setCurrentDryStreak(
            int currentDryStreak)
    {
        this.currentDryStreak =
                currentDryStreak;
    }

    public void setTotalTrackedDrops(
            int totalTrackedDrops)
    {
        this.totalTrackedDrops =
                totalTrackedDrops;
    }

    public void setLastDropKillcount(
            int lastDropKillcount)
    {
        this.lastDropKillcount =
                lastDropKillcount;
    }

    public void setLastDropTotalKillcount(
            int lastDropTotalKillcount)
    {
        this.lastDropTotalKillcount =
                lastDropTotalKillcount;
    }

    public void setLongestDryStreak(
            int longestDryStreak)
    {
        this.longestDryStreak =
                longestDryStreak;
    }

    public void setLastActivityTime(
            long lastActivityTime)
    {
        this.lastActivityTime =
                lastActivityTime;
    }

    public void setReceivedDrops(
            Map<Integer, Integer> receivedDrops)
    {
        this.receivedDrops =
                receivedDrops == null
                        ? new HashMap<>()
                        : new HashMap<>(receivedDrops);
    }

    public void recordDryKill(
            int currentKillcount)
    {
        lastActivityTime =
                System.currentTimeMillis();

        lastKnownKillcount =
                currentKillcount;

        totalKillsTracked++;

        currentDryStreak++;

        if (currentDryStreak > longestDryStreak)
        {
            longestDryStreak =
                    currentDryStreak;
        }
    }

    public void recordDrop(
            int currentKillcount,
            int totalKillcount,
            int itemId,
            int quantity)
    {
        lastActivityTime =
                System.currentTimeMillis();

        lastKnownKillcount =
                currentKillcount;

        totalKillsTracked++;

        totalTrackedDrops++;

        lastDropKillcount =
                currentKillcount;

        lastDropTotalKillcount =
                totalKillcount;

        currentDryStreak = 0;

        receivedDrops.merge(
                itemId,
                quantity,
                Integer::sum
        );
    }
}
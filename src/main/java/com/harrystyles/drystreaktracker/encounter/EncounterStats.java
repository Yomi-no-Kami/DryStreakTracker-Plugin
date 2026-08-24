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

    private int lastDropKillcount;

    private int longestDryStreak;

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

    public int getLongestDryStreak()
    {
        return longestDryStreak;
    }

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

    public void setLongestDryStreak(
            int longestDryStreak)
    {
        this.longestDryStreak =
                longestDryStreak;
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
            int itemId,
            int quantity)
    {
        lastKnownKillcount =
                currentKillcount;

        totalKillsTracked++;

        totalTrackedDrops++;

        lastDropKillcount =
                currentKillcount;

        currentDryStreak = 0;

        receivedDrops.merge(
                itemId,
                quantity,
                Integer::sum
        );
    }
}
package com.harrystyles.drystreaktracker.ui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

/**
 * Caches resolved item display information for the
 * lifetime of the plugin.
 *
 * Item names and sprites are resolved from RuneLite once
 * and then reused by sidebar refreshes.
 */
@Singleton
public class ItemDisplayCache
{
    private final Map<Integer, ItemDisplayData> cache =
            new HashMap<>();

    /**
     * Gets cached display data for an item.
     */
    public ItemDisplayData get(
            int itemId)
    {
        return cache.get(
                itemId
        );
    }

    /**
     * Checks whether display data has already been cached.
     */
    public boolean contains(
            int itemId)
    {
        return cache.containsKey(
                itemId
        );
    }

    /**
     * Stores resolved display data.
     */
    public void put(
            int itemId,
            ItemDisplayData data)
    {
        if (data == null)
        {
            return;
        }

        cache.put(
                itemId,
                data
        );
    }

    /**
     * Adds all supplied display data to the cache.
     */
    public void putAll(
            Map<Integer, ItemDisplayData> data)
    {
        if (data == null
                || data.isEmpty())
        {
            return;
        }

        cache.putAll(
                data
        );
    }

    /**
     * Returns an immutable snapshot of the cache.
     */
    public Map<Integer, ItemDisplayData> snapshot()
    {
        return Collections.unmodifiableMap(
                new HashMap<>(
                        cache
                )
        );
    }

    /**
     * Clears the in-memory display cache.
     *
     * Player drop statistics are NOT affected.
     */
    public void clear()
    {
        cache.clear();
    }
}
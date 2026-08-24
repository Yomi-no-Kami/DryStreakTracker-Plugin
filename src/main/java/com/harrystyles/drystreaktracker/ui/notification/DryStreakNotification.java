package com.harrystyles.drystreaktracker.ui.notification;

import java.util.Optional;

/**
 * Represents an in-game Dry Streak Tracker notification.
 */
public class DryStreakNotification
{
    private static final int NO_COLOR = -1;

    private final String title;
    private final String text;
    private final int color;

    public DryStreakNotification(
            String title,
            String text,
            int color)
    {
        this.title = title;
        this.text = text;
        this.color = color;
    }

    public DryStreakNotification(
            String title,
            String text)
    {
        this(
                title,
                text,
                NO_COLOR
        );
    }

    public String getTitle()
    {
        return title;
    }

    public String getText()
    {
        return text;
    }

    public int getColor()
    {
        return color;
    }

    public boolean hasCustomColor()
    {
        return color != NO_COLOR;
    }

    public Optional<Integer> getCustomColor()
    {
        return hasCustomColor()
                ? Optional.of(color)
                : Optional.empty();
    }
}
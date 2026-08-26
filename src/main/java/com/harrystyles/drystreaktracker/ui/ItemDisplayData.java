package com.harrystyles.drystreaktracker.ui;

import java.awt.Image;

/**
 * Client-thread-resolved item information used by
 * Swing components.
 */
public class ItemDisplayData {
    private final String name;
    private final Image image;

    public ItemDisplayData(String name, Image image) {
        this.name = name;
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public Image getImage() {
        return image;
    }
}
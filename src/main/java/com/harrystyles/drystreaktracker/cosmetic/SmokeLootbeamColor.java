package com.harrystyles.drystreaktracker.cosmetic;

public enum SmokeLootbeamColor {
    PURPLE("Purple", 52),
    RED("Red", 0),
    ORANGE("Orange", 5),
    YELLOW("Yellow", 10),
    GREEN("Green", 21),
    CYAN("Cyan", 32),
    BLUE("Blue", 42),
    PINK("Pink", 58);

    private final String displayName;
    private final int hue;

    SmokeLootbeamColor(String displayName, int hue) {
        this.displayName = displayName;
        this.hue = hue;
    }

    public int getHue() {
        return hue;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
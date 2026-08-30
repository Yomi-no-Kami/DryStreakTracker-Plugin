package com.harrystyles.drystreaktracker.discord;

public class DiscordField {
    private final String name;
    private final String value;
    private final boolean inline;

    public DiscordField(String name, String value, boolean inline) {
        this.name = name;
        this.value = value;
        this.inline = inline;
    }
}
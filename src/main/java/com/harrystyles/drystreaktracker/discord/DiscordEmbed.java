package com.harrystyles.drystreaktracker.discord;

public class DiscordEmbed {
    private final String title;
    private final String description;
    private final int color;
    private final DiscordField[] fields;
    private DiscordThumbnail thumbnail;

    public DiscordEmbed(String title, String description, int color, DiscordField[] fields) {
        this.title = title;
        this.description = description;
        this.color = color;
        this.fields = fields;
    }

    public void setThumbnail(DiscordThumbnail thumbnail) {
        this.thumbnail = thumbnail;
    }

}
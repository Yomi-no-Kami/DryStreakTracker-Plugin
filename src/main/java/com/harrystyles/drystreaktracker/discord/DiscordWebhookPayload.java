package com.harrystyles.drystreaktracker.discord;

public class DiscordWebhookPayload {
    private String username;
    private DiscordEmbed[] embeds;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmbeds(DiscordEmbed[] embeds) {
        this.embeds = embeds;
    }
}
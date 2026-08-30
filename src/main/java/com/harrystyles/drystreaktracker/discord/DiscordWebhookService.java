package com.harrystyles.drystreaktracker.discord;

import com.google.gson.Gson;
import com.harrystyles.drystreaktracker.DryStreakTrackerConfig;
import com.harrystyles.drystreaktracker.encounter.tracking.RecentDrop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.game.ItemManager;
import okhttp3.MultipartBody;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class DiscordWebhookService {
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final DryStreakTrackerConfig config;
    private final ItemManager itemManager;

    @Inject
    public DiscordWebhookService(OkHttpClient okHttpClient, Gson gson, DryStreakTrackerConfig config, ItemManager itemManager) {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.config = config;
        this.itemManager = itemManager;
    }

    public boolean hasValidWebhook() {
        String webhookUrl = config.discordWebhookUrl();

        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return false;
        }

        try {
            URI uri = URI.create(webhookUrl.trim());

            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }

            if (!"discord.com".equalsIgnoreCase(uri.getHost())) {
                return false;
            }

            String path = uri.getPath();

            return path != null && path.startsWith("/api/webhooks/") && path.length() > "/api/webhooks/".length();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean canAutomaticallyUpload(RecentDrop drop, boolean pet) {
        if (drop == null || !hasValidWebhook()) {
            return false;
        }

        if (pet && config.discordAlwaysUploadPets()) {
            return true;
        }

        return drop.getGeValue() >= config.discordMinimumGeValue();
    }

    public void uploadDrop(RecentDrop drop, String itemName) {
        if (drop == null || !hasValidWebhook()) {
            return;
        }

        String webhookUrl = config.discordWebhookUrl().trim();

        byte[] itemSprite = createItemSpritePng(drop.getItemId());

        DiscordWebhookPayload payload = createPayload(drop, itemName, itemSprite != null);

        String json = gson.toJson(payload);

        RequestBody body;

        if (itemSprite != null) {
            RequestBody itemSpriteBody = RequestBody.create(MediaType.get("image/png"), itemSprite);

            body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("payload_json", json)
                    .addFormDataPart("files[0]", "item.png", itemSpriteBody)
                    .build();
        } else {
            body = RequestBody.create(JSON, json);
        }

        sendWebhookRequest(webhookUrl, body);
    }

    public void uploadDrop(RecentDrop drop, String itemName, byte[] screenshot) {
        if (drop == null || !hasValidWebhook() || screenshot == null || screenshot.length == 0) {
            return;
        }

        String webhookUrl = config.discordWebhookUrl().trim();

        byte[] itemSprite = createItemSpritePng(drop.getItemId());

        DiscordWebhookPayload payload = createPayload(drop, itemName, itemSprite != null);

        String json = gson.toJson(payload);

        RequestBody screenshotBody = RequestBody.create(MediaType.get("image/png"), screenshot);

        MultipartBody.Builder bodyBuilder =
                new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("payload_json", json)
                        .addFormDataPart("files[0]", "drop.png", screenshotBody);

        if (itemSprite != null) {
            RequestBody itemSpriteBody = RequestBody.create(MediaType.get("image/png"), itemSprite);

            bodyBuilder.addFormDataPart("files[1]", "item.png", itemSpriteBody);
        }

        RequestBody body = bodyBuilder.build();

        sendWebhookRequest(webhookUrl, body);
    }

    private DiscordWebhookPayload createPayload(RecentDrop drop, String itemName, boolean includeItemThumbnail) {

        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.US);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy h:mm a");

        String kcValue = numberFormat.format(drop.getDropKillcount()) + " KC";

        if (drop.getTotalKillcount() > 0) {
            kcValue += " (" + numberFormat.format(drop.getTotalKillcount()) + " Total KC)";
        }

        String playerName = drop.getPlayerName();

        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "Unknown";
        }

        DiscordWebhookPayload payload = new DiscordWebhookPayload();

        payload.setUsername("Dry Streak Tracker");

        String description = playerName + " received a drop: " + itemName;

        String geValue = drop.getGeValue() > 0 ? numberFormat.format(drop.getGeValue()) + " gp" : "N/A";

        DiscordEmbed embed =
                new DiscordEmbed(
                        itemName,
                        description,
                        0xFF981F,
                        new DiscordField[]{
                                new DiscordField("Unique took", kcValue, false),
                                new DiscordField("G.E. Value", geValue, true),
                                new DiscordField("Quantity", String.valueOf(drop.getQuantity()), true),
                                new DiscordField("Received on", dateFormat.format(new Date(drop.getAcquiredAt())), false)
                        }
                );

        if (includeItemThumbnail) {
            embed.setThumbnail(new DiscordThumbnail("attachment://item.png"));
        }

        payload.setEmbeds(new DiscordEmbed[]{
                embed
        });

        return payload;
    }

    private byte[] createItemSpritePng(int itemId) {
        Image image = itemManager.getImage(itemId);

        if (image == null) {
            return null;
        }

        int width = image.getWidth(null);
        int height = image.getHeight(null);

        if (width <= 0 || height <= 0) {
            return null;
        }

        try {
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            Graphics2D graphics = bufferedImage.createGraphics();

            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            ImageIO.write(bufferedImage, "png", outputStream);

            return outputStream.toByteArray();
        } catch (IOException exception) {
            log.warn("Unable to create Discord item sprite: {}", exception.getMessage());

            return null;
        }
    }

    private void sendWebhookRequest(String webhookUrl, RequestBody body) {

        Request request;

        try {
            request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build();
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid Discord webhook URL");

            return;
        }

        okHttpClient
                .newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(Call call, IOException exception) {
                                log.warn("Unable to upload drop to Discord: {}", exception.getMessage());
                            }

                            @Override
                            public void onResponse(Call call, Response response) {
                                try {
                                    if (!response.isSuccessful()) {
                                        log.warn("Discord webhook returned status {}", response.code());

                                        return;
                                    }

                                    log.debug("Drop uploaded to Discord");
                                } finally {
                                    response.close();
                                }
                            }
                        }
                );
    }
}
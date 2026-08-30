package com.harrystyles.drystreaktracker.discord;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.ui.DrawManager;

@Slf4j
@Singleton
public class DropScreenshotService {
    private final DrawManager drawManager;

    private final ScheduledExecutorService executor;

    @Inject
    public DropScreenshotService(DrawManager drawManager, ScheduledExecutorService executor) {
        this.drawManager = drawManager;
        this.executor = executor;
    }

    public void captureScreenshot(Consumer<byte[]> callback) {
        if (callback == null) {
            return;
        }

        drawManager.requestNextFrameListener(image ->
                executor.submit(() -> {
                    byte[] screenshot = convertToPng(image);

                    if (screenshot != null) {
                        callback.accept(screenshot);
                    }
                })
        );
    }

    private byte[] convertToPng(Image image) {
        if (image == null) {
            return null;
        }

        try {
            BufferedImage bufferedImage = new BufferedImage(
                    image.getWidth(null),
                    image.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB
            );

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
            log.warn(
                    "Unable to create Discord drop screenshot: {}",
                    exception.getMessage()
            );

            return null;
        }
    }
}
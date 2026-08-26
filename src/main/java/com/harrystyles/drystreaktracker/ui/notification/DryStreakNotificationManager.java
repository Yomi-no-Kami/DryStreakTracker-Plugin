package com.harrystyles.drystreaktracker.ui.notification;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;

import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.WidgetUtil;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/**
 * Handles RuneLite's internal notification interface.
 *
 * @author Harry Styles
 */
@Slf4j
@Singleton
public class DryStreakNotificationManager {
    private static final int NOTIFICATION_DISPLAY_SCRIPT_ID = 3343;

    private static final int NOTIFICATION_WIDGET_INTERFACE_ID = 660;

    private static final int NOTIFICATION_WIDGET_CHILD_ID = 1;

    private static final int NOTIFICATION_COMPONENT_ID = WidgetUtil.packComponentId(303, 2);

    private final Queue<DryStreakNotification> pendingNotifications = new ConcurrentLinkedQueue<>();

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    private boolean started;

    public void start() {
        if (started) {
            return;
        }

        log.info("DryStreakNotificationManager starting");

        eventBus.register(this);

        started = true;
    }

    public void stop() {
        if (!started) {
            return;
        }

        clearNotifications();

        eventBus.unregister(this);

        started = false;

        log.info("DryStreakNotificationManager stopped");
    }

    public void notify(String title, String text) {
        notify(title, text, -1);
    }

    public void notify(String title, String text, int color) {
        pendingNotifications.offer(new DryStreakNotification(title, text, color));
    }

    public void clearNotifications() {
        pendingNotifications.clear();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        processNextNotification();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (isLoggedOut(event.getGameState())) {
            clearNotifications();
        }
    }

    private void processNextNotification() {
        if (isNotificationCurrentlyVisible()) {
            return;
        }

        DryStreakNotification notification = pendingNotifications.poll();

        if (notification == null) {
            return;
        }

        displayNotification(notification);
    }

    private boolean isNotificationCurrentlyVisible() {
        return client.getWidget(NOTIFICATION_WIDGET_INTERFACE_ID, NOTIFICATION_WIDGET_CHILD_ID) != null;
    }

    private boolean isLoggedOut(GameState gameState) {
        switch (gameState) {
            case HOPPING:
            case LOGGING_IN:
            case LOGIN_SCREEN:
            case LOGIN_SCREEN_AUTHENTICATOR:
            case CONNECTION_LOST:
                return true;

            default:
                return false;
        }
    }

    private void displayNotification(DryStreakNotification notification) {
        try {
            WidgetNode notificationNode = client.openInterface(NOTIFICATION_COMPONENT_ID, NOTIFICATION_WIDGET_INTERFACE_ID, WidgetModalMode.MODAL_CLICKTHROUGH);

            Widget notificationWidget = client.getWidget(NOTIFICATION_WIDGET_INTERFACE_ID, NOTIFICATION_WIDGET_CHILD_ID);

            if (notificationWidget == null) {
                log.warn("Notification widget was null");

                return;
            }

            client.runScript(NOTIFICATION_DISPLAY_SCRIPT_ID, notification.getTitle(), notification.getText(), notification.getColor());

            scheduleNotificationCleanup(notificationNode, notificationWidget);
        } catch (Exception e) {
            log.error("Failed to display notification", e);
        }
    }

    private void scheduleNotificationCleanup(WidgetNode notificationNode, Widget notificationWidget) {
        clientThread.invokeLater(() ->
                {
                    if (notificationWidget == null) {
                        return true;
                    }

                    if (notificationWidget.getWidth() > 0) {
                        return false;
                    }

                    try {
                        client.closeInterface(notificationNode, true);
                    } catch (Exception e) {
                        log.debug("Failed to close notification interface", e);
                    }

                    return true;
                }
        );
    }

    public boolean hasPendingNotifications() {
        return !pendingNotifications.isEmpty();
    }
}
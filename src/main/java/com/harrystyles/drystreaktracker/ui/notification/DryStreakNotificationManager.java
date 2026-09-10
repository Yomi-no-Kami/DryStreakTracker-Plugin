package com.harrystyles.drystreaktracker.ui.notification;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.harrystyles.drystreaktracker.DryStreakTrackerConfig;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;

import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.WidgetUtil;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/**
 * @author Harry Styles
 */
@Slf4j
@Singleton
public class DryStreakNotificationManager {
    private static final int NOTIFICATION_DISPLAY_SCRIPT_ID = 3343;

    private static final int NOTIFICATION_WIDGET_INTERFACE_ID = 660;

    private static final int NOTIFICATION_WIDGET_CHILD_ID = 1;

    private static final int NOTIFICATION_COMPONENT_ID = WidgetUtil.packComponentId(303, 2);

    /*
     * Number of game ticks to wait after another notification
     * disappears before Dry Streak Tracker opens its own.
     *
     * This prevents the game's collection-log notification
     * lifecycle from overlapping with our notification script.
     */
    private static final int NOTIFICATION_SETTLE_TICKS = 2;

    private final Queue<DryStreakNotification> pendingNotifications = new ConcurrentLinkedQueue<>();

    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Inject
    private DryStreakTrackerConfig config;

    private boolean started;

    /*
     * Interface currently owned by Dry Streak Tracker.
     *
     * These are only populated after this manager successfully
     * opens interface 660 itself.
     */
    private WidgetNode activeNotificationNode;

    private Widget activeNotificationWidget;

    /*
     * Last tick where interface 660 was observed being used by
     * something other than our active notification.
     */
    private int lastExternalNotificationTick = Integer.MIN_VALUE;

    /*
     * Last tick where one of our notifications finished.
     *
     * Waiting briefly after closing prevents one notification
     * lifecycle from overlapping the next one.
     */
    private int lastNotificationClosedTick = Integer.MIN_VALUE;

    public void start() {
        if (started) {
            return;
        }

        log.info("DryStreakNotificationManager starting");

        clearNotifications();
        clearActiveNotificationState();

        lastExternalNotificationTick = Integer.MIN_VALUE;
        lastNotificationClosedTick = Integer.MIN_VALUE;

        eventBus.register(this);

        started = true;
    }

    public void stop() {
        if (!started) {
            return;
        }

        clearNotifications();
        closeOwnedNotification();

        eventBus.unregister(this);

        started = false;

        log.info("DryStreakNotificationManager stopped");
    }

    public void notify(String title, String text) {
        notify(title, text, -1);
    }

    public void notify(String title, String text, int color) {
        if (!config.showNotifications()) {
            return;
        }

        if (title == null || title.trim().isEmpty()) {
            log.warn("Ignoring Dry Streak notification with empty title");

            return;
        }

        if (text == null || text.trim().isEmpty()) {
            log.warn("Ignoring Dry Streak notification with empty text");

            return;
        }

        pendingNotifications.offer(new DryStreakNotification(title, text, color));

        log.debug("Queued Dry Streak notification: title={} pending={}", title, pendingNotifications.size());
    }

    public void clearNotifications() {
        pendingNotifications.clear();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        processNotificationState();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event == null) {
            return;
        }

        if (isLoggedOut(event.getGameState())) {
            clearNotifications();
            clearActiveNotificationState();

            lastExternalNotificationTick = Integer.MIN_VALUE;
            lastNotificationClosedTick = Integer.MIN_VALUE;
        }
    }

    private void processNotificationState() {
        if (!started) {
            return;
        }

        if (!config.showNotifications()) {
            clearNotifications();
            closeOwnedNotification();

            return;
        }

        int currentTick = client.getTickCount();

        Widget currentNotificationWidget = getNotificationWidget();

        /*
         * We currently own a notification.
         */
        if (activeNotificationNode != null) {
            processOwnedNotification(currentNotificationWidget, currentTick);

            return;
        }

        /*
         * Interface 660 already exists and we did not open it.
         *
         * Treat it as a game/external notification and wait.
         */
        if (currentNotificationWidget != null) {
            lastExternalNotificationTick = currentTick;

            log.debug(
                    "Waiting for existing game notification: tick={} width={}",
                    currentTick,
                    currentNotificationWidget.getWidth()
            );

            return;
        }

        /*
         * Even though the widget has disappeared, give the game's
         * notification scripts a short period to completely finish.
         *
         * Collection-log notifications are the main reason for this
         * delay.
         */
        if (!hasNotificationInterfaceSettled(currentTick)) {
            return;
        }

        DryStreakNotification notification = pendingNotifications.poll();

        if (notification == null) {
            return;
        }

        displayNotification(notification);
    }

    private void processOwnedNotification(Widget currentNotificationWidget, int currentTick) {
        /*
         * Our widget is still the currently loaded notification.
         */
        if (currentNotificationWidget == activeNotificationWidget) {
            /*
             * Width above zero means the notification is still
             * being displayed or animated.
             */
            if (currentNotificationWidget.getWidth() > 0) {
                return;
            }

            log.debug("Dry Streak notification finished at tick {}", currentTick);

            closeOwnedNotification();

            lastNotificationClosedTick = currentTick;

            return;
        }

        /*
         * The interface disappeared or was replaced before our
         * normal cleanup completed.
         *
         * Do not close anything here because interface 660 may now
         * belong to the game.
         */
        log.debug(
                "Dry Streak notification interface was replaced or removed at tick {}",
                currentTick
        );

        clearActiveNotificationState();

        lastNotificationClosedTick = currentTick;

        /*
         * If another notification already appeared, remember that
         * the shared interface is externally occupied.
         */
        if (currentNotificationWidget != null) {
            lastExternalNotificationTick = currentTick;
        }
    }

    private boolean hasNotificationInterfaceSettled(int currentTick) {
        if (lastExternalNotificationTick != Integer.MIN_VALUE
                && currentTick - lastExternalNotificationTick <= NOTIFICATION_SETTLE_TICKS) {
            return false;
        }

        if (lastNotificationClosedTick != Integer.MIN_VALUE
                && currentTick - lastNotificationClosedTick <= NOTIFICATION_SETTLE_TICKS) {
            return false;
        }

        return true;
    }

    private Widget getNotificationWidget() {
        return client.getWidget(
                NOTIFICATION_WIDGET_INTERFACE_ID,
                NOTIFICATION_WIDGET_CHILD_ID
        );
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
        if (notification == null) {
            return;
        }

        try {
            /*
             * Never open another notification if the shared
             * interface became occupied between game ticks.
             */
            Widget existingNotificationWidget = getNotificationWidget();

            if (existingNotificationWidget != null) {
                lastExternalNotificationTick = client.getTickCount();

                pendingNotifications.offer(notification);

                log.debug(
                        "Notification interface became busy before display. Re-queued title={}",
                        notification.getTitle()
                );

                return;
            }

            WidgetNode notificationNode = client.openInterface(
                    NOTIFICATION_COMPONENT_ID,
                    NOTIFICATION_WIDGET_INTERFACE_ID,
                    WidgetModalMode.MODAL_CLICKTHROUGH
            );

            if (notificationNode == null) {
                log.warn(
                        "Could not open Dry Streak notification interface for title={}",
                        notification.getTitle()
                );

                pendingNotifications.offer(notification);

                return;
            }

            Widget notificationWidget = getNotificationWidget();

            if (notificationWidget == null) {
                log.warn(
                        "Dry Streak notification widget was null after opening interface for title={}",
                        notification.getTitle()
                );

                try {
                    client.closeInterface(notificationNode, true);
                } catch (Exception e) {
                    log.debug("Failed to close incomplete notification interface", e);
                }

                return;
            }

            /*
             * Claim ownership before running the script so subsequent
             * ticks know this interface belongs to us.
             */
            activeNotificationNode = notificationNode;
            activeNotificationWidget = notificationWidget;

            log.debug(
                    "Displaying Dry Streak notification: title={} text={} color={} tick={}",
                    notification.getTitle(),
                    notification.getText(),
                    notification.getColor(),
                    client.getTickCount()
            );

            client.runScript(
                    NOTIFICATION_DISPLAY_SCRIPT_ID,
                    notification.getTitle(),
                    notification.getText(),
                    notification.getColor()
            );
        } catch (Exception e) {
            log.error(
                    "Failed to display Dry Streak notification: title={}",
                    notification.getTitle(),
                    e
            );

            closeOwnedNotification();
        }
    }

    private void closeOwnedNotification() {
        WidgetNode notificationNode = activeNotificationNode;

        /*
         * Release our ownership first.
         *
         * If closing the interface causes another game notification
         * to be created, later code will not mistake it for ours.
         */
        clearActiveNotificationState();

        if (notificationNode == null) {
            return;
        }

        try {
            client.closeInterface(notificationNode, true);
        } catch (Exception e) {
            log.debug("Failed to close Dry Streak notification interface", e);
        }
    }

    private void clearActiveNotificationState() {
        activeNotificationNode = null;
        activeNotificationWidget = null;
    }

    public boolean hasPendingNotifications() {
        return !pendingNotifications.isEmpty();
    }
}
package com.harrystyles.drystreaktracker.ui;

import com.harrystyles.drystreaktracker.discord.DiscordWebhookService;
import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterRegistry;
import com.harrystyles.drystreaktracker.encounter.EncounterStats;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;

import com.harrystyles.drystreaktracker.encounter.tracking.RecentDrop;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * Main Dry Streak Tracker sidebar.
 * <p>
 * The tracker is only displayed while a RuneScape account
 * is logged in.
 */
@Slf4j
@Singleton
public class DryStreakSidebarPanel extends PluginPanel {
    private final EncounterRegistry encounterRegistry;
    private final EncounterTrackerManager trackerManager;
    private final ItemManager itemManager;
    private final DiscordWebhookService discordWebhookService;

    private final JPanel encounterContainer;

    private final JPanel tabContentPanel;

    private final JPanel recentDropsContainer;

    private final Map<String, Boolean> expandedStates = new HashMap<>();

    private final Map<Integer, ItemDisplayData> resolvedItemDisplayData = new HashMap<>();

    private boolean recentDropsTabActive;

    /**
     * Whether a RuneScape account is currently logged in.
     */
    private boolean loggedIn;

    @Inject
    public DryStreakSidebarPanel(EncounterRegistry encounterRegistry, EncounterTrackerManager trackerManager, ItemManager itemManager, DiscordWebhookService discordWebhookService) {
        super();

        this.encounterRegistry = encounterRegistry;
        this.trackerManager = trackerManager;
        this.itemManager = itemManager;
        this.discordWebhookService = discordWebhookService;

        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Dry Streak Tracker");

        title.setForeground(Color.WHITE);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));


        title.setHorizontalAlignment(SwingConstants.CENTER);

        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton clearAllButton = new JButton("Clear All Tracker Data");

        /**
         * Discord Button
         */
        BufferedImage discordImage = ImageUtil.loadImageResource(
                getClass(),
                "/discord-icon.png"
        );

        Image discordScaledImage = discordImage.getScaledInstance(
                14,
                14,
                Image.SCALE_SMOOTH
        );

        JButton discordButton = new JButton(
                "Our Discord",
                new ImageIcon(discordScaledImage)
        );

        discordButton.setFocusPainted(false);
        discordButton.setBorderPainted(false);
        discordButton.setOpaque(true);

        discordButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        discordButton.setBackground(ColorScheme.DARK_GRAY_COLOR);

        discordButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        discordButton.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        discordButton.setIconTextGap(6);

        discordButton.addChangeListener(event ->
        {
            ButtonModel model = discordButton.getModel();

            if (model.isPressed()) {
                discordButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            } else if (model.isRollover()) {
                discordButton.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            } else {
                discordButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        });

        discordButton.addActionListener(event ->
                LinkBrowser.browse("https://discord.gg/xyWgaHDmnh")
        );

        clearAllButton.setFocusPainted(false);
        clearAllButton.setBorderPainted(false);
        clearAllButton.setOpaque(true);

        clearAllButton.setForeground(new Color(220, 90, 90));
        clearAllButton.setBackground(ColorScheme.DARK_GRAY_COLOR);

        clearAllButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        clearAllButton.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));

        clearAllButton.setToolTipText("Clear all Dry Streak Tracker data for this account");

        clearAllButton.addChangeListener(event ->
        {
            ButtonModel model = clearAllButton.getModel();

            if (model.isPressed()) {
                clearAllButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            } else if (model.isRollover()) {
                clearAllButton.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            } else {
                clearAllButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        });

        clearAllButton.addActionListener(event ->
        {
            if (!trackerManager.isActive()) {
                return;
            }

            String message;
            String dialogTitle;

            if (recentDropsTabActive) {
                message =
                        "Clear ALL recent drop history for this account?\n\n" + "This cannot be undone.";

                dialogTitle = "Clear All Recent Drops Data";
            } else {
                message = "Clear ALL tracker data for this account?\n\n" + "This cannot be undone.";

                dialogTitle = "Clear All Tracker Data";
            }

            int result = JOptionPane.showConfirmDialog(
                    this, message, dialogTitle, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (result != JOptionPane.YES_OPTION) {
                return;
            }

            if (recentDropsTabActive) {
                trackerManager.clearRecentDrops();
            } else {
                trackerManager.clearTrackerData();

                expandedStates.clear();

                synchronized (resolvedItemDisplayData) {
                    resolvedItemDisplayData.clear();
                }
            }

            refresh();
        });

        JPanel headerPanel = new JPanel();

        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        headerPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));

        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        discordButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        clearAllButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(title);

        headerPanel.add(Box.createVerticalStrut(6));

        headerPanel.add(discordButton);

        headerPanel.add(Box.createVerticalStrut(4));

        headerPanel.add(clearAllButton);

        encounterContainer = new JPanel();

        encounterContainer.setLayout(new BoxLayout(encounterContainer, BoxLayout.Y_AXIS));

        encounterContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        encounterContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        encounterContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        encounterContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel trackerTab = new JPanel();

        trackerTab.setLayout(new BoxLayout(trackerTab, BoxLayout.Y_AXIS));

        trackerTab.setBackground(ColorScheme.DARK_GRAY_COLOR);

        trackerTab.setAlignmentX(Component.LEFT_ALIGNMENT);

        trackerTab.add(encounterContainer);


        /**
        * Recent Drops tab
        */
        recentDropsContainer = new JPanel();

        recentDropsContainer.setLayout(new BoxLayout(recentDropsContainer, BoxLayout.Y_AXIS));

        recentDropsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        recentDropsContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));


        /**
         * Tab buttons.
         */
        JPanel tabButtonPanel = new JPanel(new GridLayout(1, 2, 0, 0));

        tabButtonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel trackerTabHeader = new JPanel(new BorderLayout());

        JPanel recentDropsTabHeader = new JPanel(new BorderLayout());

        trackerTabHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);

        recentDropsTabHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton trackerTabButton = new JButton("Tracker");

        JButton recentDropsTabButton = new JButton("Recent Drops");

        trackerTabButton.setFocusPainted(false);
        trackerTabButton.setBorderPainted(false);
        trackerTabButton.setOpaque(true);

        recentDropsTabButton.setFocusPainted(false);
        recentDropsTabButton.setBorderPainted(false);
        recentDropsTabButton.setOpaque(true);

        trackerTabButton.setForeground(Color.WHITE);
        trackerTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        recentDropsTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        recentDropsTabButton.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel trackerUnderline = new JPanel();

        trackerUnderline.setPreferredSize(new Dimension(1, 2));

        trackerUnderline.setBackground(ColorScheme.BRAND_ORANGE);


        JPanel recentDropsUnderline = new JPanel();

        recentDropsUnderline.setPreferredSize(new Dimension(1, 2));

        recentDropsUnderline.setBackground(ColorScheme.DARK_GRAY_COLOR);


        trackerTabHeader.add(trackerTabButton, BorderLayout.CENTER);

        trackerTabHeader.add(trackerUnderline, BorderLayout.SOUTH);

        recentDropsTabHeader.add(recentDropsTabButton, BorderLayout.CENTER);

        recentDropsTabHeader.add(recentDropsUnderline, BorderLayout.SOUTH);

        tabButtonPanel.add(trackerTabHeader);

        tabButtonPanel.add(recentDropsTabHeader);

        /**
        * Tab content.
        */
        CardLayout tabLayout = new CardLayout();

        tabContentPanel = new JPanel(tabLayout);

        tabContentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        tabContentPanel.add(trackerTab, "tracker");

        tabContentPanel.add(recentDropsContainer, "recentDrops");


        trackerTabButton.addActionListener(event ->
        {
            recentDropsTabActive = false;

            tabLayout.show(tabContentPanel, "tracker");

            trackerTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            trackerTabButton.setForeground(Color.WHITE);

            recentDropsTabButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            recentDropsTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

            trackerUnderline.setBackground(ColorScheme.BRAND_ORANGE);

            recentDropsUnderline.setBackground(ColorScheme.DARK_GRAY_COLOR);

            clearAllButton.setText("Clear All Tracker Data");

            clearAllButton.setToolTipText(
                    "Clear all Dry Streak Tracker encounter data for this account"
            );
        });

        recentDropsTabButton.addActionListener(event ->
        {
            recentDropsTabActive = true;

            tabLayout.show(tabContentPanel, "recentDrops");

            recentDropsTabButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            recentDropsTabButton.setForeground(Color.WHITE);

            trackerTabButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
            trackerTabButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

            recentDropsUnderline.setBackground(ColorScheme.BRAND_ORANGE);

            trackerUnderline.setBackground(ColorScheme.DARK_GRAY_COLOR);

            clearAllButton.setText("Clear All Recent Drops Data");

            clearAllButton.setToolTipText(
                    "Clear all recent drop history for this account"
            );
        });


        /**
        * Main sidebar layout
        */
        JPanel mainContentPanel = new JPanel(new BorderLayout());

        mainContentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        mainContentPanel.add(tabButtonPanel, BorderLayout.NORTH);

        mainContentPanel.add(tabContentPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        add(mainContentPanel, BorderLayout.CENTER);

        /**
         * Plugin starts logged out.
         */
        loggedIn = false;

        refresh();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        if (!visible) {
            return;
        }

        refresh();
    }

    /**
     * Sets whether the tracker should currently be visible.
     * <p>
     * When false, encounter panels are completely hidden
     * and the user sees the login message instead.
     */
    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;

        log.debug("Sidebar login state changed: {}", loggedIn);

        refresh();
    }

    /**
     * Returns whether the sidebar considers the player
     * logged in.
     */
    public boolean isLoggedIn() {
        return loggedIn;
    }

    /**
     * Rebuilds the sidebar.
     */
    public void refresh() {
        final Map<Integer, ItemDisplayData> displayData;

        synchronized (resolvedItemDisplayData) {
            displayData = new HashMap<>(resolvedItemDisplayData);
        }

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshOnSwingThread(displayData));

            return;
        }

        refreshOnSwingThread(displayData);
    }

    /**
     * Resolves item data on the RuneLite client thread.
     */
    public void refreshItemDisplayData() {

        if (!trackerManager.isActive()) {
            log.debug("Skipping item display resolution: tracker is not active");

            return;
        }

        Map<Integer, ItemDisplayData> newlyResolved = new HashMap<>();

        for (EncounterDefinition encounter : encounterRegistry.getAll()) {
            EncounterStats stats = trackerManager.getStats(encounter.getEncounterId());

            if (stats == null) {
                continue;
            }

            Map<Integer, Integer> receivedDrops = stats.getReceivedDrops();

            if (receivedDrops == null || receivedDrops.isEmpty()) {
                continue;
            }

            for (Integer itemId : receivedDrops.keySet()) {
                if (itemId == null) {
                    continue;
                }

                synchronized (resolvedItemDisplayData) {
                    if (resolvedItemDisplayData.containsKey(itemId)) {
                        continue;
                    }
                }

                ItemDisplayData data = resolveItemDisplayData(itemId);

                if (data != null) {
                    newlyResolved.put(itemId, data);
                }
            }
        }

        if (!newlyResolved.isEmpty()) {
            synchronized (resolvedItemDisplayData) {
                resolvedItemDisplayData.putAll(newlyResolved);
            }

            log.debug("Resolved {} item display entries", newlyResolved.size());
        }

        refresh();
    }

    /**
     * Updates the sidebar with externally resolved item data.
     */
    public void updateItemDisplayData(Map<Integer, ItemDisplayData> itemDisplayData) {
        /**
         * Do not update the UI while logged out.
         */
        if (!loggedIn || !trackerManager.isActive()) {
            return;
        }

        if (itemDisplayData != null && !itemDisplayData.isEmpty()) {
            synchronized (resolvedItemDisplayData) {
                resolvedItemDisplayData.putAll(itemDisplayData);
            }

            log.debug("Updated sidebar with {} item display entries", itemDisplayData.size());
        }

        refresh();
    }

    /**
     * Performs the actual Swing UI rebuild.
     */
    private void refreshOnSwingThread(Map<Integer, ItemDisplayData> displayData) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshOnSwingThread(displayData));

            return;
        }

        encounterContainer.removeAll();

        recentDropsContainer.removeAll();

        /**
         * LOGGED OUT
         */
        if (!loggedIn || !trackerManager.isActive()) {
            JLabel loginLabel = new JLabel(
                    "Log in to view tracker!"
            );

            loginLabel.setHorizontalAlignment(SwingConstants.CENTER);

            loginLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

            loginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            loginLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            encounterContainer.add(loginLabel);

            JLabel recentDropsLoginLabel = new JLabel(
                    "Log in to view recent drops!"
            );

            recentDropsLoginLabel.setHorizontalAlignment(SwingConstants.CENTER);

            recentDropsLoginLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

            recentDropsLoginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            recentDropsLoginLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            recentDropsContainer.add(recentDropsLoginLabel);

            encounterContainer.revalidate();
            encounterContainer.repaint();

            recentDropsContainer.revalidate();
            recentDropsContainer.repaint();

            revalidate();
            repaint();

            return;
        }

        /**
         * LOGGED IN
         */

        int panelCount = 0;

        /**
         * Sort tracked encounters by most recent activity.
         *
         * The encounter killed most recently appears at
         * the top of the sidebar.
         */
        List<EncounterDefinition> sortedEncounters = new ArrayList<>(encounterRegistry.getAll());

        sortedEncounters.sort(Comparator.comparingLong((EncounterDefinition encounter) ->
                        {
                            EncounterStats stats = trackerManager.getStats(encounter.getEncounterId());

                            return stats != null ? stats.getLastActivityTime() : 0L;
                        }
                ).reversed()
        );

        for (EncounterDefinition encounter : sortedEncounters) {
            EncounterStats stats = trackerManager.getStats(encounter.getEncounterId());

            if (stats == null || stats.getTotalKillsTracked() <= 0) {
                continue;
            }

            boolean expanded = expandedStates.getOrDefault(encounter.getEncounterId(), false);

            EncounterPanel encounterPanel =
                    new EncounterPanel(encounter, stats, displayData, itemManager, expanded, isExpanded -> expandedStates.put(encounter.getEncounterId(), isExpanded), () ->
                    {
                        trackerManager.clearEncounterData(encounter.getEncounterId());

                        expandedStates.remove(encounter.getEncounterId());

                        refresh();
                    }
                    );

            encounterContainer.add(encounterPanel);

            JPanel spacer = new JPanel();

            spacer.setOpaque(false);

            spacer.setPreferredSize(new Dimension(1, 5));

            spacer.setMinimumSize(new Dimension(1, 5));

            spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));

            encounterContainer.add(spacer);

            panelCount++;
        }

        if (panelCount == 0) {
            JLabel emptyLabel = new JLabel("<html><center>" + "No encounters tracked yet." + "</center></html>"
            );

            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);

            emptyLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            encounterContainer.add(emptyLabel);
        }

        List<RecentDrop> recentDrops = trackerManager.getRecentDrops();

        if (recentDrops.isEmpty()) {
            JLabel noRecentDropsLabel = new JLabel(
                    "No recent drops yet."
            );

            noRecentDropsLabel.setHorizontalAlignment(SwingConstants.CENTER);

            noRecentDropsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

            noRecentDropsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            noRecentDropsLabel.setBorder(
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
            );

            recentDropsContainer.add(noRecentDropsLabel);
        } else {
            for (RecentDrop recentDrop : recentDrops) {
                ItemDisplayData itemData =
                        displayData.get(recentDrop.getItemId());

                RecentDropPanel recentDropPanel =
                        new RecentDropPanel(
                                recentDrop,
                                itemManager,
                                itemData,
                                discordWebhookService::hasValidWebhook,
                                () -> {
                                    String itemName =
                                            itemData != null
                                                    ? itemData.getName()
                                                    : "Item " + recentDrop.getItemId();

                                    discordWebhookService.uploadDrop(
                                            recentDrop,
                                            itemName
                                    );
                                },
                                () -> {
                                    trackerManager.removeRecentDrop(recentDrop);
                                    refresh();
                                }
                        );

                recentDropsContainer.add(recentDropPanel);

                recentDropsContainer.add(
                        Box.createVerticalStrut(5)
                );
            }
        }

        encounterContainer.revalidate();
        encounterContainer.repaint();

        recentDropsContainer.revalidate();
        recentDropsContainer.repaint();

        revalidate();
        repaint();
    }
    /**
     * Resolves an item's display name and sprite.
     * <p>
     * Must be called from the RuneLite client thread.
     */
    private ItemDisplayData resolveItemDisplayData(int itemId) {
        try {
            ItemComposition composition = itemManager.getItemComposition(
                    itemId
            );

            if (composition == null) {
                return null;
            }

            String itemName = composition.getName();

            if (itemName == null || itemName.isEmpty()) {
                return null;
            }

            Image itemImage = itemManager.getImage(itemId);

            /**
             * Keep the item name even if the sprite is not
             * available yet
             *
             * DropItemPanel will request the sprite asynchronously
             * when itemImage is null
             */
            return new ItemDisplayData(itemName, itemImage);
        } catch (Exception e) {
            log.debug("Unable to resolve item display data for {}", itemId, e);

            return null;
        }
    }

    public JPanel getEncounterContainer() {
        return encounterContainer;
    }
}
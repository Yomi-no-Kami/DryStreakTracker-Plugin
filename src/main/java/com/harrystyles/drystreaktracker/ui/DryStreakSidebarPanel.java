package com.harrystyles.drystreaktracker.ui;

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

    private final JPanel encounterContainer;

    private final Map<String, Boolean> expandedStates = new HashMap<>();

    private final Map<Integer, ItemDisplayData> resolvedItemDisplayData = new HashMap<>();

    /**
     * Whether a RuneScape account is currently logged in.
     */
    private boolean loggedIn;

    @Inject
    public DryStreakSidebarPanel(EncounterRegistry encounterRegistry, EncounterTrackerManager trackerManager, ItemManager itemManager) {
        super();

        this.encounterRegistry = encounterRegistry;
        this.trackerManager = trackerManager;
        this.itemManager = itemManager;

        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Dry Streak Tracker");

        title.setForeground(Color.WHITE);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));


        title.setHorizontalAlignment(SwingConstants.CENTER);

        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton clearAllButton = new JButton("Clear All Data");

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

        clearAllButton.addActionListener(
                event ->
                {
                    if (!trackerManager.isActive()) {
                        return;
                    }

                    int result = JOptionPane.showConfirmDialog(
                            this,
                            "Clear ALL Dry Streak Tracker data for this account?\n\n"
                                    + "This cannot be undone.",
                            "Clear All Tracker Data",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                    if (result != JOptionPane.YES_OPTION) {
                        return;
                    }

                    trackerManager.clearAllData();

                    expandedStates.clear();

                    synchronized (resolvedItemDisplayData) {
                        resolvedItemDisplayData.clear();
                    }

                    refresh();
                }
        );

        JPanel headerPanel = new JPanel();

        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        headerPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));

        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerPanel.getPreferredSize().height));

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

        JPanel layoutPanel = new JPanel();

        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));

        layoutPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        layoutPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        layoutPanel.add(headerPanel);

        layoutPanel.add(Box.createVerticalStrut(5));

        layoutPanel.add(encounterContainer);

        add(layoutPanel, BorderLayout.NORTH);

        /*
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

        /**
         * LOGGED OUT
         */
        if (!loggedIn || !trackerManager.isActive()) {
            JLabel loginLabel = new JLabel("<html><center>" + "Log in to view tracker!" + "</center></html>");

            loginLabel.setHorizontalAlignment(SwingConstants.CENTER);

            loginLabel.setVerticalAlignment(SwingConstants.CENTER);

            loginLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

            loginLabel.setBorder(BorderFactory.createEmptyBorder(25, 10, 25, 10));

            encounterContainer.add(loginLabel);

            encounterContainer.revalidate();
            encounterContainer.repaint();

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

        encounterContainer.revalidate();
        encounterContainer.repaint();

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
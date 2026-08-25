package com.harrystyles.drystreaktracker.ui;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterRegistry;
import com.harrystyles.drystreaktracker.encounter.EncounterStats;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Main Dry Streak Tracker sidebar.
 *
 * The tracker is only displayed while a RuneScape account
 * is logged in.
 */
@Slf4j
@Singleton
public class DryStreakSidebarPanel extends PluginPanel
{
    private final EncounterRegistry encounterRegistry;
    private final EncounterTrackerManager trackerManager;
    private final ItemManager itemManager;

    private final JPanel encounterContainer;

    private final Map<String, Boolean> expandedStates =
            new HashMap<>();

    private final Map<Integer, ItemDisplayData> resolvedItemDisplayData =
            new HashMap<>();

    /**
     * Whether a RuneScape account is currently logged in.
     */
    private boolean loggedIn;

    @Inject
    public DryStreakSidebarPanel(
            EncounterRegistry encounterRegistry,
            EncounterTrackerManager trackerManager,
            ItemManager itemManager)
    {
        super();

        this.encounterRegistry =
                encounterRegistry;

        this.trackerManager =
                trackerManager;

        this.itemManager =
                itemManager;

        setLayout(
                new BorderLayout()
        );

        setBackground(
                ColorScheme.DARK_GRAY_COLOR
        );

        setMinimumSize(
                new Dimension(
                        150,
                        100
                )
        );

        JLabel title =
                new JLabel(
                        "Dry Streak Tracker"
                );

        title.setForeground(
                Color.WHITE
        );

        title.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        title.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        10,
                        10,
                        10
                )
        );

        add(
                title,
                BorderLayout.NORTH
        );

        encounterContainer =
                new JPanel();

        encounterContainer.setLayout(
                new BoxLayout(
                        encounterContainer,
                        BoxLayout.Y_AXIS
                )
        );

        encounterContainer.setBackground(
                ColorScheme.DARK_GRAY_COLOR
        );

        encounterContainer.setBorder(
                BorderFactory.createEmptyBorder(
                        5,
                        5,
                        5,
                        5
                )
        );

        JPanel layoutPanel =
                new JPanel();

        layoutPanel.setLayout(
                new BoxLayout(
                        layoutPanel,
                        BoxLayout.Y_AXIS
                )
        );

        layoutPanel.setBackground(
                ColorScheme.DARK_GRAY_COLOR
        );

        layoutPanel.add(
                encounterContainer
        );

        add(
                layoutPanel,
                BorderLayout.NORTH
        );

        /*
         * Plugin starts logged out.
         */
        loggedIn = false;

        refresh();
    }

    @Override
    public void setVisible(
            boolean visible)
    {
        super.setVisible(
                visible
        );

        if (!visible)
        {
            return;
        }

        refresh();
    }

    /**
     * Sets whether the tracker should currently be visible.
     *
     * When false, encounter panels are completely hidden
     * and the user sees the login message instead.
     */
    public void setLoggedIn(
            boolean loggedIn)
    {
        this.loggedIn =
                loggedIn;

        log.debug(
                "Sidebar login state changed: {}",
                loggedIn
        );

        refresh();
    }

    /**
     * Returns whether the sidebar considers the player
     * logged in.
     */
    public boolean isLoggedIn()
    {
        return loggedIn;
    }

    /**
     * Rebuilds the sidebar.
     */
    public void refresh()
    {
        final Map<Integer, ItemDisplayData> displayData;

        synchronized (resolvedItemDisplayData)
        {
            displayData =
                    new HashMap<>(
                            resolvedItemDisplayData
                    );
        }

        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(
                    () ->
                            refreshOnSwingThread(
                                    displayData
                            )
            );

            return;
        }

        refreshOnSwingThread(
                displayData
        );
    }

    /**
     * Resolves item data on the RuneLite client thread.
     */
    public void refreshItemDisplayData()
    {
        /*
         * Never resolve item data while logged out.
         */
        if (!loggedIn
                || !trackerManager.isActive())
        {
            log.debug(
                    "Skipping item display resolution: player is not logged in"
            );

            return;
        }

        Map<Integer, ItemDisplayData> newlyResolved =
                new HashMap<>();

        for (EncounterDefinition encounter
                : encounterRegistry.getAll())
        {
            EncounterStats stats =
                    trackerManager.getStats(
                            encounter.getEncounterId()
                    );

            if (stats == null)
            {
                continue;
            }

            Map<Integer, Integer> receivedDrops =
                    stats.getReceivedDrops();

            if (receivedDrops == null
                    || receivedDrops.isEmpty())
            {
                continue;
            }

            for (Integer itemId
                    : receivedDrops.keySet())
            {
                if (itemId == null)
                {
                    continue;
                }

                synchronized (resolvedItemDisplayData)
                {
                    if (resolvedItemDisplayData.containsKey(
                            itemId))
                    {
                        continue;
                    }
                }

                ItemDisplayData data =
                        resolveItemDisplayData(
                                itemId
                        );

                if (data != null)
                {
                    newlyResolved.put(
                            itemId,
                            data
                    );
                }
            }
        }

        if (!newlyResolved.isEmpty())
        {
            synchronized (resolvedItemDisplayData)
            {
                resolvedItemDisplayData.putAll(
                        newlyResolved
                );
            }

            log.debug(
                    "Resolved {} item display entries",
                    newlyResolved.size()
            );
        }

        refresh();
    }

    /**
     * Updates the sidebar with externally resolved item data.
     */
    public void updateItemDisplayData(
            Map<Integer, ItemDisplayData> itemDisplayData)
    {
        /*
         * Do not update the UI while logged out.
         */
        if (!loggedIn
                || !trackerManager.isActive())
        {
            return;
        }

        if (itemDisplayData != null
                && !itemDisplayData.isEmpty())
        {
            synchronized (resolvedItemDisplayData)
            {
                resolvedItemDisplayData.putAll(
                        itemDisplayData
                );
            }

            log.debug(
                    "Updated sidebar with {} item display entries",
                    itemDisplayData.size()
            );
        }

        refresh();
    }

    /**
     * Performs the actual Swing UI rebuild.
     */
    private void refreshOnSwingThread(
            Map<Integer, ItemDisplayData> displayData)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(
                    () ->
                            refreshOnSwingThread(
                                    displayData
                            )
            );

            return;
        }

        encounterContainer.removeAll();

        /*
         * ---------------------------------------------------------
         * LOGGED OUT
         * ---------------------------------------------------------
         */
        if (!loggedIn
                || !trackerManager.isActive())
        {
            JLabel loginLabel =
                    new JLabel(
                            "<html><center>"
                                    + "Log in to view tracker!"
                                    + "</center></html>"
                    );

            loginLabel.setHorizontalAlignment(
                    SwingConstants.CENTER
            );

            loginLabel.setVerticalAlignment(
                    SwingConstants.CENTER
            );

            loginLabel.setForeground(
                    ColorScheme.LIGHT_GRAY_COLOR
            );

            loginLabel.setBorder(
                    BorderFactory.createEmptyBorder(
                            25,
                            10,
                            25,
                            10
                    )
            );

            encounterContainer.add(
                    loginLabel
            );

            encounterContainer.revalidate();
            encounterContainer.repaint();

            revalidate();
            repaint();

            return;
        }

        /*
         * ---------------------------------------------------------
         * LOGGED IN
         * ---------------------------------------------------------
         */

        int panelCount = 0;

        for (EncounterDefinition encounter
                : encounterRegistry.getAll())
        {
            EncounterStats stats =
                    trackerManager.getStats(
                            encounter.getEncounterId()
                    );

            if (stats == null
                    || stats.getTotalKillsTracked() <= 0)
            {
                continue;
            }

            boolean expanded =
                    expandedStates.getOrDefault(
                            encounter.getEncounterId(),
                            false
                    );

            EncounterPanel encounterPanel =
                    new EncounterPanel(
                            encounter,
                            stats,
                            displayData,
                            expanded,
                            isExpanded ->
                                    expandedStates.put(
                                            encounter.getEncounterId(),
                                            isExpanded
                                    )
                    );

            encounterContainer.add(
                    encounterPanel
            );

            JPanel spacer =
                    new JPanel();

            spacer.setOpaque(
                    false
            );

            spacer.setPreferredSize(
                    new Dimension(
                            1,
                            5
                    )
            );

            spacer.setMinimumSize(
                    new Dimension(
                            1,
                            5
                    )
            );

            spacer.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            5
                    )
            );

            encounterContainer.add(
                    spacer
            );

            panelCount++;
        }

        if (panelCount == 0)
        {
            JLabel emptyLabel =
                    new JLabel(
                            "<html><center>"
                                    + "No encounters tracked yet."
                                    + "</center></html>"
                    );

            emptyLabel.setHorizontalAlignment(
                    SwingConstants.CENTER
            );

            emptyLabel.setBorder(
                    BorderFactory.createEmptyBorder(
                            10,
                            10,
                            10,
                            10
                    )
            );

            encounterContainer.add(
                    emptyLabel
            );
        }

        encounterContainer.revalidate();
        encounterContainer.repaint();

        revalidate();
        repaint();
    }

    /**
     * Resolves an item's display name and sprite.
     *
     * Must be called from the RuneLite client thread.
     */
    private ItemDisplayData resolveItemDisplayData(
            int itemId)
    {
        try
        {
            net.runelite.api.ItemComposition composition =
                    itemManager.getItemComposition(
                            itemId
                    );

            if (composition == null)
            {
                return null;
            }

            String itemName =
                    composition.getName();

            if (itemName == null
                    || itemName.isEmpty())
            {
                return null;
            }

            Image itemImage =
                    itemManager.getImage(
                            itemId
                    );

            if (itemImage == null)
            {
                return null;
            }

            return new ItemDisplayData(
                    itemName,
                    itemImage
            );
        }
        catch (Exception e)
        {
            log.debug(
                    "Unable to resolve item display data for {}",
                    itemId,
                    e
            );

            return null;
        }
    }

    public JPanel getEncounterContainer()
    {
        return encounterContainer;
    }
}
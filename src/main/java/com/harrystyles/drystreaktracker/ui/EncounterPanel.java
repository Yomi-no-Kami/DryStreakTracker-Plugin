package com.harrystyles.drystreaktracker.ui;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterStats;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Map;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

public class EncounterPanel extends JPanel {
    private final EncounterDefinition encounter;
    private final EncounterStats stats;
    private final Map<Integer, ItemDisplayData> itemDisplayData;
    private final ItemManager itemManager;

    private final JPanel detailsPanel;

    private final Consumer<Boolean> expandedStateListener;

    private JLabel expandIndicator;
    private boolean expanded;

    public EncounterPanel(
            EncounterDefinition encounter,
            EncounterStats stats,
            Map<Integer, ItemDisplayData> itemDisplayData,
            ItemManager itemManager,
            boolean expanded,
            Consumer<Boolean> expandedStateListener) {
        this.encounter = encounter;
        this.stats = stats;
        this.itemDisplayData = itemDisplayData;
        this.itemManager = itemManager;
        this.expanded = expanded;
        this.expandedStateListener = expandedStateListener;

        setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        setLayout(
                new BorderLayout()
        );

        setOpaque(
                true
        );

        setBackground(
                ColorScheme.DARKER_GRAY_COLOR
        );

        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                ColorScheme.MEDIUM_GRAY_COLOR
                        ),
                        BorderFactory.createEmptyBorder(
                                8,
                                8,
                                8,
                                8
                        )
                )
        );

        JPanel summary =
                createSummaryPanel();

        summary.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        summary.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(
                            MouseEvent event) {
                        toggleExpanded();
                    }
                }
        );

        add(
                summary,
                BorderLayout.NORTH
        );

        detailsPanel =
                createDetailsPanel();

        detailsPanel.setVisible(
                expanded
        );

        add(
                detailsPanel,
                BorderLayout.CENTER
        );

    }

    private JPanel createSummaryPanel() {
        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                10,
                                0
                        )
                );

        panel.setOpaque(
                false
        );

        JLabel imageLabel =
                createEncounterImageLabel();

        panel.add(
                imageLabel,
                BorderLayout.WEST
        );

        JPanel informationPanel =
                new JPanel(
                        new GridLayout(
                                0,
                                1,
                                0,
                                4
                        )
                );

        informationPanel.setOpaque(
                false
        );

        JLabel nameLabel =
                new JLabel(
                        encounter.getDisplayName()
                );

        nameLabel.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );

        nameLabel.setFont(
                nameLabel.getFont().deriveFont(
                        15f
                )
        );

        informationPanel.add(
                nameLabel
        );

        JPanel statsPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                0,
                                0
                        )
                );

        statsPanel.setOpaque(
                false
        );

        JLabel kcLabel =
                new JLabel(
                        "KC: "
                                + stats.getTotalKillsTracked()
                );

        kcLabel.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );

        JLabel dryLabel =
                new JLabel(
                        "Dry: "
                                + stats.getCurrentDryStreak()
                );

        dryLabel.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );

        statsPanel.add(
                kcLabel
        );

        statsPanel.add(
                new JLabel(
                        "    "
                )
        );

        statsPanel.add(
                dryLabel
        );

        informationPanel.add(
                statsPanel
        );

        panel.add(
                informationPanel,
                BorderLayout.CENTER
        );

        expandIndicator =
                new JLabel(expanded ? "▲" : "▼");

        expandIndicator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        expandIndicator.setHorizontalAlignment(SwingConstants.CENTER);

        expandIndicator.setVerticalAlignment(SwingConstants.TOP);

        expandIndicator.setPreferredSize(new Dimension(18, 18));

        expandIndicator.setToolTipText(
                expanded ? "Collapse encounter" : "Expand encounter");

        panel.add(expandIndicator, BorderLayout.EAST);

        return panel;
    }

    private JLabel createEncounterImageLabel() {
        JLabel imageLabel =
                new JLabel();

        imageLabel.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        imageLabel.setVerticalAlignment(
                SwingConstants.CENTER
        );

        imageLabel.setPreferredSize(
                new Dimension(
                        64,
                        64
                )
        );

        imageLabel.setMinimumSize(
                new Dimension(
                        64,
                        64
                )
        );

        imageLabel.setMaximumSize(
                new Dimension(
                        64,
                        64
                )
        );

        String imageUrl =
                encounter.getImageUrl();

        if (imageUrl == null
                || imageUrl.trim().isEmpty()) {
            imageLabel.setText(
                    "?"
            );

            return imageLabel;
        }

        Thread imageThread =
                new Thread(
                        () ->
                        {
                            try {
                                URL url =
                                        new URL(
                                                imageUrl
                                        );

                                Image image =
                                        ImageIO.read(
                                                url
                                        );

                                if (image == null) {
                                    SwingUtilities.invokeLater(
                                            () ->
                                                    imageLabel.setText(
                                                            "?"
                                                    )
                                    );

                                    return;
                                }

                                Image scaled =
                                        image.getScaledInstance(
                                                64,
                                                64,
                                                Image.SCALE_SMOOTH
                                        );

                                SwingUtilities.invokeLater(
                                        () ->
                                        {
                                            imageLabel.setIcon(
                                                    new ImageIcon(
                                                            scaled
                                                    )
                                            );

                                            imageLabel.setText(
                                                    ""
                                            );

                                            imageLabel.revalidate();
                                            imageLabel.repaint();

                                            revalidate();
                                            repaint();
                                        }
                                );
                            } catch (Exception e) {
                                SwingUtilities.invokeLater(
                                        () ->
                                                imageLabel.setText(
                                                        "?"
                                                )
                                );
                            }
                        },
                        "DryStreak-EncounterImage"
                );

        imageThread.setDaemon(
                true
        );

        imageThread.start();

        return imageLabel;
    }

    private JPanel createDetailsPanel() {
        JPanel panel =
                new JPanel(
                        new BorderLayout()
                );

        panel.setOpaque(
                false
        );

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        0,
                        0,
                        0
                )
        );

        JPanel statisticsPanel =
                new JPanel();

        statisticsPanel.setLayout(
                new BoxLayout(
                        statisticsPanel,
                        BoxLayout.Y_AXIS
                )
        );

        statisticsPanel.setOpaque(
                false
        );

        statisticsPanel.add(
                new JLabel(
                        "Total Kills: "
                                + stats.getTotalKillsTracked()
                )
        );

        statisticsPanel.add(
                new JLabel(
                        "Current Dry Streak: "
                                + stats.getCurrentDryStreak()
                )
        );

        statisticsPanel.add(
                new JLabel(
                        "Longest Dry Streak: "
                                + stats.getLongestDryStreak()
                )
        );

        statisticsPanel.add(
                new JLabel(
                        "Tracked Drops: "
                                + stats.getTotalTrackedDrops()
                )
        );

        String lastDropText =
                "Last Drop KC: "
                        + stats.getLastDropKillcount();

        if (stats.getLastDropTotalKillcount() > 0) {
            lastDropText +=
                    " ("
                            + stats.getLastDropTotalKillcount()
                            + ")";
        }

        statisticsPanel.add(
                new JLabel(
                        lastDropText
                )
        );

        panel.add(
                statisticsPanel,
                BorderLayout.NORTH
        );

        panel.add(
                createDropsPanel(),
                BorderLayout.CENTER
        );

        return panel;
    }

    private JPanel createDropsPanel() {
        JPanel panel =
                new JPanel();

        panel.setLayout(
                new BoxLayout(
                        panel,
                        BoxLayout.Y_AXIS
                )
        );

        panel.setOpaque(
                false
        );

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        10,
                        0,
                        0,
                        0
                )
        );

        JLabel title =
                new JLabel(
                        "Tracked Drops"
                );

        title.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR
        );

        title.setBorder(
                BorderFactory.createEmptyBorder(
                        0,
                        0,
                        6,
                        0
                )
        );

        panel.add(
                title
        );

        Map<Integer, Integer> receivedDrops =
                stats.getReceivedDrops();

        if (receivedDrops == null
                || receivedDrops.isEmpty()) {
            panel.add(
                    new JLabel(
                            "No tracked drops received yet."
                    )
            );

            return panel;
        }

        JPanel itemsPanel =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                4
                        )
                );

        itemsPanel.setOpaque(
                false
        );

        itemsPanel.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        for (Map.Entry<Integer, Integer> entry
                : receivedDrops.entrySet()) {
            int itemId =
                    entry.getKey();

            int quantity =
                    entry.getValue();

            ItemDisplayData data =
                    itemDisplayData.get(
                            itemId
                    );

            String itemName =
                    data != null
                            ? data.getName()
                            : "Item " + itemId;

            Image itemImage =
                    data != null
                            ? data.getImage()
                            : null;

            DropItemPanel dropItemPanel =
                    new DropItemPanel(
                            itemManager,
                            itemId,
                            quantity,
                            itemName,
                            itemImage
                    );

            itemsPanel.add(
                    dropItemPanel
            );
        }

        panel.add(
                itemsPanel
        );

        return panel;
    }

    private void toggleExpanded() {
        expanded = !expanded;

        detailsPanel.setVisible(
                expanded
        );

        if (expandIndicator != null)
        {
            expandIndicator.setText(
                    expanded
                            ? "▲"
                            : "▼"
            );

            expandIndicator.setToolTipText(
                    expanded
                            ? "Collapse encounter"
                            : "Expand encounter"
            );
        }

        if (expandedStateListener != null) {
            expandedStateListener.accept(
                    expanded
            );
        }

        revalidate();
        repaint();
    }

}
package com.harrystyles.drystreaktracker.ui;

import com.harrystyles.drystreaktracker.encounter.tracking.RecentDrop;

import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import javax.swing.*;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

public class RecentDropPanel extends JPanel {

    private static final int PANEL_HEIGHT = 140;

    public RecentDropPanel(RecentDrop drop, ItemManager itemManager, ItemDisplayData itemDisplayData, BooleanSupplier discordAvailableSupplier, Runnable uploadDiscordListener, Runnable clearRecentDropListener) {

        setLayout(new BorderLayout());

        setOpaque(true);

        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));

        setAlignmentX(Component.LEFT_ALIGNMENT);

        setPreferredSize(new Dimension(0, PANEL_HEIGHT));

        setMinimumSize(new Dimension(0, PANEL_HEIGHT));

        setMaximumSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));


        /*
         * Item information.
         */
        String itemName = itemDisplayData != null ? itemDisplayData.getName() : "Item " + drop.getItemId();

        Image itemImage = itemDisplayData != null ? itemDisplayData.getImage() : null;


        /*
         * Header.
         */
        JPanel headerPanel = new JPanel(new BorderLayout());

        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        headerPanel.setBorder(
                BorderFactory.createEmptyBorder(7, 9, 7, 9));

        JLabel itemNameLabel = new JLabel(itemName);

        itemNameLabel.setForeground(Color.WHITE);

        itemNameLabel.setFont(itemNameLabel.getFont().deriveFont(Font.BOLD, 13f));

        itemNameLabel.setToolTipText(itemName);

        headerPanel.add(itemNameLabel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);


        /*
         * Main information area.
         */
        JPanel bodyPanel = new JPanel(new BorderLayout(10, 0));

        bodyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        bodyPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR), BorderFactory.createEmptyBorder(9, 9, 9, 9)));


        /*
         * Inventory-style drop slot.
         */
        DropItemPanel dropItemPanel = new DropItemPanel(itemManager, drop.getItemId(), drop.getQuantity(), itemName, itemImage);

        JPanel itemSlotContainer = new JPanel(new GridBagLayout());

        itemSlotContainer.setOpaque(false);

        itemSlotContainer.add(dropItemPanel);

        bodyPanel.add(itemSlotContainer, BorderLayout.WEST);


        /*
         * Statistics.
         */
        JPanel informationPanel = new JPanel();

        informationPanel.setLayout(new BoxLayout(informationPanel, BoxLayout.Y_AXIS));

        informationPanel.setOpaque(false);


        /*
         * Encounter.
         */
        JLabel encounterLabel = new JLabel(drop.getEncounterName());

        encounterLabel.setForeground(Color.WHITE);

        encounterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        informationPanel.add(encounterLabel);

        informationPanel.add(Box.createVerticalStrut(3));


        /*
         * Killcount.
         */
        String killcount = drop.getDropKillcount() + " KC";

        if (drop.getTotalKillcount() > 0) {
            killcount += " (" + drop.getTotalKillcount() + ")";
        }

        JLabel killcountLabel = createInformationLabel("Unique took", killcount, "#ffffff");

        if (drop.getTotalKillcount() > 0) {
            killcountLabel.setToolTipText(
                    drop.getDropKillcount()
                            + " KC to receive this unique. "
                            + drop.getTotalKillcount()
                            + " was your total recorded KC when the drop was received."
            );
        }

        informationPanel.add(killcountLabel);

        informationPanel.add(Box.createVerticalStrut(3));


        /*
         * GE value.
         */
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.US);

        String geValue = drop.getGeValue() > 0 ? numberFormat.format(drop.getGeValue()) + " gp" : "N/A";

        JLabel valueLabel = createInformationLabel("GE Value", geValue, "#ff981f");

        informationPanel.add(valueLabel);

        informationPanel.add(Box.createVerticalStrut(5));


        /*
         * Date received.
         */
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy · h:mm a");

        JLabel dateLabel = new JLabel(dateFormat.format(new Date(drop.getAcquiredAt())));

        dateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        dateLabel.setFont(dateLabel.getFont().deriveFont(10f));

        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        informationPanel.add(dateLabel);


        bodyPanel.add(informationPanel, BorderLayout.CENTER);

        add(bodyPanel, BorderLayout.CENTER);

        addRightClickMenu(this, drop, discordAvailableSupplier, uploadDiscordListener, clearRecentDropListener);
    }

    private void addRightClickMenu(
            Component component,
            RecentDrop drop,
            BooleanSupplier discordAvailableSupplier,
            Runnable uploadDiscordListener,
            Runnable clearRecentDropListener) {

        component.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                showRightClickMenu(
                        event,
                        drop,
                        discordAvailableSupplier,
                        uploadDiscordListener,
                        clearRecentDropListener
                );
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                showRightClickMenu(
                        event,
                        drop,
                        discordAvailableSupplier,
                        uploadDiscordListener,
                        clearRecentDropListener
                );
            }
        });

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                addRightClickMenu(
                        child,
                        drop,
                        discordAvailableSupplier,
                        uploadDiscordListener,
                        clearRecentDropListener
                );
            }
        }
    }

    private void showRightClickMenu(
            java.awt.event.MouseEvent event,
            RecentDrop drop,
            BooleanSupplier discordAvailableSupplier,
            Runnable uploadDiscordListener,
            Runnable clearRecentDropListener) {

        if (!event.isPopupTrigger()) {
            return;
        }

        JPopupMenu popupMenu = new JPopupMenu();

        boolean discordAvailable =
                discordAvailableSupplier != null
                        && discordAvailableSupplier.getAsBoolean();

        JMenuItem uploadItem =
                new JMenuItem(
                        discordAvailable
                                ? "Upload to Discord"
                                : "Upload to Discord (Not set up!)"
                );

        uploadItem.setEnabled(discordAvailable);

        uploadItem.addActionListener(actionEvent -> {
            if (uploadDiscordListener != null) {
                uploadDiscordListener.run();
            }
        });

        popupMenu.add(uploadItem);

        popupMenu.addSeparator();

        JMenuItem clearItem =
                new JMenuItem("Clear recent drop");

        clearItem.addActionListener(actionEvent -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Clear this recent drop from "
                            + drop.getEncounterName()
                            + "?",
                    "Clear Recent Drop",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result != JOptionPane.YES_OPTION) {
                return;
            }

            if (clearRecentDropListener != null) {
                clearRecentDropListener.run();
            }
        });

        popupMenu.add(clearItem);

        popupMenu.show(
                event.getComponent(),
                event.getX(),
                event.getY()
        );
    }


    private JLabel createInformationLabel(String title, String value, String valueColor) {

        JLabel label = new JLabel("<html>"
                + "<font color='#c8c8c8'>"
                + title
                + ": </font>"
                + "<font color='"
                + valueColor
                + "'>"
                + value
                + "</font>"
                + "</html>"
        );

        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        return label;
    }
}
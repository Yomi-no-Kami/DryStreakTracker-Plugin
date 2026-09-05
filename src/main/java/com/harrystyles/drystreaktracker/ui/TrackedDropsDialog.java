package com.harrystyles.drystreaktracker.ui;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterDropDefinition;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import javax.swing.*;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Allows the player to choose which supported drops should
 * reset the dry streak for one encounter.
 */
public class TrackedDropsDialog {
    private final Component parent;
    private final EncounterDefinition encounter;
    private final ItemManager itemManager;
    private final IntFunction<String> itemNameSupplier;
    private final IntPredicate enabledSupplier;
    private final BiConsumer<Integer, Boolean> changeListener;
    private final Runnable saveListener;
    private final Runnable resetListener;

    public TrackedDropsDialog(Component parent, EncounterDefinition encounter, ItemManager itemManager, IntFunction<String> itemNameSupplier, IntPredicate enabledSupplier, BiConsumer<Integer, Boolean> changeListener, Runnable saveListener, Runnable resetListener) {
        this.parent = parent;
        this.encounter = encounter;
        this.itemManager = itemManager;
        this.itemNameSupplier = itemNameSupplier;
        this.enabledSupplier = enabledSupplier;
        this.changeListener = changeListener;
        this.saveListener = saveListener;
        this.resetListener = resetListener;
    }

    public void show() {
        if (encounter == null || encounter.getTrackedDrops().isEmpty()) {
            JOptionPane.showMessageDialog(parent, "There are no configurable drops for this encounter.", "Tracked Drops", JOptionPane.INFORMATION_MESSAGE);

            return;
        }

        Map<Integer, JCheckBox> checkBoxes = new LinkedHashMap<>();

        JPanel dropsPanel = new JPanel();
        dropsPanel.setLayout(new BoxLayout(dropsPanel, BoxLayout.Y_AXIS));
        dropsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropsPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        for (EncounterDropDefinition drop : encounter.getTrackedDrops()) {
            if (drop == null) {
                continue;
            }

            int itemId = drop.getItemId();

            JPanel row = createDropRow(itemId, enabledSupplier.test(itemId), checkBoxes);

            dropsPanel.add(row);
            dropsPanel.add(Box.createVerticalStrut(3));
        }

        JScrollPane scrollPane = new JScrollPane(dropsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(340, Math.min(420, Math.max(120, encounter.getTrackedDrops().size() * 43))));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel messagePanel = new JPanel(new BorderLayout(0, 8));
        messagePanel.setOpaque(false);

        JLabel description = new JLabel("<html>Select which drops should end your dry streak.<br>Unchecked drops will be ignored.</html>");
        description.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        messagePanel.add(description, BorderLayout.NORTH);
        messagePanel.add(scrollPane, BorderLayout.CENTER);

        Object[] options = {
                "Save",
                "Reset to Defaults",
                "Cancel"
        };

        int result = JOptionPane.showOptionDialog(
                parent,
                messagePanel,
                "Tracked Drops - " + encounter.getDisplayName(),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );

        if (result == 0) {
            for (Map.Entry<Integer, JCheckBox> entry : checkBoxes.entrySet()) {
                changeListener.accept(entry.getKey(), entry.getValue().isSelected());
            }

            if (saveListener != null) {
                saveListener.run();
            }

            return;
        }

        if (result == 1) {
            int resetResult = JOptionPane.showConfirmDialog(
                    parent,
                    "Reset tracked drops for " + encounter.getDisplayName() + " to the plugin defaults?",
                    "Reset Tracked Drops",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (resetResult == JOptionPane.YES_OPTION && resetListener != null) {
                resetListener.run();
            }
        }
    }

    private JPanel createDropRow(int itemId, boolean selected, Map<Integer, JCheckBox> checkBoxes) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        iconLabel.setMinimumSize(new Dimension(32, 32));
        iconLabel.setMaximumSize(new Dimension(32, 32));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);

        AsyncBufferedImage image = itemManager.getImage(itemId);

        if (image != null) {
            image.addTo(iconLabel);
        }

        String itemName = itemNameSupplier.apply(itemId);

        if (itemName == null || itemName.trim().isEmpty()) {
            itemName = "Item " + itemId;
        }

        JCheckBox checkBox = new JCheckBox(itemName, selected);
        checkBox.setOpaque(false);
        checkBox.setForeground(Color.WHITE);

        checkBoxes.put(itemId, checkBox);

        row.add(iconLabel, BorderLayout.WEST);
        row.add(checkBox, BorderLayout.CENTER);

        return row;
    }
}
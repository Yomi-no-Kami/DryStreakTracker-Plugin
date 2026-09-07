package com.harrystyles.drystreaktracker.ui;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterDropDefinition;
import com.harrystyles.drystreaktracker.wiki.WikiDrop;
import com.harrystyles.drystreaktracker.wiki.WikiDropService;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Creates or edits one player-created NPC encounter.
 */
public class CustomNpcTrackerDialog {
    private final Component parent;
    private final String npcName;
    private final int npcId;
    private final EncounterDefinition existingEncounter;
    private final WikiDropService wikiDropService;
    private final ItemManager itemManager;
    private final Consumer<Set<Integer>> saveListener;
    private final Runnable deleteListener;

    private final List<WikiDrop> loadedDrops = new ArrayList<>();
    private final Set<Integer> selectedItemIds = new HashSet<>();

    private JPanel dropsPanel;
    private JTextField searchField;
    private JButton saveButton;
    private JLabel countLabel;

    public CustomNpcTrackerDialog(Component parent, String npcName, int npcId, EncounterDefinition existingEncounter, WikiDropService wikiDropService, ItemManager itemManager, Consumer<Set<Integer>> saveListener, Runnable deleteListener) {
        this.parent = parent;
        this.npcName = npcName;
        this.npcId = npcId;
        this.existingEncounter = existingEncounter;
        this.wikiDropService = wikiDropService;
        this.itemManager = itemManager;
        this.saveListener = saveListener;
        this.deleteListener = deleteListener;

        if (existingEncounter != null) {
            for (EncounterDropDefinition drop : existingEncounter.getTrackedDrops()) {
                if (drop != null && drop.getItemId() > 0) {
                    selectedItemIds.add(drop.getItemId());
                }
            }
        }
    }

    public void show() {
        JDialog dialog = createDialog();

        loadDrops(dialog);

        dialog.setVisible(true);
    }

    private JDialog createDialog() {
        Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;

        JDialog dialog;

        if (owner instanceof Frame) {
            dialog = new JDialog((Frame) owner, getTitle(), true);
        } else if (owner instanceof Dialog) {
            dialog = new JDialog((Dialog) owner, getTitle(), true);
        } else {
            dialog = new JDialog((Frame) null, getTitle(), true);
        }

        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 8));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

        JLabel npcLabel = new JLabel(npcName);
        npcLabel.setForeground(java.awt.Color.WHITE);
        npcLabel.setFont(npcLabel.getFont().deriveFont(Font.BOLD, 15f));
        npcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel npcIdLabel = new JLabel("NPC ID: " + npcId);
        npcIdLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        npcIdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descriptionLabel = new JLabel(
                "<html>Select the drops that should end your dry streak.<br>" +
                        "Leave all drops unchecked if you only want to track kills.<br>"
                        + "Drop information is loaded from the OSRS Wiki.</html>"
        );

        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField = new JTextField();
        searchField.setEnabled(false);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setToolTipText("Search this NPC's drop table");

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                rebuildDropsPanel();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                rebuildDropsPanel();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                rebuildDropsPanel();
            }
        });

        countLabel = new JLabel("Loading drop table...");
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        countLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        headerPanel.add(npcLabel);
        headerPanel.add(Box.createVerticalStrut(2));
        headerPanel.add(npcIdLabel);
        headerPanel.add(descriptionLabel);
        headerPanel.add(searchField);
        headerPanel.add(countLabel);

        dialog.add(headerPanel, BorderLayout.NORTH);

        dropsPanel = new JPanel();
        dropsPanel.setLayout(new BoxLayout(dropsPanel, BoxLayout.Y_AXIS));
        dropsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dropsPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JLabel loadingLabel = new JLabel("Loading drops from the OSRS Wiki...");
        loadingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loadingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        dropsPanel.add(loadingLabel);

        JScrollPane scrollPane = new JScrollPane(dropsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));

        if (existingEncounter != null) {
            JButton deleteButton = new JButton("Delete Tracker");

            deleteButton.addActionListener(event -> {
                int result = JOptionPane.showConfirmDialog(
                        dialog,
                        "Delete the custom tracker for " + npcName + "?\n\n"
                                + "Its saved tracker statistics will also be removed.",
                        "Delete Custom Tracker",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (result != JOptionPane.YES_OPTION) {
                    return;
                }

                if (deleteListener != null) {
                    deleteListener.run();
                }

                dialog.dispose();
            });

            buttonPanel.add(deleteButton, BorderLayout.WEST);
        }

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightButtons.setOpaque(false);

        JButton cancelButton = new JButton("Cancel");

        saveButton = new JButton(existingEncounter == null ? "Create Tracker" : "Save");
        saveButton.setEnabled(false);

        cancelButton.addActionListener(event -> dialog.dispose());

        saveButton.addActionListener(event -> {
            if (saveListener != null) {
                saveListener.accept(new HashSet<>(selectedItemIds));
            }

            dialog.dispose();
        });

        rightButtons.add(cancelButton);
        rightButtons.add(saveButton);

        buttonPanel.add(rightButtons, BorderLayout.EAST);

        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setPreferredSize(new Dimension(450, 580));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        return dialog;
    }

    private void loadDrops(JDialog dialog) {
        wikiDropService.getDrops(npcName, npcId).whenComplete((drops, error) -> SwingUtilities.invokeLater(() -> {
            if (!dialog.isDisplayable()) {
                return;
            }

            if (error != null) {
                showLoadFailure(error);

                return;
            }

            loadedDrops.clear();

            if (drops != null) {
                loadedDrops.addAll(drops);
            }

            searchField.setEnabled(!loadedDrops.isEmpty());
            saveButton.setEnabled(true);

            rebuildDropsPanel();
        }));
    }

    private void showLoadFailure(Throwable error) {
        error.printStackTrace();

        dropsPanel.removeAll();

        JLabel errorLabel = new JLabel(
                "<html>Could not load this NPC's drop table from the OSRS Wiki.<br>"
                        + "Please try again later.</html>"
        );

        errorLabel.setForeground(new java.awt.Color(220, 90, 90));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        dropsPanel.add(errorLabel);

        searchField.setEnabled(false);
        saveButton.setEnabled(false);

        countLabel.setText("Wiki lookup failed.");

        dropsPanel.revalidate();
        dropsPanel.repaint();
    }

    private void rebuildDropsPanel() {
        if (dropsPanel == null) {
            return;
        }

        dropsPanel.removeAll();

        if (loadedDrops.isEmpty()) {
            JLabel emptyLabel = new JLabel("No selectable drops were found for this NPC.");

            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            dropsPanel.add(emptyLabel);

            countLabel.setText("0 drops found");

            dropsPanel.revalidate();
            dropsPanel.repaint();

            return;
        }

        String search = searchField != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT)
                : "";

        int visibleDrops = 0;

        for (WikiDrop drop : loadedDrops) {
            if (drop == null || drop.getItemName() == null || drop.getItemId() <= 0) {
                continue;
            }

            if (!search.isEmpty() && !drop.getItemName().toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }

            dropsPanel.add(createDropRow(drop));
            dropsPanel.add(Box.createVerticalStrut(2));

            visibleDrops++;
        }

        if (visibleDrops == 0) {
            JLabel noMatchesLabel = new JLabel("No drops match your search.");

            noMatchesLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            noMatchesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            dropsPanel.add(noMatchesLabel);
        }

        countLabel.setText(
                visibleDrops
                        + (visibleDrops == 1 ? " drop shown" : " drops shown")
                        + " · "
                        + selectedItemIds.size()
                        + " selected"
        );

        dropsPanel.revalidate();
        dropsPanel.repaint();
    }

    private JPanel createDropRow(WikiDrop drop) {
        JPanel row = new JPanel(new BorderLayout(8, 0));

        row.setOpaque(true);
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel iconLabel = new JLabel();

        iconLabel.setPreferredSize(new Dimension(32, 32));
        iconLabel.setMinimumSize(new Dimension(32, 32));
        iconLabel.setMaximumSize(new Dimension(32, 32));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setToolTipText(drop.getItemName());

        AsyncBufferedImage image = itemManager.getImage(drop.getItemId());

        if (image != null) {
            image.addTo(iconLabel);
        }

        JCheckBox checkBox = new JCheckBox(drop.getItemName());

        checkBox.setOpaque(false);
        checkBox.setForeground(java.awt.Color.WHITE);
        checkBox.setSelected(selectedItemIds.contains(drop.getItemId()));
        checkBox.setToolTipText(drop.getItemName());

        checkBox.addActionListener(event -> {
            if (checkBox.isSelected()) {
                selectedItemIds.add(drop.getItemId());
            } else {
                selectedItemIds.remove(drop.getItemId());
            }

            updateCountLabel();
        });

        row.add(iconLabel, BorderLayout.WEST);
        row.add(checkBox, BorderLayout.CENTER);

        return row;
    }

    private void updateCountLabel() {
        if (countLabel == null) {
            return;
        }

        String search = searchField != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT)
                : "";

        int visibleDrops = 0;

        for (WikiDrop drop : loadedDrops) {
            if (drop == null || drop.getItemName() == null) {
                continue;
            }

            if (!search.isEmpty() && !drop.getItemName().toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }

            visibleDrops++;
        }

        countLabel.setText(
                visibleDrops
                        + (visibleDrops == 1 ? " drop shown" : " drops shown")
                        + " · "
                        + selectedItemIds.size()
                        + " selected"
        );
    }

    private String getTitle() {
        return existingEncounter == null
                ? "Add Custom NPC - " + npcName
                : "Configure Custom NPC - " + npcName;
    }
}
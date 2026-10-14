package com.harrystyles.drystreaktracker.ui;

import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;
import com.harrystyles.drystreaktracker.encounter.tracking.PlayerTrackingData;

import java.awt.*;
import javax.swing.*;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;

public class RestoreLegacyDataDialog extends JDialog {
    private final EncounterTrackerManager trackerManager;
    private final ClientThread clientThread;
    private final Runnable onRestored;

    private final JTextField usernameField = new JTextField(18);
    private final JTextArea previewArea = new JTextArea(7, 25);
    private final JButton findButton = new JButton("Find Old Data");
    private final JButton restoreButton = new JButton("Restore Data");

    private String selectedUsername;

    public RestoreLegacyDataDialog(Component parent, EncounterTrackerManager trackerManager, ClientThread clientThread, Runnable onRestored) {
        super(SwingUtilities.getWindowAncestor(parent), "Restore Old Username Data", ModalityType.MODELESS);

        this.trackerManager = trackerManager;
        this.clientThread = clientThread;
        this.onRestored = onRestored;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel description = new JLabel("<html>Changed your RuneScape username and lost all your data before the storage update on October 13th, 2026?<br>Enter your previous username to recover saved tracking data.</html>");
        description.setForeground(Color.WHITE);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(description);
        content.add(Box.createVerticalStrut(12));

        JLabel usernameLabel = new JLabel("Previous Username:");
        usernameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        usernameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(usernameLabel);
        content.add(Box.createVerticalStrut(4));

        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(usernameField);
        content.add(Box.createVerticalStrut(10));

        findButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        findButton.addActionListener(event -> findOldData());
        content.add(findButton);
        content.add(Box.createVerticalStrut(12));

        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        previewArea.setForeground(Color.WHITE);
        previewArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        previewArea.setText("Enter your previous username and click Find Old Data.");

        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(previewScroll);

        add(content, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dispose());

        restoreButton.setEnabled(false);
        restoreButton.addActionListener(event -> restoreOldData());

        buttons.add(cancelButton);
        buttons.add(restoreButton);

        add(buttons, BorderLayout.SOUTH);

        usernameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                invalidateSelection();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                invalidateSelection();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                invalidateSelection();
            }
        });

        pack();
        setMinimumSize(new Dimension(390, 330));
        setLocationRelativeTo(parent);
    }

    private void invalidateSelection() {
        selectedUsername = null;
        restoreButton.setEnabled(false);
    }

    private void findOldData() {
        String username = usernameField.getText().trim();

        invalidateSelection();

        if (username.isEmpty()) {
            previewArea.setText("Please enter your previous RuneScape username.");
            return;
        }

        if (!trackerManager.isActive()) {
            previewArea.setText("You must be logged in to restore tracking data.");
            return;
        }

        PlayerTrackingData data = trackerManager.previewLegacyData(username);

        if (data == null) {
            previewArea.setText("No valid saved tracking data was found for \"" + username + "\".\n\nCheck the spelling of your previous username.");
            return;
        }

        previewArea.setText(
                "Saved data found for: " + username + "\n\n"
                        + "Tracked encounters: " + data.getEncounters().size() + "\n"
                        + "Recent drops: " + data.getRecentDrops().size() + "\n"
                        + "Custom encounters: " + data.getCustomEncounters().size() + "\n"
                        + "Drop preferences: " + data.getDropPreferences().size() + "\n\n"
                        + "The original saved data will be preserved."
        );

        selectedUsername = username;
        restoreButton.setEnabled(true);
    }

    private void restoreOldData() {
        if (selectedUsername == null || !trackerManager.isActive()) {
            return;
        }

        String username = selectedUsername;
        String currentPlayer = trackerManager.getCurrentPlayerName();

        int confirmation = JOptionPane.showConfirmDialog(
                this,
                "Restore tracking data from \"" + username + "\" to \"" + currentPlayer + "\"?\n\n"
                        + "This replaces the current account's empty tracking data.\n"
                        + "If the current account already has progress, restoration will be refused.\n\n"
                        + "The original username save will remain untouched.",
                "Confirm Data Restoration",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }

        restoreButton.setEnabled(false);
        findButton.setEnabled(false);
        usernameField.setEnabled(false);

        clientThread.invokeLater(() -> {
            boolean success = trackerManager.isActive()
                    && currentPlayer.equals(trackerManager.getCurrentPlayerName())
                    && trackerManager.restoreLegacyData(username);

            SwingUtilities.invokeLater(() -> {
                if (!isDisplayable()) {
                    return;
                }

                if (success) {
                    dispose();

                    if (onRestored != null) {
                        onRestored.run();
                    }

                    JOptionPane.showMessageDialog(
                            getOwner(),
                            "Your previous tracking data has been restored successfully.",
                            "Restoration Complete",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } else {
                    previewArea.setText("Restoration was not completed.\n\n"
                            + "Your current account may already have tracking data, "
                            + "you may have switched accounts, or the saved data could not be written.\n\n"
                            + "No existing tracking data was intentionally overwritten.");

                    findButton.setEnabled(true);
                    usernameField.setEnabled(true);
                    invalidateSelection();
                }
            });
        });
    }
}
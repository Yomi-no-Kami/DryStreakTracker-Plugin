package com.harrystyles.drystreaktracker.ui;

import java.awt.*;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Displays a tracked drop as an item sprite.
 * <p>
 * The total quantity is displayed in the top-right
 * corner and the item name is shown as a tooltip
 * when hovering over the sprite.
 */
public class DropItemPanel extends JPanel {
    private static final int ICON_SIZE = 36;
    private static final int SLOT_SIZE = 40;

    public DropItemPanel(
            ItemManager itemManager,
            int itemId,
            int quantity,
            String itemName,
            Image itemImage) {
        setLayout(null);

        setOpaque(true);

        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));

        setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));

        setMinimumSize(new Dimension(SLOT_SIZE, SLOT_SIZE));

        setMaximumSize(new Dimension(SLOT_SIZE, SLOT_SIZE));

        setToolTipText(itemName);

        /**
         * Layered pane allows the quantity text to be
         * explicitly placed above the item sprite.
         */
        JLayeredPane layeredPane = new JLayeredPane();

        layeredPane.setLayout(null);

        layeredPane.setBounds(0, 0, SLOT_SIZE, SLOT_SIZE);

        layeredPane.setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));

        layeredPane.setToolTipText(itemName);

        /**
         * ITEM SPRITE
         */
        JLabel icon = new JLabel();

        icon.setBounds(2, 2, ICON_SIZE, ICON_SIZE);

        icon.setHorizontalAlignment(SwingConstants.CENTER);

        icon.setVerticalAlignment(SwingConstants.CENTER);

        icon.setToolTipText(itemName);

        if (itemImage != null) {
            /**
             * Cached image is already available.
             */
            Image scaledImage = itemImage.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);

            icon.setIcon(new ImageIcon(scaledImage));
        } else {
            /**
             * Cached image was not available yet.
             *
             * Ask RuneLite for the sprite asynchronously.
             * addTo(icon) will update the JLabel automatically
             * when the sprite finishes loading.
             */
            AsyncBufferedImage asyncImage = itemManager.getImage(itemId);

            if (asyncImage != null) {
                asyncImage.addTo(icon);
            } else {
                icon.setText("?");
            }
        }

        layeredPane.add(icon, JLayeredPane.DEFAULT_LAYER);

        /**
         * Quantity Overlay
         */
        if (quantity > 1) {
            String quantityText = "x" + quantity;

            /**
             * Quantity Shadow
             */
            JLabel quantityShadow = new JLabel(quantityText);

            quantityShadow.setHorizontalAlignment(SwingConstants.LEFT);

            quantityShadow.setVerticalAlignment(SwingConstants.TOP);

            quantityShadow.setForeground(Color.BLACK);

            quantityShadow.setFont(FontManager.getRunescapeSmallFont());

            /**
             * 1 px right and 1 px down from the quantity text
             */
            quantityShadow.setBounds(3, 2, 35, 14);

            quantityShadow.setToolTipText(itemName);

            /**
             * Quantity Text
             */
            JLabel quantityLabel = new JLabel(quantityText);

            quantityLabel.setHorizontalAlignment(SwingConstants.LEFT);

            quantityLabel.setVerticalAlignment(SwingConstants.TOP);

            quantityLabel.setForeground(Color.YELLOW);

            quantityLabel.setFont(FontManager.getRunescapeSmallFont());

            /**
             * Top left corner
             */
            quantityLabel.setBounds(2, 1, 35, 14);

            quantityLabel.setToolTipText(itemName);

            /**
             * Put the shadow behind the quantity
             */
            layeredPane.add(quantityShadow, JLayeredPane.PALETTE_LAYER);

            /**
             * Put the quantity above the shadow
             */
            layeredPane.add(quantityLabel, JLayeredPane.MODAL_LAYER);
        }

        add(layeredPane);
    }
}
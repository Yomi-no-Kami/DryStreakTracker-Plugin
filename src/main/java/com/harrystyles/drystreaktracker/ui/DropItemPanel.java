package com.harrystyles.drystreaktracker.ui;

import java.awt.Dimension;
import java.awt.Image;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.runelite.client.ui.ColorScheme;

/**
 * Displays a tracked drop as an item sprite.
 *
 * The total quantity is displayed in the top-right
 * corner and the item name is shown as a tooltip
 * when hovering over the sprite.
 */
public class DropItemPanel extends JPanel
{
    private static final int ICON_SIZE = 36;

    public DropItemPanel(
            int itemId,
            int quantity,
            String itemName,
            Image itemImage)
    {
        setLayout(null);

        setOpaque(false);

        setPreferredSize(
                new Dimension(
                        ICON_SIZE,
                        ICON_SIZE
                )
        );

        setMinimumSize(
                new Dimension(
                        ICON_SIZE,
                        ICON_SIZE
                )
        );

        setMaximumSize(
                new Dimension(
                        ICON_SIZE,
                        ICON_SIZE
                )
        );

        setToolTipText(
                itemName
        );

        /*
         * Layered pane allows the quantity text to be
         * explicitly placed ABOVE the item sprite.
         */
        JLayeredPane layeredPane =
                new JLayeredPane();

        layeredPane.setLayout(null);

        layeredPane.setBounds(
                0,
                0,
                ICON_SIZE,
                ICON_SIZE
        );

        layeredPane.setPreferredSize(
                new Dimension(
                        ICON_SIZE,
                        ICON_SIZE
                )
        );

        layeredPane.setToolTipText(
                itemName
        );

        /*
         * ---------------------------------------------------------
         * ITEM SPRITE
         * ---------------------------------------------------------
         */
        JLabel icon =
                new JLabel();

        icon.setBounds(
                0,
                0,
                ICON_SIZE,
                ICON_SIZE
        );

        icon.setHorizontalAlignment(
                SwingConstants.CENTER
        );

        icon.setVerticalAlignment(
                SwingConstants.CENTER
        );

        icon.setToolTipText(
                itemName
        );

        if (itemImage != null)
        {
            Image scaledImage =
                    itemImage.getScaledInstance(
                            ICON_SIZE,
                            ICON_SIZE,
                            Image.SCALE_SMOOTH
                    );

            icon.setIcon(
                    new ImageIcon(
                            scaledImage
                    )
            );
        }
        else
        {
            icon.setText(
                    "?"
            );

            icon.setHorizontalAlignment(
                    SwingConstants.CENTER
            );

            icon.setVerticalAlignment(
                    SwingConstants.CENTER
            );
        }

        layeredPane.add(
                icon,
                Integer.valueOf(
                        JLayeredPane.DEFAULT_LAYER
                )
        );

        /*
         * ---------------------------------------------------------
         * QUANTITY OVERLAY
         * ---------------------------------------------------------
         */
        if (quantity > 1)
        {
            JLabel quantityLabel =
                    new JLabel(
                            "x" + quantity
                    );

            quantityLabel.setHorizontalAlignment(
                    SwingConstants.RIGHT
            );

            quantityLabel.setVerticalAlignment(
                    SwingConstants.TOP
            );

            quantityLabel.setForeground(
                    ColorScheme.LIGHT_GRAY_COLOR
            );

            quantityLabel.setBorder(
                    BorderFactory.createEmptyBorder(
                            0,
                            1,
                            0,
                            1
                    )
            );

            /*
             * Top-right corner.
             */
            quantityLabel.setBounds(
                    12,
                    0,
                    24,
                    14
            );

            quantityLabel.setToolTipText(
                    itemName
            );

            /*
             * Put the quantity explicitly ABOVE the sprite.
             */
            layeredPane.add(
                    quantityLabel,
                    Integer.valueOf(
                            JLayeredPane.PALETTE_LAYER
                    )
            );
        }

        add(
                layeredPane
        );
    }
}
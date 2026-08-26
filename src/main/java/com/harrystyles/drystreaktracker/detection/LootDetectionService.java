package com.harrystyles.drystreaktracker.detection;

import com.harrystyles.drystreaktracker.DryStreakTrackerConfig;
import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterLootType;
import com.harrystyles.drystreaktracker.encounter.EncounterRegistry;
import com.harrystyles.drystreaktracker.encounter.EncounterStats;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;
import com.harrystyles.drystreaktracker.ui.DryStreakSidebarPanel;
import com.harrystyles.drystreaktracker.ui.ItemDisplayData;
import com.harrystyles.drystreaktracker.ui.notification.DryStreakNotificationManager;

import java.awt.Image;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;

import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;


@Slf4j
@Singleton
public class LootDetectionService
{
    private final Client client;

    private final EncounterRegistry encounterRegistry;

    private final EncounterTrackerManager trackerManager;

    private final DryStreakNotificationManager notificationManager;

    private final DryStreakTrackerConfig config;

    private final ItemManager itemManager;

    private final DryStreakSidebarPanel sidebarPanel;


    @Inject
    public LootDetectionService(
            Client client,
            EncounterRegistry encounterRegistry,
            EncounterTrackerManager trackerManager,
            DryStreakNotificationManager notificationManager,
            DryStreakTrackerConfig config,
            ItemManager itemManager,
            DryStreakSidebarPanel sidebarPanel)
    {
        this.client =
                client;

        this.encounterRegistry =
                encounterRegistry;

        this.trackerManager =
                trackerManager;

        this.notificationManager =
                notificationManager;

        this.config =
                config;

        this.itemManager =
                itemManager;

        this.sidebarPanel =
                sidebarPanel;
    }


    /**
     * Handles normal NPC death loot.
     *
     * Definition source:
     *
     * encounters.json
     *
     * Detection flow:
     *
     * NPC dies
     * -> NpcLootReceived
     * -> NPC ID
     * -> EncounterRegistry
     * -> encounter completion
     */
    public void handleNpcLootReceived(
            NpcLootReceived event)
    {
        if (!canProcess())
        {
            return;
        }

        if (event == null
                || event.getNpc() == null)
        {
            return;
        }

        NPC npc =
                event.getNpc();

        int npcId =
                npc.getId();

        Collection<ItemStack> items =
                event.getItems();

        log.debug(
                "NpcLootReceived: npc={} id={} items={}",
                npc.getName(),
                npcId,
                items == null
                        ? 0
                        : items.size()
        );

        EncounterDefinition encounter =
                encounterRegistry.getByNpcId(
                        npcId
                );

        if (encounter == null)
        {
            return;
        }

        /*
         * Extra safety check.
         *
         * Anything found by NPC ID should have been loaded
         * from encounters.json.
         */
        if (encounter.getLootType()
                != EncounterLootType.GROUND_LOOT)
        {
            log.debug(
                    "Ignoring NpcLootReceived for {} because lootType={}",
                    encounter.getEncounterId(),
                    encounter.getLootType()
            );

            return;
        }

        String eventKey =
                createNpcKillEventKey(
                        npc,
                        items
                );

        processEncounterLoot(
                encounter,
                items,
                eventKey
        );
    }


    /**
     * Handles RuneLite's generic LootReceived event.
     *
     * Definition source:
     *
     * loot-received-encounters.json
     *
     * This may include:
     *
     * - pickpocketing
     * - object/chest rewards
     * - reward containers
     * - raid/event loot
     * - other Loot Tracker sources
     */
    public void handleLootReceived(
            LootReceived event)
    {
        if (!canProcess())
        {
            return;
        }

        if (event == null)
        {
            return;
        }

        if (event.getItems() == null
                || event.getItems().isEmpty())
        {
            return;
        }

        String sourceName =
                event.getName();

        if (sourceName == null
                || sourceName
                .trim()
                .isEmpty())
        {
            log.debug(
                    "Ignoring LootReceived event with no source name"
            );

            return;
        }

        log.debug(
                "LootReceived: source={} type={} amount={} items={}",
                sourceName,
                event.getType(),
                event.getAmount(),
                event.getItems().size()
        );

        EncounterDefinition encounter =
                encounterRegistry
                        .getByLootSourceName(
                                sourceName
                        );

        if (encounter == null)
        {
            log.debug(
                    "No encounter registered for LootReceived source '{}'",
                    sourceName
            );

            return;
        }

        /*
         * Only encounters loaded from
         * loot-received-encounters.json are allowed here.
         */
        if (encounter.getLootType()
                != EncounterLootType.LOOT_RECEIVED)
        {
            log.debug(
                    "Ignoring LootReceived for {} because lootType={}",
                    encounter.getEncounterId(),
                    encounter.getLootType()
            );

            return;
        }

        String eventKey =
                createGenericLootEventKey(
                        event
                );

        processEncounterLoot(
                encounter,
                event.getItems(),
                eventKey
        );
    }


    private boolean canProcess()
    {
        return trackerManager.isActive()
                && client.getGameState()
                == GameState.LOGGED_IN;
    }


    /**
     * Converts one RuneLite loot event into one encounter
     * completion.
     *
     * Both NpcLootReceived and LootReceived eventually come
     * through this method.
     */
    private void processEncounterLoot(
            EncounterDefinition encounter,
            Collection<ItemStack> items,
            String eventKey)
    {
        if (encounter == null)
        {
            return;
        }

        ItemStack qualifyingDrop =
                findQualifyingDrop(
                        encounter,
                        items
                );

        Integer dropItemId =
                qualifyingDrop == null
                        ? null
                        : qualifyingDrop.getId();

        int dropQuantity =
                qualifyingDrop == null
                        ? 0
                        : qualifyingDrop.getQuantity();

        boolean recorded =
                trackerManager.recordKill(
                        encounter.getEncounterId(),
                        eventKey,
                        dropItemId,
                        dropQuantity
                );

        if (!recorded)
        {
            return;
        }

        EncounterStats stats =
                trackerManager.getStats(
                        encounter.getEncounterId()
                );

        if (stats != null)
        {
            Map<Integer, ItemDisplayData> displayData =
                    resolveEncounterItemDisplayData(
                            stats
                    );

            sidebarPanel.updateItemDisplayData(
                    displayData
            );
        }

        if (qualifyingDrop == null)
        {
            /*
             * If this kill surpassed the player's previous
             * longest dry streak, show the record notification.
             */
            if (stats != null
                    && stats.isNewDryRecordThisKill())
            {
                sendDryRecordNotification(
                        encounter,
                        stats
                );

                /*
                 * Chatbox messages still respect the plugin
                 * configuration setting.
                 */
                if (config.showChatboxMessages())
                {
                    sendDryRecordChatboxMessage(
                            encounter,
                            stats
                    );
                }

                return;
            }

            if (config.showChatboxMessages())
            {
                sendDryKillChatboxMessage(
                        encounter
                );
            }

            return;
        }

        handleQualifyingDrop(
                encounter,
                qualifyingDrop
        );
    }


    /**
     * Finds the first tracked drop contained in the loot.
     */
    private ItemStack findQualifyingDrop(
            EncounterDefinition encounter,
            Collection<ItemStack> items)
    {
        if (items == null
                || items.isEmpty())
        {
            return null;
        }

        for (ItemStack item
                : items)
        {
            if (item == null)
            {
                continue;
            }

            if (encounter.isQualifyingDrop(
                    item.getId(),
                    config.trackPets()))
            {
                return item;
            }
        }

        return null;
    }


    private void handleQualifyingDrop(
            EncounterDefinition encounter,
            ItemStack qualifyingDrop)
    {
        String itemName =
                getItemName(
                        qualifyingDrop.getId()
                );

        boolean pet =
                encounter.isPetDrop(
                        qualifyingDrop.getId()
                );

        EncounterStats stats =
                trackerManager.getStats(
                        encounter.getEncounterId()
                );

        String notificationText =
                "<col=FFFF00>"
                        + encounter.getDisplayName()
                        + "</col>"
                        + "<br>"
                        + "<col=FFFFFF>"
                        + itemName
                        + " x"
                        + qualifyingDrop.getQuantity()
                        + "</col>";

        if (stats != null
                && stats.getLastCompletedDryStreak() > 0)
        {
            notificationText +=
                    "<br>"
                            + "<col=FFFFFF>"
                            + "Dry streak ended at "
                            + stats.getLastCompletedDryStreak()
                            + " kills. Dry streak reset"
                            + "</col>";
        }

        notificationManager.notify(
                pet
                        ? "PET RECEIVED"
                        : "DROP RECEIVED",
                notificationText,
                0x00FF00
        );

        if (config.showChatboxMessages())
        {
            sendDropChatboxMessage(
                    encounter,
                    itemName,
                    qualifyingDrop.getQuantity(),
                    pet,
                    stats
            );
        }
    }


    /**
     * Generates a fingerprint for NpcLootReceived.
     *
     * This prevents the exact same kill event from being
     * recorded more than once.
     */
    private String createNpcKillEventKey(
            NPC npc,
            Collection<ItemStack> items)
    {
        StringBuilder key =
                new StringBuilder();

        key.append(
                "npc|"
        );

        key.append(
                client.getTickCount()
        );

        key.append(
                '|'
        );

        key.append(
                npc.getId()
        );

        key.append(
                '|'
        );

        key.append(
                npc.getIndex()
        );

        appendItemsToKey(
                key,
                items
        );

        return key.toString();
    }


    /**
     * Generates a fingerprint for LootReceived.
     */
    private String createGenericLootEventKey(
            LootReceived event)
    {
        StringBuilder key =
                new StringBuilder();

        key.append(
                "loot|"
        );

        key.append(
                client.getTickCount()
        );

        key.append(
                '|'
        );

        if (event.getType() != null)
        {
            key.append(
                    event.getType().name()
            );
        }

        key.append(
                '|'
        );

        if (event.getName() != null)
        {
            key.append(
                    event.getName()
            );
        }

        key.append(
                '|'
        );

        key.append(
                event.getAmount()
        );

        appendItemsToKey(
                key,
                event.getItems()
        );

        return key.toString();
    }


    private void appendItemsToKey(
            StringBuilder key,
            Collection<ItemStack> items)
    {
        if (items == null)
        {
            return;
        }

        for (ItemStack item
                : items)
        {
            if (item == null)
            {
                continue;
            }

            key.append(
                    '|'
            );

            key.append(
                    item.getId()
            );

            key.append(
                    'x'
            );

            key.append(
                    item.getQuantity()
            );
        }
    }


    private Map<Integer, ItemDisplayData>
    resolveEncounterItemDisplayData(
            EncounterStats stats)
    {
        Map<Integer, ItemDisplayData> result =
                new HashMap<>();

        if (stats == null
                || stats.getReceivedDrops() == null
                || stats.getReceivedDrops()
                .isEmpty())
        {
            return result;
        }

        for (Integer itemId
                : stats
                .getReceivedDrops()
                .keySet())
        {
            if (itemId == null)
            {
                continue;
            }

            try
            {
                String itemName =
                        getItemName(
                                itemId
                        );

                Image itemImage =
                        itemManager.getImage(
                                itemId
                        );

                /*
                 * Store the item name even if the sprite has not
                 * finished loading yet.
                 *
                 * DropItemPanel handles a null image asynchronously.
                 */
                result.put(
                        itemId,

                        new ItemDisplayData(
                                itemName,
                                itemImage
                        )
                );
            }
            catch (Exception e)
            {
                log.debug(
                        "Unable to resolve item {}",
                        itemId,
                        e
                );
            }
        }

        return result;
    }


    private String getItemName(
            int itemId)
    {
        ItemComposition item =
                itemManager
                        .getItemComposition(
                                itemId
                        );

        if (item != null
                && item.getName() != null
                && !item.getName()
                .isEmpty())
        {
            return item.getName();
        }

        return "Item "
                + itemId;
    }

    /**
     * Displays an in-game notification when the player
     * surpasses their previous longest dry streak.
     */
    private void sendDryRecordNotification(
            EncounterDefinition encounter,
            EncounterStats stats)
    {
        if (encounter == null || stats == null)
        {
            return;
        }

        String text =
                "<col=FFFF00>"
                        + encounter.getDisplayName()
                        + "</col>"
                        + "<br>"
                        + "<col=FFFFFF>"
                        + stats.getCurrentDryStreak()
                        + " KC Dry"
                        + "</col>";

        notificationManager.notify(
                "DRY STREAK RECORD",
                text,
                0xFF0000
        );
    }

    /**
     * Sends a chatbox message when the player surpasses
     * their previous longest dry streak.
     */
    private void sendDryRecordChatboxMessage(EncounterDefinition encounter, EncounterStats stats)
    {
        if (encounter == null || stats == null)
        {
            return;
        }

        String message = "<col=FF0000>[Dry Streak]</col> "
                        + "<col=FFFF00>["
                        + encounter.getDisplayName()
                        + "]</col>: "
                        + "<col=800080>New dry streak record! "
                        + stats.getCurrentDryStreak()
                        + " KC dry"
                        + "</col>";

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }

    private void sendDropChatboxMessage(
            EncounterDefinition encounter,
            String itemName,
            int quantity,
            boolean pet,
            EncounterStats stats)
    {
        String dropType =
                pet
                        ? "Pet"
                        : "Drop";

        String message =
                "<col=FF0000>[Dry Streak]</col> "
                        + "<col=FFFF00>["
                        + encounter.getDisplayName()
                        + "]</col>: "
                        + "<col=800080>"
                        + dropType
                        + " received: "
                        + itemName
                        + " x"
                        + quantity;

        if (stats != null
                && stats.getLastCompletedDryStreak() > 0)
        {
            message +=
                    ". Dry streak ended at "
                            + stats.getLastCompletedDryStreak()
                            + " kills.";
        }

        message +=
                " Dry streak reset.</col>";

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                message,
                null
        );
    }


    private void sendDryKillChatboxMessage(
            EncounterDefinition encounter)
    {
        EncounterStats stats =
                trackerManager.getStats(
                        encounter.getEncounterId()
                );

        if (stats == null)
        {
            return;
        }

        String message =
                "<col=FF0000>[Dry Streak]</col> "
                        + "<col=FFFF00>["
                        + encounter.getDisplayName()
                        + "]</col>: "
                        + "<col=800080>Kill #"
                        + stats.getCurrentDryStreak()
                        + " since last unique</col>";

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                message,
                null
        );
    }


    public void clearProcessedLootEvents()
    {
        trackerManager.clearProcessedKillEvents();
    }
}
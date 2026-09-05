package com.harrystyles.drystreaktracker.detection;

import com.harrystyles.drystreaktracker.DryStreakTrackerConfig;
import com.harrystyles.drystreaktracker.discord.DiscordWebhookService;
import com.harrystyles.drystreaktracker.discord.DropScreenshotService;
import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterLootType;
import com.harrystyles.drystreaktracker.encounter.EncounterRegistry;
import com.harrystyles.drystreaktracker.encounter.EncounterStats;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;
import com.harrystyles.drystreaktracker.encounter.tracking.RecentDrop;
import com.harrystyles.drystreaktracker.ui.DryStreakSidebarPanel;
import com.harrystyles.drystreaktracker.ui.ItemDisplayData;
import com.harrystyles.drystreaktracker.ui.notification.DryStreakNotificationManager;

import java.awt.Image;
import java.util.*;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;

import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;


@Slf4j
@Singleton
public class LootDetectionService {
    private final Client client;

    private final EncounterRegistry encounterRegistry;

    private final EncounterTrackerManager trackerManager;

    private final DryStreakNotificationManager notificationManager;

    private final DryStreakTrackerConfig config;

    private final ItemManager itemManager;

    private final DryStreakSidebarPanel sidebarPanel;

    private final DiscordWebhookService discordWebhookService;

    private final DropScreenshotService dropScreenshotService;

    /**
     * Pet tracking
     */
    private final PetAcquisitionTracker petAcquisitionTracker = new PetAcquisitionTracker();
    private EncounterDefinition pendingPetDryEncounter;
    private int pendingPetDryTick = -1;
    private int pendingPetDryStreak;
    private boolean pendingPetDryRecord;

    /**
     * NpcLootReceived events already processed during the current game tick.
     *
     * RuneLite normally follows NpcLootReceived with a LootReceived event for
     * the same NPC loot. These fingerprints allow LootReceived to act as a
     * fallback without counting normal NPC kills twice.
     */
    private final Map<String, Integer> processedNpcLootThisTick = new HashMap<>();
    private int processedNpcLootTick = -1;


    @Inject
    public LootDetectionService(
            Client client,
            EncounterRegistry encounterRegistry,
            EncounterTrackerManager trackerManager,
            DryStreakNotificationManager notificationManager,
            DryStreakTrackerConfig config,
            ItemManager itemManager,
            DryStreakSidebarPanel sidebarPanel,
            DiscordWebhookService discordWebhookService,
            DropScreenshotService dropScreenshotService) {
        this.client = client;

        this.encounterRegistry = encounterRegistry;

        this.trackerManager = trackerManager;

        this.notificationManager = notificationManager;

        this.config = config;

        this.itemManager = itemManager;

        this.sidebarPanel = sidebarPanel;

        this.discordWebhookService = discordWebhookService;
        this.dropScreenshotService = dropScreenshotService;

    }


    /**
     * Handles normal NPC death loot
     * Definition source:
     * <p>
     * encounters.json
     * <p>
     * Detection flow:
     * <p>
     * NPC dies
     * -> NpcLootReceived
     * -> NPC ID
     * -> EncounterRegistry
     * -> encounter completion
     */
    public void handleNpcLootReceived(NpcLootReceived event) {
        if (!canProcess()) {
            return;
        }

        if (event == null || event.getNpc() == null) {
            return;
        }

        NPC npc = event.getNpc();

        int npcId = npc.getId();

        Collection<ItemStack> items = event.getItems();

        log.debug("NpcLootReceived: npc={} id={} items={}", npc.getName(), npcId, items == null ? 0 : items.size());

        EncounterDefinition encounter = encounterRegistry.getByNpcId(npcId);

        if (encounter == null) {
            return;
        }

        /**
         * Extra safety check.
         *
         * Anything found by NPC id should have been loaded
         * from encounters.json.
         */
        if (encounter.getLootType() != EncounterLootType.GROUND_LOOT) {
            log.debug("Ignoring NpcLootReceived for {} because lootType={}", encounter.getEncounterId(), encounter.getLootType());

            return;
        }

        boolean petMessageMatchedBeforeLoot = false;

        if (isPetTrackingEnabledForEncounter(encounter)) {
            petMessageMatchedBeforeLoot = petAcquisitionTracker.matchEncounterLootToPendingPetMessage(encounter, client.getTickCount());
        }

        String eventKey = createNpcKillEventKey(npc, items);

        rememberNpcLootEvent(encounter, items);

        processEncounterLoot(encounter, items, eventKey, petMessageMatchedBeforeLoot);
    }


    /**
     * Handles RuneLite's generic LootReceived event.
     * Definition source:
     * loot-received-encounters.json
     *
     * This may include:
     * pickpocketing
     * object/chest rewards
     * reward containers
     * raid/event loot
     * other Loot Tracker sources
     */
    public void handleLootReceived(LootReceived event) {
        if (!canProcess()) {
            return;
        }

        if (event == null) {
            return;
        }

        if (event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }

        String sourceName = event.getName();

        if (sourceName == null || sourceName.trim().isEmpty()) {
            log.debug("Ignoring LootReceived event with no source name");

            return;
        }

        log.debug("LootReceived: source={} type={} amount={} items={}", sourceName, event.getType(), event.getAmount(), event.getItems().size());

        /*
         * Normal loot-received encounters keep using the existing
         * source-name based path.
         */
        EncounterDefinition encounter = encounterRegistry.getByLootSourceName(sourceName);

        if (encounter != null && encounter.getLootType() == EncounterLootType.LOOT_RECEIVED) {
            boolean petMessageMatchedBeforeLoot = false;

            if (isPetTrackingEnabledForEncounter(encounter)) {
                petMessageMatchedBeforeLoot = petAcquisitionTracker.matchEncounterLootToPendingPetMessage(encounter, client.getTickCount());
            }

            String eventKey = createGenericLootEventKey(event);

            processEncounterLoot(encounter, event.getItems(), eventKey, petMessageMatchedBeforeLoot);

            return;
        }

        /*
         * RuneLite normally detects NPC encounters through
         * NpcLootReceived. However, some ground-item quantity changes
         * can reach LootReceived without producing NpcLootReceived.
         *
         * Use the generic NPC loot event as a fallback in that case.
         */
        if (event.getType() == LootRecordType.NPC) {
            EncounterDefinition groundLootEncounter = encounterRegistry.getGroundLootBySourceName(sourceName);

            if (groundLootEncounter != null) {
                /*
                 * A matching NpcLootReceived already processed this
                 * exact loot during this tick, so this is RuneLite's
                 * normal duplicate LootReceived event.
                 */
                if (consumeNpcLootEvent(groundLootEncounter, event.getItems())) {
                    log.debug("Ignoring paired LootReceived for {} because NpcLootReceived already processed it", groundLootEncounter.getEncounterId());

                    return;
                }

                /*
                 * No matching NpcLootReceived was seen.
                 *
                 * This is the fallback case we observed when RuneLite
                 * detected an existing ground stack increasing.
                 */
                log.debug("Processing NPC LootReceived fallback for {}", groundLootEncounter.getEncounterId());

                boolean petMessageMatchedBeforeLoot = false;

                if (isPetTrackingEnabledForEncounter(groundLootEncounter)) {
                    petMessageMatchedBeforeLoot = petAcquisitionTracker.matchEncounterLootToPendingPetMessage(groundLootEncounter, client.getTickCount());
                }

                String eventKey = createNpcLootFallbackEventKey(groundLootEncounter, event);

                processEncounterLoot(groundLootEncounter, event.getItems(), eventKey, petMessageMatchedBeforeLoot);

                return;
            }
        }

        if (config.showLootSourceDebug() && encounter == null) {
            sendLootSourceDebugMessage(sourceName);
        }

        log.debug("No encounter registered for LootReceived source '{}'", sourceName);
    }


    private boolean canProcess() {
        return trackerManager.isActive() && client.getGameState() == GameState.LOGGED_IN;
    }


    /**
     * Converts one RuneLite loot event into one encounter
     * completion.
     *
     * Both NpcLootReceived and LootReceived eventually come
     * through this method.
     */
    private void processEncounterLoot(EncounterDefinition encounter, Collection<ItemStack> items, String eventKey, boolean petMessageMatchedBeforeLoot) {
        if (encounter == null) {
            return;
        }

        /*
         * Only normal tracked items are searched here.
         *
         * Pets are detected separately through the RuneScape
         * pet acquisition game message.
         */
        Map<Integer, Integer> qualifyingDrops = findQualifyingDrops(encounter, items);

        Integer firstDropItemId = null;
        int firstDropQuantity = 0;

        if (!qualifyingDrops.isEmpty()) {
            Map.Entry<Integer, Integer> firstDrop = qualifyingDrops.entrySet().iterator().next();

            firstDropItemId = firstDrop.getKey();
            firstDropQuantity = firstDrop.getValue();
        }

        /*
         * The encounter itself is recorded exactly once.
         *
         * The first tracked item, if present, is responsible for ending
         * the dry streak. Any additional tracked items are attached to
         * this same kill below.
         */
        boolean recorded = trackerManager.recordKill(encounter.getEncounterId(), eventKey, firstDropItemId, firstDropQuantity);

        if (!recorded) {
            return;
        }

        /*
         * Process every tracked item received from this encounter.
         */
        boolean firstDrop = true;

        for (Map.Entry<Integer, Integer> entry : qualifyingDrops.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();

            if (firstDrop) {
                firstDrop = false;

                processRecordedDrop(encounter, itemId, quantity, false);

                continue;
            }

            if (trackerManager.recordAdditionalDropForLastKill(encounter.getEncounterId(), itemId, quantity)) {
                processRecordedDrop(encounter, itemId, quantity, false);
            }
        }

        /*
         * The pet message arrived before the loot event.
         *
         * The kill has already been recorded above, so attach
         * the pet to that existing kill instead of creating a
         * second encounter completion.
         */
        if (petMessageMatchedBeforeLoot) {
            Integer petItemId = getPetItemId(encounter);

            if (petItemId != null && trackerManager.recordPetForLastKill(encounter.getEncounterId(), petItemId)) {
                clearPendingPetDryResult();

                processRecordedDrop(encounter, petItemId, 1, true);
            }
        }

        EncounterStats stats = trackerManager.getStats(encounter.getEncounterId());

        if (stats != null) {
            Map<Integer, ItemDisplayData> displayData = resolveEncounterItemDisplayData(stats);

            sidebarPanel.updateItemDisplayData(displayData);
        }

        /*
         * It was genuinely a dry kill only when neither a
         * normal tracked drop nor a pet was received.
         */
        if (qualifyingDrops.isEmpty() && !petMessageMatchedBeforeLoot) {
            /*
             * Pet-eligible encounters wait briefly before announcing
             * a dry kill because the pet acquisition message may
             * arrive shortly after the loot event.
             */
            if (isPetTrackingEnabledForEncounter(encounter)) {
                queuePendingPetDryResult(encounter, stats);

                return;
            }

            if (stats != null && stats.isNewDryRecordThisKill()) {
                sendDryRecordNotification(encounter, stats);

                if (config.showChatboxMessages()) {
                    sendDryRecordChatboxMessage(encounter, stats);
                }

                return;
            }

            if (config.showChatboxMessages()) {
                sendDryKillChatboxMessage(encounter);
            }

            return;
        }

        if (stats != null && stats.isNewDryRecordThisKill()) {
            sendDryRecordNotification(encounter, stats);

            if (config.showChatboxMessages()) {
                sendDryRecordChatboxMessage(encounter, stats);
            }
        }
    }
    /**
     * Releases a delayed dry result once the pet acquisition
     * matching window has expired.
     */
    public void processPendingPetDryResult() {
        if (!canProcess()) {
            return;
        }

        if (pendingPetDryEncounter == null || pendingPetDryTick < 0) {
            return;
        }

        int ticksElapsed = client.getTickCount() - pendingPetDryTick;

        if (ticksElapsed <= PetAcquisitionTracker.MATCH_WINDOW_TICKS) {
            return;
        }

        EncounterDefinition encounter = pendingPetDryEncounter;

        int dryStreak = pendingPetDryStreak;

        boolean newDryRecord = pendingPetDryRecord;

        /*
         * Clear first so the result cannot accidentally be sent
         * twice.
         */
        clearPendingPetDryResult();

        if (newDryRecord) {
            sendDryRecordNotification(encounter, dryStreak);

            if (config.showChatboxMessages()) {
                sendDryRecordChatboxMessage(encounter, dryStreak);
            }

            return;
        }

        if (config.showChatboxMessages()) {
            sendDryKillChatboxMessage(encounter, dryStreak);
        }
    }


    /**
     * Holds a dry result temporarily while waiting to see
     * whether a pet aquisition message belongs to the kill
     */
    private void queuePendingPetDryResult(EncounterDefinition encounter, EncounterStats stats) {
        if (encounter == null || stats == null) {
            return;
        }

        pendingPetDryEncounter = encounter;

        pendingPetDryTick = client.getTickCount();

        pendingPetDryStreak = stats.getCurrentDryStreak();

        pendingPetDryRecord = stats.isNewDryRecordThisKill();
    }

    /**
     * Clears a dry result that was waiting for the pet
     * acquisition matching window
     */
    private void clearPendingPetDryResult() {
        pendingPetDryEncounter = null;

        pendingPetDryTick = -1;

        pendingPetDryStreak = 0;

        pendingPetDryRecord = false;
    }

    /**
     * Returns the configured pet item id for an encounter
     */
    private Integer getPetItemId(EncounterDefinition encounter) {
        if (encounter == null || encounter.getPetDropIds() == null || encounter.getPetDropIds().isEmpty()) {
            return null;
        }

        return encounter.getPetDropIds().iterator().next();
    }

    /**
     * Returns whether pet detection should be active for this
     * encounter
     */
    private boolean isPetTrackingEnabledForEncounter(EncounterDefinition encounter) {
        return config.trackPets() && getPetItemId(encounter) != null;
    }

    /**
     * Handles recent-drop history, Discord uploads and
     * notifications after a tracked item has been recorded.
     *
     * This method does not modify encounter kill totals.
     */
    private void processRecordedDrop(EncounterDefinition encounter, int itemId, int quantity, boolean pet) {
        int gePrice = itemManager.getItemPrice(itemId);

        int totalGeValue = gePrice * quantity;

        trackerManager.recordRecentDrop(encounter.getEncounterId(), itemId, quantity, totalGeValue);

        Map<Integer, ItemDisplayData> displayData = new HashMap<>();

        String itemName = getItemName(itemId);

        Image itemImage = itemManager.getImage(itemId);

        displayData.put(itemId, new ItemDisplayData(itemName, itemImage));

        sidebarPanel.updateItemDisplayData(displayData);

        if (config.discordAutomaticUploads()) {
            RecentDrop recentDrop = trackerManager.getRecentDrops().isEmpty()
                    ? null
                    : trackerManager.getRecentDrops().get(0);

            if (recentDrop != null && discordWebhookService.canAutomaticallyUpload(recentDrop, pet)) {

                if (config.discordIncludeScreenshot()) {
                    dropScreenshotService.captureScreenshot(screenshot -> discordWebhookService.uploadDrop(recentDrop, itemName, screenshot));
                } else {
                    discordWebhookService.uploadDrop(recentDrop, itemName);
                }
            }
        }

        handleRecordedDrop(encounter, itemId, quantity, pet);
    }

    /**
     * Finds all tracked drops contained in one loot event.
     * <p>
     * Multiple tracked uniques may legitimately be received from
     * the same encounter completion.
     */
    private Map<Integer, Integer> findQualifyingDrops(EncounterDefinition encounter, Collection<ItemStack> items) {
        Map<Integer, Integer> qualifyingDrops = new LinkedHashMap<>();

        if (encounter == null || items == null || items.isEmpty()) {
            return qualifyingDrops;
        }

        for (ItemStack item : items) {
            if (item == null || !trackerManager.isDropEnabled(encounter.getEncounterId(), item.getId())) {
                continue;
            }

            qualifyingDrops.merge(item.getId(), item.getQuantity(), Integer::sum);
        }

        return qualifyingDrops;
    }


    /**
     * Displays notification and chat information for a
     * successfully recorded tracked drop.
     */
    private void handleRecordedDrop(EncounterDefinition encounter, int itemId, int quantity, boolean pet) {
        String itemName = getItemName(itemId);

        EncounterStats stats = trackerManager.getStats(encounter.getEncounterId());

        String notificationText =
                "<col=FFFF00>"
                        + encounter.getDisplayName()
                        + "</col>"
                        + "<br>"
                        + "<col=FFFFFF>"
                        + itemName
                        + " x"
                        + quantity
                        + "</col>";

        if (stats != null && stats.getLastCompletedDryStreak() > 0) {
            notificationText +=
                    "<br>"
                            + "<col=FFFFFF>"
                            + "Dry streak ended at "
                            + stats.getLastCompletedDryStreak()
                            + " KC. Dry streak reset"
                            + "</col>";
        }

        notificationManager.notify(pet ? "PET RECEIVED" : "DROP RECEIVED", notificationText, 0x00FF00);

        if (config.showChatboxMessages()) {
            sendDropChatboxMessage(encounter, itemName, quantity, pet, stats);
        }
    }

    /**
     * Handles RuneScape game messages used to detect pet
     * acquisitions.
     */
    public void handlePetAcquisitionMessage(ChatMessage event) {
        if (!canProcess()) {
            return;
        }

        if (!config.trackPets()) {
            return;
        }

        if (event == null || event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String message = Text.removeTags(event.getMessage());

        if (!petAcquisitionTracker.isPetAcquisitionMessage(message)) {
            return;
        }

        EncounterDefinition encounter = petAcquisitionTracker.matchPetMessageToRecentEncounter(client.getTickCount());

        /*
         * Null means the pet message arrived before the loot
         * event. The message is being held temporarily and will
         * be processed when the encounter loot arrives.
         */
        if (encounter == null) {
            return;
        }

        Integer petItemId = getPetItemId(encounter);

        if (petItemId == null) {
            return;
        }

        /*
         * Loot arrived first, so the encounter kill has already
         * been recorded.
         */
        boolean recorded = trackerManager.recordPetForLastKill(encounter.getEncounterId(), petItemId);

        if (!recorded) {
            return;
        }

        /*
         * This kill was waiting to be announced as dry, but the
         * pet message confirms that it was actually a pet kill.
         */
        clearPendingPetDryResult();

        processRecordedDrop(encounter, petItemId, 1, true);

        EncounterStats stats = trackerManager.getStats(encounter.getEncounterId());

        if (stats != null) {
            Map<Integer, ItemDisplayData> displayData = resolveEncounterItemDisplayData(stats);

            sidebarPanel.updateItemDisplayData(displayData);
        }
    }

    private void rememberNpcLootEvent(EncounterDefinition encounter, Collection<ItemStack> items) {
        int currentTick = client.getTickCount();

        if (processedNpcLootTick != currentTick) {
            processedNpcLootThisTick.clear();
            processedNpcLootTick = currentTick;
        }

        String matchKey = createNpcLootMatchKey(encounter, items);

        processedNpcLootThisTick.merge(matchKey, 1, Integer::sum);
    }


    private boolean consumeNpcLootEvent(EncounterDefinition encounter, Collection<ItemStack> items) {
        int currentTick = client.getTickCount();

        if (processedNpcLootTick != currentTick) {
            processedNpcLootThisTick.clear();
            processedNpcLootTick = currentTick;

            return false;
        }

        String matchKey = createNpcLootMatchKey(encounter, items);

        Integer amount = processedNpcLootThisTick.get(matchKey);

        if (amount == null || amount <= 0) {
            return false;
        }

        if (amount == 1) {
            processedNpcLootThisTick.remove(matchKey);
        } else {
            processedNpcLootThisTick.put(matchKey, amount - 1);
        }

        return true;
    }


    private String createNpcLootMatchKey(EncounterDefinition encounter, Collection<ItemStack> items) {
        StringBuilder key = new StringBuilder();

        key.append(encounter.getEncounterId());

        Map<Integer, Integer> quantities = new TreeMap<>();

        if (items != null) {
            for (ItemStack item : items) {
                if (item == null) {
                    continue;
                }

                quantities.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            key.append('|');
            key.append(entry.getKey());
            key.append('x');
            key.append(entry.getValue());
        }

        return key.toString();
    }


    private String createNpcLootFallbackEventKey(EncounterDefinition encounter, LootReceived event) {
        StringBuilder key = new StringBuilder();

        key.append("npc-fallback|");
        key.append(client.getTickCount());
        key.append('|');
        key.append(encounter.getEncounterId());

        appendItemsToKey(key, event.getItems());

        return key.toString();
    }


    /**
     * Generates a fingerprint for NpcLootReceived.
     * <p>
     * This prevents the exact same kill event from being
     * recorded more than once.
     */
    private String createNpcKillEventKey(NPC npc, Collection<ItemStack> items) {
        StringBuilder key = new StringBuilder();

        key.append("npc|");

        key.append(client.getTickCount());

        key.append('|');

        key.append(npc.getId());

        key.append('|');

        key.append(npc.getIndex());

        appendItemsToKey(key, items);

        return key.toString();
    }


    /**
     * Generates a fingerprint for LootReceived.
     */
    private String createGenericLootEventKey(LootReceived event) {
        StringBuilder key = new StringBuilder();

        key.append("loot|");

        key.append(client.getTickCount());

        key.append('|');

        if (event.getType() != null) {
            key.append(event.getType().name());
        }

        key.append('|');

        if (event.getName() != null) {
            key.append(event.getName());
        }

        key.append('|');

        key.append(event.getAmount());

        appendItemsToKey(key, event.getItems()
        );

        return key.toString();
    }


    private void appendItemsToKey(StringBuilder key, Collection<ItemStack> items) {
        if (items == null) {
            return;
        }

        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }

            key.append('|');

            key.append(item.getId());

            key.append('x');

            key.append(item.getQuantity());
        }
    }


    private Map<Integer, ItemDisplayData> resolveEncounterItemDisplayData(EncounterStats stats) {
        Map<Integer, ItemDisplayData> result = new HashMap<>();

        if (stats == null || stats.getReceivedDrops() == null || stats.getReceivedDrops().isEmpty()) {
            return result;
        }

        for (Integer itemId : stats.getReceivedDrops().keySet()) {
            if (itemId == null) {
                continue;
            }

            try {
                String itemName = getItemName(itemId);

                Image itemImage = itemManager.getImage(itemId);

                /**
                 * Store the item name even if the sprite has not
                 * finished loading yet.
                 *
                 * DropItemPanel handles a null image asynchronously.
                 */
                result.put(itemId, new ItemDisplayData(itemName, itemImage));
            } catch (Exception e) {
                log.debug("Unable to resolve item {}", itemId, e);
            }
        }

        return result;
    }


    private String getItemName(int itemId) {
        ItemComposition item = itemManager.getItemComposition(itemId);

        if (item != null && item.getName() != null && !item.getName().isEmpty()) {
            return item.getName();
        }

        return "Item " + itemId;
    }

    private void sendDryRecordNotification(EncounterDefinition encounter, int recordStreak) {
        if (encounter == null) {
            return;
        }

        String text =
                "<col=FFFF00>"
                        + encounter.getDisplayName()
                        + "</col>"
                        + "<br>"
                        + "<col=FFFFFF>"
                        + recordStreak
                        + " kc Dry"
                        + "</col>";

        notificationManager.notify("DRY STREAK RECORD", text, 0xFF0000);
    }

    /**
     * Displays an in-game notification when the player
     * surpasses their previous longest dry streak.
     */
    private void sendDryRecordNotification(EncounterDefinition encounter, EncounterStats stats) {
        if (encounter == null || stats == null) {
            return;
        }

        int recordStreak = stats.getCurrentDryStreak() > 0
                ? stats.getCurrentDryStreak()
                : stats.getLastCompletedDryStreak();

        sendDryRecordNotification(encounter, recordStreak);
    }

    private void sendDryRecordChatboxMessage(EncounterDefinition encounter, int recordStreak) {
        if (encounter == null) {
            return;
        }

        String message = "[<col=FF0000>Dry Streak</col>] "
                + "[<col=FFFF00>"
                + encounter.getDisplayName()
                + "</col>]: "
                + "<col=800080>New dry streak record! "
                + recordStreak
                + " kc dry"
                + "</col>";

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }

    /**
     * Sends a chatbox message when the player surpasses
     * their previous longest dry streak.
     */
    private void sendDryRecordChatboxMessage(EncounterDefinition encounter, EncounterStats stats) {
        if (encounter == null || stats == null) {
            return;
        }

        int recordStreak = stats.getCurrentDryStreak() > 0
                ? stats.getCurrentDryStreak()
                : stats.getLastCompletedDryStreak();

        sendDryRecordChatboxMessage(encounter, recordStreak);
    }

    private void sendDropChatboxMessage(EncounterDefinition encounter, String itemName, int quantity, boolean pet, EncounterStats stats) {
        String dropType = pet ? "Pet" : "Drop";

        String message =
                "[<col=FF0000>Dry Streak</col>] "
                        + "[<col=FFFF00>"
                        + encounter.getDisplayName()
                        + "</col>]: "
                        + dropType
                        + " received: <col=800080>"
                        + itemName
                        + "</col> x"
                        + quantity;

        if (stats != null && stats.getLastCompletedDryStreak() > 0) {
            message += ". Dry streak ended at " + stats.getLastCompletedDryStreak() + " KC.";
        }

        message += " Dry streak reset.</col>";

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }

    private void sendDryKillChatboxMessage(EncounterDefinition encounter, int dryStreak) {
        if (encounter == null) {
            return;
        }

        String message =
                "[<col=FF0000>Dry Streak</col>] "
                        + "[<col=FFFF00>"
                        + encounter.getDisplayName()
                        + "</col>]: <col=800080>"
                        + dryStreak
                        + "</col> kc since last unique";

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }

    private void sendDryKillChatboxMessage(EncounterDefinition encounter) {
        EncounterStats stats = trackerManager.getStats(encounter.getEncounterId());

        if (stats == null) {
            return;
        }

        sendDryKillChatboxMessage(encounter, stats.getCurrentDryStreak());
    }


    public void clearProcessedLootEvents() {
        petAcquisitionTracker.clearPetAcquisitionState();

        clearPendingPetDryResult();

        processedNpcLootThisTick.clear();
        processedNpcLootTick = -1;

        trackerManager.clearProcessedKillEvents();
    }

    /**
     * Displays internal LootReceived source name
     * for debugging and reporting missing encounters.
     */
    private void sendLootSourceDebugMessage(String sourceName) {
        if (sourceName == null || sourceName.trim().isEmpty()) {
            return;
        }

        String message =
                "[<col=FF0000>Dry Streak Debug</col>] "
                        + "Untracked loot source: "
                        + "<col=FFFF00>"
                        + sourceName
                        + "</col>"
                        + " <col=FFFFFF>- Please report this loot source on our Discord if you would like it added.</col>";

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }
}
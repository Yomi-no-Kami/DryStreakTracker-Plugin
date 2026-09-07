package com.harrystyles.drystreaktracker.cosmetic;

import com.harrystyles.drystreaktracker.DryStreakTrackerConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;

@Slf4j
@Singleton
public class SmokeLootbeamManager {
    private static final int SMOKE_MODEL_ID = 50683;
    private static final int SMOKE_ANIMATION_ID = 10727;

    /*
     * Ground item events and loot detection events should normally
     * occur during the same tick or immediately beside one another.
     *
     * Keeping only a very small tick window prevents unrelated
     * world spawns from being matched later.
     */
    private static final int MATCH_WINDOW_TICKS = 1;

    private final Client client;
    private final ClientThread clientThread;
    private final DryStreakTrackerConfig config;

    /*
     * Tiles which currently contain one or more confirmed
     * player-earned tracked drops.
     */
    private final Map<WorldPoint, SmokeTileState> activeSmoke = new HashMap<>();

    /*
     * Tracked drops confirmed by LootDetectionService but whose
     * ground ItemSpawned/ItemQuantityChanged event may not have
     * been observed yet.
     */
    private final List<PendingEarnedDrop> pendingEarnedDrops = new ArrayList<>();

    /*
     * Ground items which appeared recently but have not yet been
     * confirmed by LootDetectionService as player-earned.
     *
     * This handles RuneLite firing ItemSpawned before
     * NpcLootReceived.
     */
    private final List<RecentGroundItem> recentGroundItems = new ArrayList<>();

    private Model cachedSmokeModel;
    private SmokeLootbeamColor cachedSmokeColor;

    @Inject
    public SmokeLootbeamManager(Client client, ClientThread clientThread, DryStreakTrackerConfig config) {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
    }

    /**
     * Called by LootDetectionService after a tracked NPC loot event
     * has been successfully recorded.
     *
     * These item IDs are known to have actually been earned by the
     * player, so only they are eligible for Smoke Lootbeams.
     */
    public void markEarnedDrops(Set<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }

        cleanupMatchingState();

        int currentTick = client.getTickCount();

        for (Integer itemId : itemIds) {
            if (itemId == null || itemId <= 0) {
                continue;
            }

            /*
             * ItemSpawned may already have happened before
             * NpcLootReceived.
             *
             * If so, match the confirmed earned item against the
             * recently observed ground item immediately.
             */
            RecentGroundItem recentGroundItem = findRecentGroundItem(itemId, currentTick);

            if (recentGroundItem != null) {
                addEarnedItem(recentGroundItem.tile, itemId);

                recentGroundItems.remove(recentGroundItem);

                continue;
            }

            /*
             * Otherwise remember the earned item briefly and let the
             * upcoming ItemSpawned or ItemQuantityChanged event match it.
             */
            pendingEarnedDrops.add(new PendingEarnedDrop(itemId, currentTick));
        }
    }

    public void handleItemSpawned(ItemSpawned event) {
        if (event == null || event.getItem() == null || event.getTile() == null) {
            return;
        }

        cleanupMatchingState();

        TileItem item = event.getItem();
        int itemId = item.getId();
        int currentTick = client.getTickCount();

        PendingEarnedDrop pendingDrop = findPendingEarnedDrop(itemId, currentTick);

        if (pendingDrop != null) {
            pendingEarnedDrops.remove(pendingDrop);

            addEarnedItem(event.getTile(), itemId);

            return;
        }

        /*
         * Do not create smoke yet.
         *
         * ItemSpawned can represent static world spawns,
         * other players' visible loot, or unrelated items.
         *
         * Remember the item briefly in case LootDetectionService
         * confirms it as ours later in the same matching window.
         */
        recentGroundItems.add(new RecentGroundItem(event.getTile(), itemId, currentTick));
    }

    public void handleItemDespawned(ItemDespawned event) {
        if (event == null || event.getItem() == null || event.getTile() == null) {
            return;
        }

        int itemId = event.getItem().getId();

        removeRecentGroundItem(event.getTile(), itemId);

        removeEarnedItem(event.getTile(), itemId);
    }

    public void handleItemQuantityChanged(ItemQuantityChanged event) {
        if (event == null || event.getItem() == null || event.getTile() == null) {
            return;
        }

        cleanupMatchingState();

        int itemId = event.getItem().getId();
        int currentTick = client.getTickCount();

        /*
         * RuneLite may increase an existing ground stack instead
         * of firing ItemSpawned.
         */
        if (event.getNewQuantity() > event.getOldQuantity()) {
            PendingEarnedDrop pendingDrop = findPendingEarnedDrop(itemId, currentTick);

            if (pendingDrop != null) {
                pendingEarnedDrops.remove(pendingDrop);

                addEarnedItem(event.getTile(), itemId);

                return;
            }

            /*
             * The quantity-change event may arrive before the
             * corresponding loot detection event.
             */
            recentGroundItems.add(new RecentGroundItem(event.getTile(), itemId, currentTick));

            return;
        }

        if (event.getNewQuantity() <= 0) {
            removeRecentGroundItem(event.getTile(), itemId);

            removeEarnedItem(event.getTile(), itemId);
        }
    }

    public void handleConfigChanged(ConfigChanged event) {
        if (event == null || !"drystreaktracker".equals(event.getGroup())) {
            return;
        }

        if (!"enableSmokeLootbeams".equals(event.getKey())
                && !"smokeLootbeamColor".equals(event.getKey())) {
            return;
        }

        clientThread.invokeLater(() -> {
            cachedSmokeModel = null;
            cachedSmokeColor = null;

            if (!config.enableSmokeLootbeams()) {
                deactivateAllSmokeObjects();

                return;
            }

            rebuildSmokeObjects();
        });
    }

    public void clear() {
        for (SmokeTileState state : activeSmoke.values()) {
            deactivateSmokeObject(state);
        }

        activeSmoke.clear();
        pendingEarnedDrops.clear();
        recentGroundItems.clear();

        cachedSmokeModel = null;
        cachedSmokeColor = null;
    }

    private PendingEarnedDrop findPendingEarnedDrop(int itemId, int currentTick) {
        for (PendingEarnedDrop pendingDrop : pendingEarnedDrops) {
            if (pendingDrop == null || pendingDrop.itemId != itemId) {
                continue;
            }

            if (currentTick - pendingDrop.tick <= MATCH_WINDOW_TICKS) {
                return pendingDrop;
            }
        }

        return null;
    }

    private RecentGroundItem findRecentGroundItem(int itemId, int currentTick) {
        for (RecentGroundItem recentGroundItem : recentGroundItems) {
            if (recentGroundItem == null || recentGroundItem.itemId != itemId) {
                continue;
            }

            if (currentTick - recentGroundItem.tick <= MATCH_WINDOW_TICKS) {
                return recentGroundItem;
            }
        }

        return null;
    }

    private void cleanupMatchingState() {
        int currentTick = client.getTickCount();

        Iterator<PendingEarnedDrop> pendingIterator = pendingEarnedDrops.iterator();

        while (pendingIterator.hasNext()) {
            PendingEarnedDrop pendingDrop = pendingIterator.next();

            if (pendingDrop == null || currentTick - pendingDrop.tick > MATCH_WINDOW_TICKS) {
                pendingIterator.remove();
            }
        }

        Iterator<RecentGroundItem> recentIterator = recentGroundItems.iterator();

        while (recentIterator.hasNext()) {
            RecentGroundItem recentGroundItem = recentIterator.next();

            if (recentGroundItem == null || currentTick - recentGroundItem.tick > MATCH_WINDOW_TICKS) {
                recentIterator.remove();
            }
        }
    }

    private void removeRecentGroundItem(Tile tile, int itemId) {
        if (tile == null || itemId <= 0) {
            return;
        }

        WorldPoint worldPoint = tile.getWorldLocation();

        if (worldPoint == null) {
            return;
        }

        Iterator<RecentGroundItem> iterator = recentGroundItems.iterator();

        while (iterator.hasNext()) {
            RecentGroundItem recentGroundItem = iterator.next();

            if (recentGroundItem == null
                    || recentGroundItem.itemId != itemId
                    || recentGroundItem.tile == null) {
                continue;
            }

            WorldPoint recentWorldPoint = recentGroundItem.tile.getWorldLocation();

            if (worldPoint.equals(recentWorldPoint)) {
                iterator.remove();
            }
        }
    }

    private void addEarnedItem(Tile tile, int itemId) {
        if (tile == null || itemId <= 0) {
            return;
        }

        WorldPoint worldPoint = tile.getWorldLocation();

        if (worldPoint == null) {
            return;
        }

        SmokeTileState state = activeSmoke.get(worldPoint);

        if (state == null) {
            state = new SmokeTileState(tile.getLocalLocation(), tile.getPlane());

            activeSmoke.put(worldPoint, state);
        }

        state.itemIds.add(itemId);

        if (config.enableSmokeLootbeams() && state.smokeObject == null) {
            state.smokeObject = createSmokeObject(state.localPoint, state.plane);
        }
    }

    private void removeEarnedItem(Tile tile, int itemId) {
        if (tile == null || itemId <= 0) {
            return;
        }

        WorldPoint worldPoint = tile.getWorldLocation();

        if (worldPoint == null) {
            return;
        }

        SmokeTileState state = activeSmoke.get(worldPoint);

        if (state == null) {
            return;
        }

        state.itemIds.remove(itemId);

        if (!state.itemIds.isEmpty()) {
            return;
        }

        deactivateSmokeObject(state);

        activeSmoke.remove(worldPoint);
    }

    private RuneLiteObject createSmokeObject(LocalPoint localPoint, int plane) {
        if (localPoint == null) {
            return null;
        }

        Model model = getSmokeModel();

        if (model == null) {
            return null;
        }

        AnimationController animationController = new AnimationController(client, SMOKE_ANIMATION_ID);

        animationController.setOnFinished(AnimationController::loop);

        RuneLiteObject smokeObject = client.createRuneLiteObject();

        smokeObject.setModel(model);
        smokeObject.setAnimationController(animationController);
        smokeObject.setLocation(localPoint, plane);
        smokeObject.setActive(true);

        return smokeObject;
    }

    private Model getSmokeModel() {
        SmokeLootbeamColor smokeColor = config.smokeLootbeamColor();

        if (smokeColor == null) {
            smokeColor = SmokeLootbeamColor.PURPLE;
        }

        if (cachedSmokeModel != null && cachedSmokeColor == smokeColor) {
            return cachedSmokeModel;
        }

        ModelData modelData = client.loadModelData(SMOKE_MODEL_ID);

        if (modelData == null) {
            log.warn("Unable to load Smoke Lootbeam model {}", SMOKE_MODEL_ID);

            return null;
        }

        modelData = modelData.cloneColors();

        short[] faceColors = modelData.getFaceColors();

        if (faceColors != null) {
            for (short originalColor : faceColors) {
                int saturation = JagexColor.unpackSaturation(originalColor);
                int luminance = JagexColor.unpackLuminance(originalColor);

                short replacementColor = JagexColor.packHSL(smokeColor.getHue(), saturation, luminance);

                modelData.recolor(originalColor, replacementColor);
            }
        }

        cachedSmokeModel = modelData.light(
                ModelData.DEFAULT_AMBIENT,
                ModelData.DEFAULT_CONTRAST,
                ModelData.DEFAULT_X,
                ModelData.DEFAULT_Y,
                ModelData.DEFAULT_Z
        );

        cachedSmokeColor = smokeColor;

        return cachedSmokeModel;
    }

    private void rebuildSmokeObjects() {
        for (SmokeTileState state : activeSmoke.values()) {
            deactivateSmokeObject(state);

            state.smokeObject = createSmokeObject(state.localPoint, state.plane);
        }
    }

    private void deactivateAllSmokeObjects() {
        for (SmokeTileState state : activeSmoke.values()) {
            deactivateSmokeObject(state);
        }
    }

    private void deactivateSmokeObject(SmokeTileState state) {
        if (state == null || state.smokeObject == null) {
            return;
        }

        state.smokeObject.setActive(false);
        state.smokeObject = null;
    }

    private static class PendingEarnedDrop {
        private final int itemId;
        private final int tick;

        private PendingEarnedDrop(int itemId, int tick) {
            this.itemId = itemId;
            this.tick = tick;
        }
    }

    private static class RecentGroundItem {
        private final Tile tile;
        private final int itemId;
        private final int tick;

        private RecentGroundItem(Tile tile, int itemId, int tick) {
            this.tile = tile;
            this.itemId = itemId;
            this.tick = tick;
        }
    }

    private static class SmokeTileState {
        private final LocalPoint localPoint;
        private final int plane;
        private final Set<Integer> itemIds = new HashSet<>();

        private RuneLiteObject smokeObject;

        private SmokeTileState(LocalPoint localPoint, int plane) {
            this.localPoint = localPoint;
            this.plane = plane;
        }
    }
}
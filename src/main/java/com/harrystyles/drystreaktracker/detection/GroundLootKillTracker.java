package com.harrystyles.drystreaktracker.detection;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;
import com.harrystyles.drystreaktracker.encounter.EncounterLootType;
import com.harrystyles.drystreaktracker.encounter.EncounterRegistry;
import com.harrystyles.drystreaktracker.encounter.tracking.EncounterTrackerManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;

/**
 * Tracks the death lifecycle of NPC-based GROUND_LOOT encounters.
 *
 * Ground-loot encounters cannot rely entirely on loot events because
 * some NPC kills legitimately produce no ground loot.
 *
 * Lifecycle:
 *
 * local interaction
 * -> ActorDeath
 * -> pending death
 * -> NpcDespawned
 * -> loot event OR no-loot timeout
 *
 * LootDetectionService remains responsible for deciding whether the
 * resulting loot contains a tracked drop.
 */
@Slf4j
@Singleton
public class GroundLootKillTracker {
    /*
     * Keeps a player's NPC interaction around after they switch targets.
     *
     * This is required for fast ranged/projectile combat where the player
     * may already be attacking another NPC when the previous target dies.
     */
    private static final int INTERACTION_RETENTION_TICKS = 10;

    /*
     * Once the NPC disappears, give RuneLite a short period to deliver
     * NpcLootReceived/LootReceived before treating the kill as no-loot.
     */
    private static final int DESPAWN_LOOT_WAIT_TICKS = 2;

    /*
     * Emergency fallback only.
     *
     * NpcDespawned should normally arrive after ActorDeath. This prevents
     * an unusual missed despawn event from leaving a pending death forever.
     */
    private static final int DEATH_FALLBACK_TICKS = 20;

    /*
     * Completed deaths are remembered briefly so reversed or late
     * RuneLite events cannot count the same NPC twice.
     */
    private static final int FINALIZED_RETENTION_TICKS = 7;

    private final Client client;
    private final EncounterRegistry encounterRegistry;
    private final EncounterTrackerManager trackerManager;

    private final List<PendingDeath> pendingDeaths = new ArrayList<>();
    private final List<FinalizedDeath> finalizedDeaths = new ArrayList<>();
    private final Map<Long, Integer> recentInteractions = new HashMap<>();

    @Inject
    public GroundLootKillTracker(Client client, EncounterRegistry encounterRegistry, EncounterTrackerManager trackerManager) {
        this.client = client;
        this.encounterRegistry = encounterRegistry;
        this.trackerManager = trackerManager;
    }

    /**
     * Remembers NPCs targeted by the local player.
     *
     * NPC id + index is stored separately for every target so rapid
     * multi-target/projectile combat does not overwrite an earlier target.
     */
    public void handleInteractingChanged(InteractingChanged event) {
        if (!canProcess() || event == null || client.getLocalPlayer() == null) {
            return;
        }

        if (event.getSource() != client.getLocalPlayer()
                || !(event.getTarget() instanceof NPC)) {
            return;
        }

        NPC npc = (NPC) event.getTarget();

        EncounterDefinition encounter = getGroundLootEncounter(npc);

        if (encounter == null) {
            return;
        }

        /*
         * NPC indices are reused when NPCs respawn.
         *
         * Interacting with a living NPC proves that this id/index now
         * represents a new NPC incarnation. Remove a finalized marker
         * left by the previous NPC which occupied the same slot.
         */
        removeFinalizedDeath(encounter.getEncounterId(), npc.getId(), npc.getIndex());

        recentInteractions.put(createNpcKey(npc.getId(), npc.getIndex()), client.getTickCount());

        log.debug(
                "Remembered ground-loot NPC interaction: encounter={} npc={} id={} index={} tick={}",
                encounter.getEncounterId(),
                npc.getName(),
                npc.getId(),
                npc.getIndex(),
                client.getTickCount()
        );
    }

    /**
     * Creates a pending death for a ground-loot encounter.
     *
     * Nothing is recorded yet because the NPC may still produce loot.
     */
    public void handleActorDeath(ActorDeath event) {
        if (!canProcess()
                || event == null
                || !(event.getActor() instanceof NPC)) {
            return;
        }

        NPC npc = (NPC) event.getActor();

        EncounterDefinition encounter = getGroundLootEncounter(npc);

        if (encounter == null) {
            return;
        }

        /*
         * RuneLite can occasionally provide loot before ActorDeath.
         *
         * If this exact NPC death was already completed from loot,
         * do not create a second pending kill.
         */
        if (wasDeathFinalized(encounter, npc)) {
            log.debug(
                    "Ignoring ActorDeath for recently finalized ground-loot NPC: encounter={} npc={} id={} index={} tick={}",
                    encounter.getEncounterId(),
                    npc.getName(),
                    npc.getId(),
                    npc.getIndex(),
                    client.getTickCount()
            );

            return;
        }

        /*
         * ActorDeath is global. It also fires for NPCs killed by other
         * players, so no-loot tracking requires evidence that the local
         * player was fighting this exact NPC.
         */
        if (!wasLocalPlayerFightingNpc(npc)) {
            return;
        }

        recentInteractions.remove(createNpcKey(npc.getId(), npc.getIndex()));

        for (PendingDeath pendingDeath : pendingDeaths) {
            if (pendingDeath == null) {
                continue;
            }

            if (pendingDeath.npcId == npc.getId()
                    && pendingDeath.npcIndex == npc.getIndex()) {
                return;
            }
        }

        pendingDeaths.add(new PendingDeath(
                encounter.getEncounterId(),
                npc.getId(),
                npc.getIndex(),
                client.getTickCount()
        ));

        log.debug(
                "Pending ground-loot NPC death: encounter={} npc={} id={} index={} tick={}",
                encounter.getEncounterId(),
                npc.getName(),
                npc.getId(),
                npc.getIndex(),
                client.getTickCount()
        );
    }

    /**
     * Marks the end of a pending NPC's death animation.
     *
     * The normal no-loot grace period begins here rather than ActorDeath
     * because different NPCs have different death-animation lengths.
     */
    public void handleNpcDespawned(NpcDespawned event) {
        if (!canProcess() || event == null || event.getNpc() == null) {
            return;
        }

        NPC npc = event.getNpc();

        for (PendingDeath pendingDeath : pendingDeaths) {
            if (pendingDeath == null
                    || pendingDeath.npcId != npc.getId()
                    || pendingDeath.npcIndex != npc.getIndex()) {
                continue;
            }

            pendingDeath.despawnTick = client.getTickCount();

            log.debug(
                    "Pending ground-loot NPC death despawned: encounter={} npc={} id={} index={} deathTick={} despawnTick={}",
                    pendingDeath.encounterId,
                    npc.getName(),
                    npc.getId(),
                    npc.getIndex(),
                    pendingDeath.deathTick,
                    pendingDeath.despawnTick
            );

            return;
        }
    }


    /**
     * Consumes the exact pending death belonging to NpcLootReceived.
     */
    public PendingDeath consumePendingDeath(EncounterDefinition encounter, NPC npc) {
        if (encounter == null || npc == null) {
            return null;
        }

        Iterator<PendingDeath> iterator = pendingDeaths.iterator();

        while (iterator.hasNext()) {
            PendingDeath pendingDeath = iterator.next();

            if (pendingDeath == null) {
                iterator.remove();
                continue;
            }

            if (!encounter.getEncounterId().equals(pendingDeath.encounterId)
                    || pendingDeath.npcId != npc.getId()
                    || pendingDeath.npcIndex != npc.getIndex()) {
                continue;
            }

            iterator.remove();

            return pendingDeath;
        }

        return null;
    }

    /**
     * Generic LootReceived does not provide an NPC object.
     *
     * When it is being used as the NPC-loot fallback, consume the
     * oldest pending death belonging to this encounter.
     */
    public PendingDeath consumePendingDeath(EncounterDefinition encounter) {
        if (encounter == null) {
            return null;
        }

        PendingDeath oldestMatch = null;

        for (PendingDeath pendingDeath : pendingDeaths) {
            if (pendingDeath == null
                    || !encounter.getEncounterId().equals(pendingDeath.encounterId)) {
                continue;
            }

            if (oldestMatch == null || pendingDeath.deathTick < oldestMatch.deathTick) {
                oldestMatch = pendingDeath;
            }
        }

        if (oldestMatch != null) {
            pendingDeaths.remove(oldestMatch);
        }

        return oldestMatch;
    }

    /**
     * Returns deaths which have reached the confirmed no-loot state.
     *
     * LootDetectionService records the actual dry kill so all existing
     * encounter stats, notifications and saving still use one code path.
     */
    public List<PendingDeath> pollNoLootDeaths() {
        List<PendingDeath> noLootDeaths = new ArrayList<>();

        if (!canProcess()) {
            return noLootDeaths;
        }

        int currentTick = client.getTickCount();

        Iterator<PendingDeath> iterator = pendingDeaths.iterator();

        while (iterator.hasNext()) {
            PendingDeath pendingDeath = iterator.next();

            if (pendingDeath == null) {
                iterator.remove();
                continue;
            }

            /*
             * Normal path.
             *
             * Once the NPC has disappeared, wait briefly for its loot.
             */
            if (pendingDeath.despawnTick >= 0) {
                int ticksSinceDespawn = currentTick - pendingDeath.despawnTick;

                if (ticksSinceDespawn <= DESPAWN_LOOT_WAIT_TICKS) {
                    continue;
                }

                iterator.remove();
                noLootDeaths.add(pendingDeath);

                continue;
            }

            /*
             * Emergency fallback only.
             */
            int ticksSinceDeath = currentTick - pendingDeath.deathTick;

            if (ticksSinceDeath <= DEATH_FALLBACK_TICKS) {
                continue;
            }

            iterator.remove();

            log.debug(
                    "Ground-loot NPC death {} never received NpcDespawned after {} ticks; using fallback",
                    pendingDeath.encounterId,
                    ticksSinceDeath
            );

            noLootDeaths.add(pendingDeath);
        }

        cleanupFinalizedDeaths(currentTick);
        cleanupRecentInteractions(currentTick);

        return noLootDeaths;
    }

    public boolean wasDeathFinalized(EncounterDefinition encounter, NPC npc) {
        if (encounter == null || npc == null) {
            return false;
        }

        int currentTick = client.getTickCount();

        for (FinalizedDeath finalizedDeath : finalizedDeaths) {
            if (finalizedDeath == null
                    || !encounter.getEncounterId().equals(finalizedDeath.encounterId)) {
                continue;
            }

            if (currentTick - finalizedDeath.finalizedTick > FINALIZED_RETENTION_TICKS) {
                continue;
            }

            if ((finalizedDeath.npcId == -1 || finalizedDeath.npcId == npc.getId())
                    && (finalizedDeath.npcIndex == -1 || finalizedDeath.npcIndex == npc.getIndex())) {
                return true;
            }
        }

        return false;
    }

    public boolean wasEncounterRecentlyFinalized(EncounterDefinition encounter) {
        if (encounter == null) {
            return false;
        }

        int currentTick = client.getTickCount();

        for (FinalizedDeath finalizedDeath : finalizedDeaths) {
            if (finalizedDeath == null
                    || !encounter.getEncounterId().equals(finalizedDeath.encounterId)) {
                continue;
            }

            if (currentTick - finalizedDeath.finalizedTick <= FINALIZED_RETENTION_TICKS) {
                return true;
            }
        }

        return false;
    }

    /**
     * Marks an exact NPC death as completed.
     */
    public void finalizeDeath(EncounterDefinition encounter, NPC npc, int tick) {
        if (encounter == null || npc == null) {
            return;
        }

        removeFinalizedDeath(encounter.getEncounterId(), npc.getId(), npc.getIndex());

        finalizedDeaths.add(new FinalizedDeath(
                encounter.getEncounterId(),
                npc.getId(),
                npc.getIndex(),
                tick
        ));
    }

    /**
     * Marks a pending death as completed.
     */
    public void finalizeDeath(PendingDeath pendingDeath, int tick) {
        if (pendingDeath == null) {
            return;
        }

        removeFinalizedDeath(
                pendingDeath.encounterId,
                pendingDeath.npcId,
                pendingDeath.npcIndex
        );

        finalizedDeaths.add(new FinalizedDeath(
                pendingDeath.encounterId,
                pendingDeath.npcId,
                pendingDeath.npcIndex,
                tick
        ));
    }

    /**
     * Generic LootReceived does not identify the exact NPC.
     *
     * Only use this when there was no pending death available to provide
     * an exact NPC id/index.
     */
    public void finalizeEncounter(EncounterDefinition encounter, int tick) {
        if (encounter == null) {
            return;
        }

        finalizedDeaths.add(new FinalizedDeath(
                encounter.getEncounterId(),
                -1,
                -1,
                tick
        ));
    }

    public void clear() {
        pendingDeaths.clear();
        finalizedDeaths.clear();
        recentInteractions.clear();
    }

    /**
     * Resolves both official and player-created GROUND_LOOT encounters.
     *
     * Official encounter definitions always win by exact NPC ID.
     * Custom name + combat-level lookup remains only as the fallback for
     * player-created NPC variants which have not yet registered that ID.
     */
    private EncounterDefinition getGroundLootEncounter(NPC npc) {
        if (npc == null) {
            return null;
        }

        EncounterDefinition encounter = encounterRegistry.getByNpcId(npc.getId());

        if (encounter == null) {
            encounter = trackerManager.getCustomEncounterByNpc(npc.getName(), npc.getCombatLevel());
        }

        if (encounter == null
                || encounter.getLootType() != EncounterLootType.GROUND_LOOT) {
            return null;
        }

        return encounter;
    }

    private boolean wasLocalPlayerFightingNpc(NPC npc) {
        if (npc == null || client.getLocalPlayer() == null) {
            return false;
        }

        if (client.getLocalPlayer().getInteracting() == npc
                || npc.getInteracting() == client.getLocalPlayer()) {
            return true;
        }

        long npcKey = createNpcKey(npc.getId(), npc.getIndex());

        Integer interactionTick = recentInteractions.get(npcKey);

        if (interactionTick == null) {
            log.debug(
                    "Ignoring ground-loot NPC death because no local interaction was recorded: npc={} id={} index={} deathTick={}",
                    npc.getName(),
                    npc.getId(),
                    npc.getIndex(),
                    client.getTickCount()
            );

            return false;
        }

        if (client.getTickCount() - interactionTick > INTERACTION_RETENTION_TICKS) {
            recentInteractions.remove(npcKey);

            log.debug(
                    "Ignoring ground-loot NPC death because local interaction expired: npc={} id={} index={} interactionTick={} deathTick={}",
                    npc.getName(),
                    npc.getId(),
                    npc.getIndex(),
                    interactionTick,
                    client.getTickCount()
            );

            return false;
        }

        return true;
    }

    private void removeFinalizedDeath(String encounterId, int npcId, int npcIndex) {
        if (encounterId == null) {
            return;
        }

        finalizedDeaths.removeIf(finalizedDeath ->
                finalizedDeath != null
                        && encounterId.equals(finalizedDeath.encounterId)
                        && finalizedDeath.npcId == npcId
                        && finalizedDeath.npcIndex == npcIndex
        );
    }

    private void cleanupFinalizedDeaths(int currentTick) {
        finalizedDeaths.removeIf(finalizedDeath ->
                finalizedDeath == null
                        || currentTick - finalizedDeath.finalizedTick > FINALIZED_RETENTION_TICKS
        );
    }

    private void cleanupRecentInteractions(int currentTick) {
        recentInteractions.entrySet().removeIf(entry ->
                entry == null
                        || entry.getValue() == null
                        || currentTick - entry.getValue() > INTERACTION_RETENTION_TICKS
        );
    }

    private long createNpcKey(int npcId, int npcIndex) {
        return ((long) npcId << 32) | (npcIndex & 0xffffffffL);
    }

    private boolean canProcess() {
        return trackerManager.isActive()
                && client.getGameState() == GameState.LOGGED_IN;
    }

    public static class PendingDeath {
        private final String encounterId;
        private final int npcId;
        private final int npcIndex;
        private final int deathTick;
        private int despawnTick = -1;

        private PendingDeath(String encounterId, int npcId, int npcIndex, int deathTick) {
            this.encounterId = encounterId;
            this.npcId = npcId;
            this.npcIndex = npcIndex;
            this.deathTick = deathTick;
        }

        public String getEncounterId() {
            return encounterId;
        }

        public int getNpcId() {
            return npcId;
        }

        public int getNpcIndex() {
            return npcIndex;
        }

        public int getDeathTick() {
            return deathTick;
        }

        public int getDespawnTick() {
            return despawnTick;
        }

        public String createEventKey() {
            return "ground_loot_death|"
                    + deathTick
                    + "|"
                    + npcId
                    + "|"
                    + npcIndex;
        }
    }

    private static class FinalizedDeath {
        private final String encounterId;
        private final int npcId;
        private final int npcIndex;
        private final int finalizedTick;

        private FinalizedDeath(String encounterId, int npcId, int npcIndex, int finalizedTick) {
            this.encounterId = encounterId;
            this.npcId = npcId;
            this.npcIndex = npcIndex;
            this.finalizedTick = finalizedTick;
        }
    }
}
package com.harrystyles.drystreaktracker.detection;

import com.harrystyles.drystreaktracker.encounter.EncounterDefinition;

/**
 * Associates RuneScape pet acquisition messages with
 * recently completed encounters.
 *
 * Pets are announced through game messages rather than
 * appearing as normal loot items.
 *
 * The pet message may arrive shortly before or after the
 * encounter loot event, so both event orders are supported.
 */
public class PetAcquisitionTracker {
    static final int MATCH_WINDOW_TICKS = 10;

    private static final int NO_TICK = Integer.MIN_VALUE / 2;

    private int pendingPetMessageTick = NO_TICK;

    private int recentEncounterTick = NO_TICK;

    private EncounterDefinition recentEncounter;

    /**
     * Checks whether a game message is one of RuneScape's
     * pet acquisition messages.
     */
    public boolean isPetAcquisitionMessage(String message) {
        if (message == null) {
            return false;
        }

        return message.startsWith("You have a funny feeling like you")
                || message.startsWith("You feel something weird sneaking");
    }

    /**
     * Records a recently completed encounter and checks whether
     * a pending pet acquisition message belongs to it
     *
     * @return true if a pet acquisition message arrived shortly
     * before this encounter's loot event
     */
    public boolean matchEncounterLootToPendingPetMessage(EncounterDefinition encounter, int tick) {
        if (encounter == null) {
            return false;
        }

        recentEncounter = encounter;
        recentEncounterTick = tick;

        if (isWithinWindow(tick, pendingPetMessageTick)) {
            pendingPetMessageTick = NO_TICK;

            clearRecentEncounter();

            return true;
        }

        return false;
    }

    /**
     * Called when RuneScape sends a pet acquisition message.
     *
     * @return the recently completed encounter if one can
     * immediately be matched, otherwise null
     */
    public EncounterDefinition matchPetMessageToRecentEncounter(int tick) {
        if (recentEncounter != null && isWithinWindow(tick, recentEncounterTick)) {
            EncounterDefinition encounter = recentEncounter;

            clearRecentEncounter();

            pendingPetMessageTick = NO_TICK;

            return encounter;
        }

        /*
         * The pet message may have arrived before the loot
         * event. Hold it temporarily until loot arrives.
         */
        pendingPetMessageTick = tick;

        /*
         * Do not keep an old encounter around once it has
         * fallen outside the matching window.
         */
        if (recentEncounter != null && !isWithinWindow(tick, recentEncounterTick)) {
            clearRecentEncounter();
        }

        return null;
    }

    /**
     * Clears transient pet detection state.
     */
    public void clearPetAcquisitionState() {
        pendingPetMessageTick = NO_TICK;

        clearRecentEncounter();
    }

    private boolean isWithinWindow(int currentTick, int previousTick) {
        if (previousTick == NO_TICK) {
            return false;
        }

        int difference = currentTick - previousTick;

        return difference >= 0 && difference <= MATCH_WINDOW_TICKS;
    }

    private void clearRecentEncounter() {
        recentEncounter = null;
        recentEncounterTick = NO_TICK;
    }
}
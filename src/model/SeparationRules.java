package model;

/**
 * Minimum gap, in minutes, between two aircraft using the same runway.
 * Based on the real wake-turbulence rule: the heavier the aircraft in front and the lighter
 * the one behind, the longer the wait (scaled to whole minutes for the simulation).
 */
public final class SeparationRules {
    //                       follower:  LIGHT MEDIUM HEAVY
    private static final int[][] GAP = {
            /* leader LIGHT  */ {1, 1, 1},
            /* leader MEDIUM */ {2, 1, 1},
            /* leader HEAVY  */ {3, 2, 2},
    };

    private SeparationRules() {
    }

    public static int gapMinutes(WakeCategory leader, WakeCategory follower) {
        return GAP[leader.ordinal()][follower.ordinal()];
    }
}

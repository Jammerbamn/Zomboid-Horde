package com.jammerbam.zomboid.ai.navigation;

/** Pure scoring rules for crowd-aware shared-field exits. */
final class CrowdSteeringPolicy {
    private static final int STATIONARY_WEIGHT = 16;
    private static final int RESERVATION_WEIGHT = 8;

    private CrowdSteeringPolicy() {
    }

    static boolean qualifies(int cohortSize, int configuredMinimum) {
        return cohortSize >= Math.max(2, configuredMinimum);
    }

    static int score(int stationaryOrOtherOccupants, int reservations,
                     int turnPenalty) {
        return Math.max(0, stationaryOrOtherOccupants) * STATIONARY_WEIGHT
            + Math.max(0, reservations) * RESERVATION_WEIGHT
            + Math.max(0, turnPenalty);
    }
}

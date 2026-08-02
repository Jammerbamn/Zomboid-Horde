package com.jammerbam.zomboid.behavior;

import java.util.UUID;

/** Stable per-zombie, per-event hearing decision shared by both sound simulations. */
final class SoundDetectionRoll {
    private SoundDetectionRoll() {
    }

    static boolean accepts(UUID zombieId, long eventId, double chancePercent) {
        if (chancePercent <= 0.0D) {
            return false;
        }
        if (chancePercent >= 100.0D) {
            return true;
        }

        long value = mix(eventId ^ zombieId.getMostSignificantBits());
        value = mix(value ^ zombieId.getLeastSignificantBits());
        double rollPercent = (value >>> 11) * 0x1.0p-53 * 100.0D;
        return rollPercent < chancePercent;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        return value ^ value >>> 33;
    }
}

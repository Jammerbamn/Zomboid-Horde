package com.jammerbam.zomboid.performance;

/** Converts server health into bounded AI work without changing active paths. */
public final class AiWorkBudgetPolicy {
    private AiWorkBudgetPolicy() {
    }

    public static int pursuitPathCalculations(double ticksPerSecond,
                                              int minimum,
                                              int maximum) {
        int boundedMaximum = Math.max(1, maximum);
        int boundedMinimum = Math.max(1, Math.min(minimum, boundedMaximum));
        double fraction;
        if (ticksPerSecond >= 19.5D) {
            fraction = 1.0D;
        } else if (ticksPerSecond >= 18.0D) {
            fraction = 0.75D;
        } else if (ticksPerSecond >= 15.0D) {
            fraction = 0.5D;
        } else {
            fraction = 0.25D;
        }
        return Math.max(boundedMinimum,
            Math.min(boundedMaximum, (int) Math.floor(boundedMaximum * fraction)));
    }
}

package com.jammerbam.zomboid.performance;

/** Latest server TPS sample received by this client. */
public final class ClientTpsState {
    private static volatile double ticksPerSecond = 20.0D;

    private ClientTpsState() {
    }

    public static double getTicksPerSecond() {
        return ticksPerSecond;
    }

    public static void update(double value) {
        if (!Double.isNaN(value) && !Double.isInfinite(value)) {
            ticksPerSecond = Math.max(0.0D, Math.min(20.0D, value));
        }
    }

    public static void reset() {
        ticksPerSecond = 20.0D;
    }
}

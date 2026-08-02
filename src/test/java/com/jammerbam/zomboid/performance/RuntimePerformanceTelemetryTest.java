package com.jammerbam.zomboid.performance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimePerformanceTelemetryTest {
    @Test
    public void separatesTrackedAndUnattributedTickTime() {
        RuntimePerformanceTelemetry.Metrics metrics =
            new RuntimePerformanceTelemetry.Metrics(4);
        metrics.record(PerformancePhase.ZOMBIE_BEHAVIOR, 8_000_000L);
        metrics.record(PerformancePhase.ZOMBIE_BEHAVIOR, 2_000_000L);
        metrics.record(PerformancePhase.SOUND_SIMULATION, 5_000_000L);

        assertTrue(metrics.hasCurrentSamples());
        metrics.finishTick(100_000_000L);

        assertEquals(15_000_000L, metrics.getLastTrackedNanos());
        assertEquals(85_000_000L, metrics.getLastUnattributedNanos());
        assertFalse(metrics.hasCurrentSamples());
    }

    @Test
    public void calculatesWindowPercentilesAndResetsOnlyTheWindow() {
        RuntimePerformanceTelemetry.Metrics metrics =
            new RuntimePerformanceTelemetry.Metrics(4);
        long[] ticks = new long[]{
            40_000_000L, 50_000_000L, 100_000_000L, 200_000_000L
        };
        for (long tick : ticks) {
            metrics.record(PerformancePhase.SHARED_NAVIGATION, 1_000_000L);
            metrics.finishTick(tick);
        }

        assertEquals(100_000_000L, metrics.getIntervalPercentile(0.75D));
        assertEquals(200_000_000L, metrics.getIntervalPercentile(0.99D));
        assertEquals(97_500_000L, metrics.getIntervalAverageServerNanos());
        assertEquals(4L, metrics.getLifetimeTickCount());

        metrics.resetInterval();
        assertEquals(0L, metrics.getIntervalTickCount());
        assertEquals(4L, metrics.getLifetimeTickCount());
        assertEquals(4_000_000L, metrics.getLifetimeTrackedNanos());
    }

    @Test
    public void outsideTickCallbacksDoNotPolluteTheNextTick() {
        RuntimePerformanceTelemetry.Metrics metrics =
            new RuntimePerformanceTelemetry.Metrics(2);
        metrics.recordOutsideTick(PerformancePhase.WORLD_LOAD_CALLBACK, 25_000_000L);

        assertTrue(metrics.hasLifetimeSamples());
        assertFalse(metrics.hasCurrentSamples());
        metrics.record(PerformancePhase.SOUND_SIMULATION, 1_000_000L);
        metrics.finishTick(10_000_000L);

        assertEquals(1_000_000L, metrics.getLastTrackedNanos());
        assertEquals(9_000_000L, metrics.getLastUnattributedNanos());
    }
}

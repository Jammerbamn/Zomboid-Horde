package com.jammerbam.zomboid.performance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AutomaticRepathTelemetryTest {
    @Test
    public void resolvesVanillaNavigatorClassificationFields() {
        assertTrue(AutomaticRepathTelemetry.isClassificationAvailable());
    }

    @Test
    public void separatesTriggersAndBuildAttempts() {
        AutomaticRepathTelemetry.Stats stats = new AutomaticRepathTelemetry.Stats();
        stats.record(
            AutomaticRepathTelemetry.Trigger.BLOCK_CHANGE_IMMEDIATE,
            "managed/wandering", true, 2_000_000L
        );
        stats.record(
            AutomaticRepathTelemetry.Trigger.ENTITY_TICK_DEFERRED,
            "managed/wandering", false, 1_000_000L
        );

        assertEquals(2L, stats.totalCalls());
        String summary = stats.format();
        assertTrue(summary.contains(
            "blockChangeImmediate/managed/wandering=1 calls/1 buildAttempts"
        ));
        assertTrue(summary.contains(
            "entityTickDeferred/managed/wandering=1 calls/0 buildAttempts/1 deferred"
        ));
    }
}

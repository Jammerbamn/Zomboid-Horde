package com.jammerbam.zomboid.performance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VanillaPathRequestTelemetryTest {
    @Test
    public void accumulatesCallsSuccessesAndDurationsBySource() {
        VanillaPathRequestTelemetry.Stats stats = new VanillaPathRequestTelemetry.Stats();

        stats.record(VanillaPathRequestSource.PERSONAL_WANDER, true, 12L);
        stats.record(VanillaPathRequestSource.PERSONAL_WANDER, false, 30L);
        stats.record(VanillaPathRequestSource.RETURN_TO_ANCHOR, false, 11L);
        stats.record(VanillaPathRequestSource.ALERT_LEADER, true, 7L);

        assertEquals(2L, stats.calls(VanillaPathRequestSource.PERSONAL_WANDER));
        assertEquals(1L, stats.successes(VanillaPathRequestSource.PERSONAL_WANDER));
        assertEquals(42L,
            stats.totalNanoseconds(VanillaPathRequestSource.PERSONAL_WANDER));
        assertEquals(30L,
            stats.maximumNanoseconds(VanillaPathRequestSource.PERSONAL_WANDER));
        assertEquals(1L, stats.calls(VanillaPathRequestSource.RETURN_TO_ANCHOR));
        assertEquals(0L, stats.successes(VanillaPathRequestSource.RETURN_TO_ANCHOR));
        assertEquals(4L, stats.totalCalls());
    }
}

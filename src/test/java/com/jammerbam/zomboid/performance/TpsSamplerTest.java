package com.jammerbam.zomboid.performance;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TpsSamplerTest {
    @Test
    public void reportsVanillaCeilingForHealthyTicks() {
        TpsSampler sampler = new TpsSampler(100);
        for (int i = 0; i < 100; i++) {
            sampler.recordTick(40_000_000L);
        }
        assertEquals(20.0D, sampler.getTicksPerSecond(), 0.001D);
    }

    @Test
    public void reportsRollingAverageForSlowTicks() {
        TpsSampler sampler = new TpsSampler(100);
        for (int i = 0; i < 100; i++) {
            sampler.recordTick(100_000_000L);
        }
        assertEquals(10.0D, sampler.getTicksPerSecond(), 0.001D);

        sampler.reset();
        assertEquals(20.0D, sampler.getTicksPerSecond(), 0.001D);
    }
}

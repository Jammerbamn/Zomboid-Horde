package com.jammerbam.zomboid.performance;

/** Rolling server tick-duration sampler with a vanilla 20 TPS ceiling. */
public final class TpsSampler {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0D;
    private final long[] samples;
    private int nextIndex;
    private int sampleCount;
    private long totalNanos;

    public TpsSampler(int sampleWindow) {
        samples = new long[Math.max(1, sampleWindow)];
    }

    public void recordTick(long elapsedNanos) {
        long bounded = Math.max(1L, elapsedNanos);
        if (sampleCount == samples.length) {
            totalNanos -= samples[nextIndex];
        } else {
            sampleCount++;
        }
        samples[nextIndex] = bounded;
        totalNanos += bounded;
        nextIndex = (nextIndex + 1) % samples.length;
    }

    public double getTicksPerSecond() {
        if (sampleCount == 0) {
            return 20.0D;
        }
        double averageTickNanos = totalNanos / (double) sampleCount;
        return Math.min(20.0D, NANOS_PER_SECOND / averageTickNanos);
    }

    public void reset() {
        nextIndex = 0;
        sampleCount = 0;
        totalNanos = 0L;
        java.util.Arrays.fill(samples, 0L);
    }
}

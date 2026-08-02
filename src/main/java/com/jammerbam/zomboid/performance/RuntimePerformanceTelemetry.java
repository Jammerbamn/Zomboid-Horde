package com.jammerbam.zomboid.performance;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.config.ModConfig;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/** Correlates low-overhead Zomboid phase timers with the complete Forge server tick. */
public final class RuntimePerformanceTelemetry {
    private static final Map<World, WorldState> WORLD_STATES = new WeakHashMap<>();
    private static long serverTickSequence;
    private static boolean serverTickActive;

    private RuntimePerformanceTelemetry() {
    }

    public static long begin() {
        return ModConfig.enablePerformanceTelemetry ? System.nanoTime() : 0L;
    }

    public static void beginServerTick() {
        serverTickActive = ModConfig.enablePerformanceTelemetry;
    }

    public static long recordElapsed(World world, PerformancePhase phase, long startedAt) {
        if (startedAt == 0L || !ModConfig.enablePerformanceTelemetry) {
            return 0L;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startedAt);
        record(world, phase, elapsed);
        return elapsed;
    }

    public static void record(World world, PerformancePhase phase, long elapsedNanoseconds) {
        if (!ModConfig.enablePerformanceTelemetry || world == null || world.isRemote) {
            return;
        }
        WorldState state = WORLD_STATES.computeIfAbsent(
            world,
            ignored -> new WorldState(Math.max(1, ModConfig.performanceSummaryIntervalTicks))
        );
        if (serverTickActive) {
            state.metrics.record(phase, Math.max(0L, elapsedNanoseconds));
        } else {
            state.metrics.recordOutsideTick(phase, Math.max(0L, elapsedNanoseconds));
        }
    }

    /** Called once from the final registered server-tick listener. */
    public static void endServerTick(long elapsedNanoseconds) {
        if (!ModConfig.enablePerformanceTelemetry) {
            serverTickActive = false;
            return;
        }
        serverTickSequence++;
        long boundedElapsed = Math.max(0L, elapsedNanoseconds);
        for (Map.Entry<World, WorldState> entry : WORLD_STATES.entrySet()) {
            World world = entry.getKey();
            WorldState state = entry.getValue();
            if (world == null || !state.metrics.hasCurrentSamples()) {
                continue;
            }

            state.metrics.finishTick(boundedElapsed);
            long stallThreshold = millisToNanos(ModConfig.performanceStallThresholdMillis);
            if (boundedElapsed >= stallThreshold) {
                state.intervalStalls++;
                state.lifetimeStalls++;
                int cooldown = Math.max(0, ModConfig.performanceStallLogCooldownTicks);
                if (state.lastStallLogTick == Long.MIN_VALUE
                    || serverTickSequence - state.lastStallLogTick >= cooldown) {
                    logStall(world, state, boundedElapsed);
                    state.lastStallLogTick = serverTickSequence;
                    state.suppressedStalls = 0L;
                } else {
                    state.suppressedStalls++;
                }
            }

            if (state.metrics.getIntervalTickCount()
                >= Math.max(1, ModConfig.performanceSummaryIntervalTicks)) {
                logSummary(world, state);
                state.metrics.resetInterval();
                state.intervalStalls = 0L;
            }
        }
        serverTickActive = false;
    }

    public static void clear(World world) {
        WorldState state = WORLD_STATES.remove(world);
        if (state != null && state.metrics.hasLifetimeSamples()) {
            logClosed(world, state);
        }
    }

    public static void reset() {
        WORLD_STATES.clear();
        serverTickSequence = 0L;
        serverTickActive = false;
    }

    private static void logStall(World world, WorldState state, long serverNanos) {
        Metrics metrics = state.metrics;
        StringBuilder message = new StringBuilder(320);
        message.append("Performance stall in dimension ")
            .append(world.provider.getDimension())
            .append(" at world tick ").append(world.getTotalWorldTime())
            .append(": server=").append(millis(serverNanos)).append(" ms")
            .append(", tracked=").append(millis(metrics.getLastTrackedNanos()))
            .append(" ms, unattributed=")
            .append(millis(metrics.getLastUnattributedNanos())).append(" ms");
        appendLastTickPhases(message, metrics);
        if (state.suppressedStalls > 0L) {
            message.append("; priorSuppressed=").append(state.suppressedStalls);
        }
        Zomboid.logger.warn(message.toString());
    }

    private static void logSummary(World world, WorldState state) {
        Metrics metrics = state.metrics;
        StringBuilder message = new StringBuilder(420);
        message.append("Performance summary for dimension ")
            .append(world.provider.getDimension()).append(" over ")
            .append(metrics.getIntervalTickCount()).append(" ticks: server avg=")
            .append(millis(metrics.getIntervalAverageServerNanos())).append(" ms")
            .append(", p95=").append(millis(metrics.getIntervalPercentile(0.95D)))
            .append(" ms, p99=").append(millis(metrics.getIntervalPercentile(0.99D)))
            .append(" ms, max=").append(millis(metrics.getIntervalMaximumServerNanos()))
            .append(" ms, tracked avg=")
            .append(millis(metrics.getIntervalAverageTrackedNanos())).append(" ms")
            .append(", stalls=").append(state.intervalStalls);
        appendIntervalPhases(message, metrics);
        Zomboid.logger.info(message.toString());
    }

    private static void logClosed(World world, WorldState state) {
        Metrics metrics = state.metrics;
        StringBuilder message = new StringBuilder(420);
        message.append("Runtime performance closed for dimension ")
            .append(world.provider.getDimension()).append(": ticks=")
            .append(metrics.getLifetimeTickCount()).append(", server avg=")
            .append(millis(metrics.getLifetimeAverageServerNanos())).append(" ms")
            .append(", max=").append(millis(metrics.getLifetimeMaximumServerNanos()))
            .append(" ms, tracked total=")
            .append(millis(metrics.getLifetimeTrackedNanos())).append(" ms")
            .append(", unattributed total=")
            .append(millis(metrics.getLifetimeUnattributedNanos())).append(" ms")
            .append(", stalls=").append(state.lifetimeStalls);
        appendLifetimePhases(message, metrics);
        appendOutsideTickPhases(message, metrics);
        Zomboid.logger.info(message.toString());
    }

    private static void appendLastTickPhases(StringBuilder message, Metrics metrics) {
        message.append("; phases:");
        for (PerformancePhase phase : PerformancePhase.values()) {
            int index = phase.ordinal();
            message.append(' ').append(phase.getLabel()).append('=')
                .append(millis(metrics.lastPhaseNanos[index])).append("ms/")
                .append(metrics.lastPhaseCalls[index]);
        }
    }

    private static void appendIntervalPhases(StringBuilder message, Metrics metrics) {
        message.append("; phases:");
        for (PerformancePhase phase : PerformancePhase.values()) {
            int index = phase.ordinal();
            message.append(' ').append(phase.getLabel()).append('=')
                .append(millis(metrics.intervalPhaseNanos[index])).append("ms/")
                .append(metrics.intervalPhaseCalls[index]).append(" calls, maxTick=")
                .append(millis(metrics.intervalMaximumPhaseTickNanos[index])).append("ms;");
        }
    }

    private static void appendLifetimePhases(StringBuilder message, Metrics metrics) {
        message.append("; phases:");
        for (PerformancePhase phase : PerformancePhase.values()) {
            int index = phase.ordinal();
            message.append(' ').append(phase.getLabel()).append('=')
                .append(millis(metrics.lifetimePhaseNanos[index])).append("ms/")
                .append(metrics.lifetimePhaseCalls[index]).append(" calls, maxOp=")
                .append(millis(metrics.lifetimeMaximumOperationNanos[index])).append("ms;");
        }
    }

    private static void appendOutsideTickPhases(StringBuilder message, Metrics metrics) {
        message.append("; outsideServerTick:");
        for (PerformancePhase phase : PerformancePhase.values()) {
            int index = phase.ordinal();
            message.append(' ').append(phase.getLabel()).append('=')
                .append(millis(metrics.outsideTickPhaseNanos[index])).append("ms/")
                .append(metrics.outsideTickPhaseCalls[index]).append(" calls;");
        }
    }

    private static long millisToNanos(double value) {
        return (long) (Math.max(0.0D, value) * 1_000_000.0D);
    }

    private static String millis(long nanoseconds) {
        return String.format(java.util.Locale.ROOT, "%.2f", nanoseconds / 1_000_000.0D);
    }

    private static final class WorldState {
        private final Metrics metrics;
        private long intervalStalls;
        private long lifetimeStalls;
        private long lastStallLogTick = Long.MIN_VALUE;
        private long suppressedStalls;

        private WorldState(int summaryInterval) {
            metrics = new Metrics(summaryInterval);
        }
    }

    /** Package-private pure accumulator for unit tests. */
    static final class Metrics {
        private final long[] currentPhaseNanos = new long[PerformancePhase.values().length];
        private final long[] currentPhaseCalls = new long[PerformancePhase.values().length];
        private final long[] lastPhaseNanos = new long[PerformancePhase.values().length];
        private final long[] lastPhaseCalls = new long[PerformancePhase.values().length];
        private final long[] intervalPhaseNanos = new long[PerformancePhase.values().length];
        private final long[] intervalPhaseCalls = new long[PerformancePhase.values().length];
        private final long[] intervalMaximumPhaseTickNanos =
            new long[PerformancePhase.values().length];
        private final long[] lifetimePhaseNanos = new long[PerformancePhase.values().length];
        private final long[] lifetimePhaseCalls = new long[PerformancePhase.values().length];
        private final long[] lifetimeMaximumOperationNanos =
            new long[PerformancePhase.values().length];
        private final long[] outsideTickPhaseNanos =
            new long[PerformancePhase.values().length];
        private final long[] outsideTickPhaseCalls =
            new long[PerformancePhase.values().length];
        private final long[] intervalServerDurations;
        private int intervalDurationCount;
        private long intervalTickCount;
        private long intervalServerNanos;
        private long intervalTrackedNanos;
        private long intervalMaximumServerNanos;
        private long lifetimeTickCount;
        private long lifetimeServerNanos;
        private long lifetimeTrackedNanos;
        private long lifetimeUnattributedNanos;
        private long lifetimeMaximumServerNanos;
        private long lastTrackedNanos;
        private long lastUnattributedNanos;

        Metrics(int summaryInterval) {
            intervalServerDurations = new long[Math.max(1, summaryInterval)];
        }

        void record(PerformancePhase phase, long elapsedNanoseconds) {
            int index = phase.ordinal();
            currentPhaseNanos[index] += elapsedNanoseconds;
            currentPhaseCalls[index]++;
            intervalPhaseNanos[index] += elapsedNanoseconds;
            intervalPhaseCalls[index]++;
            lifetimePhaseNanos[index] += elapsedNanoseconds;
            lifetimePhaseCalls[index]++;
            lifetimeMaximumOperationNanos[index] = Math.max(
                lifetimeMaximumOperationNanos[index], elapsedNanoseconds
            );
        }

        void recordOutsideTick(PerformancePhase phase, long elapsedNanoseconds) {
            int index = phase.ordinal();
            outsideTickPhaseNanos[index] += elapsedNanoseconds;
            outsideTickPhaseCalls[index]++;
        }

        boolean hasLifetimeSamples() {
            if (lifetimeTickCount > 0L) {
                return true;
            }
            for (long calls : outsideTickPhaseCalls) {
                if (calls > 0L) {
                    return true;
                }
            }
            return false;
        }

        boolean hasCurrentSamples() {
            for (long calls : currentPhaseCalls) {
                if (calls > 0L) {
                    return true;
                }
            }
            return false;
        }

        void finishTick(long serverNanoseconds) {
            lastTrackedNanos = 0L;
            for (int i = 0; i < currentPhaseNanos.length; i++) {
                lastPhaseNanos[i] = currentPhaseNanos[i];
                lastPhaseCalls[i] = currentPhaseCalls[i];
                lastTrackedNanos += currentPhaseNanos[i];
                intervalMaximumPhaseTickNanos[i] = Math.max(
                    intervalMaximumPhaseTickNanos[i], currentPhaseNanos[i]
                );
                currentPhaseNanos[i] = 0L;
                currentPhaseCalls[i] = 0L;
            }
            lastUnattributedNanos = Math.max(0L, serverNanoseconds - lastTrackedNanos);
            intervalTickCount++;
            intervalServerNanos += serverNanoseconds;
            intervalTrackedNanos += lastTrackedNanos;
            intervalMaximumServerNanos = Math.max(
                intervalMaximumServerNanos, serverNanoseconds
            );
            if (intervalDurationCount < intervalServerDurations.length) {
                intervalServerDurations[intervalDurationCount++] = serverNanoseconds;
            }
            lifetimeTickCount++;
            lifetimeServerNanos += serverNanoseconds;
            lifetimeTrackedNanos += lastTrackedNanos;
            lifetimeUnattributedNanos += lastUnattributedNanos;
            lifetimeMaximumServerNanos = Math.max(
                lifetimeMaximumServerNanos, serverNanoseconds
            );
        }

        void resetInterval() {
            Arrays.fill(intervalPhaseNanos, 0L);
            Arrays.fill(intervalPhaseCalls, 0L);
            Arrays.fill(intervalMaximumPhaseTickNanos, 0L);
            Arrays.fill(intervalServerDurations, 0L);
            intervalDurationCount = 0;
            intervalTickCount = 0L;
            intervalServerNanos = 0L;
            intervalTrackedNanos = 0L;
            intervalMaximumServerNanos = 0L;
        }

        long getIntervalPercentile(double percentile) {
            if (intervalDurationCount == 0) {
                return 0L;
            }
            long[] sorted = Arrays.copyOf(intervalServerDurations, intervalDurationCount);
            Arrays.sort(sorted);
            int index = (int) Math.ceil(
                Math.max(0.0D, Math.min(1.0D, percentile)) * sorted.length
            ) - 1;
            return sorted[Math.max(0, index)];
        }

        long getLastTrackedNanos() { return lastTrackedNanos; }
        long getLastUnattributedNanos() { return lastUnattributedNanos; }
        long getIntervalTickCount() { return intervalTickCount; }
        long getIntervalAverageServerNanos() {
            return intervalTickCount == 0L ? 0L : intervalServerNanos / intervalTickCount;
        }
        long getIntervalAverageTrackedNanos() {
            return intervalTickCount == 0L ? 0L : intervalTrackedNanos / intervalTickCount;
        }
        long getIntervalMaximumServerNanos() { return intervalMaximumServerNanos; }
        long getLifetimeTickCount() { return lifetimeTickCount; }
        long getLifetimeAverageServerNanos() {
            return lifetimeTickCount == 0L ? 0L : lifetimeServerNanos / lifetimeTickCount;
        }
        long getLifetimeMaximumServerNanos() { return lifetimeMaximumServerNanos; }
        long getLifetimeTrackedNanos() { return lifetimeTrackedNanos; }
        long getLifetimeUnattributedNanos() { return lifetimeUnattributedNanos; }
    }
}

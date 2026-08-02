package com.jammerbam.zomboid.performance;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.config.ModConfig;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Exact source-aware timings around synchronous vanilla navigator route requests. */
public final class VanillaPathRequestTelemetry {
    private static final Map<World, WorldStats> WORLD_STATS = new WeakHashMap<>();
    private static long intervalTicks;

    private VanillaPathRequestTelemetry() {
    }

    public static RequestResult run(World world, VanillaPathRequestSource source,
                                    BooleanSupplier request) {
        VanillaPathRequestSource previous = VanillaEntityWorkSampler.beginPathRequest(source);
        long startedAt = System.nanoTime();
        boolean successful = false;
        long elapsed;
        try {
            successful = request.getAsBoolean();
        } finally {
            elapsed = Math.max(0L, System.nanoTime() - startedAt);
            VanillaEntityWorkSampler.endPathRequest(previous);
            if (world != null && !world.isRemote && ModConfig.enablePerformanceTelemetry) {
                WorldStats stats = WORLD_STATS.computeIfAbsent(
                    world, ignored -> new WorldStats()
                );
                stats.lifetime.record(source, successful, elapsed);
                stats.interval.record(source, successful, elapsed);
                if (source != VanillaPathRequestSource.PLAYER_PURSUIT_FALLBACK) {
                    RuntimePerformanceTelemetry.record(
                        world, PerformancePhase.VANILLA_PATH_REQUEST, elapsed
                    );
                }
            }
        }
        return new RequestResult(successful, elapsed);
    }

    public static void endServerTick() {
        if (!ModConfig.enablePerformanceTelemetry) {
            return;
        }
        intervalTicks++;
        if (intervalTicks < Math.max(1, ModConfig.performanceSummaryIntervalTicks)) {
            return;
        }
        intervalTicks = 0L;
        List<String> summaries = new ArrayList<>();
        for (Map.Entry<World, WorldStats> entry : WORLD_STATS.entrySet()) {
            Stats interval = entry.getValue().interval;
            if (interval.totalCalls() > 0L) {
                summaries.add("dimension " + entry.getKey().provider.getDimension()
                    + ": " + interval.format());
            }
            entry.getValue().interval = new Stats();
        }
        for (String summary : summaries) {
            Zomboid.logger.info("Vanilla path request summary: {}", summary);
        }
    }

    public static void clear(World world) {
        WorldStats stats = WORLD_STATS.remove(world);
        if (stats != null && stats.lifetime.totalCalls() > 0L) {
            Zomboid.logger.info(
                "Vanilla path requests closed for dimension {}: {}",
                world.provider.getDimension(), stats.lifetime.format()
            );
        }
    }

    public static void reset() {
        WORLD_STATS.clear();
        intervalTicks = 0L;
    }

    public static final class RequestResult {
        private final boolean successful;
        private final long elapsedNanoseconds;

        private RequestResult(boolean successful, long elapsedNanoseconds) {
            this.successful = successful;
            this.elapsedNanoseconds = elapsedNanoseconds;
        }

        public boolean wasSuccessful() {
            return successful;
        }

        public long getElapsedNanoseconds() {
            return elapsedNanoseconds;
        }
    }

    static final class Stats {
        private final long[] calls = new long[VanillaPathRequestSource.values().length];
        private final long[] successes = new long[VanillaPathRequestSource.values().length];
        private final long[] totalNanoseconds =
            new long[VanillaPathRequestSource.values().length];
        private final long[] maximumNanoseconds =
            new long[VanillaPathRequestSource.values().length];

        void record(VanillaPathRequestSource source, boolean successful,
                    long elapsedNanoseconds) {
            int index = source.ordinal();
            long elapsed = Math.max(0L, elapsedNanoseconds);
            calls[index]++;
            if (successful) {
                successes[index]++;
            }
            totalNanoseconds[index] += elapsed;
            maximumNanoseconds[index] = Math.max(maximumNanoseconds[index], elapsed);
        }

        long calls(VanillaPathRequestSource source) {
            return calls[source.ordinal()];
        }

        long successes(VanillaPathRequestSource source) {
            return successes[source.ordinal()];
        }

        long totalNanoseconds(VanillaPathRequestSource source) {
            return totalNanoseconds[source.ordinal()];
        }

        long maximumNanoseconds(VanillaPathRequestSource source) {
            return maximumNanoseconds[source.ordinal()];
        }

        long totalCalls() {
            long total = 0L;
            for (long count : calls) {
                total += count;
            }
            return total;
        }

        String format() {
            StringBuilder result = new StringBuilder(320);
            for (VanillaPathRequestSource source : VanillaPathRequestSource.values()) {
                if (result.length() > 0) {
                    result.append("; ");
                }
                int index = source.ordinal();
                double totalMillis = totalNanoseconds[index] / 1_000_000.0D;
                double averageMillis = calls[index] == 0L
                    ? 0.0D : totalMillis / calls[index];
                result.append(source.getLabel()).append('=')
                    .append(calls[index]).append(" calls/")
                    .append(successes[index]).append(" successful/")
                    .append(String.format(Locale.ROOT, "%.3fms total/%.3fms avg/%.3fms max",
                        totalMillis, averageMillis,
                        maximumNanoseconds[index] / 1_000_000.0D));
            }
            return result.toString();
        }
    }

    private static final class WorldStats {
        private final Stats lifetime = new Stats();
        private Stats interval = new Stats();
    }
}

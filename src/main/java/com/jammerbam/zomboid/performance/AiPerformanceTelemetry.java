package com.jammerbam.zomboid.performance;

import com.jammerbam.zomboid.Zomboid;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/** Low-overhead counters for AI work that is otherwise invisible in Forge logs. */
public final class AiPerformanceTelemetry {
    private static final Map<World, Metrics> WORLD_METRICS = new WeakHashMap<>();

    private AiPerformanceTelemetry() {
    }

    public static void recordPerceptionScan(World world, long elapsedNanoseconds,
                                            int candidates, int rangeRejected,
                                            int coneRejected, int chanceRejected,
                                            int lineOfSightChecks, int visible) {
        Metrics metrics = metrics(world);
        metrics.perceptionScans++;
        metrics.perceptionNanoseconds += elapsedNanoseconds;
        metrics.perceptionCandidates += candidates;
        metrics.rangeRejected += rangeRejected;
        metrics.coneRejected += coneRejected;
        metrics.chanceRejected += chanceRejected;
        metrics.lineOfSightChecks += lineOfSightChecks;
        metrics.visibleLineOfSight += visible;
    }

    public static void recordPursuitFallbackRequest(World world, boolean acquired) {
        Metrics metrics = metrics(world);
        metrics.fallbackRequests++;
        if (acquired) {
            metrics.fallbackGranted++;
        } else {
            metrics.fallbackDenied++;
        }
    }

    public static void recordPursuitFallbackPath(World world, boolean pathFound,
                                                 long elapsedNanoseconds) {
        Metrics metrics = metrics(world);
        metrics.fallbackPathBuilds++;
        if (pathFound) {
            metrics.fallbackPathsFound++;
        }
        metrics.fallbackPathNanoseconds += elapsedNanoseconds;
        metrics.maximumFallbackPathNanoseconds = Math.max(
            metrics.maximumFallbackPathNanoseconds, elapsedNanoseconds
        );
    }

    public static void clear(World world) {
        Metrics metrics = WORLD_METRICS.remove(world);
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        double perceptionMillis = metrics.perceptionNanoseconds / 1_000_000.0D;
        double nanosecondsPerScan = metrics.perceptionScans == 0L
            ? 0.0D : (double) metrics.perceptionNanoseconds / metrics.perceptionScans;
        double fallbackMillis = metrics.fallbackPathNanoseconds / 1_000_000.0D;
        double microsecondsPerPath = metrics.fallbackPathBuilds == 0L
            ? 0.0D : metrics.fallbackPathNanoseconds
                / 1_000.0D / metrics.fallbackPathBuilds;
        double maximumFallbackMicroseconds =
            metrics.maximumFallbackPathNanoseconds / 1_000.0D;
        Zomboid.logger.info(
            "AI telemetry closed for dimension {}: perception: {} scans, "
                + "{} candidates, {} range rejected, {} cone rejected, "
                + "{} chance rejected before LOS, {} LOS checks, {} visible, "
                + "{} ms ({} ns/scan); vanilla pursuit fallback: {} requests, "
                + "{} granted, {} denied, {} path builds, {} found, {} ms "
                + "({} us/build, {} us max).",
            world.provider.getDimension(),
            metrics.perceptionScans,
            metrics.perceptionCandidates,
            metrics.rangeRejected,
            metrics.coneRejected,
            metrics.chanceRejected,
            metrics.lineOfSightChecks,
            metrics.visibleLineOfSight,
            format(perceptionMillis, 2),
            format(nanosecondsPerScan, 1),
            metrics.fallbackRequests,
            metrics.fallbackGranted,
            metrics.fallbackDenied,
            metrics.fallbackPathBuilds,
            metrics.fallbackPathsFound,
            format(fallbackMillis, 2),
            format(microsecondsPerPath, 1),
            format(maximumFallbackMicroseconds, 1)
        );
    }

    private static Metrics metrics(World world) {
        return WORLD_METRICS.computeIfAbsent(world, ignored -> new Metrics());
    }

    private static String format(double value, int decimals) {
        return String.format(
            java.util.Locale.ROOT, decimals == 1 ? "%.1f" : "%.2f", value
        );
    }

    private static final class Metrics {
        private long perceptionScans;
        private long perceptionNanoseconds;
        private long perceptionCandidates;
        private long rangeRejected;
        private long coneRejected;
        private long chanceRejected;
        private long lineOfSightChecks;
        private long visibleLineOfSight;
        private long fallbackRequests;
        private long fallbackGranted;
        private long fallbackDenied;
        private long fallbackPathBuilds;
        private long fallbackPathsFound;
        private long fallbackPathNanoseconds;
        private long maximumFallbackPathNanoseconds;

        private boolean isEmpty() {
            return perceptionScans == 0L && fallbackRequests == 0L;
        }
    }
}

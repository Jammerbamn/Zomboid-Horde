package com.jammerbam.zomboid.performance;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.ai.brain.BrainState;
import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.population.PopulationTags;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Exact timings for vanilla's block-change and entity-tick automatic repaths. */
public final class AutomaticRepathTelemetry {
    private static final Field ENTITY = findField("entity", "field_75515_a");
    private static final Field TARGET_POS = findField("targetPos", "field_188564_r");
    private static final Field LAST_TIME_UPDATED =
        findField("lastTimeUpdated", "field_188563_q");
    private static final Map<World, WorldStats> WORLD_STATS = new WeakHashMap<>();
    private static long intervalTicks;
    private static boolean reflectionFailureLogged;

    private AutomaticRepathTelemetry() {
    }

    /** Called from the transformed PathWorldListener block-update path. */
    public static void runImmediate(PathNavigate navigator) {
        run(navigator, Trigger.BLOCK_CHANGE_IMMEDIATE);
    }

    /** Called from the transformed PathNavigate entity-tick path. */
    public static void runDeferred(PathNavigate navigator) {
        run(navigator, Trigger.ENTITY_TICK_DEFERRED);
    }

    private static void run(PathNavigate navigator, Trigger trigger) {
        if (!ModConfig.enablePerformanceTelemetry) {
            navigator.updatePath();
            return;
        }

        EntityLiving entity = entity(navigator);
        World world = entity == null ? null : entity.world;
        boolean serverWorld = world != null && !world.isRemote;
        boolean buildAttempted = serverWorld && willBuild(navigator, world);
        long startedAt = serverWorld ? System.nanoTime() : 0L;
        try {
            navigator.updatePath();
        } finally {
            if (serverWorld) {
                long elapsed = Math.max(0L, System.nanoTime() - startedAt);
                String group = group(entity);
                WorldStats stats = WORLD_STATS.computeIfAbsent(
                    world, ignored -> new WorldStats()
                );
                stats.lifetime.record(trigger, group, buildAttempted, elapsed);
                stats.interval.record(trigger, group, buildAttempted, elapsed);
            }
        }
    }

    private static boolean willBuild(PathNavigate navigator, World world) {
        if (TARGET_POS == null || LAST_TIME_UPDATED == null) {
            return false;
        }
        try {
            BlockPos target = (BlockPos) TARGET_POS.get(navigator);
            long lastUpdated = LAST_TIME_UPDATED.getLong(navigator);
            return target != null && world.getTotalWorldTime() - lastUpdated > 20L;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logReflectionFailure(exception);
            return false;
        }
    }

    private static EntityLiving entity(PathNavigate navigator) {
        if (ENTITY == null) {
            return null;
        }
        try {
            Object value = ENTITY.get(navigator);
            return value instanceof EntityLiving ? (EntityLiving) value : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logReflectionFailure(exception);
            return null;
        }
    }

    private static String group(EntityLiving entity) {
        boolean managed = entity != null && PopulationTags.isManaged(entity);
        String state = "nonZombie";
        if (entity instanceof EntityZombie) {
            BrainState brainState = ZombieBrainManager.get((EntityZombie) entity).getState();
            state = brainState.name().toLowerCase(Locale.ROOT);
        }
        return (managed ? "managed/" : "unmanaged/") + state;
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
            Zomboid.logger.info("Automatic vanilla repath summary: {}", summary);
        }
    }

    public static void clear(World world) {
        WorldStats stats = WORLD_STATS.remove(world);
        if (stats != null && stats.lifetime.totalCalls() > 0L) {
            Zomboid.logger.info(
                "Automatic vanilla repaths closed for dimension {}: {}",
                world.provider.getDimension(), stats.lifetime.format()
            );
        }
    }

    public static void reset() {
        WORLD_STATS.clear();
        intervalTicks = 0L;
    }

    static boolean isClassificationAvailable() {
        return ENTITY != null && TARGET_POS != null && LAST_TIME_UPDATED != null;
    }

    private static Field findField(String mcpName, String srgName) {
        for (String name : new String[]{mcpName, srgName}) {
            try {
                Field field = PathNavigate.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the other runtime namespace.
            }
        }
        return null;
    }

    private static void logReflectionFailure(Exception exception) {
        if (!reflectionFailureLogged) {
            reflectionFailureLogged = true;
            Zomboid.logger.warn(
                "Automatic repath timing is active, but rebuild-attempt classification "
                    + "could not read PathNavigate state.", exception
            );
        }
    }

    enum Trigger {
        BLOCK_CHANGE_IMMEDIATE("blockChangeImmediate"),
        ENTITY_TICK_DEFERRED("entityTickDeferred");

        private final String label;

        Trigger(String label) {
            this.label = label;
        }
    }

    static final class Stats {
        private final Map<String, GroupStats> groups = new LinkedHashMap<>();

        void record(Trigger trigger, String group, boolean buildAttempted, long elapsed) {
            String key = trigger.label + '/' + group;
            groups.computeIfAbsent(key, ignored -> new GroupStats())
                .record(buildAttempted, elapsed);
        }

        long totalCalls() {
            long total = 0L;
            for (GroupStats stats : groups.values()) {
                total += stats.calls;
            }
            return total;
        }

        String format() {
            StringBuilder result = new StringBuilder(384);
            for (Map.Entry<String, GroupStats> entry : groups.entrySet()) {
                if (result.length() > 0) {
                    result.append("; ");
                }
                GroupStats stats = entry.getValue();
                double totalMillis = stats.totalNanoseconds / 1_000_000.0D;
                double averageMillis = stats.calls == 0L
                    ? 0.0D : totalMillis / stats.calls;
                double buildMillis = stats.buildNanoseconds / 1_000_000.0D;
                double averageBuildMillis = stats.buildAttempts == 0L
                    ? 0.0D : buildMillis / stats.buildAttempts;
                result.append(entry.getKey()).append('=')
                    .append(stats.calls).append(" calls/")
                    .append(stats.buildAttempts).append(" buildAttempts/")
                    .append(stats.calls - stats.buildAttempts).append(" deferred/")
                    .append(String.format(Locale.ROOT,
                        "%.3fms total/%.3fms avgCall/%.3fms maxCall/"
                            + "%.3fms buildTotal/%.3fms avgBuild/%.3fms maxBuild",
                        totalMillis, averageMillis,
                        stats.maximumNanoseconds / 1_000_000.0D,
                        buildMillis, averageBuildMillis,
                        stats.maximumBuildNanoseconds / 1_000_000.0D));
            }
            return result.toString();
        }
    }

    private static final class GroupStats {
        private long calls;
        private long buildAttempts;
        private long totalNanoseconds;
        private long maximumNanoseconds;
        private long buildNanoseconds;
        private long maximumBuildNanoseconds;

        private void record(boolean buildAttempted, long elapsedNanoseconds) {
            long elapsed = Math.max(0L, elapsedNanoseconds);
            calls++;
            if (buildAttempted) {
                buildAttempts++;
                buildNanoseconds += elapsed;
                maximumBuildNanoseconds = Math.max(maximumBuildNanoseconds, elapsed);
            }
            totalNanoseconds += elapsed;
            maximumNanoseconds = Math.max(maximumNanoseconds, elapsed);
        }
    }

    private static final class WorldStats {
        private final Stats lifetime = new Stats();
        private Stats interval = new Stats();
    }
}

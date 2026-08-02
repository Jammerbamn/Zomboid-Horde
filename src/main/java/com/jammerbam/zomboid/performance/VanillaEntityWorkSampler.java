package com.jammerbam.zomboid.performance;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.config.ModConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Low-frequency statistical profiler for work that remains inside vanilla server ticks. */
public final class VanillaEntityWorkSampler {
    private static final Object LOCK = new Object();
    private static final int MAX_LEAF_FRAMES = 512;
    private static final int REPORTED_LEAF_FRAMES = 5;

    private static final long[] current = new long[Category.values().length];
    private static final long[] interval = new long[Category.values().length];
    private static final long[] lifetime = new long[Category.values().length];
    private static final long[] currentLivingDetails =
        new long[LivingDetail.values().length];
    private static final long[] intervalLivingDetails =
        new long[LivingDetail.values().length];
    private static final long[] lifetimeLivingDetails =
        new long[LivingDetail.values().length];
    private static final long[][] currentPathRequests = newPathRequestMatrix();
    private static final long[][] intervalPathRequests = newPathRequestMatrix();
    private static final long[][] lifetimePathRequests = newPathRequestMatrix();
    private static final Map<String, Long> currentLeafFrames = new HashMap<>();
    private static final Map<String, Long> intervalLeafFrames = new HashMap<>();
    private static final Map<String, Long> lifetimeLeafFrames = new HashMap<>();
    private static final Map<String, Long> currentUnwrappedPathCallers = new HashMap<>();
    private static final Map<String, Long> intervalUnwrappedPathCallers = new HashMap<>();
    private static final Map<String, Long> lifetimeUnwrappedPathCallers = new HashMap<>();

    private static volatile Thread serverThread;
    private static volatile Thread samplerThread;
    private static volatile boolean running;
    private static volatile boolean tickActive;
    private static volatile long activeTickSequence;
    private static volatile VanillaPathRequestSource activePathRequestSource;
    private static long intervalTicks;
    private static long lifetimeTicks;
    private static long lastStallLogTick = Long.MIN_VALUE;
    private static long suppressedStalls;

    private VanillaEntityWorkSampler() {
    }

    public static void beginTick(Thread thread) {
        if (!enabled()) {
            return;
        }
        ensureStarted(thread);
        synchronized (LOCK) {
            Arrays.fill(current, 0L);
            Arrays.fill(currentLivingDetails, 0L);
            clear(currentPathRequests);
            currentLeafFrames.clear();
            currentUnwrappedPathCallers.clear();
            activeTickSequence++;
            tickActive = true;
        }
    }

    public static void endTick(long elapsedNanoseconds) {
        if (!enabled()) {
            return;
        }
        Snapshot stall = null;
        Snapshot summary = null;
        synchronized (LOCK) {
            tickActive = false;
            intervalTicks++;
            lifetimeTicks++;
            add(current, interval);
            add(current, lifetime);
            add(currentLivingDetails, intervalLivingDetails);
            add(currentLivingDetails, lifetimeLivingDetails);
            add(currentPathRequests, intervalPathRequests);
            add(currentPathRequests, lifetimePathRequests);

            long threshold = (long) (Math.max(0.0D,
                ModConfig.performanceStallThresholdMillis) * 1_000_000.0D);
            if (elapsedNanoseconds >= threshold) {
                int cooldown = Math.max(0, ModConfig.performanceStallLogCooldownTicks);
                if (lastStallLogTick == Long.MIN_VALUE
                    || lifetimeTicks - lastStallLogTick >= cooldown) {
                    stall = snapshot(current, currentLivingDetails, currentPathRequests, 1L,
                        currentLeafFrames, currentUnwrappedPathCallers);
                    stall.suppressed = suppressedStalls;
                    suppressedStalls = 0L;
                    lastStallLogTick = lifetimeTicks;
                } else {
                    suppressedStalls++;
                }
            }

            if (intervalTicks >= Math.max(1, ModConfig.performanceSummaryIntervalTicks)) {
                summary = snapshot(
                    interval, intervalLivingDetails, intervalPathRequests, intervalTicks,
                    intervalLeafFrames, intervalUnwrappedPathCallers
                );
                Arrays.fill(interval, 0L);
                Arrays.fill(intervalLivingDetails, 0L);
                clear(intervalPathRequests);
                intervalLeafFrames.clear();
                intervalUnwrappedPathCallers.clear();
                intervalTicks = 0L;
            }
        }
        if (stall != null) {
            Zomboid.logger.warn("Vanilla work samples for stalled server tick: {}{}",
                formatSummary(stall), stall.suppressed > 0L
                    ? "; priorSuppressed=" + stall.suppressed : "");
        }
        if (summary != null) {
            Zomboid.logger.info("Vanilla work sampling summary: {}", formatSummary(summary));
        }
    }

    public static void reset() {
        stop(false);
        synchronized (LOCK) {
            Arrays.fill(current, 0L);
            Arrays.fill(interval, 0L);
            Arrays.fill(lifetime, 0L);
            Arrays.fill(currentLivingDetails, 0L);
            Arrays.fill(intervalLivingDetails, 0L);
            Arrays.fill(lifetimeLivingDetails, 0L);
            clear(currentPathRequests);
            clear(intervalPathRequests);
            clear(lifetimePathRequests);
            currentLeafFrames.clear();
            intervalLeafFrames.clear();
            lifetimeLeafFrames.clear();
            currentUnwrappedPathCallers.clear();
            intervalUnwrappedPathCallers.clear();
            lifetimeUnwrappedPathCallers.clear();
            intervalTicks = 0L;
            lifetimeTicks = 0L;
            lastStallLogTick = Long.MIN_VALUE;
            suppressedStalls = 0L;
            activeTickSequence = 0L;
            activePathRequestSource = null;
        }
    }

    static VanillaPathRequestSource beginPathRequest(VanillaPathRequestSource source) {
        VanillaPathRequestSource previous = activePathRequestSource;
        activePathRequestSource = source;
        return previous;
    }

    static void endPathRequest(VanillaPathRequestSource previous) {
        activePathRequestSource = previous;
    }

    public static void stopAndLog() {
        stop(true);
    }

    private static void ensureStarted(Thread thread) {
        if (running && serverThread == thread) {
            return;
        }
        synchronized (LOCK) {
            if (running && serverThread == thread) {
                return;
            }
            running = true;
            serverThread = thread;
            Thread worker = new Thread(VanillaEntityWorkSampler::sampleLoop,
                "Zomboid Vanilla Work Sampler");
            worker.setDaemon(true);
            samplerThread = worker;
            worker.start();
        }
    }

    private static void sampleLoop() {
        Thread owner = Thread.currentThread();
        while (running && samplerThread == owner) {
            try {
                Thread.sleep(Math.max(1, ModConfig.vanillaEntitySampleIntervalMillis));
            } catch (InterruptedException ignored) {
                // Stop and reset interrupt the daemon so it exits without lingering.
            }
            Thread target = serverThread;
            if (!running || samplerThread != owner || !tickActive || target == null) {
                continue;
            }
            long sequence = activeTickSequence;
            VanillaPathRequestSource requestSource = activePathRequestSource;
            StackTraceElement[] stack = target.getStackTrace();
            Category category = classify(stack);
            LivingDetail livingDetail = category == Category.LIVING_ENTITY
                ? classifyLivingDetail(stack) : null;
            String leaf = leafFrame(stack);
            String pathCaller = category == Category.PATH_SEARCH
                ? pathSearchCaller(stack) : null;
            synchronized (LOCK) {
                if (!running || samplerThread != owner || !tickActive
                    || activeTickSequence != sequence) {
                    continue;
                }
                current[category.ordinal()]++;
                if (livingDetail != null) {
                    currentLivingDetails[livingDetail.ordinal()]++;
                }
                boolean wrappedRequest = requestSource != null
                    && activePathRequestSource == requestSource;
                if (wrappedRequest) {
                    currentPathRequests[requestSource.ordinal()][category.ordinal()]++;
                } else if (pathCaller != null) {
                    incrementCapped(currentUnwrappedPathCallers, pathCaller);
                    incrementCapped(intervalUnwrappedPathCallers, pathCaller);
                    incrementCapped(lifetimeUnwrappedPathCallers, pathCaller);
                }
                incrementCapped(currentLeafFrames, leaf);
                incrementCapped(intervalLeafFrames, leaf);
                incrementCapped(lifetimeLeafFrames, leaf);
            }
        }
    }

    private static void stop(boolean log) {
        Snapshot closed = null;
        Thread worker;
        synchronized (LOCK) {
            tickActive = false;
            running = false;
            worker = samplerThread;
            samplerThread = null;
            serverThread = null;
            activePathRequestSource = null;
            if (log && lifetimeTicks > 0L) {
                closed = snapshot(
                    lifetime, lifetimeLivingDetails, lifetimePathRequests, lifetimeTicks,
                    lifetimeLeafFrames, lifetimeUnwrappedPathCallers
                );
            }
        }
        if (worker != null) {
            worker.interrupt();
        }
        if (closed != null) {
            Zomboid.logger.info("Vanilla work sampling closed: {}", formatSummary(closed));
        }
    }

    static Category classify(StackTraceElement[] stack) {
        if (containsClass(stack, "PathFinder", "NodeProcessor", "WalkNodeProcessor",
            "PathHeap")) {
            return Category.PATH_SEARCH;
        }
        if (containsClass(stack, "ChunkCache") && containsClass(stack, "PathNavigate")) {
            return Category.PATH_SNAPSHOT;
        }
        if (containsClass(stack, "PathNavigate")) {
            return Category.NAVIGATION;
        }
        if (containsEntityPushWork(stack)) {
            return Category.ENTITY_PUSH_SCAN;
        }
        if (containsMethod(stack, "World", "getCollisionBoxes", "func_184144_a")) {
            return Category.BLOCK_COLLISION_QUERY;
        }
        if (containsClass(stack, "AxisAlignedBB")) {
            return Category.AABB_RESOLUTION;
        }
        if (containsMethod(stack, "Entity", "move", "func_70091_d")) {
            return Category.ENTITY_MOVE;
        }
        if (containsMethod(stack, "EntityLivingBase", "travel", "func_191986_a")) {
            return Category.ENTITY_TRAVEL;
        }
        if (containsClass(stack, "EntityMoveHelper")) {
            return Category.MOVE_HELPER;
        }
        if (containsClass(stack, "EntityAITasks", "EntitySenses")
            || containsPackageClass(stack, ".entity.ai.EntityAI")) {
            return Category.AI_SELECTORS;
        }
        if (containsClass(stack, "EntityTracker", "EntityTrackerEntry", "PlayerChunkMap",
            "NetHandlerPlayServer", "NetworkManager")) {
            return Category.TRACKING_NETWORK;
        }
        if (containsClass(stack, "EntityLiving", "EntityLivingBase", "EntityZombie")) {
            return Category.LIVING_ENTITY;
        }
        if (containsClass(stack, "Chunk", "ChunkProvider", "WorldServer", "World")) {
            return Category.CHUNK_WORLD;
        }
        return Category.OTHER_SERVER;
    }

    /**
     * Subdivides samples which have already fallen through to the general living-entity bucket.
     * The order is deliberately deepest/specific first, matching {@link #classify}.
     */
    static LivingDetail classifyLivingDetail(StackTraceElement[] stack) {
        if (containsMethod(stack, "EntityLiving", "despawnEntity", "func_70623_bb")) {
            return LivingDetail.DESPAWN;
        }
        if (containsMethod(stack, "EntityLiving", "updateEquipmentIfNeeded", "func_175445_a",
            "canEquipItem", "func_175448_a")) {
            return LivingDetail.LOOT_EQUIPMENT;
        }
        if (containsMethodName(stack, "updateAITasks", "func_70619_bc")) {
            return LivingDetail.MOB_TICK;
        }
        if (containsClassSuffix(stack, "EntityLookHelper")) {
            return LivingDetail.LOOK_CONTROL;
        }
        if (containsClassSuffix(stack, "EntityJumpHelper")) {
            return LivingDetail.JUMP_CONTROL;
        }
        if (containsClassSuffix(stack, "EntityBodyHelper")) {
            return LivingDetail.BODY_CONTROL;
        }
        if (containsMethod(stack, "EntityLiving", "updateLeashedState", "func_110159_bB")) {
            return LivingDetail.LEASH;
        }
        if (containsMethodName(stack, "updatePotionEffects", "func_70679_bo",
            "updatePotionMetadata", "func_70695_b")) {
            return LivingDetail.STATUS_EFFECTS;
        }
        if (containsMethodName(stack, "onLivingUpdate", "func_70636_d",
            "onEntityUpdate", "func_70030_z", "onUpdate", "func_70071_h_")) {
            return LivingDetail.BASE_UPDATE;
        }
        return LivingDetail.OTHER;
    }

    /**
     * Identifies the vanilla living-entity pass which finds nearby entities, applies the
     * max-entity-cramming rule, and pushes colliding entities apart. The chunk/world query
     * frames used by this pass are intentionally not matched on their own because many unrelated
     * systems use the same broad-phase entity query.
     */
    private static boolean containsEntityPushWork(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String simple = simpleClassName(frame.getClassName());
            String method = frame.getMethodName();
            if ((simple.equals("EntityLivingBase")
                && matches(method, "collideWithNearbyEntities", "func_85033_bc",
                    "collideWithEntity", "func_82167_n"))
                || (simple.equals("Entity")
                    && matches(method, "applyEntityCollision", "func_70108_f"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsMethod(StackTraceElement[] stack, String className,
                                          String... methods) {
        for (StackTraceElement frame : stack) {
            String simple = simpleClassName(frame.getClassName());
            if (simple.equals(className) && matches(frame.getMethodName(), methods)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsMethodName(StackTraceElement[] stack, String... methods) {
        for (StackTraceElement frame : stack) {
            if (matches(frame.getMethodName(), methods)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsClass(StackTraceElement[] stack, String... prefixes) {
        for (StackTraceElement frame : stack) {
            String simple = simpleClassName(frame.getClassName());
            for (String prefix : prefixes) {
                if (simple.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsPackageClass(StackTraceElement[] stack, String marker) {
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsClassSuffix(StackTraceElement[] stack, String suffix) {
        for (StackTraceElement frame : stack) {
            if (simpleClassName(frame.getClassName()).endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String value, String... choices) {
        for (String choice : choices) {
            if (choice.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleClassName(String className) {
        int separator = className.lastIndexOf('.');
        return separator >= 0 ? className.substring(separator + 1) : className;
    }

    private static String leafFrame(StackTraceElement[] stack) {
        if (stack.length == 0) {
            return "unknown";
        }
        StackTraceElement frame = stack[0];
        return simpleClassName(frame.getClassName()) + '#' + frame.getMethodName();
    }

    /** Returns the first initiating frame outside Minecraft's pathfinding package. */
    static String pathSearchCaller(StackTraceElement[] stack) {
        boolean foundPathfinding = false;
        for (StackTraceElement frame : stack) {
            if (isPathfindingFrame(frame.getClassName())) {
                foundPathfinding = true;
            } else if (foundPathfinding) {
                return simpleClassName(frame.getClassName()) + '#' + frame.getMethodName();
            }
        }
        return "unknown";
    }

    private static boolean isPathfindingFrame(String className) {
        return className.startsWith("net.minecraft.pathfinding.");
    }

    private static void incrementCapped(Map<String, Long> counts, String key) {
        Long previous = counts.get(key);
        if (previous != null) {
            counts.put(key, previous + 1L);
        } else if (counts.size() < MAX_LEAF_FRAMES) {
            counts.put(key, 1L);
        }
    }

    private static void add(long[] source, long[] destination) {
        for (int i = 0; i < source.length; i++) {
            destination[i] += source[i];
        }
    }

    private static void add(long[][] source, long[][] destination) {
        for (int i = 0; i < source.length; i++) {
            add(source[i], destination[i]);
        }
    }

    private static void clear(long[][] values) {
        for (long[] row : values) {
            Arrays.fill(row, 0L);
        }
    }

    private static long[][] newPathRequestMatrix() {
        return new long[VanillaPathRequestSource.values().length][Category.values().length];
    }

    private static long[][] copy(long[][] values) {
        long[][] result = new long[values.length][];
        for (int i = 0; i < values.length; i++) {
            result[i] = Arrays.copyOf(values[i], values[i].length);
        }
        return result;
    }

    private static Snapshot snapshot(long[] categories, long[] livingDetails,
                                     long[][] pathRequestSamples, long ticks,
                                     Map<String, Long> leafFrames,
                                     Map<String, Long> unwrappedPathCallers) {
        return new Snapshot(Arrays.copyOf(categories, categories.length),
            Arrays.copyOf(livingDetails, livingDetails.length), copy(pathRequestSamples), ticks,
            leafFrames == null ? new HashMap<>() : new HashMap<>(leafFrames),
            unwrappedPathCallers == null ? new HashMap<>()
                : new HashMap<>(unwrappedPathCallers));
    }

    private static String formatSummary(Snapshot snapshot) {
        return "ticks=" + snapshot.ticks + ", " + formatCategories(snapshot)
            + "; livingEntityDetails=" + formatLivingDetails(snapshot)
            + "; pathRequestSamples=" + formatPathRequestSamples(snapshot.pathRequestSamples)
            + "; unwrappedPathCallers=" + formatTopFrames(snapshot.unwrappedPathCallers)
            + "; topLeafFrames=" + formatTopFrames(snapshot.leafFrames);
    }

    private static String formatPathRequestSamples(long[][] samples) {
        StringBuilder result = new StringBuilder(300);
        for (VanillaPathRequestSource source : VanillaPathRequestSource.values()) {
            long[] row = samples[source.ordinal()];
            long total = 0L;
            for (long count : row) {
                total += count;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            long snapshot = row[Category.PATH_SNAPSHOT.ordinal()];
            long search = row[Category.PATH_SEARCH.ordinal()];
            long navigation = row[Category.NAVIGATION.ordinal()];
            result.append(source.getLabel()).append('=')
                .append(total).append("[snapshot=").append(snapshot)
                .append(",search=").append(search)
                .append(",navigation=").append(navigation)
                .append(",other=").append(total - snapshot - search - navigation)
                .append(']');
        }
        return result.toString();
    }

    private static String formatCategories(Snapshot snapshot) {
        long total = 0L;
        for (long count : snapshot.categories) {
            total += count;
        }
        StringBuilder result = new StringBuilder(240);
        result.append("samples=").append(total);
        for (Category category : Category.values()) {
            long count = snapshot.categories[category.ordinal()];
            double percent = total == 0L ? 0.0D : count * 100.0D / total;
            result.append(' ').append(category.label).append('=')
                .append(count).append('/')
                .append(String.format(Locale.ROOT, "%.1f%%", percent));
        }
        return result.toString();
    }

    private static String formatLivingDetails(Snapshot snapshot) {
        long total = snapshot.categories[Category.LIVING_ENTITY.ordinal()];
        StringBuilder result = new StringBuilder(220);
        result.append("samples=").append(total);
        for (LivingDetail detail : LivingDetail.values()) {
            long count = snapshot.livingDetails[detail.ordinal()];
            double percent = total == 0L ? 0.0D : count * 100.0D / total;
            result.append(' ').append(detail.label).append('=')
                .append(count).append('/')
                .append(String.format(Locale.ROOT, "%.1f%%", percent));
        }
        return result.toString();
    }

    private static String formatTopFrames(Map<String, Long> frames) {
        if (frames.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(frames.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<String, Long> entry) ->
            entry.getValue()).reversed().thenComparing(Map.Entry::getKey));
        StringBuilder result = new StringBuilder();
        int limit = Math.min(REPORTED_LEAF_FRAMES, sorted.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                result.append(", ");
            }
            Map.Entry<String, Long> entry = sorted.get(i);
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    private static boolean enabled() {
        return ModConfig.enablePerformanceTelemetry
            && ModConfig.enableVanillaEntityWorkSampler;
    }

    enum Category {
        AI_SELECTORS("aiSelectors"),
        PATH_SNAPSHOT("pathSnapshot"),
        NAVIGATION("navigationAdvance"),
        PATH_SEARCH("pathSearch"),
        ENTITY_PUSH_SCAN("entityPushScan"),
        BLOCK_COLLISION_QUERY("blockCollisionQuery"),
        AABB_RESOLUTION("aabbResolution"),
        ENTITY_MOVE("entityMove"),
        ENTITY_TRAVEL("entityTravel"),
        MOVE_HELPER("moveHelper"),
        LIVING_ENTITY("livingEntity"),
        TRACKING_NETWORK("trackingNetwork"),
        CHUNK_WORLD("chunkWorld"),
        OTHER_SERVER("otherServer");

        private final String label;

        Category(String label) {
            this.label = label;
        }
    }

    enum LivingDetail {
        DESPAWN("despawn"),
        LOOT_EQUIPMENT("lootEquipment"),
        MOB_TICK("mobTick"),
        LOOK_CONTROL("lookControl"),
        JUMP_CONTROL("jumpControl"),
        BODY_CONTROL("bodyControl"),
        LEASH("leash"),
        STATUS_EFFECTS("statusEffects"),
        BASE_UPDATE("baseUpdate"),
        OTHER("otherLiving");

        private final String label;

        LivingDetail(String label) {
            this.label = label;
        }
    }

    private static final class Snapshot {
        private final long[] categories;
        private final long[] livingDetails;
        private final long[][] pathRequestSamples;
        private final long ticks;
        private final Map<String, Long> leafFrames;
        private final Map<String, Long> unwrappedPathCallers;
        private long suppressed;

        private Snapshot(long[] categories, long[] livingDetails,
                         long[][] pathRequestSamples, long ticks,
                         Map<String, Long> leafFrames,
                         Map<String, Long> unwrappedPathCallers) {
            this.categories = categories;
            this.livingDetails = livingDetails;
            this.pathRequestSamples = pathRequestSamples;
            this.ticks = ticks;
            this.leafFrames = leafFrames;
            this.unwrappedPathCallers = unwrappedPathCallers;
        }
    }
}

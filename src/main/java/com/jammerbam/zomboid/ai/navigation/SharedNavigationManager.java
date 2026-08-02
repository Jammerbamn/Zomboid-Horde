package com.jammerbam.zomboid.ai.navigation;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.performance.PerformancePhase;
import com.jammerbam.zomboid.performance.RuntimePerformanceTelemetry;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Owns one reusable reverse navigation field per pursued player and world. */
public final class SharedNavigationManager {
    private static final Map<World, WorldState> WORLD_STATES = new WeakHashMap<>();
    private static final long UNUSED_FIELD_LIFETIME_TICKS = 200L;

    private SharedNavigationManager() {
    }

    /** Keeps incremental field construction moving even when all users skip a cadence tick. */
    public static void tick(World world) {
        WorldState state = WORLD_STATES.get(world);
        if (state != null && !world.isRemote) {
            state.advance(world.getTotalWorldTime());
        }
    }

    public static boolean steer(EntityZombie zombie, EntityPlayer target, double speed) {
        if (!ModConfig.enableSharedPursuitFlowFields || zombie.world.isRemote) {
            return false;
        }
        long navigationStartedAt = RuntimePerformanceTelemetry.begin();
        try {
            return steerMeasured(zombie, target, speed);
        } finally {
            RuntimePerformanceTelemetry.recordElapsed(
                zombie.world, PerformancePhase.SHARED_NAVIGATION, navigationStartedAt
            );
        }
    }

    /** Re-arms 1.12.2's one-tick move helper without recomputing a direction choice. */
    public static boolean reissueSteering(EntityZombie zombie, EntityPlayer target,
                                          double speed) {
        if (!ModConfig.enableSharedPursuitFlowFields || zombie.world.isRemote) {
            return false;
        }
        long navigationStartedAt = RuntimePerformanceTelemetry.begin();
        try {
            WorldState state = WORLD_STATES.get(zombie.world);
            if (state == null) {
                return false;
            }
            SteeringCommand command = state.steeringCommands.get(zombie);
            if (command == null || !command.isFor(target.getUniqueID())) {
                state.steeringCommandMisses++;
                state.steeringCommands.remove(zombie);
                return false;
            }
            command.issue(zombie, speed);
            state.steeringCommandReissues++;
            return true;
        } finally {
            RuntimePerformanceTelemetry.recordElapsed(
                zombie.world, PerformancePhase.SHARED_NAVIGATION, navigationStartedAt
            );
        }
    }

    public static void stopSteering(EntityZombie zombie) {
        WorldState state = WORLD_STATES.get(zombie.world);
        if (state != null) {
            state.steeringCommands.remove(zombie);
        }
    }

    private static boolean steerMeasured(EntityZombie zombie, EntityPlayer target,
                                         double speed) {
        WorldState state = WORLD_STATES.computeIfAbsent(
            zombie.world, WorldState::new
        );
        long now = zombie.world.getTotalWorldTime();
        TargetField field = state.fields.computeIfAbsent(
            target.getUniqueID(), ignored -> new TargetField()
        );
        field.lastUsedAt = now;
        if (field.ensureBuild(state.navigationCache, target.getPosition(), now)) {
            state.fieldBuildsStarted++;
        }
        state.advance(now);
        long steeringStartedAt = System.nanoTime();
        boolean successful = false;
        try {
            ResolvedField usable = field.usableFor(zombie);
            if (usable == null) {
                state.steeringCommands.remove(zombie);
                return false;
            }
            FieldBuild build = usable.build;
            long current = usable.position;
            int distance = build.solver.getDistance(current);
            if (distance == 0) {
                clearVanillaPath(zombie);
                if (!CrowdNavigationManager.shouldHoldApproach(zombie, target)) {
                    state.issueSteeringCommand(
                        zombie, target.getUniqueID(), target.posX,
                        target.getEntityBoundingBox().minY, target.posZ, speed
                    );
                } else {
                    state.steeringCommands.remove(zombie);
                }
                successful = true;
                return true;
            }
            long next;
            if (distance == Integer.MAX_VALUE) {
                // A partially built field may have reached an adjacent cell but not the
                // zombie's exact cell yet. Preserve that early-coverage behavior with a
                // rare terrain lookup; covered cells use the precomputed direction field.
                int neighborCount = build.surface.collectNeighbors(
                    current, build.resolutionNeighborScratch
                );
                next = build.solver.bestLowerNeighborPacked(
                    current, build.resolutionNeighborScratch, neighborCount,
                    zombie.getEntityId()
                );
            } else {
                next = CrowdNavigationManager.selectNext(
                    zombie, target, build.solver, current
                );
            }
            if (next == PackedBlockPosition.NONE) {
                state.steeringCommands.remove(zombie);
                return false;
            }
            clearVanillaPath(zombie);
            state.issueSteeringCommand(
                zombie, target.getUniqueID(),
                PackedBlockPosition.x(next) + 0.5D,
                build.surface.movementY(next),
                PackedBlockPosition.z(next) + 0.5D, speed
            );
            successful = true;
            return true;
        } finally {
            state.recordSteering(
                successful, System.nanoTime() - steeringStartedAt
            );
        }
    }

    public static void invalidate(World world, BlockPos changedPosition) {
        NavigationManager.invalidate(world, changedPosition);
    }

    static void invalidateFields(World world, BlockPos changedPosition) {
        WorldState state = WORLD_STATES.get(world);
        if (state == null) {
            return;
        }
        for (TargetField field : state.fields.values()) {
            state.fieldInvalidationChecks++;
            boolean activeAffected = field.active != null
                && field.active.dependsOn(changedPosition);
            boolean buildingAffected = field.building != null
                && field.building.dependsOn(changedPosition);
            if (!activeAffected && !buildingAffected) {
                state.fieldInvalidationsIgnored++;
                continue;
            }
            field.dirty = true;
            field.invalidatedAt = world.getTotalWorldTime();
            if (activeAffected) {
                state.activeFieldInvalidations++;
            }
            if (buildingAffected) {
                // Do not starve moving hordes while construction changes arrive every
                // tick. Finish this generation for continuous steering, then retain the
                // dirty flag so a clean replacement follows it.
                if (!field.buildingDirty) {
                    field.buildingDirty = true;
                    state.buildingFieldsMarkedStale++;
                }
                state.buildingFieldsPreserved++;
            } else if (field.building != null) {
                state.buildingFieldsPreserved++;
            }
        }
    }

    public static void invalidateChunk(World world, int chunkX, int chunkZ) {
        NavigationManager.invalidateChunk(world, chunkX, chunkZ);
    }

    static void invalidateFieldChunks(World world, int chunkX, int chunkZ) {
        WorldState state = WORLD_STATES.get(world);
        if (state == null) {
            return;
        }
        long now = world.getTotalWorldTime();
        for (TargetField field : state.fields.values()) {
            if (field.intersectsChunk(chunkX, chunkZ)) {
                field.dirty = true;
                field.invalidatedAt = now;
                if (field.building != null) {
                    state.chunkBuildCancellations++;
                }
                field.building = null;
                field.buildingDirty = false;
            }
        }
    }

    public static void clear(World world) {
        NavigationManager.clear(world);
    }

    static void clearFields(World world) {
        WorldState state = WORLD_STATES.remove(world);
        if (state != null && (state.expandedNodes > 0L || state.steeringQueries > 0L)) {
            double expansionMillis = state.expansionNanoseconds / 1_000_000.0D;
            double nanosecondsPerNode = state.expandedNodes == 0L
                ? 0.0D : (double) state.expansionNanoseconds / state.expandedNodes;
            double steeringMillis = state.steeringNanoseconds / 1_000_000.0D;
            double nanosecondsPerSteering = state.steeringQueries == 0L
                ? 0.0D : (double) state.steeringNanoseconds / state.steeringQueries;
            double steeringSuccessRate = state.steeringQueries == 0L
                ? 0.0D : 100.0D * state.successfulSteering / state.steeringQueries;
            Zomboid.logger.info(
                "Shared navigation closed for dimension {}: packed flow fields: {} started, "
                    + "{} completed, {} nodes in {} ms ({} ns/node); "
                    + "direction steering: {} queries, {} successful ({}%), "
                    + "{} ms ({} ns/query); {} deferred vanilla routes cancelled; "
                    + "dependency invalidation: {} field checks, {} ignored, "
                    + "{} active fields dirtied, {} builds marked stale, "
                    + "{} builds preserved, {} chunk-unload cancellations; "
                    + "continuous movement: {} cached commands reissued, {} misses.",
                world.provider.getDimension(),
                state.fieldBuildsStarted,
                state.fieldBuildsCompleted,
                state.expandedNodes,
                String.format(java.util.Locale.ROOT, "%.2f", expansionMillis),
                String.format(java.util.Locale.ROOT, "%.1f", nanosecondsPerNode),
                state.steeringQueries,
                state.successfulSteering,
                String.format(java.util.Locale.ROOT, "%.1f", steeringSuccessRate),
                String.format(java.util.Locale.ROOT, "%.2f", steeringMillis),
                String.format(java.util.Locale.ROOT, "%.1f", nanosecondsPerSteering),
                state.deferredVanillaRoutesCancelled,
                state.fieldInvalidationChecks,
                state.fieldInvalidationsIgnored,
                state.activeFieldInvalidations,
                state.buildingFieldsMarkedStale,
                state.buildingFieldsPreserved,
                state.chunkBuildCancellations,
                state.steeringCommandReissues,
                state.steeringCommandMisses
            );
        }
    }

    private static void clearVanillaPath(EntityZombie zombie) {
        if (VanillaNavigationOwnership.release(zombie.getNavigator())) {
            WorldState state = WORLD_STATES.get(zombie.world);
            if (state != null) {
                state.deferredVanillaRoutesCancelled++;
            }
        }
    }

    private static final class WorldState {
        private final Map<UUID, TargetField> fields = new HashMap<>();
        private final GroundNavigationCache navigationCache;
        private final Map<EntityZombie, SteeringCommand> steeringCommands =
            new WeakHashMap<>();
        private long lastAdvancedAt = Long.MIN_VALUE;
        private long fieldBuildsStarted;
        private long fieldBuildsCompleted;
        private long expandedNodes;
        private long expansionNanoseconds;
        private long steeringQueries;
        private long successfulSteering;
        private long steeringNanoseconds;
        private long deferredVanillaRoutesCancelled;
        private long fieldInvalidationChecks;
        private long fieldInvalidationsIgnored;
        private long activeFieldInvalidations;
        private long buildingFieldsMarkedStale;
        private long buildingFieldsPreserved;
        private long chunkBuildCancellations;
        private long steeringCommandReissues;
        private long steeringCommandMisses;

        private WorldState(World world) {
            navigationCache = NavigationTerrainManager.get(world);
        }

        private void recordSteering(boolean successful, long elapsedNanoseconds) {
            steeringQueries++;
            if (successful) {
                successfulSteering++;
            }
            steeringNanoseconds += elapsedNanoseconds;
        }

        private void issueSteeringCommand(EntityZombie zombie, UUID targetId,
                                          double x, double y, double z, double speed) {
            SteeringCommand command = steeringCommands.get(zombie);
            if (command == null) {
                command = new SteeringCommand();
                steeringCommands.put(zombie, command);
            }
            command.set(targetId, x, y, z).issue(zombie, speed);
        }

        private void advance(long now) {
            if (lastAdvancedAt == now) {
                return;
            }
            lastAdvancedAt = now;
            Iterator<TargetField> iterator = fields.values().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().lastUsedAt > UNUSED_FIELD_LIFETIME_TICKS) {
                    iterator.remove();
                }
            }
            List<TargetField> building = new ArrayList<>();
            for (TargetField field : fields.values()) {
                if (field.building != null) {
                    building.add(field);
                }
            }
            int remaining = Math.max(1, ModConfig.pursuitFlowFieldNodesPerTick);
            for (int i = 0; i < building.size() && remaining > 0; i++) {
                TargetField field = building.get(i);
                int share = Math.max(1, remaining / (building.size() - i));
                long startedAt = System.nanoTime();
                int used = field.building.solver.stepPacked(
                    share, field.building.surface
                );
                expansionNanoseconds += System.nanoTime() - startedAt;
                expandedNodes += used;
                remaining -= used;
                if (field.building.solver.isComplete()) {
                    boolean needsCleanReplacement = field.buildingDirty;
                    field.active = field.building;
                    field.building = null;
                    field.buildingDirty = false;
                    field.dirty = needsCleanReplacement;
                    fieldBuildsCompleted++;
                }
            }
        }
    }

    private static final class TargetField {
        private FieldBuild active;
        private FieldBuild building;
        private long lastBuildStartedAt = Long.MIN_VALUE;
        private long lastUsedAt;
        private long invalidatedAt = Long.MIN_VALUE;
        private boolean dirty;
        private boolean buildingDirty;
        private final ResolvedField resolved = new ResolvedField();

        private boolean ensureBuild(GroundNavigationCache cache, BlockPos target,
                                    long now) {
            if (building != null) {
                return false;
            }
            boolean moved = active == null || active.target.distanceSq(target)
                >= ModConfig.pursuitFlowFieldRebuildDistance
                    * ModConfig.pursuitFlowFieldRebuildDistance;
            if (!dirty && !moved) {
                return false;
            }
            if (dirty && now <= invalidatedAt) {
                return false;
            }
            if (lastBuildStartedAt != Long.MIN_VALUE
                && now - lastBuildStartedAt
                    < ModConfig.pursuitFlowFieldMinimumRebuildTicks) {
                return false;
            }
            GroundNavigationSurface surface = new GroundNavigationSurface(
                cache, target, ModConfig.pursuitFlowFieldRadius,
                ModConfig.pursuitFlowFieldVerticalRange
            );
            long[] goals = new long[8];
            int goalCount = surface.collectApproachPositions(target, goals);
            if (goalCount == 0) {
                return false;
            }
            building = new FieldBuild(
                target, surface,
                new FlowFieldSolver(
                    goals, goalCount, ModConfig.pursuitFlowFieldMaximumNodes
                )
            );
            lastBuildStartedAt = now;
            return true;
        }

        @Nullable
        private ResolvedField usableFor(EntityZombie zombie) {
            long position = building == null
                ? PackedBlockPosition.NONE
                : building.resolvePosition(zombie);
            if (position != PackedBlockPosition.NONE) {
                return resolved.set(building, position);
            }
            position = active == null
                ? PackedBlockPosition.NONE
                : active.resolvePosition(zombie);
            if (position != PackedBlockPosition.NONE) {
                return resolved.set(active, position);
            }
            return null;
        }

        private boolean intersectsChunk(int chunkX, int chunkZ) {
            return active != null && active.surface.intersectsChunk(chunkX, chunkZ)
                || building != null && building.surface.intersectsChunk(chunkX, chunkZ);
        }
    }

    private static final class FieldBuild {
        private final BlockPos target;
        private final GroundNavigationSurface surface;
        private final FlowFieldSolver solver;
        private final long[] resolutionNeighborScratch = new long[4];

        private FieldBuild(BlockPos target, GroundNavigationSurface surface,
                           FlowFieldSolver solver) {
            this.target = target.toImmutable();
            this.surface = surface;
            this.solver = solver;
        }

        private long resolvePosition(EntityZombie zombie) {
            long position = surface.resolveEntityPositionPacked(
                zombie.posX, zombie.getEntityBoundingBox().minY, zombie.posZ
            );
            if (position == PackedBlockPosition.NONE) {
                return PackedBlockPosition.NONE;
            }
            if (solver.getDistance(position) != Integer.MAX_VALUE) {
                return position;
            }
            int count = surface.collectNeighbors(position, resolutionNeighborScratch);
            for (int i = 0; i < count; i++) {
                if (solver.getDistance(resolutionNeighborScratch[i])
                    != Integer.MAX_VALUE) {
                    return position;
                }
            }
            return PackedBlockPosition.NONE;
        }

        private boolean dependsOn(BlockPos changedPosition) {
            return surface.mayDependOn(changedPosition)
                && NavigationDependencies.affectsReachedCell(
                    solver, changedPosition
                );
        }
    }

    private static final class ResolvedField {
        private FieldBuild build;
        private long position;

        private ResolvedField set(FieldBuild build, long position) {
            this.build = build;
            this.position = position;
            return this;
        }
    }

    static final class SteeringCommand {
        private UUID targetId;
        private double x;
        private double y;
        private double z;

        SteeringCommand set(UUID targetId, double x, double y, double z) {
            this.targetId = targetId;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        boolean isFor(UUID targetId) {
            return this.targetId != null && this.targetId.equals(targetId);
        }

        void issue(EntityZombie zombie, double speed) {
            zombie.getMoveHelper().setMoveTo(x, y, z, speed);
        }

        double getX() {
            return x;
        }

        double getY() {
            return y;
        }

        double getZ() {
            return z;
        }
    }
}

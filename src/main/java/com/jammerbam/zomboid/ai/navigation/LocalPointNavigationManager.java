package com.jammerbam.zomboid.ai.navigation;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.config.ModConfig;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Incremental short-range navigation for optional idle movement. */
public final class LocalPointNavigationManager {
    private static final Map<World, WorldState> WORLD_STATES = new WeakHashMap<>();

    private LocalPointNavigationManager() {
    }

    public static boolean begin(EntityZombie zombie, BlockPos destination,
                                double speed, boolean acceptAdjacent) {
        if (zombie.world.isRemote) {
            return false;
        }
        WorldState state = WORLD_STATES.computeIfAbsent(
            zombie.world, ignored -> new WorldState()
        );
        state.routes.remove(zombie);
        state.requests++;
        RouteSearch search = createSearch(
            NavigationTerrainManager.get(zombie.world), zombie.getPosition(),
            destination, ModConfig.localNavigationDetourRadius,
            ModConfig.localNavigationMaximumNodes, acceptAdjacent
        );
        if (search == null) {
            state.rejectedRequests++;
            return false;
        }
        VanillaNavigationOwnership.release(zombie.getNavigator());
        LocalRoute route = new LocalRoute(
            search, speed,
            zombie.world.getTotalWorldTime()
        );
        state.routes.put(zombie, route);
        state.acceptedRequests++;
        return true;
    }

    public static Status getStatus(EntityZombie zombie) {
        WorldState state = WORLD_STATES.get(zombie.world);
        LocalRoute route = state == null ? null : state.routes.get(zombie);
        return route == null ? Status.NONE : route.status;
    }

    public static Status steer(EntityZombie zombie) {
        WorldState state = WORLD_STATES.get(zombie.world);
        LocalRoute route = state == null ? null : state.routes.get(zombie);
        if (route == null) {
            return Status.NONE;
        }
        if (route.status != Status.MOVING) {
            return route.status;
        }

        state.steeringQueries++;
        long current = route.search.surface.resolveEntityPositionPacked(
            zombie.posX, zombie.getEntityBoundingBox().minY, zombie.posZ
        );
        if (current == PackedBlockPosition.NONE) {
            fail(state, route, false);
            return route.status;
        }
        int distance = route.search.solver.getDistance(current);
        if (distance == 0) {
            route.status = Status.ARRIVED;
            state.arrivals++;
            return route.status;
        }
        if (distance == Integer.MAX_VALUE) {
            fail(state, route, false);
            return route.status;
        }

        long now = zombie.world.getTotalWorldTime();
        if (distance < route.bestDistance) {
            route.bestDistance = distance;
            route.lastProgressAt = now;
        } else if (now - route.lastProgressAt >= ModConfig.localNavigationStuckTicks) {
            fail(state, route, true);
            return route.status;
        }

        long next = route.search.solver.bestNextPacked(current, zombie.getEntityId());
        if (next == PackedBlockPosition.NONE) {
            fail(state, route, false);
            return route.status;
        }
        VanillaNavigationOwnership.release(zombie.getNavigator());
        zombie.getMoveHelper().setMoveTo(
            PackedBlockPosition.x(next) + 0.5D,
            route.search.surface.movementY(next),
            PackedBlockPosition.z(next) + 0.5D,
            route.speed
        );
        state.successfulSteering++;
        return route.status;
    }

    public static void cancel(EntityZombie zombie) {
        WorldState state = WORLD_STATES.get(zombie.world);
        if (state != null) {
            state.routes.remove(zombie);
        }
    }

    public static void tick(World world) {
        WorldState state = WORLD_STATES.get(world);
        if (state == null || world.isRemote) {
            return;
        }
        List<Map.Entry<EntityZombie, LocalRoute>> building = new ArrayList<>();
        Iterator<Map.Entry<EntityZombie, LocalRoute>> iterator =
            state.routes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityZombie, LocalRoute> entry = iterator.next();
            EntityZombie zombie = entry.getKey();
            if (zombie == null || !zombie.isEntityAlive() || zombie.world != world) {
                iterator.remove();
            } else if (entry.getValue().status == Status.BUILDING) {
                building.add(entry);
            }
        }

        int remaining = Math.max(1, ModConfig.localNavigationNodesPerTick);
        for (int i = 0; i < building.size() && remaining > 0; i++) {
            LocalRoute route = building.get(i).getValue();
            int share = Math.max(1, remaining / (building.size() - i));
            long startedAt = System.nanoTime();
            int used = route.search.advance(share);
            state.buildNanoseconds += System.nanoTime() - startedAt;
            state.expandedNodes += used;
            remaining -= used;
            if (route.search.isFound()) {
                route.status = Status.MOVING;
                route.bestDistance = route.search.solver.getDistance(route.search.start);
                route.lastProgressAt = world.getTotalWorldTime();
                state.completedBuilds++;
            } else if (route.search.isExhausted()) {
                fail(state, route, false);
            }
        }
    }

    static void invalidate(World world, BlockPos position) {
        WorldState state = WORLD_STATES.get(world);
        if (state == null) {
            return;
        }
        for (LocalRoute route : state.routes.values()) {
            state.invalidationChecks++;
            if (route.search.dependsOn(position)
                && route.status != Status.ARRIVED && route.status != Status.FAILED) {
                route.status = Status.FAILED;
                state.invalidatedRoutes++;
            } else {
                state.ignoredInvalidations++;
            }
        }
    }

    static void invalidateChunk(World world, int chunkX, int chunkZ) {
        WorldState state = WORLD_STATES.get(world);
        if (state == null) {
            return;
        }
        for (LocalRoute route : state.routes.values()) {
            if (route.search.surface.intersectsChunk(chunkX, chunkZ)
                && route.status != Status.ARRIVED && route.status != Status.FAILED) {
                route.status = Status.FAILED;
                state.invalidatedRoutes++;
            }
        }
    }

    static void clear(World world) {
        WorldState state = WORLD_STATES.remove(world);
        if (state == null || state.requests == 0L) {
            return;
        }
        double buildMillis = state.buildNanoseconds / 1_000_000.0D;
        double nanosPerNode = state.expandedNodes == 0L
            ? 0.0D : (double) state.buildNanoseconds / state.expandedNodes;
        Zomboid.logger.info(
            "Local navigation closed for dimension {}: {} requests, {} accepted, "
                + "{} rejected; {} builds completed, {} failed, {} nodes in {} ms "
                + "({} ns/node); {} steering queries, {} successful, {} arrivals, "
                + "{} stuck failures, {} invalidated routes; dependency invalidation: "
                + "{} route checks, {} ignored.",
            world.provider.getDimension(), state.requests, state.acceptedRequests,
            state.rejectedRequests, state.completedBuilds, state.failedBuilds,
            state.expandedNodes, String.format(Locale.ROOT, "%.2f", buildMillis),
            String.format(Locale.ROOT, "%.1f", nanosPerNode),
            state.steeringQueries, state.successfulSteering, state.arrivals,
            state.stuckFailures, state.invalidatedRoutes,
            state.invalidationChecks, state.ignoredInvalidations
        );
    }

    static RouteSearch createSearch(GroundNavigationCache cache, BlockPos start,
                                    BlockPos destination, int detourRadius,
                                    int maximumNodes, boolean acceptAdjacent) {
        int dx = start.getX() - destination.getX();
        int dz = start.getZ() - destination.getZ();
        int horizontalDistance = (int) Math.ceil(Math.sqrt((double) dx * dx + dz * dz));
        int horizontalRadius = Math.max(2, horizontalDistance + Math.max(0, detourRadius));
        int verticalRadius = Math.max(4,
            Math.abs(start.getY() - destination.getY()) + 2);
        GroundNavigationSurface surface = new GroundNavigationSurface(
            cache, destination, horizontalRadius, verticalRadius
        );
        long startPosition = surface.resolveEntityPositionPacked(
            start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D
        );
        if (startPosition == PackedBlockPosition.NONE) {
            return null;
        }
        long[] goals = new long[5];
        int goalCount = acceptAdjacent
            ? surface.collectArrivalPositions(destination, goals)
            : surface.collectGoalPositions(destination, goals);
        if (goalCount == 0) {
            return null;
        }
        return new RouteSearch(
            surface, new FlowFieldSolver(goals, goalCount, maximumNodes), startPosition
        );
    }

    private static void fail(WorldState state, LocalRoute route, boolean stuck) {
        if (route.status == Status.FAILED) {
            return;
        }
        route.status = Status.FAILED;
        state.failedBuilds++;
        if (stuck) {
            state.stuckFailures++;
        }
    }

    public enum Status {
        NONE,
        BUILDING,
        MOVING,
        ARRIVED,
        FAILED
    }

    static final class RouteSearch {
        private final GroundNavigationSurface surface;
        private final FlowFieldSolver solver;
        private final long start;

        private RouteSearch(GroundNavigationSurface surface, FlowFieldSolver solver,
                            long start) {
            this.surface = surface;
            this.solver = solver;
            this.start = start;
        }

        int advance(int budget) {
            if (isFound() || solver.isComplete()) {
                return 0;
            }
            return solver.stepPacked(budget, surface);
        }

        boolean isFound() {
            return solver.getDistance(start) != Integer.MAX_VALUE;
        }

        boolean isExhausted() {
            return !isFound() && solver.isComplete();
        }

        int distance(long position) {
            return solver.getDistance(position);
        }

        long next(long position, int tieSeed) {
            return solver.bestNextPacked(position, tieSeed);
        }

        boolean dependsOn(BlockPos changedPosition) {
            return surface.mayDependOn(changedPosition)
                && NavigationDependencies.affectsReachedCell(
                    solver, changedPosition
                );
        }
    }

    private static final class LocalRoute {
        private final RouteSearch search;
        private final double speed;
        private Status status = Status.BUILDING;
        private int bestDistance = Integer.MAX_VALUE;
        private long lastProgressAt;

        private LocalRoute(RouteSearch search, double speed, long createdAt) {
            this.search = search;
            this.speed = speed;
            this.lastProgressAt = createdAt;
        }
    }

    private static final class WorldState {
        private final Map<EntityZombie, LocalRoute> routes = new WeakHashMap<>();
        private long requests;
        private long acceptedRequests;
        private long rejectedRequests;
        private long completedBuilds;
        private long failedBuilds;
        private long expandedNodes;
        private long buildNanoseconds;
        private long steeringQueries;
        private long successfulSteering;
        private long arrivals;
        private long stuckFailures;
        private long invalidatedRoutes;
        private long invalidationChecks;
        private long ignoredInvalidations;
    }
}

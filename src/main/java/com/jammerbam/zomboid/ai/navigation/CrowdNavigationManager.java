package com.jammerbam.zomboid.ai.navigation;

import com.jammerbam.zomboid.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Per-tick pursuit cohorts, cell reservations, and temporary collision teams. */
public final class CrowdNavigationManager {
    private static final Logger LOGGER = LogManager.getLogger("zomboid-crowd");
    private static final Map<World, WorldState> WORLD_STATES = new WeakHashMap<>();
    private static final Set<Scoreboard> SANITIZED_SCOREBOARDS =
        java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final String INTERNAL_TEAM_PREFIX = "zbc";

    private CrowdNavigationManager() {
    }

    /** Builds one occupancy/cohort snapshot before entities update. */
    public static void beginTick(World world) {
        if (world.isRemote) {
            return;
        }
        WorldState state = WORLD_STATES.computeIfAbsent(world, ignored -> new WorldState());
        sanitizeScoreboard(world.getScoreboard());
        state.beginTick(world);
    }

    static void sanitizeScoreboard(Scoreboard scoreboard) {
        if (!SANITIZED_SCOREBOARDS.add(scoreboard)) {
            return;
        }
        int removed = 0;
        for (ScorePlayerTeam team : new ArrayList<>(scoreboard.getTeams())) {
            if (isInternalTeamName(team.getName())) {
                scoreboard.removeTeam(team);
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("Removed {} stale Zomboid collision teams from the scoreboard.",
                removed);
        }
    }

    static boolean isInternalTeamName(String name) {
        return name != null && name.startsWith(INTERNAL_TEAM_PREFIX);
    }

    public static boolean canOptimizeCollisionQuery(Entity source) {
        if (!ModConfig.enableCohortCollisionQueryOptimization
            || !(source instanceof EntityZombie) || source.world.isRemote) {
            return false;
        }
        WorldState state = WORLD_STATES.get(source.world);
        return state != null && state.canOptimizeCollisionQuery((EntityZombie) source);
    }

    public static void appendOptimizedEntityCollisions(Entity source, AxisAlignedBB query,
                                                        List<AxisAlignedBB> boxes) {
        WorldState state = WORLD_STATES.get(source.world);
        if (state != null) {
            state.appendEntityCollisions(source, query, boxes);
        }
    }

    static long selectNext(EntityZombie zombie, EntityPlayer target,
                           FlowFieldSolver solver, long current) {
        WorldState state = WORLD_STATES.get(zombie.world);
        if (!ModConfig.enableCrowdAwarePursuit || state == null
            || !state.qualifies(target, ModConfig.crowdSteeringMinimumCohortSize)) {
            return solver.bestNextPacked(current, zombie.getEntityId());
        }

        UUID targetId = target.getUniqueID();
        Direction previous = state.previousDirections.get(zombie);
        long baseline = solver.bestNextPacked(current, zombie.getEntityId());
        long selected = solver.bestNextPacked(
            current, zombie.getEntityId(), candidate -> {
                int allOccupants = count(state.occupancy, candidate);
                int sameTarget = count(state.directedOccupancy.get(targetId), candidate);
                int stationaryOrOther = Math.max(0, allOccupants - sameTarget);
                int reserved = count(state.reservations, candidate);
                int turn = turnPenalty(previous, current, candidate);
                return CrowdSteeringPolicy.score(stationaryOrOther, reserved, turn);
            }
        );
        if (selected == PackedBlockPosition.NONE) {
            return selected;
        }
        increment(state.reservations, selected);
        state.previousDirections.put(zombie, Direction.between(current, selected));
        state.crowdChoices++;
        if (baseline != selected) {
            state.alternateChoices++;
        }
        return selected;
    }

    public static boolean shouldHoldApproach(EntityZombie zombie, EntityPlayer target) {
        WorldState state = WORLD_STATES.get(zombie.world);
        return ModConfig.enableCrowdAwarePursuit && state != null
            && state.qualifies(target, ModConfig.crowdSteeringMinimumCohortSize)
            && zombie.getDistanceSq(target) <= 2.25D;
    }

    /** Avoids a new fallback A* while a large cohort still has movement in flight. */
    public static boolean shouldDeferFallback(EntityZombie zombie, EntityPlayer target) {
        WorldState state = WORLD_STATES.get(zombie.world);
        return ModConfig.enableCrowdAwarePursuit && state != null
            && state.qualifies(target, ModConfig.crowdSteeringMinimumCohortSize)
            && (!zombie.getNavigator().noPath() || zombie.getMoveHelper().isUpdating());
    }

    static void clear(World world) {
        WorldState state = WORLD_STATES.remove(world);
        if (state != null) {
            state.clear(world);
        }
    }

    private static int turnPenalty(Direction previous, long current, long candidate) {
        if (previous == null) {
            return 0;
        }
        Direction next = Direction.between(current, candidate);
        if (next.dx == previous.dx && next.dz == previous.dz) {
            return 0;
        }
        return next.dx == -previous.dx && next.dz == -previous.dz ? 2 : 1;
    }

    private static int count(Map<Long, Integer> values, long key) {
        if (values == null) {
            return 0;
        }
        Integer value = values.get(key);
        return value == null ? 0 : value;
    }

    private static void increment(Map<Long, Integer> values, long key) {
        values.put(key, count(values, key) + 1);
    }

    private static final class WorldState {
        private final Map<UUID, Integer> cohortSizes = new HashMap<>();
        private final Map<Long, Integer> occupancy = new HashMap<>();
        private final Map<UUID, Map<Long, Integer>> directedOccupancy = new HashMap<>();
        private final Map<Long, Integer> reservations = new HashMap<>();
        private final Map<EntityZombie, Direction> previousDirections = new WeakHashMap<>();
        private final Map<EntityZombie, ScorePlayerTeam> assignments = new WeakHashMap<>();
        private final Map<UUID, ScorePlayerTeam> targetTeams = new HashMap<>();
        private final Map<Long, List<Entity>> collisionEntities = new HashMap<>();
        private final ArrayDeque<List<Entity>> collisionBucketPool = new ArrayDeque<>();
        private final List<Entity> directCollisionEntities = new ArrayList<>();
        private boolean collisionIndexReady;
        private long snapshots;
        private long zombiesObserved;
        private long directedObserved;
        private long qualifyingCohorts;
        private long collisionAssignments;
        private long collisionReleases;
        private long teamConflicts;
        private long optimizedCollisionQueries;
        private long collisionIndexBuilds;
        private long collisionEntitiesIndexed;
        private long directCollisionEntitiesIndexed;
        private long collisionBucketsAllocated;
        private int peakCollisionBuckets;
        private long collisionCandidatesExamined;
        private long cohortCollisionCandidatesSkipped;
        private long crowdChoices;
        private long alternateChoices;

        private void beginTick(World world) {
            cohortSizes.clear();
            occupancy.clear();
            directedOccupancy.clear();
            reservations.clear();
            recycleCollisionIndex();
            snapshots++;

            List<EntityZombie> directed = new ArrayList<>();
            for (Entity entity : world.loadedEntityList) {
                if (!(entity instanceof EntityZombie) || !entity.isEntityAlive()) {
                    continue;
                }
                EntityZombie zombie = (EntityZombie) entity;
                zombiesObserved++;
                long cell = PackedBlockPosition.pack(
                    (int) Math.floor(zombie.posX),
                    (int) Math.floor(zombie.getEntityBoundingBox().minY + 0.5D),
                    (int) Math.floor(zombie.posZ)
                );
                increment(occupancy, cell);
                EntityLivingBase target = zombie.getAttackTarget();
                if (!(target instanceof EntityPlayer) || !target.isEntityAlive()) {
                    continue;
                }
                UUID targetId = target.getUniqueID();
                cohortSizes.put(targetId, cohortSize(targetId) + 1);
                directedOccupancy.computeIfAbsent(targetId, ignored -> new HashMap<>());
                increment(directedOccupancy.get(targetId), cell);
                directed.add(zombie);
                directedObserved++;
            }

            Set<UUID> qualifyingTargets = new HashSet<>();
            for (Map.Entry<UUID, Integer> entry : cohortSizes.entrySet()) {
                if (CrowdSteeringPolicy.qualifies(
                    entry.getValue(), ModConfig.crowdCollisionMinimumCohortSize
                )) {
                    qualifyingTargets.add(entry.getKey());
                    qualifyingCohorts++;
                }
            }
            previousDirections.keySet().retainAll(directed);
            updateCollisionAssignments(world, directed, qualifyingTargets);
            if (ModConfig.enableCohortCollisionQueryOptimization
                && !assignments.isEmpty()) {
                buildCollisionIndex(world);
            }
        }

        private void recycleCollisionIndex() {
            collisionIndexReady = false;
            directCollisionEntities.clear();
            for (List<Entity> bucket : collisionEntities.values()) {
                bucket.clear();
                collisionBucketPool.addLast(bucket);
            }
            collisionEntities.clear();
        }

        private void buildCollisionIndex(World world) {
            collisionIndexBuilds++;
            for (Entity entity : world.loadedEntityList) {
                if (entity == null || entity instanceof EntityPlayer) {
                    continue;
                }
                AxisAlignedBB bounds = entity.getEntityBoundingBox();
                if (CollisionIndexPolicy.requiresDirectScan(
                    bounds, entity.motionX, entity.motionZ
                )) {
                    directCollisionEntities.add(entity);
                    directCollisionEntitiesIndexed++;
                    continue;
                }
                int x = (int) Math.floor((bounds.minX + bounds.maxX) * 0.5D);
                int z = (int) Math.floor((bounds.minZ + bounds.maxZ) * 0.5D);
                collisionBucket(x, z).add(entity);
                collisionEntitiesIndexed++;
            }
            peakCollisionBuckets = Math.max(peakCollisionBuckets, collisionEntities.size());
            collisionIndexReady = true;
        }

        private List<Entity> collisionBucket(int x, int z) {
            long key = collisionCell(x, z);
            List<Entity> bucket = collisionEntities.get(key);
            if (bucket == null) {
                bucket = collisionBucketPool.pollFirst();
                if (bucket == null) {
                    bucket = new ArrayList<>(8);
                    collisionBucketsAllocated++;
                }
                collisionEntities.put(key, bucket);
            }
            return bucket;
        }

        private boolean canOptimizeCollisionQuery(EntityZombie source) {
            return collisionIndexReady && assignments.containsKey(source);
        }

        private void appendEntityCollisions(Entity source, AxisAlignedBB query,
                                            List<AxisAlignedBB> boxes) {
            ScorePlayerTeam sourceTeam = source instanceof EntityZombie
                ? assignments.get((EntityZombie) source) : null;
            if (sourceTeam == null) {
                return;
            }
            optimizedCollisionQueries++;
            AxisAlignedBB search = query.grow(CollisionIndexPolicy.QUERY_MARGIN);
            int minX = (int) Math.floor(search.minX);
            int maxX = (int) Math.floor(search.maxX);
            int minZ = (int) Math.floor(search.minZ);
            int maxZ = (int) Math.floor(search.maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    List<Entity> candidates = collisionEntities.get(collisionCell(x, z));
                    if (candidates == null) {
                        continue;
                    }
                    for (Entity candidate : candidates) {
                        appendCandidateCollision(source, query, boxes, sourceTeam, candidate);
                    }
                }
            }
            for (Entity candidate : directCollisionEntities) {
                appendCandidateCollision(source, query, boxes, sourceTeam, candidate);
            }
            // Players are few and may sprint or teleport after the start-of-tick snapshot. A
            // direct pass keeps player contact exact without restoring the broad entity scan.
            for (EntityPlayer player : source.world.playerEntities) {
                appendCandidateCollision(source, query, boxes, sourceTeam, player);
            }
        }

        private void appendCandidateCollision(Entity source, AxisAlignedBB query,
                                              List<AxisAlignedBB> boxes,
                                              ScorePlayerTeam sourceTeam,
                                              Entity candidate) {
            if (candidate == source
                || candidate instanceof EntityPlayer && ((EntityPlayer) candidate).isSpectator()
                || source.isRidingSameEntity(candidate)) {
                return;
            }
            collisionCandidatesExamined++;
            if (candidate instanceof EntityZombie && candidate.getTeam() == sourceTeam) {
                cohortCollisionCandidatesSkipped++;
                return;
            }
            AxisAlignedBB collision = candidate.getCollisionBoundingBox();
            if (collision != null && collision.intersects(query)) {
                boxes.add(collision);
            }
            collision = source.getCollisionBox(candidate);
            if (collision != null && collision.intersects(query)) {
                boxes.add(collision);
            }
        }

        private long collisionCell(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        private boolean qualifies(EntityPlayer target, int minimum) {
            return CrowdSteeringPolicy.qualifies(
                cohortSize(target.getUniqueID()), minimum
            );
        }

        private int cohortSize(UUID targetId) {
            Integer size = cohortSizes.get(targetId);
            return size == null ? 0 : size;
        }

        private void updateCollisionAssignments(World world,
                                                List<EntityZombie> directed,
                                                Set<UUID> qualifyingTargets) {
            Iterator<Map.Entry<EntityZombie, ScorePlayerTeam>> assigned =
                assignments.entrySet().iterator();
            while (assigned.hasNext()) {
                Map.Entry<EntityZombie, ScorePlayerTeam> entry = assigned.next();
                EntityZombie zombie = entry.getKey();
                EntityLivingBase target = zombie == null ? null : zombie.getAttackTarget();
                UUID targetId = target instanceof EntityPlayer ? target.getUniqueID() : null;
                ScorePlayerTeam desired = targetId == null ? null : targetTeams.get(targetId);
                if (!ModConfig.enableCohortCollisionSuppression
                    || !qualifyingTargets.contains(targetId)
                    || desired != entry.getValue()) {
                    removeAssignment(world.getScoreboard(), zombie, entry.getValue());
                    assigned.remove();
                }
            }

            if (!ModConfig.enableCohortCollisionSuppression) {
                removeEmptyTeams(world.getScoreboard());
                return;
            }
            for (EntityZombie zombie : directed) {
                EntityPlayer target = (EntityPlayer) zombie.getAttackTarget();
                UUID targetId = target.getUniqueID();
                if (!qualifyingTargets.contains(targetId) || assignments.containsKey(zombie)) {
                    continue;
                }
                if (zombie.getTeam() != null) {
                    teamConflicts++;
                    continue;
                }
                ScorePlayerTeam team = targetTeams.computeIfAbsent(
                    targetId, ignored -> createTeam(world.getScoreboard(), targetId)
                );
                if (world.getScoreboard().addPlayerToTeam(
                    zombie.getCachedUniqueIdString(), team.getName()
                )) {
                    assignments.put(zombie, team);
                    collisionAssignments++;
                }
            }
            removeEmptyTeams(world.getScoreboard());
        }

        private ScorePlayerTeam createTeam(Scoreboard scoreboard, UUID targetId) {
            String stem = INTERNAL_TEAM_PREFIX
                + Integer.toUnsignedString(targetId.hashCode(), 36);
            String name = stem.length() <= 14 ? stem : stem.substring(0, 14);
            int suffix = 0;
            while (scoreboard.getTeam(name) != null) {
                String tail = Integer.toUnsignedString(++suffix, 36);
                name = stem.substring(0, Math.min(stem.length(), 16 - tail.length())) + tail;
            }
            ScorePlayerTeam team = scoreboard.createTeam(name);
            // MCP's misleading HIDE_FOR_OWN_TEAM constant serializes as
            // pushOtherTeams: teammates do not push; players outside the team do.
            team.setCollisionRule(Team.CollisionRule.HIDE_FOR_OWN_TEAM);
            return team;
        }

        private void removeAssignment(Scoreboard scoreboard, EntityZombie zombie,
                                      ScorePlayerTeam team) {
            if (zombie != null && zombie.getTeam() == team
                && team.getMembershipCollection().contains(zombie.getCachedUniqueIdString())) {
                scoreboard.removePlayerFromTeam(zombie.getCachedUniqueIdString(), team);
                collisionReleases++;
            }
        }

        private void removeEmptyTeams(Scoreboard scoreboard) {
            Iterator<Map.Entry<UUID, ScorePlayerTeam>> teams = targetTeams.entrySet().iterator();
            while (teams.hasNext()) {
                ScorePlayerTeam team = teams.next().getValue();
                if (team.getMembershipCollection().isEmpty()) {
                    scoreboard.removeTeam(team);
                    teams.remove();
                }
            }
        }

        private void clear(World world) {
            Scoreboard scoreboard = world.getScoreboard();
            for (Map.Entry<EntityZombie, ScorePlayerTeam> entry : assignments.entrySet()) {
                removeAssignment(scoreboard, entry.getKey(), entry.getValue());
            }
            assignments.clear();
            for (ScorePlayerTeam team : targetTeams.values()) {
                if (scoreboard.getTeam(team.getName()) == team) {
                    scoreboard.removeTeam(team);
                }
            }
            targetTeams.clear();
            LOGGER.info(
                "Crowd navigation closed for dimension {}: {} snapshots, {} zombie "
                    + "observations, {} directed, {} qualifying cohorts; {} steering "
                    + "choices, {} alternate exits; {} collision assignments, {} releases, "
                    + "{} existing-team conflicts; collision index: {} builds, {} ordinary "
                    + "entities, {} direct-scan entities, {} peak buckets, {} bucket "
                    + "allocations; {} optimized collision queries, {} candidates examined, "
                    + "{} same-cohort candidates skipped.",
                world.provider.getDimension(), snapshots, zombiesObserved, directedObserved,
                qualifyingCohorts, crowdChoices, alternateChoices,
                collisionAssignments, collisionReleases, teamConflicts,
                collisionIndexBuilds, collisionEntitiesIndexed,
                directCollisionEntitiesIndexed, peakCollisionBuckets,
                collisionBucketsAllocated,
                optimizedCollisionQueries, collisionCandidatesExamined,
                cohortCollisionCandidatesSkipped
            );
        }
    }

    private static final class Direction {
        private final int dx;
        private final int dz;

        private Direction(int dx, int dz) {
            this.dx = Integer.signum(dx);
            this.dz = Integer.signum(dz);
        }

        private static Direction between(long from, long to) {
            return new Direction(
                PackedBlockPosition.x(to) - PackedBlockPosition.x(from),
                PackedBlockPosition.z(to) - PackedBlockPosition.z(from)
            );
        }
    }
}

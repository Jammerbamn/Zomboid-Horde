package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/** Incremental unweighted reverse search shared by every agent pursuing one goal. */
public final class FlowFieldSolver {
    public interface NeighborProvider {
        Iterable<BlockPos> neighbors(BlockPos position);
    }

    public interface PackedNeighborProvider {
        int collectNeighbors(long position, long[] output);
    }

    interface PackedCandidateScorer {
        int score(long candidate);
    }

    private static final int MAX_NEIGHBORS = 8;
    private static final int ROUTE_MASK = 0x0F;
    private static final int ROUTE_HEIGHT_SHIFT = 4;
    private static final int[] ROUTE_X = {1, -1, 0, 0};
    private static final int[] ROUTE_Z = {0, 0, 1, -1};

    private final PrimitiveLongIntMap distances;
    private final long[] frontier;
    private final long[] neighborScratch = new long[MAX_NEIGHBORS];
    private final int maximumNodes;
    private int frontierHead;
    private int frontierTail;
    private int processedNodes;

    public FlowFieldSolver(Collection<BlockPos> goals, int maximumNodes) {
        this.maximumNodes = Math.max(1, maximumNodes);
        distances = new PrimitiveLongIntMap(this.maximumNodes);
        frontier = new long[this.maximumNodes];
        for (BlockPos goal : goals) {
            addGoal(goal.toLong());
        }
    }

    FlowFieldSolver(long[] goals, int goalCount, int maximumNodes) {
        this.maximumNodes = Math.max(1, maximumNodes);
        distances = new PrimitiveLongIntMap(this.maximumNodes);
        frontier = new long[this.maximumNodes];
        for (int i = 0; i < goalCount; i++) {
            addGoal(goals[i]);
        }
    }

    public int step(int budget, NeighborProvider provider) {
        int processed = 0;
        int allowed = Math.max(1, budget);
        while (frontierHead < frontierTail && processed < allowed
            && processedNodes < maximumNodes) {
            long currentKey = frontier[frontierHead++];
            int distance = distances.getOrDefault(currentKey, Integer.MAX_VALUE);
            processed++;
            processedNodes++;
            for (BlockPos neighbor : provider.neighbors(BlockPos.fromLong(currentKey))) {
                long key = neighbor.toLong();
                int proposedDistance = distance + 1;
                int existingDistance = distances.getOrDefault(
                    key, Integer.MAX_VALUE
                );
                if (existingDistance == proposedDistance) {
                    recordRoute(key, currentKey);
                    continue;
                }
                if (existingDistance == Integer.MAX_VALUE
                    && distances.size() < maximumNodes
                    && distances.putIfAbsent(key, proposedDistance)) {
                    recordRoute(key, currentKey);
                    frontier[frontierTail++] = key;
                }
            }
        }
        return processed;
    }

    int stepPacked(int budget, PackedNeighborProvider provider) {
        int processed = 0;
        int allowed = Math.max(1, budget);
        while (frontierHead < frontierTail && processed < allowed
            && processedNodes < maximumNodes) {
            long current = frontier[frontierHead++];
            int distance = distances.getOrDefault(current, Integer.MAX_VALUE);
            processed++;
            processedNodes++;
            int neighborCount = provider.collectNeighbors(current, neighborScratch);
            for (int i = 0; i < neighborCount; i++) {
                long neighbor = neighborScratch[i];
                int proposedDistance = distance + 1;
                int existingDistance = distances.getOrDefault(
                    neighbor, Integer.MAX_VALUE
                );
                if (existingDistance == proposedDistance) {
                    recordRoute(neighbor, current);
                    continue;
                }
                if (existingDistance == Integer.MAX_VALUE
                    && distances.size() < maximumNodes
                    && distances.putIfAbsent(neighbor, proposedDistance)) {
                    recordRoute(neighbor, current);
                    frontier[frontierTail++] = neighbor;
                }
            }
        }
        return processed;
    }

    public int getDistance(BlockPos position) {
        return getDistance(position.toLong());
    }

    int getDistance(long position) {
        return distances.getOrDefault(position, Integer.MAX_VALUE);
    }

    boolean hasReached(long position) {
        return distances.getOrDefault(position, Integer.MAX_VALUE) != Integer.MAX_VALUE;
    }

    public BlockPos bestLowerNeighbor(BlockPos current,
                                      Iterable<BlockPos> neighbors,
                                      int tieSeed) {
        int currentDistance = getDistance(current);
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestTie = Integer.MAX_VALUE;
        for (BlockPos candidate : neighbors) {
            int distance = getDistance(candidate);
            if (distance == Integer.MAX_VALUE
                || currentDistance != Integer.MAX_VALUE && distance >= currentDistance) {
                continue;
            }
            int tie = mix(candidate.toLong(), tieSeed);
            if (distance < bestDistance || distance == bestDistance && tie < bestTie) {
                best = candidate;
                bestDistance = distance;
                bestTie = tie;
            }
        }
        return best;
    }

    public boolean isComplete() {
        return frontierHead >= frontierTail || processedNodes >= maximumNodes;
    }

    public int getProcessedNodes() {
        return processedNodes;
    }

    public int getReachableNodeCount() {
        return distances.size();
    }

    public Map<Long, Integer> getDistances() {
        return Collections.unmodifiableMap(distances.boxedSnapshot());
    }

    long bestLowerNeighborPacked(long current, long[] neighbors,
                                 int neighborCount, int tieSeed) {
        int currentDistance = getDistance(current);
        long best = PackedBlockPosition.NONE;
        int bestDistance = Integer.MAX_VALUE;
        int bestTie = Integer.MAX_VALUE;
        for (int i = 0; i < neighborCount; i++) {
            long candidate = neighbors[i];
            int distance = getDistance(candidate);
            if (distance == Integer.MAX_VALUE
                || currentDistance != Integer.MAX_VALUE && distance >= currentDistance) {
                continue;
            }
            int tie = mix(candidate, tieSeed);
            if (distance < bestDistance || distance == bestDistance && tie < bestTie) {
                best = candidate;
                bestDistance = distance;
                bestTie = tie;
            }
        }
        return best;
    }

    /** Selects a precomputed lower-cost exit without re-querying terrain. */
    long bestNextPacked(long current, int tieSeed) {
        return bestNextPacked(current, tieSeed, candidate -> 0);
    }

    /** Selects among precomputed equal-cost exits using a caller-supplied crowd score. */
    long bestNextPacked(long current, int tieSeed, PackedCandidateScorer scorer) {
        int routes = distances.getMetadataOrDefault(current, 0);
        int mask = routes & ROUTE_MASK;
        if (mask == 0) {
            return PackedBlockPosition.NONE;
        }
        int x = PackedBlockPosition.x(current);
        int y = PackedBlockPosition.y(current);
        int z = PackedBlockPosition.z(current);
        long best = PackedBlockPosition.NONE;
        int bestScore = Integer.MAX_VALUE;
        int bestTie = Integer.MAX_VALUE;
        for (int direction = 0; direction < ROUTE_X.length; direction++) {
            if ((mask & 1 << direction) == 0) {
                continue;
            }
            int heightCode = routes >> routeHeightShift(direction) & 0x03;
            long candidate = PackedBlockPosition.pack(
                x + ROUTE_X[direction], y + heightCode - 1,
                z + ROUTE_Z[direction]
            );
            int score = scorer.score(candidate);
            int tie = mix(candidate, tieSeed);
            if (score < bestScore || score == bestScore && tie < bestTie) {
                best = candidate;
                bestScore = score;
                bestTie = tie;
            }
        }
        return best;
    }

    private void addGoal(long goal) {
        if (distances.size() < maximumNodes && distances.putIfAbsent(goal, 0)) {
            frontier[frontierTail++] = goal;
        }
    }

    private void recordRoute(long from, long to) {
        int deltaX = PackedBlockPosition.x(to) - PackedBlockPosition.x(from);
        int deltaY = PackedBlockPosition.y(to) - PackedBlockPosition.y(from);
        int deltaZ = PackedBlockPosition.z(to) - PackedBlockPosition.z(from);
        if (deltaY < -1 || deltaY > 1) {
            return;
        }
        int direction = direction(deltaX, deltaZ);
        if (direction < 0) {
            return;
        }
        int directionBit = 1 << direction;
        int routes = distances.getMetadataOrDefault(from, 0);
        if ((routes & directionBit) != 0) {
            return;
        }
        routes |= directionBit;
        routes |= (deltaY + 1) << routeHeightShift(direction);
        distances.setMetadata(from, routes);
    }

    private static int direction(int deltaX, int deltaZ) {
        for (int direction = 0; direction < ROUTE_X.length; direction++) {
            if (ROUTE_X[direction] == deltaX && ROUTE_Z[direction] == deltaZ) {
                return direction;
            }
        }
        return -1;
    }

    private static int routeHeightShift(int direction) {
        return ROUTE_HEIGHT_SHIFT + direction * 2;
    }

    private static int mix(long value, int seed) {
        long mixed = value ^ seed * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return (int) (mixed & Integer.MAX_VALUE);
    }
}

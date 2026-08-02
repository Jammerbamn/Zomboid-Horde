package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowFieldSolverTest {
    @Test
    public void oneReverseSearchServesMultipleStartsAroundAnObstacle() {
        Set<BlockPos> blocked = new HashSet<>(Arrays.asList(
            new BlockPos(2, 0, 0), new BlockPos(2, 0, 1)
        ));
        FlowFieldSolver solver = new FlowFieldSolver(
            Collections.singletonList(new BlockPos(4, 0, 0)), 100
        );
        FlowFieldSolver.NeighborProvider surface = grid(0, 4, -1, 2, blocked);
        while (!solver.isComplete()) {
            solver.step(2, surface);
        }

        assertEquals(6, solver.getDistance(BlockPos.ORIGIN));
        assertEquals(2, solver.getDistance(new BlockPos(4, 0, 2)));
        assertTrue(solver.getReachableNodeCount() > 2);
    }

    @Test
    public void incrementalBudgetLimitsWorkPerCall() {
        FlowFieldSolver solver = new FlowFieldSolver(
            Collections.singletonList(BlockPos.ORIGIN), 100
        );
        int processed = solver.step(3, grid(-5, 5, -5, 5, Collections.emptySet()));

        assertEquals(3, processed);
        assertEquals(3, solver.getProcessedNodes());
        assertFalse(solver.isComplete());
    }

    @Test
    public void bestNeighborAlwaysLowersTheSharedCost() {
        FlowFieldSolver solver = new FlowFieldSolver(
            Collections.singletonList(BlockPos.ORIGIN), 100
        );
        FlowFieldSolver.NeighborProvider surface =
            grid(-4, 4, -4, 4, Collections.emptySet());
        while (!solver.isComplete()) {
            solver.step(100, surface);
        }
        BlockPos start = new BlockPos(3, 0, 2);
        BlockPos next = solver.bestLowerNeighbor(start, surface.neighbors(start), 7);

        assertTrue(next != null);
        assertEquals(solver.getDistance(start) - 1, solver.getDistance(next));
    }

    @Test
    public void packedTraversalMatchesObjectTraversal() {
        BlockPos goal = new BlockPos(5, 64, -3);
        FlowFieldSolver objectSolver = new FlowFieldSolver(
            Collections.singletonList(goal), 500
        );
        FlowFieldSolver packedSolver = new FlowFieldSolver(
            new long[]{goal.toLong()}, 1, 500
        );
        FlowFieldSolver.NeighborProvider objectSurface =
            grid(-2, 6, -6, 2, Collections.emptySet(), 64);
        FlowFieldSolver.PackedNeighborProvider packedSurface =
            packedGrid(-2, 6, -6, 2, 64);

        while (!objectSolver.isComplete()) {
            objectSolver.step(7, objectSurface);
        }
        while (!packedSolver.isComplete()) {
            packedSolver.stepPacked(7, packedSurface);
        }

        for (int x = -2; x <= 6; x++) {
            for (int z = -6; z <= 2; z++) {
                BlockPos position = new BlockPos(x, 64, z);
                assertEquals(
                    objectSolver.getDistance(position),
                    packedSolver.getDistance(position.toLong())
                );
            }
        }
        assertEquals(
            objectSolver.getReachableNodeCount(),
            packedSolver.getReachableNodeCount()
        );
    }

    @Test
    public void packedNodeLimitCannotOverflowItsPrimitiveQueue() {
        FlowFieldSolver solver = new FlowFieldSolver(
            new long[]{PackedBlockPosition.pack(0, 64, 0)}, 1, 37
        );
        FlowFieldSolver.PackedNeighborProvider surface =
            packedGrid(-50, 50, -50, 50, 64);

        while (!solver.isComplete()) {
            solver.stepPacked(100, surface);
        }

        assertEquals(37, solver.getReachableNodeCount());
        assertTrue(solver.getProcessedNodes() <= 37);
    }

    @Test
    public void packedDirectionFieldSelectsALowerCostCellWithoutTerrainTraversal() {
        FlowFieldSolver solver = new FlowFieldSolver(
            new long[]{PackedBlockPosition.pack(0, 64, 0)}, 1, 500
        );
        FlowFieldSolver.PackedNeighborProvider surface =
            packedGrid(-4, 4, -4, 4, 64);
        while (!solver.isComplete()) {
            solver.stepPacked(17, surface);
        }

        long current = PackedBlockPosition.pack(3, 64, 2);
        long next = solver.bestNextPacked(current, 7);

        assertTrue(next != PackedBlockPosition.NONE);
        assertEquals(solver.getDistance(current) - 1, solver.getDistance(next));
    }

    @Test
    public void packedDirectionFieldRetainsEqualCostAlternatives() {
        FlowFieldSolver solver = new FlowFieldSolver(
            new long[]{PackedBlockPosition.pack(0, 64, 0)}, 1, 100
        );
        FlowFieldSolver.PackedNeighborProvider surface =
            packedGrid(0, 2, 0, 2, 64);
        while (!solver.isComplete()) {
            solver.stepPacked(100, surface);
        }

        long current = PackedBlockPosition.pack(1, 64, 1);
        Set<Long> choices = new HashSet<>();
        for (int seed = 0; seed < 128; seed++) {
            choices.add(solver.bestNextPacked(current, seed));
        }

        assertEquals(2, choices.size());
        for (long next : choices) {
            assertEquals(solver.getDistance(current) - 1, solver.getDistance(next));
        }
    }

    @Test
    public void packedDirectionFieldHonorsCrowdScoreBeforeStableTieBreak() {
        FlowFieldSolver solver = new FlowFieldSolver(
            new long[]{PackedBlockPosition.pack(0, 64, 0)}, 1, 100
        );
        FlowFieldSolver.PackedNeighborProvider surface =
            packedGrid(0, 2, 0, 2, 64);
        while (!solver.isComplete()) {
            solver.stepPacked(100, surface);
        }

        long current = PackedBlockPosition.pack(1, 64, 1);
        long preferred = PackedBlockPosition.pack(1, 64, 0);
        long selected = solver.bestNextPacked(
            current, 7, candidate -> candidate == preferred ? 0 : 100
        );

        assertEquals(preferred, selected);
        assertEquals(solver.getDistance(current) - 1, solver.getDistance(selected));
    }

    @Test
    public void packedDirectionFieldPreservesOneBlockHeightChanges() {
        long start = PackedBlockPosition.pack(0, 64, 0);
        long goal = PackedBlockPosition.pack(1, 65, 0);
        FlowFieldSolver solver = new FlowFieldSolver(
            new long[]{goal}, 1, 10
        );
        FlowFieldSolver.PackedNeighborProvider steppedSurface = (position, output) -> {
            if (position == goal) {
                output[0] = start;
                return 1;
            }
            if (position == start) {
                output[0] = goal;
                return 1;
            }
            return 0;
        };
        while (!solver.isComplete()) {
            solver.stepPacked(10, steppedSurface);
        }

        assertEquals(goal, solver.bestNextPacked(start, 3));
    }

    @Test
    public void packedCoordinatesMatchMinecraftEncoding() {
        int[][] coordinates = {
            {0, 0, 0}, {1, 255, -1}, {-30_000_000, 64, 30_000_000},
            {12345, 12, -67890}
        };
        for (int[] coordinate : coordinates) {
            BlockPos minecraft = new BlockPos(
                coordinate[0], coordinate[1], coordinate[2]
            );
            long packed = PackedBlockPosition.pack(
                coordinate[0], coordinate[1], coordinate[2]
            );
            assertEquals(minecraft.toLong(), packed);
            assertEquals(coordinate[0], PackedBlockPosition.x(packed));
            assertEquals(coordinate[1], PackedBlockPosition.y(packed));
            assertEquals(coordinate[2], PackedBlockPosition.z(packed));
        }
    }

    private static FlowFieldSolver.NeighborProvider grid(
        int minX, int maxX, int minZ, int maxZ, Set<BlockPos> blocked
    ) {
        return grid(minX, maxX, minZ, maxZ, blocked, 0);
    }

    private static FlowFieldSolver.NeighborProvider grid(
        int minX, int maxX, int minZ, int maxZ, Set<BlockPos> blocked, int y
    ) {
        return position -> {
            List<BlockPos> neighbors = new ArrayList<>();
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                BlockPos candidate = position.add(offset[0], 0, offset[1]);
                if (candidate.getX() >= minX && candidate.getX() <= maxX
                    && candidate.getZ() >= minZ && candidate.getZ() <= maxZ
                    && !blocked.contains(candidate)) {
                    neighbors.add(candidate);
                }
            }
            return neighbors;
        };
    }

    private static FlowFieldSolver.PackedNeighborProvider packedGrid(
        int minX, int maxX, int minZ, int maxZ, int y
    ) {
        return (position, output) -> {
            int x = PackedBlockPosition.x(position);
            int z = PackedBlockPosition.z(position);
            int count = 0;
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                int candidateX = x + offset[0];
                int candidateZ = z + offset[1];
                if (candidateX >= minX && candidateX <= maxX
                    && candidateZ >= minZ && candidateZ <= maxZ) {
                    output[count++] = PackedBlockPosition.pack(
                        candidateX, y, candidateZ
                    );
                }
            }
            return count;
        };
    }
}

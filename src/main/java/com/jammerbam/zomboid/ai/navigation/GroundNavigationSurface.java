package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A conservative block-aware walking surface for ordinary ground zombies. */
public final class GroundNavigationSurface implements FlowFieldSolver.NeighborProvider,
    FlowFieldSolver.PackedNeighborProvider {
    private static final int[] X_OFFSETS = {1, -1, 0, 0};
    private static final int[] Z_OFFSETS = {0, 0, 1, -1};
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1};
    private static final int[] APPROACH_X = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] APPROACH_Z = {0, 1, 1, 1, 0, -1, -1, -1};

    private final GroundNavigationCache cache;
    private final BlockPos center;
    private final int horizontalRadius;
    private final int verticalRadius;

    GroundNavigationSurface(GroundNavigationCache cache, BlockPos center,
                            int horizontalRadius, int verticalRadius) {
        this.cache = cache;
        this.center = center.toImmutable();
        this.horizontalRadius = Math.max(1, horizontalRadius);
        this.verticalRadius = Math.max(1, verticalRadius);
    }

    @Override
    public Iterable<BlockPos> neighbors(BlockPos position) {
        List<BlockPos> result = new ArrayList<>(4);
        long[] packed = new long[4];
        int count = collectNeighbors(position.toLong(), packed);
        for (int i = 0; i < count; i++) {
            result.add(BlockPos.fromLong(packed[i]));
        }
        return result;
    }

    @Override
    public int collectNeighbors(long position, long[] output) {
        int x = PackedBlockPosition.x(position);
        int y = PackedBlockPosition.y(position);
        int z = PackedBlockPosition.z(position);
        int count = 0;
        for (int i = 0; i < X_OFFSETS.length; i++) {
            long neighbor = resolveStandPositionPacked(
                x + X_OFFSETS[i], z + Z_OFFSETS[i], y
            );
            if (neighbor != PackedBlockPosition.NONE) {
                output[count++] = neighbor;
            }
        }
        return count;
    }

    public List<BlockPos> goalPositions(BlockPos target) {
        long[] packed = new long[4];
        int count = collectGoalPositions(target, packed);
        if (count == 1) {
            return Collections.singletonList(BlockPos.fromLong(packed[0]));
        }
        List<BlockPos> goals = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            goals.add(BlockPos.fromLong(packed[i]));
        }
        return goals;
    }

    int collectGoalPositions(BlockPos target, long[] output) {
        long primary = resolveStandPositionPacked(
            target.getX(), target.getZ(), target.getY()
        );
        if (primary != PackedBlockPosition.NONE) {
            output[0] = primary;
            return 1;
        }
        // If a partial-height block makes the exact player column unresolvable,
        // use nearby valid ground rather than disabling the whole shared field.
        int count = 0;
        for (int i = 0; i < X_OFFSETS.length; i++) {
            long goal = resolveStandPositionPacked(
                target.getX() + X_OFFSETS[i], target.getZ() + Z_OFFSETS[i],
                target.getY()
            );
            if (goal == PackedBlockPosition.NONE || contains(output, count, goal)) {
                continue;
            }
            output[count++] = goal;
        }
        return count;
    }

    int collectArrivalPositions(BlockPos target, long[] output) {
        int count = 0;
        long primary = resolveStandPositionPacked(
            target.getX(), target.getZ(), target.getY()
        );
        if (primary != PackedBlockPosition.NONE) {
            output[count++] = primary;
        }
        for (int i = 0; i < X_OFFSETS.length; i++) {
            long goal = resolveStandPositionPacked(
                target.getX() + X_OFFSETS[i], target.getZ() + Z_OFFSETS[i],
                target.getY()
            );
            if (goal != PackedBlockPosition.NONE && !contains(output, count, goal)) {
                output[count++] = goal;
            }
        }
        return count;
    }

    int collectApproachPositions(BlockPos target, long[] output) {
        int count = 0;
        for (int i = 0; i < APPROACH_X.length; i++) {
            long goal = resolveStandPositionPacked(
                target.getX() + APPROACH_X[i], target.getZ() + APPROACH_Z[i],
                target.getY()
            );
            if (goal != PackedBlockPosition.NONE && !contains(output, count, goal)) {
                output[count++] = goal;
            }
        }
        if (count == 0) {
            return collectGoalPositions(target, output);
        }
        return count;
    }

    @Nullable
    public BlockPos resolveEntityPosition(double x, double y, double z) {
        long packed = resolveEntityPositionPacked(x, y, z);
        return packed == PackedBlockPosition.NONE ? null : BlockPos.fromLong(packed);
    }

    long resolveEntityPositionPacked(double x, double y, double z) {
        return resolveStandPositionPacked(
            (int) Math.floor(x), (int) Math.floor(z), (int) Math.floor(y + 0.5D)
        );
    }

    public double movementY(BlockPos feet) {
        return cache.movementY(feet);
    }

    double movementY(long feet) {
        return cache.movementY(
            PackedBlockPosition.x(feet),
            PackedBlockPosition.y(feet),
            PackedBlockPosition.z(feet)
        );
    }

    public boolean contains(BlockPos position) {
        return contains(position.getX(), position.getY(), position.getZ());
    }

    private boolean contains(int x, int y, int z) {
        long dx = x - center.getX();
        long dz = z - center.getZ();
        return dx * dx + dz * dz <= (long) horizontalRadius * horizontalRadius
            && Math.abs(y - center.getY()) <= verticalRadius;
    }

    boolean mayDependOn(BlockPos position) {
        long dx = position.getX() - center.getX();
        long dz = position.getZ() - center.getZ();
        long dependencyRadius = horizontalRadius + 1L;
        return dx * dx + dz * dz <= dependencyRadius * dependencyRadius
            && Math.abs(position.getY() - center.getY()) <= verticalRadius + 1;
    }

    boolean intersectsChunk(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int closestX = Math.max(minX, Math.min(minX + 15, center.getX()));
        int closestZ = Math.max(minZ, Math.min(minZ + 15, center.getZ()));
        long dx = closestX - center.getX();
        long dz = closestZ - center.getZ();
        return dx * dx + dz * dz <= (long) horizontalRadius * horizontalRadius;
    }

    private long resolveStandPositionPacked(int x, int z, int referenceY) {
        for (int offset : HEIGHT_OFFSETS) {
            int y = referenceY + offset;
            if (contains(x, y, z) && cache.isStandable(x, y, z)) {
                return PackedBlockPosition.pack(x, y, z);
            }
        }
        return PackedBlockPosition.NONE;
    }

    private static boolean contains(long[] values, int count, long candidate) {
        for (int i = 0; i < count; i++) {
            if (values[i] == candidate) {
                return true;
            }
        }
        return false;
    }
}

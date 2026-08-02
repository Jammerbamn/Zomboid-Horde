package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazily classifies ground-navigation cells and retains them in chunk-aligned,
 * vertically layered snapshots. The cache is server-thread-owned by one world.
 */
final class GroundNavigationCache {
    private static final byte UNKNOWN = 0;
    private static final byte BLOCKED = 1;
    private static final byte STANDABLE = 2;

    private final CellClassifier classifier;
    private final Map<Long, ChunkSnapshot> chunks = new HashMap<>();
    private long cachedCellCount;
    private long classificationCount;
    private long cacheHitCount;
    private long invalidationCount;

    GroundNavigationCache(World world) {
        this(feet -> classify(world, feet));
    }

    GroundNavigationCache(CellClassifier classifier) {
        this.classifier = classifier;
    }

    boolean isStandable(BlockPos feet) {
        return isStandable(feet.getX(), feet.getY(), feet.getZ());
    }

    boolean isStandable(int x, int y, int z) {
        return loadState(x, y, z) == STANDABLE;
    }

    double movementY(BlockPos feet) {
        return movementY(feet.getX(), feet.getY(), feet.getZ());
    }

    double movementY(int x, int y, int z) {
        if (loadState(x, y, z) != STANDABLE) {
            return y;
        }
        ChunkSnapshot chunk = chunks.get(chunkKey(x >> 4, z >> 4));
        return chunk == null ? y : chunk.movementY(x, y, z);
    }

    void invalidate(BlockPos changedBlock) {
        // A changed block may be the head, feet, or support of a cached cell.
        // Neighboring collision shapes (notably fences and walls) may also change.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos column = changedBlock.add(x, 0, z);
                invalidateFeet(column.down());
                invalidateFeet(column);
                invalidateFeet(column.up());
            }
        }
    }

    void invalidateChunk(int chunkX, int chunkZ) {
        ChunkSnapshot removed = chunks.remove(chunkKey(chunkX, chunkZ));
        if (removed != null) {
            cachedCellCount -= removed.size();
            invalidationCount += removed.size();
        }
    }

    long getCachedCellCount() {
        return cachedCellCount;
    }

    long getClassificationCount() {
        return classificationCount;
    }

    long getCacheHitCount() {
        return cacheHitCount;
    }

    long getInvalidationCount() {
        return invalidationCount;
    }

    private byte loadState(int x, int y, int z) {
        long key = chunkKey(x >> 4, z >> 4);
        ChunkSnapshot chunk = chunks.get(key);
        byte state = chunk == null ? UNKNOWN : chunk.state(x, y, z);
        if (state != UNKNOWN) {
            cacheHitCount++;
            return state;
        }

        Classification classification = classifier.classify(new BlockPos(x, y, z));
        classificationCount++;
        // null means the cell could not safely be inspected (normally because
        // its chunk is unloaded), so do not turn temporary absence into terrain.
        if (classification == null) {
            return UNKNOWN;
        }
        if (chunk == null) {
            chunk = new ChunkSnapshot();
            chunks.put(key, chunk);
        }
        chunk.store(x, y, z, classification);
        cachedCellCount++;
        return classification.standable ? STANDABLE : BLOCKED;
    }

    private void invalidateFeet(BlockPos feet) {
        long key = chunkKey(feet.getX() >> 4, feet.getZ() >> 4);
        ChunkSnapshot chunk = chunks.get(key);
        if (chunk != null && chunk.invalidate(feet)) {
            cachedCellCount--;
            invalidationCount++;
            if (chunk.size() == 0) {
                chunks.remove(key);
            }
        }
    }

    @Nullable
    private static Classification classify(World world, BlockPos feet) {
        if (feet.getY() <= 0 || feet.getY() >= world.getHeight() - 1
            || !world.getWorldBorder().contains(feet)) {
            return Classification.blocked();
        }
        if (!world.isBlockLoaded(feet, false)
            || !world.isBlockLoaded(feet.up(), false)
            || !world.isBlockLoaded(feet.down(), false)) {
            return null;
        }
        if (!isClear(world, feet) || !isClear(world, feet.up())) {
            return Classification.blocked();
        }
        IBlockState support = world.getBlockState(feet.down());
        if (isHazard(support)) {
            return Classification.blocked();
        }
        AxisAlignedBB box = support.getCollisionBoundingBox(world, feet.down());
        if (box == null || box == Block.NULL_AABB
            || box.maxY <= 0.0D || box.maxY > 1.0D) {
            return Classification.blocked();
        }
        return Classification.standable(
            feet.getY() - 1.0D + Math.min(1.0D, box.maxY)
        );
    }

    private static boolean isClear(World world, BlockPos position) {
        IBlockState state = world.getBlockState(position);
        if (isHazard(state)) {
            return false;
        }
        AxisAlignedBB box = state.getCollisionBoundingBox(world, position);
        return box == null || box == Block.NULL_AABB;
    }

    private static boolean isHazard(IBlockState state) {
        Material material = state.getMaterial();
        Block block = state.getBlock();
        return material == Material.WATER || material == Material.LAVA
            || block == Blocks.FIRE || block == Blocks.CACTUS
            || block == Blocks.MAGMA;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    interface CellClassifier {
        @Nullable
        Classification classify(BlockPos feet);
    }

    static final class Classification {
        private static final Classification BLOCKED_CELL =
            new Classification(false, 0.0F);

        private final boolean standable;
        private final float movementY;

        private Classification(boolean standable, float movementY) {
            this.standable = standable;
            this.movementY = movementY;
        }

        static Classification blocked() {
            return BLOCKED_CELL;
        }

        static Classification standable(double movementY) {
            return new Classification(true, (float) movementY);
        }
    }

    private static final class ChunkSnapshot {
        private final Map<Integer, Section> sections = new HashMap<>();
        private int size;

        private byte state(int x, int y, int z) {
            Section section = sections.get(y >> 4);
            return section == null ? UNKNOWN : section.states[index(x, y, z)];
        }

        private double movementY(int x, int y, int z) {
            Section section = sections.get(y >> 4);
            return section == null ? y : section.movementY[index(x, y, z)];
        }

        private void store(int x, int y, int z, Classification classification) {
            int sectionY = y >> 4;
            Section section = sections.computeIfAbsent(sectionY, ignored -> new Section());
            int index = index(x, y, z);
            if (section.states[index] == UNKNOWN) {
                section.size++;
                size++;
            }
            section.states[index] = classification.standable ? STANDABLE : BLOCKED;
            section.movementY[index] = classification.movementY;
        }

        private boolean invalidate(BlockPos position) {
            int sectionY = position.getY() >> 4;
            Section section = sections.get(sectionY);
            if (section == null) {
                return false;
            }
            int index = index(position.getX(), position.getY(), position.getZ());
            if (section.states[index] == UNKNOWN) {
                return false;
            }
            section.states[index] = UNKNOWN;
            section.movementY[index] = 0.0F;
            section.size--;
            size--;
            if (section.size == 0) {
                sections.remove(sectionY);
            }
            return true;
        }

        private int size() {
            return size;
        }

        private static int index(int x, int y, int z) {
            return (y & 15) << 8 | (z & 15) << 4 | x & 15;
        }
    }

    private static final class Section {
        private final byte[] states = new byte[4096];
        private final float[] movementY = new float[4096];
        private int size;
    }
}

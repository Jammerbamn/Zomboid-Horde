package com.jammerbam.zomboid.ai.navigation;

import com.jammerbam.zomboid.Zomboid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Coordinates terrain invalidation and lifecycle across all navigation modes. */
public final class NavigationManager {
    private static final Map<World, InvalidationState> INVALIDATIONS =
        new WeakHashMap<>();

    private NavigationManager() {
    }

    public static void invalidate(World world, BlockPos position) {
        InvalidationState state = INVALIDATIONS.computeIfAbsent(
            world, ignored -> new InvalidationState()
        );
        state.requests++;
        if (!state.batch.accept(world.getTotalWorldTime(), position.toLong())) {
            state.duplicates++;
            return;
        }
        state.processed++;
        NavigationTerrainManager.invalidate(world, position);
        SharedNavigationManager.invalidateFields(world, position);
        LocalPointNavigationManager.invalidate(world, position);
    }

    public static void invalidateChunk(World world, int chunkX, int chunkZ) {
        NavigationTerrainManager.invalidateChunk(world, chunkX, chunkZ);
        SharedNavigationManager.invalidateFieldChunks(world, chunkX, chunkZ);
        LocalPointNavigationManager.invalidateChunk(world, chunkX, chunkZ);
    }

    public static void clear(World world) {
        CrowdNavigationManager.clear(world);
        SharedNavigationManager.clearFields(world);
        LocalPointNavigationManager.clear(world);
        NavigationTerrainManager.clear(world);
        InvalidationState state = INVALIDATIONS.remove(world);
        if (state != null && state.requests > 0L) {
            Zomboid.logger.info(
                "Navigation invalidations closed for dimension {}: {} callbacks, "
                    + "{} unique positions, {} same-tick duplicates suppressed.",
                world.provider.getDimension(), state.requests, state.processed,
                state.duplicates
            );
        }
    }

    static final class SameTickInvalidationBatch {
        private final Set<Long> positions = new HashSet<>();
        private long tick = Long.MIN_VALUE;

        boolean accept(long worldTick, long position) {
            if (tick != worldTick) {
                tick = worldTick;
                positions.clear();
            }
            return positions.add(position);
        }
    }

    private static final class InvalidationState {
        private final SameTickInvalidationBatch batch = new SameTickInvalidationBatch();
        private long requests;
        private long processed;
        private long duplicates;
    }
}

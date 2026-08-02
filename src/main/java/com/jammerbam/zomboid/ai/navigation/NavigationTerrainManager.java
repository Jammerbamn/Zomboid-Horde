package com.jammerbam.zomboid.ai.navigation;

import com.jammerbam.zomboid.Zomboid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Owns the one loaded-chunk-only ground cache shared by every navigation mode. */
final class NavigationTerrainManager {
    private static final Map<World, GroundNavigationCache> CACHES = new WeakHashMap<>();

    private NavigationTerrainManager() {
    }

    static GroundNavigationCache get(World world) {
        return CACHES.computeIfAbsent(world, GroundNavigationCache::new);
    }

    static void invalidate(World world, BlockPos position) {
        GroundNavigationCache cache = CACHES.get(world);
        if (cache != null) {
            cache.invalidate(position);
        }
    }

    static void invalidateChunk(World world, int chunkX, int chunkZ) {
        GroundNavigationCache cache = CACHES.get(world);
        if (cache != null) {
            cache.invalidateChunk(chunkX, chunkZ);
        }
    }

    static void clear(World world) {
        GroundNavigationCache cache = CACHES.remove(world);
        if (cache == null || cache.getClassificationCount() == 0L) {
            return;
        }
        long classifications = cache.getClassificationCount();
        long hits = cache.getCacheHitCount();
        long queries = hits + classifications;
        double hitRate = queries == 0L ? 0.0D : 100.0D * hits / queries;
        Zomboid.logger.info(
            "Navigation terrain cache closed for dimension {}: {} cells retained, "
                + "{} terrain classifications, {} cache hits ({}% hit rate), "
                + "{} invalidations.",
            world.provider.getDimension(),
            cache.getCachedCellCount(), classifications, hits,
            String.format(Locale.ROOT, "%.1f", hitRate),
            cache.getInvalidationCount()
        );
    }
}

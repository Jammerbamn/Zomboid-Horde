package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroundNavigationCacheTest {
    @Test
    public void classifiesEachExactLayerOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        GroundNavigationCache cache = new GroundNavigationCache(position -> {
            calls.incrementAndGet();
            return GroundNavigationCache.Classification.standable(
                position.getY() + 0.375D
            );
        });
        BlockPos lower = new BlockPos(4, 20, 7);
        BlockPos upper = new BlockPos(4, 52, 7);

        assertTrue(cache.isStandable(lower));
        assertEquals(20.375D, cache.movementY(lower), 0.0001D);
        assertTrue(cache.isStandable(upper));
        assertTrue(cache.isStandable(lower));

        assertEquals(2, calls.get());
        assertEquals(2L, cache.getCachedCellCount());
        assertEquals(2L, cache.getClassificationCount());
        assertEquals(2L, cache.getCacheHitCount());
    }

    @Test
    public void blockChangeInvalidatesDependentAndConnectedCells() {
        AtomicInteger calls = new AtomicInteger();
        GroundNavigationCache cache = new GroundNavigationCache(position -> {
            calls.incrementAndGet();
            return GroundNavigationCache.Classification.standable(position.getY());
        });
        BlockPos changed = new BlockPos(10, 40, 10);
        BlockPos supportDependent = changed.up();
        BlockPos connectedNeighbor = changed.east();
        BlockPos unaffected = changed.add(3, 0, 0);
        cache.isStandable(supportDependent);
        cache.isStandable(connectedNeighbor);
        cache.isStandable(unaffected);

        cache.invalidate(changed);
        assertEquals(1L, cache.getCachedCellCount());
        assertTrue(cache.isStandable(supportDependent));
        assertTrue(cache.isStandable(connectedNeighbor));
        assertTrue(cache.isStandable(unaffected));

        assertEquals(5, calls.get());
        assertEquals(2L, cache.getInvalidationCount());
    }

    @Test
    public void chunkUnloadDropsOnlyThatChunksSnapshot() {
        GroundNavigationCache cache = new GroundNavigationCache(position ->
            position.getY() == 30
                ? GroundNavigationCache.Classification.standable(30.0D)
                : GroundNavigationCache.Classification.blocked()
        );
        BlockPos negativeChunk = new BlockPos(-1, 30, -1);
        BlockPos originChunk = new BlockPos(0, 30, 0);
        BlockPos blocked = new BlockPos(-2, 31, -2);
        assertTrue(cache.isStandable(negativeChunk));
        assertTrue(cache.isStandable(originChunk));
        assertFalse(cache.isStandable(blocked));

        cache.invalidateChunk(-1, -1);

        assertEquals(1L, cache.getCachedCellCount());
        assertTrue(cache.isStandable(originChunk));
        assertTrue(cache.isStandable(negativeChunk));
        assertEquals(4L, cache.getClassificationCount());
    }
}

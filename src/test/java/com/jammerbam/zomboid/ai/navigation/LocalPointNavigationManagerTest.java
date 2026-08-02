package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalPointNavigationManagerTest {
    @Test
    public void incrementalLocalSearchRoutesAroundBlockedCells() {
        Set<BlockPos> blocked = new HashSet<>();
        blocked.add(new BlockPos(2, 64, 0));
        blocked.add(new BlockPos(2, 64, 1));
        GroundNavigationCache cache = flatCache(blocked);
        BlockPos start = new BlockPos(0, 64, 0);
        LocalPointNavigationManager.RouteSearch search =
            LocalPointNavigationManager.createSearch(
                cache, start, new BlockPos(4, 64, 0), 3, 512, false
            );

        assertTrue(search != null);
        while (!search.isFound() && !search.isExhausted()) {
            assertTrue(search.advance(2) <= 2);
        }

        assertTrue(search.isFound());
        long packedStart = start.toLong();
        long next = search.next(packedStart, 7);
        assertTrue(next != PackedBlockPosition.NONE);
        assertEquals(search.distance(packedStart) - 1, search.distance(next));
    }

    @Test
    public void crowdedDestinationCanFinishAtAdjacentCell() {
        BlockPos destination = new BlockPos(4, 64, 0);
        Set<BlockPos> blocked = new HashSet<>();
        blocked.add(destination);
        LocalPointNavigationManager.RouteSearch search =
            LocalPointNavigationManager.createSearch(
                flatCache(blocked), new BlockPos(0, 64, 0),
                destination, 2, 256, true
            );

        assertTrue(search != null);
        while (!search.isFound() && !search.isExhausted()) {
            search.advance(8);
        }

        assertTrue(search.isFound());
        assertEquals(3, search.distance(new BlockPos(0, 64, 0).toLong()));
    }

    private static GroundNavigationCache flatCache(Set<BlockPos> blocked) {
        return new GroundNavigationCache(position ->
            position.getY() == 64 && !blocked.contains(position)
                ? GroundNavigationCache.Classification.standable(64.0D)
                : GroundNavigationCache.Classification.blocked()
        );
    }
}

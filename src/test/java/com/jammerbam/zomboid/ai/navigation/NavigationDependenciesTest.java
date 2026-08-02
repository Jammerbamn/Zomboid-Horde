package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationDependenciesTest {
    @Test
    public void ignoresChangedBlocksOutsideReachedCells() {
        FlowFieldSolver solver = new FlowFieldSolver(
            new long[]{PackedBlockPosition.pack(0, 64, 0)}, 1, 128
        );

        assertFalse(NavigationDependencies.affectsReachedCell(
            solver, new BlockPos(20, 64, 0)
        ));
    }

    @Test
    public void detectsSupportHeadAndConnectedShapeDependencies() {
        FlowFieldSolver solver = new FlowFieldSolver(
            new long[]{PackedBlockPosition.pack(0, 64, 0)}, 1, 128
        );

        assertTrue(NavigationDependencies.affectsReachedCell(
            solver, new BlockPos(0, 63, 0)
        ));
        assertTrue(NavigationDependencies.affectsReachedCell(
            solver, new BlockPos(1, 64, 0)
        ));
        assertTrue(NavigationDependencies.affectsReachedCell(
            solver, new BlockPos(0, 65, 0)
        ));
    }
}

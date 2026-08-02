package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.BlockPos;

/** Maps a changed world block to feet cells whose cached classification depends on it. */
final class NavigationDependencies {
    private NavigationDependencies() {
    }

    static boolean affectsReachedCell(FlowFieldSolver solver, BlockPos changedBlock) {
        int baseX = changedBlock.getX();
        int baseY = changedBlock.getY();
        int baseZ = changedBlock.getZ();
        // GroundNavigationCache invalidates these exact feet cells: the changed
        // block may be their support, feet, or head block, and connected collision
        // shapes can affect the adjacent X/Z columns.
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    long feet = PackedBlockPosition.pack(
                        baseX + offsetX, baseY + offsetY, baseZ + offsetZ
                    );
                    if (solver.hasReached(feet)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

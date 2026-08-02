package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.AxisAlignedBB;

/** Geometry policy for the allocation-light cohort collision index. */
final class CollisionIndexPolicy {
    static final double NORMAL_HALF_EXTENT = 1.0D;
    static final double SNAPSHOT_MOVEMENT_MARGIN = 0.5D;
    static final double QUERY_MARGIN = NORMAL_HALF_EXTENT + SNAPSHOT_MOVEMENT_MARGIN + 0.25D;

    private CollisionIndexPolicy() {
    }

    static boolean requiresDirectScan(AxisAlignedBB bounds, double motionX, double motionZ) {
        return bounds.maxX - bounds.minX > NORMAL_HALF_EXTENT * 2.0D
            || bounds.maxZ - bounds.minZ > NORMAL_HALF_EXTENT * 2.0D
            || Math.abs(motionX) > SNAPSHOT_MOVEMENT_MARGIN
            || Math.abs(motionZ) > SNAPSHOT_MOVEMENT_MARGIN;
    }
}

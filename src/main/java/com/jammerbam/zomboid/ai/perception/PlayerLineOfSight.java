package com.jammerbam.zomboid.ai.perception;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Server-safe player vision ray that allows light-transmitting blocks to be
 * seen through without treating every non-full collision shape as transparent.
 */
public final class PlayerLineOfSight {
    private static final double DIRECTION_EPSILON = 1.0E-9D;
    private static final double EXIT_EPSILON = 1.0E-4D;
    private static final int MAX_TRANSMITTING_BLOCKS = 256;

    private PlayerLineOfSight() {
    }

    public static boolean isClear(World world, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double length = delta.lengthVector();
        if (length <= DIRECTION_EPSILON) {
            return true;
        }

        Vec3d direction = delta.scale(1.0D / length);
        Vec3d rayStart = start;
        for (int passedBlocks = 0;
             passedBlocks < MAX_TRANSMITTING_BLOCKS;
             passedBlocks++) {
            RayTraceResult hit = world.rayTraceBlocks(
                rayStart, end, false, true, false
            );
            if (hit == null || hit.typeOfHit == RayTraceResult.Type.MISS) {
                return true;
            }
            if (hit.typeOfHit != RayTraceResult.Type.BLOCK
                || hit.getBlockPos() == null
                || hit.hitVec == null) {
                return false;
            }

            BlockPos hitPos = hit.getBlockPos();
            IBlockState hitState = world.getBlockState(hitPos);
            if (!transmitsVision(
                hitState.getMaterial().isOpaque(),
                hitState.getBlock().getLightOpacity(hitState, world, hitPos)
            )) {
                return false;
            }

            rayStart = advancePastBlock(hit.hitVec, direction, hitPos);
            if (rayStart.squareDistanceTo(end) <= EXIT_EPSILON * EXIT_EPSILON
                || direction.dotProduct(end.subtract(rayStart)) <= 0.0D) {
                return true;
            }
        }

        // A corrupt or adversarial block implementation must not turn the LOS
        // test into an unbounded server-thread loop.
        return false;
    }

    static boolean transmitsVision(boolean materialOpaque, int lightOpacity) {
        return !materialOpaque && lightOpacity < 255;
    }

    private static Vec3d advancePastBlock(Vec3d point,
                                          Vec3d direction,
                                          BlockPos blockPos) {
        double exitDistance = Double.POSITIVE_INFINITY;
        exitDistance = Math.min(
            exitDistance,
            distanceToExit(point.x, direction.x, blockPos.getX())
        );
        exitDistance = Math.min(
            exitDistance,
            distanceToExit(point.y, direction.y, blockPos.getY())
        );
        exitDistance = Math.min(
            exitDistance,
            distanceToExit(point.z, direction.z, blockPos.getZ())
        );

        if (!Double.isFinite(exitDistance)) {
            return point.add(direction.scale(EXIT_EPSILON));
        }
        return point.add(direction.scale(exitDistance + EXIT_EPSILON));
    }

    private static double distanceToExit(double coordinate,
                                         double direction,
                                         int blockCoordinate) {
        if (direction > DIRECTION_EPSILON) {
            double distance = (blockCoordinate + 1.0D - coordinate) / direction;
            return distance >= 0.0D ? distance : Double.POSITIVE_INFINITY;
        }
        if (direction < -DIRECTION_EPSILON) {
            double distance = (blockCoordinate - coordinate) / direction;
            return distance >= 0.0D ? distance : Double.POSITIVE_INFINITY;
        }
        return Double.POSITIVE_INFINITY;
    }
}

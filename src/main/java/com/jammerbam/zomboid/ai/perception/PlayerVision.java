package com.jammerbam.zomboid.ai.perception;

import net.minecraft.util.math.Vec3d;

/** Pure vision geometry and distance falloff used by the zombie brain. */
public final class PlayerVision {
    private PlayerVision() {
    }

    public static boolean isInsideViewCone(Vec3d headFacing, Vec3d towardPlayer,
                                           double fieldOfViewDegrees) {
        return isInsideViewConeWithThreshold(
            headFacing, towardPlayer, viewConeCosineThreshold(fieldOfViewDegrees)
        );
    }

    /** Precomputes the cosine threshold so one scan can reuse it for every player. */
    public static double viewConeCosineThreshold(double fieldOfViewDegrees) {
        double clampedFov = Math.max(1.0D, Math.min(360.0D, fieldOfViewDegrees));
        return Math.cos(Math.toRadians(clampedFov * 0.5D));
    }

    /** Tests a view cone using a threshold returned by viewConeCosineThreshold. */
    public static boolean isInsideViewConeWithThreshold(
        Vec3d headFacing, Vec3d towardPlayer, double cosineThreshold
    ) {
        double facingLength = length(headFacing);
        double targetLength = length(towardPlayer);
        if (facingLength <= 0.000001D || targetLength <= 0.000001D) {
            return targetLength <= 0.000001D;
        }
        double dot = (headFacing.x * towardPlayer.x
            + headFacing.y * towardPlayer.y
            + headFacing.z * towardPlayer.z) / (facingLength * targetLength);
        return dot >= cosineThreshold;
    }

    /** Returns an acquisition probability from 0 to 1 with a quadratic falloff. */
    public static double detectionChance(double distance, double maximumRange,
                                         double guaranteedRange,
                                         double chanceAtMaximumRangePercent) {
        if (maximumRange <= 0.0D || distance > maximumRange) {
            return 0.0D;
        }
        double near = Math.max(0.0D, Math.min(maximumRange, guaranteedRange));
        if (distance <= near || maximumRange <= near) {
            return 1.0D;
        }
        double minimum = Math.max(
            0.0D, Math.min(100.0D, chanceAtMaximumRangePercent)
        ) / 100.0D;
        double normalized = (distance - near) / (maximumRange - near);
        double remaining = 1.0D - normalized;
        return minimum + (1.0D - minimum) * remaining * remaining;
    }

    private static double length(Vec3d vector) {
        return Math.sqrt(
            vector.x * vector.x + vector.y * vector.y + vector.z * vector.z
        );
    }
}

package com.jammerbam.zomboid.ai.perception;

import net.minecraft.util.math.Vec3d;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerVisionTest {
    @Test
    public void viewConeUsesHeadFacingDirection() {
        Vec3d forward = new Vec3d(0.0D, 0.0D, 1.0D);

        assertTrue(PlayerVision.isInsideViewCone(
            forward, new Vec3d(0.5D, 0.0D, 1.0D), 120.0D
        ));
        assertFalse(PlayerVision.isInsideViewCone(
            forward, new Vec3d(0.0D, 0.0D, -1.0D), 120.0D
        ));
    }

    @Test
    public void precomputedViewConeThresholdMatchesDegreeBasedCheck() {
        Vec3d forward = new Vec3d(0.0D, 0.0D, 1.0D);
        Vec3d inside = new Vec3d(0.5D, 0.0D, 1.0D);
        Vec3d outside = new Vec3d(1.0D, 0.0D, 0.0D);
        double threshold = PlayerVision.viewConeCosineThreshold(120.0D);

        assertEquals(
            PlayerVision.isInsideViewCone(forward, inside, 120.0D),
            PlayerVision.isInsideViewConeWithThreshold(forward, inside, threshold)
        );
        assertEquals(
            PlayerVision.isInsideViewCone(forward, outside, 120.0D),
            PlayerVision.isInsideViewConeWithThreshold(forward, outside, threshold)
        );
    }

    @Test
    public void detectionChanceFallsSmoothlyWithDistance() {
        assertEquals(1.0D, PlayerVision.detectionChance(4.0D, 48.0D, 4.0D, 2.0D), 0.0001D);
        double middle = PlayerVision.detectionChance(26.0D, 48.0D, 4.0D, 2.0D);
        assertTrue(middle > 0.02D && middle < 1.0D);
        assertEquals(0.02D, PlayerVision.detectionChance(48.0D, 48.0D, 4.0D, 2.0D), 0.0001D);
        assertEquals(0.0D, PlayerVision.detectionChance(49.0D, 48.0D, 4.0D, 2.0D), 0.0001D);
    }
}

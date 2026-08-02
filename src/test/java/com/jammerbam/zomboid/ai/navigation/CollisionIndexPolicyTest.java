package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.util.math.AxisAlignedBB;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollisionIndexPolicyTest {
    @Test
    public void ordinaryMobFitsOneBucketAndQueryMarginCoversTickMovement() {
        AxisAlignedBB zombie = new AxisAlignedBB(4.7D, 20.0D, 8.7D,
            5.3D, 21.8D, 9.3D);

        assertFalse(CollisionIndexPolicy.requiresDirectScan(zombie, 0.23D, -0.23D));
        assertEquals(1.75D, CollisionIndexPolicy.QUERY_MARGIN, 0.0001D);
    }

    @Test
    public void wideOrFastEntitiesStayOnExactDirectScanPath() {
        AxisAlignedBB wide = new AxisAlignedBB(0.0D, 0.0D, 0.0D,
            2.01D, 1.0D, 1.0D);
        AxisAlignedBB normal = new AxisAlignedBB(0.0D, 0.0D, 0.0D,
            0.6D, 1.8D, 0.6D);

        assertTrue(CollisionIndexPolicy.requiresDirectScan(wide, 0.0D, 0.0D));
        assertTrue(CollisionIndexPolicy.requiresDirectScan(normal, 0.51D, 0.0D));
        assertFalse(CollisionIndexPolicy.requiresDirectScan(normal, 0.5D, -0.5D));
    }
}

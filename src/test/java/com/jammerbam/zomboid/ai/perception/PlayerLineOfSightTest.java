package com.jammerbam.zomboid.ai.perception;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerLineOfSightTest {
    @Test
    public void lightTransmittingMaterialPassesVision() {
        assertTrue(PlayerLineOfSight.transmitsVision(false, 0));
        assertTrue(PlayerLineOfSight.transmitsVision(false, 3));
    }

    @Test
    public void opaqueMaterialStillBlocksPartialGeometry() {
        assertFalse(PlayerLineOfSight.transmitsVision(true, 0));
    }

    @Test
    public void fullyLightBlockingBlockDoesNotPassVision() {
        assertFalse(PlayerLineOfSight.transmitsVision(false, 255));
    }
}

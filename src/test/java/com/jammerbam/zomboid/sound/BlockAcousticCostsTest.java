package com.jammerbam.zomboid.sound;

import net.minecraft.block.material.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlockAcousticCostsTest {
    @Test
    public void simpleModeLeavesClearAirUnattenuated() {
        assertEquals(1.0D, BlockAcousticCosts.simpleOcclusionMultiplier(Material.AIR), 0.0D);
    }

    @Test
    public void transparentAndSoftBlocksMuffleLessThanDenseBlocks() {
        double leaves = BlockAcousticCosts.simpleOcclusionMultiplier(Material.LEAVES);
        double glass = BlockAcousticCosts.simpleOcclusionMultiplier(Material.GLASS);
        double wood = BlockAcousticCosts.simpleOcclusionMultiplier(Material.WOOD);
        double stone = BlockAcousticCosts.simpleOcclusionMultiplier(Material.ROCK);
        double metal = BlockAcousticCosts.simpleOcclusionMultiplier(Material.IRON);

        assertTrue(leaves > glass);
        assertTrue(glass > wood);
        assertTrue(wood > stone);
        assertTrue(stone > metal);
    }

    @Test
    public void structuralImpactsPenetrateStoneBetterThanLightFootsteps() {
        double lightCost = 9.0D * AcousticProfile.LIGHT_FOOTSTEP
            .materialCostMultiplier(Material.ROCK);
        double structuralCost = 9.0D * AcousticProfile.STRUCTURAL_IMPACT
            .materialCostMultiplier(Material.ROCK);
        assertTrue(structuralCost < lightCost);

        double lightTransmission = BlockAcousticCosts.simpleOcclusionMultiplier(
            Material.ROCK, AcousticProfile.LIGHT_FOOTSTEP
        );
        double structuralTransmission = BlockAcousticCosts.simpleOcclusionMultiplier(
            Material.ROCK, AcousticProfile.STRUCTURAL_IMPACT
        );
        assertTrue(structuralTransmission > lightTransmission);
    }

    @Test
    public void neutralProfilePreservesOriginalSimpleTransmission() {
        assertEquals(
            BlockAcousticCosts.simpleOcclusionMultiplier(Material.WOOD),
            BlockAcousticCosts.simpleOcclusionMultiplier(
                Material.WOOD, AcousticProfile.NEUTRAL
            ),
            0.0D
        );
    }
}

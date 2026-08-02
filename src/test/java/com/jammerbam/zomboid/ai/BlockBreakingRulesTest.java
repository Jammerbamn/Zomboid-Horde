package com.jammerbam.zomboid.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockBreakingRulesTest {
    @Test
    public void mapsHandThroughIronHarvestTiersToFourLevels() {
        assertEquals(1, BlockBreakingRules.requiredLevel(null, true, -1));
        assertEquals(2, BlockBreakingRules.requiredLevel("pickaxe", false, 0));
        assertEquals(3, BlockBreakingRules.requiredLevel("pickaxe", false, 1));
        assertEquals(4, BlockBreakingRules.requiredLevel("pickaxe", false, 2));
        assertEquals(5, BlockBreakingRules.requiredLevel("pickaxe", false, 3));
    }

    @Test
    public void capabilityCannotBreakAboveItsTierOrDiamondRequirements() {
        assertTrue(BlockBreakingRules.canBreak(1, null, true, -1));
        assertFalse(BlockBreakingRules.canBreak(1, "pickaxe", false, 0));
        assertTrue(BlockBreakingRules.canBreak(3, "pickaxe", false, 1));
        assertFalse(BlockBreakingRules.canBreak(3, "pickaxe", false, 2));
        assertFalse(BlockBreakingRules.canBreak(4, "pickaxe", false, 3));
    }

    @Test
    public void durationMakesDirtThirtySecondsAndHarderBlocksMinutes() {
        assertEquals(600, BlockBreakingRules.durationTicks(0.5F));
        assertEquals(1800, BlockBreakingRules.durationTicks(1.5F));
        assertEquals(2400, BlockBreakingRules.durationTicks(2.0F));
        assertEquals(-1, BlockBreakingRules.durationTicks(-1.0F));
    }

    @Test
    public void undergroundWinsOnlyWhenFasterOrDirectIsImpossible() {
        assertTrue(BlockBreakingRules.shouldUseUnderground(-1L, 1200L));
        assertTrue(BlockBreakingRules.shouldUseUnderground(2400L, 1800L));
        assertFalse(BlockBreakingRules.shouldUseUnderground(1800L, 2400L));
        assertFalse(BlockBreakingRules.shouldUseUnderground(1800L, 1800L));
        assertFalse(BlockBreakingRules.shouldUseUnderground(1800L, -1L));
    }

    @Test
    public void planCostIncludesEstimatedTraversalTime() {
        assertEquals(1880L, BlockBreakingRules.totalPlanTicks(1800L, 4));
        assertEquals(1800L, BlockBreakingRules.totalPlanTicks(1800L, -1));
    }

    @Test
    public void stallProgressRequiresGettingCloserToTheObjective() {
        assertTrue(BlockBreakingRules.madeObjectiveProgress(
            Double.POSITIVE_INFINITY, 8.0D
        ));
        assertTrue(BlockBreakingRules.madeObjectiveProgress(8.0D, 7.75D));
        assertFalse(BlockBreakingRules.madeObjectiveProgress(8.0D, 7.90D));
        assertFalse(BlockBreakingRules.madeObjectiveProgress(8.0D, 8.0D));
        assertFalse(BlockBreakingRules.madeObjectiveProgress(8.0D, 8.5D));
    }
}

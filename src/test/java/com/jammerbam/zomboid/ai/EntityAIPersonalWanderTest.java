package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.ai.brain.BrainState;
import com.jammerbam.zomboid.performance.VanillaPathRequestSource;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityAIPersonalWanderTest {
    @Test
    public void candidateIsMeasuredFromPersonalSpawnOrigin() {
        BlockPos personalOrigin = new BlockPos(100, 64, 200);

        assertTrue(EntityAIPersonalWander.isInsidePersonalRadius(
            personalOrigin,
            new BlockPos(106, 70, 205),
            8
        ));
        assertFalse(EntityAIPersonalWander.isInsidePersonalRadius(
            personalOrigin,
            new BlockPos(109, 64, 200),
            8
        ));
    }

    @Test
    public void returnMovementUsesItsOwnPathRequestSource() {
        assertEquals(
            VanillaPathRequestSource.RETURN_TO_ANCHOR,
            EntityAIPersonalWander.pathRequestSource(BrainState.RETURNING_HOME)
        );
        assertEquals(
            VanillaPathRequestSource.PERSONAL_WANDER,
            EntityAIPersonalWander.pathRequestSource(BrainState.WANDERING)
        );
    }
}

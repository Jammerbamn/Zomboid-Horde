package com.jammerbam.zomboid.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityAIZombiePursuitTest {
    @Test
    public void buffSizedZombieReceivesReachMatchingItsLargerBody() {
        double vanilla = EntityAIZombiePursuit.attackReachSq(0.6F, 0.6F);
        double buff = EntityAIZombiePursuit.attackReachSq(1.1F, 0.6F);

        assertTrue(buff > vanilla);
        assertEquals(5.44D, buff, 0.0001D);
    }

    @Test
    public void closePursuitRefreshesEveryTickAndFarPursuitUsesConfiguredInterval() {
        assertEquals(1, EntityAIZombiePursuit.sharedSteeringInterval(64.0D, 8.0D, 3));
        assertEquals(3, EntityAIZombiePursuit.sharedSteeringInterval(64.01D, 8.0D, 3));
        assertEquals(1, EntityAIZombiePursuit.sharedSteeringInterval(4096.0D, 8.0D, 1));
    }

    @Test
    public void farSteeringUpdatesAreStaggeredByEntity() {
        assertTrue(EntityAIZombiePursuit.isSharedSteeringUpdateDue(100L, 2, 3));
        assertFalse(EntityAIZombiePursuit.isSharedSteeringUpdateDue(100L, 3, 3));
        assertFalse(EntityAIZombiePursuit.isSharedSteeringUpdateDue(100L, 4, 3));
        assertTrue(EntityAIZombiePursuit.isSharedSteeringUpdateDue(101L, 4, 3));
        assertTrue(EntityAIZombiePursuit.isSharedSteeringUpdateDue(101L, 99, 1));
    }
}

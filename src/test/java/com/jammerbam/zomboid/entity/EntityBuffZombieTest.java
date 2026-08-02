package com.jammerbam.zomboid.entity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntityBuffZombieTest {
    @Test
    public void declaresItsSturdierBaseAttributeProfile() {
        assertEquals(40.0D, EntityBuffZombie.BASE_MAX_HEALTH, 0.000001D);
        assertEquals(4.0D, EntityBuffZombie.BASE_ATTACK_DAMAGE, 0.000001D);
        assertEquals(0.21D, EntityBuffZombie.BASE_MOVEMENT_SPEED, 0.000001D);
        assertTrue(EntityBuffZombie.BASE_MAX_HEALTH > 20.0D);
        assertTrue(EntityBuffZombie.BASE_ATTACK_DAMAGE > 3.0D);
        assertTrue(EntityBuffZombie.BASE_MOVEMENT_SPEED < 0.23D);
    }
}

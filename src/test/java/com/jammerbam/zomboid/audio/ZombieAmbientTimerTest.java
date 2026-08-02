package com.jammerbam.zomboid.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZombieAmbientTimerTest {
    @Test
    public void startsAtNegativeVanillaTalkInterval() {
        ZombieAmbientTimer timer = new ZombieAmbientTimer(80);

        assertEquals(-80, timer.getSoundTime());
        assertFalse(timer.advance(80, 0));
        assertEquals(-79, timer.getSoundTime());
    }

    @Test
    public void comparesTheRollBeforeIncrementLikeVanilla() {
        ZombieAmbientTimer timer = new ZombieAmbientTimer(1);
        assertFalse(timer.advance(1, 0));
        assertFalse(timer.advance(1, 0));

        assertTrue(timer.advance(1, 0));
        assertEquals(-1, timer.getSoundTime());
    }

    @Test
    public void dueOpportunityResetsEvenWhenCallerMayRejectIt() {
        ZombieAmbientTimer timer = new ZombieAmbientTimer(1);
        timer.advance(1, 0);
        timer.advance(1, 0);
        assertTrue(timer.advance(1, 0));

        assertFalse(timer.advance(1, 0));
        assertEquals(0, timer.getSoundTime());
    }
}

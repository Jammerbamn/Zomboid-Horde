package com.jammerbam.zomboid.behavior;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupActivityScheduleTest {
    @Test
    public void missingScheduleIsDue() {
        assertTrue(GroupActivitySchedule.isDue(null, 100L));
    }

    @Test
    public void scheduleBecomesDueOnItsExactTick() {
        assertFalse(GroupActivitySchedule.isDue(101L, 100L));
        assertTrue(GroupActivitySchedule.isDue(101L, 101L));
    }

    @Test
    public void nextTickUsesConfiguredInterval() {
        assertEquals(200L, GroupActivitySchedule.nextAt(100L, 100));
    }

    @Test
    public void intervalCannotScheduleTheSameTick() {
        assertEquals(101L, GroupActivitySchedule.nextAt(100L, 0));
    }

    @Test
    public void randomIntervalVariesWithinInclusiveRange() {
        Random random = new Random(12345L);
        Set<Integer> observed = new HashSet<>();
        for (int index = 0; index < 100; index++) {
            int interval = GroupActivitySchedule.randomInterval(random, 60, 140);
            assertTrue(interval >= 60);
            assertTrue(interval <= 140);
            observed.add(interval);
        }
        assertTrue(observed.size() > 1);
    }

    @Test
    public void randomIntervalHandlesReversedBounds() {
        int interval = GroupActivitySchedule.randomInterval(new Random(7L), 140, 60);
        assertTrue(interval >= 60);
        assertTrue(interval <= 140);
    }

    @Test
    public void randomCountIncludesZeroThroughMaximum() {
        Random random = new Random(2468L);
        Set<Integer> observed = new HashSet<>();
        for (int index = 0; index < 200; index++) {
            int count = GroupActivitySchedule.randomCount(random, 3);
            assertTrue(count >= 0);
            assertTrue(count <= 3);
            observed.add(count);
        }
        assertTrue(observed.contains(0));
        assertTrue(observed.contains(3));
    }

    @Test
    public void activeWanderersReducePendingStarts() {
        assertEquals(2, GroupActivitySchedule.remainingStarts(3, 1));
        assertEquals(0, GroupActivitySchedule.remainingStarts(1, 2));
    }
}

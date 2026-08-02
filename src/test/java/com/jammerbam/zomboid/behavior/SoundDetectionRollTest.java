package com.jammerbam.zomboid.behavior;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SoundDetectionRollTest {
    private static final UUID ZOMBIE = UUID.fromString(
        "4b82339f-19e4-45a0-89b8-ff4eb357fa83"
    );

    @Test
    public void honorsDisabledAndGuaranteedBoundaries() {
        for (long eventId = 0L; eventId < 100L; eventId++) {
            assertFalse(SoundDetectionRoll.accepts(ZOMBIE, eventId, 0.0D));
            assertTrue(SoundDetectionRoll.accepts(ZOMBIE, eventId, 100.0D));
        }
    }

    @Test
    public void decisionIsStableForOneZombieAndEvent() {
        boolean first = SoundDetectionRoll.accepts(ZOMBIE, 42L, 75.0D);
        for (int repeat = 0; repeat < 100; repeat++) {
            assertEquals(first, SoundDetectionRoll.accepts(ZOMBIE, 42L, 75.0D));
        }
    }

    @Test
    public void defaultAcceptsApproximatelyThreeQuartersOfEvents() {
        int accepted = 0;
        int events = 10000;
        for (long eventId = 0L; eventId < events; eventId++) {
            if (SoundDetectionRoll.accepts(ZOMBIE, eventId, 75.0D)) {
                accepted++;
            }
        }
        assertTrue("accepted=" + accepted, accepted >= 7300);
        assertTrue("accepted=" + accepted, accepted <= 7700);
    }
}

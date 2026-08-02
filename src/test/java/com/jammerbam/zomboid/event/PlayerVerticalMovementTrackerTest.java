package com.jammerbam.zomboid.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayerVerticalMovementTrackerTest {
    @Test
    public void jumpArcEmitsOneLandingTransition() {
        PlayerVerticalMovementTracker tracker =
            new PlayerVerticalMovementTracker(64.0D, true);

        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(64.42D, false, false));
        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(65.25D, false, false));
        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(64.70D, false, false));
        assertEquals(PlayerVerticalMovementTracker.Transition.LANDING,
            tracker.update(64.0D, true, false));
        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(64.0D, true, false));
        assertEquals(Math.sqrt(1.25D), tracker.getLandingStrengthMultiplier(), 0.0001D);
    }

    @Test
    public void walkingOffAnEdgeOnlyEmitsTheLanding() {
        PlayerVerticalMovementTracker tracker =
            new PlayerVerticalMovementTracker(70.0D, true);

        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(69.95D, false, false));
        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(68.0D, false, false));
        assertEquals(PlayerVerticalMovementTracker.Transition.LANDING,
            tracker.update(64.0D, true, false));
        assertEquals(Math.sqrt(5.95D), tracker.getLandingStrengthMultiplier(), 0.0001D);
    }

    @Test
    public void flightSuppressionDoesNotCreateAFakeLanding() {
        PlayerVerticalMovementTracker tracker =
            new PlayerVerticalMovementTracker(64.0D, true);

        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(80.0D, false, true));
        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(64.0D, true, true));
        assertEquals(PlayerVerticalMovementTracker.Transition.NONE,
            tracker.update(64.0D, true, false));
    }

    @Test
    public void landingStrengthIsCappedForLargeFalls() {
        PlayerVerticalMovementTracker tracker =
            new PlayerVerticalMovementTracker(100.0D, false);

        assertEquals(PlayerVerticalMovementTracker.Transition.LANDING,
            tracker.update(0.0D, true, false));
        assertEquals(3.0D, tracker.getLandingStrengthMultiplier(), 0.0D);
    }
}

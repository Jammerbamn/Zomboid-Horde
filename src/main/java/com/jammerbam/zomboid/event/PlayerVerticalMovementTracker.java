package com.jammerbam.zomboid.event;

/**
 * Converts sampled player movement state into a one-shot landing transition.
 *
 * Forge's living-update event observes the result of the previous movement tick. Tracking
 * transitions avoids emitting a sound every tick while a player is airborne, while retaining
 * the peak needed after vanilla has reset its fall-distance field on landing.
 */
final class PlayerVerticalMovementTracker {
    private static final double MINIMUM_LANDING_DROP = 0.5D;
    private static final double MAXIMUM_LANDING_STRENGTH_MULTIPLIER = 3.0D;

    enum Transition {
        NONE,
        LANDING
    }

    private boolean wasOnGround;
    private double airbornePeakY;
    private double lastLandingDrop;

    PlayerVerticalMovementTracker(double positionY, boolean onGround) {
        wasOnGround = onGround;
        airbornePeakY = positionY;
    }

    Transition update(double positionY, boolean onGround, boolean suppressAirborneSounds) {
        if (suppressAirborneSounds) {
            wasOnGround = onGround;
            airbornePeakY = positionY;
            return Transition.NONE;
        }

        if (wasOnGround && !onGround) {
            wasOnGround = false;
            airbornePeakY = positionY;
            return Transition.NONE;
        }

        if (!onGround) {
            airbornePeakY = Math.max(airbornePeakY, positionY);
            wasOnGround = false;
            return Transition.NONE;
        }

        if (!wasOnGround) {
            wasOnGround = true;
            lastLandingDrop = Math.max(0.0D, airbornePeakY - positionY);
            airbornePeakY = positionY;
            return lastLandingDrop >= MINIMUM_LANDING_DROP
                ? Transition.LANDING
                : Transition.NONE;
        }

        airbornePeakY = positionY;
        return Transition.NONE;
    }

    double getLandingStrengthMultiplier() {
        return Math.min(
            MAXIMUM_LANDING_STRENGTH_MULTIPLIER,
            Math.sqrt(Math.max(1.0D, lastLandingDrop))
        );
    }
}

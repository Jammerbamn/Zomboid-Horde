package com.jammerbam.zomboid.ai.brain;

public enum BrainState {
    IDLE(false),
    WANDERING(false),
    INVESTIGATING_SOUND(false),
    OBSERVING_ALERT(true),
    FOLLOWING_ALERT_LEADER(true),
    PURSUING_TARGET(true),
    PURSUING_LAST_KNOWN_POSITION(true),
    SEARCHING(true),
    RETURNING_HOME(false);

    private final boolean alertedForAudio;

    BrainState(boolean alertedForAudio) {
        this.alertedForAudio = alertedForAudio;
    }

    public boolean isAlertedForAudio() {
        return alertedForAudio;
    }
}

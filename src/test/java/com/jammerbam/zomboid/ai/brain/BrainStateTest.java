package com.jammerbam.zomboid.ai.brain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrainStateTest {
    @Test
    public void pursuitAndSearchStatesAreAlertedForAudio() {
        assertTrue(BrainState.OBSERVING_ALERT.isAlertedForAudio());
        assertTrue(BrainState.FOLLOWING_ALERT_LEADER.isAlertedForAudio());
        assertTrue(BrainState.PURSUING_TARGET.isAlertedForAudio());
        assertTrue(BrainState.PURSUING_LAST_KNOWN_POSITION.isAlertedForAudio());
        assertTrue(BrainState.SEARCHING.isAlertedForAudio());
    }

    @Test
    public void idleAndInvestigationStatesUseIdleAudioBudget() {
        assertFalse(BrainState.IDLE.isAlertedForAudio());
        assertFalse(BrainState.WANDERING.isAlertedForAudio());
        assertFalse(BrainState.INVESTIGATING_SOUND.isAlertedForAudio());
        assertFalse(BrainState.RETURNING_HOME.isAlertedForAudio());
    }
}

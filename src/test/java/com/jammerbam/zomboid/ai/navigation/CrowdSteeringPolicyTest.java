package com.jammerbam.zomboid.ai.navigation;

import net.minecraft.scoreboard.Scoreboard;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CrowdSteeringPolicyTest {
    @Test
    public void cohortThresholdNeverTreatsOneZombieAsACrowd() {
        assertFalse(CrowdSteeringPolicy.qualifies(1, 1));
        assertTrue(CrowdSteeringPolicy.qualifies(2, 1));
        assertFalse(CrowdSteeringPolicy.qualifies(3, 4));
        assertTrue(CrowdSteeringPolicy.qualifies(4, 4));
    }

    @Test
    public void stationaryObstaclesOutweighReservationsAndTurns() {
        int stationary = CrowdSteeringPolicy.score(1, 0, 0);
        int reservedAndTurning = CrowdSteeringPolicy.score(0, 1, 2);

        assertTrue(stationary > reservedAndTurning);
        assertTrue(CrowdSteeringPolicy.score(0, 2, 0)
            > CrowdSteeringPolicy.score(0, 1, 2));
    }

    @Test
    public void onlyZomboidCollisionTeamsUseTheInternalNamespace() {
        assertTrue(CrowdNavigationManager.isInternalTeamName("zbc1kz9"));
        assertFalse(CrowdNavigationManager.isInternalTeamName("players"));
        assertFalse(CrowdNavigationManager.isInternalTeamName(null));
    }

    @Test
    public void staleInternalTeamsAreRemovedWithoutTouchingExternalTeams() {
        Scoreboard scoreboard = new Scoreboard();
        scoreboard.createTeam("zbcstale");
        scoreboard.createTeam("players");

        CrowdNavigationManager.sanitizeScoreboard(scoreboard);

        assertNull(scoreboard.getTeam("zbcstale"));
        assertNotNull(scoreboard.getTeam("players"));
    }
}

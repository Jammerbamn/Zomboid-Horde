package com.jammerbam.zomboid.ai.navigation;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SharedNavigationManagerTest {
    @Test
    public void cachedWaypointBelongsOnlyToItsPursuitTarget() {
        UUID target = UUID.randomUUID();
        SharedNavigationManager.SteeringCommand command =
            new SharedNavigationManager.SteeringCommand()
                .set(target, 12.5D, 64.0D, -8.5D);

        assertTrue(command.isFor(target));
        assertFalse(command.isFor(UUID.randomUUID()));
        assertEquals(12.5D, command.getX(), 0.0D);
        assertEquals(64.0D, command.getY(), 0.0D);
        assertEquals(-8.5D, command.getZ(), 0.0D);
    }
}

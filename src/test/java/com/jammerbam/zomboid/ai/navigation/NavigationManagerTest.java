package com.jammerbam.zomboid.ai.navigation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationManagerTest {
    @Test
    public void suppressesOnlyDuplicatePositionsWithinOneTick() {
        NavigationManager.SameTickInvalidationBatch batch =
            new NavigationManager.SameTickInvalidationBatch();

        assertTrue(batch.accept(100L, 42L));
        assertFalse(batch.accept(100L, 42L));
        assertTrue(batch.accept(100L, 43L));
        assertTrue(batch.accept(101L, 42L));
    }
}

package com.jammerbam.zomboid.ai.navigation;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VanillaNavigationOwnershipTest {
    @Test
    public void resolvesDeferredRouteFieldsInDevelopmentMappings() {
        assertTrue(VanillaNavigationOwnership.isAvailable());
    }
}

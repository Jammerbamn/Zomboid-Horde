package com.jammerbam.zomboid.core;

import com.jammerbam.zomboid.config.ModConfig;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DaylightZombieHooksTest {
    @Test
    public void daylightOptionControlsOnlySunlightIgnitionDecision() {
        boolean original = ModConfig.allowDaylightZombies;
        try {
            ModConfig.allowDaylightZombies = true;
            assertTrue(DaylightZombieHooks.preventDaylightBurning(null));

            ModConfig.allowDaylightZombies = false;
            assertFalse(DaylightZombieHooks.preventDaylightBurning(null));
        } finally {
            ModConfig.allowDaylightZombies = original;
        }
    }
}

package com.jammerbam.zomboid.core;

import com.jammerbam.zomboid.config.ModConfig;
import net.minecraft.entity.monster.EntityZombie;

/** Prevents sunlight ignition before Minecraft creates a transient fire state. */
public final class DaylightZombieHooks {
    private DaylightZombieHooks() {
    }

    public static boolean preventDaylightBurning(EntityZombie zombie) {
        return ModConfig.allowDaylightZombies;
    }
}

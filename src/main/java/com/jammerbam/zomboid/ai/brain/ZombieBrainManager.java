package com.jammerbam.zomboid.ai.brain;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.WeakHashMap;

public final class ZombieBrainManager {
    private static final Map<EntityZombie, ZombieBrain> BRAINS = new WeakHashMap<>();

    private ZombieBrainManager() {
    }

    public static ZombieBrain get(EntityZombie zombie) {
        ZombieBrain brain = BRAINS.get(zombie);
        if (brain == null) {
            brain = new ZombieBrain(zombie);
            BRAINS.put(zombie, brain);
        }
        return brain;
    }

    public static void tick(EntityZombie zombie) {
        get(zombie).tick();
    }

    public static void setMovementIntent(EntityZombie zombie, BrainState intent) {
        get(zombie).setMovementIntent(intent);
    }

    public static void clearMovementIntent(EntityZombie zombie, BrainState intent) {
        get(zombie).clearMovementIntent(intent);
    }

    public static void hearSound(EntityZombie zombie, long eventId,
                                 BlockPos estimatedPosition, long expiresAt,
                                 double perceivedStrength) {
        get(zombie).rememberSound(
            eventId, estimatedPosition, expiresAt, perceivedStrength
        );
    }
}

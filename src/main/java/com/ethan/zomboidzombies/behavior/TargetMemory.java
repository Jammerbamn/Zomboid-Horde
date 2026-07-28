package com.ethan.zomboidzombies.behavior;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

public final class TargetMemory {
    private static final Map<EntityZombie, Memory> MEMORIES = new WeakHashMap<>();

    private TargetMemory() {
    }

    public static void remember(EntityZombie zombie, BlockPos position, int durationTicks) {
        if (durationTicks <= 0) {
            return;
        }
        MEMORIES.put(zombie, new Memory(
            position.toImmutable(),
            zombie.world.getTotalWorldTime() + durationTicks
        ));
    }

    @Nullable
    public static BlockPos recall(EntityZombie zombie) {
        Memory memory = MEMORIES.get(zombie);
        if (memory == null) {
            return null;
        }
        if (memory.expiresAt < zombie.world.getTotalWorldTime()) {
            MEMORIES.remove(zombie);
            return null;
        }
        return memory.position;
    }

    public static void forget(EntityZombie zombie) {
        MEMORIES.remove(zombie);
    }

    private static final class Memory {
        private final BlockPos position;
        private final long expiresAt;

        private Memory(BlockPos position, long expiresAt) {
            this.position = position;
            this.expiresAt = expiresAt;
        }
    }
}

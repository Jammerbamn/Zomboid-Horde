package com.jammerbam.zomboid.behavior;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
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
            zombie.world.getTotalWorldTime() + durationTicks,
            null,
            false
        ));
    }

    public static void rememberPlayer(EntityZombie zombie, EntityPlayer player,
                                      int durationTicks, boolean persistentWhileAlive) {
        if (player == null || durationTicks <= 0 && !persistentWhileAlive) {
            return;
        }
        MEMORIES.put(zombie, new Memory(
            player.getPosition().toImmutable(),
            persistentWhileAlive
                ? Long.MAX_VALUE
                : zombie.world.getTotalWorldTime() + durationTicks,
            player.getUniqueID(),
            persistentWhileAlive
        ));
    }

    @Nullable
    public static BlockPos recall(EntityZombie zombie) {
        Memory memory = recallValidMemory(zombie);
        return memory == null ? null : memory.position;
    }

    @Nullable
    public static UUID recallPlayerId(EntityZombie zombie) {
        Memory memory = recallValidMemory(zombie);
        return memory == null ? null : memory.playerId;
    }

    private static Memory recallValidMemory(EntityZombie zombie) {
        Memory memory = MEMORIES.get(zombie);
        if (memory == null) {
            return null;
        }
        if (memory.persistentWhileAlive) {
            EntityPlayer player = zombie.world.getPlayerEntityByUUID(memory.playerId);
            if (player == null || !player.isEntityAlive() || player.isSpectator()
                || player.capabilities.disableDamage) {
                MEMORIES.remove(zombie);
                return null;
            }
        } else if (memory.expiresAt < zombie.world.getTotalWorldTime()) {
            MEMORIES.remove(zombie);
            return null;
        }
        return memory;
    }

    public static void forget(EntityZombie zombie) {
        MEMORIES.remove(zombie);
    }

    private static final class Memory {
        private final BlockPos position;
        private final long expiresAt;
        private final UUID playerId;
        private final boolean persistentWhileAlive;

        private Memory(BlockPos position, long expiresAt, UUID playerId,
                       boolean persistentWhileAlive) {
            this.position = position;
            this.expiresAt = expiresAt;
            this.playerId = playerId;
            this.persistentWhileAlive = persistentWhileAlive;
        }
    }
}

package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.performance.AiWorkBudgetPolicy;
import com.jammerbam.zomboid.performance.ServerTpsMonitor;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/** Shares a bounded number of expensive path calculations across a world tick. */
public final class PursuitPathScheduler {
    private static final Map<World, TickWorkBudget> WORLD_BUDGETS =
        new WeakHashMap<>();

    private PursuitPathScheduler() {
    }

    public static boolean tryAcquire(EntityZombie zombie) {
        TickWorkBudget budget = WORLD_BUDGETS.computeIfAbsent(
            zombie.world, ignored -> new TickWorkBudget()
        );
        int maximum = ModConfig.pursuitPathCalculationsPerTick;
        int allowed = ModConfig.dynamicAiWorkBudget
            ? AiWorkBudgetPolicy.pursuitPathCalculations(
                ServerTpsMonitor.INSTANCE.getTicksPerSecond(),
                ModConfig.minimumPursuitPathCalculationsPerTick,
                maximum
            )
            : maximum;
        return budget.tryAcquire(zombie.world.getTotalWorldTime(), allowed);
    }

    public static void clear(World world) {
        WORLD_BUDGETS.remove(world);
    }
}

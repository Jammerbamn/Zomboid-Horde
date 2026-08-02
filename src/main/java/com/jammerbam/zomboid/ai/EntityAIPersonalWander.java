package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.ai.brain.BrainState;
import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.ai.navigation.LocalPointNavigationManager;
import com.jammerbam.zomboid.ai.navigation.VanillaNavigationOwnership;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.performance.PerformancePhase;
import com.jammerbam.zomboid.performance.RuntimePerformanceTelemetry;
import com.jammerbam.zomboid.population.PopulationTags;
import com.jammerbam.zomboid.performance.VanillaPathRequestSource;
import com.jammerbam.zomboid.performance.VanillaPathRequestTelemetry;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class EntityAIPersonalWander extends EntityAIBase {
    private static final int VERTICAL_SEARCH_RANGE = 4;
    private static final int DESTINATION_ATTEMPTS = 8;

    private final EntityZombie zombie;
    private final double speed;
    private BlockPos destination;
    private BrainState movementState;
    private boolean holdsWanderSlot;
    private boolean usingLocalNavigation;
    private boolean localRouteFailed;
    private long nextAttemptAt;

    public EntityAIPersonalWander(EntityZombie zombie, double speed) {
        this.zombie = zombie;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!canWander() || zombie.world.getTotalWorldTime() < nextAttemptAt) {
            return false;
        }

        BlockPos home = PopulationTags.getHome(zombie);
        int radius = ModConfig.hordeWanderRadius;
        boolean outsidePersonalRadius =
            zombie.getDistanceSqToCenter(home) > (double) radius * radius;
        if (!outsidePersonalRadius && !WanderCoordinator.tryClaim(zombie)) {
            nextAttemptAt = zombie.world.getTotalWorldTime() + nextRetryDelay();
            return false;
        }

        holdsWanderSlot = !outsidePersonalRadius;
        destination = chooseDestination();
        if (destination == null) {
            releaseWanderSlot();
            nextAttemptAt = zombie.world.getTotalWorldTime() + nextRetryDelay();
            return false;
        }
        movementState = outsidePersonalRadius
            ? BrainState.RETURNING_HOME
            : BrainState.WANDERING;
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (!canWander()) {
            return false;
        }
        if (!usingLocalNavigation) {
            return !zombie.getNavigator().noPath();
        }
        LocalPointNavigationManager.Status status =
            LocalPointNavigationManager.getStatus(zombie);
        localRouteFailed = status == LocalPointNavigationManager.Status.FAILED;
        return status == LocalPointNavigationManager.Status.BUILDING
            || status == LocalPointNavigationManager.Status.MOVING;
    }

    @Override
    public void startExecuting() {
        ZombieBrainManager.setMovementIntent(zombie, movementState);
        if (ModConfig.enableLocalWanderNavigation) {
            usingLocalNavigation = true;
            long startedAt = RuntimePerformanceTelemetry.begin();
            try {
                localRouteFailed = !LocalPointNavigationManager.begin(
                    zombie, destination, speed, true
                );
            } finally {
                RuntimePerformanceTelemetry.recordElapsed(
                    zombie.world, PerformancePhase.LOCAL_NAVIGATION, startedAt
                );
            }
            return;
        }
        VanillaPathRequestTelemetry.run(
            zombie.world, pathRequestSource(movementState),
            () -> zombie.getNavigator().tryMoveToXYZ(
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                speed
            )
        );
    }

    @Override
    public void updateTask() {
        if (!usingLocalNavigation) {
            return;
        }
        long startedAt = RuntimePerformanceTelemetry.begin();
        try {
            localRouteFailed = LocalPointNavigationManager.steer(zombie)
                == LocalPointNavigationManager.Status.FAILED;
        } finally {
            RuntimePerformanceTelemetry.recordElapsed(
                zombie.world, PerformancePhase.LOCAL_NAVIGATION, startedAt
            );
        }
    }

    @Override
    public void resetTask() {
        if (usingLocalNavigation) {
            LocalPointNavigationManager.cancel(zombie);
        }
        VanillaNavigationOwnership.release(zombie.getNavigator());
        releaseWanderSlot();
        if (movementState != null) {
            ZombieBrainManager.clearMovementIntent(zombie, movementState);
        }
        movementState = null;
        destination = null;
        nextAttemptAt = zombie.world.getTotalWorldTime()
            + (localRouteFailed ? failedRouteRetryDelay() : nextRetryDelay());
        usingLocalNavigation = false;
        localRouteFailed = false;
    }

    private boolean canWander() {
        return zombie.isEntityAlive()
            && zombie.getAttackTarget() == null
            && PopulationTags.isManaged(zombie)
            && PopulationTags.hasHome(zombie)
            && ModConfig.hordeWanderRadius > 0;
    }

    private BlockPos chooseDestination() {
        BlockPos home = PopulationTags.getHome(zombie);
        int radius = ModConfig.hordeWanderRadius;
        if (zombie.getDistanceSqToCenter(home) > (double) radius * radius) {
            return home;
        }

        for (int attempt = 0; attempt < DESTINATION_ATTEMPTS; attempt++) {
            Vec3d candidate = RandomPositionGenerator.findRandomTarget(
                zombie,
                radius,
                VERTICAL_SEARCH_RANGE
            );
            if (candidate == null) {
                continue;
            }
            BlockPos position = new BlockPos(candidate);
            if (isInsidePersonalRadius(home, position, radius)) {
                return position;
            }
        }
        return null;
    }

    private int nextRetryDelay() {
        return 10 + zombie.getRNG().nextInt(21);
    }

    private int failedRouteRetryDelay() {
        return 100 + zombie.getRNG().nextInt(101);
    }

    private void releaseWanderSlot() {
        if (holdsWanderSlot) {
            WanderCoordinator.release(zombie);
            holdsWanderSlot = false;
        }
    }

    static boolean isInsidePersonalRadius(BlockPos home, BlockPos position, int radius) {
        long dx = (long) position.getX() - home.getX();
        long dz = (long) position.getZ() - home.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    static VanillaPathRequestSource pathRequestSource(BrainState state) {
        return state == BrainState.RETURNING_HOME
            ? VanillaPathRequestSource.RETURN_TO_ANCHOR
            : VanillaPathRequestSource.PERSONAL_WANDER;
    }
}

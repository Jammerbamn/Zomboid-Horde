package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.behavior.TargetMemory;
import com.jammerbam.zomboid.ai.brain.BrainState;
import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.ai.navigation.VanillaNavigationOwnership;
import com.jammerbam.zomboid.population.PopulationManager;
import com.jammerbam.zomboid.performance.VanillaPathRequestSource;
import com.jammerbam.zomboid.performance.VanillaPathRequestTelemetry;
import com.jammerbam.zomboid.variation.VariationTags;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;

public final class EntityAIPursueLastKnownPosition extends EntityAIBase {
    private final EntityZombie zombie;
    private final double speed;
    private BlockPos destination;

    public EntityAIPursueLastKnownPosition(EntityZombie zombie, double speed) {
        this.zombie = zombie;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (zombie.getAttackTarget() != null) {
            return false;
        }
        destination = TargetMemory.recall(zombie);
        return destination != null && zombie.getDistanceSqToCenter(destination) > 4.0D;
    }

    @Override
    public void startExecuting() {
        ZombieBrainManager.setMovementIntent(
            zombie,
            BrainState.PURSUING_LAST_KNOWN_POSITION
        );
        VanillaPathRequestTelemetry.run(
            zombie.world, VanillaPathRequestSource.LAST_KNOWN_POSITION,
            () -> zombie.getNavigator().tryMoveToXYZ(
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                speed
            )
        );
    }

    @Override
    public boolean shouldContinueExecuting() {
        return destination != null
            && zombie.getAttackTarget() == null
            && TargetMemory.recall(zombie) != null
            && (!zombie.getNavigator().noPath()
                || VariationTags.getBlockBreakingLevel(zombie) > 0)
            && zombie.getDistanceSqToCenter(destination) > 4.0D;
    }

    @Override
    public void updateTask() {
        ZombieBlockBreakingManager.steerRemembered(zombie, speed);
    }

    @Override
    public void resetTask() {
        VanillaNavigationOwnership.release(zombie.getNavigator());
        if (destination != null && zombie.getDistanceSqToCenter(destination) <= 4.0D) {
            TargetMemory.forget(zombie);
        }
        if (zombie.getAttackTarget() == null) {
            // The investigated location becomes the zombie's new idle territory.
            // This prevents a completed or failed search from pulling it all the
            // way back toward its original materialization point.
            PopulationManager.moveWanderAnchor(zombie, zombie.getPosition());
        }
        ZombieBrainManager.clearMovementIntent(
            zombie,
            BrainState.PURSUING_LAST_KNOWN_POSITION
        );
        destination = null;
    }
}

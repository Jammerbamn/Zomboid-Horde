package com.ethan.zomboidzombies.ai;

import com.ethan.zomboidzombies.behavior.TargetMemory;
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
        zombie.getNavigator().tryMoveToXYZ(
            destination.getX() + 0.5D,
            destination.getY(),
            destination.getZ() + 0.5D,
            speed
        );
    }

    @Override
    public boolean shouldContinueExecuting() {
        return destination != null
            && zombie.getAttackTarget() == null
            && TargetMemory.recall(zombie) != null
            && !zombie.getNavigator().noPath()
            && zombie.getDistanceSqToCenter(destination) > 4.0D;
    }

    @Override
    public void resetTask() {
        if (destination != null && zombie.getDistanceSqToCenter(destination) <= 4.0D) {
            TargetMemory.forget(zombie);
        }
        destination = null;
    }
}

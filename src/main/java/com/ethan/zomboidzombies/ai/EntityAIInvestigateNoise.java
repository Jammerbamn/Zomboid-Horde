package com.ethan.zomboidzombies.ai;

import com.ethan.zomboidzombies.behavior.NoiseManager;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;

public final class EntityAIInvestigateNoise extends EntityAIBase {
    private final EntityZombie zombie;
    private final double speed;
    private BlockPos destination;
    private long expiresAt;
    private int searchCooldown;

    public EntityAIInvestigateNoise(EntityZombie zombie, double speed) {
        this.zombie = zombie;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (zombie.getAttackTarget() != null || --searchCooldown > 0) {
            return false;
        }
        searchCooldown = 10;

        NoiseManager.NoiseTarget noise = NoiseManager.findBestNoise(zombie);
        if (noise == null) {
            return false;
        }

        destination = noise.position;
        expiresAt = noise.expiresAt;
        return true;
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
            && zombie.world.getTotalWorldTime() <= expiresAt
            && !zombie.getNavigator().noPath()
            && zombie.getDistanceSqToCenter(destination) > 4.0D;
    }

    @Override
    public void resetTask() {
        destination = null;
    }
}

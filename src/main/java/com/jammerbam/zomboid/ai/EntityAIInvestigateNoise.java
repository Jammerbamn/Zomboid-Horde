package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.behavior.NoiseManager;
import com.jammerbam.zomboid.ai.brain.BrainState;
import com.jammerbam.zomboid.ai.brain.ZombieBrain;
import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.ai.navigation.VanillaNavigationOwnership;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.population.PopulationManager;
import com.jammerbam.zomboid.performance.VanillaPathRequestSource;
import com.jammerbam.zomboid.performance.VanillaPathRequestTelemetry;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.BlockPos;

public final class EntityAIInvestigateNoise extends EntityAIBase {
    private final EntityZombie zombie;
    private final double speed;
    private BlockPos destination;
    private long expiresAt;
    private long eventId;
    private long lastCompletedEventId = -1L;
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

        ZombieBrain brain = ZombieBrainManager.get(zombie);
        ZombieBrain.SoundMemory memory = brain.recallSound();
        if (memory == null && !ModConfig.realisticSimulation) {
            NoiseManager.NoiseTarget noise = NoiseManager.findBestNoise(zombie);
            if (noise != null) {
                brain.rememberSound(
                    noise.eventId, noise.position, noise.expiresAt,
                    noise.perceivedStrength
                );
                memory = brain.recallSound();
            }
        }
        if (memory == null) {
            return false;
        }
        if (memory.eventId == lastCompletedEventId) {
            return false;
        }

        destination = memory.estimatedPosition;
        expiresAt = memory.expiresAt;
        eventId = memory.eventId;
        return true;
    }

    @Override
    public void startExecuting() {
        // Investigation establishes a new local territory immediately. Moving both the
        // persistent tag and vanilla home restriction prevents the old spawn point from
        // pulling this zombie back after it finishes checking the sound.
        PopulationManager.moveWanderAnchor(zombie, destination);
        ZombieBrainManager.setMovementIntent(zombie, BrainState.INVESTIGATING_SOUND);
        VanillaPathRequestTelemetry.run(
            zombie.world, VanillaPathRequestSource.SOUND_INVESTIGATION,
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
        ZombieBrain.SoundMemory memory = ZombieBrainManager.get(zombie).recallSound();
        return destination != null
            && memory != null
            && memory.eventId == eventId
            && zombie.getAttackTarget() == null
            && zombie.world.getTotalWorldTime() <= expiresAt
            && !zombie.getNavigator().noPath()
            && zombie.getDistanceSqToCenter(destination) > 4.0D;
    }

    @Override
    public void resetTask() {
        VanillaNavigationOwnership.release(zombie.getNavigator());
        if (destination != null) {
            // Finish on an actually reachable position rather than retaining an estimated
            // destination that may have landed across a wall or other obstruction.
            PopulationManager.moveWanderAnchor(zombie, zombie.getPosition());
        }
        if (destination != null && zombie.getDistanceSqToCenter(destination) <= 4.0D) {
            lastCompletedEventId = eventId;
            ZombieBrainManager.get(zombie).forgetSound(eventId);
        }
        ZombieBrainManager.clearMovementIntent(zombie, BrainState.INVESTIGATING_SOUND);
        destination = null;
    }
}

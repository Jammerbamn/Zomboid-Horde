package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.ai.brain.BrainState;
import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.ai.navigation.VanillaNavigationOwnership;
import com.jammerbam.zomboid.population.PopulationManager;
import com.jammerbam.zomboid.performance.VanillaPathRequestSource;
import com.jammerbam.zomboid.performance.VanillaPathRequestTelemetry;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;

public final class EntityAIFollowAlertLeader extends EntityAIBase {
    private static final double STOP_DISTANCE_SQ = 16.0D;

    private final EntityZombie zombie;
    private final double speed;
    private EntityZombie leader;
    private BrainState movementState;
    private int pathCooldown;

    public EntityAIFollowAlertLeader(EntityZombie zombie, double speed) {
        this.zombie = zombie;
        this.speed = speed;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (zombie.getAttackTarget() != null) {
            return false;
        }
        leader = ZombieAlertManager.getObservableLeader(zombie);
        return leader != null;
    }

    @Override
    public void startExecuting() {
        movementState = BrainState.OBSERVING_ALERT;
        ZombieBrainManager.setMovementIntent(zombie, movementState);
        pathCooldown = 0;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return zombie.getAttackTarget() == null
            && leader != null
            && leader == ZombieAlertManager.getObservableLeader(zombie);
    }

    @Override
    public void updateTask() {
        zombie.getLookHelper().setLookPositionWithEntity(leader, 30.0F, 30.0F);
        boolean following = ZombieAlertManager.shouldFollow(zombie);
        BrainState desiredState = following
            ? BrainState.FOLLOWING_ALERT_LEADER
            : BrainState.OBSERVING_ALERT;
        if (movementState != desiredState) {
            ZombieBrainManager.clearMovementIntent(zombie, movementState);
            movementState = desiredState;
            ZombieBrainManager.setMovementIntent(zombie, movementState);
        }
        if (!following) {
            VanillaNavigationOwnership.release(zombie.getNavigator());
            return;
        }
        if (zombie.getDistanceSq(leader) <= STOP_DISTANCE_SQ) {
            VanillaNavigationOwnership.release(zombie.getNavigator());
            return;
        }
        if (--pathCooldown <= 0 || zombie.getNavigator().noPath()) {
            pathCooldown = 10 + zombie.getRNG().nextInt(11);
            VanillaPathRequestTelemetry.run(
                zombie.world, VanillaPathRequestSource.ALERT_LEADER,
                () -> zombie.getNavigator().tryMoveToEntityLiving(leader, speed)
            );
        }
    }

    @Override
    public void resetTask() {
        if (leader != null) {
            PopulationManager.moveWanderAnchor(zombie, zombie.getPosition());
        }
        VanillaNavigationOwnership.release(zombie.getNavigator());
        if (movementState != null) {
            ZombieBrainManager.clearMovementIntent(zombie, movementState);
        }
        movementState = null;
        leader = null;
    }
}

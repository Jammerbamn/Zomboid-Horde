package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.ai.navigation.SharedNavigationManager;
import com.jammerbam.zomboid.ai.navigation.CrowdNavigationManager;
import com.jammerbam.zomboid.ai.navigation.VanillaNavigationOwnership;
import com.jammerbam.zomboid.performance.AiPerformanceTelemetry;
import com.jammerbam.zomboid.performance.PerformancePhase;
import com.jammerbam.zomboid.performance.RuntimePerformanceTelemetry;
import com.jammerbam.zomboid.performance.VanillaPathRequestSource;
import com.jammerbam.zomboid.performance.VanillaPathRequestTelemetry;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

/**
 * Zombie melee pursuit driven by shared flow fields with vanilla fallback.
 */
public final class EntityAIZombiePursuit extends EntityAIBase {
    private final EntityZombie zombie;
    private final double speed;
    private long nextPathAt;
    private double lastTargetX;
    private double lastTargetY;
    private double lastTargetZ;
    private int attackCooldown;
    private int pursuitTicks;
    private boolean usingSharedSteering;

    public EntityAIZombiePursuit(EntityZombie zombie, double speed) {
        this.zombie = zombie;
        this.speed = speed;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return isValidTarget(zombie.getAttackTarget());
    }

    @Override
    public boolean shouldContinueExecuting() {
        return isValidTarget(zombie.getAttackTarget());
    }

    @Override
    public void startExecuting() {
        pursuitTicks = 0;
        attackCooldown = 0;
        usingSharedSteering = false;
        int spread = Math.max(1, ModConfig.pursuitPathRecalculationMinTicks);
        nextPathAt = zombie.world.getTotalWorldTime()
            + (zombie.getUniqueID().hashCode() & Integer.MAX_VALUE) % spread;
    }

    @Override
    public void resetTask() {
        zombie.setArmsRaised(false);
        VanillaNavigationOwnership.release(zombie.getNavigator());
        SharedNavigationManager.stopSteering(zombie);
        usingSharedSteering = false;
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = zombie.getAttackTarget();
        if (!isValidTarget(target)) {
            return;
        }

        float headTurn = (float) ModConfig.pursuitHeadTurnDegreesPerTick;
        zombie.getLookHelper().setLookPositionWithEntity(target, headTurn, headTurn);
        double distanceSq = zombie.getDistanceSq(
            target.posX, target.getEntityBoundingBox().minY, target.posZ
        );
        updateMovement(target, distanceSq);

        attackCooldown = Math.max(0, attackCooldown - 1);
        pursuitTicks++;
        zombie.setArmsRaised(pursuitTicks >= 5 && attackCooldown < 10);
        if (distanceSq <= getAttackReachSq(target) && attackCooldown <= 0) {
            attackCooldown = 20;
            zombie.swingArm(EnumHand.MAIN_HAND);
            zombie.attackEntityAsMob(target);
        }
    }

    private void updateMovement(EntityLivingBase target, double distanceSq) {
        if (target instanceof EntityPlayer
            && ZombieBlockBreakingManager.steer(
                zombie, (EntityPlayer) target, speed
            )) {
            usingSharedSteering = false;
            return;
        }
        if (target instanceof EntityPlayer && ModConfig.enableSharedPursuitFlowFields) {
            int interval = sharedSteeringInterval(
                distanceSq,
                ModConfig.pursuitFlowFieldFullRateRadius,
                ModConfig.pursuitFlowFieldSteeringIntervalTicks
            );
            long now = zombie.world.getTotalWorldTime();
            if (usingSharedSteering
                && !isSharedSteeringUpdateDue(now, zombie.getEntityId(), interval)) {
                if (SharedNavigationManager.reissueSteering(
                    zombie, (EntityPlayer) target, speed
                )) {
                    return;
                }
            }
            if (SharedNavigationManager.steer(
                zombie, (EntityPlayer) target, speed
            )) {
                usingSharedSteering = true;
                return;
            }
        }
        usingSharedSteering = false;
        if (target instanceof EntityPlayer
            && CrowdNavigationManager.shouldDeferFallback(
                zombie, (EntityPlayer) target
            )) {
            return;
        }
        if (ModConfig.pursuitFlowFieldVanillaFallback) {
            updatePath(target, distanceSq);
        }
    }

    static int sharedSteeringInterval(double distanceSq, double fullRateRadius,
                                      int configuredInterval) {
        double radius = Math.max(0.0D, fullRateRadius);
        return distanceSq <= radius * radius
            ? 1 : Math.max(1, configuredInterval);
    }

    static boolean isSharedSteeringUpdateDue(long worldTime, int entityId,
                                             int interval) {
        int boundedInterval = Math.max(1, interval);
        return boundedInterval == 1
            || Math.floorMod(worldTime + entityId, boundedInterval) == 0;
    }

    private void updatePath(EntityLivingBase target, double distanceSq) {
        long now = zombie.world.getTotalWorldTime();
        if (now < nextPathAt) {
            return;
        }

        boolean targetMoved = target.getDistanceSq(
            lastTargetX, lastTargetY, lastTargetZ
        ) >= 1.0D;
        if (!zombie.getNavigator().noPath() && !targetMoved
            && zombie.getRNG().nextFloat() >= 0.05F) {
            nextPathAt = now + nextPathDelay();
            return;
        }

        // Only consume shared solver work after proving that this zombie really needs
        // a new route. A denied request gets a short randomized retry so stable entity
        // tick order cannot let the same few zombies monopolize every tick's budget.
        boolean acquired = PursuitPathScheduler.tryAcquire(zombie);
        AiPerformanceTelemetry.recordPursuitFallbackRequest(zombie.world, acquired);
        if (!acquired) {
            nextPathAt = now + 1 + zombie.getRNG().nextInt(4);
            return;
        }

        lastTargetX = target.posX;
        lastTargetY = target.getEntityBoundingBox().minY;
        lastTargetZ = target.posZ;
        int delay = nextPathDelay();
        if (distanceSq > 1024.0D) {
            delay += 10;
        } else if (distanceSq > 256.0D) {
            delay += 5;
        }
        VanillaPathRequestTelemetry.RequestResult request =
            VanillaPathRequestTelemetry.run(
                zombie.world, VanillaPathRequestSource.PLAYER_PURSUIT_FALLBACK,
                () -> zombie.getNavigator().tryMoveToEntityLiving(target, speed)
            );
        boolean pathFound = request.wasSuccessful();
        long pathElapsed = request.getElapsedNanoseconds();
        AiPerformanceTelemetry.recordPursuitFallbackPath(
            zombie.world, pathFound, pathElapsed
        );
        RuntimePerformanceTelemetry.record(
            zombie.world, PerformancePhase.VANILLA_PATH_FALLBACK, pathElapsed
        );
        if (!pathFound) {
            delay += 15;
        }
        nextPathAt = now + delay;
    }

    private int nextPathDelay() {
        int minimum = Math.max(1, ModConfig.pursuitPathRecalculationMinTicks);
        int maximum = Math.max(minimum, ModConfig.pursuitPathRecalculationMaxTicks);
        return minimum + zombie.getRNG().nextInt(maximum - minimum + 1);
    }

    private double getAttackReachSq(EntityLivingBase target) {
        return attackReachSq(zombie.width, target.width);
    }

    static double attackReachSq(float attackerWidth, float targetWidth) {
        double width = Math.max(0.0F, attackerWidth) * 2.0D;
        return width * width + Math.max(0.0F, targetWidth);
    }

    private static boolean isValidTarget(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive()) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            return !player.isSpectator() && !player.capabilities.disableDamage;
        }
        return true;
    }
}

package com.jammerbam.zomboid.ai.brain;

import com.jammerbam.zomboid.ai.perception.PlayerVision;
import com.jammerbam.zomboid.ai.perception.PlayerLineOfSight;
import com.jammerbam.zomboid.behavior.TargetMemory;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.performance.AiPerformanceTelemetry;
import com.jammerbam.zomboid.population.PopulationManager;
import com.jammerbam.zomboid.variation.VariationTags;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ZombieBrain {
    private final EntityZombie zombie;
    private BrainState state = BrainState.IDLE;
    private BrainState movementIntent;
    private long stateChangedAt;
    private SoundMemory soundMemory;
    private boolean wasDirectlyChasingPlayer;
    private long nextPlayerScanAt;
    private long lastPlayerSeenAt = Long.MIN_VALUE;
    private int scanCandidates;
    private int scanRangeRejected;
    private int scanConeRejected;
    private int scanChanceRejected;
    private int scanLineOfSightChecks;
    private int scanVisible;

    ZombieBrain(EntityZombie zombie) {
        this.zombie = zombie;
        this.stateChangedAt = zombie.world.getTotalWorldTime();
        int interval = Math.max(1, ModConfig.playerDetectionIntervalTicks);
        int offset = (zombie.getUniqueID().hashCode() & Integer.MAX_VALUE) % interval;
        this.nextPlayerScanAt = this.stateChangedAt + offset;
    }

    public void tick() {
        if (!zombie.isEntityAlive()) {
            wasDirectlyChasingPlayer = false;
            transitionTo(BrainState.IDLE);
            return;
        }
        updatePlayerPerception();
        boolean directlyChasingPlayer = zombie.getAttackTarget() instanceof EntityPlayer;
        if (wasDirectlyChasingPlayer && !directlyChasingPlayer) {
            // Commit the new territory once at the direct-chase boundary. The anchor is
            // deliberately not moved every tick while the target is active.
            PopulationManager.moveWanderAnchor(zombie, zombie.getPosition());
        }
        wasDirectlyChasingPlayer = directlyChasingPlayer;
        if (zombie.getAttackTarget() != null) {
            transitionTo(BrainState.PURSUING_TARGET);
            return;
        }
        transitionTo(movementIntent == null ? BrainState.IDLE : movementIntent);
    }

    private void updatePlayerPerception() {
        long now = zombie.world.getTotalWorldTime();
        if (now < nextPlayerScanAt) {
            return;
        }
        nextPlayerScanAt = now + Math.max(1, ModConfig.playerDetectionIntervalTicks);
        resetScanCounters();
        long startedAt = System.nanoTime();
        try {
            double detectionRange = getDetectionRange();
            EntityLivingBase currentTarget = zombie.getAttackTarget();
            if (currentTarget instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) currentTarget;
                double distanceSq = zombie.getDistanceSq(player);
                if (!isAttackablePlayer(player)
                    || !isInsideDetectionRange(distanceSq, detectionRange)) {
                    finishDirectPlayerPursuit();
                    currentTarget = null;
                } else if (hasHeadLineOfSight(player)) {
                    lastPlayerSeenAt = now;
                    rememberPlayer(player);
                    return;
                } else if (lastPlayerSeenAt == Long.MIN_VALUE
                    || now - lastPlayerSeenAt > ModConfig.playerSightLossGraceTicks) {
                    finishDirectPlayerPursuit();
                    currentTarget = null;
                } else {
                    return;
                }
            }

            EntityPlayer detected = findNearestVisiblePlayer(detectionRange);
            if (detected != null) {
                zombie.setAttackTarget(detected);
                lastPlayerSeenAt = now;
                rememberPlayer(detected);
            }
        } finally {
            AiPerformanceTelemetry.recordPerceptionScan(
                zombie.world,
                System.nanoTime() - startedAt,
                scanCandidates,
                scanRangeRejected,
                scanConeRejected,
                scanChanceRejected,
                scanLineOfSightChecks,
                scanVisible
            );
        }
    }

    private EntityPlayer findNearestVisiblePlayer(double detectionRange) {
        EntityPlayer nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        Vec3d zombieEyes = getZombieEyes();
        Vec3d headFacing = getHeadFacingVector();
        double coneThreshold = PlayerVision.viewConeCosineThreshold(
            ModConfig.playerVisionFieldOfViewDegrees
        );
        for (EntityPlayer player : zombie.world.playerEntities) {
            if (!isAttackablePlayer(player)) {
                continue;
            }
            scanCandidates++;
            double distanceSq = zombie.getDistanceSq(player);
            if (!isInsideDetectionRange(distanceSq, detectionRange)) {
                scanRangeRejected++;
                continue;
            }
            Vec3d playerEyes = getPlayerEyes(player);
            Vec3d towardPlayer = playerEyes.subtract(zombieEyes);
            if (!PlayerVision.isInsideViewConeWithThreshold(
                headFacing, towardPlayer, coneThreshold
            )) {
                scanConeRejected++;
                continue;
            }
            double chance = PlayerVision.detectionChance(
                Math.sqrt(distanceSq), detectionRange,
                ModConfig.playerGuaranteedDetectionRadius,
                ModConfig.playerDetectionChanceAtMaximumRangePercent
            );
            if (zombie.getRNG().nextDouble() >= chance) {
                scanChanceRejected++;
                continue;
            }
            if (!hasHeadLineOfSight(zombieEyes, playerEyes)) {
                continue;
            }
            if (distanceSq < nearestDistanceSq) {
                nearest = player;
                nearestDistanceSq = distanceSq;
            }
        }
        return nearest;
    }

    private static boolean isInsideDetectionRange(double distanceSq,
                                                  double detectionRange) {
        return distanceSq <= detectionRange * detectionRange;
    }

    private double getDetectionRange() {
        return ModConfig.followRange;
    }

    private boolean hasHeadLineOfSight(EntityPlayer player) {
        return hasHeadLineOfSight(getZombieEyes(), getPlayerEyes(player));
    }

    private boolean hasHeadLineOfSight(Vec3d zombieEyes, Vec3d playerEyes) {
        scanLineOfSightChecks++;
        boolean visible = PlayerLineOfSight.isClear(
            zombie.world, zombieEyes, playerEyes
        );
        if (visible) {
            scanVisible++;
        }
        return visible;
    }

    private Vec3d getZombieEyes() {
        return new Vec3d(
            zombie.posX, zombie.posY + zombie.getEyeHeight(), zombie.posZ
        );
    }

    private static Vec3d getPlayerEyes(EntityPlayer player) {
        return new Vec3d(
            player.posX, player.posY + player.getEyeHeight(), player.posZ
        );
    }

    private Vec3d getHeadFacingVector() {
        double yaw = Math.toRadians(zombie.rotationYawHead);
        double pitch = Math.toRadians(zombie.rotationPitch);
        double cosPitch = Math.cos(pitch);
        return new Vec3d(
            -Math.sin(yaw) * cosPitch,
            -Math.sin(pitch),
            Math.cos(yaw) * cosPitch
        );
    }

    private void resetScanCounters() {
        scanCandidates = 0;
        scanRangeRejected = 0;
        scanConeRejected = 0;
        scanChanceRejected = 0;
        scanLineOfSightChecks = 0;
        scanVisible = 0;
    }

    private void finishDirectPlayerPursuit() {
        zombie.setAttackTarget(null);
        // Commit immediately instead of relying only on an observed state edge.
        // This covers creative/spectator changes and range invalidation inside
        // the sensor itself.
        PopulationManager.moveWanderAnchor(zombie, zombie.getPosition());
        wasDirectlyChasingPlayer = false;
        lastPlayerSeenAt = Long.MIN_VALUE;
    }

    private void rememberPlayer(EntityPlayer player) {
        TargetMemory.rememberPlayer(
            zombie,
            player,
            ModConfig.targetMemoryTicks,
            VariationTags.getBlockBreakingLevel(zombie) > 0
        );
    }

    private static boolean isAttackablePlayer(EntityPlayer player) {
        return player.isEntityAlive()
            && !player.isSpectator()
            && !player.capabilities.disableDamage;
    }

    public BrainState getState() {
        return state;
    }

    public long getStateChangedAt() {
        return stateChangedAt;
    }

    public void setMovementIntent(BrainState intent) {
        movementIntent = intent;
        if (zombie.getAttackTarget() == null) {
            transitionTo(intent);
        }
    }

    public void clearMovementIntent(BrainState intent) {
        if (movementIntent == intent) {
            movementIntent = null;
        }
        tick();
    }

    public void rememberSound(long eventId, BlockPos estimatedPosition, long expiresAt,
                              double perceivedStrength) {
        long now = zombie.world.getTotalWorldTime();
        if (expiresAt < now) {
            return;
        }
        if (soundMemory != null && soundMemory.expiresAt >= now) {
            if (soundMemory.eventId == eventId) {
                return;
            }
            if (soundMemory.perceivedStrength > perceivedStrength) {
                return;
            }
        }
        soundMemory = new SoundMemory(
            eventId, estimatedPosition.toImmutable(), expiresAt, perceivedStrength
        );
    }

    public SoundMemory recallSound() {
        if (soundMemory != null
            && soundMemory.expiresAt < zombie.world.getTotalWorldTime()) {
            soundMemory = null;
        }
        return soundMemory;
    }

    public void forgetSound(long eventId) {
        if (soundMemory != null && soundMemory.eventId == eventId) {
            soundMemory = null;
        }
    }

    private void transitionTo(BrainState nextState) {
        if (nextState == null || state == nextState) {
            return;
        }
        state = nextState;
        stateChangedAt = zombie.world.getTotalWorldTime();
    }

    public static final class SoundMemory {
        public final long eventId;
        public final BlockPos estimatedPosition;
        public final long expiresAt;
        public final double perceivedStrength;

        private SoundMemory(long eventId, BlockPos estimatedPosition, long expiresAt,
                            double perceivedStrength) {
            this.eventId = eventId;
            this.estimatedPosition = estimatedPosition;
            this.expiresAt = expiresAt;
            this.perceivedStrength = perceivedStrength;
        }
    }
}

package com.jammerbam.zomboid.event;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.ai.EntityAIInvestigateNoise;
import com.jammerbam.zomboid.ai.EntityAIPersonalWander;
import com.jammerbam.zomboid.ai.EntityAIPursueLastKnownPosition;
import com.jammerbam.zomboid.ai.EntityAIFollowAlertLeader;
import com.jammerbam.zomboid.ai.EntityAIZombiePursuit;
import com.jammerbam.zomboid.ai.ZombieAlertManager;
import com.jammerbam.zomboid.ai.ZombieBlockBreakingManager;
import com.jammerbam.zomboid.ai.navigation.NavigationManager;
import com.jammerbam.zomboid.ai.brain.ZombieBrainManager;
import com.jammerbam.zomboid.audio.ZombieAudioController;
import com.jammerbam.zomboid.behavior.NoiseManager;
import com.jammerbam.zomboid.behavior.TargetMemory;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.entity.EntityBuffZombie;
import com.jammerbam.zomboid.variation.VariationTags;
import com.jammerbam.zomboid.variation.ZombieVariationDefinitions;
import com.jammerbam.zomboid.variation.ZombieVariationEffects;
import com.jammerbam.zomboid.compat.AiImprovementsCompatibility;
import com.jammerbam.zomboid.population.PopulationTags;
import com.jammerbam.zomboid.performance.PerformancePhase;
import com.jammerbam.zomboid.performance.RuntimePerformanceTelemetry;
import com.jammerbam.zomboid.sound.SoundType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIMoveThroughVillage;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIZombieAttack;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ZombieBehaviorEvents {
    private static boolean warnedPlayerTargetTaskReflection;
    private final Set<EntityZombie> initializedZombies =
        Collections.newSetFromMap(new WeakHashMap<EntityZombie, Boolean>());
    private final Map<EntityPlayer, PlayerMovementSample> playerMovementSamples =
        new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityZombie)) {
            return;
        }

        EntityZombie zombie = (EntityZombie) event.getEntity();
        AiImprovementsCompatibility.observe(zombie);
        if (!initializedZombies.add(zombie)) {
            return;
        }

        ZombieVariationDefinitions.refreshRuntimeTags(zombie);
        ZombieBlockBreakingManager.observeCapability(zombie);
        if (!VariationTags.hasMovementSpeedOverride(zombie)
            && !(zombie instanceof EntityBuffZombie)) {
            setAttribute(zombie, SharedMonsterAttributes.MOVEMENT_SPEED, ModConfig.movementSpeed);
        }
        setAttribute(
            zombie, SharedMonsterAttributes.FOLLOW_RANGE, ModConfig.vanillaPathSearchRange
        );
        if (ModConfig.disableVanillaReinforcements) {
            IAttributeInstance reinforcements =
                zombie.getAttributeMap().getAttributeInstanceByName("zombie.spawnReinforcements");
            if (reinforcements != null) {
                reinforcements.setBaseValue(0.0D);
            }
        }
        // Variation-driven digging owns door destruction too; leaving vanilla's global
        // hard-difficulty task enabled would let zombies without the capability break doors.
        zombie.setBreakDoorsAItask(false);
        ZombieBrainManager.get(zombie);
        if (PopulationTags.isManaged(zombie)) {
            removeVanillaRoamingTasks(zombie);
        }
        removeVanillaZombieAttackTask(zombie);
        removeVanillaPlayerTargetTask(zombie);
        zombie.tasks.addTask(2, new EntityAIZombiePursuit(zombie, 1.0D));
        zombie.tasks.addTask(3, new EntityAIPursueLastKnownPosition(zombie, 1.0D));
        zombie.tasks.addTask(4, new EntityAIFollowAlertLeader(zombie, 1.0D));
        zombie.tasks.addTask(5, new EntityAIInvestigateNoise(zombie, 1.0D));
        zombie.tasks.addTask(6, new EntityAIPersonalWander(zombie, 1.0D));
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }

        if (event.getEntityLiving() instanceof EntityPlayer) {
            samplePlayerMovement((EntityPlayer) event.getEntityLiving());
            return;
        }

        if (!(event.getEntityLiving() instanceof EntityZombie)) {
            return;
        }

        EntityZombie zombie = (EntityZombie) event.getEntityLiving();
        long behaviorStartedAt = RuntimePerformanceTelemetry.begin();
        try {
            ZombieBrainManager.tick(zombie);
            ZombieBlockBreakingManager.tick(zombie);
            ZombieAudioController.tick(zombie);
            ZombieVariationEffects.tickAura(zombie);
            Entity target = zombie.getAttackTarget();
            if (target instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) target;
                ZombieAlertManager.updateDirectTarget(zombie, player);
            } else {
                ZombieAlertManager.endDirectTarget(zombie);
            }
            ZombieAlertManager.tryRecruitNearby(zombie);
        } finally {
            RuntimePerformanceTelemetry.recordElapsed(
                zombie.world, PerformancePhase.ZOMBIE_BEHAVIOR, behaviorStartedAt
            );
        }
    }

    @SubscribeEvent
    public void onLivingJump(LivingJumpEvent event) {
        if (event.getEntityLiving().world.isRemote
            || !(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (canEmitPlayerNoise(player) && !suppressVerticalMovementNoise(player)) {
            NoiseManager.recordNoise(
                player.world, player.getPosition(), ModConfig.jumpNoiseRadius,
                ModConfig.noiseLifetimeTicks, SoundType.JUMP, player.getUniqueID()
            );
        }
    }

    @SubscribeEvent
    public void onTargetChanged(LivingSetAttackTargetEvent event) {
        if (event.getEntityLiving().world.isRemote
            || !(event.getEntityLiving() instanceof EntityZombie)
            || !(event.getTarget() instanceof EntityPlayer)) {
            return;
        }

        EntityZombie zombie = (EntityZombie) event.getEntityLiving();
        // Break-capable zombies establish their persistent player-linked memory
        // only in ZombieBrain after a successful eye-to-eye LOS check. Retaliation
        // or another target setter must not grant them knowledge through walls.
        if (VariationTags.getBlockBreakingLevel(zombie) > 0) {
            return;
        }
        TargetMemory.rememberPlayer(
            zombie,
            (EntityPlayer) event.getTarget(),
            ModConfig.targetMemoryTicks,
            false
        );
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }
        NavigationManager.invalidate(event.getWorld(), event.getPos());
        if (canEmitPlayerNoise(event.getPlayer())) {
            NoiseManager.recordNoise(
                event.getWorld(), event.getPos(), ModConfig.blockBreakNoiseRadius,
                ModConfig.noiseLifetimeTicks, SoundType.BLOCK_BREAK,
                event.getPlayer().getUniqueID()
            );
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!player.world.isRemote && canEmitPlayerNoise(player)) {
            // BreakEvent only fires when the block is fully removed. This stimulus makes
            // the initial mining impact audible as soon as the interaction begins.
            NoiseManager.recordNoise(
                player.world, event.getPos(), ModConfig.blockBreakNoiseRadius,
                ModConfig.noiseLifetimeTicks, SoundType.BLOCK_BREAK,
                player.getUniqueID()
            );
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }
        NavigationManager.invalidate(event.getWorld(), event.getPos());
        if (canEmitPlayerNoise(event.getPlayer())) {
            NoiseManager.recordNoise(
                event.getWorld(), event.getPos(), ModConfig.blockPlaceNoiseRadius,
                ModConfig.noiseLifetimeTicks, SoundType.BLOCK_PLACE,
                event.getPlayer().getUniqueID()
            );
        }
    }

    @SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!event.getWorld().isRemote) {
            // Covers non-place/break state changes such as doors, gates,
            // pistons, and connected collision shapes.
            NavigationManager.invalidate(event.getWorld(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }

        Entity trueSource = event.getSource().getTrueSource();
        Entity immediateSource = event.getSource().getImmediateSource();
        if (event.getEntityLiving() instanceof EntityPlayer
            && trueSource instanceof EntityZombie
            && immediateSource == trueSource
            && event.getAmount() > 0.0F) {
            ZombieVariationEffects.applyOnHit(
                (EntityZombie) trueSource, (EntityPlayer) event.getEntityLiving()
            );
        }
        EntityPlayer player = event.getEntityLiving() instanceof EntityPlayer
            ? (EntityPlayer) event.getEntityLiving()
            : trueSource instanceof EntityPlayer ? (EntityPlayer) trueSource : null;
        if (canEmitPlayerNoise(player)) {
            NoiseManager.recordNoise(
                event.getEntityLiving().world,
                event.getEntityLiving().getPosition(),
                ModConfig.combatNoiseRadius,
                ModConfig.noiseLifetimeTicks, SoundType.COMBAT, player.getUniqueID()
            );
        }
    }

    @SubscribeEvent
    public void onZombieDeath(LivingDeathEvent event) {
        if (!event.getEntityLiving().world.isRemote
            && event.getEntityLiving() instanceof EntityZombie) {
            ZombieAlertManager.forget((EntityZombie) event.getEntityLiving());
            ZombieBlockBreakingManager.forget((EntityZombie) event.getEntityLiving());
        }
    }

    private static boolean canEmitPlayerNoise(EntityPlayer player) {
        return player != null
            && !player.isSpectator();
    }

    private void samplePlayerMovement(EntityPlayer player) {
        PlayerMovementSample sample = playerMovementSamples.get(player);
        long now = player.world.getTotalWorldTime();
        if (sample == null || sample.world != player.world) {
            playerMovementSamples.put(
                player, new PlayerMovementSample(
                    player.world, player.posX, player.posZ, player.posY, player.onGround, now
                )
            );
            return;
        }

        double movementX = player.posX - sample.lastX;
        double movementZ = player.posZ - sample.lastZ;
        sample.lastX = player.posX;
        sample.lastZ = player.posZ;
        boolean canEmit = canEmitPlayerNoise(player);
        PlayerVerticalMovementTracker.Transition verticalTransition =
            sample.verticalMovement.update(
                player.posY,
                player.onGround,
                !canEmit || suppressVerticalMovementNoise(player)
            );
        if (verticalTransition == PlayerVerticalMovementTracker.Transition.LANDING) {
            NoiseManager.recordNoise(
                player.world,
                player.getPosition(),
                ModConfig.landingNoiseRadius
                    * sample.verticalMovement.getLandingStrengthMultiplier(),
                ModConfig.noiseLifetimeTicks,
                SoundType.LANDING,
                player.getUniqueID()
            );
            // The landing impact replaces a simultaneous grounded footstep.
            sample.nextFootstepAt = now + ModConfig.footstepNoiseIntervalTicks;
            return;
        }

        boolean movingHorizontally =
            movementX * movementX + movementZ * movementZ > 0.0004D;
        if (!canEmit || !player.onGround || !movingHorizontally
            || player.isSneaking() || now < sample.nextFootstepAt) {
            return;
        }

        boolean sprinting = player.isSprinting();
        NoiseManager.recordNoise(
            player.world,
            player.getPosition(),
            sprinting ? ModConfig.sprintNoiseRadius : ModConfig.walkNoiseRadius,
            ModConfig.noiseLifetimeTicks,
            sprinting ? SoundType.SPRINT : SoundType.WALK,
            player.getUniqueID()
        );
        sample.nextFootstepAt = now + ModConfig.footstepNoiseIntervalTicks;
    }

    private static boolean suppressVerticalMovementNoise(EntityPlayer player) {
        return player.capabilities.isFlying
            || player.isElytraFlying()
            || player.isInWater()
            || player.isOnLadder()
            || player.isRiding();
    }

    private static void setAttribute(EntityZombie zombie, net.minecraft.entity.ai.attributes.IAttribute attribute,
                                     double value) {
        IAttributeInstance instance = zombie.getEntityAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void removeVanillaRoamingTasks(EntityZombie zombie) {
        List<EntityAIBase> tasksToRemove = new ArrayList<>();
        for (EntityAITasks.EntityAITaskEntry entry : zombie.tasks.taskEntries) {
            if (entry.action instanceof EntityAIWander
                || entry.action instanceof EntityAIMoveThroughVillage) {
                tasksToRemove.add(entry.action);
            }
        }
        for (EntityAIBase task : tasksToRemove) {
            zombie.tasks.removeTask(task);
        }
    }

    private static void removeVanillaZombieAttackTask(EntityZombie zombie) {
        List<EntityAIBase> tasksToRemove = new ArrayList<>();
        for (EntityAITasks.EntityAITaskEntry entry : zombie.tasks.taskEntries) {
            if (entry.action instanceof EntityAIZombieAttack) {
                tasksToRemove.add(entry.action);
            }
        }
        for (EntityAIBase task : tasksToRemove) {
            zombie.tasks.removeTask(task);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void removeVanillaPlayerTargetTask(EntityZombie zombie) {
        List<EntityAIBase> tasksToRemove = new ArrayList<>();
        for (EntityAITasks.EntityAITaskEntry entry : zombie.targetTasks.taskEntries) {
            if (!(entry.action instanceof EntityAINearestAttackableTarget)) {
                continue;
            }
            try {
                Class<?> targetClass = ReflectionHelper.getPrivateValue(
                    EntityAINearestAttackableTarget.class,
                    (EntityAINearestAttackableTarget) entry.action,
                    "targetClass",
                    "field_75307_b"
                );
                if (targetClass != null && EntityPlayer.class.isAssignableFrom(targetClass)) {
                    tasksToRemove.add(entry.action);
                }
            } catch (RuntimeException exception) {
                if (!warnedPlayerTargetTaskReflection) {
                    warnedPlayerTargetTaskReflection = true;
                    Zomboid.logger.warn(
                        "Could not inspect the vanilla zombie player-target task; the custom "
                            + "player sensor will still run, but vanilla acquisition may remain.",
                        exception
                    );
                }
            }
        }
        for (EntityAIBase task : tasksToRemove) {
            zombie.targetTasks.removeTask(task);
        }
    }

    private static final class PlayerMovementSample {
        private final World world;
        private final PlayerVerticalMovementTracker verticalMovement;
        private double lastX;
        private double lastZ;
        private long nextFootstepAt;

        private PlayerMovementSample(World world, double lastX, double lastZ, double lastY,
                                     boolean onGround, long nextFootstepAt) {
            this.world = world;
            this.verticalMovement = new PlayerVerticalMovementTracker(lastY, onGround);
            this.lastX = lastX;
            this.lastZ = lastZ;
            this.nextFootstepAt = nextFootstepAt;
        }
    }
}

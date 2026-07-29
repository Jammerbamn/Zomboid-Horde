package com.ethan.zomboidzombies.event;

import com.ethan.zomboidzombies.ai.EntityAIInvestigateNoise;
import com.ethan.zomboidzombies.ai.EntityAIPursueLastKnownPosition;
import com.ethan.zomboidzombies.behavior.NoiseManager;
import com.ethan.zomboidzombies.behavior.TargetMemory;
import com.ethan.zomboidzombies.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class ZombieBehaviorEvents {
    private final Set<EntityZombie> initializedZombies =
        Collections.newSetFromMap(new WeakHashMap<EntityZombie, Boolean>());

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityZombie)) {
            return;
        }

        EntityZombie zombie = (EntityZombie) event.getEntity();
        if (!initializedZombies.add(zombie)) {
            return;
        }

        setAttribute(zombie, SharedMonsterAttributes.MOVEMENT_SPEED, ModConfig.movementSpeed);
        setAttribute(zombie, SharedMonsterAttributes.FOLLOW_RANGE, ModConfig.followRange);
        if (ModConfig.disableVanillaReinforcements) {
            IAttributeInstance reinforcements =
                zombie.getAttributeMap().getAttributeInstanceByName("zombie.spawnReinforcements");
            if (reinforcements != null) {
                reinforcements.setBaseValue(0.0D);
            }
        }
        zombie.setBreakDoorsAItask(ModConfig.breakWoodenDoors);
        zombie.tasks.addTask(3, new EntityAIPursueLastKnownPosition(zombie, 1.0D));
        zombie.tasks.addTask(4, new EntityAIInvestigateNoise(zombie, 1.0D));
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }

        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            if (player.isSprinting() && player.onGround && player.ticksExisted % 10 == 0) {
                NoiseManager.recordNoise(
                    player.world, player.getPosition(), ModConfig.sprintNoiseRadius,
                    ModConfig.noiseLifetimeTicks
                );
            }
            return;
        }

        if (!(event.getEntityLiving() instanceof EntityZombie)) {
            return;
        }

        EntityZombie zombie = (EntityZombie) event.getEntityLiving();
        if (ModConfig.allowDaylightZombies
            && zombie.world.isDaytime()
            && zombie.world.canSeeSky(zombie.getPosition())) {
            zombie.extinguish();
        }

        Entity target = zombie.getAttackTarget();
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            TargetMemory.remember(zombie, player.getPosition(), ModConfig.targetMemoryTicks);

            if (ModConfig.hordeAlertRadius > 0.0D
                && zombie.ticksExisted % ModConfig.hordeAlertIntervalTicks == 0) {
                alertNearbyZombies(zombie, player);
            }
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
        TargetMemory.remember(zombie, event.getTarget().getPosition(), ModConfig.targetMemoryTicks);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.getWorld().isRemote) {
            NoiseManager.recordNoise(
                event.getWorld(), event.getPos(), ModConfig.blockBreakNoiseRadius,
                ModConfig.noiseLifetimeTicks
            );
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!event.getWorld().isRemote) {
            NoiseManager.recordNoise(
                event.getWorld(), event.getPos(), ModConfig.blockPlaceNoiseRadius,
                ModConfig.noiseLifetimeTicks
            );
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }

        Entity trueSource = event.getSource().getTrueSource();
        if (event.getEntityLiving() instanceof EntityPlayer || trueSource instanceof EntityPlayer) {
            NoiseManager.recordNoise(
                event.getEntityLiving().world,
                event.getEntityLiving().getPosition(),
                ModConfig.combatNoiseRadius,
                ModConfig.noiseLifetimeTicks
            );
        }
    }

    private static void alertNearbyZombies(EntityZombie source, EntityPlayer target) {
        double radius = ModConfig.hordeAlertRadius;
        AxisAlignedBB area = source.getEntityBoundingBox().grow(radius);
        for (EntityZombie nearby : source.world.getEntitiesWithinAABB(EntityZombie.class, area)) {
            if (nearby != source && nearby.isEntityAlive() && nearby.getAttackTarget() == null) {
                nearby.setAttackTarget(target);
                TargetMemory.remember(nearby, target.getPosition(), ModConfig.targetMemoryTicks);
            }
        }
    }

    private static void setAttribute(EntityZombie zombie, net.minecraft.entity.ai.attributes.IAttribute attribute,
                                     double value) {
        IAttributeInstance instance = zombie.getEntityAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}

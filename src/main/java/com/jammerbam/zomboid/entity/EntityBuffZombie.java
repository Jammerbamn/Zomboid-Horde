package com.jammerbam.zomboid.entity;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;

/**
 * First custom zombie type. It inherits vanilla zombie AI, but has a sturdier and slightly
 * slower base attribute profile. Horde variations may still replace these values.
 */
public final class EntityBuffZombie extends EntityZombie {
    public static final float ENTITY_WIDTH = 1.1F;
    public static final float ENTITY_HEIGHT = 2.25F;
    public static final float EYE_HEIGHT = 2.0F;
    public static final double BASE_MAX_HEALTH = 40.0D;
    public static final double BASE_ATTACK_DAMAGE = 4.0D;
    public static final double BASE_MOVEMENT_SPEED = 0.21D;

    private static final DataParameter<Boolean> CHASING_PLAYER =
        EntityDataManager.createKey(EntityBuffZombie.class, DataSerializers.BOOLEAN);

    public EntityBuffZombie(World world) {
        super(world);
        setSize(ENTITY_WIDTH, ENTITY_HEIGHT);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
            .setBaseValue(BASE_MAX_HEALTH);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE)
            .setBaseValue(BASE_ATTACK_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED)
            .setBaseValue(BASE_MOVEMENT_SPEED);
    }

    @Override
    public float getEyeHeight() {
        return EYE_HEIGHT;
    }

    @Override
    public void setChild(boolean childZombie) {
        // The imported model has no child geometry or child-scale render path.
        super.setChild(false);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(CHASING_PLAYER, false);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!world.isRemote) {
            dataManager.set(
                CHASING_PLAYER,
                getAttackTarget() instanceof EntityPlayer && getAttackTarget().isEntityAlive()
            );
        }
    }

    public boolean isChasingPlayer() {
        return dataManager.get(CHASING_PLAYER);
    }
}

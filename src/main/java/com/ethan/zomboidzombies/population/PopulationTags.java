package com.ethan.zomboidzombies.population;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.nbt.NBTTagCompound;

public final class PopulationTags {
    private static final String MANAGED = "zomboidzombiesManaged";
    private static final String POPULATION_ID = "zomboidzombiesPopulationId";
    private static final String GROUP_ID = "zomboidzombiesGroupId";
    private static final String REGION_X = "zomboidzombiesRegionX";
    private static final String REGION_Z = "zomboidzombiesRegionZ";
    private static final String SLOT = "zomboidzombiesSlot";

    private PopulationTags() {
    }

    public static void apply(EntityZombie zombie, ZombieSpawnPlan plan) {
        NBTTagCompound data = zombie.getEntityData();
        data.setBoolean(MANAGED, true);
        data.setString(POPULATION_ID, plan.getPopulationId());
        data.setString(GROUP_ID, plan.getGroupId());
        data.setInteger(REGION_X, plan.getRegionX());
        data.setInteger(REGION_Z, plan.getRegionZ());
        data.setInteger(SLOT, plan.getSlot());
    }

    public static boolean isManaged(Entity entity) {
        return entity != null && entity.getEntityData().getBoolean(MANAGED);
    }

    public static String getPopulationId(Entity entity) {
        return entity == null ? "" : entity.getEntityData().getString(POPULATION_ID);
    }

    public static String getGroupId(Entity entity) {
        return entity == null ? "" : entity.getEntityData().getString(GROUP_ID);
    }
}

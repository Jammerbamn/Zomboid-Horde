package com.jammerbam.zomboid.population;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public final class PopulationTags {
    private static final String MANAGED = "zomboidManaged";
    private static final String POPULATION_ID = "zomboidPopulationId";
    private static final String GROUP_ID = "zomboidGroupId";
    private static final String REGION_X = "zomboidRegionX";
    private static final String REGION_Z = "zomboidRegionZ";
    private static final String SLOT = "zomboidSlot";
    private static final String REGENERATION_EPOCH = "zomboidRegenerationEpoch";
    private static final String HOME_SET = "zomboidHomeSet";
    private static final String HOME_X = "zomboidHomeX";
    private static final String HOME_Y = "zomboidHomeY";
    private static final String HOME_Z = "zomboidHomeZ";
    private static final String LEGACY_PREFIX = "zomboidzombies";

    private PopulationTags() {
    }

    public static void apply(Entity entity, ZombieSpawnPlan plan, int regenerationEpoch) {
        NBTTagCompound data = entity.getEntityData();
        data.setBoolean(MANAGED, true);
        data.setString(POPULATION_ID, plan.getPopulationId());
        data.setString(GROUP_ID, plan.getGroupId());
        data.setInteger(REGION_X, plan.getRegionX());
        data.setInteger(REGION_Z, plan.getRegionZ());
        data.setInteger(SLOT, plan.getSlot());
        data.setInteger(REGENERATION_EPOCH, regenerationEpoch);
    }

    public static boolean isManaged(Entity entity) {
        if (entity == null) {
            return false;
        }
        NBTTagCompound data = entity.getEntityData();
        if (data.getBoolean(MANAGED)) {
            return true;
        }
        if (!data.getBoolean(LEGACY_PREFIX + "Managed")) {
            return false;
        }
        migrateLegacyTags(data);
        return true;
    }

    public static String getPopulationId(Entity entity) {
        return entity == null ? "" : entity.getEntityData().getString(POPULATION_ID);
    }

    public static String getGroupId(Entity entity) {
        return entity == null ? "" : entity.getEntityData().getString(GROUP_ID);
    }

    public static int getRegenerationEpoch(Entity entity) {
        return entity == null ? 0 : entity.getEntityData().getInteger(REGENERATION_EPOCH);
    }

    public static void setHome(Entity entity, BlockPos home) {
        NBTTagCompound data = entity.getEntityData();
        data.setBoolean(HOME_SET, true);
        data.setInteger(HOME_X, home.getX());
        data.setInteger(HOME_Y, home.getY());
        data.setInteger(HOME_Z, home.getZ());
    }

    public static boolean hasHome(Entity entity) {
        return entity != null && entity.getEntityData().getBoolean(HOME_SET);
    }

    public static BlockPos getHome(Entity entity) {
        NBTTagCompound data = entity.getEntityData();
        return new BlockPos(
            data.getInteger(HOME_X),
            data.getInteger(HOME_Y),
            data.getInteger(HOME_Z)
        );
    }

    private static void migrateLegacyTags(NBTTagCompound data) {
        data.setBoolean(MANAGED, true);
        data.setString(POPULATION_ID, data.getString(LEGACY_PREFIX + "PopulationId"));
        data.setString(GROUP_ID, data.getString(LEGACY_PREFIX + "GroupId"));
        data.setInteger(REGION_X, data.getInteger(LEGACY_PREFIX + "RegionX"));
        data.setInteger(REGION_Z, data.getInteger(LEGACY_PREFIX + "RegionZ"));
        data.setInteger(SLOT, data.getInteger(LEGACY_PREFIX + "Slot"));
        if (data.getBoolean(LEGACY_PREFIX + "HomeSet")) {
            data.setBoolean(HOME_SET, true);
            data.setInteger(HOME_X, data.getInteger(LEGACY_PREFIX + "HomeX"));
            data.setInteger(HOME_Y, data.getInteger(LEGACY_PREFIX + "HomeY"));
            data.setInteger(HOME_Z, data.getInteger(LEGACY_PREFIX + "HomeZ"));
        }
    }
}

package com.jammerbam.zomboid.variation;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

public final class VariationTags {
    private static final String VARIATION_ID = "zomboidVariationId";
    private static final String MOVEMENT_SPEED_OVERRIDE = "zomboidVariationMovementSpeed";
    private static final String BLOCK_BREAKING_LEVEL = "zomboidBlockBreakingLevel";

    private VariationTags() {
    }

    public static void apply(Entity entity, ZombieVariationDefinition definition) {
        NBTTagCompound data = entity.getEntityData();
        data.setString(VARIATION_ID, definition.getId());
        data.setBoolean(MOVEMENT_SPEED_OVERRIDE, definition.getMovementSpeed() != null);
        Integer blockBreakingLevel = definition.getBlockBreakingLevel();
        if (blockBreakingLevel == null) {
            data.removeTag(BLOCK_BREAKING_LEVEL);
        } else {
            data.setInteger(BLOCK_BREAKING_LEVEL, blockBreakingLevel);
        }
    }

    public static String getVariationId(Entity entity) {
        return entity == null ? "" : entity.getEntityData().getString(VARIATION_ID);
    }

    public static boolean hasMovementSpeedOverride(Entity entity) {
        return entity != null
            && entity.getEntityData().getBoolean(MOVEMENT_SPEED_OVERRIDE);
    }

    public static int getBlockBreakingLevel(Entity entity) {
        if (entity == null) {
            return 0;
        }
        int level = entity.getEntityData().getInteger(BLOCK_BREAKING_LEVEL);
        return Math.max(0, Math.min(4, level));
    }
}

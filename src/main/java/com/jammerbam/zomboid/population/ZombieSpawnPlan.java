package com.jammerbam.zomboid.population;

public final class ZombieSpawnPlan {
    private final String populationId;
    private final String groupId;
    private final int regionX;
    private final int regionZ;
    private final int slot;
    private final int x;
    private final int z;
    private final String entityId;
    private final String variationId;

    public ZombieSpawnPlan(String populationId, String groupId, int regionX, int regionZ,
                           int slot, int x, int z, String entityId) {
        this(populationId, groupId, regionX, regionZ, slot, x, z, entityId, null);
    }

    public ZombieSpawnPlan(String populationId, String groupId, int regionX, int regionZ,
                           int slot, int x, int z, String entityId, String variationId) {
        this.populationId = populationId;
        this.groupId = groupId;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.slot = slot;
        this.x = x;
        this.z = z;
        this.entityId = entityId;
        this.variationId = variationId;
    }

    public String getPopulationId() {
        return populationId;
    }

    public String getGroupId() {
        return groupId;
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public int getSlot() {
        return slot;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getVariationId() {
        return variationId;
    }
}

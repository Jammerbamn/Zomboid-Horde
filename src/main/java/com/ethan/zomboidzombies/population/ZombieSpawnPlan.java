package com.ethan.zomboidzombies.population;

public final class ZombieSpawnPlan {
    private final String populationId;
    private final String groupId;
    private final int regionX;
    private final int regionZ;
    private final int slot;
    private final int x;
    private final int z;
    private final ZombieKind kind;

    public ZombieSpawnPlan(String populationId, String groupId, int regionX, int regionZ,
                           int slot, int x, int z, ZombieKind kind) {
        this.populationId = populationId;
        this.groupId = groupId;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.slot = slot;
        this.x = x;
        this.z = z;
        this.kind = kind;
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

    public ZombieKind getKind() {
        return kind;
    }
}

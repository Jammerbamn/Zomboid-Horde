package com.ethan.zomboidzombies.population;

import net.minecraft.nbt.NBTTagCompound;

public final class HordeRecord {
    private final int regionX;
    private final int regionZ;
    private final String groupId;
    private final int centerX;
    private final int centerZ;
    private final int plannedSize;
    private final int spreadRadius;
    private final int normalWeight;
    private final int huskWeight;
    private final int villagerWeight;

    public HordeRecord(int regionX, int regionZ, String groupId, int centerX, int centerZ,
                       int plannedSize, int spreadRadius, int normalWeight, int huskWeight,
                       int villagerWeight) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.groupId = groupId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.plannedSize = plannedSize;
        this.spreadRadius = spreadRadius;
        this.normalWeight = normalWeight;
        this.huskWeight = huskWeight;
        this.villagerWeight = villagerWeight;
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public String getGroupId() {
        return groupId;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getPlannedSize() {
        return plannedSize;
    }

    public int getSpreadRadius() {
        return spreadRadius;
    }

    public int getNormalWeight() {
        return normalWeight;
    }

    public int getHuskWeight() {
        return huskWeight;
    }

    public int getVillagerWeight() {
        return villagerWeight;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("RegionX", regionX);
        tag.setInteger("RegionZ", regionZ);
        tag.setString("GroupId", groupId);
        tag.setInteger("CenterX", centerX);
        tag.setInteger("CenterZ", centerZ);
        tag.setInteger("PlannedSize", plannedSize);
        tag.setInteger("SpreadRadius", spreadRadius);
        tag.setInteger("NormalWeight", normalWeight);
        tag.setInteger("HuskWeight", huskWeight);
        tag.setInteger("VillagerWeight", villagerWeight);
        return tag;
    }

    public static HordeRecord readFromNBT(NBTTagCompound tag) {
        return new HordeRecord(
            tag.getInteger("RegionX"),
            tag.getInteger("RegionZ"),
            tag.getString("GroupId"),
            tag.getInteger("CenterX"),
            tag.getInteger("CenterZ"),
            tag.getInteger("PlannedSize"),
            tag.getInteger("SpreadRadius"),
            tag.getInteger("NormalWeight"),
            tag.getInteger("HuskWeight"),
            tag.getInteger("VillagerWeight")
        );
    }
}

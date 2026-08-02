package com.jammerbam.zomboid.population;

import net.minecraft.nbt.NBTTagCompound;

public final class HordeVariation {
    public static final String STANDARD_ID = "zomboid:standard";

    private final String variationId;
    private final int weight;

    public HordeVariation(String variationId, int weight) {
        this.variationId = variationId;
        this.weight = Math.max(0, weight);
    }

    public String getVariationId() {
        return variationId;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isStandard() {
        return STANDARD_ID.equals(variationId);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("VariationId", variationId);
        tag.setInteger("Weight", weight);
        return tag;
    }

    public static HordeVariation readFromNBT(NBTTagCompound tag) {
        return new HordeVariation(tag.getString("VariationId"), tag.getInteger("Weight"));
    }
}

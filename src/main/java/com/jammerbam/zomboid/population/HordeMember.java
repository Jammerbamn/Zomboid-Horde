package com.jammerbam.zomboid.population;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HordeMember {
    private final String entityId;
    private final int weight;
    private final List<HordeVariation> variations;

    public HordeMember(String entityId, int weight) {
        this(entityId, weight, Collections.<HordeVariation>emptyList());
    }

    public HordeMember(String entityId, int weight, List<HordeVariation> variations) {
        this.entityId = entityId;
        this.weight = Math.max(0, weight);
        this.variations = Collections.unmodifiableList(new ArrayList<>(variations));
    }

    public String getEntityId() {
        return entityId;
    }

    public int getWeight() {
        return weight;
    }

    public List<HordeVariation> getVariations() {
        return variations;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("EntityId", entityId);
        tag.setInteger("Weight", weight);
        NBTTagList variationList = new NBTTagList();
        for (HordeVariation variation : variations) {
            variationList.appendTag(variation.writeToNBT());
        }
        tag.setTag("Variations", variationList);
        return tag;
    }

    public static HordeMember readFromNBT(NBTTagCompound tag) {
        List<HordeVariation> variations = new ArrayList<>();
        NBTTagList variationList = tag.getTagList("Variations", 10);
        for (int i = 0; i < variationList.tagCount(); i++) {
            variations.add(HordeVariation.readFromNBT(variationList.getCompoundTagAt(i)));
        }
        return new HordeMember(
            tag.getString("EntityId"), tag.getInteger("Weight"), variations
        );
    }
}

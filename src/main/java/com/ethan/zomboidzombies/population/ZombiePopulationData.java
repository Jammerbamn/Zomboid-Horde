package com.ethan.zomboidzombies.population;

import com.ethan.zomboidzombies.config.ModConfig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ZombiePopulationData extends WorldSavedData {
    public static final String DATA_NAME = "zomboidzombies_population";
    private static final int SAVE_VERSION = 1;

    private final Set<Long> initializedRegions = new HashSet<>();
    private final Map<Long, HordeRecord> hordes = new HashMap<>();
    private final Set<String> deadPopulationIds = new HashSet<>();
    private final Set<String> materializedPopulationIds = new HashSet<>();

    private int generatorVersion = SeededPopulationGenerator.GENERATOR_VERSION;
    private int regionSizeChunks;

    public ZombiePopulationData() {
        this(DATA_NAME);
    }

    public ZombiePopulationData(String name) {
        super(name);
    }

    public static ZombiePopulationData get(WorldServer world) {
        MapStorage storage = world.getPerWorldStorage();
        ZombiePopulationData data = (ZombiePopulationData) storage.getOrLoadData(
            ZombiePopulationData.class, DATA_NAME
        );
        if (data == null) {
            data = new ZombiePopulationData();
            storage.setData(DATA_NAME, data);
        }
        data.ensureMetadata();
        return data;
    }

    public boolean isRegionInitialized(int regionX, int regionZ) {
        return initializedRegions.contains(regionKey(regionX, regionZ));
    }

    public HordeRecord getHorde(int regionX, int regionZ) {
        return hordes.get(regionKey(regionX, regionZ));
    }

    public void initializeRegion(int regionX, int regionZ, HordeRecord horde) {
        long key = regionKey(regionX, regionZ);
        if (!initializedRegions.add(key)) {
            return;
        }
        if (horde != null) {
            hordes.put(key, horde);
        }
        markDirty();
    }

    public boolean isDead(String populationId) {
        return deadPopulationIds.contains(populationId);
    }

    public boolean isMaterialized(String populationId) {
        return materializedPopulationIds.contains(populationId);
    }

    public void markMaterialized(String populationId) {
        if (!deadPopulationIds.contains(populationId)
            && materializedPopulationIds.add(populationId)) {
            markDirty();
        }
    }

    public void markDead(String populationId) {
        boolean changed = deadPopulationIds.add(populationId);
        changed |= materializedPopulationIds.remove(populationId);
        if (changed) {
            markDirty();
        }
    }

    public int getRegionSizeChunks() {
        ensureMetadata();
        return regionSizeChunks;
    }

    public int getInitializedRegionCount() {
        return initializedRegions.size();
    }

    public int getHordeCount() {
        return hordes.size();
    }

    public int getDeadCount() {
        return deadPopulationIds.size();
    }

    public int getMaterializedCount() {
        return materializedPopulationIds.size();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        initializedRegions.clear();
        hordes.clear();
        deadPopulationIds.clear();
        materializedPopulationIds.clear();

        generatorVersion = compound.hasKey("GeneratorVersion")
            ? compound.getInteger("GeneratorVersion")
            : SeededPopulationGenerator.GENERATOR_VERSION;
        regionSizeChunks = compound.getInteger("RegionSizeChunks");

        NBTTagList regionList = compound.getTagList("InitializedRegions", 10);
        for (int i = 0; i < regionList.tagCount(); i++) {
            NBTTagCompound region = regionList.getCompoundTagAt(i);
            initializedRegions.add(regionKey(region.getInteger("X"), region.getInteger("Z")));
        }

        NBTTagList hordeList = compound.getTagList("Hordes", 10);
        for (int i = 0; i < hordeList.tagCount(); i++) {
            HordeRecord horde = HordeRecord.readFromNBT(hordeList.getCompoundTagAt(i));
            hordes.put(regionKey(horde.getRegionX(), horde.getRegionZ()), horde);
        }

        readStrings(compound.getTagList("DeadPopulationIds", 8), deadPopulationIds);
        readStrings(compound.getTagList("MaterializedPopulationIds", 8), materializedPopulationIds);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("SaveVersion", SAVE_VERSION);
        compound.setInteger("GeneratorVersion", generatorVersion);
        compound.setInteger("RegionSizeChunks", getRegionSizeChunks());

        NBTTagList regionList = new NBTTagList();
        for (long key : initializedRegions) {
            NBTTagCompound region = new NBTTagCompound();
            region.setInteger("X", regionX(key));
            region.setInteger("Z", regionZ(key));
            regionList.appendTag(region);
        }
        compound.setTag("InitializedRegions", regionList);

        NBTTagList hordeList = new NBTTagList();
        for (HordeRecord horde : hordes.values()) {
            hordeList.appendTag(horde.writeToNBT());
        }
        compound.setTag("Hordes", hordeList);
        compound.setTag("DeadPopulationIds", writeStrings(deadPopulationIds));
        compound.setTag("MaterializedPopulationIds", writeStrings(materializedPopulationIds));
        return compound;
    }

    private void ensureMetadata() {
        if (regionSizeChunks <= 0) {
            regionSizeChunks = ModConfig.populationRegionSizeChunks;
            generatorVersion = SeededPopulationGenerator.GENERATOR_VERSION;
            markDirty();
        }
    }

    private static NBTTagList writeStrings(Set<String> values) {
        NBTTagList list = new NBTTagList();
        for (String value : values) {
            list.appendTag(new NBTTagString(value));
        }
        return list;
    }

    private static void readStrings(NBTTagList list, Set<String> destination) {
        for (int i = 0; i < list.tagCount(); i++) {
            destination.add(list.getStringTagAt(i));
        }
    }

    public static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    private static int regionX(long key) {
        return (int) (key >> 32);
    }

    private static int regionZ(long key) {
        return (int) key;
    }
}

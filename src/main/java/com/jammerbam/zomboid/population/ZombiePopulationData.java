package com.jammerbam.zomboid.population;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ZombiePopulationData extends WorldSavedData {
    public static final String DATA_NAME = "zomboid_population";
    static final String LEGACY_DATA_NAME = "zomboidzombies_population";
    private static final int SAVE_VERSION = 3;

    private final Set<Long> initializedRegions = new HashSet<>();
    private final Map<Long, List<HordeRecord>> hordesByRegion = new HashMap<>();
    private final Map<String, HordeRecord> hordesById = new HashMap<>();
    private final Set<String> deadPopulationIds = new HashSet<>();
    private final Set<String> materializedPopulationIds = new HashSet<>();

    private int generatorVersion = SeededPopulationGenerator.GENERATOR_VERSION;
    private int regenerationEpoch;
    private boolean migrationPending;

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
            ZombiePopulationData legacy = (ZombiePopulationData) storage.getOrLoadData(
                ZombiePopulationData.class, LEGACY_DATA_NAME
            );
            data = new ZombiePopulationData();
            if (legacy != null) {
                data.copyFrom(legacy);
                data.markDirty();
            }
            storage.setData(DATA_NAME, data);
        }
        if (data.migrationPending) {
            data.migrationPending = false;
            data.markDirty();
        }
        return data;
    }

    private void copyFrom(ZombiePopulationData source) {
        initializedRegions.addAll(source.initializedRegions);
        for (HordeRecord horde : source.hordesById.values()) {
            addHorde(horde);
        }
        deadPopulationIds.addAll(source.deadPopulationIds);
        materializedPopulationIds.addAll(source.materializedPopulationIds);
        generatorVersion = source.generatorVersion;
        regenerationEpoch = source.regenerationEpoch;
        migrationPending = source.migrationPending;
    }

    public boolean isRegionInitialized(int regionX, int regionZ) {
        return initializedRegions.contains(regionKey(regionX, regionZ));
    }

    public void initializeRegion(int regionX, int regionZ, List<HordeRecord> hordes) {
        long key = regionKey(regionX, regionZ);
        if (!initializedRegions.add(key)) {
            return;
        }
        if (hordes != null) {
            for (HordeRecord horde : hordes) {
                addHorde(horde);
            }
        }
        markDirty();
    }

    public List<HordeRecord> getHordesInRegion(int regionX, int regionZ) {
        List<HordeRecord> hordes = hordesByRegion.get(regionKey(regionX, regionZ));
        return hordes == null
            ? Collections.<HordeRecord>emptyList()
            : Collections.unmodifiableList(hordes);
    }

    public List<HordeRecord> getHordesNearChunk(int chunkX, int chunkZ) {
        int regionSize = HordeCatalog.PLANNING_REGION_SIZE_CHUNKS;
        int regionX = Math.floorDiv(chunkX, regionSize);
        int regionZ = Math.floorDiv(chunkZ, regionSize);
        List<HordeRecord> result = new ArrayList<>();
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                result.addAll(getHordesInRegion(regionX + offsetX, regionZ + offsetZ));
            }
        }
        return result;
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
        return HordeCatalog.PLANNING_REGION_SIZE_CHUNKS;
    }

    public int getInitializedRegionCount() {
        return initializedRegions.size();
    }

    public int getHordeCount() {
        return hordesById.size();
    }

    public int getDeadCount() {
        return deadPopulationIds.size();
    }

    public int getMaterializedCount() {
        return materializedPopulationIds.size();
    }

    public int getRegenerationEpoch() {
        return regenerationEpoch;
    }

    public void resetForRegeneration() {
        initializedRegions.clear();
        hordesByRegion.clear();
        hordesById.clear();
        deadPopulationIds.clear();
        materializedPopulationIds.clear();
        generatorVersion = SeededPopulationGenerator.GENERATOR_VERSION;
        regenerationEpoch = regenerationEpoch == Integer.MAX_VALUE
            ? 1 : regenerationEpoch + 1;
        migrationPending = false;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        initializedRegions.clear();
        hordesByRegion.clear();
        hordesById.clear();
        deadPopulationIds.clear();
        materializedPopulationIds.clear();

        int saveVersion = compound.hasKey("SaveVersion")
            ? compound.getInteger("SaveVersion")
            : 1;
        generatorVersion = compound.hasKey("GeneratorVersion")
            ? compound.getInteger("GeneratorVersion")
            : 1;
        regenerationEpoch = compound.hasKey("RegenerationEpoch")
            ? compound.getInteger("RegenerationEpoch")
            : 0;

        NBTTagList hordeList = compound.getTagList("Hordes", 10);
        for (int i = 0; i < hordeList.tagCount(); i++) {
            addHorde(HordeRecord.readFromNBT(hordeList.getCompoundTagAt(i)));
        }

        NBTTagList regionList = compound.getTagList("InitializedRegions", 10);
        if (saveVersion >= 2) {
            for (int i = 0; i < regionList.tagCount(); i++) {
                NBTTagCompound region = regionList.getCompoundTagAt(i);
                initializedRegions.add(regionKey(region.getInteger("X"), region.getInteger("Z")));
            }
        } else {
            int oldRegionSize = compound.getInteger("RegionSizeChunks");
            if (oldRegionSize <= 0) {
                oldRegionSize = 8;
            }
            for (int i = 0; i < regionList.tagCount(); i++) {
                NBTTagCompound region = regionList.getCompoundTagAt(i);
                int originChunkX = region.getInteger("X") * oldRegionSize;
                int originChunkZ = region.getInteger("Z") * oldRegionSize;
                initializedRegions.add(regionKey(
                    Math.floorDiv(originChunkX, HordeCatalog.PLANNING_REGION_SIZE_CHUNKS),
                    Math.floorDiv(originChunkZ, HordeCatalog.PLANNING_REGION_SIZE_CHUNKS)
                ));
            }
            migrationPending = true;
        }

        readStrings(compound.getTagList("DeadPopulationIds", 8), deadPopulationIds);
        readStrings(compound.getTagList("MaterializedPopulationIds", 8), materializedPopulationIds);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("SaveVersion", SAVE_VERSION);
        compound.setInteger("GeneratorVersion", SeededPopulationGenerator.GENERATOR_VERSION);
        compound.setInteger("RegionSizeChunks", HordeCatalog.PLANNING_REGION_SIZE_CHUNKS);
        compound.setInteger("RegenerationEpoch", regenerationEpoch);

        NBTTagList regionList = new NBTTagList();
        for (long key : initializedRegions) {
            NBTTagCompound region = new NBTTagCompound();
            region.setInteger("X", regionX(key));
            region.setInteger("Z", regionZ(key));
            regionList.appendTag(region);
        }
        compound.setTag("InitializedRegions", regionList);

        NBTTagList hordeList = new NBTTagList();
        for (HordeRecord horde : hordesById.values()) {
            hordeList.appendTag(horde.writeToNBT());
        }
        compound.setTag("Hordes", hordeList);
        compound.setTag("DeadPopulationIds", writeStrings(deadPopulationIds));
        compound.setTag("MaterializedPopulationIds", writeStrings(materializedPopulationIds));
        return compound;
    }

    private void addHorde(HordeRecord horde) {
        if (horde == null || hordesById.putIfAbsent(horde.getGroupId(), horde) != null) {
            return;
        }
        long key = regionKey(horde.getPlanningRegionX(), horde.getPlanningRegionZ());
        hordesByRegion.computeIfAbsent(key, ignored -> new ArrayList<>()).add(horde);
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

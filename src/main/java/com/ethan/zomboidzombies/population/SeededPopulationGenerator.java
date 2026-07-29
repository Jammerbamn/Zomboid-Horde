package com.ethan.zomboidzombies.population;

import com.ethan.zomboidzombies.config.ModConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SeededPopulationGenerator {
    public static final int GENERATOR_VERSION = 1;

    private static final long REGION_SALT = 0x5A17D3E4B92C6F01L;
    private static final long SLOT_SALT = 0x28F19C74A6E35B0DL;

    private SeededPopulationGenerator() {
    }

    public static HordeRecord generateHorde(long worldSeed, int dimension, int regionX, int regionZ,
                                             int regionSizeChunks) {
        Random random = new Random(deriveSeed(worldSeed, dimension, regionX, regionZ, REGION_SALT));
        if (random.nextInt(100) >= ModConfig.hordeFrequencyPercent) {
            return null;
        }

        int regionBlocks = regionSizeChunks * 16;
        int regionStartX = regionX * regionBlocks;
        int regionStartZ = regionZ * regionBlocks;
        int inset = Math.min(8, Math.max(0, regionBlocks / 4));
        int usableWidth = Math.max(1, regionBlocks - inset * 2);
        int centerX = regionStartX + inset + random.nextInt(usableWidth);
        int centerZ = regionStartZ + inset + random.nextInt(usableWidth);
        int sizeRange = ModConfig.hordeMaximumSize - ModConfig.hordeMinimumSize + 1;
        int plannedSize = ModConfig.hordeMinimumSize + random.nextInt(Math.max(1, sizeRange));
        int maximumSpread = Math.max(1, regionBlocks / 2 - 1);
        int spread = Math.min(ModConfig.hordeSpreadRadius, maximumSpread);
        String groupId = groupId(dimension, regionX, regionZ);

        return new HordeRecord(
            regionX,
            regionZ,
            groupId,
            centerX,
            centerZ,
            plannedSize,
            spread,
            ModConfig.normalZombieWeight,
            ModConfig.huskWeight,
            ModConfig.zombieVillagerWeight
        );
    }

    public static List<ZombieSpawnPlan> generateSlots(long worldSeed, int dimension,
                                                       int regionSizeChunks, HordeRecord horde) {
        if (horde == null || horde.getPlannedSize() <= 0) {
            return Collections.emptyList();
        }

        Random random = new Random(deriveSeed(
            worldSeed, dimension, horde.getRegionX(), horde.getRegionZ(), SLOT_SALT
        ));
        int regionBlocks = regionSizeChunks * 16;
        int minX = horde.getRegionX() * regionBlocks;
        int minZ = horde.getRegionZ() * regionBlocks;
        int maxX = minX + regionBlocks - 1;
        int maxZ = minZ + regionBlocks - 1;
        List<ZombieSpawnPlan> result = new ArrayList<>(horde.getPlannedSize());

        for (int slot = 0; slot < horde.getPlannedSize(); slot++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(random.nextDouble()) * horde.getSpreadRadius();
            int x = clamp((int) Math.round(horde.getCenterX() + Math.cos(angle) * radius), minX, maxX);
            int z = clamp((int) Math.round(horde.getCenterZ() + Math.sin(angle) * radius), minZ, maxZ);
            ZombieKind kind = chooseKind(random, horde);
            result.add(new ZombieSpawnPlan(
                populationId(dimension, horde.getRegionX(), horde.getRegionZ(), slot),
                horde.getGroupId(),
                horde.getRegionX(),
                horde.getRegionZ(),
                slot,
                x,
                z,
                kind
            ));
        }

        return result;
    }

    public static String groupId(int dimension, int regionX, int regionZ) {
        return "d" + dimension + ":r" + regionX + "," + regionZ + ":g0";
    }

    public static String populationId(int dimension, int regionX, int regionZ, int slot) {
        return "d" + dimension + ":r" + regionX + "," + regionZ + ":z" + slot;
    }

    private static ZombieKind chooseKind(Random random, HordeRecord horde) {
        int total = horde.getNormalWeight() + horde.getHuskWeight() + horde.getVillagerWeight();
        if (total <= 0) {
            return ZombieKind.NORMAL;
        }

        int roll = random.nextInt(total);
        if (roll < horde.getNormalWeight()) {
            return ZombieKind.NORMAL;
        }
        roll -= horde.getNormalWeight();
        if (roll < horde.getHuskWeight()) {
            return ZombieKind.HUSK;
        }
        return ZombieKind.VILLAGER;
    }

    private static long deriveSeed(long worldSeed, int dimension, int regionX, int regionZ, long salt) {
        long value = worldSeed ^ salt;
        value ^= mix64(((long) dimension << 32) ^ dimension);
        value ^= mix64(((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL));
        value ^= (long) GENERATOR_VERSION * 0x9E3779B97F4A7C15L;
        return mix64(value);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

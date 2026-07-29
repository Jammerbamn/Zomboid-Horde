package com.ethan.zomboidzombies.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class ModConfig {
    private static final String GENERAL = "general";
    private static final String NOISE = "noise";
    private static final String HORDE = "horde";
    private static final String POPULATION = "population";

    public static boolean allowDaylightZombies = true;
    public static boolean breakWoodenDoors = true;
    public static double movementSpeed = 0.19D;
    public static double followRange = 48.0D;
    public static int targetMemoryTicks = 20 * 20;

    public static double sprintNoiseRadius = 12.0D;
    public static double blockBreakNoiseRadius = 32.0D;
    public static double blockPlaceNoiseRadius = 18.0D;
    public static double combatNoiseRadius = 24.0D;
    public static int noiseLifetimeTicks = 20 * 15;

    public static double hordeAlertRadius = 20.0D;
    public static int hordeAlertIntervalTicks = 20;

    public static boolean enableSeededPopulation = true;
    public static boolean replaceNaturalZombieSpawns = true;
    public static boolean disableVanillaReinforcements = true;
    public static int[] populationDimensions = new int[]{0};
    public static int populationRegionSizeChunks = 8;
    public static int hordeFrequencyPercent = 75;
    public static int hordeMinimumSize = 20;
    public static int hordeMaximumSize = 40;
    public static int hordeSpreadRadius = 32;
    public static int normalZombieWeight = 80;
    public static int huskWeight = 10;
    public static int zombieVillagerWeight = 10;
    public static int materializedChunksPerTick = 2;
    public static int materializationRetryTicks = 200;

    private ModConfig() {
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();

        allowDaylightZombies = config.getBoolean(
            "allowDaylightZombies", GENERAL, allowDaylightZombies,
            "If true, seeded zombies may spawn in daylight and all zombies survive exposure to the daytime sky."
        );
        breakWoodenDoors = config.getBoolean(
            "breakWoodenDoors", GENERAL, breakWoodenDoors,
            "Enables vanilla wooden-door breaking AI on all vanilla zombies."
        );
        movementSpeed = bounded(config.get(
            GENERAL, "movementSpeed", movementSpeed,
            "Zombie movement-speed attribute. Vanilla is roughly 0.23."
        ).getDouble(), 0.05D, 0.5D);
        followRange = bounded(config.get(
            GENERAL, "followRange", followRange,
            "Distance in blocks at which zombies can acquire targets."
        ).getDouble(), 8.0D, 128.0D);
        targetMemoryTicks = bounded(config.getInt(
            "targetMemoryTicks", GENERAL, targetMemoryTicks, 0, 20 * 60 * 10,
            "How long a zombie pursues a player's last known position."
        ), 0, 20 * 60 * 10);

        sprintNoiseRadius = radius(config, "sprintNoiseRadius", sprintNoiseRadius,
            "Radius of repeated noise made by a sprinting player.");
        blockBreakNoiseRadius = radius(config, "blockBreakNoiseRadius", blockBreakNoiseRadius,
            "Radius of noise made when a player breaks a block.");
        blockPlaceNoiseRadius = radius(config, "blockPlaceNoiseRadius", blockPlaceNoiseRadius,
            "Radius of noise made when a player places a block.");
        combatNoiseRadius = radius(config, "combatNoiseRadius", combatNoiseRadius,
            "Radius of noise made when a player deals or receives damage.");
        noiseLifetimeTicks = bounded(config.getInt(
            "noiseLifetimeTicks", NOISE, noiseLifetimeTicks, 1, 20 * 60 * 10,
            "How long zombies can investigate a noise."
        ), 1, 20 * 60 * 10);

        hordeAlertRadius = bounded(config.get(
            HORDE, "alertRadius", hordeAlertRadius,
            "Nearby zombies inside this radius share an acquired player target."
        ).getDouble(), 0.0D, 128.0D);
        hordeAlertIntervalTicks = bounded(config.getInt(
            "alertIntervalTicks", HORDE, hordeAlertIntervalTicks, 1, 20 * 60,
            "Ticks between horde alert pulses for a zombie with a target."
        ), 1, 20 * 60);

        enableSeededPopulation = config.getBoolean(
            "enableSeededPopulation", POPULATION, enableSeededPopulation,
            "Enables deterministic persistent hordes generated from the current world's seed."
        );
        replaceNaturalZombieSpawns = config.getBoolean(
            "replaceNaturalZombieSpawns", POPULATION, replaceNaturalZombieSpawns,
            "If true, denies vanilla random zombie spawns. Spawners, commands, and spawn eggs remain available."
        );
        disableVanillaReinforcements = config.getBoolean(
            "disableVanillaReinforcements", POPULATION, disableVanillaReinforcements,
            "Prevents attacked zombies from creating new vanilla reinforcement zombies outside the population ledger."
        );
        populationDimensions = config.get(
            POPULATION, "dimensions", populationDimensions,
            "Dimension IDs in which the seeded population system operates. Defaults to the Overworld."
        ).getIntList();
        populationRegionSizeChunks = bounded(config.getInt(
            "regionSizeChunks", POPULATION, populationRegionSizeChunks, 2, 64,
            "Width and depth of one deterministic population region, measured in chunks."
        ), 2, 64);
        hordeFrequencyPercent = bounded(config.getInt(
            "hordeFrequencyPercent", POPULATION, hordeFrequencyPercent, 0, 100,
            "Chance that a newly initialized population region contains a horde."
        ), 0, 100);
        hordeMinimumSize = bounded(config.getInt(
            "hordeMinimumSize", POPULATION, hordeMinimumSize, 1, 1000,
            "Minimum number of zombies in a generated horde."
        ), 1, 1000);
        hordeMaximumSize = bounded(config.getInt(
            "hordeMaximumSize", POPULATION, hordeMaximumSize, 1, 1000,
            "Maximum number of zombies in a generated horde."
        ), 1, 1000);
        if (hordeMaximumSize < hordeMinimumSize) {
            hordeMaximumSize = hordeMinimumSize;
        }
        hordeSpreadRadius = bounded(config.getInt(
            "hordeSpreadRadius", POPULATION, hordeSpreadRadius, 1, 256,
            "Maximum initial distance in blocks between a horde member and its generated center."
        ), 1, 256);
        normalZombieWeight = populationWeight(
            config, "normalZombieWeight", normalZombieWeight,
            "Relative population weight for ordinary zombies."
        );
        huskWeight = populationWeight(
            config, "huskWeight", huskWeight,
            "Relative population weight for husks."
        );
        zombieVillagerWeight = populationWeight(
            config, "zombieVillagerWeight", zombieVillagerWeight,
            "Relative population weight for zombie villagers."
        );
        if (normalZombieWeight + huskWeight + zombieVillagerWeight == 0) {
            normalZombieWeight = 1;
        }
        materializedChunksPerTick = bounded(config.getInt(
            "materializedChunksPerTick", POPULATION, materializedChunksPerTick, 1, 64,
            "Maximum loaded chunks checked for seeded population materialization each server tick."
        ), 1, 64);
        materializationRetryTicks = bounded(config.getInt(
            "materializationRetryTicks", POPULATION, materializationRetryTicks, 20, 20 * 60 * 10,
            "Delay before retrying seeded zombies that could not find a valid spawn position."
        ), 20, 20 * 60 * 10);

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static double radius(Configuration config, String name, double defaultValue, String comment) {
        return bounded(config.get(NOISE, name, defaultValue, comment).getDouble(), 0.0D, 128.0D);
    }

    private static int populationWeight(Configuration config, String name, int defaultValue, String comment) {
        return bounded(config.getInt(name, POPULATION, defaultValue, 0, 10000, comment), 0, 10000);
    }

    public static boolean isPopulationDimension(int dimension) {
        for (int configuredDimension : populationDimensions) {
            if (configuredDimension == dimension) {
                return true;
            }
        }
        return false;
    }

    private static double bounded(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

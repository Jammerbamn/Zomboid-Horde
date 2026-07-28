package com.ethan.zomboidzombies.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class ModConfig {
    private static final String GENERAL = "general";
    private static final String NOISE = "noise";
    private static final String HORDE = "horde";

    public static boolean preventDaylightBurning = true;
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

    private ModConfig() {
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();

        preventDaylightBurning = config.getBoolean(
            "preventDaylightBurning", GENERAL, preventDaylightBurning,
            "If true, zombies exposed to the daytime sky will not remain on fire."
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

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static double radius(Configuration config, String name, double defaultValue, String comment) {
        return bounded(config.get(NOISE, name, defaultValue, comment).getDouble(), 0.0D, 128.0D);
    }

    private static double bounded(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

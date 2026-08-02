package com.jammerbam.zomboid.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public final class ModConfig {
    private static final String GAMEPLAY = "01_gameplay";
    private static final String HORDES = "02_hordes";
    private static final String SOUND = "03_sound";
    private static final String POPULATION = "04_population";
    private static final String ADVANCED = "99_advanced";
    private static final String ADVANCED_AI = ADVANCED + ".ai";
    private static final String ADVANCED_HORDES = ADVANCED + ".hordes";
    private static final String ADVANCED_SOUND = ADVANCED + ".sound";
    private static final String ADVANCED_POPULATION = ADVANCED + ".population";
    private static final String ADVANCED_DIAGNOSTICS = ADVANCED + ".diagnostics";

    private static final String LEGACY_GENERAL = "general";
    private static final String LEGACY_AUDIO = "audio";
    private static final String LEGACY_NOISE = "noise";
    private static final String LEGACY_HORDE = "horde";
    private static final String LEGACY_POPULATION = "population";
    private static final String LEGACY_TELEMETRY = "telemetry";

    public static boolean allowDaylightZombies = true;
    public static boolean breakWoodenDoors = true;
    public static double movementSpeed = 0.23D;
    public static double followRange = 32.0D;
    public static double vanillaPathSearchRange = 32.0D;
    public static int playerDetectionIntervalTicks = 5;
    public static double playerVisionFieldOfViewDegrees = 120.0D;
    public static double playerGuaranteedDetectionRadius = 8.0D;
    public static double playerDetectionChanceAtMaximumRangePercent = 2.0D;
    public static int playerSightLossGraceTicks = 10;
    public static int targetMemoryTicks = 20 * 20;
    public static int pursuitPathCalculationsPerTick = 4;
    public static boolean dynamicAiWorkBudget = true;
    public static int minimumPursuitPathCalculationsPerTick = 1;
    public static int pursuitPathRecalculationMinTicks = 8;
    public static int pursuitPathRecalculationMaxTicks = 14;
    public static double pursuitHeadTurnDegreesPerTick = 10.0D;
    public static boolean enableSharedPursuitFlowFields = true;
    public static int pursuitFlowFieldRadius = 64;
    public static int pursuitFlowFieldVerticalRange = 16;
    public static int pursuitFlowFieldNodesPerTick = 4000;
    public static int pursuitFlowFieldMaximumNodes = 24000;
    public static double pursuitFlowFieldRebuildDistance = 2.0D;
    public static int pursuitFlowFieldMinimumRebuildTicks = 10;
    public static int pursuitFlowFieldSteeringIntervalTicks = 3;
    public static double pursuitFlowFieldFullRateRadius = 8.0D;
    public static boolean pursuitFlowFieldVanillaFallback = true;
    public static boolean enableLocalWanderNavigation = true;
    public static int localNavigationNodesPerTick = 2000;
    public static int localNavigationMaximumNodes = 2048;
    public static int localNavigationDetourRadius = 4;
    public static int localNavigationStuckTicks = 40;
    public static boolean enableCrowdAwarePursuit = true;
    public static int crowdSteeringMinimumCohortSize = 2;
    public static boolean enableCohortCollisionSuppression = true;
    public static boolean enableCohortCollisionQueryOptimization = true;
    public static int crowdCollisionMinimumCohortSize = 4;

    public static double walkNoiseRadius = 6.0D;
    public static double sprintNoiseRadius = 12.0D;
    public static double jumpNoiseRadius = 8.0D;
    public static double landingNoiseRadius = 10.0D;
    public static double blockBreakNoiseRadius = 32.0D;
    public static double blockPlaceNoiseRadius = 18.0D;
    public static double combatNoiseRadius = 24.0D;
    public static int noiseLifetimeTicks = 20 * 15;
    public static int footstepNoiseIntervalTicks = 10;
    public static boolean realisticSimulation = true;
    public static double soundDetectionChancePercent = 75.0D;
    public static int simpleSoundOcclusionCellSize = 4;
    public static int soundWaveIntervalTicks = 2;
    public static int soundPropagationNodesPerTick = 5000;
    public static int soundMaximumNodesPerEvent = 60000;
    public static int soundMaximumActiveEvents = 16;
    public static int soundDebugParticleBudget = 256;

    public static boolean enableStateAwareZombieAudio = true;
    public static int normalSoundChannels = 128;
    public static int idleHordeSoundIntervalTicks = 200;
    public static int alertedHordeSoundIntervalTicks = 10;
    public static boolean dynamicSoundChannels = true;
    public static int minimumDynamicSoundChannels = 28;
    public static double mobSoundChannelPercent = 30.0D;
    public static int unmanagedZombieSoundCellSize = 16;

    public static double hordeAlertnessRadius = 4.0D;
    public static int hordeAlertMaximumFollowers = 4;
    public static int hordeAlertMaximumRecruitsPerZombie = 2;
    public static double hordeAlertLookChancePercent = 35.0D;
    public static double hordeAlertFollowChancePercent = 75.0D;
    public static int hordeAlertPropagationIntervalTicks = 10;
    public static int hordeAlertLookDelayMinTicks = 10;
    public static int hordeAlertLookDelayMaxTicks = 25;
    public static int hordeAlertFollowDelayMinTicks = 40;
    public static int hordeAlertFollowDelayMaxTicks = 80;
    public static int hordeWanderRadius = 4;
    public static int hordeWanderIntervalMinTicks = 60;
    public static int hordeWanderIntervalMaxTicks = 140;
    public static int hordeWanderMaximumActive = 3;
    public static double hordeFrequencyPercentPerChunk = 7.0D;
    public static String[] hordeDefinitionFiles = new String[]{
        "zomboid/hordes/standard.xml"
    };

    public static boolean enableSeededPopulation = true;
    public static boolean replaceNaturalZombieSpawns = true;
    public static boolean disableVanillaReinforcements = true;
    public static int[] populationDimensions = new int[]{0};
    public static int materializedChunksPerTick = 2;
    public static int materializationRetryTicks = 200;

    public static boolean enablePerformanceTelemetry = true;
    public static double performanceStallThresholdMillis = 100.0D;
    public static int performanceStallLogCooldownTicks = 20;
    public static int performanceSummaryIntervalTicks = 1200;
    public static boolean enableVanillaEntityWorkSampler = true;
    public static int vanillaEntitySampleIntervalMillis = 5;

    private static Configuration activeConfiguration;

    private ModConfig() {
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        loadConfiguration(config, true);
    }

    static void load(Configuration config) {
        loadConfiguration(config, false);
    }

    private static void loadConfiguration(Configuration config, boolean save) {
        boolean migratedLayout = migrateCurrentLayout(config);
        migratedLayout |= removeObsoleteVariationDefinitionSetting(config);
        activeConfiguration = config;

        allowDaylightZombies = config.getBoolean(
            "allowDaylightZombies", GAMEPLAY, allowDaylightZombies,
            "If true, seeded zombies may spawn in daylight and all zombies survive exposure to the daytime sky."
        );
        breakWoodenDoors = config.getBoolean(
            "breakWoodenDoors", GAMEPLAY, breakWoodenDoors,
            "Enables vanilla wooden-door breaking AI on all vanilla zombies."
        );
        movementSpeed = bounded(config.get(
            GAMEPLAY, "movementSpeed", movementSpeed,
            "Base movement speed for ordinary zombies. Vanilla zombies are roughly 0.23; "
                + "lower values produce slower shamblers."
        ).getDouble(), 0.05D, 0.5D);
        followRange = bounded(config.get(
            GAMEPLAY, "followRange", followRange,
            "Farthest distance, in blocks, at which a zombie can potentially notice a player. "
                + "Line of sight and distance-based detection chance still apply."
        ).getDouble(), 8.0D, 128.0D);
        vanillaPathSearchRange = bounded(config.get(
            ADVANCED_AI, "vanillaPathSearchRange", vanillaPathSearchRange,
            "FOLLOW_RANGE attribute used by Minecraft's vanilla navigator and target tasks. "
                + "This bounds vanilla A* ChunkCache snapshots independently from the custom "
                + "player-detection range."
        ).getDouble(), 8.0D, 128.0D);
        playerDetectionIntervalTicks = bounded(config.getInt(
            "playerDetectionIntervalTicks", ADVANCED_AI,
            playerDetectionIntervalTicks, 1, 40,
            "Ticks between player-visibility scans by each zombie. Scan timing is staggered "
                + "per entity to distribute server work."
        ), 1, 40);
        playerVisionFieldOfViewDegrees = bounded(config.get(
            GAMEPLAY, "playerVisionFieldOfViewDegrees", playerVisionFieldOfViewDegrees,
            "Width of a zombie's vision cone in degrees. A player outside this head-facing "
                + "cone is not noticed until the zombie turns or another sense alerts it."
        ).getDouble(), 1.0D, 360.0D);
        playerGuaranteedDetectionRadius = bounded(config.get(
            GAMEPLAY, "playerGuaranteedDetectionRadius", playerGuaranteedDetectionRadius,
            "Distance, in blocks, at which a visible player inside the vision cone is always "
                + "noticed. Detection becomes less likely beyond this distance."
        ).getDouble(), 0.0D, followRange);
        playerDetectionChanceAtMaximumRangePercent = bounded(config.get(
            GAMEPLAY, "playerDetectionChanceAtMaximumRangePercent",
            playerDetectionChanceAtMaximumRangePercent,
            "Chance per scan to notice an unobstructed player at maximum follow range. "
                + "Detection uses a smooth quadratic falloff from the guaranteed radius."
        ).getDouble(), 0.0D, 100.0D);
        playerSightLossGraceTicks = bounded(config.getInt(
            "playerSightLossGraceTicks", ADVANCED_AI, playerSightLossGraceTicks, 0, 200,
            "How long a directly targeted player may remain out of sight before the brain "
                + "hands pursuit to last-known-position behavior."
        ), 0, 200);
        targetMemoryTicks = bounded(config.getInt(
            "targetMemoryTicks", GAMEPLAY, targetMemoryTicks, 0, 20 * 60 * 10,
            "How long a zombie investigates a player's last known position after losing the "
                + "target. Minecraft runs at 20 ticks per second."
        ), 0, 20 * 60 * 10);
        pursuitPathCalculationsPerTick = bounded(config.getInt(
            "pursuitPathCalculationsPerTick", ADVANCED_AI,
            pursuitPathCalculationsPerTick, 1, 128,
            "Maximum expensive player-pursuit path calculations shared by all zombies in "
                + "one world tick. Existing paths continue to be followed while requests wait."
        ), 1, 128);
        dynamicAiWorkBudget = config.getBoolean(
            "dynamicAiWorkBudget", ADVANCED_AI, dynamicAiWorkBudget,
            "If true, the shared pursuit path-request budget scales down as rolling server "
                + "TPS falls. Zombies continue following existing paths while new requests wait."
        );
        minimumPursuitPathCalculationsPerTick = bounded(config.getInt(
            "minimumPursuitPathCalculationsPerTick", ADVANCED_AI,
            minimumPursuitPathCalculationsPerTick, 1, pursuitPathCalculationsPerTick,
            "Lowest number of pursuit path calculations allowed per world tick when the "
                + "dynamic AI work budget is enabled."
        ), 1, pursuitPathCalculationsPerTick);
        pursuitPathRecalculationMinTicks = bounded(config.getInt(
            "pursuitPathRecalculationMinTicks", ADVANCED_AI,
            pursuitPathRecalculationMinTicks, 1, 200,
            "Shortest randomized delay between pursuit path recalculations for one zombie."
        ), 1, 200);
        pursuitPathRecalculationMaxTicks = bounded(config.getInt(
            "pursuitPathRecalculationMaxTicks", ADVANCED_AI,
            pursuitPathRecalculationMaxTicks, 1, 400,
            "Longest randomized delay between pursuit path recalculations for one zombie."
        ), 1, 400);
        pursuitPathRecalculationMaxTicks = Math.max(
            pursuitPathRecalculationMinTicks, pursuitPathRecalculationMaxTicks
        );
        pursuitHeadTurnDegreesPerTick = bounded(config.get(
            ADVANCED_AI, "pursuitHeadTurnDegreesPerTick",
            pursuitHeadTurnDegreesPerTick,
            "Maximum yaw and pitch change applied to a pursuing zombie's head each tick. "
                + "Lower values produce smoother tracking."
        ).getDouble(), 1.0D, 90.0D);
        enableSharedPursuitFlowFields = config.getBoolean(
            "enableSharedPursuitFlowFields", ADVANCED_AI,
            enableSharedPursuitFlowFields,
            "Uses one block-aware reverse navigation field per pursued player so nearby "
                + "zombies share route computation instead of running independent A* searches."
        );
        pursuitFlowFieldRadius = bounded(config.getInt(
            "pursuitFlowFieldRadius", ADVANCED_AI, pursuitFlowFieldRadius, 8, 128,
            "Horizontal radius in blocks covered by each shared player-pursuit field."
        ), 8, 128);
        pursuitFlowFieldVerticalRange = bounded(config.getInt(
            "pursuitFlowFieldVerticalRange", ADVANCED_AI,
            pursuitFlowFieldVerticalRange, 4, 64,
            "Maximum vertical distance above or below the field target considered by the "
                + "first ground-navigation implementation."
        ), 4, 64);
        pursuitFlowFieldNodesPerTick = bounded(config.getInt(
            "pursuitFlowFieldNodesPerTick", ADVANCED_AI,
            pursuitFlowFieldNodesPerTick, 100, 50000,
            "Maximum shared navigation cells expanded across active player fields per world "
                + "tick. This builds reusable routes incrementally."
        ), 100, 50000);
        pursuitFlowFieldMaximumNodes = bounded(config.getInt(
            "pursuitFlowFieldMaximumNodes", ADVANCED_AI,
            pursuitFlowFieldMaximumNodes, 1000, 250000,
            "Hard memory and search limit for one shared pursuit field."
        ), 1000, 250000);
        pursuitFlowFieldRebuildDistance = bounded(config.get(
            ADVANCED_AI, "pursuitFlowFieldRebuildDistance",
            pursuitFlowFieldRebuildDistance,
            "Horizontal or vertical target movement in blocks that requests a replacement "
                + "field. The previous complete field remains usable while it builds."
        ).getDouble(), 1.0D, 16.0D);
        pursuitFlowFieldMinimumRebuildTicks = bounded(config.getInt(
            "pursuitFlowFieldMinimumRebuildTicks", ADVANCED_AI,
            pursuitFlowFieldMinimumRebuildTicks, 1, 200,
            "Minimum ticks between rebuilding one player's shared pursuit field."
        ), 1, 200);
        pursuitFlowFieldSteeringIntervalTicks = bounded(config.getInt(
            "pursuitFlowFieldSteeringIntervalTicks", ADVANCED_AI,
            pursuitFlowFieldSteeringIntervalTicks, 1, 20,
            "Ticks between direction-field waypoint refreshes for zombies outside the "
                + "full-rate radius. Direction selection is staggered per entity; the last "
                + "selected waypoint is reissued every tick for continuous movement."
        ), 1, 20);
        pursuitFlowFieldFullRateRadius = bounded(config.get(
            ADVANCED_AI, "pursuitFlowFieldFullRateRadius",
            pursuitFlowFieldFullRateRadius,
            "Distance from a pursued player within which direction-field steering refreshes "
                + "every tick for responsive close movement and combat."
        ).getDouble(), 1.0D, pursuitFlowFieldRadius);
        pursuitFlowFieldVanillaFallback = config.getBoolean(
            "pursuitFlowFieldVanillaFallback", ADVANCED_AI,
            pursuitFlowFieldVanillaFallback,
            "Allows the existing budgeted vanilla navigator when the shared ground field is "
                + "still building or cannot represent the zombie's terrain."
        );
        enableLocalWanderNavigation = config.getBoolean(
            "enableLocalWanderNavigation", ADVANCED_AI,
            enableLocalWanderNavigation,
            "Uses the lightweight incremental ground-field navigator for managed-zombie "
                + "wandering and return-to-home movement. Disable this to compare against "
                + "the vanilla per-zombie pathfinder."
        );
        localNavigationNodesPerTick = bounded(config.getInt(
            "localNavigationNodesPerTick", ADVANCED_AI,
            localNavigationNodesPerTick, 100, 50000,
            "Maximum local route cells expanded across all wandering and returning zombies "
                + "in one world tick. Route builds share this budget and continue incrementally."
        ), 100, 50000);
        localNavigationMaximumNodes = bounded(config.getInt(
            "localNavigationMaximumNodes", ADVANCED_AI,
            localNavigationMaximumNodes, 64, 20000,
            "Hard search and memory limit for one short-range wander or return route."
        ), 64, 20000);
        localNavigationDetourRadius = bounded(config.getInt(
            "localNavigationDetourRadius", ADVANCED_AI,
            localNavigationDetourRadius, 0, 16,
            "Extra blocks around the direct start-to-destination distance that a local route "
                + "may inspect while finding a detour."
        ), 0, 16);
        localNavigationStuckTicks = bounded(config.getInt(
            "localNavigationStuckTicks", ADVANCED_AI,
            localNavigationStuckTicks, 10, 400,
            "Ticks without reaching a lower-distance route cell before local navigation "
                + "abandons the route and waits for a later retry."
        ), 10, 400);
        enableCrowdAwarePursuit = config.getBoolean(
            "enableCrowdAwarePursuit", ADVANCED_AI, enableCrowdAwarePursuit,
            "Uses per-tick occupancy and waypoint reservations to distribute zombies that "
                + "are directly pursuing the same player across equal-cost flow-field lanes."
        );
        crowdSteeringMinimumCohortSize = bounded(config.getInt(
            "crowdSteeringMinimumCohortSize", ADVANCED_AI,
            crowdSteeringMinimumCohortSize, 2, 128,
            "Minimum zombies directly pursuing one player before crowd-aware exit selection, "
                + "approach positions, and in-flight fallback deferral are enabled."
        ), 2, 128);
        enableCohortCollisionSuppression = config.getBoolean(
            "enableCohortCollisionSuppression", ADVANCED_AI,
            enableCohortCollisionSuppression,
            "Temporarily prevents zombies in the same sufficiently large direct-pursuit "
                + "cohort from applying vanilla push impulses to one another. Block collision "
                + "and collision with the pursued player remain enabled."
        );
        enableCohortCollisionQueryOptimization = config.getBoolean(
            "enableCohortCollisionQueryOptimization", ADVANCED_AI,
            enableCohortCollisionQueryOptimization,
            "For collision-suppressed pursuit cohorts, reuses the crowd snapshot instead of "
                + "running Minecraft's broad nearby-entity query for every moving zombie. "
                + "Block, player, and non-cohort entity collisions remain enabled."
        );
        crowdCollisionMinimumCohortSize = bounded(config.getInt(
            "crowdCollisionMinimumCohortSize", ADVANCED_AI,
            crowdCollisionMinimumCohortSize, 2, 128,
            "Minimum zombies directly pursuing the same player before same-cohort entity "
                + "push filtering is enabled. Zombies already assigned to external scoreboard "
                + "teams are left untouched."
        ), 2, 128);

        enableStateAwareZombieAudio = config.getBoolean(
            "enableStateAwareZombieAudio", SOUND, enableStateAwareZombieAudio,
            "Controls ambient zombie vocalizations by brain state. Each zombie receives "
                + "vanilla-style randomized opportunities; idle managed hordes share one "
                + "admission budget, while alerted hordes use a separate faster budget. "
                + "Hurt, death, attack, and step sounds are unaffected."
        );
        normalSoundChannels = bounded(config.getInt(
            "normalSoundChannels", ADVANCED_SOUND, normalSoundChannels, 28, 128,
            "Requested client channels for non-streaming sounds. Default is 128; use 64 or "
                + "48 as compatibility fallbacks, or 28 for vanilla behavior. OpenAL "
                + "allocates only what the device supports. Changing this requires a client "
                + "restart."
        ), 28, 128);
        idleHordeSoundIntervalTicks = bounded(config.getInt(
            "idleHordeSoundIntervalTicks", SOUND, idleHordeSoundIntervalTicks, 1, 12000,
            "Minimum delay between idle ambient vocalizations from the same managed horde. "
                + "Higher values make resting hordes quieter. Minecraft runs at 20 ticks "
                + "per second."
        ), 1, 12000);
        alertedHordeSoundIntervalTicks = bounded(config.getInt(
            "alertedHordeSoundIntervalTicks", SOUND,
            alertedHordeSoundIntervalTicks, 1, 12000,
            "Minimum ticks between ambient vocalizations from alerted members of the same "
                + "horde. A shared budget keeps dense hordes loud without starting every "
                + "zombie sound simultaneously."
        ), 1, 12000);
        boolean migratedLegacyProtectedChannels = false;
        if (config.getCategory(LEGACY_AUDIO).containsKey("protectedZombieAmbientChannels")) {
            config.getCategory(LEGACY_AUDIO).remove("protectedZombieAmbientChannels");
            migratedLegacyProtectedChannels = true;
        }
        boolean migratedLegacyZombieLimiter = false;
        if (config.getCategory(LEGACY_AUDIO).containsKey("dynamicZombieSoundLimit")) {
            dynamicSoundChannels = config.getCategory(LEGACY_AUDIO)
                .get("dynamicZombieSoundLimit").getBoolean();
        }
        String[] legacyZombieLimiterSettings = new String[]{
            "dynamicZombieSoundLimit",
            "minimumConcurrentZombieSounds",
            "initialConcurrentZombieSounds",
            "maximumConcurrentZombieSounds"
        };
        for (String setting : legacyZombieLimiterSettings) {
            migratedLegacyZombieLimiter |=
                config.getCategory(LEGACY_AUDIO).remove(setting) != null;
        }
        dynamicSoundChannels = config.getBoolean(
            "dynamicSoundChannels", ADVANCED_SOUND, dynamicSoundChannels,
            "If true, the client throttles new non-critical sounds using the server's "
                + "rolling TPS. Physical OpenAL channels remain allocated until restart."
        );
        minimumDynamicSoundChannels = bounded(config.getInt(
            "minimumDynamicSoundChannels", ADVANCED_SOUND,
            minimumDynamicSoundChannels, 8, 128,
            "Lowest effective non-streaming sound budget used under severe TPS load. "
                + "This cannot exceed normalSoundChannels."
        ), 8, normalSoundChannels);
        mobSoundChannelPercent = bounded(config.get(
            ADVANCED_SOUND, "mobSoundChannelPercent", mobSoundChannelPercent,
            "Maximum percentage of the current effective sound budget admitted for "
                + "HOSTILE and NEUTRAL mob sounds. This is a cap, not a reservation."
        ).getDouble(), 1.0D, 100.0D);
        unmanagedZombieSoundCellSize = bounded(config.getInt(
            "unmanagedZombieSoundCellSize", ADVANCED_SOUND,
            unmanagedZombieSoundCellSize, 4, 128,
            "Horizontal cell size, in blocks, used to share the idle ambient-sound budget "
                + "between zombies that do not have a managed horde ID."
        ), 4, 128);

        walkNoiseRadius = radius(config, "walkNoiseRadius", walkNoiseRadius,
            "Radius of repeated noise made by a walking player.");
        sprintNoiseRadius = radius(config, "sprintNoiseRadius", sprintNoiseRadius,
            "Radius of repeated noise made by a sprinting player.");
        jumpNoiseRadius = radius(config, "jumpNoiseRadius", jumpNoiseRadius,
            "Radius of the one-shot takeoff noise made when a grounded player jumps.");
        landingNoiseRadius = radius(config, "landingNoiseRadius", landingNoiseRadius,
            "Base radius of a player landing impact. Longer falls scale this value up to "
                + "three times the configured radius.");
        blockBreakNoiseRadius = radius(config, "blockBreakNoiseRadius", blockBreakNoiseRadius,
            "Radius of noise made by the initial mining impact and by completing a block break.");
        blockPlaceNoiseRadius = radius(config, "blockPlaceNoiseRadius", blockPlaceNoiseRadius,
            "Radius of noise made when a player places a block.");
        combatNoiseRadius = radius(config, "combatNoiseRadius", combatNoiseRadius,
            "Radius of noise made when a player deals or receives damage.");
        noiseLifetimeTicks = bounded(config.getInt(
            "noiseLifetimeTicks", SOUND, noiseLifetimeTicks, 1, 20 * 60 * 10,
            "How long a zombie remembers and investigates a heard noise. Minecraft runs at "
                + "20 ticks per second."
        ), 1, 20 * 60 * 10);
        footstepNoiseIntervalTicks = bounded(config.getInt(
            "footstepIntervalTicks", ADVANCED_SOUND, footstepNoiseIntervalTicks, 1, 200,
            "Minimum interval in ticks between walking or sprinting sound events from a "
                + "player. Minecraft runs at 20 ticks per second."
        ), 1, 200);
        boolean migratedLegacySimulationMode = false;
        if (config.getCategory(LEGACY_NOISE).containsKey("simulationMode")) {
            realisticSimulation = !"simple".equalsIgnoreCase(
                config.getCategory(LEGACY_NOISE).get("simulationMode").getString()
            );
            config.getCategory(LEGACY_NOISE).remove("simulationMode");
            migratedLegacySimulationMode = true;
        }
        realisticSimulation = config.getBoolean(
            "realisticSimulation", SOUND, realisticSimulation,
            "If true, zombie hearing uses the incrementally budgeted block-aware voxel "
                + "simulation. If false, it uses the lower-cost distance and cached "
                + "material-aware occlusion-ray simulation."
        );
        soundDetectionChancePercent = bounded(config.get(
            SOUND, "soundDetectionChancePercent", soundDetectionChancePercent,
            "Percentage chance that an individual zombie notices each distinct sound event. "
                + "The decision is made once per zombie and event, so an ignored sound is "
                + "not reconsidered on a later AI tick."
        ).getDouble(), 0.0D, 100.0D);
        simpleSoundOcclusionCellSize = bounded(config.getInt(
            "simpleOcclusionCellSize", ADVANCED_SOUND,
            simpleSoundOcclusionCellSize, 1, 16,
            "Width, height, and depth of listener cells that share one simple-mode sound "
                + "occlusion ray. Larger cells reduce raycasts but make obstruction checks "
                + "less precise."
        ), 1, 16);
        soundWaveIntervalTicks = bounded(config.getInt(
            "waveIntervalTicks", SOUND, soundWaveIntervalTicks, 1, 40,
            "Ticks between advances of a realistic sound wave. One advance normally "
                + "moves about one open-air block, so larger values create a slower and "
                + "more visibly progressive response."
        ), 1, 40);
        soundPropagationNodesPerTick = bounded(config.getInt(
            "propagationNodesPerTick", ADVANCED_SOUND,
            soundPropagationNodesPerTick, 100, 50000,
            "Maximum realistic acoustic cells processed across active wavefronts when they "
                + "advance. Lower values reduce CPU spikes but may split a large front across "
                + "multiple advances."
        ), 100, 50000);
        soundMaximumNodesPerEvent = bounded(config.getInt(
            "maximumNodesPerEvent", ADVANCED_SOUND,
            soundMaximumNodesPerEvent, 1000, 250000,
            "Hard memory and work limit for one realistic sound field."
        ), 1000, 250000);
        soundMaximumActiveEvents = bounded(config.getInt(
            "maximumActiveEvents", ADVANCED_SOUND, soundMaximumActiveEvents, 1, 128,
            "Maximum sound events retained per world. New events evict the oldest event."
        ), 1, 128);
        soundDebugParticleBudget = bounded(config.getInt(
            "debugParticleBudget", ADVANCED_SOUND, soundDebugParticleBudget, 16, 2048,
            "Maximum sampled wavefront particles sent to each sound-debug viewer per update."
        ), 16, 2048);

        hordeAlertnessRadius = bounded(config.get(
            HORDES, "alertnessRadius", hordeAlertnessRadius,
            "Distance, in blocks, at which an alerted zombie can influence nearby zombies. "
                + "Larger values make alerts spread through a wider group."
        ).getDouble(), 0.0D, 32.0D);
        hordeAlertMaximumFollowers = bounded(config.getInt(
            "alertMaximumFollowers", HORDES, hordeAlertMaximumFollowers, 0, 32,
            "Maximum zombies that can join one staged alert chain. This does not limit "
                + "zombies that see and target the player on their own."
        ), 0, 32);
        hordeAlertMaximumRecruitsPerZombie = bounded(config.getInt(
            "alertMaximumRecruitsPerZombie", ADVANCED_HORDES,
            hordeAlertMaximumRecruitsPerZombie, 0, 16,
            "Maximum successful recruits carried by the origin or any individual follower."
        ), 0, 16);
        hordeAlertLookChancePercent = bounded(config.get(
            HORDES, "alertLookChancePercent", hordeAlertLookChancePercent,
            "Percentage chance that an eligible nearby zombie notices an alert carrier "
                + "and enters the delayed look stage. This decision is made once per "
                + "zombie and alert episode."
        ).getDouble(), 0.0D, 100.0D);
        hordeAlertFollowChancePercent = bounded(config.get(
            HORDES, "alertFollowChancePercent", hordeAlertFollowChancePercent,
            "Percentage chance that a zombie which passed the look gate commits to following. "
                + "The one-time decision occurs when it begins looking; successful zombies "
                + "wait through the configured follow delay before walking."
        ).getDouble(), 0.0D, 100.0D);
        hordeAlertPropagationIntervalTicks = bounded(config.getInt(
            "alertPropagationIntervalTicks", ADVANCED_HORDES,
            hordeAlertPropagationIntervalTicks, 1, 200,
            "Ticks between local alertness scans by the origin and active followers."
        ), 1, 200);
        hordeAlertLookDelayMinTicks = bounded(config.getInt(
            "alertLookDelayMinTicks", ADVANCED_HORDES,
            hordeAlertLookDelayMinTicks, 0, 200,
            "Shortest delay after passing the look chance before an observing zombie turns "
                + "toward its alert leader."
        ), 0, 200);
        hordeAlertLookDelayMaxTicks = bounded(config.getInt(
            "alertLookDelayMaxTicks", ADVANCED_HORDES,
            hordeAlertLookDelayMaxTicks, 0, 200,
            "Longest randomized delay after passing the look chance before an observing "
                + "zombie turns toward its alert leader."
        ), 0, 200);
        hordeAlertLookDelayMaxTicks = Math.max(
            hordeAlertLookDelayMinTicks, hordeAlertLookDelayMaxTicks
        );
        hordeAlertFollowDelayMinTicks = bounded(config.getInt(
            "alertFollowDelayMinTicks", ADVANCED_HORDES,
            hordeAlertFollowDelayMinTicks, 0, 400,
            "Shortest additional delay after looking before a successful zombie starts "
                + "following."
        ), 0, 400);
        hordeAlertFollowDelayMaxTicks = bounded(config.getInt(
            "alertFollowDelayMaxTicks", ADVANCED_HORDES,
            hordeAlertFollowDelayMaxTicks, 0, 400,
            "Longest randomized additional delay after looking before a successful zombie "
                + "starts following."
        ), 0, 400);
        hordeAlertFollowDelayMaxTicks = Math.max(
            hordeAlertFollowDelayMinTicks, hordeAlertFollowDelayMaxTicks
        );
        hordeFrequencyPercentPerChunk = bounded(config.get(
            HORDES, "frequencyPercentPerChunk", hordeFrequencyPercentPerChunk,
            "Chance, as a percentage, that each eligible chunk requests one persistent "
                + "horde when its region is first planned. Higher values create more hordes."
        ).getDouble(), 0.0D, 100.0D);
        hordeWanderRadius = bounded(config.getInt(
            "wanderRadius", HORDES, hordeWanderRadius, 0, 128,
            "Maximum idle wander distance, in blocks, from a managed horde mob's personal "
                + "anchor. Investigation and completed direct pursuits move this anchor. "
                + "Set to 0 to disable the restriction."
        ), 0, 128);
        hordeWanderIntervalMinTicks = bounded(config.getInt(
            "wanderIntervalMinTicks", ADVANCED_HORDES,
            hordeWanderIntervalMinTicks, 1, 12000,
            "Shortest randomly selected delay between new idle wander paths for members of "
                + "the same managed horde. Minecraft runs at 20 ticks per second."
        ), 1, 12000);
        hordeWanderIntervalMaxTicks = bounded(config.getInt(
            "wanderIntervalMaxTicks", ADVANCED_HORDES,
            hordeWanderIntervalMaxTicks, 1, 12000,
            "Longest randomly selected delay between new idle wander paths for members of "
                + "the same managed horde. Returning to a personal anchor bypasses "
                + "this budget."
        ), 1, 12000);
        hordeWanderIntervalMaxTicks =
            Math.max(hordeWanderIntervalMinTicks, hordeWanderIntervalMaxTicks);
        hordeWanderMaximumActive = bounded(config.getInt(
            "wanderMaximumActive", HORDES, hordeWanderMaximumActive, 0, 32,
            "Maximum number of zombies from one idle horde that may wander at the same time. "
                + "The actual number varies between zero and this value."
        ), 0, 32);
        hordeDefinitionFiles = config.get(
            HORDES, "definitionFiles", hordeDefinitionFiles,
            "Ordered list of horde XML files to load. Paths are relative to the Forge config "
                + "directory, and each file defines one selectable horde profile."
        ).getStringList();

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
        boolean removedLegacySettings = migratedLegacySimulationMode
            || migratedLegacyProtectedChannels
            || migratedLegacyZombieLimiter
            || migratedLayout;
        String[] legacyAlertSettings = new String[]{
            "alertIntervalTicks",
            "alertRadius",
            "alertFollowerDelayMinTicks",
            "alertFollowerDelayMaxTicks"
        };
        for (String setting : legacyAlertSettings) {
            removedLegacySettings |=
                config.getCategory(LEGACY_HORDE).remove(setting) != null;
        }
        removedLegacySettings |=
            config.getCategory(LEGACY_HORDE).remove("fallbackHorde") != null;
        removedLegacySettings |=
            config.getCategory(LEGACY_HORDE).remove("wanderIntervalTicks") != null;
        String[] legacyAudioSettings = new String[]{
            "enableDensityAwareZombieAudio",
            "idleZombieAmbientChance",
            "minimumIdleZombieAmbientChance",
            "idleZombieSoundDensityRadius",
            "idleZombieSoundReferenceCount"
        };
        for (String setting : legacyAudioSettings) {
            removedLegacySettings |=
                config.getCategory(LEGACY_AUDIO).remove(setting) != null;
        }
        String[] legacyPopulationSettings = new String[]{
            "regionSizeChunks",
            "hordeFrequencyPercent",
            "hordeMinimumSize",
            "hordeMaximumSize",
            "hordeRadius",
            "hordeSpreadRadius",
            "hordeTightnessPercent",
            "normalZombieWeight",
            "huskWeight",
            "zombieVillagerWeight",
            "hordeCatalog"
        };
        for (String setting : legacyPopulationSettings) {
            removedLegacySettings |=
                config.getCategory(LEGACY_POPULATION).remove(setting) != null;
        }
        materializedChunksPerTick = bounded(config.getInt(
            "materializedChunksPerTick", ADVANCED_POPULATION,
            materializedChunksPerTick, 1, 64,
            "Maximum loaded chunks checked for seeded population materialization each server tick."
        ), 1, 64);
        materializationRetryTicks = bounded(config.getInt(
            "materializationRetryTicks", ADVANCED_POPULATION,
            materializationRetryTicks, 20, 20 * 60 * 10,
            "Delay before retrying seeded zombies that could not find a valid spawn position."
        ), 20, 20 * 60 * 10);

        enablePerformanceTelemetry = config.getBoolean(
            "enabled", ADVANCED_DIAGNOSTICS, enablePerformanceTelemetry,
            "Measures Zomboid server phases and correlates them with complete server ticks. "
                + "Disable this only when profiling overhead itself must be eliminated."
        );
        performanceStallThresholdMillis = bounded(config.get(
            ADVANCED_DIAGNOSTICS, "stallThresholdMillis",
            performanceStallThresholdMillis,
            "Server-tick duration that writes an immediate phase breakdown to the log. "
                + "Minecraft's normal tick budget is 50 milliseconds."
        ).getDouble(), 50.0D, 10000.0D);
        performanceStallLogCooldownTicks = bounded(config.getInt(
            "stallLogCooldownTicks", ADVANCED_DIAGNOSTICS,
            performanceStallLogCooldownTicks, 0, 1200,
            "Minimum ticks between detailed stall lines. Additional stalls are counted and "
                + "reported by the next permitted line."
        ), 0, 1200);
        performanceSummaryIntervalTicks = bounded(config.getInt(
            "summaryIntervalTicks", ADVANCED_DIAGNOSTICS,
            performanceSummaryIntervalTicks, 100, 12000,
            "Interval between performance summaries. At 20 TPS, 1200 ticks is one minute."
        ), 100, 12000);
        enableVanillaEntityWorkSampler = config.getBoolean(
            "vanillaEntityWorkSampler", ADVANCED_DIAGNOSTICS,
            enableVanillaEntityWorkSampler,
            "Samples the server thread during active ticks to estimate where vanilla entity "
                + "time is spent: AI selectors, navigation advancement, nearby-entity push "
                + "scans, block collision queries, AABB resolution, movement/travel, "
                + "move-helper processing, "
                + "tracking/networking, chunks/world work, and other code. This is a diagnostic "
                + "statistical profiler and does not change entity behavior."
        );
        vanillaEntitySampleIntervalMillis = bounded(config.getInt(
            "vanillaEntitySampleIntervalMillis", ADVANCED_DIAGNOSTICS,
            vanillaEntitySampleIntervalMillis, 1, 100,
            "Milliseconds between server-thread samples while a tick is running. Five "
                + "milliseconds provides useful attribution with modest diagnostic overhead."
        ), 1, 100);

        configureLayout(config);
        removedLegacySettings |= removeEmptyLegacyCategories(config);
        if (save && (config.hasChanged() || removedLegacySettings)) {
            config.save();
        }
    }

    static boolean migrateCurrentLayout(Configuration config) {
        boolean migrated = false;
        migrated |= migrateProperties(config, LEGACY_GENERAL, GAMEPLAY,
            "allowDaylightZombies", "breakWoodenDoors", "movementSpeed", "followRange",
            "playerVisionFieldOfViewDegrees", "playerGuaranteedDetectionRadius",
            "playerDetectionChanceAtMaximumRangePercent", "targetMemoryTicks");
        migrated |= migrateProperties(config, LEGACY_GENERAL, ADVANCED_AI,
            "vanillaPathSearchRange", "playerDetectionIntervalTicks",
            "playerSightLossGraceTicks", "pursuitPathCalculationsPerTick",
            "dynamicAiWorkBudget", "minimumPursuitPathCalculationsPerTick",
            "pursuitPathRecalculationMinTicks", "pursuitPathRecalculationMaxTicks",
            "pursuitHeadTurnDegreesPerTick", "enableSharedPursuitFlowFields",
            "pursuitFlowFieldRadius", "pursuitFlowFieldVerticalRange",
            "pursuitFlowFieldNodesPerTick", "pursuitFlowFieldMaximumNodes",
            "pursuitFlowFieldRebuildDistance", "pursuitFlowFieldMinimumRebuildTicks",
            "pursuitFlowFieldSteeringIntervalTicks", "pursuitFlowFieldFullRateRadius",
            "pursuitFlowFieldVanillaFallback", "enableLocalWanderNavigation",
            "localNavigationNodesPerTick", "localNavigationMaximumNodes",
            "localNavigationDetourRadius", "localNavigationStuckTicks",
            "enableCrowdAwarePursuit", "crowdSteeringMinimumCohortSize",
            "enableCohortCollisionSuppression", "enableCohortCollisionQueryOptimization",
            "crowdCollisionMinimumCohortSize");

        migrated |= migrateProperties(config, LEGACY_HORDE, HORDES,
            "frequencyPercentPerChunk", "wanderRadius", "wanderMaximumActive",
            "alertnessRadius", "alertLookChancePercent", "alertFollowChancePercent",
            "alertMaximumFollowers",
            "definitionFiles");
        migrated |= migrateProperties(config, LEGACY_HORDE, ADVANCED_HORDES,
            "alertMaximumRecruitsPerZombie", "alertPropagationIntervalTicks",
            "alertLookDelayMinTicks", "alertLookDelayMaxTicks",
            "alertFollowDelayMinTicks", "alertFollowDelayMaxTicks",
            "wanderIntervalMinTicks", "wanderIntervalMaxTicks");
        migrated |= migrateProperties(config, ADVANCED_HORDES, HORDES,
            "definitionFiles");

        migrated |= migrateProperties(config, LEGACY_AUDIO, SOUND,
            "enableStateAwareZombieAudio", "idleHordeSoundIntervalTicks",
            "alertedHordeSoundIntervalTicks");
        migrated |= migrateProperties(config, LEGACY_NOISE, SOUND,
            "realisticSimulation", "walkNoiseRadius", "sprintNoiseRadius",
            "jumpNoiseRadius", "landingNoiseRadius", "blockBreakNoiseRadius",
            "blockPlaceNoiseRadius", "combatNoiseRadius", "noiseLifetimeTicks",
            "waveIntervalTicks");
        migrated |= migrateProperties(config, LEGACY_AUDIO, ADVANCED_SOUND,
            "normalSoundChannels", "dynamicSoundChannels", "minimumDynamicSoundChannels",
            "mobSoundChannelPercent", "unmanagedZombieSoundCellSize");
        migrated |= migrateProperties(config, LEGACY_NOISE, ADVANCED_SOUND,
            "footstepIntervalTicks", "simpleOcclusionCellSize", "propagationNodesPerTick",
            "maximumNodesPerEvent", "maximumActiveEvents", "debugParticleBudget");

        migrated |= migrateProperties(config, LEGACY_POPULATION, POPULATION,
            "enableSeededPopulation", "replaceNaturalZombieSpawns",
            "disableVanillaReinforcements", "dimensions");
        migrated |= migrateProperties(config, LEGACY_POPULATION, ADVANCED_POPULATION,
            "materializedChunksPerTick", "materializationRetryTicks");
        migrated |= migrateProperties(config, LEGACY_TELEMETRY, ADVANCED_DIAGNOSTICS,
            "enabled", "stallThresholdMillis", "stallLogCooldownTicks",
            "summaryIntervalTicks", "vanillaEntityWorkSampler",
            "vanillaEntitySampleIntervalMillis");

        ensureCategoryOrder(config);
        return migrated;
    }

    private static boolean migrateProperties(Configuration config, String sourceCategory,
                                             String targetCategory, String... names) {
        if (!config.hasCategory(sourceCategory)) {
            return false;
        }
        ConfigCategory source = config.getCategory(sourceCategory);
        ConfigCategory target = config.getCategory(targetCategory);
        boolean migrated = false;
        for (String name : names) {
            Property legacy = source.remove(name);
            if (legacy == null) {
                continue;
            }
            if (!target.containsKey(name)) {
                target.put(name, legacy);
            }
            migrated = true;
        }
        return migrated;
    }

    private static boolean removeObsoleteVariationDefinitionSetting(Configuration config) {
        boolean removed = false;
        String[] categories = new String[]{LEGACY_HORDE, HORDES, ADVANCED_HORDES};
        for (String category : categories) {
            if (config.hasCategory(category)) {
                removed |= config.getCategory(category).remove("variationDefinitionFiles")
                    != null;
            }
        }
        return removed;
    }

    private static void ensureCategoryOrder(Configuration config) {
        config.getCategory(GAMEPLAY);
        config.getCategory(HORDES);
        config.getCategory(SOUND);
        config.getCategory(POPULATION);
        config.getCategory(ADVANCED_AI);
        config.getCategory(ADVANCED_HORDES);
        config.getCategory(ADVANCED_SOUND);
        config.getCategory(ADVANCED_POPULATION);
        config.getCategory(ADVANCED_DIAGNOSTICS);
    }

    static void configureLayout(Configuration config) {
        config.setCategoryComment(GAMEPLAY,
            "Common zombie behavior settings. Start here when changing how dangerous or "
                + "observant zombies feel.");
        config.setCategoryComment(HORDES,
            "Common horde density, grouping, idle-behavior, and definition-file settings.");
        config.setCategoryComment(SOUND,
            "Player-facing hearing simulation and zombie ambience settings.");
        config.setCategoryComment(POPULATION,
            "Rules controlling persistent world-seeded zombies and vanilla spawning.");
        config.setCategoryComment(ADVANCED,
            "Advanced technical tuning. Defaults are designed to work together. Change these "
                + "only for compatibility, profiling, or deliberate engine tuning.");
        config.setCategoryComment(ADVANCED_AI,
            "Pathfinding, perception scheduling, flow-field, and crowd-performance controls.");
        config.setCategoryComment(ADVANCED_HORDES,
            "Detailed alert timing and wander scheduling controls.");
        config.setCategoryComment(ADVANCED_SOUND,
            "Audio-channel allocation and sound-simulation work and memory limits.");
        config.setCategoryComment(ADVANCED_POPULATION,
            "Per-tick population materialization scheduling and retry controls.");
        config.setCategoryComment(ADVANCED_DIAGNOSTICS,
            "Performance logging and server-thread sampling. These settings do not alter AI "
                + "behavior directly.");

        config.setCategoryPropertyOrder(GAMEPLAY, order(
            "movementSpeed", "allowDaylightZombies", "breakWoodenDoors", "followRange",
            "playerVisionFieldOfViewDegrees", "playerGuaranteedDetectionRadius",
            "playerDetectionChanceAtMaximumRangePercent", "targetMemoryTicks"));
        config.setCategoryPropertyOrder(HORDES, order(
            "frequencyPercentPerChunk", "wanderRadius", "wanderMaximumActive",
            "alertnessRadius", "alertLookChancePercent", "alertFollowChancePercent",
            "alertMaximumFollowers",
            "definitionFiles"));
        config.setCategoryPropertyOrder(SOUND, order(
            "realisticSimulation", "soundDetectionChancePercent", "walkNoiseRadius", "sprintNoiseRadius",
            "jumpNoiseRadius", "landingNoiseRadius", "blockBreakNoiseRadius",
            "blockPlaceNoiseRadius", "combatNoiseRadius", "noiseLifetimeTicks",
            "waveIntervalTicks", "enableStateAwareZombieAudio",
            "idleHordeSoundIntervalTicks", "alertedHordeSoundIntervalTicks"));
        config.setCategoryPropertyOrder(POPULATION, order(
            "enableSeededPopulation", "replaceNaturalZombieSpawns",
            "disableVanillaReinforcements", "dimensions"));

        config.setCategoryPropertyOrder(ADVANCED_AI, order(
            "playerDetectionIntervalTicks", "playerSightLossGraceTicks",
            "vanillaPathSearchRange", "pursuitHeadTurnDegreesPerTick",
            "dynamicAiWorkBudget", "pursuitPathCalculationsPerTick",
            "minimumPursuitPathCalculationsPerTick", "pursuitPathRecalculationMinTicks",
            "pursuitPathRecalculationMaxTicks", "enableSharedPursuitFlowFields",
            "pursuitFlowFieldRadius", "pursuitFlowFieldVerticalRange",
            "pursuitFlowFieldNodesPerTick", "pursuitFlowFieldMaximumNodes",
            "pursuitFlowFieldRebuildDistance", "pursuitFlowFieldMinimumRebuildTicks",
            "pursuitFlowFieldSteeringIntervalTicks", "pursuitFlowFieldFullRateRadius",
            "pursuitFlowFieldVanillaFallback", "enableLocalWanderNavigation",
            "localNavigationNodesPerTick", "localNavigationMaximumNodes",
            "localNavigationDetourRadius", "localNavigationStuckTicks",
            "enableCrowdAwarePursuit", "crowdSteeringMinimumCohortSize",
            "enableCohortCollisionSuppression", "enableCohortCollisionQueryOptimization",
            "crowdCollisionMinimumCohortSize"));
        config.setCategoryPropertyOrder(ADVANCED_HORDES, order(
            "alertMaximumRecruitsPerZombie", "alertPropagationIntervalTicks",
            "alertLookDelayMinTicks", "alertLookDelayMaxTicks",
            "alertFollowDelayMinTicks", "alertFollowDelayMaxTicks",
            "wanderIntervalMinTicks", "wanderIntervalMaxTicks"));
        config.setCategoryPropertyOrder(ADVANCED_SOUND, order(
            "normalSoundChannels", "dynamicSoundChannels", "minimumDynamicSoundChannels",
            "mobSoundChannelPercent", "unmanagedZombieSoundCellSize",
            "footstepIntervalTicks", "simpleOcclusionCellSize", "propagationNodesPerTick",
            "maximumNodesPerEvent", "maximumActiveEvents", "debugParticleBudget"));
        config.setCategoryPropertyOrder(ADVANCED_POPULATION, order(
            "materializedChunksPerTick", "materializationRetryTicks"));
        config.setCategoryPropertyOrder(ADVANCED_DIAGNOSTICS, order(
            "enabled", "stallThresholdMillis", "stallLogCooldownTicks",
            "summaryIntervalTicks", "vanillaEntityWorkSampler",
            "vanillaEntitySampleIntervalMillis"));
    }

    private static ArrayList<String> order(String... names) {
        return new ArrayList<>(Arrays.asList(names));
    }

    private static boolean removeEmptyLegacyCategories(Configuration config) {
        boolean removed = false;
        String[] categories = new String[]{
            LEGACY_GENERAL, LEGACY_AUDIO, LEGACY_NOISE, LEGACY_HORDE,
            LEGACY_POPULATION, LEGACY_TELEMETRY
        };
        for (String name : categories) {
            if (!config.hasCategory(name)) {
                continue;
            }
            ConfigCategory category = config.getCategory(name);
            if (category.getValues().isEmpty() && category.getChildren().isEmpty()) {
                config.removeCategory(category);
                removed = true;
            }
        }
        return removed;
    }

    private static double radius(Configuration config, String name, double defaultValue, String comment) {
        return bounded(config.get(SOUND, name, defaultValue, comment).getDouble(), 0.0D, 128.0D);
    }

    public static boolean isPopulationDimension(int dimension) {
        for (int configuredDimension : populationDimensions) {
            if (configuredDimension == dimension) {
                return true;
            }
        }
        return false;
    }

    public static void setRealisticSimulation(boolean enabled) {
        realisticSimulation = enabled;
        if (activeConfiguration == null) {
            return;
        }
        activeConfiguration.get(
            SOUND,
            "realisticSimulation",
            realisticSimulation,
            "If true, zombie hearing uses the incrementally budgeted block-aware voxel "
                + "simulation. If false, it uses the lower-cost distance and cached "
                + "material-aware occlusion-ray simulation."
        ).set(enabled);
        activeConfiguration.save();
    }

    private static double bounded(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

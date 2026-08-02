package com.jammerbam.zomboid.performance;

/** Mutually exclusive Zomboid server-work phases used in per-tick telemetry. */
public enum PerformancePhase {
    ZOMBIE_BEHAVIOR("zombieBehavior"),
    SOUND_SIMULATION("soundSimulation"),
    SHARED_NAVIGATION("sharedNavigation"),
    LOCAL_NAVIGATION("localNavigation"),
    CROWD_COORDINATION("crowdCoordination"),
    VANILLA_PATH_REQUEST("vanillaPathRequests"),
    VANILLA_PATH_FALLBACK("vanillaPathFallback"),
    POPULATION_MATERIALIZATION("materialization"),
    CHUNK_CALLBACK("chunkCallbacks"),
    WORLD_LOAD_CALLBACK("worldLoadCallback");

    private final String label;

    PerformancePhase(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

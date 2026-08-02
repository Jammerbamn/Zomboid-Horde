package com.jammerbam.zomboid.ai;

/** Tool-tier eligibility and hardness timing for variation-driven zombie digging. */
final class BlockBreakingRules {
    static final int MINIMUM_LEVEL = 1;
    static final int MAXIMUM_LEVEL = 4;
    static final int VANILLA_BASE_TICKS_PER_HARDNESS = 30;
    static final int ZOMBIE_DURATION_MULTIPLIER = 40;
    static final int ESTIMATED_TRAVERSAL_TICKS_PER_BLOCK = 20;

    private BlockBreakingRules() {
    }

    static int requiredLevel(String harvestTool, boolean toolNotRequired,
                             int harvestLevel) {
        if (toolNotRequired || harvestTool == null) {
            return 1;
        }
        return Math.max(0, harvestLevel) + 2;
    }

    static boolean canBreak(int zombieLevel, String harvestTool,
                            boolean toolNotRequired, int harvestLevel) {
        int required = requiredLevel(harvestTool, toolNotRequired, harvestLevel);
        return zombieLevel >= MINIMUM_LEVEL
            && zombieLevel <= MAXIMUM_LEVEL
            && required <= MAXIMUM_LEVEL
            && zombieLevel >= required;
    }

    static int durationTicks(float hardness) {
        if (hardness < 0.0F) {
            return -1;
        }
        double ticks = Math.ceil(
            hardness * VANILLA_BASE_TICKS_PER_HARDNESS * ZOMBIE_DURATION_MULTIPLIER
        );
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1.0D, ticks));
    }

    static boolean shouldUseUnderground(long directTicks, long undergroundTicks) {
        return undergroundTicks >= 0L
            && (directTicks < 0L || undergroundTicks < directTicks);
    }

    static long totalPlanTicks(long breakingTicks, int traversalBlocks) {
        return breakingTicks
            + (long) Math.max(0, traversalBlocks) * ESTIMATED_TRAVERSAL_TICKS_PER_BLOCK;
    }

    static boolean madeObjectiveProgress(double closestDistance,
                                         double currentDistance) {
        return !Double.isFinite(closestDistance)
            || currentDistance <= closestDistance - 0.25D;
    }
}

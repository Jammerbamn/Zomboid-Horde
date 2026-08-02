package com.jammerbam.zomboid.ai;

/** Small reusable per-tick work limiter for server-side AI services. */
public final class TickWorkBudget {
    private long tick = Long.MIN_VALUE;
    private int used;

    public boolean tryAcquire(long currentTick, int maximumPerTick) {
        if (tick != currentTick) {
            tick = currentTick;
            used = 0;
        }
        if (used >= Math.max(1, maximumPerTick)) {
            return false;
        }
        used++;
        return true;
    }
}

package com.jammerbam.zomboid.behavior;

import java.util.Random;

public final class GroupActivitySchedule {
    private GroupActivitySchedule() {
    }

    public static boolean isDue(Long nextAllowed, long now) {
        return nextAllowed == null || nextAllowed <= now;
    }

    public static long nextAt(long now, int intervalTicks) {
        return now + Math.max(1, intervalTicks);
    }

    public static int randomInterval(Random random, int minimumTicks, int maximumTicks) {
        int minimum = Math.max(1, Math.min(minimumTicks, maximumTicks));
        int maximum = Math.max(minimum, Math.max(minimumTicks, maximumTicks));
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    public static int randomCount(Random random, int maximum) {
        return random.nextInt(Math.max(0, maximum) + 1);
    }

    public static int remainingStarts(int selectedActive, int currentActive) {
        return Math.max(0, selectedActive - currentActive);
    }
}

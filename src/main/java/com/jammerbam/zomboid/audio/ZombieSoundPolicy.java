package com.jammerbam.zomboid.audio;

import net.minecraft.util.SoundCategory;

/** Pure sound classification and admission policy shared with client audio. */
public final class ZombieSoundPolicy {
    private ZombieSoundPolicy() {
    }

    public static boolean isZombieSound(String resourcePath) {
        if (resourcePath == null) {
            return false;
        }
        return resourcePath.startsWith("entity.zombie")
            || resourcePath.startsWith("entity.husk");
    }

    public static boolean canAdmit(int active, int maximum) {
        return maximum > 0 && active < maximum;
    }

    public static boolean isMobSound(SoundCategory category) {
        return category == SoundCategory.HOSTILE
            || category == SoundCategory.NEUTRAL;
    }

    public static boolean bypassesGlobalLimit(SoundCategory category) {
        return category == SoundCategory.MASTER
            || category == SoundCategory.MUSIC
            || category == SoundCategory.RECORDS
            || category == SoundCategory.PLAYERS
            || category == SoundCategory.VOICE;
    }

    /**
     * Selects one of four load tiers. Degradation is immediate; recovery crosses
     * a wider threshold and advances only one tier per evaluation.
     */
    public static int selectLoadTier(double ticksPerSecond, int currentTier) {
        int boundedCurrent = Math.max(0, Math.min(3, currentTier));
        int measuredTier;
        if (ticksPerSecond < 15.0D) {
            measuredTier = 3;
        } else if (ticksPerSecond < 18.0D) {
            measuredTier = 2;
        } else if (ticksPerSecond < 19.5D) {
            measuredTier = 1;
        } else {
            measuredTier = 0;
        }

        if (measuredTier > boundedCurrent) {
            return measuredTier;
        }
        if (measuredTier == boundedCurrent) {
            return boundedCurrent;
        }

        if (boundedCurrent == 3 && ticksPerSecond >= 15.5D) {
            return 2;
        }
        if (boundedCurrent == 2 && ticksPerSecond >= 18.5D) {
            return 1;
        }
        if (boundedCurrent == 1 && ticksPerSecond >= 19.75D) {
            return 0;
        }
        return boundedCurrent;
    }

    public static int effectiveChannelBudget(int normalChannels,
                                             int minimumChannels,
                                             int loadTier) {
        int maximum = Math.max(1, normalChannels);
        int minimum = Math.max(1, Math.min(minimumChannels, maximum));
        int[] percentages = new int[]{100, 75, 50, 25};
        int tier = Math.max(0, Math.min(3, loadTier));
        int scaled = maximum * percentages[tier] / 100;
        return Math.max(minimum, scaled);
    }

    public static int mobSoundBudget(int effectiveChannels,
                                     double mobPercent) {
        double boundedPercent = Math.max(0.0D, Math.min(100.0D, mobPercent));
        return Math.max(1, (int) Math.floor(
            Math.max(1, effectiveChannels) * boundedPercent / 100.0D
        ));
    }
}

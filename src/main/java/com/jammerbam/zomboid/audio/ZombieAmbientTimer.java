package com.jammerbam.zomboid.audio;

/** Reproduces EntityLiving's per-entity ambient-sound opportunity timer. */
final class ZombieAmbientTimer {
    private int soundTime;

    ZombieAmbientTimer(int talkInterval) {
        reset(talkInterval);
    }

    /**
     * Mirrors {@code random.nextInt(1000) < livingSoundTime++}. A due
     * opportunity resets even if a separate horde-level limiter later rejects
     * the sound, preventing a blocked zombie from retrying on every tick.
     */
    boolean advance(int talkInterval, int randomRoll) {
        int threshold = soundTime++;
        if (randomRoll >= threshold) {
            return false;
        }
        reset(talkInterval);
        return true;
    }

    int getSoundTime() {
        return soundTime;
    }

    private void reset(int talkInterval) {
        soundTime = -Math.max(1, talkInterval);
    }
}

package com.jammerbam.zomboid.variation;

final class VariationSeed {
    private static final long VARIATION_SALT = 0x4D3B2A1908F7E6D5L;

    private VariationSeed() {
    }

    static long derive(long worldSeed, int dimension, String populationId) {
        long value = worldSeed ^ VARIATION_SALT;
        value ^= mix64(((long) dimension << 32) ^ dimension);
        long text = 0xCBF29CE484222325L;
        for (int i = 0; i < populationId.length(); i++) {
            text ^= populationId.charAt(i);
            text *= 0x100000001B3L;
        }
        return mix64(value ^ text);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}

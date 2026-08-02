package com.jammerbam.zomboid.ai.navigation;

import java.util.HashMap;
import java.util.Map;

/** Fixed-capacity open-addressed map used by one bounded flow-field build. */
final class PrimitiveLongIntMap {
    private static final float LOAD_FACTOR = 0.65F;

    private final long[] keys;
    private final int[] values;
    private final int[] metadata;
    private final byte[] occupied;
    private final int mask;
    private int size;

    PrimitiveLongIntMap(int maximumEntries) {
        int requested = Math.max(2, maximumEntries);
        int capacity = 1;
        long needed = (long) Math.ceil(requested / LOAD_FACTOR);
        while (capacity < needed && capacity < 1 << 30) {
            capacity <<= 1;
        }
        keys = new long[capacity];
        values = new int[capacity];
        metadata = new int[capacity];
        occupied = new byte[capacity];
        mask = capacity - 1;
    }

    int getOrDefault(long key, int fallback) {
        int slot = slot(key);
        return occupied[slot] == 0 ? fallback : values[slot];
    }

    boolean putIfAbsent(long key, int value) {
        int slot = slot(key);
        if (occupied[slot] != 0) {
            return false;
        }
        occupied[slot] = 1;
        keys[slot] = key;
        values[slot] = value;
        size++;
        return true;
    }

    int getMetadataOrDefault(long key, int fallback) {
        int slot = slot(key);
        return occupied[slot] == 0 ? fallback : metadata[slot];
    }

    boolean setMetadata(long key, int value) {
        int slot = slot(key);
        if (occupied[slot] == 0) {
            return false;
        }
        metadata[slot] = value;
        return true;
    }

    int size() {
        return size;
    }

    Map<Long, Integer> boxedSnapshot() {
        Map<Long, Integer> result = new HashMap<>(size);
        for (int i = 0; i < occupied.length; i++) {
            if (occupied[i] != 0) {
                result.put(keys[i], values[i]);
            }
        }
        return result;
    }

    private int slot(long key) {
        int slot = mix(key) & mask;
        while (occupied[slot] != 0 && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (int) value;
    }
}

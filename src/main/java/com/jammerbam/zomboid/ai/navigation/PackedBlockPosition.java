package com.jammerbam.zomboid.ai.navigation;

/** Allocation-free access to Minecraft 1.12.2's BlockPos long encoding. */
final class PackedBlockPosition {
    static final long NONE = Long.MIN_VALUE;

    private static final int X_BITS = 26;
    private static final int Y_BITS = 12;
    private static final int Z_BITS = 26;
    private static final int Y_SHIFT = Z_BITS;
    private static final int X_SHIFT = Y_SHIFT + Y_BITS;
    private static final long X_MASK = (1L << X_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;

    private PackedBlockPosition() {
    }

    static long pack(int x, int y, int z) {
        return ((long) x & X_MASK) << X_SHIFT
            | ((long) y & Y_MASK) << Y_SHIFT
            | (long) z & Z_MASK;
    }

    static int x(long packed) {
        return (int) (packed << (64 - X_SHIFT - X_BITS) >> (64 - X_BITS));
    }

    static int y(long packed) {
        return (int) (packed << (64 - Y_SHIFT - Y_BITS) >> (64 - Y_BITS));
    }

    static int z(long packed) {
        return (int) (packed << (64 - Z_BITS) >> (64 - Z_BITS));
    }
}

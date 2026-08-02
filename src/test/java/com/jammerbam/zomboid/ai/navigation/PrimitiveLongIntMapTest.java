package com.jammerbam.zomboid.ai.navigation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrimitiveLongIntMapTest {
    @Test
    public void storesZeroNegativeAndManyPackedKeysWithoutBoxedEntries() {
        PrimitiveLongIntMap map = new PrimitiveLongIntMap(2048);
        assertTrue(map.putIfAbsent(0L, 11));
        assertTrue(map.putIfAbsent(-1L, 12));
        for (int i = 0; i < 2000; i++) {
            assertTrue(map.putIfAbsent(PackedBlockPosition.pack(i, 64, -i), i));
        }

        assertEquals(11, map.getOrDefault(0L, -1));
        assertEquals(12, map.getOrDefault(-1L, -1));
        for (int i = 0; i < 2000; i++) {
            assertEquals(
                i,
                map.getOrDefault(PackedBlockPosition.pack(i, 64, -i), -1)
            );
        }
        assertEquals(2002, map.size());
        assertFalse(map.putIfAbsent(0L, 99));
        assertEquals(11, map.getOrDefault(0L, -1));
        assertEquals(-1, map.getMetadataOrDefault(99L, -1));
        assertFalse(map.setMetadata(99L, 7));
        assertTrue(map.setMetadata(0L, 0x1234));
        assertEquals(0x1234, map.getMetadataOrDefault(0L, -1));
    }
}

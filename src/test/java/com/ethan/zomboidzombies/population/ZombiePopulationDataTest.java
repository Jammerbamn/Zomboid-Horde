package com.ethan.zomboidzombies.population;

import com.ethan.zomboidzombies.config.ModConfig;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ZombiePopulationDataTest {
    @Before
    public void configureRegionSize() {
        ModConfig.populationRegionSizeChunks = 8;
    }

    @Test
    public void populationChangesRoundTripThroughWorldSaveNbt() {
        ZombiePopulationData original = new ZombiePopulationData();
        HordeRecord horde = new HordeRecord(
            -2, 4, "d0:r-2,4:g0", -200, 520, 30, 32, 80, 10, 10
        );
        original.initializeRegion(-2, 4, horde);
        original.markMaterialized("d0:r-2,4:z0");
        original.markMaterialized("d0:r-2,4:z1");
        original.markDead("d0:r-2,4:z0");

        NBTTagCompound saved = original.writeToNBT(new NBTTagCompound());
        ZombiePopulationData restored = new ZombiePopulationData();
        restored.readFromNBT(saved);

        assertTrue(restored.isRegionInitialized(-2, 4));
        assertNotNull(restored.getHorde(-2, 4));
        assertEquals(30, restored.getHorde(-2, 4).getPlannedSize());
        assertTrue(restored.isDead("d0:r-2,4:z0"));
        assertFalse(restored.isMaterialized("d0:r-2,4:z0"));
        assertTrue(restored.isMaterialized("d0:r-2,4:z1"));
        assertEquals(1, restored.getDeadCount());
        assertEquals(1, restored.getMaterializedCount());
    }

    @Test
    public void emptyInitializedRegionsRemainEmptyAfterReload() {
        ZombiePopulationData original = new ZombiePopulationData();
        original.initializeRegion(7, -9, null);

        ZombiePopulationData restored = new ZombiePopulationData();
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));

        assertTrue(restored.isRegionInitialized(7, -9));
        assertNull(restored.getHorde(7, -9));
    }

    @Test
    public void savedRegionSizeIsNotChangedByLaterConfigurationEdits() {
        ZombiePopulationData original = new ZombiePopulationData();
        assertEquals(8, original.getRegionSizeChunks());
        NBTTagCompound saved = original.writeToNBT(new NBTTagCompound());

        ModConfig.populationRegionSizeChunks = 16;
        ZombiePopulationData restored = new ZombiePopulationData();
        restored.readFromNBT(saved);

        assertEquals(8, restored.getRegionSizeChunks());
    }
}

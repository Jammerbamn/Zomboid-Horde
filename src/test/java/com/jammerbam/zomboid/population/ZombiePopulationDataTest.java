package com.jammerbam.zomboid.population;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZombiePopulationDataTest {
    @Test
    public void multipleHordesRoundTripThroughWorldSaveNbt() {
        ZombiePopulationData original = new ZombiePopulationData();
        HordeRecord first = horde(1, -2, 32, -64, "d0:c32,-64:g0");
        HordeRecord second = horde(1, -2, 40, -60, "d0:c40,-60:g0");
        original.initializeRegion(1, -2, Arrays.asList(first, second));
        original.markMaterialized(first.getGroupId() + ":z0");
        original.markMaterialized(first.getGroupId() + ":z1");
        original.markDead(first.getGroupId() + ":z0");

        ZombiePopulationData restored = new ZombiePopulationData();
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));

        assertTrue(restored.isRegionInitialized(1, -2));
        assertEquals(2, restored.getHordesInRegion(1, -2).size());
        assertEquals("zomboid:test",
            restored.getHordesInRegion(1, -2).get(0).getDefinitionId());
        assertEquals(2, restored.getHordesInRegion(1, -2).get(0).getMembers().size());
        assertEquals("zomboid:tough", restored.getHordesInRegion(1, -2).get(0)
            .getMembers().get(0).getVariations().get(0).getVariationId());
        assertTrue(restored.isDead(first.getGroupId() + ":z0"));
        assertFalse(restored.isMaterialized(first.getGroupId() + ":z0"));
        assertTrue(restored.isMaterialized(first.getGroupId() + ":z1"));
    }

    @Test
    public void emptyInitializedRegionsRemainEmptyAfterReload() {
        ZombiePopulationData original = new ZombiePopulationData();
        original.initializeRegion(7, -9, Collections.<HordeRecord>emptyList());

        ZombiePopulationData restored = new ZombiePopulationData();
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));

        assertTrue(restored.isRegionInitialized(7, -9));
        assertTrue(restored.getHordesInRegion(7, -9).isEmpty());
    }

    @Test
    public void versionOneRegionsAndHordesMigrateIntoNativePlanningRegions() {
        NBTTagCompound legacySave = new NBTTagCompound();
        legacySave.setInteger("SaveVersion", 1);
        legacySave.setInteger("GeneratorVersion", 1);
        legacySave.setInteger("RegionSizeChunks", 8);

        NBTTagCompound legacyRegion = new NBTTagCompound();
        legacyRegion.setInteger("X", 4);
        legacyRegion.setInteger("Z", -4);
        NBTTagList regions = new NBTTagList();
        regions.appendTag(legacyRegion);
        legacySave.setTag("InitializedRegions", regions);

        NBTTagCompound legacyHorde = new NBTTagCompound();
        legacyHorde.setInteger("RegionX", 4);
        legacyHorde.setInteger("RegionZ", -4);
        legacyHorde.setString("GroupId", "d0:r4,-4:g0");
        legacyHorde.setInteger("CenterX", 520);
        legacyHorde.setInteger("CenterZ", -504);
        legacyHorde.setInteger("PlannedSize", 30);
        legacyHorde.setInteger("SpreadRadius", 32);
        legacyHorde.setInteger("NormalWeight", 80);
        legacyHorde.setInteger("HuskWeight", 10);
        legacyHorde.setInteger("VillagerWeight", 10);
        NBTTagList hordes = new NBTTagList();
        hordes.appendTag(legacyHorde);
        legacySave.setTag("Hordes", hordes);

        ZombiePopulationData restored = new ZombiePopulationData();
        restored.readFromNBT(legacySave);

        assertTrue(restored.isRegionInitialized(1, -1));
        assertEquals(1, restored.getHordesInRegion(1, -1).size());
        assertEquals("zomboid:legacy",
            restored.getHordesInRegion(1, -1).get(0).getDefinitionId());
        assertEquals("d0:r4,-4:z0", SeededPopulationGenerator.populationId(
            restored.getHordesInRegion(1, -1).get(0), 0
        ));
        assertEquals(32, restored.getRegionSizeChunks());
    }

    @Test
    public void regenerationResetClearsPlansDeathsAndMaterialization() {
        ZombiePopulationData data = new ZombiePopulationData();
        HordeRecord record = horde(1, -2, 32, -64, "d0:c32,-64:g0");
        String aliveId = record.getGroupId() + ":z0";
        String deadId = record.getGroupId() + ":z1";
        data.initializeRegion(1, -2, Collections.singletonList(record));
        data.markMaterialized(aliveId);
        data.markDead(deadId);

        data.resetForRegeneration();

        assertEquals(0, data.getInitializedRegionCount());
        assertEquals(0, data.getHordeCount());
        assertEquals(0, data.getMaterializedCount());
        assertEquals(0, data.getDeadCount());
        assertFalse(data.isRegionInitialized(1, -2));
        assertFalse(data.isMaterialized(aliveId));
        assertFalse(data.isDead(deadId));
        assertEquals(1, data.getRegenerationEpoch());

        ZombiePopulationData restored = new ZombiePopulationData();
        restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        assertEquals(1, restored.getRegenerationEpoch());
    }

    private static HordeRecord horde(
        int regionX,
        int regionZ,
        int anchorChunkX,
        int anchorChunkZ,
        String id
    ) {
        return new HordeRecord(
            regionX,
            regionZ,
            anchorChunkX,
            anchorChunkZ,
            id,
            "zomboid:test",
            anchorChunkX * 16 + 8,
            anchorChunkZ * 16 + 8,
            20,
            12,
            Arrays.asList(
                new HordeMember(
                    "minecraft:zombie",
                    80,
                    Collections.singletonList(new HordeVariation("zomboid:tough", 15))
                ),
                new HordeMember("minecraft:husk", 20)
            )
        );
    }
}

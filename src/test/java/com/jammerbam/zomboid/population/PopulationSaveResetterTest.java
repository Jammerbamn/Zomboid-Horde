package com.jammerbam.zomboid.population;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PopulationSaveResetterTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void resetsOfflineLedgersAndRemovesLegacyData() throws Exception {
        Path world = temporary.newFolder("world").toPath();
        Path overworldData = world.resolve("data");
        Path netherData = world.resolve("DIM-1").resolve("data");
        Path endData = world.resolve("DIM1").resolve("data");
        Files.createDirectories(overworldData);
        Files.createDirectories(netherData);
        Files.createDirectories(endData);

        Path loadedLedger = overworldData.resolve(ZombiePopulationData.DATA_NAME + ".dat");
        Path legacyLedger = netherData.resolve(
            ZombiePopulationData.LEGACY_DATA_NAME + ".dat"
        );
        Path currentEnd = endData.resolve(ZombiePopulationData.DATA_NAME + ".dat");
        write(loadedLedger, populated());
        write(legacyLedger, populated());
        write(currentEnd, populated());

        PopulationSaveResetter.ResetPlan plan = PopulationSaveResetter.prepare(
            world, Collections.singleton(overworldData)
        );

        assertEquals(2, plan.getOfflineLedgerCount());
        assertEquals(2, plan.getClearedRegionCount());
        assertEquals(2, plan.getClearedHordeCount());
        plan.apply();

        ZombiePopulationData loaded = read(loadedLedger);
        assertEquals(1, loaded.getHordeCount());

        Path currentNether = netherData.resolve(ZombiePopulationData.DATA_NAME + ".dat");
        assertTrue(Files.exists(currentNether));
        assertFalse(Files.exists(legacyLedger));
        ZombiePopulationData reset = read(currentNether);
        assertEquals(0, reset.getInitializedRegionCount());
        assertEquals(0, reset.getHordeCount());
        assertEquals(0, reset.getDeadCount());
        assertEquals(0, reset.getMaterializedCount());
        assertEquals(1, reset.getRegenerationEpoch());

        ZombiePopulationData resetEnd = read(currentEnd);
        assertEquals(0, resetEnd.getInitializedRegionCount());
        assertEquals(0, resetEnd.getHordeCount());
        assertEquals(1, resetEnd.getRegenerationEpoch());
    }

    private static ZombiePopulationData populated() {
        ZombiePopulationData data = new ZombiePopulationData();
        HordeRecord horde = new HordeRecord(
            0, 0, 0, 0, "d0:c0,0:g0", "zomboid:test", 8, 8, 1, 8,
            Collections.singletonList(new HordeMember("minecraft:zombie", 1))
        );
        data.initializeRegion(0, 0, Collections.singletonList(horde));
        data.markMaterialized("d0:c0,0:g0:z0");
        data.markDead("d0:c0,0:g0:z0");
        return data;
    }

    private static void write(Path path, ZombiePopulationData data) throws Exception {
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("data", data.writeToNBT(new NBTTagCompound()));
        try (OutputStream output = Files.newOutputStream(path)) {
            CompressedStreamTools.writeCompressed(wrapper, output);
        }
    }

    private static ZombiePopulationData read(Path path) throws Exception {
        NBTTagCompound wrapper;
        try (InputStream input = Files.newInputStream(path)) {
            wrapper = CompressedStreamTools.readCompressed(input);
        }
        ZombiePopulationData data = new ZombiePopulationData();
        data.readFromNBT(wrapper.getCompoundTag("data"));
        return data;
    }
}

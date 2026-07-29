package com.ethan.zomboidzombies.population;

import com.ethan.zomboidzombies.config.ModConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SeededPopulationGeneratorTest {
    @Before
    public void configureGenerator() {
        ModConfig.populationRegionSizeChunks = 8;
        ModConfig.hordeFrequencyPercent = 100;
        ModConfig.hordeMinimumSize = 24;
        ModConfig.hordeMaximumSize = 24;
        ModConfig.hordeSpreadRadius = 32;
        ModConfig.normalZombieWeight = 80;
        ModConfig.huskWeight = 10;
        ModConfig.zombieVillagerWeight = 10;
    }

    @Test
    public void sameWorldSeedAndRegionProduceTheSamePlan() {
        HordeRecord first = SeededPopulationGenerator.generateHorde(987654321L, 0, -2, 3, 8);
        HordeRecord second = SeededPopulationGenerator.generateHorde(987654321L, 0, -2, 3, 8);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getGroupId(), second.getGroupId());
        assertEquals(first.getCenterX(), second.getCenterX());
        assertEquals(first.getCenterZ(), second.getCenterZ());
        assertEquals(first.getPlannedSize(), second.getPlannedSize());

        List<ZombieSpawnPlan> firstSlots =
            SeededPopulationGenerator.generateSlots(987654321L, 0, 8, first);
        List<ZombieSpawnPlan> secondSlots =
            SeededPopulationGenerator.generateSlots(987654321L, 0, 8, second);

        assertEquals(firstSlots.size(), secondSlots.size());
        for (int i = 0; i < firstSlots.size(); i++) {
            ZombieSpawnPlan a = firstSlots.get(i);
            ZombieSpawnPlan b = secondSlots.get(i);
            assertEquals(a.getPopulationId(), b.getPopulationId());
            assertEquals(a.getX(), b.getX());
            assertEquals(a.getZ(), b.getZ());
            assertEquals(a.getKind(), b.getKind());
        }
    }

    @Test
    public void differentWorldSeedsChangeTheGeneratedPlan() {
        HordeRecord first = SeededPopulationGenerator.generateHorde(111L, 0, 4, 7, 8);
        HordeRecord second = SeededPopulationGenerator.generateHorde(222L, 0, 4, 7, 8);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.getCenterX() != second.getCenterX()
            || first.getCenterZ() != second.getCenterZ());
    }

    @Test
    public void generatedSlotsStayInsideTheirPopulationRegion() {
        int regionX = -3;
        int regionZ = 5;
        int regionBlocks = 8 * 16;
        HordeRecord horde = SeededPopulationGenerator.generateHorde(
            4444L, 0, regionX, regionZ, 8
        );

        assertNotNull(horde);
        for (ZombieSpawnPlan slot :
            SeededPopulationGenerator.generateSlots(4444L, 0, 8, horde)) {
            assertTrue(slot.getX() >= regionX * regionBlocks);
            assertTrue(slot.getX() < (regionX + 1) * regionBlocks);
            assertTrue(slot.getZ() >= regionZ * regionBlocks);
            assertTrue(slot.getZ() < (regionZ + 1) * regionBlocks);
        }
    }

    @Test
    public void makeupWeightsControlGeneratedZombieKinds() {
        ModConfig.normalZombieWeight = 0;
        ModConfig.huskWeight = 100;
        ModConfig.zombieVillagerWeight = 0;
        HordeRecord horde = SeededPopulationGenerator.generateHorde(333L, 0, 1, 1, 8);

        assertNotNull(horde);
        for (ZombieSpawnPlan slot :
            SeededPopulationGenerator.generateSlots(333L, 0, 8, horde)) {
            assertEquals(ZombieKind.HUSK, slot.getKind());
        }
        assertNotEquals(0, horde.getPlannedSize());
    }
}

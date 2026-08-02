package com.jammerbam.zomboid.population;

import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SeededPopulationGeneratorTest {
    private static final BiomeResolver PLAINS =
        (x, z) -> new BiomeDescriptor("minecraft:plains", setOf("PLAINS"));

    @Test
    public void sameSeedAndPlanningRegionProduceTheSameHordes() {
        HordeCatalog catalog = catalog(4.0D, 12, 18, 16);
        SeededPopulationGenerator.PlanningResult first =
            SeededPopulationGenerator.generatePlanningRegion(
                987654321L, 0, -2, 3, catalog, PLAINS
            );
        SeededPopulationGenerator.PlanningResult second =
            SeededPopulationGenerator.generatePlanningRegion(
                987654321L, 0, -2, 3, catalog, PLAINS
            );

        assertEquals(first.getRequestedCount(), second.getRequestedCount());
        assertEquals(first.getBlockedCount(), second.getBlockedCount());
        assertEquals(first.getHordes().size(), second.getHordes().size());
        for (int i = 0; i < first.getHordes().size(); i++) {
            HordeRecord a = first.getHordes().get(i);
            HordeRecord b = second.getHordes().get(i);
            assertEquals(a.getGroupId(), b.getGroupId());
            assertEquals(a.getDefinitionId(), b.getDefinitionId());
            assertEquals(a.getCenterX(), b.getCenterX());
            assertEquals(a.getCenterZ(), b.getCenterZ());
            assertEquals(a.getPlannedSize(), b.getPlannedSize());
        }
    }

    @Test
    public void acceptedHordeFootprintsNeverOverlap() {
        HordeCatalog catalog = catalog(20.0D, 10, 10, 24);
        List<HordeRecord> hordes = SeededPopulationGenerator.generatePlanningRegion(
            4444L, 0, 0, 0, catalog, PLAINS
        ).getHordes();

        assertFalse(hordes.isEmpty());
        for (int first = 0; first < hordes.size(); first++) {
            for (int second = first + 1; second < hordes.size(); second++) {
                HordeRecord a = hordes.get(first);
                HordeRecord b = hordes.get(second);
                long dx = (long) a.getCenterX() - b.getCenterX();
                long dz = (long) a.getCenterZ() - b.getCenterZ();
                long minimum = (long) a.getSpreadRadius() + b.getSpreadRadius();
                assertTrue(dx * dx + dz * dz >= minimum * minimum);
            }
        }
    }

    @Test
    public void overlapProtectionIsStableAcrossPlanningRegionBoundaries() {
        HordeCatalog catalog = catalog(20.0D, 10, 10, 24);
        List<HordeRecord> hordes = new ArrayList<>();
        hordes.addAll(SeededPopulationGenerator.generatePlanningRegion(
            554433L, 0, 0, 0, catalog, PLAINS
        ).getHordes());
        hordes.addAll(SeededPopulationGenerator.generatePlanningRegion(
            554433L, 0, 1, 0, catalog, PLAINS
        ).getHordes());

        for (int first = 0; first < hordes.size(); first++) {
            for (int second = first + 1; second < hordes.size(); second++) {
                HordeRecord a = hordes.get(first);
                HordeRecord b = hordes.get(second);
                long dx = (long) a.getCenterX() - b.getCenterX();
                long dz = (long) a.getCenterZ() - b.getCenterZ();
                long minimum = (long) a.getSpreadRadius() + b.getSpreadRadius();
                assertTrue(dx * dx + dz * dz >= minimum * minimum);
            }
        }
    }

    @Test
    public void successfulFrequencyRollUsesUniversalAllFallback() {
        HordeDefinition standard = new HordeDefinition(
            "zomboid:standard",
            4,
            4,
            1,
            Collections.singletonList(new HordeMember("minecraft:zombie", 1)),
            Collections.singletonList(new BiomeWeight("ALL", 1.0D))
        );
        HordeDefinition ocean = new HordeDefinition(
            "zomboid:ocean",
            4,
            4,
            1,
            Collections.singletonList(new HordeMember("minecraft:drowned", 1)),
            Collections.singletonList(new BiomeWeight("OCEAN", 9.0D))
        );
        HordeCatalog catalog = new HordeCatalog(
            100.0D,
            Arrays.asList(standard, ocean)
        );
        SeededPopulationGenerator.PlanningResult result =
            SeededPopulationGenerator.generatePlanningRegion(
                123L, 0, 0, 0, catalog, PLAINS
            );

        assertEquals(1024, result.getRequestedCount());
        assertFalse(result.getHordes().isEmpty());
        for (HordeRecord horde : result.getHordes()) {
            assertEquals(standard.getId(), horde.getDefinitionId());
        }
    }

    @Test
    public void sizeAndEntityMakeupAreCapturedInGeneratedSlots() {
        HordeCatalog catalog = catalog(
            100.0D,
            12,
            28,
            1,
            Collections.singletonList(new HordeMember(
                "minecraft:husk",
                100,
                Collections.singletonList(new HordeVariation("zomboid:test_husk", 1))
            ))
        );
        HordeRecord horde = SeededPopulationGenerator.generatePlanningRegion(
            333L, 0, 0, 0, catalog, PLAINS
        ).getHordes().get(0);

        assertTrue(horde.getPlannedSize() >= 12);
        assertTrue(horde.getPlannedSize() <= 28);
        for (ZombieSpawnPlan slot :
            SeededPopulationGenerator.generateSlots(333L, 0, horde)) {
            assertEquals("minecraft:husk", slot.getEntityId());
            assertEquals("zomboid:test_husk", slot.getVariationId());
            double dx = slot.getX() - horde.getCenterX();
            double dz = slot.getZ() - horde.getCenterZ();
            assertTrue(Math.sqrt(dx * dx + dz * dz) <= horde.getSpreadRadius() + 1.0D);
        }
    }

    @Test
    public void standardHordeChoiceProducesVanillaSpawnPlan() {
        HordeMember member = new HordeMember(
            "minecraft:zombie",
            1,
            Collections.singletonList(new HordeVariation(HordeVariation.STANDARD_ID, 1))
        );
        HordeRecord horde = new HordeRecord(
            0, 0, 0, 0, "d0:c0,0:g0", "zomboid:test",
            8, 8, 1, 8, Collections.singletonList(member)
        );

        ZombieSpawnPlan plan = SeededPopulationGenerator.generateSlots(
            123L, 0, horde
        ).get(0);

        assertNull(plan.getVariationId());
        assertTrue(new HordeVariation(HordeVariation.STANDARD_ID, 1).isStandard());
        assertFalse(new HordeVariation("zomboid:tough", 1).isStandard());
    }

    @Test
    public void biomeWeightUsesExactThenHighestTypeThenAll() {
        HordeDefinition definition = new HordeDefinition(
            "zomboid:desert",
            10,
            10,
            8,
            Collections.singletonList(new HordeMember("minecraft:husk", 1)),
            Arrays.asList(
                new BiomeWeight("ALL", 1.0D),
                new BiomeWeight("HOT", 3.0D),
                new BiomeWeight("DRY", 2.0D),
                new BiomeWeight("minecraft:desert", 6.0D)
            )
        );
        BiomeDescriptor desert =
            new BiomeDescriptor("minecraft:desert", setOf("HOT", "DRY"));
        BiomeDescriptor savanna =
            new BiomeDescriptor("minecraft:savanna", setOf("HOT", "DRY"));
        BiomeDescriptor forest =
            new BiomeDescriptor("minecraft:forest", setOf("FOREST"));

        assertEquals(6.0D, definition.effectiveBiomeWeight(desert), 0.0001D);
        assertEquals(3.0D, definition.effectiveBiomeWeight(savanna), 0.0001D);
        assertEquals(1.0D, definition.effectiveBiomeWeight(forest), 0.0001D);
    }

    @Test
    public void catalogRequiresAnAllOnlyFallbackDefinition() {
        HordeDefinition plainsOnly = new HordeDefinition(
            "zomboid:plains",
            4,
            4,
            1,
            Collections.singletonList(new HordeMember("minecraft:zombie", 1)),
            Collections.singletonList(new BiomeWeight("PLAINS", 1.0D))
        );

        try {
            new HordeCatalog(100.0D, Collections.singletonList(plainsOnly));
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Catalog accepted definitions without an ALL-only fallback");
    }

    private static HordeCatalog catalog(
        double frequency,
        int minimum,
        int maximum,
        int radius
    ) {
        return catalog(
            frequency,
            minimum,
            maximum,
            radius,
            Arrays.asList(
                new HordeMember("minecraft:zombie", 80),
                new HordeMember("minecraft:husk", 20)
            )
        );
    }

    private static HordeCatalog catalog(
        double frequency,
        int minimum,
        int maximum,
        int radius,
        List<HordeMember> members
    ) {
        HordeDefinition definition = new HordeDefinition(
            "zomboid:test",
            minimum,
            maximum,
            radius,
            members,
            Collections.singletonList(new BiomeWeight("ALL", 1.0D))
        );
        return new HordeCatalog(
            frequency,
            Collections.singletonList(definition)
        );
    }

    private static HashSet<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}

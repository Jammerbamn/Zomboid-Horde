package com.jammerbam.zomboid.population;

import com.jammerbam.zomboid.variation.ZombieVariationCatalog;
import com.jammerbam.zomboid.variation.ZombieVariationDefinitions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.apache.logging.log4j.LogManager;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HordeDefinitionsTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void missingConfigurationCreatesAndLoadsOnlyBundledStandard() throws Exception {
        File config = temporary.newFolder("config");

        HordeDefinitions.load(
            config,
            15.0D,
            new String[]{"zomboid/hordes/standard.xml"},
            LogManager.getLogger(HordeDefinitionsTest.class)
        );

        assertEquals(1, HordeDefinitions.get().getDefinitions().size());
        assertEquals(15.0D,
            HordeDefinitions.get().getFrequencyPercentPerChunk(), 0.000001D);
        HordeDefinition standard = HordeDefinitions.get().getDefinitions().stream()
            .filter(definition -> "zomboid:standard".equals(definition.getId()))
            .findFirst()
            .get();
        assertEquals(6, standard.getMinimumSize());
        assertEquals(12, standard.getMaximumSize());
        assertEquals(8, standard.getRadius());
        assertEquals(5, standard.getMembers().get(0).getVariations().size());
        assertEquals("zomboid:standard",
            standard.getMembers().get(0).getVariations().get(0).getVariationId());
        assertEquals(68,
            standard.getMembers().get(0).getVariations().get(0).getWeight());
        assertEquals("zomboid:sprinter",
            standard.getMembers().get(0).getVariations().get(1).getVariationId());
        assertTrue(standard.isUniversalBiomeFallback());
        assertEquals("ALL", standard.getBiomeWeights().get(0).getSelector());
        assertEquals(1.0D, standard.getBiomeWeights().get(0).getWeight(), 0.000001D);
        assertFalse(new File(config, "zomboid/horde-catalog.json").exists());
        assertTrue(new File(config, "zomboid/hordes/standard.xml").isFile());
        assertFalse(new File(config, "zomboid/hordes/desert.xml").exists());
    }

    @Test
    public void ignoresBlankLinesAroundConfiguredDefinitionPaths() throws Exception {
        File config = temporary.newFolder("config-with-blank-lines");

        assertTrue(HordeDefinitions.load(
            config,
            7.0D,
            new String[]{"", "  zomboid/hordes/standard.xml  ", "   "},
            LogManager.getLogger(HordeDefinitionsTest.class)
        ));

        assertEquals(1, HordeDefinitions.get().getDefinitions().size());
        assertEquals("zomboid:standard",
            HordeDefinitions.get().getDefinitions().get(0).getId());
    }

    @Test
    public void bundledStandardReferencesOnlyBundledVariations() throws Exception {
        File config = temporary.newFolder("cross-file-defaults");
        assertTrue(ZombieVariationDefinitions.load(
            config, LogManager.getLogger(HordeDefinitionsTest.class)
        ));
        assertTrue(HordeDefinitions.load(
            config,
            7.0D,
            new String[]{"zomboid/hordes/standard.xml"},
            LogManager.getLogger(HordeDefinitionsTest.class)
        ));

        ZombieVariationCatalog variations = ZombieVariationDefinitions.get();
        for (HordeMember member : HordeDefinitions.get().getDefinitions().get(0).getMembers()) {
            for (HordeVariation reference : member.getVariations()) {
                if (!reference.isStandard()) {
                    assertTrue("Missing bundled variation " + reference.getVariationId()
                            + " for " + member.getEntityId(),
                        variations.get(reference.getVariationId(), member.getEntityId()) != null);
                }
            }
        }
    }

    @Test
    public void invalidConfiguredPathFallsBackToCurrentStandardProfile() throws Exception {
        File config = temporary.newFolder("fallback-defaults");

        assertFalse(HordeDefinitions.load(
            config,
            7.0D,
            new String[]{"zomboid/hordes/missing.xml"},
            LogManager.getLogger(HordeDefinitionsTest.class)
        ));

        HordeDefinition fallback = HordeDefinitions.get().getDefinitions().get(0);
        assertEquals("zomboid:standard", fallback.getId());
        assertEquals(6, fallback.getMinimumSize());
        assertEquals(12, fallback.getMaximumSize());
        assertEquals("zomboid:buff_zombie", fallback.getMembers().get(1).getEntityId());
        assertEquals("zomboid:sprinter",
            fallback.getMembers().get(0).getVariations().get(1).getVariationId());
    }
}

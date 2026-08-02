package com.jammerbam.zomboid.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModConfigTest {
    @Test
    public void createsPlayerCategoriesAndNestedAdvancedSections() {
        ModConfig.movementSpeed = 0.23D;
        ModConfig.hordeFrequencyPercentPerChunk = 7.0D;
        ModConfig.hordeWanderRadius = 4;
        ModConfig.hordeAlertnessRadius = 4.0D;
        ModConfig.hordeDefinitionFiles =
            new String[]{"zomboid/hordes/standard.xml"};
        Configuration config = new Configuration();

        ModConfig.load(config);

        Set<String> names = config.getCategoryNames();
        assertTrue(names.contains("01_gameplay"));
        assertTrue(names.contains("02_hordes"));
        assertTrue(names.contains("03_sound"));
        assertTrue(names.contains("04_population"));
        assertTrue(names.contains("99_advanced"));
        assertTrue(names.contains("99_advanced.ai"));
        assertTrue(names.contains("99_advanced.hordes"));
        assertTrue(names.contains("99_advanced.sound"));
        assertTrue(names.contains("99_advanced.population"));
        assertTrue(names.contains("99_advanced.diagnostics"));

        List<String> gameplayOrder = config.getCategory("01_gameplay").getPropertyOrder();
        assertEquals("movementSpeed", gameplayOrder.get(0));
        assertEquals("allowDaylightZombies", gameplayOrder.get(1));
        assertEquals(32.0D,
            config.getCategory("01_gameplay").get("followRange").getDouble(),
            0.000001D);
        assertEquals(0.23D,
            config.getCategory("01_gameplay").get("movementSpeed").getDouble(),
            0.000001D);
        assertEquals(7.0D,
            config.getCategory("02_hordes").get("frequencyPercentPerChunk").getDouble(),
            0.000001D);
        assertEquals(4, config.getCategory("02_hordes").get("wanderRadius").getInt());
        assertEquals(4.0D,
            config.getCategory("02_hordes").get("alertnessRadius").getDouble(),
            0.000001D);
        assertEquals(1,
            config.getCategory("02_hordes").get("definitionFiles").getStringList().length);
        assertEquals("zomboid/hordes/standard.xml",
            config.getCategory("02_hordes").get("definitionFiles").getStringList()[0]);
        assertEquals(8, config.getCategory("01_gameplay").getValues().size());
        assertEquals(8, config.getCategory("02_hordes").getValues().size());
        assertEquals(14, config.getCategory("03_sound").getValues().size());
        assertEquals(75.0D,
            config.getCategory("03_sound").get("soundDetectionChancePercent").getDouble(),
            0.000001D);
        assertEquals(35.0D,
            config.getCategory("02_hordes").get("alertLookChancePercent").getDouble(),
            0.000001D);
        assertEquals(75.0D,
            config.getCategory("02_hordes").get("alertFollowChancePercent").getDouble(),
            0.000001D);
        assertEquals(10,
            config.getCategory("99_advanced.hordes").get("alertLookDelayMinTicks").getInt());
        assertEquals(25,
            config.getCategory("99_advanced.hordes").get("alertLookDelayMaxTicks").getInt());
        assertEquals(40,
            config.getCategory("99_advanced.hordes").get("alertFollowDelayMinTicks").getInt());
        assertEquals(80,
            config.getCategory("99_advanced.hordes").get("alertFollowDelayMaxTicks").getInt());
        assertEquals(4, config.getCategory("04_population").getValues().size());

        ConfigCategory advanced = config.getCategory("99_advanced");
        assertEquals(5, advanced.getChildren().size());
        assertEquals(29, config.getCategory("99_advanced.ai").getValues().size());
        assertEquals(8, config.getCategory("99_advanced.hordes").getValues().size());
        assertEquals(11, config.getCategory("99_advanced.sound").getValues().size());
        assertEquals(2, config.getCategory("99_advanced.population").getValues().size());
        assertEquals(6, config.getCategory("99_advanced.diagnostics").getValues().size());
        assertFalse(names.contains("general"));
        assertFalse(names.contains("horde"));
        assertFalse(names.contains("noise"));
        assertFalse(names.contains("audio"));
        assertFalse(names.contains("telemetry"));
    }

    @Test
    public void migratesCurrentLayoutWithoutLosingValues() {
        Configuration config = new Configuration();
        config.get("general", "movementSpeed", 0.31D).set(0.31D);
        config.get("horde", "frequencyPercentPerChunk", 7.25D).set(7.25D);
        config.get("horde", "wanderRadius", 11).set(11);
        config.get("horde", "definitionFiles",
            new String[]{"zomboid/hordes/custom.xml"});
        config.get("horde", "variationDefinitionFiles",
            new String[]{"zomboid/custom-variations.xml"});
        config.get("noise", "realisticSimulation", false).set(false);
        config.get("audio", "normalSoundChannels", 64).set(64);
        config.get("telemetry", "enabled", false).set(false);

        ModConfig.load(config);

        assertEquals(0.31D,
            config.getCategory("01_gameplay").get("movementSpeed").getDouble(), 0.000001D);
        assertEquals(7.25D,
            config.getCategory("02_hordes").get("frequencyPercentPerChunk").getDouble(),
            0.000001D);
        assertEquals(11, config.getCategory("02_hordes").get("wanderRadius").getInt());
        assertEquals("zomboid/hordes/custom.xml",
            config.getCategory("02_hordes").get("definitionFiles").getStringList()[0]);
        assertFalse(config.getCategory("03_sound").get("realisticSimulation").getBoolean());
        assertEquals(64,
            config.getCategory("99_advanced.sound").get("normalSoundChannels").getInt());
        assertFalse(config.getCategory("99_advanced.diagnostics").get("enabled").getBoolean());

        assertFalse(config.getCategory("general").containsKey("movementSpeed"));
        assertFalse(config.getCategory("horde").containsKey("frequencyPercentPerChunk"));
        assertFalse(config.getCategory("horde").containsKey("variationDefinitionFiles"));
        assertFalse(config.getCategory("02_hordes").containsKey("variationDefinitionFiles"));
        assertFalse(config.getCategory("99_advanced.hordes")
            .containsKey("variationDefinitionFiles"));
        assertFalse(config.getCategory("noise").containsKey("realisticSimulation"));
        assertFalse(config.getCategory("audio").containsKey("normalSoundChannels"));
        assertFalse(config.getCategory("telemetry").containsKey("enabled"));
    }

    @Test
    public void movesDefinitionFilesOutOfAdvancedAndRemovesVariationList() {
        Configuration config = new Configuration();
        config.get("99_advanced.hordes", "definitionFiles",
            new String[]{"zomboid/hordes/legacy-new-layout.xml"});
        config.get("99_advanced.hordes", "variationDefinitionFiles",
            new String[]{"zomboid/unused.xml"});

        ModConfig.load(config);

        assertEquals("zomboid/hordes/legacy-new-layout.xml",
            config.getCategory("02_hordes").get("definitionFiles").getStringList()[0]);
        assertFalse(config.getCategory("99_advanced.hordes").containsKey("definitionFiles"));
        assertFalse(config.getCategory("99_advanced.hordes")
            .containsKey("variationDefinitionFiles"));
    }
}

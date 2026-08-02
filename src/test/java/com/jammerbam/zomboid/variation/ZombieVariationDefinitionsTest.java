package com.jammerbam.zomboid.variation;

import net.minecraft.inventory.EntityEquipmentSlot;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ZombieVariationDefinitionsTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void missingConfigurationCreatesAndLoadsCurrentBundledCatalog() throws Exception {
        Path root = temporary.newFolder("bundled-config").toPath();

        ZombieVariationDefinitions.load(
            root.toFile(), LogManager.getLogger(ZombieVariationDefinitionsTest.class)
        );

        Path generated = root.resolve("zomboid/variations.xml");
        assertEquals(6, ZombieVariationDefinitions.get().getDefinitions().size());
        assertNotNull(ZombieVariationDefinitions.get().get("zomboid:sprinter"));
        assertNotNull(ZombieVariationDefinitions.get().get("zomboid:tank"));
        assertNotNull(ZombieVariationDefinitions.get().get("zomboid:jogger"));
        assertNotNull(ZombieVariationDefinitions.get().get("zomboid:shambler")
            .getEquipment().get(EntityEquipmentSlot.MAINHAND));
        assertEquals(true, Files.isRegularFile(generated));
    }

    @Test
    public void loadsMultipleProfilesAndNamespacedEquipmentFromOneFile() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path file = write(root, "zomboid/variations/test.xml",
            "<?xml version=\"1.0\"?><variations formatVersion=\"1\">"
                + "<entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:normal\"/>"
                + "<variation id=\"test:worker\" blockBreakingLevel=\"3\">"
                + "<attributes health=\"30\" speed=\"0.17\" damage=\"6\""
                + " knockbackResistance=\"0.35\" swimSpeed=\"1.4\"/>"
                + "<items><mainhand chancePercent=\"75\">"
                + "<item id=\"examplemod:wrench\" weight=\"2\" metadata=\"4\""
                + " count=\"1\" dropChancePercent=\"5\">"
                + "<nbt>{\"Energy\":1000}</nbt></item>"
                + "</mainhand></items>"
                + "<effects><onHit potion=\"minecraft:poison\" durationSeconds=\"6.5\""
                + " amplifier=\"1\" chancePercent=\"35\"/></effects>"
                + "</variation></entity></variations>"
        );

        ZombieVariationCatalog catalog = ZombieVariationDefinitions.readCatalog(
            root, new String[]{root.relativize(file).toString()}
        );

        assertEquals(2, catalog.getDefinitions().size());
        ZombieVariationDefinition worker = catalog.getDefinitions().get(1);
        assertEquals("test:worker", worker.getId());
        assertEquals(30.0D, worker.getMaximumHealth(), 0.0001D);
        assertEquals(0.17D, worker.getMovementSpeed(), 0.0001D);
        assertEquals(6.0D, worker.getAttackDamage(), 0.0001D);
        assertEquals(0.35D, worker.getKnockbackResistance(), 0.0001D);
        assertEquals(1.4D, worker.getSwimSpeed(), 0.0001D);
        assertEquals(Integer.valueOf(3), worker.getBlockBreakingLevel());
        ZombieVariationDefinition.EquipmentPool pool =
            worker.getEquipment().get(EntityEquipmentSlot.MAINHAND);
        assertNotNull(pool);
        assertEquals(75.0D, pool.getChancePercent(), 0.0001D);
        assertEquals("examplemod:wrench", pool.getChoices().get(0).getItemId());
        assertEquals(4, pool.getChoices().get(0).getMetadata());
        assertNotNull(pool.getChoices().get(0).getNbtJson());
        assertEquals(1, worker.getOnHitEffects().size());
        ZombieVariationDefinition.OnHitEffect effect = worker.getOnHitEffects().get(0);
        assertEquals("minecraft:poison", effect.getPotionId());
        assertEquals(6.5D, effect.getDurationSeconds(), 0.0001D);
        assertEquals(130, effect.getDurationTicks());
        assertEquals(1, effect.getAmplifier());
        assertEquals(35.0D, effect.getChancePercent(), 0.0001D);
    }

    @Test
    public void supportsReadableShorthandAndDefaultNamespace() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"soldier\"><attributes speed=\"0.30\" health=\"30\""
                + " damage=\"5\"/><items>"
                + "<head item=\"minecraft:golden_helmet\"/>"
                + "<chest>minecraft:golden_chestplate</chest>"
                + "<hand item=\"minecraft:iron_sword\"/>"
                + "</items></variation></entity></variations>"
        );

        ZombieVariationDefinition soldier = ZombieVariationDefinitions.readCatalog(
            root, new String[]{"variations.xml"}
        ).get("zomboid:soldier");

        assertNotNull(soldier);
        assertEquals(0.30D, soldier.getMovementSpeed(), 0.0001D);
        assertNotNull(soldier.getEquipment().get(EntityEquipmentSlot.HEAD));
        assertNotNull(soldier.getEquipment().get(EntityEquipmentSlot.CHEST));
        assertNotNull(soldier.getEquipment().get(EntityEquipmentSlot.MAINHAND));
    }

    @Test(expected = IOException.class)
    public void rejectsMultipleRegistryIdsInOneEntityField() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\">"
                + "<entity id=\"minecraft:zombie,minecraft:husk\">"
                + "<variation id=\"test:invalid\"/></entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsEquipmentSlotsOutsideItemsSection() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:invalid\"><mainhand item=\"minecraft:stick\"/>"
                + "</variation></entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsKnockbackResistanceAboveAttributeLimit() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:invalid\"><attributes"
                + " knockbackResistance=\"1.01\"/></variation>"
                + "</entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsSwimSpeedAboveForgeAttributeLimit() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:invalid\"><attributes swimSpeed=\"1025\"/>"
                + "</variation></entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsBlockBreakingLevelAboveIronTier() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:invalid\" blockBreakingLevel=\"5\"/>"
                + "</entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsDuplicateProfileIdsAcrossFiles() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "first.xml", single("test:duplicate"));
        write(root, "second.xml", single("test:duplicate"));
        ZombieVariationDefinitions.readCatalog(
            root, new String[]{"first.xml", "second.xml"}
        );
    }

    @Test(expected = IOException.class)
    public void rejectsPathsOutsideConfigRoot() throws Exception {
        ZombieVariationDefinitions.readCatalog(
            temporary.getRoot().toPath(), new String[]{"../outside.xml"}
        );
    }

    @Test(expected = IOException.class)
    public void rejectsProfileSelectionWeight() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:weighted\" weight=\"10\"/>"
                + "</entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsReservedStandardProfile() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"standard\"/></entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsUnknownVariationEffectElements() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:invalid\"><effects>"
                + "<particle type=\"smoke\"/>"
                + "</effects></variation></entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test(expected = IOException.class)
    public void rejectsRetiredTickDurationField() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:invalid\"><effects>"
                + "<onHit potion=\"minecraft:poison\" durationTicks=\"100\"/>"
                + "</effects></variation></entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"variations.xml"});
    }

    @Test
    public void acceptsCatalogWithNoCustomVariations() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml", "<variations formatVersion=\"1\"/>");

        ZombieVariationCatalog catalog = ZombieVariationDefinitions.readCatalog(
            root, new String[]{"variations.xml"}
        );

        assertEquals(0, catalog.getDefinitions().size());
    }

    @Test(expected = IOException.class)
    public void rejectsDoctypeAndExternalEntities() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "unsafe.xml",
            "<?xml version=\"1.0\"?><!DOCTYPE variations ["
                + "<!ENTITY outside SYSTEM \"file:///definitely-not-readable\">]>"
                + "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
                + "<variation id=\"test:unsafe\"><items>"
                + "<head>&outside;</head></items></variation>"
                + "</entity></variations>"
        );
        ZombieVariationDefinitions.readCatalog(root, new String[]{"unsafe.xml"});
    }

    @Test
    public void catalogRegistersProfilesByNamespacedId() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\">"
                + "<entity id=\"minecraft:zombie\"><variation id=\"test:zombie\"/>"
                + "</entity><entity id=\"minecraft:husk\">"
                + "<variation id=\"test:husk\"/></entity></variations>"
        );
        ZombieVariationCatalog catalog = ZombieVariationDefinitions.readCatalog(
            root, new String[]{"variations.xml"}
        );

        assertEquals("test:zombie", catalog.get("test:zombie").getId());
        assertEquals("test:husk", catalog.get("test:husk").getId());
        assertEquals(true, catalog.get("test:zombie").appliesTo("minecraft:zombie"));
        assertNull(catalog.get("test:missing"));
    }

    @Test
    public void resolvesSameVariationIdSeparatelyForEachEntity() throws Exception {
        Path root = temporary.getRoot().toPath();
        write(root, "variations.xml",
            "<variations formatVersion=\"1\">"
                + "<entity id=\"minecraft:zombie\"><variation id=\"soldier\">"
                + "<attributes health=\"30\"/></variation></entity>"
                + "<entity id=\"minecraft:husk\"><variation id=\"soldier\">"
                + "<attributes health=\"40\"/></variation></entity>"
                + "</variations>"
        );

        ZombieVariationCatalog catalog = ZombieVariationDefinitions.readCatalog(
            root, new String[]{"variations.xml"}
        );

        assertEquals(30.0D,
            catalog.get("zomboid:soldier", "minecraft:zombie").getMaximumHealth(),
            0.0001D);
        assertEquals(40.0D,
            catalog.get("zomboid:soldier", "minecraft:husk").getMaximumHealth(),
            0.0001D);
        assertNull(catalog.get("zomboid:soldier", "minecraft:skeleton"));
    }

    @Test
    public void seedIsStableAndPopulationSpecific() {
        long first = VariationSeed.derive(1234L, 0, "d0:c1,2:g0:z3");
        assertEquals(first, VariationSeed.derive(1234L, 0, "d0:c1,2:g0:z3"));
        assertNotEquals(first, VariationSeed.derive(1234L, 0, "d0:c1,2:g0:z4"));
        assertNotEquals(first, VariationSeed.derive(1234L, 1, "d0:c1,2:g0:z3"));
    }

    private static Path write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String single(String id) {
        return "<variations formatVersion=\"1\"><entity id=\"minecraft:zombie\">"
            + "<variation id=\"" + id + "\"/></entity></variations>";
    }
}

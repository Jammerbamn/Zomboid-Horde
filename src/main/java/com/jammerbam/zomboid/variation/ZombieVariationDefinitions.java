package com.jammerbam.zomboid.variation;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.population.HordeVariation;
import com.jammerbam.zomboid.population.ZombieSpawnPlan;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public final class ZombieVariationDefinitions {
    private static final String DEFAULT_RESOURCE =
        "/assets/zomboid/default-config/variations.xml";
    private static final String DEFAULT_PATH = "zomboid/variations.xml";
    private static ZombieVariationCatalog active = emptyCatalog();
    private static Logger logger;

    private ZombieVariationDefinitions() {
    }

    public static boolean load(File configDirectory, Logger log) {
        logger = log;
        try {
            Path configRoot = configDirectory.toPath().toAbsolutePath().normalize();
            Path defaultPath = resolveInside(configRoot, DEFAULT_PATH);
            copyDefaultIfMissing(defaultPath, DEFAULT_RESOURCE);
            active = readCatalog(configRoot, new String[]{DEFAULT_PATH});
            if (logger != null) {
                logger.info(
                    "Loaded {} zombie variation profiles from {}.",
                    active.getDefinitions().size(), DEFAULT_PATH
                );
            }
            return true;
        } catch (Exception exception) {
            active = emptyCatalog();
            if (logger != null) {
                logger.error(
                    "Could not load zombie variation definitions. Custom variations are disabled.",
                    exception
                );
            }
            return false;
        }
    }

    public static void validateRegistries() {
        List<ZombieVariationDefinition> validated = new ArrayList<>();
        for (ZombieVariationDefinition definition : active.getDefinitions()) {
            Set<String> entityIds = new LinkedHashSet<>();
            for (String entityId : definition.getEntityIds()) {
                Class<? extends Entity> entityClass =
                    EntityList.getClass(new ResourceLocation(entityId));
                if (entityClass == null || !EntityLiving.class.isAssignableFrom(entityClass)) {
                    warn("Ignoring unavailable variation entity {} in {}.", entityId,
                        definition.getId());
                } else {
                    entityIds.add(entityId);
                }
            }
            if (entityIds.isEmpty()) {
                warn("Disabling variation {} because none of its living entities are available.",
                    definition.getId());
                continue;
            }

            EnumMap<EntityEquipmentSlot, ZombieVariationDefinition.EquipmentPool> equipment =
                new EnumMap<>(EntityEquipmentSlot.class);
            for (Map.Entry<EntityEquipmentSlot, ZombieVariationDefinition.EquipmentPool> entry
                : definition.getEquipment().entrySet()) {
                List<ZombieVariationDefinition.EquipmentChoice> choices = new ArrayList<>();
                for (ZombieVariationDefinition.EquipmentChoice choice
                    : entry.getValue().getChoices()) {
                    ResourceLocation itemId = new ResourceLocation(choice.getItemId());
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item == null || item == Items.AIR) {
                        warn("Ignoring unavailable variation item {} in {}.",
                            choice.getItemId(), definition.getId());
                        continue;
                    }
                    choices.add(choice);
                }
                equipment.put(entry.getKey(), new ZombieVariationDefinition.EquipmentPool(
                    entry.getValue().getChancePercent(), choices
                ));
            }
            List<ZombieVariationDefinition.OnHitEffect> effects = new ArrayList<>();
            for (ZombieVariationDefinition.OnHitEffect effect
                : definition.getOnHitEffects()) {
                ResourceLocation potionId = new ResourceLocation(effect.getPotionId());
                if (ForgeRegistries.POTIONS.getValue(potionId) == null) {
                    warn("Ignoring unavailable on-hit potion {} in {}.",
                        effect.getPotionId(), definition.getId());
                } else {
                    effects.add(effect);
                }
            }
            validated.add(copy(definition, entityIds, equipment, effects));
        }

        active = new ZombieVariationCatalog(validated);
    }

    public static ZombieVariationCatalog get() {
        return active;
    }

    /**
     * Rehydrates config-derived runtime tags on an already materialized entity.
     * Equipment and living attributes are deliberately left untouched.
     */
    public static boolean refreshRuntimeTags(EntityLiving living) {
        ResourceLocation entityId = EntityList.getKey(living);
        String variationId = VariationTags.getVariationId(living);
        if (entityId == null || variationId.isEmpty()) {
            return false;
        }
        ZombieVariationDefinition definition = active.get(
            variationId, entityId.toString()
        );
        if (definition == null) {
            return false;
        }
        VariationTags.apply(living, definition);
        return true;
    }

    public static void apply(WorldServer world, EntityLiving living, ZombieSpawnPlan plan) {
        ResourceLocation key = EntityList.getKey(living);
        if (key == null) {
            return;
        }
        ZombieVariationDefinition definition = active.get(
            plan.getVariationId(), key.toString()
        );
        if (definition == null) {
            return;
        }
        Random random = new Random(VariationSeed.derive(
            world.getSeed(), world.provider.getDimension(), plan.getPopulationId()
        ));

        setAttribute(living, SharedMonsterAttributes.MAX_HEALTH,
            definition.getMaximumHealth());
        setAttribute(living, SharedMonsterAttributes.MOVEMENT_SPEED,
            definition.getMovementSpeed());
        setAttribute(living, SharedMonsterAttributes.ATTACK_DAMAGE,
            definition.getAttackDamage());
        setAttribute(living, SharedMonsterAttributes.KNOCKBACK_RESISTANCE,
            definition.getKnockbackResistance());
        setAttribute(living, EntityLivingBase.SWIM_SPEED,
            definition.getSwimSpeed());
        if (definition.getMaximumHealth() != null) {
            living.setHealth(living.getMaxHealth());
        }

        for (Map.Entry<EntityEquipmentSlot, ZombieVariationDefinition.EquipmentPool> entry
            : definition.getEquipment().entrySet()) {
            applyEquipment(living, entry.getKey(), entry.getValue(), random);
        }
        VariationTags.apply(living, definition);
    }

    static ZombieVariationCatalog readCatalog(Path configRoot, String[] definitionFiles)
        throws IOException {
        if (definitionFiles == null || definitionFiles.length == 0) {
            throw new IOException("No variation definition file was provided");
        }
        List<ZombieVariationDefinition> definitions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String definitionFile : definitionFiles) {
            if (definitionFile == null || definitionFile.trim().isEmpty()) {
                throw new IOException("Variation definition paths may not be empty");
            }
            Path path = resolveInside(configRoot, definitionFile.trim());
            Element root = readDocument(path).getDocumentElement();
            if (!"variations".equals(root.getTagName())) {
                throw new IOException("Expected <variations> as the root element in " + path);
            }
            rejectUnknownChildren(root, path, "entity");
            int formatVersion = requireIntAttribute(
                root, "formatVersion", 1, 1, path
            );
            if (formatVersion != 1) {
                throw new IOException("Unsupported formatVersion " + formatVersion + " in " + path);
            }
            for (Element entity : childElements(root, "entity")) {
                rejectUnknownChildren(entity, path, "variation");
                Set<String> entityIds = readEntityId(entity, path);
                List<Element> variations = childElements(entity, "variation");
                if (variations.isEmpty()) {
                    throw new IOException("Variation entity has no <variation> elements in "
                        + path);
                }
                for (Element element : variations) {
                    ZombieVariationDefinition definition = readDefinition(
                        element, entityIds, path
                    );
                    String scopedId = entityIds.iterator().next() + "\u0000"
                        + definition.getId();
                    if (!ids.add(scopedId)) {
                        throw new IOException("Duplicate variation id " + definition.getId()
                            + " for entity " + entityIds.iterator().next());
                    }
                    definitions.add(definition);
                }
            }
        }
        return new ZombieVariationCatalog(definitions);
    }

    private static Set<String> readEntityId(Element entity, Path path) throws IOException {
        String entityId = requireAttribute(entity, "id", path);
        if (entityId.indexOf(',') >= 0 || entityId.matches(".*\\s+.*")) {
            throw new IOException("Each <entity> must contain exactly one registry ID in "
                + path + ": " + entityId);
        }
        requireNamespacedValue(entityId, "entity id", path);
        Set<String> result = new LinkedHashSet<>();
        result.add(entityId);
        return result;
    }

    private static ZombieVariationDefinition readDefinition(
        Element element, Set<String> entityIds, Path path
    ) throws IOException {
        rejectUnknownChildren(element, path, "attributes", "items", "effects");
        if (element.hasAttribute("weight")) {
            throw new IOException("Variation profile weights belong in horde member "
                + "definitions, not in " + path);
        }
        String id = variationId(requireAttribute(element, "id", path), path);
        if (HordeVariation.STANDARD_ID.equals(id)) {
            throw new IOException(HordeVariation.STANDARD_ID + " is reserved for the vanilla "
                + "choice in horde definitions and must not be registered in " + path);
        }
        Integer blockBreakingLevel = element.hasAttribute("blockBreakingLevel")
            ? optionalIntAttribute(element, "blockBreakingLevel", 1, 1, 4, path)
            : null;

        Element attributes = optionalSingleChild(element, "attributes", path);
        Double health = null;
        Double speed = null;
        Double damage = null;
        Double knockbackResistance = null;
        Double swimSpeed = null;
        if (attributes != null) {
            health = optionalAliasedDoubleAttribute(
                attributes, "health", "maxHealth", 1.0D, 2048.0D, path
            );
            speed = optionalAliasedDoubleAttribute(
                attributes, "speed", "movementSpeed", 0.01D, 2.0D, path
            );
            damage = optionalAliasedDoubleAttribute(
                attributes, "damage", "attackDamage", 0.0D, 2048.0D, path
            );
            knockbackResistance = optionalAliasedDoubleAttribute(
                attributes, "knockbackResistance", "knockback", 0.0D, 1.0D, path
            );
            swimSpeed = optionalAliasedDoubleAttribute(
                attributes, "swimSpeed", "swim", 0.0D, 1024.0D, path
            );
        }

        EnumMap<EntityEquipmentSlot, ZombieVariationDefinition.EquipmentPool> equipment =
            new EnumMap<>(EntityEquipmentSlot.class);
        Element items = optionalSingleChild(element, "items", path);
        if (items != null) {
            for (Element slotElement : childElements(items, null)) {
                EntityEquipmentSlot slot = slot(slotElement.getTagName(), path);
                if (equipment.containsKey(slot)) {
                    throw new IOException("Duplicate equipment slot "
                        + slotElement.getTagName() + " in " + path);
                }
                equipment.put(slot, readEquipmentPool(slotElement, path));
            }
        }
        List<ZombieVariationDefinition.OnHitEffect> onHitEffects =
            readEffects(element, path);
        return new ZombieVariationDefinition(
            id, entityIds, health, speed, damage, knockbackResistance, swimSpeed,
            blockBreakingLevel, equipment, onHitEffects
        );
    }

    private static List<ZombieVariationDefinition.OnHitEffect> readEffects(
        Element variation, Path path
    ) throws IOException {
        Element effects = optionalSingleChild(variation, "effects", path);
        if (effects == null) {
            return new ArrayList<>();
        }
        List<ZombieVariationDefinition.OnHitEffect> result = new ArrayList<>();
        for (Element child : childElements(effects, null)) {
            if (!"onHit".equals(child.getTagName())) {
                throw new IOException("Unknown variation effect <" + child.getTagName()
                    + "> in " + path + "; expected <onHit>");
            }
            String potionId = requireAttribute(child, "potion", path);
            requireNamespacedValue(potionId, "on-hit potion", path);
            rejectRetiredEffectAttribute(child, "durationTicks", "durationSeconds", path);
            rejectRetiredEffectAttribute(child, "ambient", null, path);
            rejectRetiredEffectAttribute(child, "showParticles", null, path);
            double duration = optionalDoubleAttribute(
                child, "durationSeconds", 5.0D, 0.05D, 50_000.0D, path
            );
            int amplifier = optionalIntAttribute(child, "amplifier", 0, 0, 255, path);
            double chance = optionalDoubleAttribute(
                child, "chancePercent", 100.0D, 0.0D, 100.0D, path
            );
            result.add(new ZombieVariationDefinition.OnHitEffect(
                potionId, duration, amplifier, chance
            ));
        }
        return result;
    }

    private static void rejectRetiredEffectAttribute(Element element, String name,
                                                     String replacement, Path path)
        throws IOException {
        if (!element.hasAttribute(name)) {
            return;
        }
        String message = "Attribute '" + name + "' is no longer supported on <onHit> in "
            + path;
        if (replacement != null) {
            message += "; use '" + replacement + "' instead";
        }
        throw new IOException(message);
    }

    private static ZombieVariationDefinition.EquipmentPool readEquipmentPool(
        Element slotElement, Path path
    ) throws IOException {
        double chance = optionalDoubleAttribute(
            slotElement, "chancePercent", 100.0D, 0.0D, 100.0D, path
        );
        List<ZombieVariationDefinition.EquipmentChoice> choices = new ArrayList<>();
        List<Element> itemElements = childElements(slotElement, "item");
        boolean shorthand = slotElement.hasAttribute("item")
            || slotElement.hasAttribute("id") || itemElements.isEmpty();
        if (shorthand && !itemElements.isEmpty()) {
            throw new IOException("Equipment slot " + slotElement.getTagName()
                + " cannot mix an inline item with nested <item> choices in " + path);
        }
        if (shorthand) {
            choices.add(readEquipmentChoice(slotElement, true, path));
        } else {
            for (Element itemElement : itemElements) {
                choices.add(readEquipmentChoice(itemElement, false, path));
            }
        }
        return new ZombieVariationDefinition.EquipmentPool(chance, choices);
    }

    private static ZombieVariationDefinition.EquipmentChoice readEquipmentChoice(
        Element element, boolean shorthand, Path path
    ) throws IOException {
        String itemId = optionalAttribute(element, "item");
        if (itemId == null) {
            itemId = optionalAttribute(element, "id");
        }
        if (itemId == null && shorthand) {
            itemId = element.getTextContent().trim();
        }
        if (itemId == null || itemId.isEmpty()) {
            throw new IOException("Missing item registry ID in <" + element.getTagName()
                + "> in " + path);
        }
        requireNamespacedValue(itemId, "item", path);
        int weight = optionalIntAttribute(element, "weight", 1, 1, 1_000_000, path);
        int count = optionalIntAttribute(element, "count", 1, 1, 64, path);
        int metadata = optionalIntAttribute(element, "metadata", 0, 0, 32767, path);
        double dropChance = optionalDoubleAttribute(
            element, "dropChancePercent", 0.0D, 0.0D, 100.0D, path
        );
        String nbt = optionalAttribute(element, "nbt");
        Element nbtElement = optionalSingleChild(element, "nbt", path);
        if (nbt != null && nbtElement != null) {
            throw new IOException("Equipment item cannot define NBT both as an attribute and "
                + "a child element in " + path);
        }
        if (nbtElement != null) {
            nbt = nbtElement.getTextContent().trim();
        }
        if (nbt != null && !nbt.isEmpty()) {
            try {
                JsonToNBT.getTagFromJson(nbt);
            } catch (NBTException exception) {
                throw new IOException("Invalid equipment NBT in " + path, exception);
            }
        } else {
            nbt = null;
        }
        return new ZombieVariationDefinition.EquipmentChoice(
            itemId, weight, count, metadata, dropChance, nbt
        );
    }

    private static void applyEquipment(EntityLiving living, EntityEquipmentSlot slot,
                                       ZombieVariationDefinition.EquipmentPool pool,
                                       Random random) {
        living.setItemStackToSlot(slot, ItemStack.EMPTY);
        if (pool.getChoices().isEmpty()
            || random.nextDouble() * 100.0D >= pool.getChancePercent()) {
            return;
        }
        long total = 0L;
        for (ZombieVariationDefinition.EquipmentChoice choice : pool.getChoices()) {
            total += choice.getWeight();
        }
        double roll = random.nextDouble() * total;
        ZombieVariationDefinition.EquipmentChoice selected = null;
        for (ZombieVariationDefinition.EquipmentChoice choice : pool.getChoices()) {
            roll -= choice.getWeight();
            if (roll < 0.0D) {
                selected = choice;
                break;
            }
        }
        if (selected == null) {
            selected = pool.getChoices().get(0);
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(selected.getItemId()));
        if (item == null || item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(
            item, selected.getCount(), selected.getMetadata()
        );
        if (selected.getNbtJson() != null) {
            try {
                stack.setTagCompound(JsonToNBT.getTagFromJson(selected.getNbtJson()));
            } catch (NBTException exception) {
                Zomboid.logger.warn("Could not apply NBT for variation item {}.",
                    selected.getItemId(), exception);
            }
        }
        living.setItemStackToSlot(slot, stack);
        living.setDropChance(slot, (float) (selected.getDropChancePercent() / 100.0D));
    }

    private static void setAttribute(EntityLiving living, IAttribute attribute, Double value) {
        if (value == null) {
            return;
        }
        IAttributeInstance instance = living.getEntityAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static ZombieVariationDefinition copy(
        ZombieVariationDefinition source,
        Set<String> entities,
        Map<EntityEquipmentSlot, ZombieVariationDefinition.EquipmentPool> equipment,
        List<ZombieVariationDefinition.OnHitEffect> effects
    ) {
        return new ZombieVariationDefinition(
            source.getId(), entities, source.getMaximumHealth(),
            source.getMovementSpeed(), source.getAttackDamage(),
            source.getKnockbackResistance(), source.getSwimSpeed(),
            source.getBlockBreakingLevel(), equipment, effects
        );
    }

    private static ZombieVariationCatalog emptyCatalog() {
        return new ZombieVariationCatalog(
            new ArrayList<ZombieVariationDefinition>()
        );
    }

    private static Document readDocument(Path path) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            try (InputStream input = Files.newInputStream(path)) {
                Document document = factory.newDocumentBuilder().parse(input);
                document.getDocumentElement().normalize();
                return document;
            }
        } catch (ParserConfigurationException | SAXException | RuntimeException exception) {
            throw new IOException("Invalid or unsafe XML in " + path + ": "
                + exception.getMessage(), exception);
        }
    }

    private static void copyDefaultIfMissing(Path destination, String resource)
        throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        Files.createDirectories(destination.getParent());
        try (InputStream input = ZombieVariationDefinitions.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Bundled resource is missing: " + resource);
            }
            Files.copy(input, destination);
        }
    }

    private static Path resolveInside(Path root, String relative) throws IOException {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Variation path leaves the config directory: " + relative);
        }
        return resolved;
    }

    private static void requireNamespacedValue(String value, String name, Path path)
        throws IOException {
        if (value.indexOf(':') <= 0) {
            throw new IOException(name + " must include a mod namespace in " + path
                + ": " + value);
        }
        try {
            new ResourceLocation(value);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid registry ID " + value + " in " + path, exception);
        }
    }

    private static String variationId(String configured, Path path) throws IOException {
        String id = configured.indexOf(':') < 0 ? "zomboid:" + configured : configured;
        requireNamespacedValue(id, "variation id", path);
        return id;
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (name == null || name.equals(element.getTagName())) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    private static void rejectUnknownChildren(Element parent, Path path,
                                              String... allowedNames)
        throws IOException {
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedNames));
        for (Element child : childElements(parent, null)) {
            if (!allowed.contains(child.getTagName())) {
                throw new IOException("Unknown <" + child.getTagName() + "> inside <"
                    + parent.getTagName() + "> in " + path);
            }
        }
    }

    private static Element optionalSingleChild(Element parent, String name, Path path)
        throws IOException {
        List<Element> children = childElements(parent, name);
        if (children.size() > 1) {
            throw new IOException("Only one <" + name + "> is allowed inside <"
                + parent.getTagName() + "> in " + path);
        }
        return children.isEmpty() ? null : children.get(0);
    }

    private static String requireAttribute(Element element, String name, Path path)
        throws IOException {
        String value = optionalAttribute(element, name);
        if (value == null) {
            throw new IOException("Missing attribute '" + name + "' on <"
                + element.getTagName() + "> in " + path);
        }
        return value;
    }

    private static String optionalAttribute(Element element, String name) {
        if (!element.hasAttribute(name)) {
            return null;
        }
        String value = element.getAttribute(name).trim();
        return value.isEmpty() ? null : value;
    }

    private static int requireIntAttribute(Element element, String name, int min, int max,
                                           Path path) throws IOException {
        return readIntAttribute(element, name, requireAttribute(element, name, path),
            min, max, path);
    }

    private static int optionalIntAttribute(Element element, String name, int fallback,
                                            int min, int max, Path path) throws IOException {
        String configured = optionalAttribute(element, name);
        return configured == null
            ? fallback
            : readIntAttribute(element, name, configured, min, max, path);
    }

    private static int readIntAttribute(Element element, String name, String configured,
                                        int min, int max, Path path) throws IOException {
        try {
            int value = Integer.parseInt(configured);
            if (value < min || value > max) {
                throw new IOException(name + " must be between " + min + " and " + max
                    + " in " + path);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid integer attribute '" + name + "' on <"
                + element.getTagName() + "> in " + path, exception);
        }
    }

    private static Double optionalAliasedDoubleAttribute(
        Element element, String shortName, String longName, double min, double max, Path path
    ) throws IOException {
        String shortValue = optionalAttribute(element, shortName);
        String longValue = optionalAttribute(element, longName);
        if (shortValue != null && longValue != null) {
            throw new IOException("Use either '" + shortName + "' or '" + longName
                + "', not both, on <" + element.getTagName() + "> in " + path);
        }
        if (shortValue != null) {
            return readDoubleAttribute(element, shortName, shortValue, min, max, path);
        }
        return longValue == null
            ? null
            : readDoubleAttribute(element, longName, longValue, min, max, path);
    }

    private static double optionalDoubleAttribute(Element element, String name,
                                                  double fallback, double min, double max,
                                                  Path path) throws IOException {
        String configured = optionalAttribute(element, name);
        return configured == null
            ? fallback
            : readDoubleAttribute(element, name, configured, min, max, path);
    }

    private static double readDoubleAttribute(Element element, String name, String configured,
                                              double min, double max, Path path)
        throws IOException {
        try {
            double value = Double.parseDouble(configured);
            if (Double.isNaN(value) || Double.isInfinite(value)
                || value < min || value > max) {
                throw new IOException(name + " must be between " + min + " and " + max
                    + " in " + path);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid number attribute '" + name + "' on <"
                + element.getTagName() + "> in " + path, exception);
        }
    }

    private static EntityEquipmentSlot slot(String name, Path path) throws IOException {
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "hand":
            case "mainhand": return EntityEquipmentSlot.MAINHAND;
            case "offhand": return EntityEquipmentSlot.OFFHAND;
            case "boots":
            case "feet": return EntityEquipmentSlot.FEET;
            case "leggings":
            case "legs": return EntityEquipmentSlot.LEGS;
            case "chest": return EntityEquipmentSlot.CHEST;
            case "head": return EntityEquipmentSlot.HEAD;
            default: throw new IOException("Unknown equipment slot " + name + " in " + path);
        }
    }

    private static void warn(String message, Object... arguments) {
        if (logger != null) {
            logger.warn(message, arguments);
        }
    }
}

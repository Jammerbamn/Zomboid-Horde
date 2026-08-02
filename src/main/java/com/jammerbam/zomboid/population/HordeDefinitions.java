package com.jammerbam.zomboid.population;

import com.jammerbam.zomboid.variation.ZombieVariationCatalog;
import com.jammerbam.zomboid.variation.ZombieVariationDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public final class HordeDefinitions {
    private static final String DEFAULT_HORDE_RESOURCE =
        "/assets/zomboid/default-config/hordes/standard.xml";

    private static HordeCatalog active = builtInDefault();
    private static Logger logger;

    private HordeDefinitions() {
    }

    public static boolean load(File configDirectory, double frequencyPercentPerChunk,
                               String[] definitionFiles, Logger log) {
        logger = log;
        try {
            Path configRoot = configDirectory.toPath().toAbsolutePath().normalize();
            Path hordePath =
                resolveInside(configRoot, "zomboid/hordes/standard.xml");
            copyDefaultIfMissing(hordePath, DEFAULT_HORDE_RESOURCE);
            active = readDefinitions(
                configRoot,
                frequencyPercentPerChunk,
                definitionFiles
            );
            if (logger != null) {
                logger.info(
                    "Loaded {} horde definitions from the Forge config with {}% frequency per chunk.",
                    active.getDefinitions().size(),
                    active.getFrequencyPercentPerChunk()
                );
            }
            return true;
        } catch (Exception exception) {
            active = builtInDefault();
            if (logger != null) {
                logger.error(
                    "Could not load horde definitions. Using the bundled standard horde.",
                    exception
                );
            }
            return false;
        }
    }

    public static void validateRegistries(ZombieVariationCatalog variationCatalog) {
        List<HordeDefinition> validated = new ArrayList<>();
        for (HordeDefinition definition : active.getDefinitions()) {
            List<HordeMember> members = new ArrayList<>();
            for (HordeMember member : definition.getMembers()) {
                ResourceLocation id = new ResourceLocation(member.getEntityId());
                Class<? extends Entity> entityClass = EntityList.getClass(id);
                if (entityClass == null || !EntityLiving.class.isAssignableFrom(entityClass)) {
                    if (logger != null) {
                        logger.warn(
                            "Ignoring horde member {} in {} because it is not a registered living entity.",
                            member.getEntityId(),
                            definition.getId()
                        );
                    }
                    continue;
                }
                if (member.getVariations().isEmpty()) {
                    members.add(member);
                    continue;
                }
                List<HordeVariation> variations = new ArrayList<>();
                for (HordeVariation reference : member.getVariations()) {
                    if (reference.isStandard()) {
                        variations.add(reference);
                        continue;
                    }
                    ZombieVariationDefinition variation =
                        variationCatalog.get(reference.getVariationId(), member.getEntityId());
                    if (variation == null) {
                        warn("Ignoring unavailable variation {} for horde member {} in {}.",
                            reference.getVariationId(), member.getEntityId(), definition.getId());
                    } else {
                        variations.add(reference);
                    }
                }
                if (variations.isEmpty()) {
                    warn("Ignoring horde member {} in {} because none of its configured "
                            + "variations are available for that entity.",
                        member.getEntityId(), definition.getId());
                    continue;
                }
                members.add(new HordeMember(
                    member.getEntityId(), member.getWeight(), variations
                ));
            }
            if (members.isEmpty()) {
                if (logger != null) {
                    logger.warn(
                        "Disabling horde definition {} because it has no available living entities.",
                        definition.getId()
                    );
                }
                continue;
            }
            validated.add(new HordeDefinition(
                definition.getId(),
                definition.getMinimumSize(),
                definition.getMaximumSize(),
                definition.getRadius(),
                members,
                definition.getBiomeWeights()
            ));
        }

        boolean hasUniversalFallback = false;
        for (HordeDefinition definition : validated) {
            hasUniversalFallback |= definition.isUniversalBiomeFallback();
        }
        if (validated.isEmpty() || !hasUniversalFallback) {
            if (logger != null) {
                logger.error(
                    "No usable positive ALL-only horde fallback is available. "
                        + "Using the bundled standard horde."
                );
            }
            active = builtInDefault();
            return;
        }
        active = new HordeCatalog(
            active.getFrequencyPercentPerChunk(),
            validated
        );
    }

    public static HordeCatalog get() {
        return active;
    }

    private static HordeCatalog readDefinitions(Path configRoot, double frequency,
                                                String[] definitionFiles)
        throws IOException {
        if (Double.isNaN(frequency) || Double.isInfinite(frequency)
            || frequency < 0.0D || frequency > 100.0D) {
            throw new IOException("Horde frequency must be between 0 and 100");
        }
        if (definitionFiles == null || definitionFiles.length == 0) {
            throw new IOException("The Forge configuration contains no horde definition files");
        }

        List<HordeDefinition> definitions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String definitionFile : definitionFiles) {
            if (definitionFile == null || definitionFile.trim().isEmpty()) {
                continue;
            }
            Path definitionPath = resolveInside(configRoot, definitionFile.trim());
            HordeDefinition definition = readDefinition(definitionPath);
            if (!ids.add(definition.getId())) {
                throw new IOException("Duplicate horde definition id " + definition.getId());
            }
            definitions.add(definition);
        }

        if (definitions.isEmpty()) {
            throw new IOException("The Forge configuration contains no horde definitions");
        }
        return new HordeCatalog(frequency, definitions);
    }

    private static HordeDefinition readDefinition(Path path) throws IOException {
        Element root = readDocument(path).getDocumentElement();
        if (!"horde".equals(root.getTagName())) {
            throw new IOException("Expected <horde> as the root element in " + path);
        }
        int formatVersion = requireIntAttribute(root, "formatVersion", 2, 2, path);
        if (formatVersion != 2) {
            throw new IOException("Unsupported formatVersion " + formatVersion + " in " + path);
        }
        String id = requireAttribute(root, "id", path);
        if (id.indexOf(':') <= 0) {
            throw new IOException("Horde id must include a registry namespace in " + path);
        }
        requireResourceLocation(id, "horde id", path);
        if (root.hasAttribute("selectionPercent")) {
            throw new IOException("selectionPercent is no longer supported in " + path
                + "; define relative weights under <biomeWeights>");
        }

        Element population = requireSingleChild(root, "population", path);
        int minimum = requireIntAttribute(population, "minimum", 1, 1000, path);
        int maximum = requireIntAttribute(population, "maximum", minimum, 1000, path);
        int radius = requireIntAttribute(population, "radius", 1, 256, path);

        List<HordeMember> members = new ArrayList<>();
        Element membersElement = requireSingleChild(root, "members", path);
        for (Element member : childElements(membersElement, "member")) {
            String entityId = requireAttribute(member, "entity", path);
            if (entityId.indexOf(':') <= 0) {
                throw new IOException(
                    "Entity id must include its mod namespace in " + path + ": " + entityId
                );
            }
            requireResourceLocation(entityId, "entity id", path);
            int weight = requireIntAttribute(member, "weight", 1, 1000000, path);
            List<HordeVariation> variations = new ArrayList<>();
            Element variationsElement = optionalSingleChild(member, "variations", path);
            if (variationsElement != null) {
                Set<String> variationIds = new HashSet<>();
                for (Element variation : childElements(variationsElement, "variation")) {
                    String variationId = requireAttribute(variation, "id", path);
                    if (variationId.indexOf(':') <= 0) {
                        throw new IOException("Variation id must include its mod namespace in "
                            + path + ": " + variationId);
                    }
                    requireResourceLocation(variationId, "variation id", path);
                    if (!variationIds.add(variationId)) {
                        throw new IOException("Duplicate variation reference " + variationId
                            + " for horde member " + entityId + " in " + path);
                    }
                    int variationWeight = requireIntAttribute(
                        variation, "weight", 1, 1000000, path
                    );
                    variations.add(new HordeVariation(variationId, variationWeight));
                }
                if (variations.isEmpty()) {
                    throw new IOException("Horde member variation list may not be empty in "
                        + path);
                }
            }
            members.add(new HordeMember(entityId, weight, variations));
        }
        if (members.isEmpty()) {
            throw new IOException("Horde definition has no members: " + path);
        }

        if (optionalSingleChild(root, "biomeModifiers", path) != null) {
            throw new IOException("<biomeModifiers> is no longer supported in " + path
                + "; use <biomeWeights>");
        }
        List<BiomeWeight> biomeWeights = new ArrayList<>();
        Set<String> biomeSelectors = new HashSet<>();
        Element weightsElement = requireSingleChild(root, "biomeWeights", path);
        for (Element configured : childElements(weightsElement, "biome")) {
            String selector = requireAttribute(configured, "selector", path);
            if (!BiomeWeight.ALL.equalsIgnoreCase(selector) && selector.indexOf(':') >= 0) {
                requireResourceLocation(selector, "biome selector", path);
            }
            double weight = requireDoubleAttribute(
                configured, "weight", 0.0D, 1000000.0D, path
            );
            BiomeWeight biomeWeight = new BiomeWeight(selector, weight);
            if (!biomeSelectors.add(biomeWeight.getSelector())) {
                throw new IOException("Duplicate biome selector " + selector + " in " + path);
            }
            biomeWeights.add(biomeWeight);
        }
        if (biomeWeights.isEmpty()) {
            throw new IOException("<biomeWeights> must contain at least one <biome> in " + path);
        }

        return new HordeDefinition(
            id, minimum, maximum, radius, members, biomeWeights
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

    private static void copyDefaultIfMissing(Path destination, String resource) throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        Files.createDirectories(destination.getParent());
        try (InputStream input = HordeDefinitions.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Bundled resource is missing: " + resource);
            }
            Files.copy(input, destination);
        }
    }

    private static Path resolveInside(Path configRoot, String relativePath) throws IOException {
        Path resolved = configRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(configRoot)) {
            throw new IOException("Horde configuration path leaves the config directory: " + relativePath);
        }
        return resolved;
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

    private static Element requireSingleChild(Element parent, String name, Path path)
        throws IOException {
        Element child = optionalSingleChild(parent, name, path);
        if (child == null) {
            throw new IOException("Missing <" + name + "> inside <" + parent.getTagName()
                + "> in " + path);
        }
        return child;
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
        if (!element.hasAttribute(name)) {
            throw new IOException("Missing attribute '" + name + "' on <"
                + element.getTagName() + "> in " + path);
        }
        String value = element.getAttribute(name).trim();
        if (value.isEmpty()) {
            throw new IOException("Empty attribute '" + name + "' on <"
                + element.getTagName() + "> in " + path);
        }
        return value;
    }

    private static int requireIntAttribute(Element element, String name, int minimum,
                                           int maximum, Path path) throws IOException {
        String configured = requireAttribute(element, name, path);
        try {
            int value = Integer.parseInt(configured);
            if (value < minimum || value > maximum) {
                throw new IOException(name + " must be between " + minimum + " and "
                    + maximum + " in " + path);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid integer attribute '" + name + "' on <"
                + element.getTagName() + "> in " + path, exception);
        }
    }

    private static double requireDoubleAttribute(Element element, String name,
                                                 double minimum, double maximum, Path path)
        throws IOException {
        String configured = requireAttribute(element, name, path);
        try {
            double value = Double.parseDouble(configured);
            if (Double.isNaN(value) || Double.isInfinite(value)
                || value < minimum || value > maximum) {
                throw new IOException(name + " must be between " + minimum + " and "
                    + maximum + " in " + path);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid number attribute '" + name + "' on <"
                + element.getTagName() + "> in " + path, exception);
        }
    }

    private static void requireResourceLocation(String value, String name, Path path)
        throws IOException {
        try {
            new ResourceLocation(value);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid " + name + " " + value + " in " + path,
                exception);
        }
    }

    private static HordeCatalog builtInDefault() {
        List<HordeVariation> zombieVariations = new ArrayList<>();
        zombieVariations.add(new HordeVariation(HordeVariation.STANDARD_ID, 68));
        zombieVariations.add(new HordeVariation("zomboid:sprinter", 15));
        zombieVariations.add(new HordeVariation("zomboid:shambler", 10));
        zombieVariations.add(new HordeVariation("zomboid:tunneler", 5));
        zombieVariations.add(new HordeVariation("zomboid:experienced", 2));
        List<HordeVariation> buffVariations = new ArrayList<>();
        buffVariations.add(new HordeVariation(HordeVariation.STANDARD_ID, 85));
        buffVariations.add(new HordeVariation("zomboid:tank", 5));
        buffVariations.add(new HordeVariation("zomboid:jogger", 10));
        List<HordeMember> members = new ArrayList<>();
        members.add(new HordeMember("minecraft:zombie", 70, zombieVariations));
        members.add(new HordeMember("zomboid:buff_zombie", 10, buffVariations));
        List<HordeDefinition> definitions = new ArrayList<>();
        definitions.add(new HordeDefinition(
            "zomboid:standard",
            6,
            12,
            8,
            members,
            java.util.Collections.singletonList(new BiomeWeight(BiomeWeight.ALL, 1.0D))
        ));
        return new HordeCatalog(7.0D, definitions);
    }

    private static void warn(String message, Object... arguments) {
        if (logger != null) {
            logger.warn(message, arguments);
        }
    }
}

package com.jammerbam.zomboid.variation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZombieVariationCatalog {
    private final List<ZombieVariationDefinition> definitions;
    private final Map<String, List<ZombieVariationDefinition>> definitionsById;

    public ZombieVariationCatalog(List<ZombieVariationDefinition> definitions) {
        this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
        Map<String, List<ZombieVariationDefinition>> indexed = new LinkedHashMap<>();
        for (ZombieVariationDefinition definition : definitions) {
            List<ZombieVariationDefinition> matches = indexed.get(definition.getId());
            if (matches == null) {
                matches = new ArrayList<>();
                indexed.put(definition.getId(), matches);
            }
            matches.add(definition);
        }
        Map<String, List<ZombieVariationDefinition>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<ZombieVariationDefinition>> entry : indexed.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        this.definitionsById = Collections.unmodifiableMap(immutable);
    }

    public List<ZombieVariationDefinition> getDefinitions() {
        return definitions;
    }

    public ZombieVariationDefinition get(String id) {
        List<ZombieVariationDefinition> matches = id == null ? null : definitionsById.get(id);
        return matches == null || matches.isEmpty() ? null : matches.get(0);
    }

    public ZombieVariationDefinition get(String id, String entityId) {
        List<ZombieVariationDefinition> matches = id == null ? null : definitionsById.get(id);
        if (matches == null || entityId == null) {
            return null;
        }
        for (ZombieVariationDefinition definition : matches) {
            if (definition.appliesTo(entityId)) {
                return definition;
            }
        }
        return null;
    }
}

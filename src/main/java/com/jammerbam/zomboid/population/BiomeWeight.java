package com.jammerbam.zomboid.population;

import java.util.Locale;

/** One relative horde-selection weight associated with a biome selector. */
public final class BiomeWeight {
    public static final String ALL = "ALL";

    private final String selector;
    private final double weight;

    public BiomeWeight(String selector, double weight) {
        String trimmed = selector == null ? "" : selector.trim();
        if (ALL.equalsIgnoreCase(trimmed)) {
            this.selector = ALL;
        } else if (trimmed.indexOf(':') >= 0) {
            this.selector = trimmed.toLowerCase(Locale.ROOT);
        } else {
            this.selector = trimmed.toUpperCase(Locale.ROOT);
        }
        this.weight = weight;
    }

    public String getSelector() {
        return selector;
    }

    public double getWeight() {
        return weight;
    }

    public boolean isAll() {
        return ALL.equals(selector);
    }

    public boolean isSpecificBiome() {
        return selector.indexOf(':') >= 0;
    }

    public boolean matches(BiomeDescriptor biome) {
        return !isAll() && biome != null && biome.matches(selector);
    }
}

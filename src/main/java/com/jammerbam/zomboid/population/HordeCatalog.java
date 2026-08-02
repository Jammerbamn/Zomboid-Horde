package com.jammerbam.zomboid.population;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class HordeCatalog {
    public static final int PLANNING_REGION_SIZE_CHUNKS = 32;

    private final double frequencyPercentPerChunk;
    private final List<HordeDefinition> definitions;
    private final int maximumRadius;

    public HordeCatalog(double frequencyPercentPerChunk,
                        List<HordeDefinition> definitions) {
        this.frequencyPercentPerChunk = frequencyPercentPerChunk;
        List<HordeDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparing(HordeDefinition::getId));
        boolean hasUniversalFallback = false;
        for (HordeDefinition definition : sorted) {
            hasUniversalFallback |= definition.isUniversalBiomeFallback();
        }
        if (sorted.isEmpty() || !hasUniversalFallback) {
            throw new IllegalArgumentException(
                "A horde catalog requires a positive ALL-only biome fallback definition"
            );
        }
        this.definitions = Collections.unmodifiableList(sorted);
        int largestRadius = 1;
        for (HordeDefinition definition : sorted) {
            largestRadius = Math.max(largestRadius, definition.getRadius());
        }
        this.maximumRadius = largestRadius;
    }

    public double getFrequencyPercentPerChunk() {
        return frequencyPercentPerChunk;
    }

    public List<HordeDefinition> getDefinitions() {
        return definitions;
    }

    public int getMaximumRadius() {
        return maximumRadius;
    }

    public HordeDefinition select(Random random, BiomeDescriptor biome) {
        double total = 0.0D;
        for (HordeDefinition definition : definitions) {
            total += definition.effectiveBiomeWeight(biome);
        }
        if (total <= 0.0D) {
            throw new IllegalStateException("No horde definition is eligible for biome selection");
        }

        double roll = random.nextDouble() * total;
        HordeDefinition lastEligible = null;
        for (HordeDefinition definition : definitions) {
            double weight = definition.effectiveBiomeWeight(biome);
            if (weight <= 0.0D) {
                continue;
            }
            lastEligible = definition;
            roll -= weight;
            if (roll < 0.0D) {
                return definition;
            }
        }
        return lastEligible;
    }
}

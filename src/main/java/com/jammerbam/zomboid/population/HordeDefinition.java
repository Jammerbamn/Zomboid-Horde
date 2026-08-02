package com.jammerbam.zomboid.population;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HordeDefinition {
    private final String id;
    private final int minimumSize;
    private final int maximumSize;
    private final int radius;
    private final List<HordeMember> members;
    private final List<BiomeWeight> biomeWeights;

    public HordeDefinition(String id, int minimumSize, int maximumSize, int radius,
                           List<HordeMember> members, List<BiomeWeight> biomeWeights) {
        this.id = id;
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
        this.radius = radius;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
        this.biomeWeights = Collections.unmodifiableList(new ArrayList<>(biomeWeights));
    }

    public String getId() {
        return id;
    }

    public int getMinimumSize() {
        return minimumSize;
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    public int getRadius() {
        return radius;
    }

    public List<HordeMember> getMembers() {
        return members;
    }

    public List<BiomeWeight> getBiomeWeights() {
        return biomeWeights;
    }

    public double effectiveBiomeWeight(BiomeDescriptor biome) {
        Double allWeight = null;
        Double matchingTypeWeight = null;
        for (BiomeWeight configured : biomeWeights) {
            if (configured.isAll()) {
                allWeight = configured.getWeight();
            } else if (configured.isSpecificBiome() && configured.matches(biome)) {
                return configured.getWeight();
            } else if (!configured.isSpecificBiome() && configured.matches(biome)) {
                matchingTypeWeight = matchingTypeWeight == null
                    ? configured.getWeight()
                    : Math.max(matchingTypeWeight, configured.getWeight());
            }
        }
        if (matchingTypeWeight != null) {
            return matchingTypeWeight;
        }
        return allWeight == null ? 0.0D : allWeight;
    }

    public boolean isUniversalBiomeFallback() {
        return biomeWeights.size() == 1
            && biomeWeights.get(0).isAll()
            && biomeWeights.get(0).getWeight() > 0.0D;
    }
}

package com.jammerbam.zomboid.population;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BiomeDescriptor {
    private final String registryName;
    private final Set<String> types;

    public BiomeDescriptor(String registryName, Set<String> types) {
        this.registryName = registryName == null ? "" : registryName.toLowerCase();
        Set<String> normalized = new HashSet<>();
        if (types != null) {
            for (String type : types) {
                if (type != null && !type.trim().isEmpty()) {
                    normalized.add(type.trim().toUpperCase());
                }
            }
        }
        this.types = Collections.unmodifiableSet(normalized);
    }

    public static BiomeDescriptor from(Biome biome) {
        if (biome == null) {
            return new BiomeDescriptor("", Collections.<String>emptySet());
        }

        ResourceLocation registryName = biome.getRegistryName();
        Set<String> types = new HashSet<>();
        for (BiomeDictionary.Type type : BiomeDictionary.getTypes(biome)) {
            types.add(type.getName());
        }
        return new BiomeDescriptor(registryName == null ? "" : registryName.toString(), types);
    }

    public boolean matches(String selector) {
        if (selector == null) {
            return false;
        }
        String normalized = selector.trim();
        if (normalized.indexOf(':') >= 0) {
            return registryName.equals(normalized.toLowerCase());
        }
        return types.contains(normalized.toUpperCase());
    }
}

package com.jammerbam.zomboid.population;

public interface BiomeResolver {
    BiomeDescriptor resolve(int blockX, int blockZ);
}

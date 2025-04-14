package com.ecoland.generator;

import com.ecoland.model.World;

/**
 * Interface for world generation algorithms.
 */
public interface WorldGenerator {
    void generate(World world);

    /**
     * Factory method to get a default generator instance.
     * This allows flexibility in choosing the generation algorithm later.
     */
    static WorldGenerator createDefaultGenerator() {
        // Switch to Perlin Noise generation
        return new PerlinNoiseGenerator();
        // return new SimpleLandWaterGenerator(); // Old generator
    }
} 
package com.ecoland.model;

import java.io.Serializable;

/**
 * Enum representing different biome types in the world.
 * Each biome has different characteristics affecting gameplay.
 */
public enum BiomeType implements Serializable {
    OCEAN(0.0, 0.0, 1.0),       // Deep ocean, mostly water
    LAKE(0.2, 0.2, 0.9),        // Shallower water body
    DESERT(0.3, 0.8, 0.1),      // Arid land with sparse vegetation
    FOREST(1.0, 0.7, 0.6),      // Dense vegetation
    MOUNTAINS(0.4, 0.5, 0.3),   // High elevation, rugged terrain
    PLAINS(0.7, 0.6, 0.4),      // Flat grasslands
    SWAMP(0.9, 0.4, 0.8);       // Wet, marshy areas
    
    private final double baseResourceDensity;
    private final double initialPlantFood;
    private final double waterRetention;
    
    /**
     * Constructor for BiomeType.
     * 
     * @param baseResourceDensity The base density of resources in this biome (0.0-1.0)
     * @param initialPlantFood The initial plant food value for this biome (0.0-1.0)
     * @param waterRetention How well this biome retains water for plant growth (0.0-1.0)
     */
    BiomeType(double baseResourceDensity, double initialPlantFood, double waterRetention) {
        this.baseResourceDensity = baseResourceDensity;
        this.initialPlantFood = initialPlantFood;
        this.waterRetention = waterRetention;
    }
    
    /**
     * Get the base resource density for this biome.
     * 
     * @return The base resource density (0.0-1.0)
     */
    public double getBaseResourceDensity() {
        return baseResourceDensity;
    }
    
    /**
     * Get the initial plant food value for this biome, scaled by fertility.
     * 
     * @param fertility The fertility of the tile (0.0-1.0)
     * @return The initial plant food value
     */
    public double getInitialPlantFood(double fertility) {
        return initialPlantFood * fertility;
    }
    
    /**
     * Get the water retention factor for this biome.
     * 
     * @return The water retention factor (0.0-1.0)
     */
    public double getWaterRetention() {
        return waterRetention;
    }
} 
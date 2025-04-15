package com.ecoland.model;

import com.ecoland.entity.Entity;

// TODO: Add imports for any resource classes if needed later

public class Tile {
    private final TerrainType terrainType;
    private final BiomeType biomeType;
    private final double elevation;
    private final double waterLevel;
    private double fertility;
    private double plantFoodValue; // Represents available food for herbivores or plant density
    private double temperature; // Added temperature for biome effects (0.0 to 1.0 scale, 0 = cold, 1 = hot)
    private double moisture; // Added moisture for biome effects (0.0 to 1.0 scale, 0 = dry, 1 = wet)
    // TODO: Add other resource fields (e.g., water presence for drinking)

    /**
     * Creates a new tile with specified properties
     * @param terrainType The visual/physical terrain type
     * @param biomeType The ecological biome type
     * @param elevation Height value (0.0 to 1.0)
     * @param waterLevel Water depth/presence (0.0 to 1.0)
     * @param fertility Base fertility of the soil (0.0 to 1.0)
     * @param temperature Temperature value (0.0 to 1.0)
     * @param moisture Moisture/humidity value (0.0 to 1.0)
     */
    public Tile(TerrainType terrainType, BiomeType biomeType, double elevation, double waterLevel, 
                double fertility, double temperature, double moisture) {
        this.terrainType = terrainType;
        this.biomeType = biomeType;
        this.elevation = elevation;
        this.waterLevel = waterLevel;
        this.fertility = fertility;
        this.temperature = temperature;
        this.moisture = moisture;
        this.plantFoodValue = biomeType.getInitialPlantFood(fertility); // Initialize food based on biome
    }
    
    /**
     * Legacy constructor for backward compatibility, infers biome
     */
    public Tile(TerrainType terrainType, double elevation, double waterLevel, double fertility) {
        this.terrainType = terrainType;
        this.elevation = elevation;
        this.waterLevel = waterLevel;
        this.fertility = fertility;
        this.temperature = 0.5; // Default temperature
        this.moisture = terrainType == TerrainType.WATER ? 1.0 : 0.5; // Default moisture
        
        // Infer biome from terrain type
        switch (terrainType) {
            case WATER:
                this.biomeType = elevation < 0.2 ? BiomeType.OCEAN : BiomeType.LAKE;
                break;
            case FOREST:
                this.biomeType = BiomeType.FOREST;
                break;
            case DESERT:
                this.biomeType = BiomeType.DESERT;
                break;
            case HILL:
                this.biomeType = BiomeType.MOUNTAINS;
                break;
            case GRASS:
            default:
                this.biomeType = BiomeType.PLAINS;
                break;
        }
        
        this.plantFoodValue = 0; // No initial food for backward compatibility
    }

    // Getters
    public TerrainType getTerrainType() {
        return terrainType;
    }
    
    public BiomeType getBiomeType() {
        return biomeType;
    }

    public double getElevation() {
        return elevation;
    }

    public double getWaterLevel() {
        return waterLevel;
    }

    public double getFertility() {
        return fertility;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public double getMoisture() {
        return moisture;
    }

    public double getPlantFoodValue() {
        return plantFoodValue;
    }

    // Setters for mutable properties
    public void setFertility(double fertility) {
        this.fertility = Math.max(0, fertility); // Ensure non-negative
    }

    public void setPlantFoodValue(double plantFoodValue) {
        this.plantFoodValue = Math.max(0, plantFoodValue); // Ensure non-negative
    }

    /**
     * Sets plant food value without any cap enforced.
     * This is used by tools that explicitly want to set a food value
     * regardless of natural caps.
     */
    public void forceSetPlantFoodValue(double value) {
        this.plantFoodValue = Math.max(0, value);
    }

    /**
     * Checks if this tile is passable by the given entity
     * @param entity The entity attempting to pass through
     * @return true if the entity can pass through this tile
     */
    public boolean isPassable(Entity entity) {
        if (entity == null) return false;
        
        // Water is impassable for most land creatures
        if (terrainType == TerrainType.WATER) {
            // Plants can't grow in water, but animals could potentially swim
            switch (entity.getSpeciesType()) {
                case PLANT:
                    return false;
                // Future: could add AQUATIC species that can only move in water
                default:
                    return false; // For now, water is impassable to all
            }
        }
        
        return true; // All other terrain is passable
    }

    /**
     * Increases food value based on biome and fertility
     */
    public void growPlantFood(double amount) {
        // Apply biome-specific growth modifiers
        double adjustedGrowth = amount;
        
        // Adjust growth based on biome characteristics
        switch (biomeType) {
            case DESERT:
                adjustedGrowth *= 0.3; // Slow growth in deserts
                break;
            case FOREST:
                adjustedGrowth *= 1.2; // Enhanced growth in forests
                break;
            case MOUNTAINS:
                adjustedGrowth *= 0.7; // Reduced growth in mountains
                break;
            case SWAMP:
                adjustedGrowth *= 1.1; // Slightly enhanced growth in swamps
                break;
            default:
                // No adjustment for other biomes
                break;
        }
        
        // Moisture affects plant growth
        adjustedGrowth *= (0.5 + moisture * 0.5);
        
        // Add the growth
        this.plantFoodValue += adjustedGrowth;
        
        // Cap food value based on biome and fertility
        double maxFood = fertility * 5.0 * biomeType.getBaseResourceDensity();
        if (this.plantFoodValue > maxFood) {
            this.plantFoodValue = maxFood;
        }
    }

    /**
     * Decrease food value (e.g., being eaten)
     * Returns the actual amount consumed (might be less than requested)
     */
    public double consumePlantFood(double amount) {
        double consumed = Math.min(this.plantFoodValue, amount);
        this.plantFoodValue -= consumed;
        return consumed;
    }
    
    /**
     * Update the tile for environmental effects during a simulation tick.
     * This allows for dynamic changes like seasonal effects, erosion, etc.
     * @param worldTime The current world time
     */
    public void update(long worldTime) {
        // For now, just natural regrowth based on biome
        if (isPassable(null) && fertility > 0) { // Null check is just for terrain passability
            double baseGrowth = 0.01 * fertility * biomeType.getWaterRetention();
            growPlantFood(baseGrowth);
        }
        
        // Future: Could add seasonal effects, natural disasters, etc. based on worldTime
    }

    // TODO: Add methods related to resources or tile state changes
} 
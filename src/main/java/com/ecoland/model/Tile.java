package com.ecoland.model;

// TODO: Add imports for any resource classes if needed later

public class Tile {
    private final TerrainType terrainType;
    private final double elevation;
    private final double waterLevel;
    private double fertility;
    private double plantFoodValue; // Represents available food for herbivores or plant density
    // TODO: Add other resource fields (e.g., water presence for drinking)

    public Tile(TerrainType terrainType, double elevation, double waterLevel, double fertility) {
        this.terrainType = terrainType;
        this.elevation = elevation;
        this.waterLevel = waterLevel;
        this.fertility = fertility;
        this.plantFoodValue = 0; // Initially no food
    }

    // Getters
    public TerrainType getTerrainType() {
        return terrainType;
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

    // Increase food value (e.g., plant growth)
    public void growPlantFood(double amount) {
        this.plantFoodValue += amount;
        // TODO: Maybe cap based on fertility or tile capacity?
    }

    // Decrease food value (e.g., being eaten)
    // Returns the actual amount consumed (might be less than requested)
    public double consumePlantFood(double amount) {
        double consumed = Math.min(this.plantFoodValue, amount);
        this.plantFoodValue -= consumed;
        return consumed;
    }

    // TODO: Add methods related to resources or tile state changes
} 
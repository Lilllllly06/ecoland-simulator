package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

/**
 * Minimalist brain for Plants with basic environmental awareness.
 * Plants are immobile but can still make decisions about spreading and resource allocation.
 */
public class PlantBrain extends AnimalBrain {
    private static final long serialVersionUID = 1L;
    
    // Minimal neural network for plants
    private static final int PLANT_INPUT_SIZE = 6;
    private static final int PLANT_HIDDEN_SIZE = 4;
    private static final int PLANT_OUTPUT_SIZE = 3;
    
    // Output indexes
    private static final int OUTPUT_SPREAD = 0;    // Threshold for spreading
    private static final int OUTPUT_ENERGY_STORE = 1; // How much energy to store vs. use for growth
    private static final int OUTPUT_ROOT_GROWTH = 2; // Whether to grow deeper roots
    
    // Plant-specific parameters
    private double soilQualityAwareness = 1.0;
    private double waterSensitivity = 1.0;
    private double lightSensitivity = 1.0;
    private double spreadingChance = 0.02; // Base chance to spread
    
    // Environmental memory
    private int droughtCounter = 0;
    private int highGrowthCounter = 0;
    
    /**
     * Create a new plant brain.
     * Plants have a fixed vision range of 1 (just immediate surroundings).
     */
    public PlantBrain() {
        super(1); // Plants can only sense their immediate surroundings
        
        // Initialize plant-specific parameters with some randomization
        this.soilQualityAwareness = 0.9 + Math.random() * 0.2; // 0.9-1.1
        this.waterSensitivity = 0.9 + Math.random() * 0.2; // 0.9-1.1
        this.lightSensitivity = 0.9 + Math.random() * 0.2; // 0.9-1.1
        this.spreadingChance = 0.01 + Math.random() * 0.03; // 0.01-0.04
    }
    
    /**
     * Create a plant brain based on a parent brain.
     * 
     * @param parentBrain The parent brain to inherit from
     */
    public PlantBrain(AnimalBrain parentBrain) {
        super(1); // Plants have fixed vision
        
        if (parentBrain instanceof PlantBrain) {
            PlantBrain parent = (PlantBrain) parentBrain;
            
            // Inherit traits with slight mutations
            this.soilQualityAwareness = mutateValue(parent.soilQualityAwareness, 0.1, 0.5, 1.5);
            this.waterSensitivity = mutateValue(parent.waterSensitivity, 0.1, 0.5, 1.5);
            this.lightSensitivity = mutateValue(parent.lightSensitivity, 0.1, 0.5, 1.5);
            this.spreadingChance = mutateValue(parent.spreadingChance, 0.2, 0.005, 0.1);
        }
    }
    
    @Override
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        if (entity == null || world == null) {
            return new BrainDecision(0, 0, false, false, false);
        }
        
        // Plants can't move, so always set moveX and moveY to 0
        int moveX = 0;
        int moveY = 0;
        
        // Gather plant-specific environment data
        int x = entity.getX();
        int y = entity.getY();
        Tile currentTile = world.getTile(x, y);
        
        if (currentTile == null) {
            return new BrainDecision(0, 0, false, false, false);
        }
        
        // Calculate environmental factors
        double soilQuality = currentTile.getFertility();
        double moisture = calculateMoisture(currentTile);
        double sunlight = calculateSunlight(currentTile);
        
        // Update environmental memory
        if (moisture < 0.3) {
            droughtCounter++;
        } else {
            droughtCounter = Math.max(0, droughtCounter - 1);
        }
        
        if (soilQuality > 0.7 && moisture > 0.7) {
            highGrowthCounter++;
        } else {
            highGrowthCounter = Math.max(0, highGrowthCounter - 1);
        }
        
        // Calculate reproduction threshold as a percentage of max energy
        double reproductionThreshold = entity.getReproductionThreshold() / entity.getMaxEnergy();
        
        // Decide whether to spread based on energy, environment, and inherited chance
        boolean shouldSpread = false;
        if (entity.getEnergy() >= entity.getReproductionThreshold()) {
            // Base spreading on favorable conditions and genetic factors
            double spreadProbability = spreadingChance;
            
            // Increase spread probability in good conditions
            if (soilQuality > 0.5) spreadProbability *= (1.0 + soilQuality * 0.5);
            if (moisture > 0.5) spreadProbability *= (1.0 + moisture * 0.3);
            
            // Decrease in drought
            if (droughtCounter > 3) spreadProbability *= 0.5;
            
            // Increase if consistently good growing conditions
            if (highGrowthCounter > 5) spreadProbability *= 1.5;
            
            // Make the final decision
            shouldSpread = Math.random() < spreadProbability;
        }
        
        // Plants use the "eat" flag to absorb nutrients
        boolean absorbNutrients = true;
        
        // Return decision with plant-specific actions
        return new BrainDecision(moveX, moveY, absorbNutrients, shouldSpread, false);
    }
    
    /**
     * Calculate soil moisture based on terrain and tile properties.
     */
    private double calculateMoisture(Tile tile) {
        if (tile.getTerrainType() == TerrainType.WATER) {
            return 1.0;
        } else if (tile.getTerrainType() == TerrainType.FOREST) {
            return 0.7;
        } else if (tile.getTerrainType() == TerrainType.GRASS) {
            return 0.5;
        } else if (tile.getTerrainType() == TerrainType.DESERT) {
            return 0.1;
        } else {
            return 0.3;
        }
    }
    
    /**
     * Calculate available sunlight based on terrain.
     */
    private double calculateSunlight(Tile tile) {
        if (tile.getTerrainType() == TerrainType.FOREST) {
            return 0.6; // Less sunlight in forests
        } else if (tile.getTerrainType() == TerrainType.HILL) {
            return 0.9; // Good sunlight on hills
        } else {
            return 0.8; // Average sunlight on other terrains
        }
    }
    
    /**
     * Mutate a value within bounds.
     */
    private double mutateValue(double value, double mutationChance, double min, double max) {
        if (Math.random() < mutationChance) {
            value += (Math.random() * 0.2) - 0.1; // ±0.1
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    @Override
    public PlantBrain createChild() {
        return new PlantBrain(this);
    }
} 
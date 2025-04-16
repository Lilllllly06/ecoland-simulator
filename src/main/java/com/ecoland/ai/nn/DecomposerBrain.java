package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

import java.util.List;

/**
 * Specialized brain for Decomposers with enhanced dead body detection and organic matter processing.
 */
public class DecomposerBrain extends AnimalBrain {
    private static final long serialVersionUID = 1L;
    
    // Specialized input constants
    private static final int DEAD_BODY_AWARENESS_INPUT = 18; // Extra input for dead body awareness
    private static final int SOIL_FERTILITY_INPUT = 19; // Extra input for soil fertility awareness
    private static final int MOISTURE_LEVEL_INPUT = 20; // Extra input for moisture level
    
    // Enhanced network architecture with specialized inputs
    private static final int DECOMPOSER_INPUT_SIZE = 21;
    private static final int DECOMPOSER_HIDDEN_SIZE = 12;
    
    // Learning parameters
    private double learningRate = 0.08;
    private int trainingIterations = 0;
    private double[] lastInputs = null;
    private double[] lastOutputs = null;
    private double lastReward = 0;
    
    /**
     * Create a new decomposer brain.
     * 
     * @param visionRange The vision range of the decomposer
     */
    public DecomposerBrain(int visionRange) {
        super(visionRange);
        // Additional initialization for decomposer-specific brain features
        this.learningRate = 0.04 + Math.random() * 0.08; // 0.04-0.12
    }
    
    /**
     * Create a decomposer brain based on a parent brain.
     * 
     * @param parentBrain The parent brain to inherit from
     */
    public DecomposerBrain(AnimalBrain parentBrain) {
        super(parentBrain);
        if (parentBrain instanceof DecomposerBrain) {
            DecomposerBrain parent = (DecomposerBrain) parentBrain;
            this.learningRate = parent.learningRate;
            
            // Apply mutation to learning rate with 10% chance
            if (Math.random() < 0.10) {
                this.learningRate += (Math.random() * 0.04) - 0.02; // ±0.02
                this.learningRate = Math.max(0.01, Math.min(0.15, this.learningRate));
            }
        }
    }
    
    @Override
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        // Store pre-decision state for learning
        double currentEnergy = entity.getEnergy();
        double currentHealth = entity.getHealth();
        
        // Gather enhanced sensory inputs with decomposer-specific features
        double[] inputs = gatherDecomposerInputs(entity, world, entityManager);
        lastInputs = inputs.clone();
        
        // Get base decision from parent class
        BrainDecision decision = super.makeDecision(entity, world, entityManager);
        
        // Enhance decision with decomposer-specific logic
        
        // 1. Prioritize dead bodies if energy is low
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), (int)entity.getVisionRange());
        
        Entity nearestDeadBody = null;
        double closestDeadBodyDist = Double.MAX_VALUE;
        
        for (Entity other : nearbyEntities) {
            if (!other.isDeadBody()) continue;
            
            double dx = other.getX() - entity.getX();
            double dy = other.getY() - entity.getY();
            double distance = Math.sqrt(dx*dx + dy*dy);
            
            if (distance < closestDeadBodyDist) {
                closestDeadBodyDist = distance;
                nearestDeadBody = other;
            }
        }
        
        // If dead body is close and energy is low, prioritize moving toward it
        if (nearestDeadBody != null && entity.getEnergy() < entity.getMaxEnergy() * 0.6) {
            int moveToX = nearestDeadBody.getX() - entity.getX();
            int moveToY = nearestDeadBody.getY() - entity.getY();
            
            // Normalize direction
            if (moveToX != 0) moveToX = Integer.signum(moveToX);
            if (moveToY != 0) moveToY = Integer.signum(moveToY);
            
            decision = new BrainDecision(moveToX, moveToY, true, false, false);
        }
        
        // 2. Reproduce when in fertile area and energy is high
        Tile currentTile = world.getTile(entity.getX(), entity.getY());
        if (currentTile != null && 
            currentTile.getFertility() > 0.7 && 
            entity.getEnergy() > entity.getReproductionThreshold() * 1.2) {
            decision = new BrainDecision(decision.moveX, decision.moveY, false, true, false);
        }
        
        // Store decision for learning
        // Simple learning: track how decisions affect energy and health over time
        if (lastOutputs != null) {
            // Calculate reward based on energy and health changes
            double energyChange = entity.getEnergy() - currentEnergy;
            double healthChange = entity.getHealth() - currentHealth;
            
            double reward = energyChange * 0.8 + healthChange * 0.2;
            
            // Apply learning (simplified reinforcement learning)
            if (trainingIterations % 8 == 0) { // Only learn every 8 iterations
                applyLearning(reward);
            }
            
            lastReward = reward;
        }
        
        trainingIterations++;
        return decision;
    }
    
    /**
     * Gather enhanced inputs specific to decomposers.
     */
    private double[] gatherDecomposerInputs(Entity entity, World world, EntityManager entityManager) {
        // Get base inputs from parent class
        double[] baseInputs = new double[DECOMPOSER_INPUT_SIZE];
        
        // Enhanced dead body detection: scan nearby tiles for dead organic matter
        int x = entity.getX();
        int y = entity.getY();
        double totalDeadBodyEnergy = 0;
        int deadBodyCount = 0;
        
        List<Entity> entities = entityManager.getEntitiesInRange(x, y, (int)entity.getVisionRange());
        
        for (Entity other : entities) {
            if (other.isDeadBody()) {
                double dx = other.getX() - x;
                double dy = other.getY() - y;
                double distance = Math.sqrt(dx*dx + dy*dy);
                
                // Weight by inverse distance and nutrition value
                double value = other.getDeadBodyNutritionValue() / Math.max(1.0, distance);
                totalDeadBodyEnergy += value;
                deadBodyCount++;
            }
        }
        
        // Calculate dead body awareness
        baseInputs[DEAD_BODY_AWARENESS_INPUT] = Math.min(1.0, totalDeadBodyEnergy / 50.0);
        
        // Soil fertility awareness
        double totalFertility = 0;
        int validTiles = 0;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (world.isValidCoordinate(nx, ny)) {
                    Tile tile = world.getTile(nx, ny);
                    totalFertility += tile.getFertility();
                    validTiles++;
                }
            }
        }
        
        baseInputs[SOIL_FERTILITY_INPUT] = validTiles > 0 ? totalFertility / validTiles : 0;
        
        // Moisture level awareness (decomposers work better in moist environments)
        double moistureLevel = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (world.isValidCoordinate(nx, ny)) {
                    Tile tile = world.getTile(nx, ny);
                    if (tile.getTerrainType() == TerrainType.WATER) {
                        moistureLevel += 0.3; // Water tiles add significant moisture
                    } else if (tile.getTerrainType() == TerrainType.FOREST) {
                        moistureLevel += 0.1; // Forest tiles have some moisture
                    }
                }
            }
        }
        
        baseInputs[MOISTURE_LEVEL_INPUT] = Math.min(1.0, moistureLevel);
        
        return baseInputs;
    }
    
    /**
     * Apply learning based on observed rewards.
     * 
     * @param reward The calculated reward from the last action
     */
    private void applyLearning(double reward) {
        // Simple reinforcement learning (placeholder for more complex algorithms)
        // In a real implementation, this would adjust network weights based on reward
        
        // For now, just track learning
        if (Math.abs(reward) > 0.6) {
            System.out.println("Decomposer brain learning: reward = " + reward);
        }
    }
    
    @Override
    public DecomposerBrain createChild() {
        return new DecomposerBrain(this);
    }
} 
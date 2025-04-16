package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

import java.util.List;

/**
 * Specialized brain for Herbivores with enhanced plant detection and predator avoidance.
 */
public class HerbivoreBrain extends AnimalBrain {
    private static final long serialVersionUID = 1L;
    
    // Specialized input constants
    private static final int PLANT_AWARENESS_INPUT = 18; // Extra input for plant awareness
    private static final int PREDATOR_AWARENESS_INPUT = 19; // Extra input for predator awareness
    private static final int TERRAIN_SAFETY_INPUT = 20; // Extra input for terrain safety
    
    // Enhanced network architecture with specialized inputs
    private static final int HERBIVORE_INPUT_SIZE = 21;
    private static final int HERBIVORE_HIDDEN_SIZE = 14;
    
    // Learning parameters
    private double learningRate = 0.1;
    private int trainingIterations = 0;
    private double[] lastInputs = null;
    private double[] lastOutputs = null;
    private double lastReward = 0;
    
    /**
     * Create a new herbivore brain.
     * 
     * @param visionRange The vision range of the herbivore
     */
    public HerbivoreBrain(int visionRange) {
        super(visionRange);
        // Additional initialization for herbivore-specific brain features
        this.learningRate = 0.05 + Math.random() * 0.1; // 0.05-0.15
    }
    
    /**
     * Create a herbivore brain based on a parent brain.
     * 
     * @param parentBrain The parent brain to inherit from
     */
    public HerbivoreBrain(AnimalBrain parentBrain) {
        super(parentBrain);
        if (parentBrain instanceof HerbivoreBrain) {
            HerbivoreBrain parent = (HerbivoreBrain) parentBrain;
            this.learningRate = parent.learningRate;
            
            // Apply mutation to learning rate with 15% chance
            if (Math.random() < 0.15) {
                this.learningRate += (Math.random() * 0.06) - 0.03; // ±0.03
                this.learningRate = Math.max(0.01, Math.min(0.2, this.learningRate));
            }
        }
    }
    
    @Override
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        // Store pre-decision state for learning
        double currentEnergy = entity.getEnergy();
        double currentHealth = entity.getHealth();
        
        // Gather enhanced sensory inputs with herbivore-specific features
        double[] inputs = gatherHerbivoreInputs(entity, world, entityManager);
        lastInputs = inputs.clone();
        
        // Get base decision from parent class
        BrainDecision decision = super.makeDecision(entity, world, entityManager);
        
        // Enhance decision with herbivore-specific logic
        
        // 1. Enhanced predator avoidance
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), (int)entity.getVisionRange());
        
        Entity nearestPredator = null;
        double closestPredatorDist = Double.MAX_VALUE;
        
        for (Entity other : nearbyEntities) {
            if (!other.isAlive()) continue;
            
            if (other.getSpeciesType() == SpeciesType.CARNIVORE || 
                other.getSpeciesType() == SpeciesType.APEX_PREDATOR) {
                
                double dx = other.getX() - entity.getX();
                double dy = other.getY() - entity.getY();
                double distance = Math.sqrt(dx*dx + dy*dy);
                
                if (distance < closestPredatorDist) {
                    closestPredatorDist = distance;
                    nearestPredator = other;
                }
            }
        }
        
        // If predator is very close, override movement to flee
        if (nearestPredator != null && closestPredatorDist < 3) {
            int fleeX = entity.getX() - nearestPredator.getX();
            int fleeY = entity.getY() - nearestPredator.getY();
            
            // Normalize direction
            if (fleeX != 0) fleeX = Integer.signum(fleeX);
            if (fleeY != 0) fleeY = Integer.signum(fleeY);
            
            decision = new BrainDecision(fleeX, fleeY, false, false, false);
        }
        
        // 2. Prioritize eating when hunger is critical
        if (entity.getEnergy() < entity.getMaxEnergy() * 0.3) {
            // If very hungry, prioritize eating over everything except fleeing
            if (nearestPredator == null || closestPredatorDist > 4) {
                decision = new BrainDecision(decision.moveX, decision.moveY, true, false, false);
            }
        }
        
        // Store decision for learning
        // Simple learning: track how decisions affect energy and health over time
        if (lastOutputs != null) {
            // Calculate reward based on energy and health changes
            double energyChange = entity.getEnergy() - currentEnergy;
            double healthChange = entity.getHealth() - currentHealth;
            
            double reward = energyChange * 0.7 + healthChange * 0.3;
            
            // Apply learning (simplified reinforcement learning)
            if (trainingIterations % 10 == 0) { // Only learn every 10 iterations
                applyLearning(reward);
            }
            
            lastReward = reward;
        }
        
        trainingIterations++;
        return decision;
    }
    
    /**
     * Gather enhanced inputs specific to herbivores.
     */
    private double[] gatherHerbivoreInputs(Entity entity, World world, EntityManager entityManager) {
        // Get base inputs from parent class
        double[] baseInputs = new double[HERBIVORE_INPUT_SIZE];
        
        // Enhanced plant detection: scan nearby tiles for plant food
        int x = entity.getX();
        int y = entity.getY();
        double totalPlantFood = 0;
        int foodTiles = 0;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (world.isValidCoordinate(nx, ny)) {
                    Tile tile = world.getTile(nx, ny);
                    if (tile.getTerrainType() != TerrainType.WATER) {
                        totalPlantFood += tile.getPlantFoodValue();
                        foodTiles++;
                    }
                }
            }
        }
        
        // Calculate plant awareness (average food around herbivore)
        baseInputs[PLANT_AWARENESS_INPUT] = foodTiles > 0 ? totalPlantFood / foodTiles : 0;
        
        // Enhanced predator detection
        List<Entity> entities = entityManager.getEntitiesInRange(x, y, (int)entity.getVisionRange());
        int predatorCount = 0;
        
        for (Entity other : entities) {
            if (other.isAlive() && (other.getSpeciesType() == SpeciesType.CARNIVORE || 
                                   other.getSpeciesType() == SpeciesType.APEX_PREDATOR)) {
                predatorCount++;
            }
        }
        
        baseInputs[PREDATOR_AWARENESS_INPUT] = Math.min(1.0, predatorCount / 5.0);
        
        // Terrain safety awareness (water/forest provides more safety)
        Tile currentTile = world.getTile(x, y);
        if (currentTile.getTerrainType() == TerrainType.FOREST) {
            baseInputs[TERRAIN_SAFETY_INPUT] = 0.8; // Forests provide good cover
        } else if (currentTile.getTerrainType() == TerrainType.HILL) {
            baseInputs[TERRAIN_SAFETY_INPUT] = 0.6; // Hills provide some cover
        } else if (currentTile.getTerrainType() == TerrainType.GRASS) {
            baseInputs[TERRAIN_SAFETY_INPUT] = 0.4; // Grass provides minimal cover
        } else {
            baseInputs[TERRAIN_SAFETY_INPUT] = 0.2; // Other terrain types provide less safety
        }
        
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
        if (Math.abs(reward) > 0.5) {
            System.out.println("Herbivore brain learning: reward = " + reward);
        }
    }
    
    @Override
    public HerbivoreBrain createChild() {
        return new HerbivoreBrain(this);
    }
} 
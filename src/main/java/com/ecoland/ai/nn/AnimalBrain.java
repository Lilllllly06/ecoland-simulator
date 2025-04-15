package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.Tile;
import com.ecoland.model.TerrainType;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

import java.io.Serializable;
import java.util.List;

/**
 * Represents an animal's brain using a neural network for decision making.
 * This class handles converting world observations into neural inputs 
 * and neural outputs into actions.
 */
public class AnimalBrain implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // The neural network
    private final NeuralNetwork network;
    
    // Network architecture constants
    private static final int INPUT_SIZE = 12; // Sensory inputs
    private static final int HIDDEN_SIZE = 8; // Hidden layer size
    private static final int OUTPUT_SIZE = 5; // Action outputs
    
    // Output neuron indexes
    private static final int OUTPUT_MOVE_X = 0; // -1 to 1 (normalized to -1, 0, 1)
    private static final int OUTPUT_MOVE_Y = 1; // -1 to 1 (normalized to -1, 0, 1)
    private static final int OUTPUT_EAT = 2;    // Threshold for eating
    private static final int OUTPUT_REPRODUCE = 3; // Threshold for reproduction
    private static final int OUTPUT_AGGRESSION = 4; // Threshold for attacking
    
    // Vision range (how far the animal can "see")
    private final int visionRange;
    
    /**
     * Create a new animal brain with a fresh neural network.
     * 
     * @param visionRange How far the animal can see
     */
    public AnimalBrain(int visionRange) {
        this.network = new NeuralNetwork(INPUT_SIZE, HIDDEN_SIZE, OUTPUT_SIZE);
        this.visionRange = visionRange;
    }
    
    /**
     * Create a brain by copying another brain's network.
     * 
     * @param other The brain to copy
     */
    public AnimalBrain(AnimalBrain other) {
        this.network = new NeuralNetwork(other.network);
        this.visionRange = other.visionRange;
    }
    
    /**
     * Create a brain with a specific neural network.
     * 
     * @param network The neural network to use
     * @param visionRange How far the animal can see
     */
    public AnimalBrain(NeuralNetwork network, int visionRange) {
        this.network = network;
        this.visionRange = visionRange;
    }
    
    /**
     * Create a child brain by crossover of two parent brains.
     * 
     * @param parent1 First parent brain
     * @param parent2 Second parent brain
     * @return A new brain with traits from both parents
     */
    public static AnimalBrain crossover(AnimalBrain parent1, AnimalBrain parent2) {
        // Create child network by crossover
        NeuralNetwork childNetwork = NeuralNetwork.crossover(parent1.network, parent2.network);
        
        // Average vision range (with possible mutation)
        int childVisionRange = (parent1.visionRange + parent2.visionRange) / 2;
        if (Math.random() < 0.1) { // 10% mutation chance
            childVisionRange += (Math.random() > 0.5) ? 1 : -1; // Increase or decrease by 1
            if (childVisionRange < 1) childVisionRange = 1; // Ensure positive
        }
        
        return new AnimalBrain(childNetwork, childVisionRange);
    }
    
    /**
     * Make a decision based on the entity's surroundings.
     * 
     * @param entity The entity making the decision
     * @param world The world state
     * @param entityManager The entity manager
     * @return An array containing the decision [moveX, moveY, eat, reproduce, attack]
     */
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        // Prepare sensory inputs
        double[] inputs = gatherSensoryInputs(entity, world, entityManager);
        
        // Process through neural network
        double[] outputs = network.feedForward(inputs);
        
        // Convert outputs to decisions
        int moveX = mapOutputToDirection(outputs[OUTPUT_MOVE_X]);
        int moveY = mapOutputToDirection(outputs[OUTPUT_MOVE_Y]);
        boolean eat = outputs[OUTPUT_EAT] > 0.7; // Threshold for eating
        boolean reproduce = outputs[OUTPUT_REPRODUCE] > 0.8; // Threshold for reproduction
        boolean attack = outputs[OUTPUT_AGGRESSION] > 0.7; // Threshold for attacking
        
        return new BrainDecision(moveX, moveY, eat, reproduce, attack);
    }
    
    /**
     * Gather sensory inputs from the entity's surroundings.
     * 
     * @param entity The entity
     * @param world The world state
     * @param entityManager The entity manager
     * @return An array of normalized sensory inputs
     */
    private double[] gatherSensoryInputs(Entity entity, World world, EntityManager entityManager) {
        double[] inputs = new double[INPUT_SIZE];
        int x = entity.getX();
        int y = entity.getY();
        
        // 1. Internal state (normalized to 0-1)
        inputs[0] = entity.getEnergy() / entity.getMaxEnergy(); // Current energy level
        inputs[1] = entity.getHealth() / entity.getMaxHealth(); // Current health level
        
        // 2. Nearby food/prey/predators
        inputs[2] = 0; // Nearest food distance (1 = close, 0 = far/none)
        inputs[3] = 0; // Nearest food direction X (-1 to 1)
        inputs[4] = 0; // Nearest food direction Y (-1 to 1)
        inputs[5] = 0; // Nearest prey distance (1 = close, 0 = far/none)
        inputs[6] = 0; // Nearest prey direction X (-1 to 1)
        inputs[7] = 0; // Nearest prey direction Y (-1 to 1)
        inputs[8] = 0; // Nearest predator distance (1 = close, 0 = far/none)
        inputs[9] = 0; // Nearest predator direction X (-1 to 1)
        inputs[10] = 0; // Nearest predator direction Y (-1 to 1)
        
        // Local tile fertility (which affects food growth)
        Tile currentTile = world.getTile(x, y);
        inputs[11] = currentTile != null ? currentTile.getFertility() : 0;
        
        // Scan surroundings for food and other entities
        findNearestFood(entity, world, inputs);
        findNearestEntities(entity, entityManager, inputs);
        
        return inputs;
    }
    
    /**
     * Find the nearest food source and update the relevant inputs.
     */
    private void findNearestFood(Entity entity, World world, double[] inputs) {
        int x = entity.getX();
        int y = entity.getY();
        
        // If herbivore, look for plant food on tiles
        if (entity.getSpeciesType() == SpeciesType.HERBIVORE) {
            double bestFoodValue = 0;
            int bestFoodX = x;
            int bestFoodY = y;
            boolean foundFood = false;
            
            // Scan in vision range
            for (int scanX = x - visionRange; scanX <= x + visionRange; scanX++) {
                for (int scanY = y - visionRange; scanY <= y + visionRange; scanY++) {
                    if (!world.isValidCoordinate(scanX, scanY)) continue;
                    
                    Tile tile = world.getTile(scanX, scanY);
                    if (tile != null && tile.getTerrainType() != TerrainType.WATER) {
                        double foodValue = tile.getPlantFoodValue();
                        if (foodValue > bestFoodValue) {
                            bestFoodValue = foodValue;
                            bestFoodX = scanX;
                            bestFoodY = scanY;
                            foundFood = true;
                        }
                    }
                }
            }
            
            if (foundFood) {
                double distance = Math.sqrt(Math.pow(bestFoodX - x, 2) + Math.pow(bestFoodY - y, 2));
                double normalizedDistance = Math.max(0, 1 - (distance / visionRange));
                
                inputs[2] = normalizedDistance; // Food distance
                inputs[3] = distance > 0 ? (bestFoodX - x) / distance : 0; // Direction X
                inputs[4] = distance > 0 ? (bestFoodY - y) / distance : 0; // Direction Y
            }
        }
    }
    
    /**
     * Find the nearest prey and predator, and update the relevant inputs.
     */
    private void findNearestEntities(Entity entity, EntityManager entityManager, double[] inputs) {
        int x = entity.getX();
        int y = entity.getY();
        
        Entity nearestPrey = null;
        Entity nearestPredator = null;
        double nearestPreyDistance = Double.MAX_VALUE;
        double nearestPredatorDistance = Double.MAX_VALUE;
        
        // Get all entities in vision range
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(x, y, visionRange);
        
        for (Entity other : nearbyEntities) {
            if (other == entity || !other.isAlive()) continue;
            
            double distance = Math.sqrt(Math.pow(other.getX() - x, 2) + Math.pow(other.getY() - y, 2));
            
            // If carnivore, herbivores are prey
            if (entity.getSpeciesType() == SpeciesType.CARNIVORE && other.getSpeciesType() == SpeciesType.HERBIVORE) {
                if (distance < nearestPreyDistance) {
                    nearestPrey = other;
                    nearestPreyDistance = distance;
                }
            }
            // If herbivore, carnivores are predators
            else if (entity.getSpeciesType() == SpeciesType.HERBIVORE && other.getSpeciesType() == SpeciesType.CARNIVORE) {
                if (distance < nearestPredatorDistance) {
                    nearestPredator = other;
                    nearestPredatorDistance = distance;
                }
            }
        }
        
        // Update prey inputs
        if (nearestPrey != null && nearestPreyDistance <= visionRange) {
            double normalizedDistance = Math.max(0, 1 - (nearestPreyDistance / visionRange));
            inputs[5] = normalizedDistance; // Prey distance
            inputs[6] = nearestPreyDistance > 0 ? (nearestPrey.getX() - x) / nearestPreyDistance : 0; // Direction X
            inputs[7] = nearestPreyDistance > 0 ? (nearestPrey.getY() - y) / nearestPreyDistance : 0; // Direction Y
        }
        
        // Update predator inputs
        if (nearestPredator != null && nearestPredatorDistance <= visionRange) {
            double normalizedDistance = Math.max(0, 1 - (nearestPredatorDistance / visionRange));
            inputs[8] = normalizedDistance; // Predator distance
            inputs[9] = nearestPredatorDistance > 0 ? (nearestPredator.getX() - x) / nearestPredatorDistance : 0; // Direction X
            inputs[10] = nearestPredatorDistance > 0 ? (nearestPredator.getY() - y) / nearestPredatorDistance : 0; // Direction Y
        }
    }
    
    /**
     * Map a continuous output (-1 to 1) to a discrete direction (-1, 0, 1).
     */
    private int mapOutputToDirection(double output) {
        if (output < -0.33) return -1;
        if (output > 0.33) return 1;
        return 0;
    }
    
    /**
     * A class to hold the brain's decision.
     */
    public static class BrainDecision {
        public final int moveX;
        public final int moveY;
        public final boolean eat;
        public final boolean reproduce;
        public final boolean attack;
        
        public BrainDecision(int moveX, int moveY, boolean eat, boolean reproduce, boolean attack) {
            this.moveX = moveX;
            this.moveY = moveY;
            this.eat = eat;
            this.reproduce = reproduce;
            this.attack = attack;
        }
    }
} 
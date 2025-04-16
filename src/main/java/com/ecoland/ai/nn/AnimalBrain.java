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
    private static final int INPUT_SIZE = 18; // Expanded sensory inputs for better decision making
    private static final int HIDDEN_SIZE = 12; // Increased hidden layer for more complex behaviors
    private static final int OUTPUT_SIZE = 5; // Action outputs
    
    // Output neuron indexes
    private static final int OUTPUT_MOVE_X = 0; // -1 to 1 (normalized to -1, 0, 1)
    private static final int OUTPUT_MOVE_Y = 1; // -1 to 1 (normalized to -1, 0, 1)
    private static final int OUTPUT_EAT = 2;    // Threshold for eating
    private static final int OUTPUT_REPRODUCE = 3; // Threshold for reproduction
    private static final int OUTPUT_AGGRESSION = 4; // Threshold for attacking
    
    // Vision range (how far the animal can "see")
    private final int visionRange;
    
    // Behavior modifiers (allows personality variation among individuals)
    private double aggressionModifier = 1.0;
    private double hungerSensitivity = 1.0;
    private double reproductiveUrge = 1.0;
    private double fearResponse = 1.0;
    
    /**
     * Create a new animal brain with a fresh neural network.
     * 
     * @param visionRange How far the animal can see
     */
    public AnimalBrain(int visionRange) {
        this.network = new NeuralNetwork(INPUT_SIZE, HIDDEN_SIZE, OUTPUT_SIZE);
        this.visionRange = visionRange;
        initializePersonality();
    }
    
    /**
     * Create a brain by copying another brain's network.
     * 
     * @param other The brain to copy
     */
    public AnimalBrain(AnimalBrain other) {
        this.network = new NeuralNetwork(other.network);
        this.visionRange = other.visionRange;
        this.aggressionModifier = other.aggressionModifier;
        this.hungerSensitivity = other.hungerSensitivity;
        this.reproductiveUrge = other.reproductiveUrge;
        this.fearResponse = other.fearResponse;
        
        // Add mutation to personality traits (10% chance for each trait)
        if (Math.random() < 0.1) {
            this.aggressionModifier += (Math.random() * 0.4) - 0.2; // +/- 0.2
            this.aggressionModifier = Math.max(0.2, Math.min(2.0, this.aggressionModifier));
        }
        if (Math.random() < 0.1) {
            this.hungerSensitivity += (Math.random() * 0.4) - 0.2; // +/- 0.2
            this.hungerSensitivity = Math.max(0.2, Math.min(2.0, this.hungerSensitivity));
        }
        if (Math.random() < 0.1) {
            this.reproductiveUrge += (Math.random() * 0.4) - 0.2; // +/- 0.2
            this.reproductiveUrge = Math.max(0.2, Math.min(2.0, this.reproductiveUrge));
        }
        if (Math.random() < 0.1) {
            this.fearResponse += (Math.random() * 0.4) - 0.2; // +/- 0.2
            this.fearResponse = Math.max(0.2, Math.min(2.0, this.fearResponse));
        }
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
        initializePersonality();
    }
    
    /**
     * Initialize personality traits with random values.
     */
    private void initializePersonality() {
        // Generate random personality traits
        this.aggressionModifier = 0.7 + (Math.random() * 0.6); // 0.7-1.3
        this.hungerSensitivity = 0.7 + (Math.random() * 0.6); // 0.7-1.3
        this.reproductiveUrge = 0.7 + (Math.random() * 0.6); // 0.7-1.3
        this.fearResponse = 0.7 + (Math.random() * 0.6); // 0.7-1.3
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
        
        AnimalBrain childBrain = new AnimalBrain(childNetwork, childVisionRange);
        
        // Inherit personality traits from parents with crossover
        if (Math.random() < 0.5) {
            childBrain.aggressionModifier = parent1.aggressionModifier;
        } else {
            childBrain.aggressionModifier = parent2.aggressionModifier;
        }
        
        if (Math.random() < 0.5) {
            childBrain.hungerSensitivity = parent1.hungerSensitivity;
        } else {
            childBrain.hungerSensitivity = parent2.hungerSensitivity;
        }
        
        if (Math.random() < 0.5) {
            childBrain.reproductiveUrge = parent1.reproductiveUrge;
        } else {
            childBrain.reproductiveUrge = parent2.reproductiveUrge;
        }
        
        if (Math.random() < 0.5) {
            childBrain.fearResponse = parent1.fearResponse;
        } else {
            childBrain.fearResponse = parent2.fearResponse;
        }
        
        // Add mutation to personality traits (10% chance for each trait)
        if (Math.random() < 0.1) {
            childBrain.aggressionModifier += (Math.random() * 0.4) - 0.2; // +/- 0.2
            childBrain.aggressionModifier = Math.max(0.2, Math.min(2.0, childBrain.aggressionModifier));
        }
        
        return childBrain;
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
        
        // Apply personality modifiers to outputs
        if (entity.getSpeciesType() == SpeciesType.CARNIVORE) {
            // For carnivores, increase aggression threshold if energy is low
            double energyRatio = entity.getEnergy() / entity.getMaxEnergy();
            double aggressionBoost = (1.0 - energyRatio) * 0.3 * hungerSensitivity;
            outputs[OUTPUT_AGGRESSION] = Math.min(1.0, outputs[OUTPUT_AGGRESSION] * aggressionModifier + aggressionBoost);
            
            // Stronger hunting drive when hungry
            if (energyRatio < 0.4) {
                // Boost movement toward prey when hungry
                Entity nearestPrey = findNearestPrey(entity, entityManager);
                if (nearestPrey != null) {
                    double dx = nearestPrey.getX() - entity.getX();
                    double dy = nearestPrey.getY() - entity.getY();
                    double dist = Math.sqrt(dx*dx + dy*dy);
                    
                    if (dist > 0 && dist <= visionRange) {
                        // Normalize direction and apply hunger-based attraction
                        double hungerFactor = (1.0 - energyRatio) * hungerSensitivity * 0.5;
                        outputs[OUTPUT_MOVE_X] += (dx/dist) * hungerFactor;
                        outputs[OUTPUT_MOVE_Y] += (dy/dist) * hungerFactor;
                        
                        // Clamp to valid range (-1 to 1)
                        outputs[OUTPUT_MOVE_X] = Math.max(-1, Math.min(1, outputs[OUTPUT_MOVE_X]));
                        outputs[OUTPUT_MOVE_Y] = Math.max(-1, Math.min(1, outputs[OUTPUT_MOVE_Y]));
                    }
                }
            }
        } else if (entity.getSpeciesType() == SpeciesType.HERBIVORE) {
            // For herbivores, increase flee response when predators nearby
            Entity nearestPredator = findNearestPredator(entity, entityManager);
            if (nearestPredator != null) {
                double dx = nearestPredator.getX() - entity.getX();
                double dy = nearestPredator.getY() - entity.getY();
                double dist = Math.sqrt(dx*dx + dy*dy);
                
                if (dist > 0 && dist <= visionRange) {
                    // Normalize direction and apply fear-based repulsion
                    double fearFactor = (visionRange - dist) / visionRange * fearResponse * 0.6;
                    // Move away from predator (negative of the direction)
                    outputs[OUTPUT_MOVE_X] -= (dx/dist) * fearFactor;
                    outputs[OUTPUT_MOVE_Y] -= (dy/dist) * fearFactor;
                    
                    // Clamp to valid range (-1 to 1)
                    outputs[OUTPUT_MOVE_X] = Math.max(-1, Math.min(1, outputs[OUTPUT_MOVE_X]));
                    outputs[OUTPUT_MOVE_Y] = Math.max(-1, Math.min(1, outputs[OUTPUT_MOVE_Y]));
                    
                    // Suppress reproduction and eating when threatened
                    outputs[OUTPUT_REPRODUCE] *= Math.max(0.1, 1.0 - fearFactor);
                    outputs[OUTPUT_EAT] *= Math.max(0.3, 1.0 - fearFactor * 0.5);
                }
            }
        }
        
        // Apply reproduction modifier based on energy level
        double energyRatio = entity.getEnergy() / entity.getMaxEnergy();
        double reproductionThresholdRatio = entity.getReproductionThreshold() / entity.getMaxEnergy();
        
        // Only allow reproduction when energy is sufficiently above threshold
        if (energyRatio < reproductionThresholdRatio + 0.05) {
            outputs[OUTPUT_REPRODUCE] = 0; // Suppress reproduction when energy is too low
        } else {
            // Adjust reproduction desire based on personality
            outputs[OUTPUT_REPRODUCE] *= reproductiveUrge;
        }
        
        // Convert outputs to decisions
        int moveX = mapOutputToDirection(outputs[OUTPUT_MOVE_X]);
        int moveY = mapOutputToDirection(outputs[OUTPUT_MOVE_Y]);
        boolean eat = outputs[OUTPUT_EAT] > 0.6; // Threshold for eating
        boolean reproduce = outputs[OUTPUT_REPRODUCE] > 0.7 && energyRatio >= reproductionThresholdRatio; 
        boolean attack = outputs[OUTPUT_AGGRESSION] > 0.6; // Threshold for attacking
        
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
        inputs[2] = entity.getReproductionThreshold() / entity.getMaxEnergy(); // Reproduction threshold
        
        // 2. Nearby food/prey/predators
        inputs[3] = 0; // Nearest food distance (1 = close, 0 = far/none)
        inputs[4] = 0; // Nearest food direction X (-1 to 1)
        inputs[5] = 0; // Nearest food direction Y (-1 to 1)
        inputs[6] = 0; // Nearest prey distance (1 = close, 0 = far/none)
        inputs[7] = 0; // Nearest prey direction X (-1 to 1)
        inputs[8] = 0; // Nearest prey direction Y (-1 to 1)
        inputs[9] = 0; // Nearest prey health ratio (1 = healthy, 0 = weak)
        inputs[10] = 0; // Nearest predator distance (1 = close, 0 = far/none)
        inputs[11] = 0; // Nearest predator direction X (-1 to 1)
        inputs[12] = 0; // Nearest predator direction Y (-1 to 1)
        inputs[13] = 0; // Nearest predator health ratio (1 = healthy, 0 = weak)
        
        // 3. Environmental state
        Tile currentTile = world.getTile(x, y);
        inputs[14] = currentTile != null ? currentTile.getFertility() : 0; // Local fertility
        
        // 4. Population density in vision range (normalized)
        int herbivoreCount = 0;
        int carnivoreCount = 0;
        int plantCount = 0;
        
        List<Entity> entitiesInRange = entityManager.getEntitiesInRange(x, y, visionRange);
        for (Entity other : entitiesInRange) {
            if (other == entity || !other.isAlive()) continue;
            
            switch (other.getSpeciesType()) {
                case HERBIVORE: herbivoreCount++; break;
                case CARNIVORE: carnivoreCount++; break;
                case PLANT: plantCount++; break;
            }
        }
        
        // Calculate local population density (normalized to vision range area)
        double visionArea = Math.PI * visionRange * visionRange;
        inputs[15] = Math.min(1.0, herbivoreCount / (visionArea * 0.05)); // Herbivore density
        inputs[16] = Math.min(1.0, carnivoreCount / (visionArea * 0.02)); // Carnivore density
        inputs[17] = Math.min(1.0, plantCount / (visionArea * 0.1)); // Plant density
        
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
                    
                    // Skip if outside of vision circle
                    double distSq = Math.pow(scanX - x, 2) + Math.pow(scanY - y, 2);
                    if (distSq > visionRange * visionRange) continue;
                    
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
                
                inputs[3] = normalizedDistance; // Food distance
                inputs[4] = distance > 0 ? (bestFoodX - x) / distance : 0; // Direction X
                inputs[5] = distance > 0 ? (bestFoodY - y) / distance : 0; // Direction Y
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
            // If carnivore, stronger carnivores could be threats
            else if (entity.getSpeciesType() == SpeciesType.CARNIVORE && other.getSpeciesType() == SpeciesType.CARNIVORE) {
                // Check if the other carnivore is significantly stronger
                if (other.getHealth() > entity.getHealth() * 1.5 && distance < nearestPredatorDistance) {
                    nearestPredator = other;
                    nearestPredatorDistance = distance;
                }
            }
        }
        
        // Update prey inputs
        if (nearestPrey != null && nearestPreyDistance <= visionRange) {
            double normalizedDistance = Math.max(0, 1 - (nearestPreyDistance / visionRange));
            inputs[6] = normalizedDistance; // Prey distance
            inputs[7] = nearestPreyDistance > 0 ? (nearestPrey.getX() - x) / nearestPreyDistance : 0; // Direction X
            inputs[8] = nearestPreyDistance > 0 ? (nearestPrey.getY() - y) / nearestPreyDistance : 0; // Direction Y
            inputs[9] = nearestPrey.getHealth() / nearestPrey.getMaxHealth(); // Prey health ratio
        }
        
        // Update predator inputs
        if (nearestPredator != null && nearestPredatorDistance <= visionRange) {
            double normalizedDistance = Math.max(0, 1 - (nearestPredatorDistance / visionRange));
            inputs[10] = normalizedDistance; // Predator distance
            inputs[11] = nearestPredatorDistance > 0 ? (nearestPredator.getX() - x) / nearestPredatorDistance : 0; // Direction X
            inputs[12] = nearestPredatorDistance > 0 ? (nearestPredator.getY() - y) / nearestPredatorDistance : 0; // Direction Y
            inputs[13] = nearestPredator.getHealth() / nearestPredator.getMaxHealth(); // Predator health ratio
        }
    }
    
    /**
     * Find the nearest prey entity for a carnivore.
     */
    private Entity findNearestPrey(Entity entity, EntityManager entityManager) {
        if (entity.getSpeciesType() != SpeciesType.CARNIVORE) return null;
        
        Entity nearestPrey = null;
        double nearestDistance = Double.MAX_VALUE;
        
        List<Entity> entitiesInRange = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), visionRange);
        
        for (Entity other : entitiesInRange) {
            if (!other.isAlive() || other.getSpeciesType() != SpeciesType.HERBIVORE) continue;
            
            double distance = Math.sqrt(
                    Math.pow(other.getX() - entity.getX(), 2) + 
                    Math.pow(other.getY() - entity.getY(), 2));
            
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPrey = other;
            }
        }
        
        return nearestPrey;
    }
    
    /**
     * Find the nearest predator entity for a herbivore or weaker carnivore.
     */
    private Entity findNearestPredator(Entity entity, EntityManager entityManager) {
        Entity nearestPredator = null;
        double nearestDistance = Double.MAX_VALUE;
        
        List<Entity> entitiesInRange = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), visionRange);
        
        for (Entity other : entitiesInRange) {
            if (!other.isAlive()) continue;
            
            // For herbivores, all carnivores are predators
            if (entity.getSpeciesType() == SpeciesType.HERBIVORE && other.getSpeciesType() == SpeciesType.CARNIVORE) {
                double distance = Math.sqrt(
                        Math.pow(other.getX() - entity.getX(), 2) + 
                        Math.pow(other.getY() - entity.getY(), 2));
                
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPredator = other;
                }
            }
            // For carnivores, stronger carnivores could be threats
            else if (entity.getSpeciesType() == SpeciesType.CARNIVORE && 
                    other.getSpeciesType() == SpeciesType.CARNIVORE &&
                    other.getHealth() > entity.getHealth() * 1.5) {
                
                double distance = Math.sqrt(
                        Math.pow(other.getX() - entity.getX(), 2) + 
                        Math.pow(other.getY() - entity.getY(), 2));
                
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPredator = other;
                }
            }
        }
        
        return nearestPredator;
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
    
    /**
     * Create a child brain based on this brain with possible mutations.
     * @return A new brain that inherits from this brain
     */
    public AnimalBrain createChild() {
        return new AnimalBrain(this);
    }
} 
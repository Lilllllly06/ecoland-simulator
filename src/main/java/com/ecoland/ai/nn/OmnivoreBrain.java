package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

import java.util.List;

/**
 * Specialized brain for Omnivores with balanced plant and prey detection capabilities.
 */
public class OmnivoreBrain extends AnimalBrain {
    private static final long serialVersionUID = 1L;
    
    // Enhanced sensory inputs for omnivores
    private static final int PLANT_DETECTION_INPUT = 18;
    private static final int PREY_DETECTION_INPUT = 19;
    private static final int PREDATOR_DETECTION_INPUT = 20;
    private static final int DIET_BALANCE_INPUT = 21;
    
    // Enhanced network architecture
    private static final int OMNIVORE_INPUT_SIZE = 22;
    private static final int OMNIVORE_HIDDEN_SIZE = 15;
    
    // Omnivore-specific parameters
    private double plantPreference = 0.5; // 0.0-1.0 scale (0 = prefer meat, 1 = prefer plants)
    private double adaptability = 1.0; // How quickly diet preference can change
    private double opportunismFactor = 1.0; // How opportunistic when finding food
    
    // Learning and memory
    private double learningRate = 0.1;
    private int meatMealsCounter = 0;
    private int plantMealsCounter = 0;
    
    /**
     * Create a new omnivore brain.
     * 
     * @param visionRange The vision range of the omnivore
     */
    public OmnivoreBrain(int visionRange) {
        super(visionRange);
        // Initialize omnivore-specific features
        this.plantPreference = 0.4 + Math.random() * 0.4; // 0.4-0.8 (slight bias toward balanced diet)
        this.adaptability = 0.8 + Math.random() * 0.4; // 0.8-1.2
        this.opportunismFactor = 0.8 + Math.random() * 0.4; // 0.8-1.2
        this.learningRate = 0.08 + Math.random() * 0.08; // 0.08-0.16
    }
    
    /**
     * Create an omnivore brain based on a parent brain.
     * 
     * @param parentBrain The parent brain to inherit from
     */
    public OmnivoreBrain(AnimalBrain parentBrain) {
        super(parentBrain);
        
        if (parentBrain instanceof OmnivoreBrain) {
            OmnivoreBrain parent = (OmnivoreBrain) parentBrain;
            this.plantPreference = parent.plantPreference;
            this.adaptability = parent.adaptability;
            this.opportunismFactor = parent.opportunismFactor;
            this.learningRate = parent.learningRate;
            
            // Inherit some meal history (weighted average of past meals)
            this.meatMealsCounter = parent.meatMealsCounter / 3;
            this.plantMealsCounter = parent.plantMealsCounter / 3;
            
            // Apply mutations (15% chance)
            if (Math.random() < 0.15) {
                this.plantPreference += (Math.random() * 0.2) - 0.1; // ±0.1
                this.plantPreference = Math.max(0.1, Math.min(0.9, this.plantPreference));
            }
            
            if (Math.random() < 0.15) {
                this.adaptability += (Math.random() * 0.2) - 0.1; // ±0.1
                this.adaptability = Math.max(0.5, Math.min(1.5, this.adaptability));
            }
            
            if (Math.random() < 0.15) {
                this.opportunismFactor += (Math.random() * 0.2) - 0.1; // ±0.1
                this.opportunismFactor = Math.max(0.5, Math.min(1.5, this.opportunismFactor));
            }
        }
    }
    
    @Override
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        // Track energy before decision for learning
        double previousEnergy = entity.getEnergy();
        
        // Gather enhanced omnivore-specific inputs
        double[] inputs = gatherOmnivoreInputs(entity, world, entityManager);
        
        // Get base decision from parent class
        BrainDecision decision = super.makeDecision(entity, world, entityManager);
        
        // Enhanced omnivore behavior - opportunistic feeding
        Entity nearestPrey = findNearestPrey(entity, entityManager);
        Tile bestPlantTile = findBestPlantTile(entity, world);
        Entity nearestPredator = findNearestPredator(entity, entityManager);
        
        // Prioritize safety - if predator is close, flee
        if (nearestPredator != null) {
            double predatorDistance = calculateDistance(entity, nearestPredator);
            
            if (predatorDistance < 3) {
                // Flee from predator (move in opposite direction)
                int fleeX = entity.getX() - nearestPredator.getX();
                int fleeY = entity.getY() - nearestPredator.getY();
                
                // Normalize direction
                if (fleeX != 0) fleeX = Integer.signum(fleeX);
                if (fleeY != 0) fleeY = Integer.signum(fleeY);
                
                return new BrainDecision(fleeX, fleeY, false, false, false);
            }
        }
        
        // If hungry, make opportunistic food choice based on availability and preference
        if (entity.getEnergy() < entity.getMaxEnergy() * 0.7) {
            boolean preyAvailable = nearestPrey != null;
            boolean plantAvailable = bestPlantTile != null;
            
            if (preyAvailable && plantAvailable) {
                // Both food types available - choose based on preference and distance
                double preyDistance = calculateDistance(entity, nearestPrey);
                double plantDistance = calculatePlantDistance(entity, bestPlantTile, world);
                
                // Adjust distances by preference (shorter = more attractive)
                double adjustedPreyDistance = preyDistance * (1.0 + plantPreference);
                double adjustedPlantDistance = plantDistance * (2.0 - plantPreference);
                
                // Apply opportunism factor - more opportunistic omnivores care more about proximity
                double proximityWeight = opportunismFactor * 0.5;
                double preferenceWeight = 1.0 - proximityWeight;
                
                boolean choosePrey = (adjustedPreyDistance * proximityWeight + (1.0 - plantPreference) * preferenceWeight) < 
                                  (adjustedPlantDistance * proximityWeight + plantPreference * preferenceWeight);
                
                if (choosePrey) {
                    moveTowardAndEat(entity, nearestPrey, decision);
                } else {
                    moveTowardPlant(entity, bestPlantTile, decision);
                }
            } else if (preyAvailable) {
                moveTowardAndEat(entity, nearestPrey, decision);
            } else if (plantAvailable) {
                moveTowardPlant(entity, bestPlantTile, decision);
            }
        }
        
        // Learning from feeding results
        double energyChange = entity.getEnergy() - previousEnergy;
        
        // If significant energy increase, a successful meal occurred
        if (energyChange > 3.0) {
            // Identify what type of meal it likely was based on position
            boolean likelyAtePlant = false;
            Tile currentTile = world.getTile(entity.getX(), entity.getY());
            if (currentTile != null && currentTile.getPlantFoodValue() > 0) {
                likelyAtePlant = true;
                plantMealsCounter++;
            } else {
                meatMealsCounter++;
            }
            
            // Adjust preferences based on successful meals
            if (Math.random() < learningRate * adaptability) {
                if (likelyAtePlant) {
                    // Successful plant meal increases plant preference slightly
                    plantPreference = Math.min(0.9, plantPreference + 0.02);
                } else {
                    // Successful meat meal decreases plant preference slightly
                    plantPreference = Math.max(0.1, plantPreference - 0.02);
                }
            }
        }
        
        return decision;
    }
    
    /**
     * Move toward and attempt to eat prey.
     */
    private void moveTowardAndEat(Entity entity, Entity prey, BrainDecision decision) {
        if (prey == null) return;
        
        double distance = calculateDistance(entity, prey);
        
        // Calculate direction to prey
        int dx = prey.getX() - entity.getX();
        int dy = prey.getY() - entity.getY();
        
        // Normalize direction
        int moveX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
        int moveY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
        
        // If adjacent to prey, attack/eat
        boolean shouldEat = distance <= 1.5;
        boolean shouldAttack = shouldEat && prey.isAlive();
        
        // Update decision
        decision = new BrainDecision(moveX, moveY, shouldEat, false, shouldAttack);
    }
    
    /**
     * Find the best plant tile for feeding.
     */
    private Tile findBestPlantTile(Entity entity, World world) {
        int x = entity.getX();
        int y = entity.getY();
        int entityVisionRange = (int)entity.getVisionRange();
        Tile bestTile = null;
        double bestValue = 0;
        
        // Scan in vision range
        for (int scanX = x - entityVisionRange; scanX <= x + entityVisionRange; scanX++) {
            for (int scanY = y - entityVisionRange; scanY <= y + entityVisionRange; scanY++) {
                if (!world.isValidCoordinate(scanX, scanY)) continue;
                
                // Skip if outside of vision circle
                double distSq = Math.pow(scanX - x, 2) + Math.pow(scanY - y, 2);
                if (distSq > entityVisionRange * entityVisionRange) continue;
                
                Tile tile = world.getTile(scanX, scanY);
                if (tile != null && tile.getTerrainType() != TerrainType.WATER) {
                    double foodValue = tile.getPlantFoodValue();
                    
                    // Calculate value taking into account distance
                    double distance = Math.sqrt(distSq);
                    double value = foodValue / (1 + distance * 0.5);
                    
                    if (value > bestValue) {
                        bestValue = value;
                        bestTile = tile;
                    }
                }
            }
        }
        
        return bestTile;
    }
    
    /**
     * Calculate distance between entity and plant food target.
     */
    private double calculatePlantDistance(Entity entity, Tile plantTile, World world) {
        if (entity == null || plantTile == null || world == null) return Double.MAX_VALUE;
        
        int entityX = entity.getX();
        int entityY = entity.getY();
        
        // Find the tile location in the world
        int worldWidth = world.getWidth();
        int worldHeight = world.getHeight();
        
        // We need to find the tile position by scanning the world
        // This is inefficient but necessary since Tile doesn't store coords
        int tileX = -1;
        int tileY = -1;
        
        for (int x = 0; x < worldWidth; x++) {
            for (int y = 0; y < worldHeight; y++) {
                if (world.getTile(x, y) == plantTile) {
                    tileX = x;
                    tileY = y;
                    break;
                }
            }
            if (tileX >= 0) break; // Found it
        }
        
        if (tileX >= 0 && tileY >= 0) {
            return calculateDistance(entityX, entityY, tileX, tileY);
        }
        
        return Double.MAX_VALUE; // Tile not found
    }
    
    /**
     * Move toward and attempt to eat plant food.
     */
    private void moveTowardPlant(Entity entity, Tile plantTile, BrainDecision decision) {
        if (plantTile == null) return;
        
        // Approximate direction to move based on plant food in nearby tiles
        int entityX = entity.getX();
        int entityY = entity.getY();
        World world = null;
        
        // Since we don't have direct access to the world, use the best nearby tile
        // for determining direction
        double bestValue = 0.0;
        int bestDx = 0;
        int bestDy = 0;
        
        // Scan immediate surroundings
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip current position
                
                int nx = entityX + dx;
                int ny = entityY + dy;
                
                // Approximation - we don't have world access inside the brain,
                // so use a simple gradient movement toward higher plant food
                double valueInDirection = dx * 0.5 + dy * 0.5; // Move in some direction
                if (valueInDirection > bestValue) {
                    bestValue = valueInDirection;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }
        
        // Use the best direction found
        decision = new BrainDecision(bestDx, bestDy, true, false, false);
    }
    
    /**
     * Find the nearest potential prey.
     */
    private Entity findNearestPrey(Entity entity, EntityManager entityManager) {
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), (int)entity.getVisionRange());
        
        Entity closestPrey = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Entity other : nearbyEntities) {
            if (!other.isAlive() || other == entity) continue;
            
            // Omnivores can prey on smaller herbivores and plants
            boolean isPotentialPrey = (other.getSpeciesType() == SpeciesType.HERBIVORE && 
                                     other.getHealth() < entity.getHealth()) || 
                                     other.getSpeciesType() == SpeciesType.PLANT;
            
            if (isPotentialPrey) {
                double distance = calculateDistance(entity, other);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPrey = other;
                }
            }
        }
        
        return closestPrey;
    }
    
    /**
     * Find the nearest predator threat.
     */
    private Entity findNearestPredator(Entity entity, EntityManager entityManager) {
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), (int)entity.getVisionRange());
        
        Entity closestPredator = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Entity other : nearbyEntities) {
            if (!other.isAlive() || other == entity) continue;
            
            boolean isPredator = other.getSpeciesType() == SpeciesType.CARNIVORE || 
                              other.getSpeciesType() == SpeciesType.APEX_PREDATOR;
            
            if (isPredator) {
                double distance = calculateDistance(entity, other);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPredator = other;
                }
            }
        }
        
        return closestPredator;
    }
    
    /**
     * Calculate distance between two entities.
     */
    private double calculateDistance(Entity a, Entity b) {
        return Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
    }
    
    /**
     * Calculate distance between two coordinates.
     */
    private double calculateDistance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }
    
    /**
     * Gather enhanced inputs specific to omnivores.
     */
    private double[] gatherOmnivoreInputs(Entity entity, World world, EntityManager entityManager) {
        double[] inputs = new double[OMNIVORE_INPUT_SIZE];
        
        // Base inputs would be handled by the parent class
        
        // Enhanced food detection
        int x = entity.getX();
        int y = entity.getY();
        
        // Plant food detection
        double maxPlantFood = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (world.isValidCoordinate(nx, ny)) {
                    Tile tile = world.getTile(nx, ny);
                    if (tile.getTerrainType() != TerrainType.WATER) {
                        maxPlantFood = Math.max(maxPlantFood, tile.getPlantFoodValue());
                    }
                }
            }
        }
        inputs[PLANT_DETECTION_INPUT] = Math.min(1.0, maxPlantFood);
        
        // Prey detection
        List<Entity> entities = entityManager.getEntitiesInRange(x, y, (int)entity.getVisionRange());
        int preyCount = 0;
        
        for (Entity other : entities) {
            if (other.isAlive() && other != entity && 
                (other.getSpeciesType() == SpeciesType.HERBIVORE || 
                 other.getSpeciesType() == SpeciesType.PLANT)) {
                preyCount++;
            }
        }
        inputs[PREY_DETECTION_INPUT] = Math.min(1.0, preyCount / 8.0);
        
        // Predator detection
        int predatorCount = 0;
        for (Entity other : entities) {
            if (other.isAlive() && 
               (other.getSpeciesType() == SpeciesType.CARNIVORE || 
                other.getSpeciesType() == SpeciesType.APEX_PREDATOR)) {
                predatorCount++;
            }
        }
        inputs[PREDATOR_DETECTION_INPUT] = Math.min(1.0, predatorCount / 3.0);
        
        // Current diet balance
        int totalMeals = meatMealsCounter + plantMealsCounter;
        if (totalMeals > 0) {
            inputs[DIET_BALANCE_INPUT] = (double)plantMealsCounter / totalMeals;
        } else {
            inputs[DIET_BALANCE_INPUT] = plantPreference; // Default to genetic preference
        }
        
        return inputs;
    }
    
    @Override
    public OmnivoreBrain createChild() {
        return new OmnivoreBrain(this);
    }
} 
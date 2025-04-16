package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Specialized brain for Carnivores with enhanced hunting and prey tracking abilities.
 */
public class CarnivoreBrain extends AnimalBrain {
    private static final long serialVersionUID = 1L;
    
    // Enhanced sensory inputs for hunting
    private static final int PREY_DENSITY_INPUT = 18;
    private static final int PREY_HEALTH_INPUT = 19;
    private static final int HUNTING_SUCCESS_INPUT = 20;
    private static final int PACK_HUNTING_INPUT = 21;
    
    // Enhanced network architecture
    private static final int CARNIVORE_INPUT_SIZE = 22;
    private static final int CARNIVORE_HIDDEN_SIZE = 16;
    
    // Hunting memory
    private int[] lastSuccessfulHuntLocation = null;
    private int huntingSuccessCounter = 0;
    private Map<Integer, Entity> trackedPrey = new HashMap<>();
    
    // Learning parameters
    private double learningRate = 0.12;
    private int consecutiveFailedHunts = 0;
    private double aggressionMultiplier = 1.0;
    
    /**
     * Create a new carnivore brain.
     * 
     * @param visionRange The vision range of the carnivore
     */
    public CarnivoreBrain(int visionRange) {
        super(visionRange);
        // Initialize carnivore-specific features
        this.learningRate = 0.08 + Math.random() * 0.1; // 0.08-0.18
        this.aggressionMultiplier = 0.8 + Math.random() * 0.4; // 0.8-1.2
    }
    
    /**
     * Create a carnivore brain based on a parent brain.
     * 
     * @param parentBrain The parent brain to inherit from
     */
    public CarnivoreBrain(AnimalBrain parentBrain) {
        super(parentBrain);
        
        if (parentBrain instanceof CarnivoreBrain) {
            CarnivoreBrain parent = (CarnivoreBrain) parentBrain;
            this.learningRate = parent.learningRate;
            this.aggressionMultiplier = parent.aggressionMultiplier;
            this.huntingSuccessCounter = parent.huntingSuccessCounter / 4; // Inherit some hunting experience
            
            // Apply mutations to learning rate (15% chance)
            if (Math.random() < 0.15) {
                this.learningRate += (Math.random() * 0.06) - 0.03; // ±0.03
                this.learningRate = Math.max(0.05, Math.min(0.25, this.learningRate));
            }
            
            // Apply mutations to aggression (20% chance)
            if (Math.random() < 0.2) {
                this.aggressionMultiplier += (Math.random() * 0.3) - 0.15; // ±0.15
                this.aggressionMultiplier = Math.max(0.6, Math.min(1.5, this.aggressionMultiplier));
            }
        }
    }
    
    @Override
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        // Track energy before decision for learning
        double previousEnergy = entity.getEnergy();
        
        // Gather enhanced carnivore-specific inputs
        double[] inputs = gatherCarnivoreInputs(entity, world, entityManager);
        
        // Get base decision from parent class
        BrainDecision decision = super.makeDecision(entity, world, entityManager);
        
        // Enhanced hunting behavior
        Entity bestPreyTarget = findOptimalPrey(entity, entityManager);
        
        // If hungry enough and prey available, focus on hunting
        if (entity.getEnergy() < entity.getMaxEnergy() * 0.6 && bestPreyTarget != null) {
            // Calculate direction to prey
            int dx = bestPreyTarget.getX() - entity.getX();
            int dy = bestPreyTarget.getY() - entity.getY();
            
            // Only override movement if prey is in good hunting range
            double distanceToPrey = Math.sqrt(dx*dx + dy*dy);
            
            if (distanceToPrey <= entity.getVisionRange()) {
                // Store prey for tracking
                trackedPrey.put(bestPreyTarget.hashCode(), bestPreyTarget);
                
                // Normalize direction
                int moveX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
                int moveY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
                
                // If adjacent to prey, attack
                boolean shouldAttack = distanceToPrey <= 1.5;
                
                // Update decision with hunting behavior
                decision = new BrainDecision(moveX, moveY, shouldAttack, false, shouldAttack);
            }
        }
        
        // Learning from hunting - was the last decision effective?
        double energyChange = entity.getEnergy() - previousEnergy;
        
        // If energy increased significantly, a successful hunt occurred
        if (energyChange > 5.0) {
            huntingSuccessCounter++;
            consecutiveFailedHunts = 0;
            lastSuccessfulHuntLocation = new int[] {entity.getX(), entity.getY()};
            
            // Positive reinforcement - successful hunt increases aggression
            if (Math.random() < learningRate * 0.5) {
                aggressionMultiplier = Math.min(1.5, aggressionMultiplier + 0.02);
            }
        } 
        // If energy decreased and in hunting mode, track failed hunts
        else if (energyChange < -1.0 && bestPreyTarget != null) {
            consecutiveFailedHunts++;
            
            // Negative reinforcement - too many failed hunts decreases aggression
            if (consecutiveFailedHunts > 3 && Math.random() < learningRate * 0.3) {
                aggressionMultiplier = Math.max(0.6, aggressionMultiplier - 0.03);
                // Clean up tracked prey that may be unreachable
                cleanupTrackedPrey();
            }
        }
        
        return decision;
    }
    
    /**
     * Find the optimal prey to hunt based on distance, health, and size.
     */
    private Entity findOptimalPrey(Entity entity, EntityManager entityManager) {
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), (int)entity.getVisionRange());
        
        Entity bestTarget = null;
        double bestTargetScore = -1;
        
        for (Entity other : nearbyEntities) {
            if (!other.isAlive() || other == entity) continue;
            
            // Check if entity is potential prey
            boolean isPrey = other.getSpeciesType() == SpeciesType.HERBIVORE ||
                            (other.getSpeciesType() == SpeciesType.OMNIVORE && 
                             other.getHealth() < entity.getHealth() * 0.8);
            
            if (isPrey) {
                double distance = Math.sqrt(
                        Math.pow(other.getX() - entity.getX(), 2) + 
                        Math.pow(other.getY() - entity.getY(), 2));
                
                // Calculate prey attractiveness score (lower distance and health = better target)
                double healthFactor = 1.0 - (other.getHealth() / other.getMaxHealth());
                double distanceFactor = 1.0 - (distance / entity.getVisionRange());
                
                double preyScore = (healthFactor * 0.6) + (distanceFactor * 0.4);
                
                // Bonus for previously tracked prey (persistence)
                if (trackedPrey.containsKey(other.hashCode())) {
                    preyScore += 0.15;
                }
                
                if (preyScore > bestTargetScore) {
                    bestTargetScore = preyScore;
                    bestTarget = other;
                }
            }
        }
        
        return bestTarget;
    }
    
    /**
     * Gather enhanced inputs specific to carnivores.
     */
    private double[] gatherCarnivoreInputs(Entity entity, World world, EntityManager entityManager) {
        double[] inputs = new double[CARNIVORE_INPUT_SIZE];
        
        // Base inputs would be populated here
        
        // Enhanced prey detection
        int x = entity.getX();
        int y = entity.getY();
        int preyCount = 0;
        double totalPreyHealth = 0;
        
        List<Entity> entities = entityManager.getEntitiesInRange(x, y, (int)entity.getVisionRange());
        
        for (Entity other : entities) {
            if (other.isAlive() && other.getSpeciesType() == SpeciesType.HERBIVORE) {
                preyCount++;
                totalPreyHealth += other.getHealth() / other.getMaxHealth();
            }
        }
        
        // Calculate prey density
        inputs[PREY_DENSITY_INPUT] = Math.min(1.0, preyCount / 10.0);
        
        // Calculate average prey health (0 if no prey)
        inputs[PREY_HEALTH_INPUT] = preyCount > 0 ? (totalPreyHealth / preyCount) : 0;
        
        // Hunting success based on past experience (normalized 0-1)
        inputs[HUNTING_SUCCESS_INPUT] = Math.min(1.0, huntingSuccessCounter / 10.0);
        
        // Pack hunting potential (other carnivores nearby)
        int carnivoreCount = 0;
        for (Entity other : entities) {
            if (other != entity && other.isAlive() && other.getSpeciesType() == SpeciesType.CARNIVORE) {
                carnivoreCount++;
            }
        }
        inputs[PACK_HUNTING_INPUT] = Math.min(1.0, carnivoreCount / 3.0);
        
        return inputs;
    }
    
    /**
     * Clean up tracked prey that may no longer be valid targets.
     */
    private void cleanupTrackedPrey() {
        trackedPrey.entrySet().removeIf(entry -> 
            entry.getValue() == null || 
            !entry.getValue().isAlive() || 
            entry.getValue().getHealth() < 0.1
        );
    }
    
    @Override
    public CarnivoreBrain createChild() {
        return new CarnivoreBrain(this);
    }
} 
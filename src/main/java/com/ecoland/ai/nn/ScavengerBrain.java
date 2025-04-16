package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

import java.util.List;
import java.util.ArrayList;

/**
 * Specialized brain for Scavengers with enhanced dead body detection and predator avoidance.
 */
public class ScavengerBrain extends AnimalBrain {
    private static final long serialVersionUID = 1L;
    
    // Enhanced sensory inputs for scavenging
    private static final int DEAD_BODY_DENSITY_INPUT = 18;
    private static final int DEAD_BODY_QUALITY_INPUT = 19;
    private static final int PREDATOR_THREAT_INPUT = 20;
    private static final int DECOMPOSITION_STAGE_INPUT = 21;
    
    // Enhanced network architecture
    private static final int SCAVENGER_INPUT_SIZE = 22;
    private static final int SCAVENGER_HIDDEN_SIZE = 14;
    
    // Scavenging memory
    private List<int[]> knownDeadBodyLocations = new ArrayList<>();
    private double scavengingEfficiency = 1.0;
    private int successfulScavenges = 0;
    
    // Learning parameters
    private double learningRate = 0.1;
    private double detectionBonus = 1.0;
    
    /**
     * Create a new scavenger brain.
     * 
     * @param visionRange The vision range of the scavenger
     */
    public ScavengerBrain(int visionRange) {
        super(visionRange);
        // Initialize scavenger-specific features
        this.learningRate = 0.07 + Math.random() * 0.08; // 0.07-0.15
        this.scavengingEfficiency = 0.9 + Math.random() * 0.3; // 0.9-1.2
        this.detectionBonus = 1.0 + Math.random() * 0.5; // 1.0-1.5
    }
    
    /**
     * Create a scavenger brain based on a parent brain.
     * 
     * @param parentBrain The parent brain to inherit from
     */
    public ScavengerBrain(AnimalBrain parentBrain) {
        super(parentBrain);
        
        if (parentBrain instanceof ScavengerBrain) {
            ScavengerBrain parent = (ScavengerBrain) parentBrain;
            this.learningRate = parent.learningRate;
            this.scavengingEfficiency = parent.scavengingEfficiency;
            this.detectionBonus = parent.detectionBonus;
            this.successfulScavenges = parent.successfulScavenges / 3; // Inherit some scavenging experience
            
            // Apply mutations (20% chance)
            if (Math.random() < 0.2) {
                this.scavengingEfficiency += (Math.random() * 0.2) - 0.1; // ±0.1
                this.scavengingEfficiency = Math.max(0.7, Math.min(1.5, this.scavengingEfficiency));
            }
            
            if (Math.random() < 0.2) {
                this.detectionBonus += (Math.random() * 0.3) - 0.15; // ±0.15
                this.detectionBonus = Math.max(0.8, Math.min(2.0, this.detectionBonus));
            }
        }
    }
    
    @Override
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        // Track energy before decision for learning
        double previousEnergy = entity.getEnergy();
        
        // Gather enhanced scavenger-specific inputs
        double[] inputs = gatherScavengerInputs(entity, world, entityManager);
        
        // Get base decision from parent class
        BrainDecision decision = super.makeDecision(entity, world, entityManager);
        
        // Enhanced scavenging behavior
        Entity nearestDeadBody = findNearestDeadBody(entity, entityManager);
        Entity nearestPredator = findNearestPredator(entity, entityManager);
        
        // Prioritize safety - if predator is close, flee regardless of food
        if (nearestPredator != null) {
            double predatorDistance = calculateDistance(entity, nearestPredator);
            
            if (predatorDistance < 4) {
                // Flee from predator (move in opposite direction)
                int fleeX = entity.getX() - nearestPredator.getX();
                int fleeY = entity.getY() - nearestPredator.getY();
                
                // Normalize direction
                if (fleeX != 0) fleeX = Integer.signum(fleeX);
                if (fleeY != 0) fleeY = Integer.signum(fleeY);
                
                // Override with flee behavior
                return new BrainDecision(fleeX, fleeY, false, false, false);
            }
        }
        
        // If hungry enough and dead body available, focus on scavenging
        if (entity.getEnergy() < entity.getMaxEnergy() * 0.7 && nearestDeadBody != null) {
            double deadBodyDistance = calculateDistance(entity, nearestDeadBody);
            
            // Remember this dead body location
            int[] location = new int[]{nearestDeadBody.getX(), nearestDeadBody.getY()};
            if (!containsLocation(knownDeadBodyLocations, location)) {
                knownDeadBodyLocations.add(location);
                // Keep list manageable size
                if (knownDeadBodyLocations.size() > 5) {
                    knownDeadBodyLocations.remove(0);
                }
            }
            
            // Move toward the dead body
            if (deadBodyDistance > 0) {
                int dx = nearestDeadBody.getX() - entity.getX();
                int dy = nearestDeadBody.getY() - entity.getY();
                
                // Normalize direction
                int moveX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
                int moveY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
                
                // If adjacent to dead body, scavenge
                boolean shouldEat = deadBodyDistance <= 1.5;
                
                // Update decision with scavenging behavior
                decision = new BrainDecision(moveX, moveY, shouldEat, false, false);
            }
        }
        
        // Learning from scavenging results
        double energyChange = entity.getEnergy() - previousEnergy;
        
        // If energy increased significantly, successful scavenging occurred
        if (energyChange > 3.0) {
            successfulScavenges++;
            
            // Positive reinforcement - successful scavenging improves efficiency
            if (Math.random() < learningRate * 0.5) {
                scavengingEfficiency = Math.min(1.5, scavengingEfficiency + 0.02);
                System.out.println("Scavenger improved efficiency to: " + scavengingEfficiency);
            }
        }
        
        return decision;
    }
    
    /**
     * Find the nearest dead body for scavenging.
     */
    private Entity findNearestDeadBody(Entity entity, EntityManager entityManager) {
        List<Entity> deadBodies = entityManager.findDeadBodiesInRange(
                entity.getX(), entity.getY(), entity.getVisionRange() * detectionBonus, null);
        
        return deadBodies.isEmpty() ? null : deadBodies.get(0); // Already sorted by distance
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
     * Check if a location is in the known locations list.
     */
    private boolean containsLocation(List<int[]> locations, int[] target) {
        for (int[] loc : locations) {
            if (loc[0] == target[0] && loc[1] == target[1]) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gather enhanced inputs specific to scavengers.
     */
    private double[] gatherScavengerInputs(Entity entity, World world, EntityManager entityManager) {
        double[] inputs = new double[SCAVENGER_INPUT_SIZE];
        
        // Base inputs would be populated here
        
        // Enhanced dead body detection
        int x = entity.getX();
        int y = entity.getY();
        List<Entity> deadBodies = entityManager.findDeadBodiesInRange(
                x, y, entity.getVisionRange() * detectionBonus, world);
        
        // Dead body density
        inputs[DEAD_BODY_DENSITY_INPUT] = Math.min(1.0, deadBodies.size() / 5.0);
        
        // Dead body quality (average nutrition value)
        if (!deadBodies.isEmpty()) {
            double totalNutrition = 0;
            for (Entity deadBody : deadBodies) {
                totalNutrition += deadBody.getDeadBodyNutritionValue();
            }
            inputs[DEAD_BODY_QUALITY_INPUT] = Math.min(1.0, totalNutrition / (deadBodies.size() * 100));
        }
        
        // Predator threat assessment
        List<Entity> entities = entityManager.getEntitiesInRange(x, y, (int)entity.getVisionRange());
        double maxThreat = 0;
        
        for (Entity other : entities) {
            if (!other.isAlive() || other == entity) continue;
            
            if (other.getSpeciesType() == SpeciesType.CARNIVORE || 
                other.getSpeciesType() == SpeciesType.APEX_PREDATOR) {
                
                double distance = calculateDistance(entity, other);
                double threat = 1.0 - (distance / entity.getVisionRange());
                
                if (threat > maxThreat) {
                    maxThreat = threat;
                }
            }
        }
        
        inputs[PREDATOR_THREAT_INPUT] = maxThreat;
        
        // Decomposition awareness (based on scavenging experience)
        inputs[DECOMPOSITION_STAGE_INPUT] = Math.min(1.0, successfulScavenges / 20.0);
        
        return inputs;
    }
    
    @Override
    public ScavengerBrain createChild() {
        return new ScavengerBrain(this);
    }
} 
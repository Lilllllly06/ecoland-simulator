package com.ecoland.ai.nn;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.World;
import com.ecoland.simulation.EntityManager;

import java.util.List;
import java.util.ArrayList;

/**
 * Specialized brain for Apex Predators with advanced hunting and territorial behaviors.
 * Apex predators have enhanced memory, learning, and decision-making compared to regular carnivores.
 */
public class ApexPredatorBrain extends AnimalBrain {
    private static final long serialVersionUID = 1L;
    
    // Enhanced sensory inputs for apex predator
    private static final int TERRITORY_AWARENESS_INPUT = 18;
    private static final int PREY_VULNERABILITY_INPUT = 19;
    private static final int STAMINA_INPUT = 20;
    private static final int RIVAL_AWARENESS_INPUT = 21;
    private static final int HUNTING_EXPERIENCE_INPUT = 22;
    
    // Enhanced network architecture
    private static final int APEX_INPUT_SIZE = 23;
    private static final int APEX_HIDDEN_SIZE = 18;
    
    // Territory and hunting memory
    private int[] territoryCenter = null;
    private double territoryRadius = 15.0;
    private List<int[]> successfulHuntLocations = new ArrayList<>();
    private int huntingExperience = 0;
    private int failedHuntCounter = 0;
    
    // Advanced learning parameters
    private double learningRate = 0.15;
    private double territorialism = 1.0;
    private double huntingProficiency = 1.0;
    private double staminaEfficiency = 1.0;
    private double aggressionLevel = 1.0;
    
    // Cooldown timers
    private int attackCooldown = 0;
    private static final int ATTACK_COOLDOWN_MAX = 3;
    
    /**
     * Create a new apex predator brain.
     * 
     * @param visionRange The vision range of the apex predator
     */
    public ApexPredatorBrain(int visionRange) {
        super(visionRange);
        // Initialize apex predator-specific features with randomization
        this.learningRate = 0.12 + Math.random() * 0.08; // 0.12-0.20
        this.territorialism = 0.8 + Math.random() * 0.6; // 0.8-1.4
        this.huntingProficiency = 0.9 + Math.random() * 0.6; // 0.9-1.5
        this.staminaEfficiency = 0.9 + Math.random() * 0.3; // 0.9-1.2
        this.aggressionLevel = 0.8 + Math.random() * 0.7; // 0.8-1.5
    }
    
    /**
     * Create an apex predator brain based on a parent brain.
     * 
     * @param parentBrain The parent brain to inherit from
     */
    public ApexPredatorBrain(AnimalBrain parentBrain) {
        super(parentBrain);
        
        if (parentBrain instanceof ApexPredatorBrain) {
            ApexPredatorBrain parent = (ApexPredatorBrain) parentBrain;
            
            // Inherit traits with slight mutations
            this.learningRate = mutateValue(parent.learningRate, 0.2, 0.1, 0.25);
            this.territorialism = mutateValue(parent.territorialism, 0.2, 0.7, 1.8);
            this.huntingProficiency = mutateValue(parent.huntingProficiency, 0.2, 0.8, 2.0);
            this.staminaEfficiency = mutateValue(parent.staminaEfficiency, 0.2, 0.8, 1.5);
            this.aggressionLevel = mutateValue(parent.aggressionLevel, 0.25, 0.7, 2.0);
            
            // Inherit some hunting experience
            this.huntingExperience = parent.huntingExperience / 4;
            
            // If parent has a territory, inherit a position near it
            if (parent.territoryCenter != null) {
                this.territoryCenter = new int[]{
                    parent.territoryCenter[0] + (int)(Math.random() * 10) - 5,
                    parent.territoryCenter[1] + (int)(Math.random() * 10) - 5
                };
                this.territoryRadius = parent.territoryRadius * 0.8;
            }
        }
    }
    
    @Override
    public BrainDecision makeDecision(Entity entity, World world, EntityManager entityManager) {
        // Track energy and position before decision
        double previousEnergy = entity.getEnergy();
        int previousX = entity.getX();
        int previousY = entity.getY();
        
        // Decrease cooldowns
        if (attackCooldown > 0) attackCooldown--;
        
        // Establish territory if not already set
        if (territoryCenter == null) {
            territoryCenter = new int[]{entity.getX(), entity.getY()};
            territoryRadius = 10.0 + (Math.random() * 10); // 10-20 radius
        }
        
        // Gather enhanced apex predator-specific inputs
        double[] inputs = gatherApexPredatorInputs(entity, world, entityManager);
        
        // Get base decision from parent class
        BrainDecision decision = super.makeDecision(entity, world, entityManager);
        
        // Enhanced apex predator behavior
        Entity bestPreyTarget = findOptimalPrey(entity, entityManager);
        Entity rivalPredator = findNearestRival(entity, entityManager);
        
        // Territorial defense - challenge rivals in territory
        if (rivalPredator != null && isWithinTerritory(rivalPredator.getX(), rivalPredator.getY()) && 
            aggressionLevel > 0.9 && attackCooldown == 0) {
            
            double rivalDistance = calculateDistance(entity, rivalPredator);
            
            // Only challenge if stronger or similar strength
            if (entity.getHealth() >= rivalPredator.getHealth() * 0.8 && rivalDistance < 5) {
                int dx = rivalPredator.getX() - entity.getX();
                int dy = rivalPredator.getY() - entity.getY();
                
                // Normalize direction
                int moveX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
                int moveY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
                
                // If adjacent to rival, attack
                boolean shouldAttack = rivalDistance <= 1.5;
                
                if (shouldAttack) {
                    attackCooldown = ATTACK_COOLDOWN_MAX;
                }
                
                // Override with territorial defense behavior
                return new BrainDecision(moveX, moveY, false, false, shouldAttack);
            }
        }
        
        // Enhanced hunting behavior - especially effective when hungry
        if (entity.getEnergy() < entity.getMaxEnergy() * 0.7 && bestPreyTarget != null) {
            double preyDistance = calculateDistance(entity, bestPreyTarget);
            
            // If prey is within good hunting range
            if (preyDistance <= entity.getVisionRange() * huntingProficiency) {
                int dx = bestPreyTarget.getX() - entity.getX();
                int dy = bestPreyTarget.getY() - entity.getY();
                
                // Normalize direction
                int moveX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
                int moveY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
                
                // If adjacent to prey, attack
                boolean shouldEat = preyDistance <= 1.5;
                boolean shouldAttack = shouldEat;
                
                if (shouldAttack) {
                    attackCooldown = ATTACK_COOLDOWN_MAX;
                }
                
                // Override with hunting behavior
                decision = new BrainDecision(moveX, moveY, shouldEat, false, shouldAttack);
            }
        }
        
        // Territorial behavior - prefer to stay in territory when not hunting
        if (bestPreyTarget == null && !isWithinTerritory(entity.getX(), entity.getY()) && 
            territorialism > 0.7 && entity.getEnergy() > entity.getMaxEnergy() * 0.3) {
            
            // Move toward territory center
            int dx = territoryCenter[0] - entity.getX();
            int dy = territoryCenter[1] - entity.getY();
            
            // Only override if significant distance from territory
            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                // Normalize direction
                int moveX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
                int moveY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
                
                // Return to territory behavior
                decision = new BrainDecision(moveX, moveY, false, false, false);
            }
        }
        
        // Learning from hunting results
        double energyChange = entity.getEnergy() - previousEnergy;
        
        // If energy increased significantly, successful hunt occurred
        if (energyChange > 8.0) {
            huntingExperience++;
            failedHuntCounter = 0;
            
            // Remember successful hunt location
            int[] location = new int[]{entity.getX(), entity.getY()};
            successfulHuntLocations.add(location);
            if (successfulHuntLocations.size() > 5) {
                successfulHuntLocations.remove(0); // Keep list manageable
            }
            
            // Learning: Successful hunt improves hunting proficiency
            if (Math.random() < learningRate) {
                huntingProficiency = Math.min(2.0, huntingProficiency + 0.03);
                System.out.println("Apex predator improved hunting to: " + huntingProficiency);
            }
        }
        // Track failed hunts (significant energy loss during hunting)
        else if (energyChange < -3.0 && bestPreyTarget != null) {
            failedHuntCounter++;
            
            // Too many consecutive failed hunts reduces aggression
            if (failedHuntCounter > 3 && Math.random() < learningRate * 0.5) {
                aggressionLevel = Math.max(0.7, aggressionLevel - 0.05);
            }
        }
        
        // Territory adjustment based on movement and success
        // If moving consistently away from territory center, gradually shift territory
        if (Math.abs(entity.getX() - previousX) > 0 || Math.abs(entity.getY() - previousY) > 0) {
            double distanceFromTerritory = calculateDistance(
                entity.getX(), entity.getY(), territoryCenter[0], territoryCenter[1]);
            
            // If consistently successful in a new area, gradually shift territory
            if (distanceFromTerritory > territoryRadius * 0.7 && huntingExperience > 5 && 
                successfulHuntLocations.size() >= 3) {
                
                // Calculate average position of recent successful hunts
                int sumX = 0, sumY = 0;
                for (int[] loc : successfulHuntLocations) {
                    sumX += loc[0];
                    sumY += loc[1];
                }
                int avgX = sumX / successfulHuntLocations.size();
                int avgY = sumY / successfulHuntLocations.size();
                
                // Gradually shift territory center toward successful hunting grounds
                if (Math.random() < learningRate * 0.3) {
                    territoryCenter[0] = (int)(territoryCenter[0] * 0.8 + avgX * 0.2);
                    territoryCenter[1] = (int)(territoryCenter[1] * 0.8 + avgY * 0.2);
                    System.out.println("Apex predator shifted territory to: " + 
                                     territoryCenter[0] + "," + territoryCenter[1]);
                }
            }
        }
        
        return decision;
    }
    
    /**
     * Find the optimal prey to hunt based on distance, vulnerability, and size.
     */
    private Entity findOptimalPrey(Entity entity, EntityManager entityManager) {
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), (int)(entity.getVisionRange() * huntingProficiency));
        
        Entity bestTarget = null;
        double bestTargetScore = -1;
        
        for (Entity other : nearbyEntities) {
            if (!other.isAlive() || other == entity) continue;
            
            // Apex predators can hunt most other animals (except other apex predators)
            boolean isPotentialPrey = other.getSpeciesType() == SpeciesType.HERBIVORE || 
                                   other.getSpeciesType() == SpeciesType.OMNIVORE ||
                                   other.getSpeciesType() == SpeciesType.SCAVENGER ||
                                   (other.getSpeciesType() == SpeciesType.CARNIVORE && 
                                    other.getHealth() < entity.getHealth());
            
            if (isPotentialPrey) {
                double distance = calculateDistance(entity, other);
                
                // Calculate prey attractiveness score
                double healthFactor = 1.0 - (other.getHealth() / other.getMaxHealth());
                double distanceFactor = 1.0 - (distance / (entity.getVisionRange() * huntingProficiency));
                
                // Energy yield factor - bigger prey = more energy
                double energyFactor = other.getMaxEnergy() / 150.0; // Normalize to ~0-1
                
                // Calculate overall score
                double preyScore = (healthFactor * 0.4) + (distanceFactor * 0.4) + (energyFactor * 0.2);
                
                // Apply hunting experience bonus for familiar prey types
                if (huntingExperience > 3 && 
                    (other.getSpeciesType() == SpeciesType.HERBIVORE || 
                     other.getSpeciesType() == SpeciesType.CARNIVORE)) {
                    preyScore *= 1.0 + (Math.min(10, huntingExperience) * 0.02);
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
     * Find the nearest rival predator (other apex predators or strong carnivores).
     */
    private Entity findNearestRival(Entity entity, EntityManager entityManager) {
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(
                entity.getX(), entity.getY(), (int)entity.getVisionRange());
        
        Entity closestRival = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Entity other : nearbyEntities) {
            if (!other.isAlive() || other == entity) continue;
            
            boolean isRival = other.getSpeciesType() == SpeciesType.APEX_PREDATOR || 
                          (other.getSpeciesType() == SpeciesType.CARNIVORE && 
                           other.getHealth() > entity.getHealth() * 0.8);
            
            if (isRival) {
                double distance = calculateDistance(entity, other);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestRival = other;
                }
            }
        }
        
        return closestRival;
    }
    
    /**
     * Check if a position is within the apex predator's territory.
     */
    private boolean isWithinTerritory(int x, int y) {
        if (territoryCenter == null) return false;
        
        double distance = calculateDistance(x, y, territoryCenter[0], territoryCenter[1]);
        return distance <= territoryRadius;
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
     * Gather enhanced inputs specific to apex predators.
     */
    private double[] gatherApexPredatorInputs(Entity entity, World world, EntityManager entityManager) {
        double[] inputs = new double[APEX_INPUT_SIZE];
        
        // Base inputs would be populated here
        
        // Calculate territory awareness
        if (territoryCenter != null) {
            double distanceFromCenter = calculateDistance(
                entity.getX(), entity.getY(), territoryCenter[0], territoryCenter[1]);
            inputs[TERRITORY_AWARENESS_INPUT] = 1.0 - Math.min(1.0, distanceFromCenter / territoryRadius);
        } else {
            inputs[TERRITORY_AWARENESS_INPUT] = 0.0;
        }
        
        // Enhanced prey vulnerability detection
        Entity bestPrey = findOptimalPrey(entity, entityManager);
        if (bestPrey != null) {
            inputs[PREY_VULNERABILITY_INPUT] = 1.0 - (bestPrey.getHealth() / bestPrey.getMaxHealth());
        } else {
            inputs[PREY_VULNERABILITY_INPUT] = 0.0;
        }
        
        // Stamina awareness (based on recent attacks and energy cost)
        inputs[STAMINA_INPUT] = attackCooldown > 0 ? 0.0 : 1.0;
        
        // Rival predator awareness
        Entity nearestRival = findNearestRival(entity, entityManager);
        if (nearestRival != null) {
            double distance = calculateDistance(entity, nearestRival);
            double normalizedDistance = Math.max(0, 1.0 - (distance / entity.getVisionRange()));
            inputs[RIVAL_AWARENESS_INPUT] = normalizedDistance;
        } else {
            inputs[RIVAL_AWARENESS_INPUT] = 0.0;
        }
        
        // Hunting experience
        inputs[HUNTING_EXPERIENCE_INPUT] = Math.min(1.0, huntingExperience / 15.0);
        
        return inputs;
    }
    
    /**
     * Mutate a value within bounds with a chance.
     */
    private double mutateValue(double value, double mutationChance, double min, double max) {
        if (Math.random() < mutationChance) {
            value += (Math.random() * 0.2) - 0.1; // ±0.1
            return Math.max(min, Math.min(max, value));
        }
        return value;
    }
    
    @Override
    public ApexPredatorBrain createChild() {
        return new ApexPredatorBrain(this);
    }
} 
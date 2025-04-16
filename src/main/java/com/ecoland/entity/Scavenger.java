package com.ecoland.entity;

import com.ecoland.ai.Pathfinder;
import com.ecoland.ai.nn.AnimalBrain;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.model.TerrainType;
import com.ecoland.simulation.Simulation;
import com.ecoland.simulation.EntityManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Scavenger class representing animals that feed on dead bodies.
 * They have enhanced senses for detecting dead entities.
 */
public class Scavenger extends Entity {

    // Constants
    private static final double BASE_ENERGY_DEPLETION = 0.08; // Lower energy cost
    private static final double MOVE_ENERGY_COST_FACTOR = 0.04; // Lower movement cost
    private static final double EAT_ENERGY_GAIN_FACTOR = 0.7; // Energy gained from dead bodies
    private static final double HUNGER_THRESHOLD_FACTOR = 0.4; // Lower hunger threshold
    private static final double DETECTION_RANGE_MULTIPLIER = 1.5; // Better at detecting dead bodies
    private static final double PREDATOR_DETECTION_RANGE_FACTOR = 1.0; // Standard detection range for predators
    private static final double FLEE_SPEED_BOOST = 1.2; // Good at fleeing
    
    private static final Random random = new Random();
    
    // State and Pathfinding
    private enum State { IDLE, WANDERING, SEEKING_FOOD, FOLLOWING_PATH, EATING, REPRODUCING, FLEEING, NEURAL }
    private State currentState = State.IDLE;
    private int[] targetCoords = null;
    private Entity targetDeadBody = null;
    private LinkedList<int[]> currentPath = null;
    private final Pathfinder pathfinder = new Pathfinder();
    private int pathRepathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;
    
    // Flag for neural network behavior
    private boolean useNeuralBehavior = true;
    
    // Movement Accumulator for Speed Gene
    private double moveAccumulator = 0.0;
    
    /** Constructor for initial placement */
    public Scavenger(int x, int y) {
        super(x, y, SpeciesType.SCAVENGER);
    }
    
    /** Constructor for offspring */
    public Scavenger(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.SCAVENGER, new Genes(parentGenes));
    }
    
    /** Constructor for offspring with inherited brain */
    public Scavenger(int x, int y, Genes parentGenes, AnimalBrain parentBrain) {
        super(x, y, SpeciesType.SCAVENGER, new Genes(parentGenes), parentBrain);
    }
    
    @Override
    public void update(Simulation simulation, World world) {
        if (!isAlive) return;
        
        // Base energy depletion
        depleteEnergy(BASE_ENERGY_DEPLETION);
        if (!isAlive) return;
        
        // Choose between neural behavior and traditional behavior
        if (useNeuralBehavior && brain != null) {
            updateNeuralBehavior(simulation, world);
        } else {
            updateTraditionalBehavior(simulation, world);
        }
    }
    
    /**
     * Neural network-based behavior update.
     */
    private void updateNeuralBehavior(Simulation simulation, World world) {
        // Get decision from the brain
        AnimalBrain.BrainDecision decision = brain.makeDecision(this, world, simulation.getEntityManager());
        
        // Apply the decision
        
        // 1. Movement
        if (decision.moveX != 0 || decision.moveY != 0) {
            moveAccumulator += getSpeed();
            
            while (moveAccumulator >= 1.0) {
                boolean moved = moveBy(simulation, decision.moveX, decision.moveY, world);
                
                if (!moved) {
                    // Try alternative directions if blocked
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            if (dx == decision.moveX && dy == decision.moveY) continue;
                            
                            if (moveBy(simulation, dx, dy, world)) {
                                moved = true;
                                break;
                            }
                        }
                        if (moved) break;
                    }
                }
                
                moveAccumulator -= 1.0;
            }
        }
        
        // 2. Eating
        if (decision.eat) {
            // Find dead bodies at current location
            List<Entity> nearbyDeadBodies = findNearbyDeadBodies(simulation);
            if (!nearbyDeadBodies.isEmpty()) {
                eatDeadBody(nearbyDeadBodies.get(0), simulation);
            }
        }
        
        // 3. Reproduction
        if (decision.reproduce && energy >= getReproductionThreshold()) {
            reproduce(simulation);
        }
    }
    
    /**
     * Traditional rule-based behavior update.
     */
    private void updateTraditionalBehavior(Simulation simulation, World world) {
        // Check for predators first (survival priority)
        if (checkForPredators(simulation, world)) {
            return; // Already took flee action
        }
        
        // Path validation
        validateTargetPath(world);
        validateTargetDeadBody(simulation);
        
        // If energy is high enough, try to reproduce
        if (energy > genes.reproductionThreshold) {
            boolean reproduced = tryReproduce(simulation);
            if (reproduced) {
                currentState = State.SEEKING_FOOD;
                return;
            }
        }
        
        // Main state machine for behavior
        switch (currentState) {
            case IDLE:
                // Transition to wandering or seeking food
                if (random.nextDouble() < 0.3) {
                    currentState = State.WANDERING;
                } else {
                    currentState = State.SEEKING_FOOD;
                }
                break;
                
            case WANDERING:
                // Random movement with chance to start seeking food
                wander(simulation, world);
                if (random.nextDouble() < 0.2 || energy < genes.maxEnergy * HUNGER_THRESHOLD_FACTOR) {
                    currentState = State.SEEKING_FOOD;
                }
                break;
                
            case SEEKING_FOOD:
                // Look for dead bodies
                boolean foundTarget = findAndTargetDeadBodies(simulation, world);
                if (foundTarget) {
                    currentState = State.FOLLOWING_PATH;
                } else if (random.nextDouble() < 0.1) {
                    currentState = State.WANDERING;
                }
                break;
                
            case FOLLOWING_PATH:
                if (targetCoords == null || currentPath == null || currentPath.isEmpty()) {
                    currentState = State.SEEKING_FOOD;
                    break;
                }
                
                // Check if we've reached the target dead body
                if (x == targetCoords[0] && y == targetCoords[1] && targetDeadBody != null) {
                    eatDeadBody(targetDeadBody, simulation);
                    currentState = State.EATING;
                    break;
                }
                
                // Follow current path to dead body
                followPath(simulation, world);
                break;
                
            case EATING:
                // After eating, go back to seeking food
                if (random.nextDouble() < 0.5) {
                    currentState = State.SEEKING_FOOD;
                }
                break;
                
            case FLEEING:
                // Continue fleeing for a few more ticks
                if (random.nextDouble() < 0.2) {
                    currentState = State.SEEKING_FOOD;
                } else {
                    flee(simulation, world);
                }
                break;
                
            default:
                currentState = State.IDLE;
                break;
        }
    }
    
    /**
     * Find nearby dead bodies at the current location.
     */
    private List<Entity> findNearbyDeadBodies(Simulation simulation) {
        List<Entity> nearbyEntities = simulation.getEntityManager().getAllEntities();
        List<Entity> deadBodies = new ArrayList<>();
        
        for (Entity entity : nearbyEntities) {
            if (entity.isDeadBody() && entity.getX() == x && entity.getY() == y) {
                deadBodies.add(entity);
            }
        }
        
        return deadBodies;
    }
    
    /**
     * Find and target dead bodies in vision range.
     */
    private boolean findAndTargetDeadBodies(Simulation simulation, World world) {
        // Calculate effective vision range (enhanced for detecting dead bodies)
        double effectiveVisionRange = getVisionRange() * DETECTION_RANGE_MULTIPLIER;
        
        // Find all dead bodies in range, sorted by proximity (closest first)
        List<Entity> deadBodies = simulation.getEntityManager().findDeadBodiesInRange(
                x, y, effectiveVisionRange, world);
        
        if (deadBodies.isEmpty()) {
            return false;
        }
        
        // Target the closest dead body (first in the list)
        Entity closestDeadBody = deadBodies.get(0);
        targetDeadBody = closestDeadBody;
        targetCoords = new int[]{closestDeadBody.getX(), closestDeadBody.getY()};
        
        // Calculate path to target
        return calculatePath(simulation, world, targetCoords[0], targetCoords[1]);
    }
    
    /**
     * Eat a dead body, gaining energy and marking it as decomposed.
     */
    private void eatDeadBody(Entity deadBody, Simulation simulation) {
        if (deadBody == null) {
            System.err.println("Cannot eat null dead body");
            return;
        }
        
        if (!deadBody.isDeadBody()) {
            System.err.println("Cannot eat entity that is not a dead body: " + deadBody.getSpeciesType() + 
                               " at (" + deadBody.getX() + ", " + deadBody.getY() + ")");
            return;
        }
        
        // Gain energy from the dead body
        double energyGain = deadBody.getDeadBodyNutritionValue() * EAT_ENERGY_GAIN_FACTOR;
        gainEnergy(energyGain);
        
        // Log the scavenging action
        System.out.println("Scavenger at (" + x + ", " + y + ") ate dead " + 
                           deadBody.getSpeciesType() + " body, gained " + energyGain + " energy");
        
        // Mark the dead body as decomposed
        deadBody.setDecomposed();
        
        // Request removal of the now decomposed entity
        simulation.getEntityManager().removeEntity(deadBody);
        
        // Clear target references
        targetDeadBody = null;
        targetCoords = null;
        clearPath();
    }
    
    /**
     * Calculate a path to the target coordinates.
     */
    private boolean calculatePath(Simulation simulation, World world, int targetX, int targetY) {
        // Clear any existing path
        clearPath();
        
        // Reset path finding attempts
        pathRepathAttempts = 0;
        
        // Find path using A* algorithm
        List<int[]> path = pathfinder.findPath(world, x, y, targetX, targetY, this);
        
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // Convert to LinkedList
        currentPath = new LinkedList<>(path);
        
        // Remove the first node which is our current position
        if (!currentPath.isEmpty()) {
            currentPath.removeFirst();
        }
        
        return !currentPath.isEmpty();
    }
    
    /**
     * Follow the current calculated path.
     */
    private void followPath(Simulation simulation, World world) {
        if (currentPath == null || currentPath.isEmpty()) {
            currentState = State.SEEKING_FOOD;
            return;
        }
        
        int[] nextNode = currentPath.getFirst();
        int nextX = nextNode[0];
        int nextY = nextNode[1];
        
        // Check if the next node is occupied
        if (simulation.getEntityManager().isTileOccupiedByOther(nextX, nextY, this)) {
            // Try to find a new path
            if (pathRepathAttempts < MAX_REPATH_ATTEMPTS) {
                pathRepathAttempts++;
                calculatePath(simulation, world, targetCoords[0], targetCoords[1]);
            } else {
                // If we've tried too many times, give up
                clearPath();
                currentState = State.SEEKING_FOOD;
            }
            return;
        }
        
        // Calculate movement direction
        int dx = Integer.compare(nextX, x);
        int dy = Integer.compare(nextY, y);
        
        // Try to move
        moveAccumulator += getSpeed();
        
        while (moveAccumulator >= 1.0 && !currentPath.isEmpty()) {
            boolean moved = moveBy(simulation, dx, dy, world);
            
            if (moved) {
                // Remove the node we just moved to
                currentPath.removeFirst();
                
                // If there are more nodes, prepare for the next one
                if (!currentPath.isEmpty()) {
                    nextNode = currentPath.getFirst();
                    nextX = nextNode[0];
                    nextY = nextNode[1];
                    dx = Integer.compare(nextX, x);
                    dy = Integer.compare(nextY, y);
                }
            } else {
                // If we couldn't move, try to find a new path
                if (pathRepathAttempts < MAX_REPATH_ATTEMPTS) {
                    pathRepathAttempts++;
                    calculatePath(simulation, world, targetCoords[0], targetCoords[1]);
                } else {
                    // If we've tried too many times, give up
                    clearPath();
                    currentState = State.SEEKING_FOOD;
                }
                break;
            }
            
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Clear the current path.
     */
    private void clearPath() {
        if (currentPath != null) {
            currentPath.clear();
        }
        currentPath = null;
    }
    
    /**
     * Validate that the target path is still valid.
     */
    private void validateTargetPath(World world) {
        if (targetCoords != null && !world.isValidCoordinate(targetCoords[0], targetCoords[1])) {
            targetCoords = null;
            clearPath();
        }
    }
    
    /**
     * Validate that the target dead body is still valid.
     */
    private void validateTargetDeadBody(Simulation simulation) {
        if (targetDeadBody != null && !targetDeadBody.isDeadBody()) {
            targetDeadBody = null;
            targetCoords = null;
            clearPath();
        }
    }
    
    /**
     * Wander randomly around the environment.
     */
    private void wander(Simulation simulation, World world) {
        // Random direction
        int dx = random.nextInt(3) - 1; // -1, 0, or 1
        int dy = random.nextInt(3) - 1; // -1, 0, or 1
        
        // Try to move in that direction
        moveAccumulator += getSpeed();
        
        while (moveAccumulator >= 1.0) {
            moveBy(simulation, dx, dy, world);
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Try to reproduce if energy is sufficient.
     */
    private boolean tryReproduce(Simulation simulation) {
        if (energy < genes.reproductionThreshold) {
            return false;
        }
        
        // Deplete reproduction cost
        energy -= genes.reproductionCost;
        
        // Let the simulation handle spawning the offspring
        simulation.spawnOffspring(this, SpeciesType.SCAVENGER, genes);
        
        return true;
    }
    
    /**
     * Move in the specified direction.
     */
    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        int newX = x + dx;
        int newY = y + dy;
        
        // Check if the new position is valid
        if (!world.isValidCoordinate(newX, newY)) {
            return false;
        }
        
        // Check if the tile is water (can't move into water)
        Tile tile = world.getTile(newX, newY);
        if (tile.getTerrainType() == TerrainType.WATER) {
            return false;
        }
        
        // Check if the tile is occupied by another entity
        if (simulation.getEntityManager().isTileOccupiedByOther(newX, newY, this)) {
            return false;
        }
        
        // Move to the new position
        x = newX;
        y = newY;
        
        // Deplete energy based on movement cost
        depleteEnergy(MOVE_ENERGY_COST_FACTOR);
        
        return true;
    }
    
    /**
     * Check for nearby predators and flee if needed.
     * Scavengers are more intelligent about which predators to flee from,
     * considering the predator's energy level (hungry predators are more dangerous).
     */
    private boolean checkForPredators(Simulation simulation, World world) {
        double predatorDetectionRange = getVisionRange() * PREDATOR_DETECTION_RANGE_FACTOR;
        Entity nearestDangerousPredator = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Entity entity : simulation.getEntityManager().findEntitiesInRange(x, y, predatorDetectionRange, world)) {
            if (entity.isAlive() && 
                (entity.getSpeciesType() == SpeciesType.CARNIVORE || 
                 entity.getSpeciesType() == SpeciesType.APEX_PREDATOR)) {
                
                // Calculate if the predator is dangerous (hungry)
                // Predators with more than 70% energy are less likely to chase prey
                boolean isPredatorHungry = entity.getEnergy() < (entity.getMaxEnergy() * 0.7);
                
                double dx = entity.getX() - x;
                double dy = entity.getY() - y;
                double distance = Math.sqrt(dx*dx + dy*dy);
                
                // Always flee from very close predators regardless of hunger
                boolean isTooClose = distance < 3.0;
                
                if ((isPredatorHungry || isTooClose) && distance < minDistance) {
                    minDistance = distance;
                    nearestDangerousPredator = entity;
                }
            }
        }
        
        if (nearestDangerousPredator != null) {
            // Set state to fleeing
            currentState = State.FLEEING;
            // Clear path and targets
            clearPath();
            targetCoords = null;
            targetDeadBody = null;
            
            // Flee from predator
            flee(simulation, world, nearestDangerousPredator);
            return true;
        }
        
        return false;
    }
    
    /**
     * Flee from a predator.
     */
    private void flee(Simulation simulation, World world) {
        // Find nearest predator to flee from
        Entity nearestPredator = findNearestPredator(simulation, world);
        if (nearestPredator != null) {
            flee(simulation, world, nearestPredator);
        } else {
            // If no predator found, just wander
            wander(simulation, world);
        }
    }
    
    /**
     * Find the nearest predator in vision range.
     */
    private Entity findNearestPredator(Simulation simulation, World world) {
        double effectiveVisionRange = getVisionRange() * PREDATOR_DETECTION_RANGE_FACTOR;
        List<Entity> nearbyEntities = simulation.getEntityManager().getEntitiesInRange(
                x, y, (int)effectiveVisionRange);
        
        Entity nearestPredator = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Entity entity : nearbyEntities) {
            if (!entity.isAlive() || entity == this) continue;
            
            if (entity.getSpeciesType() == SpeciesType.CARNIVORE || 
                entity.getSpeciesType() == SpeciesType.APEX_PREDATOR) {
                
                double dx = entity.getX() - x;
                double dy = entity.getY() - y;
                double distanceSq = dx*dx + dy*dy;
                
                if (distanceSq < closestDistance) {
                    closestDistance = distanceSq;
                    nearestPredator = entity;
                }
            }
        }
        
        return nearestPredator;
    }
    
    /**
     * Toggle neural network behavior.
     */
    public void setUseNeuralBehavior(boolean useNeural) {
        this.useNeuralBehavior = useNeural;
    }

    /**
     * Try to reproduce if conditions are right.
     * @param simulation The simulation instance
     * @return true if reproduction was successful
     */
    private boolean reproduce(Simulation simulation) {
        return tryReproduce(simulation);
    }

    /**
     * Flee from a predator.
     */
    private void flee(Simulation simulation, World world, Entity predator) {
        flee(simulation, world);
    }
} 
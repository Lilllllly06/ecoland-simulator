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

public class Carnivore extends Entity {

    // Constants
    private static final double BASE_ENERGY_DEPLETION = 0.15; // Slightly higher baseline cost than herbivores
    private static final double MOVE_ENERGY_COST_FACTOR = 0.08; // Higher move cost than herbivores
    private static final double ATTACK_ENERGY_COST = 2.0;
    private static final double ATTACK_DAMAGE = 30.0;
    private static final double EAT_ENERGY_GAIN = 25.0; // Higher energy gain from meat
    private static final double HUNGER_THRESHOLD_FACTOR = 0.6; // Start hunting below this energy percentage
    private static final double ATTACK_RANGE = 1.5; // Range for attacking prey
    
    // Constants for fleeing behavior
    private static final double HEALTH_FLEE_THRESHOLD = 0.3; // Flee when health below 30% of max
    private static final double THREAT_RANGE_FACTOR = 0.8; // Detect threats at 80% of vision range
    private static final int FLEE_DISTANCE = 8; // Target distance to flee in tiles
    private static final double FLEE_SPEED_BOOST = 1.1; // Speed boost when fleeing
    private static final double THREAT_POWER_THRESHOLD = 1.5; // Flee when threat is 1.5x stronger

    private static final Random random = new Random();

    // State and Pathfinding
    private enum State { IDLE, WANDERING, HUNTING, FOLLOWING_PATH, ATTACKING, REPRODUCING, FLEEING, NEURAL }
    private State currentState = State.IDLE;
    private int[] targetCoords = null;
    private Entity targetPrey = null;
    private LinkedList<int[]> currentPath = null;
    private final Pathfinder pathfinder = new Pathfinder();
    private int pathRepathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;
    
    // Flag to use neural network or traditional behavior
    private boolean useNeuralBehavior = true;

    // Movement Accumulator for Speed Gene
    private double moveAccumulator = 0.0;

    /** Constructor for initial placement */
    public Carnivore(int x, int y) {
        super(x, y, SpeciesType.CARNIVORE);
    }

    /** Constructor for offspring */
    public Carnivore(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.CARNIVORE, new Genes(parentGenes));
    }
    
    /** Constructor for offspring with inherited brain */
    public Carnivore(int x, int y, Genes parentGenes, AnimalBrain parentBrain) {
        super(x, y, SpeciesType.CARNIVORE, new Genes(parentGenes), parentBrain);
    }

    @Override
    public void update(Simulation simulation, World world) {
        if (!isAlive) return;

        // Base energy depletion (slightly higher for carnivores)
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
        
        // 2. Attack (if prey nearby)
        if (decision.attack) {
            EntityManager entityManager = simulation.getEntityManager();
            List<Entity> nearbyEntities = entityManager.getEntitiesInRange(x, y, 1); // Range 1 for adjacent tiles
            
            Entity bestPrey = null;
            double lowestHealth = Double.MAX_VALUE;
            
            // Find the weakest prey in attack range
            for (Entity entity : nearbyEntities) {
                if (entity.getSpeciesType() == SpeciesType.HERBIVORE && entity.isAlive()) {
                    if (entity.getHealth() < lowestHealth) {
                        lowestHealth = entity.getHealth();
                        bestPrey = entity;
                    }
                }
            }
            
            // Attack if prey found
            if (bestPrey != null) {
                attackEntity(bestPrey);
                
                // If prey dies, consume it
                if (!bestPrey.isAlive()) {
                    eatPrey(bestPrey);
                }
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
        // Path validation
        validateTargetPath(world);
        validateTargetPrey(simulation);

        // Decide current action
        decideState(simulation, world);

        // Execute action based on state
        switch (currentState) {
            case HUNTING:
                // Hunting just sets up the target and path, following handles movement
                findAndSetTarget(simulation, world);
                // If path found, immediately try to follow it this tick
                if (currentState == State.FOLLOWING_PATH) {
                    followPath(simulation, world);
                }
                break;
            case FOLLOWING_PATH:
                followPath(simulation, world);
                break;
            case ATTACKING:
                attackEntity(targetPrey);
                if (targetPrey != null && !targetPrey.isAlive()) { // If prey dies from this attack
                    eatPrey(targetPrey);
                    // After eating, re-evaluate state immediately
                    targetPrey = null;
                    decideState(simulation, world);
                    if (currentState == State.WANDERING) wander(simulation, world); // Execute wander if decided
                }
                break;
            case REPRODUCING:
                reproduce(simulation);
                // After reproducing, re-evaluate state immediately (might wander or hunt)
                decideState(simulation, world);
                if (currentState == State.WANDERING) wander(simulation, world); // Execute wander if decided
                break;
            case FLEEING:
                flee(simulation, world);
                break;
            case WANDERING:
            default: // Includes IDLE, which defaults to wandering
                wander(simulation, world);
                break;
        }
    }
    
    /**
     * Toggle between neural and traditional behavior.
     */
    public void toggleNeuralBehavior() {
        useNeuralBehavior = !useNeuralBehavior;
    }
    
    /**
     * Set whether to use neural behavior.
     */
    public void setUseNeuralBehavior(boolean useNeural) {
        useNeuralBehavior = useNeural;
    }
    
    /**
     * Get whether neural behavior is active.
     */
    public boolean isUsingNeuralBehavior() {
        return useNeuralBehavior;
    }
    
    /**
     * Validate if the target prey is still valid
     */
    private void validateTargetPrey(Simulation simulation) {
        if (targetPrey != null) {
            // Check if prey is still alive and exists in the simulation
            if (!targetPrey.isAlive() || 
                !simulation.getEntityManager().getAllEntities().contains(targetPrey)) {
                targetPrey = null;
                currentState = State.IDLE;
            }
        }
    }
    
    /**
     * Attack a prey entity
     */
    private void attackEntity(Entity prey) {
        if (prey != null && prey.isAlive()) {
            // Apply damage to prey
            prey.takeDamage(ATTACK_DAMAGE);
            
            // Use some energy to attack
            depleteEnergy(ATTACK_ENERGY_COST);
        }
    }
    
    /**
     * Eat a prey that was killed
     */
    private void eatPrey(Entity prey) {
        if (prey != null && !prey.isAlive()) {
            // Gain energy based on prey's max energy
            gainEnergy(prey.getMaxEnergy() * 0.8); // Gain 80% of prey's max energy
        }
    }
    
    /**
     * Check if prey is within attack range
     */
    private boolean isInAttackRange(Entity prey) {
        if (prey == null) return false;
        
        double dx = prey.getX() - x;
        double dy = prey.getY() - y;
        double distSq = dx*dx + dy*dy;
        
        return distSq <= ATTACK_RANGE * ATTACK_RANGE;
    }
    
    /**
     * Flee from a threat
     */
    private void flee(Simulation simulation, World world) {
        // Similar implementation to Herbivore's flee method
        EntityManager entityManager = simulation.getEntityManager();
        double currentVisionRange = getVisionRange();
        List<Entity> threats = entityManager.findEntitiesInRange(
                x, y, currentVisionRange * THREAT_RANGE_FACTOR, SpeciesType.CARNIVORE, world);
                
        // Filter threats by removing self
        threats.removeIf(e -> e == this);
        
        if (threats.isEmpty()) {
            return;
        }
        
        // Calculate average threat location
        double avgThreatX = 0, avgThreatY = 0;
        for (Entity threat : threats) {
            avgThreatX += threat.getX();
            avgThreatY += threat.getY();
        }
        avgThreatX /= threats.size();
        avgThreatY /= threats.size();
        
        // Calculate flee direction (away from average threat location)
        double fleeVectorX = x - avgThreatX;
        double fleeVectorY = y - avgThreatY;
        double magnitude = Math.sqrt(fleeVectorX * fleeVectorX + fleeVectorY * fleeVectorY);
        
        // Normalize flee vector to get direction (dx, dy)
        int dx = 0, dy = 0;
        if (magnitude > 0.1) {
            dx = (int) Math.round(fleeVectorX / magnitude);
            dy = (int) Math.round(fleeVectorY / magnitude);
        } else { // If threat is very close, choose random direction away
            dx = random.nextInt(3) - 1;
            dy = random.nextInt(3) - 1;
        }
        
        // Ensure movement if direction is (0,0)
        if (dx == 0 && dy == 0) dx = (random.nextBoolean() ? 1 : -1);
        
        // Speed boost when fleeing
        double effectiveSpeed = getSpeed() * FLEE_SPEED_BOOST;
        moveAccumulator += effectiveSpeed;
        
        while (moveAccumulator >= 1.0) {
            boolean moved = moveBy(simulation, dx, dy, world, FLEE_SPEED_BOOST);
            
            if (!moved) {
                // Try alternative directions if blocked
                int rdx = dx;
                int rdy = dy;
                for (int i = 0; i < 5; i++) {
                    if (i%2 == 0) rdx = (rdx + (random.nextBoolean()?1:-1) + 3) % 3 - 1;
                    else rdy = (rdy + (random.nextBoolean()?1:-1) + 3) % 3 - 1;
                    if (rdx == 0 && rdy == 0) continue;
                    
                    if (moveBy(simulation, rdx, rdy, world, FLEE_SPEED_BOOST)) {
                        moved = true;
                        break;
                    }
                }
                
                if (!moved) {
                    // If still couldn't move, stop trying this tick
                    moveAccumulator = 0;
                    break;
                }
            }
            
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Find and set a target for hunting.
     */
    private void findAndSetTarget(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double currentVisionRange = getVisionRange();
        
        // Find nearby prey (herbivores)
        List<Entity> nearbyPrey = entityManager.findEntitiesInRange(
                x, y, currentVisionRange, SpeciesType.HERBIVORE, world);
        
        // Find the best prey target based on health or distance
        Entity bestPrey = null;
        double bestScore = Double.MAX_VALUE; // Lower is better
        
        for (Entity prey : nearbyPrey) {
            if (!prey.isAlive()) continue;
            
            double distance = Math.sqrt(Math.pow(prey.getX() - x, 2) + Math.pow(prey.getY() - y, 2));
            double healthRatio = prey.getHealth() / prey.getMaxHealth();
            
            // Score calculation: combines distance and health
            // Prefer closer and weaker prey
            double score = distance * 0.8 + healthRatio * currentVisionRange * 0.2;
            
            if (score < bestScore) {
                bestScore = score;
                bestPrey = prey;
            }
        }
        
        if (bestPrey != null) {
            targetPrey = bestPrey;
            
            // If prey is in attack range, switch to attacking
            if (isInAttackRange(targetPrey)) {
                currentState = State.ATTACKING;
            } else {
                // Otherwise, pathfind to the prey
                targetCoords = new int[]{targetPrey.getX(), targetPrey.getY()};
                pathRepathAttempts = 0;
                
                if (calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                    currentState = State.FOLLOWING_PATH;
                } else {
                    // If pathing fails, wander
                    targetPrey = null;
                    targetCoords = null;
                    currentState = State.WANDERING;
                }
            }
        } else {
            // No prey found, wander
            targetPrey = null;
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Move the carnivore along a calculated path.
     */
    private void followPath(Simulation simulation, World world) {
        if (currentPath == null || currentPath.isEmpty()) {
            return;
        }
        
        // If the target is a prey entity, update the target coordinates
        if (targetPrey != null && targetPrey.isAlive()) {
            int preyX = targetPrey.getX();
            int preyY = targetPrey.getY();
            
            // If prey moved, recalculate path
            if (targetCoords[0] != preyX || targetCoords[1] != preyY) {
                targetCoords[0] = preyX;
                targetCoords[1] = preyY;
                
                // If in attack range, switch to attacking
                if (isInAttackRange(targetPrey)) {
                    currentState = State.ATTACKING;
                    return;
                }
                
                // Otherwise recalculate path if prey moved significantly
                double distance = Math.sqrt(Math.pow(preyX - x, 2) + Math.pow(preyY - y, 2));
                if (distance > 2) {
                    calculatePath(simulation, world, preyX, preyY);
                }
            }
        }
        
        // Speed accumulator for following path
        moveAccumulator += getSpeed();
        
        while (moveAccumulator >= 1.0 && currentPath != null && !currentPath.isEmpty()) {
            int[] nextStep = currentPath.peek();
            int targetX = nextStep[0];
            int targetY = nextStep[1];
            
            // Calculate direction
            int dx = Integer.compare(targetX, x);
            int dy = Integer.compare(targetY, y);
            
            if (dx == 0 && dy == 0) {
                currentPath.poll();
                moveAccumulator -= 1.0;
                continue;
            }
            
            boolean moved = moveBy(simulation, dx, dy, world);
            
            if (moved) {
                currentPath.poll();
                
                // Check if reached end of path
                if (currentPath.isEmpty()) {
                    targetCoords = null;
                    
                    // If we were pursuing prey, check if in attack range
                    if (targetPrey != null && targetPrey.isAlive() && isInAttackRange(targetPrey)) {
                        currentState = State.ATTACKING;
                    } else {
                        currentState = State.IDLE;
                    }
                }
            } else {
                // Path blocked, try to recalculate
                pathRepathAttempts++;
                if (targetCoords != null && pathRepathAttempts <= MAX_REPATH_ATTEMPTS) {
                    if (!calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                        clearPath();
                        currentState = State.WANDERING;
                        moveAccumulator = 0;
                        break;
                    }
                } else {
                    clearPath();
                    currentState = State.WANDERING;
                    moveAccumulator = 0;
                    break;
                }
            }
            
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Calculate a path to the target.
     */
    private boolean calculatePath(Simulation simulation, World world, int targetX, int targetY) {
        List<int[]> pathList = pathfinder.findPath(world, x, y, targetX, targetY, this);
        if (pathList != null && !pathList.isEmpty()) {
            this.currentPath = new LinkedList<>(pathList);
            return true;
        } else {
            this.currentPath = null;
            return false;
        }
    }
    
    /**
     * Clear the current path and related data.
     */
    private void clearPath() {
        this.currentPath = null;
        this.targetCoords = null;
        this.pathRepathAttempts = 0;
    }
    
    /**
     * Validate if the target path is still valid.
     */
    private void validateTargetPath(World world) {
        if (currentPath != null && !currentPath.isEmpty() && targetPrey != null) {
            // If target prey died or is too far, clear the path
            if (!targetPrey.isAlive()) {
                clearPath();
                currentState = State.IDLE;
            } else {
                // Update target coordinates to prey's current position
                targetCoords[0] = targetPrey.getX();
                targetCoords[1] = targetPrey.getY();
            }
        }
    }
    
    /**
     * Random wandering behavior.
     */
    private void wander(Simulation simulation, World world) {
        moveAccumulator += getSpeed();
        
        while (moveAccumulator >= 1.0) {
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            
            if (dx == 0 && dy == 0) {
                moveAccumulator -= 1.0;
                continue;
            }
            
            boolean moved = moveBy(simulation, dx, dy, world);
            
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Decide the carnivore's state based on its needs and surroundings.
     */
    private void decideState(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        
        // Check if currently attacking
        if (currentState == State.ATTACKING) {
            if (targetPrey == null || !targetPrey.isAlive() || !isInAttackRange(targetPrey)) {
                currentState = State.IDLE;
            } else {
                return; // Continue attacking
            }
        }
        
        // Check if should flee from stronger predators
        if (health < getMaxHealth() * HEALTH_FLEE_THRESHOLD) {
            List<Entity> threats = entityManager.findEntitiesInRange(
                    x, y, getVisionRange() * THREAT_RANGE_FACTOR, SpeciesType.CARNIVORE, world);
            
            // Remove self from threats
            threats.removeIf(e -> e == this);
            
            if (!threats.isEmpty()) {
                // Evaluate if any threats are significantly stronger
                for (Entity threat : threats) {
                    if (threat.getHealth() > health * THREAT_POWER_THRESHOLD) {
                        currentState = State.FLEEING;
                        clearPath();
                        return;
                    }
                }
            }
        }
        
        // If following path, continue if valid
        if (currentState == State.FOLLOWING_PATH) {
            if (currentPath == null || currentPath.isEmpty()) {
                currentState = State.IDLE;
            } else {
                return; // Continue following path
            }
        }
        
        // Ready to reproduce?
        if (energy >= getReproductionThreshold()) {
            currentState = State.REPRODUCING;
            clearPath();
            return;
        }
        
        // Hungry? Go hunting
        if (energy < getMaxEnergy() * HUNGER_THRESHOLD_FACTOR) {
            currentState = State.HUNTING;
            clearPath();
            return;
        }
        
        // Default to wandering
        if (currentState != State.WANDERING) {
            clearPath();
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Reproduce - create offspring with similar genes.
     */
    private void reproduce(Simulation simulation) {
        if (energy >= getReproductionThreshold()) {
            Entity offspring = simulation.spawnOffspring(this, SpeciesType.CARNIVORE, this.genes);
            if (offspring != null) {
                depleteEnergy(getReproductionCost());
            }
        }
    }
    
    /**
     * Move the carnivore by the specified delta with an optional speed multiplier.
     */
    private boolean moveBy(Simulation simulation, int dx, int dy, World world, double speedMultiplier) {
        int nextX = x + dx;
        int nextY = y + dy;
        
        if (world.isValidCoordinate(nextX, nextY)) {
            Tile targetTile = world.getTile(nextX, nextY);
            if (targetTile != null && targetTile.isPassable(this) &&
                !simulation.getEntityManager().isTileOccupiedByOther(nextX, nextY, this)) {
                
                double energyCost = MOVE_ENERGY_COST_FACTOR * getSpeed() * speedMultiplier;
                if (energy >= energyCost) {
                    depleteEnergy(energyCost);
                    setPosition(nextX, nextY);
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Move the carnivore by the specified delta with default speed.
     */
    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        return moveBy(simulation, dx, dy, world, 1.0);
    }

    // ... rest of the methods ...
} 
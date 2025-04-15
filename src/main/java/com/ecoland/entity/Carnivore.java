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
        // Simple implementation of fleeing behavior
        EntityManager entityManager = simulation.getEntityManager();
        List<Entity> threats = entityManager.findEntitiesInRange(x, y, getVisionRange(), SpeciesType.CARNIVORE, world);
        
        // Remove self from threats
        threats.removeIf(e -> e == this);
        
        if (threats.isEmpty()) {
            return;
        }
        
        // Move in random direction for now
        int dx = random.nextInt(3) - 1;
        int dy = random.nextInt(3) - 1;
        
        moveBy(simulation, dx, dy, world);
    }
    
    /**
     * Basic wander implementation
     */
    private void wander(Simulation simulation, World world) {
        moveAccumulator += getSpeed();
        
        while (moveAccumulator >= 1.0) {
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            
            moveBy(simulation, dx, dy, world);
            
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Simple find and set target implementation
     */
    private void findAndSetTarget(Simulation simulation, World world) {
        // Simplified implementation
        EntityManager entityManager = simulation.getEntityManager();
        List<Entity> prey = entityManager.findEntitiesInRange(x, y, getVisionRange(), SpeciesType.HERBIVORE, world);
        
        if (!prey.isEmpty()) {
            targetPrey = prey.get(0);
            if (isInAttackRange(targetPrey)) {
                currentState = State.ATTACKING;
            } else {
                targetCoords = new int[]{targetPrey.getX(), targetPrey.getY()};
                currentState = State.FOLLOWING_PATH;
            }
        } else {
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Simple follow path implementation
     */
    private void followPath(Simulation simulation, World world) {
        if (targetCoords == null) return;
        
        int dx = Integer.compare(targetCoords[0], x);
        int dy = Integer.compare(targetCoords[1], y);
        
        moveBy(simulation, dx, dy, world);
        
        // Check if reached target
        if (x == targetCoords[0] && y == targetCoords[1]) {
            targetCoords = null;
            currentState = State.IDLE;
        }
    }
    
    /**
     * Simple validate target path implementation
     */
    private void validateTargetPath(World world) {
        // Simplified implementation
        if (targetPrey != null && !targetPrey.isAlive()) {
            targetPrey = null;
            targetCoords = null;
            currentState = State.IDLE;
        }
    }
    
    /**
     * Simple decide state implementation
     */
    private void decideState(Simulation simulation, World world) {
        // Simplified implementation
        if (energy >= getReproductionThreshold()) {
            currentState = State.REPRODUCING;
        } else if (energy < getMaxEnergy() * HUNGER_THRESHOLD_FACTOR) {
            currentState = State.HUNTING;
        } else {
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Simple reproduce implementation
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
     * Move with speed multiplier
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
     * Move with default speed
     */
    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        return moveBy(simulation, dx, dy, world, 1.0);
    }
} 
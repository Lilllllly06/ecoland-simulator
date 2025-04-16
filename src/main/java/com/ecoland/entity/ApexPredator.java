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
 * ApexPredator represents the top predator in the ecosystem.
 * They can hunt both herbivores and carnivores, but require more energy.
 * They have enhanced hunting capabilities and higher damage output.
 */
public class ApexPredator extends Entity {

    // Constants - generally more powerful than regular carnivores
    private static final double BASE_ENERGY_DEPLETION = 0.2; // Higher base energy cost
    private static final double MOVE_ENERGY_COST_FACTOR = 0.1; // Higher movement cost
    private static final double ATTACK_ENERGY_COST = 3.0; // Higher attack cost
    private static final double ATTACK_DAMAGE = 50.0; // Higher damage
    private static final double EAT_ENERGY_GAIN_FACTOR = 0.8; // Energy gained as proportion of prey max energy
    private static final double HUNGER_THRESHOLD_FACTOR = 0.7; // Higher hunger threshold
    private static final double ATTACK_RANGE = 2.0; // Longer attack range
    private static final double TERRITORIAL_RANGE = 10.0; // Range for defending territory
    
    private static final Random random = new Random();
    
    // State and Pathfinding
    private enum State { IDLE, WANDERING, HUNTING, FOLLOWING_PATH, ATTACKING, REPRODUCING, DEFENDING, NEURAL }
    private State currentState = State.IDLE;
    private int[] targetCoords = null;
    private Entity targetPrey = null;
    private LinkedList<int[]> currentPath = null;
    private final Pathfinder pathfinder = new Pathfinder();
    private int pathRepathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;
    
    // Flag for neural network behavior
    private boolean useNeuralBehavior = true;
    
    // Movement Accumulator for Speed Gene
    private double moveAccumulator = 0.0;
    
    // Territory center (apex predators are territorial)
    private int territoryCenterX;
    private int territoryCenterY;
    private double territoryRadius;
    
    /** Constructor for initial placement */
    public ApexPredator(int x, int y) {
        super(x, y, SpeciesType.APEX_PREDATOR);
        initializeTerritory(x, y);
    }
    
    /** Constructor for offspring */
    public ApexPredator(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.APEX_PREDATOR, new Genes(parentGenes));
        initializeTerritory(x, y);
    }
    
    /** Constructor for offspring with inherited brain */
    public ApexPredator(int x, int y, Genes parentGenes, AnimalBrain parentBrain) {
        super(x, y, SpeciesType.APEX_PREDATOR, new Genes(parentGenes), parentBrain);
        initializeTerritory(x, y);
    }
    
    /**
     * Initialize the predator's territory.
     */
    private void initializeTerritory(int x, int y) {
        this.territoryCenterX = x;
        this.territoryCenterY = y;
        this.territoryRadius = 15 + random.nextInt(10); // 15-24 radius
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
        
        // 2. Attack (if prey nearby)
        if (decision.attack) {
            EntityManager entityManager = simulation.getEntityManager();
            List<Entity> nearbyEntities = entityManager.getEntitiesInRange(x, y, (int)ATTACK_RANGE);
            
            Entity bestPrey = null;
            double bestScore = Double.MAX_VALUE;
            
            // Find best prey - prefer carnivores when hungry, otherwise take easiest target
            for (Entity entity : nearbyEntities) {
                if ((entity.getSpeciesType() == SpeciesType.HERBIVORE || 
                     entity.getSpeciesType() == SpeciesType.CARNIVORE ||
                     entity.getSpeciesType() == SpeciesType.OMNIVORE) && 
                    entity.isAlive()) {
                    
                    double healthScore = entity.getHealth() / entity.getMaxHealth();
                    double distanceScore = Math.sqrt(
                            Math.pow(entity.getX() - x, 2) + 
                            Math.pow(entity.getY() - y, 2));
                    
                    // Preference for carnivores when hungry for more energy
                    double typeBonus = 0;
                    if (energy < getMaxEnergy() * 0.4) {
                        if (entity.getSpeciesType() == SpeciesType.CARNIVORE) {
                            typeBonus = -3; // High priority for carnivores when hungry
                        } else if (entity.getSpeciesType() == SpeciesType.OMNIVORE) {
                            typeBonus = -1.5; // Medium priority for omnivores
                        }
                    }
                    
                    // Combined score (lower is better)
                    double score = healthScore * 0.3 + distanceScore * 0.7 + typeBonus;
                    
                    if (score < bestScore) {
                        bestScore = score;
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
        
        // Periodically update territory center to current position if far from it
        double distToTerritory = Math.sqrt(
                Math.pow(x - territoryCenterX, 2) + 
                Math.pow(y - territoryCenterY, 2));
                
        // If outside territory for a while, consider relocating
        if (distToTerritory > territoryRadius * 0.8 && random.nextDouble() < 0.01) {
            territoryCenterX = x;
            territoryCenterY = y;
        }
        
        // Decide current action
        decideState(simulation, world);
        
        // Execute action based on state
        switch (currentState) {
            case HUNTING:
                hunt(simulation, world);
                if (currentState == State.FOLLOWING_PATH) {
                    followPath(simulation, world);
                }
                break;
            case FOLLOWING_PATH:
                followPath(simulation, world);
                break;
            case ATTACKING:
                attackEntity(targetPrey);
                if (targetPrey != null && !targetPrey.isAlive()) {
                    eatPrey(targetPrey);
                    targetPrey = null;
                    decideState(simulation, world);
                    if (currentState == State.WANDERING) wander(simulation, world);
                }
                break;
            case REPRODUCING:
                reproduce(simulation);
                decideState(simulation, world);
                if (currentState == State.WANDERING) wander(simulation, world);
                break;
            case DEFENDING:
                defendTerritory(simulation, world);
                break;
            case WANDERING:
            default:
                wander(simulation, world);
                break;
        }
    }
    
    /**
     * Toggle neural network behavior.
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
     * Decide the apex predator's state based on needs and surroundings.
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
        
        // Check if intruder in territory
        List<Entity> intruders = entityManager.findEntitiesInRange(territoryCenterX, territoryCenterY, territoryRadius, world);
        
        boolean hasIntruder = false;
        for (Entity entity : intruders) {
            if (entity != this && 
                entity.isAlive() && 
                (entity.getSpeciesType() == SpeciesType.APEX_PREDATOR && entity != this) &&
                random.nextDouble() < 0.7) { // 70% chance to defend territory
                
                hasIntruder = true;
                targetPrey = entity;
                currentState = State.DEFENDING;
                clearPath();
                return;
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
        
        // Far from territory center? Return to territory
        double distToTerritory = Math.sqrt(
                Math.pow(x - territoryCenterX, 2) + 
                Math.pow(y - territoryCenterY, 2));
                
        if (distToTerritory > territoryRadius * 0.8 && random.nextDouble() < 0.3) {
            targetCoords = new int[]{territoryCenterX, territoryCenterY};
            if (calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                currentState = State.FOLLOWING_PATH;
                return;
            }
        }
        
        // Default to wandering
        if (currentState != State.WANDERING) {
            clearPath();
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Hunt for prey (prioritizing carnivores when hungry).
     */
    private void hunt(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double visionRange = getVisionRange();
        
        // Find all potential prey
        List<Entity> potentialPrey = new ArrayList<>();
        
        // Add herbivores
        potentialPrey.addAll(entityManager.findEntitiesInRange(
                x, y, visionRange, SpeciesType.HERBIVORE, world));
        
        // Add carnivores
        potentialPrey.addAll(entityManager.findEntitiesInRange(
                x, y, visionRange, SpeciesType.CARNIVORE, world));
        
        // Add omnivores
        potentialPrey.addAll(entityManager.findEntitiesInRange(
                x, y, visionRange, SpeciesType.OMNIVORE, world));
        
        // Remove non-viable prey
        potentialPrey.removeIf(prey -> !prey.isAlive());
        
        Entity bestPrey = null;
        double bestScore = Double.MAX_VALUE;
        
        for (Entity prey : potentialPrey) {
            double distance = Math.sqrt(Math.pow(prey.getX() - x, 2) + Math.pow(prey.getY() - y, 2));
            double healthRatio = prey.getHealth() / prey.getMaxHealth();
            
            // Prefer different prey based on energy level
            double typeBonus = 0;
            if (energy < getMaxEnergy() * 0.4) {
                // When very hungry, prefer carnivores (more energy)
                if (prey.getSpeciesType() == SpeciesType.CARNIVORE) {
                    typeBonus = -3; // High priority
                } else if (prey.getSpeciesType() == SpeciesType.OMNIVORE) {
                    typeBonus = -1.5; // Medium priority
                }
            } else {
                // When not very hungry, prefer easier prey (herbivores)
                if (prey.getSpeciesType() == SpeciesType.HERBIVORE) {
                    typeBonus = -1;
                }
            }
            
            // Score: balance of distance, health and type preference
            double score = distance * 0.5 + healthRatio * visionRange * 0.3 + typeBonus;
            
            if (score < bestScore) {
                bestScore = score;
                bestPrey = prey;
            }
        }
        
        if (bestPrey != null) {
            targetPrey = bestPrey;
            
            if (isInAttackRange(targetPrey)) {
                currentState = State.ATTACKING;
            } else {
                targetCoords = new int[]{targetPrey.getX(), targetPrey.getY()};
                pathRepathAttempts = 0;
                
                if (calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                    currentState = State.FOLLOWING_PATH;
                } else {
                    targetPrey = null;
                    targetCoords = null;
                    currentState = State.WANDERING;
                }
            }
        } else {
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Defend territory against intruders.
     */
    private void defendTerritory(Simulation simulation, World world) {
        if (targetPrey == null || !targetPrey.isAlive()) {
            currentState = State.IDLE;
            return;
        }
        
        if (isInAttackRange(targetPrey)) {
            attackEntity(targetPrey);
            if (!targetPrey.isAlive()) {
                // Don't eat other apex predators - just drive them away
                if (targetPrey.getSpeciesType() != SpeciesType.APEX_PREDATOR) {
                    eatPrey(targetPrey);
                }
                targetPrey = null;
                currentState = State.IDLE;
            }
        } else {
            // Chase the intruder
            targetCoords = new int[]{targetPrey.getX(), targetPrey.getY()};
            
            if (calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                followPath(simulation, world);
            } else {
                currentState = State.WANDERING;
                targetPrey = null;
                targetCoords = null;
            }
        }
    }
    
    /**
     * Attack a prey entity.
     */
    private void attackEntity(Entity prey) {
        if (prey != null && prey.isAlive()) {
            prey.takeDamage(ATTACK_DAMAGE);
            depleteEnergy(ATTACK_ENERGY_COST);
        }
    }
    
    /**
     * Eat a prey that was killed.
     */
    private void eatPrey(Entity prey) {
        if (prey != null && !prey.isAlive()) {
            gainEnergy(prey.getMaxEnergy() * EAT_ENERGY_GAIN_FACTOR);
        }
    }
    
    /**
     * Check if target is within attack range.
     */
    private boolean isInAttackRange(Entity target) {
        if (target == null) return false;
        
        double dx = target.getX() - x;
        double dy = target.getY() - y;
        double distSq = dx*dx + dy*dy;
        
        return distSq <= ATTACK_RANGE * ATTACK_RANGE;
    }
    
    /**
     * Validate target prey is still valid.
     */
    private void validateTargetPrey(Simulation simulation) {
        if (targetPrey != null) {
            if (!targetPrey.isAlive() || 
                !simulation.getEntityManager().getAllEntities().contains(targetPrey)) {
                targetPrey = null;
                currentState = State.IDLE;
            }
        }
    }
    
    /**
     * Follow a calculated path.
     */
    private void followPath(Simulation simulation, World world) {
        if (currentPath == null || currentPath.isEmpty()) {
            return;
        }
        
        // Update target coords if pursuing prey
        if (targetPrey != null && targetPrey.isAlive()) {
            int preyX = targetPrey.getX();
            int preyY = targetPrey.getY();
            
            if (targetCoords[0] != preyX || targetCoords[1] != preyY) {
                targetCoords[0] = preyX;
                targetCoords[1] = preyY;
                
                if (isInAttackRange(targetPrey)) {
                    currentState = State.ATTACKING;
                    return;
                }
                
                // Recalculate path if prey moved significantly
                double distance = Math.sqrt(Math.pow(preyX - x, 2) + Math.pow(preyY - y, 2));
                if (distance > 2) {
                    calculatePath(simulation, world, preyX, preyY);
                }
            }
        }
        
        // Move along path
        moveAccumulator += getSpeed();
        
        while (moveAccumulator >= 1.0 && currentPath != null && !currentPath.isEmpty()) {
            int[] nextStep = currentPath.peek();
            int targetX = nextStep[0];
            int targetY = nextStep[1];
            
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
                
                if (currentPath.isEmpty()) {
                    // Reached destination
                    if (targetPrey != null && targetPrey.isAlive() && isInAttackRange(targetPrey)) {
                        currentState = State.ATTACKING;
                    } else {
                        currentState = State.IDLE;
                    }
                    
                    targetCoords = null;
                }
            } else {
                // Path blocked, recalculate
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
     * Clear the current path.
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
        if (currentState == State.FOLLOWING_PATH && targetCoords != null) {
            if (targetPrey != null && !targetPrey.isAlive()) {
                clearPath();
                currentState = State.IDLE;
            }
        }
    }
    
    /**
     * Random wandering behavior (usually stays within territory).
     */
    private void wander(Simulation simulation, World world) {
        moveAccumulator += getSpeed();
        
        while (moveAccumulator >= 1.0) {
            int dx, dy;
            
            // Calculate distance to territory center
            double distToTerritory = Math.sqrt(
                    Math.pow(x - territoryCenterX, 2) + 
                    Math.pow(y - territoryCenterY, 2));
            
            // If near edge of territory, bias movement toward center
            if (distToTerritory > territoryRadius * 0.7 && random.nextDouble() < 0.7) {
                // Calculate direction toward territory center
                double dirX = territoryCenterX - x;
                double dirY = territoryCenterY - y;
                double magnitude = Math.sqrt(dirX * dirX + dirY * dirY);
                
                if (magnitude > 0) {
                    dx = (int) Math.round(dirX / magnitude);
                    dy = (int) Math.round(dirY / magnitude);
                } else {
                    dx = random.nextInt(3) - 1;
                    dy = random.nextInt(3) - 1;
                }
            } else {
                // Random movement within territory
                dx = random.nextInt(3) - 1;
                dy = random.nextInt(3) - 1;
            }
            
            if (dx == 0 && dy == 0) {
                moveAccumulator -= 1.0;
                continue;
            }
            
            moveBy(simulation, dx, dy, world);
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Reproduce and create offspring.
     */
    private void reproduce(Simulation simulation) {
        if (energy >= getReproductionThreshold()) {
            Entity offspring = simulation.spawnOffspring(this, SpeciesType.APEX_PREDATOR, this.genes);
            
            if (offspring != null) {
                depleteEnergy(getReproductionCost());
                
                // Expand territory for offspring
                territoryRadius = Math.min(30, territoryRadius + 2);
            }
        }
    }
    
    /**
     * Move with default speed.
     */
    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        int nextX = x + dx;
        int nextY = y + dy;
        
        if (world.isValidCoordinate(nextX, nextY)) {
            Tile targetTile = world.getTile(nextX, nextY);
            if (targetTile != null && targetTile.isPassable(this) &&
                !simulation.getEntityManager().isTileOccupiedByOther(nextX, nextY, this)) {
                
                double energyCost = MOVE_ENERGY_COST_FACTOR * getSpeed();
                if (energy >= energyCost) {
                    depleteEnergy(energyCost);
                    setPosition(nextX, nextY);
                    return true;
                }
            }
        }
        
        return false;
    }
} 
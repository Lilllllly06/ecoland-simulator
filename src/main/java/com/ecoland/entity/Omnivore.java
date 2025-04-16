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
 * Omnivore class representing animals that can consume both plants and meat.
 * They are versatile and can switch food sources based on availability.
 */
public class Omnivore extends Entity {

    // Constants
    private static final double BASE_ENERGY_DEPLETION = 0.12; // Medium energy cost
    private static final double MOVE_ENERGY_COST_FACTOR = 0.06; // Medium movement cost
    private static final double ATTACK_ENERGY_COST = 1.5; // Lower than carnivores
    private static final double ATTACK_DAMAGE = 20.0; // Weaker than carnivores
    private static final double EAT_MEAT_ENERGY_GAIN = 20.0; // Gain from meat
    private static final double EAT_PLANT_ENERGY_GAIN_FACTOR = 4.0; // Gain from plants
    private static final double HUNGER_THRESHOLD_FACTOR = 0.5; // Medium hunger threshold
    private static final double ATTACK_RANGE = 1.3; // Medium attack range
    private static final double PREDATOR_DETECTION_RANGE_FACTOR = 1.0; // Standard detection
    private static final double FLEE_SPEED_BOOST = 1.15; // Medium flee boost
    
    private static final Random random = new Random();
    
    // State and Pathfinding
    private enum State { IDLE, WANDERING, HUNTING, SEEKING_FOOD, FOLLOWING_PATH, EATING, ATTACKING, REPRODUCING, FLEEING, NEURAL }
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
    
    // Food preference (dynamically adjusted based on availability)
    private double plantPreference = 0.5; // 0-1 range, 1 means full plant preference
    
    /** Constructor for initial placement */
    public Omnivore(int x, int y) {
        super(x, y, SpeciesType.OMNIVORE);
    }
    
    /** Constructor for offspring */
    public Omnivore(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.OMNIVORE, new Genes(parentGenes));
    }
    
    /** Constructor for offspring with inherited brain */
    public Omnivore(int x, int y, Genes parentGenes, AnimalBrain parentBrain) {
        super(x, y, SpeciesType.OMNIVORE, new Genes(parentGenes), parentBrain);
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
            // First check if there's plant food at current location
            Tile currentTile = world.getTile(x, y);
            if (currentTile != null && currentTile.getPlantFoodValue() > 0.1) {
                eatPlant(world);
            }
        }
        
        // 3. Attacking 
        if (decision.attack) {
            EntityManager entityManager = simulation.getEntityManager();
            List<Entity> nearbyEntities = entityManager.getEntitiesInRange(x, y, 1);
            
            Entity bestPrey = null;
            double bestPreyScore = Double.MAX_VALUE;
            
            // Find the best prey (weakest and closest)
            for (Entity entity : nearbyEntities) {
                if ((entity.getSpeciesType() == SpeciesType.HERBIVORE || 
                     entity.getSpeciesType() == SpeciesType.PLANT) && entity.isAlive()) {
                    
                    double healthScore = entity.getHealth() / entity.getMaxHealth();
                    double distanceScore = Math.sqrt(
                            Math.pow(entity.getX() - x, 2) + 
                            Math.pow(entity.getY() - y, 2));
                    
                    // Combined score (lower is better)
                    double score = healthScore * 0.7 + distanceScore * 0.3;
                    
                    if (score < bestPreyScore) {
                        bestPreyScore = score;
                        bestPrey = entity;
                    }
                }
            }
            
            // Attack if prey found
            if (bestPrey != null) {
                if (bestPrey.getSpeciesType() == SpeciesType.HERBIVORE) {
                    attackEntity(bestPrey);
                    
                    // If prey dies, consume it
                    if (!bestPrey.isAlive()) {
                        eatPrey(bestPrey);
                    }
                } else if (bestPrey.getSpeciesType() == SpeciesType.PLANT) {
                    eatPlant(world);
                }
            }
        }
        
        // 4. Reproduction
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
        
        // Update food preference based on environment
        updateFoodPreference(simulation, world);
        
        // Decide current action
        decideState(simulation, world);
        
        // Execute action based on state
        switch (currentState) {
            case FLEEING:
                flee(simulation, world);
                break;
            case HUNTING:
                hunt(simulation, world);
                if (currentState == State.FOLLOWING_PATH) {
                    followPath(simulation, world);
                }
                break;
            case SEEKING_FOOD:
                seekFood(simulation, world);
                if (currentState == State.FOLLOWING_PATH) {
                    followPath(simulation, world);
                }
                break;
            case FOLLOWING_PATH:
                followPath(simulation, world);
                break;
            case EATING:
                eatPlant(world);
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
            case WANDERING:
            default:
                wander(simulation, world);
                break;
        }
    }
    
    /**
     * Update food preference based on environmental factors.
     */
    private void updateFoodPreference(Simulation simulation, World world) {
        // Count available food sources in vision range
        int plantFoodCount = 0;
        int preyCount = 0;
        
        // Check for plant food
        int visionRange = (int) getVisionRange();
        for (int dx = -visionRange; dx <= visionRange; dx++) {
            for (int dy = -visionRange; dy <= visionRange; dy++) {
                int checkX = x + dx;
                int checkY = y + dy;
                
                if (!world.isValidCoordinate(checkX, checkY)) continue;
                
                double distSq = dx * dx + dy * dy;
                if (distSq > visionRange * visionRange) continue;
                
                Tile tile = world.getTile(checkX, checkY);
                if (tile != null && tile.getPlantFoodValue() > 0.3) {
                    plantFoodCount++;
                }
            }
        }
        
        // Check for prey
        EntityManager entityManager = simulation.getEntityManager();
        List<Entity> nearbyEntities = entityManager.getEntitiesInRange(x, y, visionRange);
        
        for (Entity entity : nearbyEntities) {
            if (entity != this && entity.isAlive() && entity.getSpeciesType() == SpeciesType.HERBIVORE) {
                preyCount++;
            }
        }
        
        // Adjust preference based on availability (scarce resources are less preferred)
        if (plantFoodCount == 0 && preyCount > 0) {
            plantPreference = Math.max(0.1, plantPreference - 0.1); // Shift toward meat
        } else if (preyCount == 0 && plantFoodCount > 0) {
            plantPreference = Math.min(0.9, plantPreference + 0.1); // Shift toward plants
        } else if (plantFoodCount > 0 && preyCount > 0) {
            // Adjust based on relative abundance
            double ratio = (double) plantFoodCount / (plantFoodCount + preyCount * 3); // Prey counts more
            plantPreference = plantPreference * 0.8 + ratio * 0.2; // Gradual adjustment
        }
        
        // If very hungry, prefer the more abundant food
        if (energy < getMaxEnergy() * 0.3) {
            if (plantFoodCount > preyCount * 2) {
                plantPreference = Math.min(0.9, plantPreference + 0.2);
            } else if (preyCount > plantFoodCount) {
                plantPreference = Math.max(0.1, plantPreference - 0.2);
            }
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
     * Decide the omnivore's state based on needs and surroundings.
     */
    private void decideState(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        
        // Check for threats first (highest priority)
        double predatorDetectionRange = getVisionRange() * PREDATOR_DETECTION_RANGE_FACTOR;
        List<Entity> nearbyPredators = entityManager.findEntitiesInRange(
                x, y, predatorDetectionRange, SpeciesType.CARNIVORE, world);
        
        // Add apex predators to the threat list
        nearbyPredators.addAll(entityManager.findEntitiesInRange(
                x, y, predatorDetectionRange, SpeciesType.APEX_PREDATOR, world));
        
        // Filter out non-threats
        nearbyPredators.removeIf(predator -> 
                !predator.isAlive() || 
                (predator.getHealth() < health * 0.5)); // Ignore weakened predators
        
        if (!nearbyPredators.isEmpty()) {
            if (currentState != State.FLEEING) {
                clearPath();
                currentState = State.FLEEING;
            }
            return;
        }
        
        // If we were fleeing but predators are gone, become idle
        if (currentState == State.FLEEING) {
            currentState = State.IDLE;
        }
        
        // Continue with current path if valid
        if (currentState == State.FOLLOWING_PATH) {
            if (currentPath == null || currentPath.isEmpty()) {
                currentState = State.IDLE;
            } else {
                return;
            }
        }
        
        // Check if can eat at current location
        Tile currentTile = world.getTile(x, y);
        boolean canEatHere = currentTile != null && currentTile.getPlantFoodValue() > 0.1;
        
        if (currentState == State.EATING) {
            if (!canEatHere || energy >= getMaxEnergy()) {
                currentState = State.IDLE;
            } else {
                return;
            }
        }
        
        // Check if attacking
        if (currentState == State.ATTACKING) {
            if (targetPrey == null || !targetPrey.isAlive() || !isInAttackRange(targetPrey)) {
                currentState = State.IDLE;
            } else {
                return;
            }
        }
        
        // Ready to reproduce?
        if (energy >= getReproductionThreshold()) {
            clearPath();
            currentState = State.REPRODUCING;
            return;
        }
        
        // Hungry?
        double hungerThreshold = getMaxEnergy() * HUNGER_THRESHOLD_FACTOR;
        if (energy < hungerThreshold) {
            // Decide between hunting and plant eating based on preference
            if (canEatHere && random.nextDouble() < plantPreference) {
                clearPath();
                currentState = State.EATING;
            } else if (random.nextDouble() < plantPreference) {
                clearPath();
                currentState = State.SEEKING_FOOD;
            } else {
                clearPath();
                currentState = State.HUNTING;
            }
            return;
        }
        
        // Default to wandering
        if (currentState != State.WANDERING) {
            clearPath();
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Hunt for prey.
     */
    private void hunt(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double visionRange = getVisionRange();
        
        // Find nearby herbivores
        List<Entity> nearbyPrey = entityManager.findEntitiesInRange(
                x, y, visionRange, SpeciesType.HERBIVORE, world);
        
        Entity bestPrey = null;
        double bestScore = Double.MAX_VALUE;
        
        for (Entity prey : nearbyPrey) {
            if (!prey.isAlive()) continue;
            
            double distance = Math.sqrt(Math.pow(prey.getX() - x, 2) + Math.pow(prey.getY() - y, 2));
            double healthRatio = prey.getHealth() / prey.getMaxHealth();
            
            // Score: balance of distance and health (prefer weaker and closer prey)
            double score = distance * 0.7 + healthRatio * visionRange * 0.3;
            
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
            // No prey found, try plant food instead
            currentState = State.SEEKING_FOOD;
            seekFood(simulation, world);
        }
    }
    
    /**
     * Seek plant food on tiles.
     */
    private void seekFood(Simulation simulation, World world) {
        targetCoords = findBestFoodSourceCoords(world);
        
        if (targetCoords != null) {
            pathRepathAttempts = 0;
            
            if (calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                currentState = State.FOLLOWING_PATH;
            } else {
                targetCoords = null;
                currentState = State.WANDERING;
            }
        } else {
            currentState = State.WANDERING;
        }
    }
    
    /**
     * Find the best food source coordinates.
     */
    private int[] findBestFoodSourceCoords(World world) {
        int[] bestCoords = null;
        double maxFood = 0;
        int visionRadius = (int) Math.ceil(getVisionRange());
        
        for (int dx = -visionRadius; dx <= visionRadius; dx++) {
            for (int dy = -visionRadius; dy <= visionRadius; dy++) {
                if (dx == 0 && dy == 0) continue;
                
                int checkX = x + dx;
                int checkY = y + dy;
                
                double distSq = dx * dx + dy * dy;
                if (distSq > visionRadius * visionRadius) continue;
                
                if (!world.isValidCoordinate(checkX, checkY)) continue;
                
                Tile tile = world.getTile(checkX, checkY);
                if (tile != null && tile.getTerrainType() != TerrainType.WATER) {
                    double foodValue = tile.getPlantFoodValue();
                    if (foodValue > maxFood) {
                        maxFood = foodValue;
                        bestCoords = new int[]{checkX, checkY};
                    }
                }
            }
        }
        
        return bestCoords;
    }
    
    /**
     * Eat plant food from the current tile.
     */
    private void eatPlant(World world) {
        Tile currentTile = world.getTile(x, y);
        
        if (currentTile != null && currentTile.getPlantFoodValue() > 0) {
            double foodAvailable = currentTile.getPlantFoodValue();
            double amountToEat = Math.min(1.0, foodAvailable);
            double consumed = currentTile.consumePlantFood(amountToEat);
            
            if (consumed > 0) {
                gainEnergy(consumed * EAT_PLANT_ENERGY_GAIN_FACTOR);
                
                if (energy >= getMaxEnergy()) {
                    currentState = State.IDLE;
                }
            } else {
                currentState = State.IDLE;
                clearPath();
            }
        } else {
            currentState = State.IDLE;
            clearPath();
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
            gainEnergy(prey.getMaxEnergy() * 0.7);
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
     * Flee from predators.
     */
    private void flee(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double visionRange = getVisionRange();
        
        // Get all nearby predators (both carnivores and apex predators)
        List<Entity> nearbyPredators = entityManager.findEntitiesInRange(
                x, y, visionRange, SpeciesType.CARNIVORE, world);
        
        nearbyPredators.addAll(entityManager.findEntitiesInRange(
                x, y, visionRange, SpeciesType.APEX_PREDATOR, world));
        
        // Filter out non-threats
        nearbyPredators.removeIf(predator -> 
                !predator.isAlive() || 
                (predator.getHealth() < health * 0.5));
        
        if (nearbyPredators.isEmpty()) {
            return;
        }
        
        // Calculate average predator location
        double avgPredatorX = 0, avgPredatorY = 0;
        for (Entity predator : nearbyPredators) {
            avgPredatorX += predator.getX();
            avgPredatorY += predator.getY();
        }
        avgPredatorX /= nearbyPredators.size();
        avgPredatorY /= nearbyPredators.size();
        
        // Calculate flee direction (away from average predator location)
        double fleeVectorX = x - avgPredatorX;
        double fleeVectorY = y - avgPredatorY;
        double magnitude = Math.sqrt(fleeVectorX * fleeVectorX + fleeVectorY * fleeVectorY);
        
        // Normalize the flee vector
        int dx = 0, dy = 0;
        if (magnitude > 0.1) {
            dx = (int) Math.round(fleeVectorX / magnitude);
            dy = (int) Math.round(fleeVectorY / magnitude);
        } else {
            dx = random.nextInt(3) - 1;
            dy = random.nextInt(3) - 1;
        }
        
        // Ensure movement if direction is (0,0)
        if (dx == 0 && dy == 0) dx = (random.nextBoolean() ? 1 : -1);
        
        // Apply speed boost for fleeing
        double effectiveSpeed = getSpeed() * FLEE_SPEED_BOOST;
        moveAccumulator += effectiveSpeed;
        
        while (moveAccumulator >= 1.0) {
            boolean moved = moveBy(simulation, dx, dy, world, FLEE_SPEED_BOOST);
            
            if (!moved) {
                // Try alternative directions
                for (int i = 0; i < 5; i++) {
                    int rdx = (dx + (random.nextInt(3) - 1) + 3) % 3 - 1;
                    int rdy = (dy + (random.nextInt(3) - 1) + 3) % 3 - 1;
                    
                    if (rdx == 0 && rdy == 0) continue;
                    
                    if (moveBy(simulation, rdx, rdy, world, FLEE_SPEED_BOOST)) {
                        moved = true;
                        break;
                    }
                }
                
                if (!moved) {
                    moveAccumulator = 0;
                    break;
                }
            }
            
            moveAccumulator -= 1.0;
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
                    } else if (targetCoords != null) {
                        // Check if we reached plant food
                        Tile targetTile = world.getTile(targetCoords[0], targetCoords[1]);
                        if (targetTile != null && targetTile.getPlantFoodValue() > 0.1) {
                            currentState = State.EATING;
                        } else {
                            currentState = State.IDLE;
                        }
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
        if ((currentState == State.FOLLOWING_PATH || currentState == State.SEEKING_FOOD) && targetCoords != null) {
            Tile targetTile = world.getTile(targetCoords[0], targetCoords[1]);
            
            // Invalidate for plant food goals
            if (targetPrey == null && (targetTile == null || targetTile.getPlantFoodValue() < 0.1 || 
                targetTile.getTerrainType() == TerrainType.WATER)) {
                clearPath();
                if (currentState == State.FOLLOWING_PATH) {
                    currentState = State.IDLE;
                }
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
            
            moveBy(simulation, dx, dy, world);
            moveAccumulator -= 1.0;
        }
    }
    
    /**
     * Reproduce and create offspring.
     */
    private void reproduce(Simulation simulation) {
        if (energy >= getReproductionThreshold()) {
            Entity offspring = simulation.spawnOffspring(this, SpeciesType.OMNIVORE, this.genes);
            
            if (offspring != null) {
                depleteEnergy(getReproductionCost());
            }
        }
    }
    
    /**
     * Move with a speed multiplier.
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
     * Move with default speed.
     */
    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        return moveBy(simulation, dx, dy, world, 1.0);
    }
} 
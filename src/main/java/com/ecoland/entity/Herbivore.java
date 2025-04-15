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

public class Herbivore extends Entity {

    // Constants
    private static final double BASE_ENERGY_DEPLETION = 0.1;
    private static final double MOVE_ENERGY_COST_FACTOR = 0.05; // Energy cost per unit speed *per step taken*
    private static final double EAT_ENERGY_GAIN_FACTOR = 5.0;
    private static final double HUNGER_THRESHOLD_FACTOR = 0.5;
    private static final double PREDATOR_DETECTION_RANGE_FACTOR = 1.0;
    private static final double FLEE_SPEED_BOOST = 1.2; // Factor applied to base speed when fleeing

    private static final Random random = new Random();

    // State and Pathfinding
    private enum State { IDLE, WANDERING, SEEKING_FOOD, FOLLOWING_PATH, EATING, REPRODUCING, FLEEING, NEURAL }
    private State currentState = State.IDLE;
    private int[] targetCoords = null;
    private LinkedList<int[]> currentPath = null;
    private final Pathfinder pathfinder = new Pathfinder();
    private int pathRepathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;

    // Movement Accumulator for Speed Gene
    private double moveAccumulator = 0.0;
    
    // Flag to use neural network or traditional behavior
    private boolean useNeuralBehavior = true;

    /** Constructor for initial placement */
    public Herbivore(int x, int y) {
        super(x, y, SpeciesType.HERBIVORE);
    }

    /** Constructor for offspring */
    public Herbivore(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.HERBIVORE, new Genes(parentGenes));
    }
    
    /** Constructor for offspring with inherited brain */
    public Herbivore(int x, int y, Genes parentGenes, AnimalBrain parentBrain) {
        super(x, y, SpeciesType.HERBIVORE, new Genes(parentGenes), parentBrain);
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
                boolean moved = moveBy(simulation, decision.moveX, decision.moveY, world, 1.0);
                
                if (!moved) {
                    // Try alternative directions if blocked
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            if (dx == decision.moveX && dy == decision.moveY) continue;
                            
                            if (moveBy(simulation, dx, dy, world, 1.0)) {
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
            Tile currentTile = world.getTile(x, y);
            if (currentTile != null && currentTile.getPlantFoodValue() > 0.1) {
                eat(world);
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

        // Decide current action
        decideState(simulation, world);

        // Execute action based on state
        switch (currentState) {
            case FLEEING:
                flee(simulation, world);
                break;
            case SEEKING_FOOD:
                // Seeking just sets up the path, following handles movement
                seekFood(simulation, world);
                // If path found, immediately try to follow it this tick
                if (currentState == State.FOLLOWING_PATH) {
                    followPath(simulation, world);
                }
                break;
            case FOLLOWING_PATH:
                followPath(simulation, world);
                break;
            case EATING:
                eat(world);
                break;
            case REPRODUCING:
                reproduce(simulation);
                // After reproducing, re-evaluate state immediately (might wander or eat)
                decideState(simulation, world);
                if (currentState == State.WANDERING) wander(simulation, world); // Execute wander if decided
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

    private void decideState(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double currentVisionRange = getVisionRange();
        double predatorDetectionRange = currentVisionRange * PREDATOR_DETECTION_RANGE_FACTOR;

        // --- Check for Predators --- (Highest Priority)
        List<Entity> nearbyPredators = entityManager.findEntitiesInRange(x, y, predatorDetectionRange, SpeciesType.CARNIVORE, world);
        if (!nearbyPredators.isEmpty()) {
            if (currentState != State.FLEEING) { // Avoid clearing path if already fleeing
                 clearPath();
                 currentState = State.FLEEING;
            }
            return; // Fleeing overrides other states
        }

        // If we were fleeing but predators are gone, become idle to re-evaluate
        if (currentState == State.FLEEING) {
            currentState = State.IDLE;
        }

        // Check if currently following a path that's still valid
        if (currentState == State.FOLLOWING_PATH) {
             if (currentPath == null || currentPath.isEmpty()) {
                 // Reached destination or path lost, re-evaluate
                 currentState = State.IDLE;
             } else {
                 return; // Continue following path state
             }
        }

         // If eating, check if still possible/necessary
         if (currentState == State.EATING) {
             Tile currentTile = world.getTile(x, y);
             boolean canEatHere = currentTile != null && currentTile.getPlantFoodValue() > 0.1;
             if (!canEatHere || energy >= getMaxEnergy()) { // Stop eating if full or food gone
                 currentState = State.IDLE;
             } else {
                 return; // Continue eating state
             }
         }

        // --- Normal State Logic --- (If not fleeing, following path, or eating)
        // Re-evaluate based on needs
        Tile currentTile = world.getTile(x, y);
        boolean canEatHere = currentTile != null && currentTile.getPlantFoodValue() > 0.1;
        double currentHungerThreshold = getMaxEnergy() * HUNGER_THRESHOLD_FACTOR;

        if (energy < currentHungerThreshold) { // Need food
            if (canEatHere) {
                clearPath(); // Clear any previous path
                currentState = State.EATING;
            } else {
                 // Only start seeking if not already seeking/following path
                if (currentState != State.SEEKING_FOOD && currentState != State.FOLLOWING_PATH) {
                    clearPath();
                    currentState = State.SEEKING_FOOD;
                } else if (currentState == State.SEEKING_FOOD && targetCoords == null) {
                    // If was seeking but failed to find food last tick, reset to wander
                     currentState = State.WANDERING;
                }
            }
        } else if (energy >= getReproductionThreshold()) { // Ready to reproduce
             clearPath();
             currentState = State.REPRODUCING;
        } else { // Neither hungry nor ready to reproduce -> Wander
             if (currentState != State.WANDERING) { // Only switch if not already wandering
                 clearPath();
                 currentState = State.WANDERING;
             }
        }
    }

    private void flee(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double currentVisionRange = getVisionRange();
        List<Entity> nearbyPredators = entityManager.findEntitiesInRange(x, y, currentVisionRange, SpeciesType.CARNIVORE, world);

        if (nearbyPredators.isEmpty()) {
            // currentState = State.IDLE; // State change handled in decideState
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

        // Normalize flee vector to get direction (dx, dy)
        int dx = 0, dy = 0;
        if (magnitude > 0.1) {
            dx = (int) Math.round(fleeVectorX / magnitude);
            dy = (int) Math.round(fleeVectorY / magnitude);
        } else { // If predator is very close, choose random direction away
            dx = random.nextInt(3) - 1;
            dy = random.nextInt(3) - 1;
        }
        // Ensure movement if direction is (0,0)
        if (dx == 0 && dy == 0) dx = (random.nextBoolean() ? 1 : -1);

        // --- Speed Accumulator Logic for Fleeing ---
        double effectiveSpeed = getSpeed() * FLEE_SPEED_BOOST;
        moveAccumulator += effectiveSpeed;

        while (moveAccumulator >= 1.0) {
            boolean moved = moveBy(simulation, dx, dy, world, FLEE_SPEED_BOOST);

            if (!moved) {
                // If preferred flee direction blocked, try adjacent tiles
                int rdx = dx;
                int rdy = dy;
                for (int i = 0; i < 5; i++) { // Try alternatives
                     // Simple orthogonal/diagonal alternatives
                     if (i%2 == 0) rdx = (rdx + (random.nextBoolean()?1:-1) + 3) % 3 - 1; // Change dx
                     else rdy = (rdy + (random.nextBoolean()?1:-1) + 3) % 3 - 1; // Change dy
                     if (rdx == 0 && rdy == 0) continue; // Skip no move

                     if (moveBy(simulation, rdx, rdy, world, FLEE_SPEED_BOOST)) {
                         moved = true;
                         break;
                     }
                }
                if (!moved) {
                    // If still couldn't move, stop trying this tick (might be trapped)
                    moveAccumulator = 0; // Prevent further attempts this tick
                    break;
                }
            }
            moveAccumulator -= 1.0; // Decrement accumulator after move attempt
        }
    }

    private void seekFood(Simulation simulation, World world) {
        // This method now only finds the target and calculates the initial path.
        // Movement is handled by followPath.
        targetCoords = findBestFoodSourceCoords(world);
        if (targetCoords != null) {
            pathRepathAttempts = 0; // Reset attempts for new target
            if (calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                currentState = State.FOLLOWING_PATH;
            } else {
                System.out.println("Herbivore failed to pathfind to food at (" + targetCoords[0] + "," + targetCoords[1] + "). Wandering.");
                targetCoords = null; // Clear target if pathing failed
                currentState = State.WANDERING;
            }
        } else {
            // No food found in vision range
            currentState = State.WANDERING;
        }
    }

    private int[] findBestFoodSourceCoords(World world) {
        int[] bestCoords = null;
        double maxFood = 0;
        int visionRadius = (int) Math.ceil(getVisionRange()); // Use ceil for safety

        for (int dx = -visionRadius; dx <= visionRadius; dx++) {
            for (int dy = -visionRadius; dy <= visionRadius; dy++) {
                if (dx == 0 && dy == 0) continue;
                int checkX = x + dx;
                int checkY = y + dy;

                // Check distance using squared values for efficiency
                double distSq = dx * dx + dy * dy;
                if (distSq > visionRadius * visionRadius) { // Use radius derived from visionRange
                    continue;
                }
                 if (!world.isValidCoordinate(checkX, checkY)) {
                     continue;
                 }

                Tile tile = world.getTile(checkX, checkY);
                // Find the tile with the most food, preferring closer tiles slightly (implicitly by search order)
                if (tile != null && tile.getTerrainType() != TerrainType.WATER && tile.getPlantFoodValue() > maxFood) {
                    maxFood = tile.getPlantFoodValue();
                    bestCoords = new int[]{checkX, checkY};
                }
            }
        }
        return bestCoords;
    }

    private void eat(World world) {
        Tile currentTile = world.getTile(x, y);
        if (currentTile != null && currentTile.getPlantFoodValue() > 0) {
             // Eat a fixed amount or remaining amount, whichever is smaller
             double foodAvailable = currentTile.getPlantFoodValue();
             double amountToEat = Math.min(1.0, foodAvailable); // Eat up to 1 unit per tick
             double consumed = currentTile.consumePlantFood(amountToEat);
             if (consumed > 0) {
                 gainEnergy(consumed * EAT_ENERGY_GAIN_FACTOR);
                 // Check if full after eating
                 if (energy >= getMaxEnergy()) {
                     currentState = State.IDLE; // Become idle if full
                 }
             } else {
                 // Food source depleted unexpectedly
                 currentState = State.IDLE;
                 clearPath();
             }
        } else {
             // No food here anymore
             currentState = State.IDLE;
             clearPath();
        }
    }

    private void reproduce(Simulation simulation) {
        if (this.energy >= getReproductionThreshold()) {
             // Pass parent's genes (which include mutations) to spawnOffspring
             Entity offspring = simulation.spawnOffspring(this, SpeciesType.HERBIVORE, this.genes);
             if (offspring != null) {
                 depleteEnergy(getReproductionCost());
                 // State is reset in decideState after this method returns
             }
             // If spawning failed (e.g., no space), state will be reset in decideState
        }
         // If energy not sufficient, state will be reset in decideState
    }

    private void wander(Simulation simulation, World world) {
        // --- Speed Accumulator Logic for Wandering ---
        moveAccumulator += getSpeed(); // Add base speed to accumulator

        while (moveAccumulator >= 1.0) {
            int dx = random.nextInt(3) - 1; // -1, 0, or 1
            int dy = random.nextInt(3) - 1;

            if (dx == 0 && dy == 0) {
                // No movement chosen, but consume accumulator turn
                moveAccumulator -= 1.0;
                continue; // Try again next iteration if accumulator still >= 1.0
            }

            boolean moved = moveBy(simulation, dx, dy, world); // Use default speedMultiplier (1.0)

            if (!moved) {
                // Optional: could add logic to retry a different direction if blocked,
                // but for simple wandering, just failing the move is acceptable.
                // We still decrement the accumulator as an attempt was made.
            }

            moveAccumulator -= 1.0; // Decrement accumulator after move attempt
        }
    }

    private void followPath(Simulation simulation, World world) {
        if (currentPath == null || currentPath.isEmpty()) {
            // System.out.println("Path empty/null in followPath. State should change.");
            // State change is handled by decideState
            return;
        }

        // --- Speed Accumulator Logic for Following Path ---
        moveAccumulator += getSpeed(); // Add base speed

        while (moveAccumulator >= 1.0 && currentPath != null && !currentPath.isEmpty()) {
            int[] nextStep = currentPath.peek(); // Look at the next step
            int targetX = nextStep[0];
            int targetY = nextStep[1];

            // Calculate direction vector
            int dx = Integer.compare(targetX, x);
            int dy = Integer.compare(targetY, y);

            if (dx == 0 && dy == 0) {
                 // This case should ideally not happen if path is valid, but handle defensively
                 System.out.println("Warning: Path step is current location ("+x+","+y+"). Polling path.");
                 currentPath.poll(); // Remove the redundant step
                 moveAccumulator -= 1.0; // Consume accumulator turn
                 continue; // Check next step in the while loop
            }

            boolean moved = moveBy(simulation, dx, dy, world); // Use default speedMultiplier

            if (moved) {
                currentPath.poll(); // Successfully moved, remove step from path
                if (currentPath.isEmpty()) {
                    // Reached destination
                    // currentState = State.IDLE; // Let decideState handle next action
                    targetCoords = null; // Clear target coords as we've arrived
                    // Don't break here, allow using remaining accumulator if speed > 1
                }
            } else {
                // Path blocked
                System.out.println("Herbivore path blocked at ("+x+","+y+") -> ("+targetX+","+targetY+"). Recalculating...");
                pathRepathAttempts++;
                if (targetCoords != null && pathRepathAttempts <= MAX_REPATH_ATTEMPTS) {
                    if (!calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                        System.out.println("Path recalculation failed. Wandering.");
                        clearPath();
                        currentState = State.WANDERING; // Switch state immediately
                        moveAccumulator = 0; // Stop trying to move this tick
                        break; // Exit the while loop
                    } else {
                         // Successfully recalculated, loop will continue with new path
                         System.out.println("Path recalculated successfully.");
                    }
                } else {
                    System.out.println("Too many path recalculation attempts or no target. Wandering.");
                    clearPath();
                    currentState = State.WANDERING; // Switch state immediately
                    moveAccumulator = 0; // Stop trying to move this tick
                    break; // Exit the while loop
                }
            }
             moveAccumulator -= 1.0; // Decrement accumulator after move attempt
        }

         // If path finished mid-tick due to high speed, ensure state is updated
         if (currentPath != null && currentPath.isEmpty()) {
             currentState = State.IDLE; // Or whatever decideState determines next tick
             clearPath();
         }
    }

    private boolean calculatePath(Simulation simulation, World world, int targetX, int targetY) {
        List<int[]> pathList = pathfinder.findPath(world, x, y, targetX, targetY, this);
        if (pathList != null && !pathList.isEmpty()) {
            this.currentPath = new LinkedList<>(pathList);
            // Don't poll the first step, it represents the target tile of the first move
            return true;
        } else {
            this.currentPath = null;
            return false;
        }
    }

    private void clearPath() {
        this.currentPath = null;
        this.targetCoords = null;
        this.pathRepathAttempts = 0;
    }

    private void validateTargetPath(World world) {
        if ((currentState == State.FOLLOWING_PATH || currentState == State.SEEKING_FOOD) && targetCoords != null) {
            Tile targetTile = world.getTile(targetCoords[0], targetCoords[1]);
            // Invalidate if food gone or tile becomes invalid
            if (targetTile == null || targetTile.getPlantFoodValue() < 0.1 || targetTile.getTerrainType() == TerrainType.WATER) {
                // System.out.println("Herbivore path target invalidated. Clearing path.");
                clearPath();
                 // If was following path, switch to idle to re-evaluate. If was seeking, let decideState handle it.
                if (currentState == State.FOLLOWING_PATH) {
                     currentState = State.IDLE;
                }
            }
        }
    }

    /**
     * Attempts to move the entity by dx, dy. Called potentially multiple times per tick based on speed.
     * Energy cost is applied *per successful step*.
     * @return true if movement was successful.
     */
    private boolean moveBy(Simulation simulation, int dx, int dy, World world, double speedMultiplier) {
        int nextX = x + dx;
        int nextY = y + dy;

        if (world.isValidCoordinate(nextX, nextY)) {
            Tile targetTile = world.getTile(nextX, nextY);
            // Check terrain and if *another* entity occupies the target tile
            if (targetTile != null && targetTile.isPassable(this) &&
                !simulation.getEntityManager().isTileOccupiedByOther(nextX, nextY, this)) { // Check against others

                // Energy cost calculation: Base cost scaled by speed gene and any multiplier (like fleeing)
                // This reflects that moving faster costs more energy per step.
                double energyCost = MOVE_ENERGY_COST_FACTOR * getSpeed() * speedMultiplier;
                if (energy >= energyCost) { // Only move if enough energy for this step
                    depleteEnergy(energyCost); // Deplete energy *before* moving
                    setPosition(nextX, nextY); // Update position
                    return true;
                } else {
                    // Not enough energy to move this step, even if path is clear
                    return false;
                }
            }
        }
        return false; // Invalid coordinate, impassable terrain, or occupied
    }

    // Overload for default speed multiplier (1.0) used by wander/followPath
    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        return moveBy(simulation, dx, dy, world, 1.0);
    }

    // Helper for passability check in Pathfinder (optional)
    public boolean isTilePassable(Tile tile) {
        return tile != null && tile.getTerrainType() != TerrainType.WATER;
    }

} 
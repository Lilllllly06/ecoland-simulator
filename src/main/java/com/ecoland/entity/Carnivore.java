package com.ecoland.entity;

import com.ecoland.ai.Pathfinder;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.model.TerrainType;
import com.ecoland.simulation.Simulation;
import com.ecoland.simulation.EntityManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class Carnivore extends Entity {

    // Remove constants now in Genes
    private static final double BASE_ENERGY_DEPLETION = 0.15; // Base metabolic rate
    private static final double MOVE_ENERGY_COST_FACTOR = 0.06; // Cost per unit speed
    private static final double HUNT_ENERGY_GAIN_FACTOR = 0.8; // Base energy gain factor from prey
    private static final double ATTACK_DAMAGE = 35.0; // Potential gene?
    private static final double HUNGER_THRESHOLD_FACTOR = 0.5; // Hunger below 50% max energy
    private static final double ATTACK_RANGE = 1.5; // Potential gene?

    private static final Random random = new Random();

    // State and Pathfinding
    private enum State { IDLE, WANDERING, HUNTING, FOLLOWING_PATH, ATTACKING, REPRODUCING }
    private State currentState = State.IDLE;
    private Entity targetPrey = null;
    private LinkedList<int[]> currentPath = null;
    private final Pathfinder pathfinder = new Pathfinder();
    private int pathRepathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;

    /** Constructor for initial placement with default genes */
    public Carnivore(int x, int y) {
        super(x, y, SpeciesType.CARNIVORE);
    }

    /** Constructor for offspring with inherited genes */
    public Carnivore(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.CARNIVORE, new Genes(parentGenes));
    }

    /**
     * Update method for Carnivore.
     * Requires the Simulation instance for context (world, entity manager, spawning).
     * Requires the World instance for convenience.
     */
    @Override
    public void update(Simulation simulation, World world) {
        if (!isAlive) return;

        depleteEnergy(BASE_ENERGY_DEPLETION);
        if (!isAlive) return;

        // Invalidate target/path if prey died or moved too far
        validateTargetPath(world);

        decideState(simulation, world);

        switch (currentState) {
            case HUNTING: // State now primarily used to find target
                findAndSetTarget(simulation, world);
                break;
            case FOLLOWING_PATH:
                 followPath(simulation, world);
                 break;
            case ATTACKING:
                attack(simulation, world);
                break;
            case REPRODUCING:
                reproduce(simulation);
                decideState(simulation, world); // Re-evaluate state after trying
                break;
            case WANDERING:
            default:
                wander(simulation, world);
                break;
        }
    }

    private void validateTargetPath(World world) {
         if (targetPrey != null) {
             if (!targetPrey.isAlive()) {
                 System.out.println("Carnivore target died. Clearing target.");
                 clearTargetAndPath();
             } else if (isTooFar(targetPrey)) { // Check if prey moved out of vision range
                  System.out.println("Carnivore target moved too far. Clearing target.");
                  clearTargetAndPath();
             }
             // Optionally: Check if path is still valid (e.g., blocked)
             // This is partly handled by followPath needing recalculation
         }
         if (currentState == State.FOLLOWING_PATH && targetPrey == null) {
              System.out.println("Following path but targetPrey is null. Clearing path.");
              clearPath(); // Path is invalid without a target
              currentState = State.IDLE; // Re-evaluate needed
         }
    }

    // Combined state decision and finding prey if needed
    private void decideState(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();

         // If pathfinding, check if reached destination or path invalid
         if (currentState == State.FOLLOWING_PATH) {
             if (currentPath == null || currentPath.isEmpty()) {
                  // Reached destination (should be adjacent to prey)
                  currentState = State.IDLE; // Re-evaluate (should go to ATTACKING)
             } else if (targetPrey == null){ // Target lost while pathfinding
                  clearPath();
                  currentState = State.IDLE;
             } else {
                  return; // Continue following path
             }
         }

        // Check if target prey is still valid (redundant with validateTargetPath? Keep for safety)
        if (targetPrey != null && (!targetPrey.isAlive() || isTooFar(targetPrey))) {
            clearTargetAndPath();
        }

        double currentHungerThreshold = getMaxEnergy() * HUNGER_THRESHOLD_FACTOR;

        if (energy < currentHungerThreshold) {
            // If hungry and have a valid target
            if (targetPrey != null) {
                if (canAttack(targetPrey)) {
                    clearPath(); // Stop pathfinding if close enough to attack
                    currentState = State.ATTACKING;
                } else {
                    // Need to move closer - ensure path exists or calculate it
                    if (currentPath == null || currentPath.isEmpty()) {
                        if (!calculatePath(simulation, world, targetPrey.getX(), targetPrey.getY())) {
                             // Can't reach prey, maybe wander?
                             System.out.println("Carnivore cannot pathfind to prey. Wandering.");
                             clearTargetAndPath();
                             currentState = State.WANDERING;
                        } else {
                             currentState = State.FOLLOWING_PATH;
                        }
                    } else {
                         currentState = State.FOLLOWING_PATH; // Already have a path
                    }
                }
            } else {
                // Hungry but no target -> Hunt (find target)
                currentState = State.HUNTING;
            }
        } else if (energy >= getReproductionThreshold()) {
            clearTargetAndPath();
            currentState = State.REPRODUCING;
        } else {
            // Not hungry, not reproducing -> Wander
            clearTargetAndPath();
            currentState = State.WANDERING;
        }
    }

    // Renamed from hunt - now finds target and initiates pathfinding
    private void findAndSetTarget(Simulation simulation, World world) {
        targetPrey = findNearestPrey(simulation, world); // Use EntityManager via Simulation
        if (targetPrey != null) {
            System.out.println("Carnivore found prey at (" + targetPrey.getX() + "," + targetPrey.getY() + ")");
             pathRepathAttempts = 0;
             if (calculatePath(simulation, world, targetPrey.getX(), targetPrey.getY())) {
                 currentState = State.FOLLOWING_PATH;
             } else {
                  System.out.println("Carnivore failed to pathfind to new prey. Wandering.");
                  clearTargetAndPath();
                  currentState = State.WANDERING;
             }
        } else {
            // No prey found, continue wandering
            currentState = State.WANDERING;
        }
    }

    // Refactored findNearestPrey to use EntityManager
    private Entity findNearestPrey(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double currentVisionRange = getVisionRange();
        List<Entity> potentialPrey = entityManager.findEntitiesInRange(x, y, currentVisionRange, SpeciesType.HERBIVORE, world);

        Entity nearest = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (Entity prey : potentialPrey) {
            // TODO: Add LOS check here using world grid raycasting or similar
            double dx = prey.getX() - this.x;
            double dy = prey.getY() - this.y;
            double distSq = dx * dx + dy * dy;
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                nearest = prey;
            }
        }
        return nearest;
    }

     private void followPath(Simulation simulation, World world) {
         if (currentPath == null || currentPath.isEmpty() || targetPrey == null) {
             System.out.println("Path/Target invalid in followPath. Re-evaluating.");
             currentState = State.IDLE;
             clearTargetAndPath();
             return;
         }

         // Check if prey moved significantly, requiring repathing
         int[] lastKnownPreyPos = currentPath.getLast(); // Path target coords
         if (Math.abs(targetPrey.getX() - lastKnownPreyPos[0]) > 1 || Math.abs(targetPrey.getY() - lastKnownPreyPos[1]) > 1) {
              System.out.println("Prey moved significantly. Recalculating path...");
              pathRepathAttempts++;
              if (pathRepathAttempts <= MAX_REPATH_ATTEMPTS) {
                  if (!calculatePath(simulation, world, targetPrey.getX(), targetPrey.getY())) {
                      System.out.println("Path recalculation failed (prey moved). Wandering.");
                      clearTargetAndPath();
                      currentState = State.WANDERING;
                      return;
                  }
                  // If recalculation succeeds, continue with the new path in the next part of this method
              } else {
                    System.out.println("Too many path recalculation attempts (prey moved). Wandering.");
                    clearTargetAndPath();
                    currentState = State.WANDERING;
                    return;
              }
         }

         int[] nextStep = currentPath.peek();
         int targetX = nextStep[0];
         int targetY = nextStep[1];
         int dx = Integer.compare(targetX, x);
         int dy = Integer.compare(targetY, y);

         if (moveBy(simulation, dx, dy, world)) {
             currentPath.poll(); // Consumed step
             if (currentPath.isEmpty()) {
                 // Reached end of path (should be near prey)
                 currentState = State.IDLE; // Re-evaluate (likely -> ATTACKING)
                 clearPath(); // Clear path part, but keep targetPrey
             }
         } else {
             // Blocked, try recalculating
             System.out.println("Carnivore path blocked. Recalculating...");
             pathRepathAttempts++;
              if (pathRepathAttempts <= MAX_REPATH_ATTEMPTS) {
                 if (!calculatePath(simulation, world, targetPrey.getX(), targetPrey.getY())) {
                     System.out.println("Path recalculation failed (blocked). Wandering.");
                     clearTargetAndPath();
                     currentState = State.WANDERING;
                 }
             } else {
                  System.out.println("Too many path recalculation attempts (blocked). Wandering.");
                  clearTargetAndPath();
                  currentState = State.WANDERING;
             }
         }
     }

    // Attack method needs simulation context if it modifies world/other entities (it does)
    private void attack(Simulation simulation, World world) {
        if (targetPrey != null && targetPrey.isAlive() && canAttack(targetPrey)) {
            targetPrey.takeDamage(ATTACK_DAMAGE); // Apply damage

            if (!targetPrey.isAlive()) {
                gainEnergy(targetPrey.getMaxEnergy() * HUNT_ENERGY_GAIN_FACTOR); // Gain based on potential max energy?
                System.out.println("Carnivore successfully hunted prey. Energy: " + String.format("%.1f", energy));
                clearTargetAndPath(); // Hunt successful, clear target
                currentState = State.IDLE;
            } else {
                 // Prey survived, continue attacking if possible, otherwise re-evaluate
                 // DecideState will likely keep it in ATTACKING if still in range
                 currentState = State.IDLE; // Force re-evaluation
            }
        } else {
            // Target invalid or out of range
            clearTargetAndPath();
            currentState = State.IDLE;
        }
    }

    private boolean canAttack(Entity prey) {
        if (prey == null) return false;
        int dx = prey.getX() - this.x;
        int dy = prey.getY() - this.y;
        double distSq = dx*dx + dy*dy;
        return distSq <= ATTACK_RANGE * ATTACK_RANGE;
    }

    private boolean isTooFar(Entity prey) {
         if (prey == null) return true;
         int dx = prey.getX() - this.x;
         int dy = prey.getY() - this.y;
         double distSq = dx*dx + dy*dy;
         double currentVisionRange = getVisionRange();
         return distSq > currentVisionRange * currentVisionRange; // Too far if outside vision range
    }

    // Wander needs simulation context now
    private void wander(Simulation simulation, World world) {
        int attempts = 0;
        int maxAttempts = 8;
        while (attempts < maxAttempts) {
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            if (dx == 0 && dy == 0) {
                attempts++; continue;
            }
            if (moveBy(simulation, dx, dy, world)) {
                return;
            }
            attempts++;
        }
    }

    // Helper to calculate path
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

    // Helper to clear target and path info
    private void clearTargetAndPath(){
        this.targetPrey = null;
        this.currentPath = null;
        this.pathRepathAttempts = 0;
    }
     // Helper to clear just path
    private void clearPath(){
        this.currentPath = null;
        this.pathRepathAttempts = 0;
    }

    // MoveBy methods need simulation context
    private boolean moveBy(Simulation simulation, int dx, int dy, World world, double speedMultiplier) {
        int nextX = x + dx;
        int nextY = y + dy;
        if (world.isValidCoordinate(nextX, nextY)) {
            Tile targetTile = world.getTile(nextX, nextY);
            if (targetTile != null && targetTile.getTerrainType() != TerrainType.WATER &&
                !simulation.getEntityManager().isTileOccupied(nextX, nextY)) {
                setPosition(nextX, nextY);
                depleteEnergy(MOVE_ENERGY_COST_FACTOR * getSpeed() * speedMultiplier);
                return true;
            }
        }
        return false;
    }

    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        return moveBy(simulation, dx, dy, world, 1.0);
    }

    private void reproduce(Simulation simulation) {
        if (this.energy >= getReproductionThreshold()) {
            Entity offspring = simulation.spawnOffspring(this, SpeciesType.CARNIVORE);
            if (offspring != null) {
                 depleteEnergy(getReproductionCost());
                 currentState = State.IDLE; // Transition out of reproducing
            } else {
                 currentState = State.WANDERING; // Failed to reproduce, wander
            }
        } else {
             currentState = State.WANDERING; // Not enough energy
        }
    }

    // TODO:
    // - Refine energy gain from hunting
    // - Implement fleeing behaviour (e.g., from stronger carnivores?)
    // - Integrate with EntityManager
    // - Add Line-of-Sight checks for hunting
} 
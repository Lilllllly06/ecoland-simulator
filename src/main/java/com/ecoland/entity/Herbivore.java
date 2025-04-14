package com.ecoland.entity;

import com.ecoland.ai.Pathfinder;
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

    // Remove constants that are now part of Genes (MAX_ENERGY, thresholds, etc.)
    private static final double BASE_ENERGY_DEPLETION = 0.1; // Base metabolic rate
    private static final double MOVE_ENERGY_COST_FACTOR = 0.05; // Energy cost per unit speed
    private static final double EAT_ENERGY_GAIN_FACTOR = 5.0; // Base energy gained per unit of food eaten
    private static final double HUNGER_THRESHOLD_FACTOR = 0.5; // Hunger below 50% of max energy
    private static final double PREDATOR_DETECTION_RANGE_FACTOR = 1.0; // Detect predators within vision range
    private static final double FLEE_SPEED_BOOST = 1.2; // Move slightly faster when fleeing

    private static final Random random = new Random();

    // State and Pathfinding data
    private enum State { IDLE, WANDERING, SEEKING_FOOD, FOLLOWING_PATH, EATING, REPRODUCING, FLEEING }
    private State currentState = State.IDLE;
    private int[] targetCoords = null;
    private LinkedList<int[]> currentPath = null;
    private final Pathfinder pathfinder = new Pathfinder();
    private int pathRepathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;

    /** Constructor for initial placement with default genes */
    public Herbivore(int x, int y) {
        super(x, y, SpeciesType.HERBIVORE);
    }

     /** Constructor for offspring with inherited genes */
     public Herbivore(int x, int y, Genes parentGenes) {
         super(x, y, SpeciesType.HERBIVORE, new Genes(parentGenes));
     }

    @Override
    public void update(Simulation simulation, World world) {
        if (!isAlive) return;

        // Base energy depletion (influenced by efficiency)
        depleteEnergy(BASE_ENERGY_DEPLETION);
        if (!isAlive) return;

        // Invalidate path if target is no longer valid (e.g., food eaten by someone else)
        validateTargetPath(world);

        decideState(simulation, world);

        switch (currentState) {
            case FLEEING:
                flee(simulation, world);
                break;
            case SEEKING_FOOD:
                seekFood(simulation, world);
                break;
            case FOLLOWING_PATH:
                followPath(simulation, world);
                break;
            case EATING:
                eat(world);
                break;
            case REPRODUCING:
                reproduce(simulation);
                decideState(simulation, world);
                break;
            case WANDERING:
            default:
                wander(simulation, world);
                break;
        }
    }

    private void decideState(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        double currentVisionRange = getVisionRange();
        double predatorDetectionRange = currentVisionRange * PREDATOR_DETECTION_RANGE_FACTOR;

        // --- Check for Predators --- (Highest Priority)
        List<Entity> nearbyPredators = entityManager.findEntitiesInRange(x, y, predatorDetectionRange, SpeciesType.CARNIVORE, world);
        if (!nearbyPredators.isEmpty()) {
            clearPath();
            currentState = State.FLEEING;
            return; // Fleeing overrides other states
        }

        // Check if currently following a path
        if (currentState == State.FOLLOWING_PATH) {
             if (currentPath == null || currentPath.isEmpty()) {
                 // Reached destination or path lost, re-evaluate
                 currentState = State.IDLE;
             } else {
                 return; // Continue following path
             }
        }

        // --- Normal State Logic --- (If no predators nearby)
        Tile currentTile = world.getTile(x, y);
        boolean canEatHere = currentTile != null && currentTile.getPlantFoodValue() > 0.1;
        double currentHungerThreshold = getMaxEnergy() * HUNGER_THRESHOLD_FACTOR;

        if (energy < currentHungerThreshold) {
            if (canEatHere) {
                clearPath();
                currentState = State.EATING;
            } else {
                if (currentState != State.SEEKING_FOOD) {
                    clearPath();
                    currentState = State.SEEKING_FOOD;
                }
            }
        } else if (energy >= getReproductionThreshold()) {
            clearPath();
            currentState = State.REPRODUCING;
        } else {
            if (currentState != State.EATING || !canEatHere) {
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
            currentState = State.IDLE;
            return;
        }

        double avgPredatorX = 0, avgPredatorY = 0;
        for (Entity predator : nearbyPredators) {
            avgPredatorX += predator.getX();
            avgPredatorY += predator.getY();
        }
        avgPredatorX /= nearbyPredators.size();
        avgPredatorY /= nearbyPredators.size();

        double fleeVectorX = x - avgPredatorX;
        double fleeVectorY = y - avgPredatorY;

        double magnitude = Math.sqrt(fleeVectorX * fleeVectorX + fleeVectorY * fleeVectorY);
        int dx = 0, dy = 0;
        if (magnitude > 0.1) {
            dx = (int) Math.round(fleeVectorX / magnitude);
            dy = (int) Math.round(fleeVectorY / magnitude);
        } else {
            dx = random.nextInt(3) - 1;
            dy = random.nextInt(3) - 1;
        }
        if (dx == 0 && dy == 0) dx = (random.nextBoolean() ? 1 : -1);

        if (!moveBy(simulation, dx, dy, world, FLEE_SPEED_BOOST)) {
            int attempts = 0;
            while (attempts < 5) {
                int rdx = random.nextInt(3) - 1;
                int rdy = random.nextInt(3) - 1;
                if (rdx == 0 && rdy == 0) continue;
                if (moveBy(simulation, rdx, rdy, world, FLEE_SPEED_BOOST)) return;
                attempts++;
            }
        }
    }

    private void seekFood(Simulation simulation, World world) {
        targetCoords = findBestFoodSourceCoords(world);
        if (targetCoords != null) {
            pathRepathAttempts = 0;
            if (calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                currentState = State.FOLLOWING_PATH;
            } else {
                System.out.println("Herbivore failed to pathfind to food at (" + targetCoords[0] + "," + targetCoords[1] + "). Wandering.");
                targetCoords = null;
                currentState = State.WANDERING;
            }
        } else {
            currentState = State.WANDERING;
        }
    }

    private int[] findBestFoodSourceCoords(World world) {
        int[] bestCoords = null;
        double maxFood = 0;
        int visionRadius = (int) getVisionRange();

        for (int dx = -visionRadius; dx <= visionRadius; dx++) {
            for (int dy = -visionRadius; dy <= visionRadius; dy++) {
                if (dx == 0 && dy == 0) continue;
                int checkX = x + dx;
                int checkY = y + dy;

                double distSq = dx * dx + dy * dy;
                if (distSq > getVisionRange() * getVisionRange() || !world.isValidCoordinate(checkX, checkY)) {
                    continue;
                }

                Tile tile = world.getTile(checkX, checkY);
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
            double foodToEat = 1.0;
            double consumed = currentTile.consumePlantFood(foodToEat);
            if (consumed > 0) {
                gainEnergy(consumed * EAT_ENERGY_GAIN_FACTOR);
            } else {
                currentState = State.IDLE;
                clearPath();
            }
        } else {
            currentState = State.IDLE;
            clearPath();
        }
    }

    private void reproduce(Simulation simulation) {
        if (this.energy >= getReproductionThreshold()) {
            Entity offspring = simulation.spawnOffspring(this, SpeciesType.HERBIVORE);
            if (offspring != null) {
                depleteEnergy(getReproductionCost());
                currentState = State.IDLE;
                clearPath();
            } else {
                currentState = State.WANDERING;
                clearPath();
            }
        } else {
            currentState = State.WANDERING;
            clearPath();
        }
    }

    private void wander(Simulation simulation, World world) {
        int attempts = 0;
        int maxAttempts = 8;
        while (attempts < maxAttempts) {
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            if (dx == 0 && dy == 0) {
                attempts++;
                continue;
            }
            if (moveBy(simulation, dx, dy, world)) {
                return;
            }
            attempts++;
        }
    }

    private void followPath(Simulation simulation, World world) {
        if (currentPath == null || currentPath.isEmpty()) {
            System.out.println("Path empty or null in followPath. Re-evaluating.");
            currentState = State.IDLE;
            clearPath();
            return;
        }

        int[] nextStep = currentPath.peek();
        int targetX = nextStep[0];
        int targetY = nextStep[1];

        int dx = Integer.compare(targetX, x);
        int dy = Integer.compare(targetY, y);

        if (moveBy(simulation, dx, dy, world)) {
            currentPath.poll();
            if (currentPath.isEmpty()) {
                currentState = State.IDLE;
                clearPath();
            }
        } else {
            System.out.println("Herbivore path blocked at ("+x+","+y+") -> ("+targetX+","+targetY+"). Recalculating...");
            pathRepathAttempts++;
            if (targetCoords != null && pathRepathAttempts <= MAX_REPATH_ATTEMPTS) {
                if (!calculatePath(simulation, world, targetCoords[0], targetCoords[1])) {
                    System.out.println("Path recalculation failed. Wandering.");
                    clearPath();
                    currentState = State.WANDERING;
                }
            } else {
                System.out.println("Too many path recalculation attempts. Wandering.");
                clearPath();
                currentState = State.WANDERING;
            }
        }
    }

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

    private void clearPath() {
        this.currentPath = null;
        this.targetCoords = null;
        this.pathRepathAttempts = 0;
    }

    private void validateTargetPath(World world) {
        if (currentState == State.FOLLOWING_PATH && targetCoords != null) {
            Tile targetTile = world.getTile(targetCoords[0], targetCoords[1]);
            if (targetTile == null || targetTile.getPlantFoodValue() < 0.1) {
                System.out.println("Herbivore path target invalidated (food gone?). Clearing path.");
                clearPath();
                currentState = State.IDLE;
            }
        }
    }

    /**
     * Attempts to move the entity by dx, dy, checking for passability and occupation.
     * @param simulation Simulation context for EntityManager access.
     * @param dx Change in x.
     * @param dy Change in y.
     * @param world World model.
     * @param speedMultiplier Factor to adjust speed/energy cost (e.g., for fleeing).
     * @return true if movement was successful.
     */
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

    // Overload for default speed multiplier (1.0)
    private boolean moveBy(Simulation simulation, int dx, int dy, World world) {
        return moveBy(simulation, dx, dy, world, 1.0);
    }

    // TODO:
    // - Implement predator avoidance
    // - Implement proper pathfinding (A*)
    // - Integrate with EntityManager for finding mates/other entities
} 
package com.ecoland.entity;

import com.ecoland.ai.Pathfinder;
import com.ecoland.ai.nn.AnimalBrain;
import com.ecoland.ai.nn.DecomposerBrain;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.model.TerrainType;
import com.ecoland.simulation.Simulation;
import com.ecoland.simulation.EntityManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class Decomposer extends Entity {

    // Constants
    private static final double BASE_ENERGY_DEPLETION = 0.05;
    private static final double MOVE_ENERGY_COST_FACTOR = 0.03;
    private static final double DECOMPOSE_ENERGY_GAIN_FACTOR = 4.0;
    private static final double FERTILITY_BOOST_FACTOR = 0.05;
    private static final double PREDATOR_DETECTION_RANGE_FACTOR = 0.8;
    private static final double FLEE_SPEED_BOOST = 1.1;

    private static final Random random = new Random();

    // State and Pathfinding
    private enum State { IDLE, WANDERING, SEEKING_FOOD, FOLLOWING_PATH, DECOMPOSING, REPRODUCING, FLEEING, NEURAL }
    private State currentState = State.IDLE;
    private int[] targetCoords = null;
    private List<int[]> currentPath = null;
    private final Pathfinder pathfinder = new Pathfinder();
    private int pathRepathAttempts = 0;
    private static final int MAX_REPATH_ATTEMPTS = 3;

    // Movement Accumulator for Speed Gene
    private double moveAccumulator = 0.0;
    
    // Flag to use neural network or traditional behavior
    private boolean useNeuralBehavior = true;

    /** Constructor for initial placement */
    public Decomposer(int x, int y) {
        super(x, y, SpeciesType.DECOMPOSER);
        this.brain = new DecomposerBrain((int)getVisionRange());
    }

    /** Constructor for offspring */
    public Decomposer(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.DECOMPOSER, new Genes(parentGenes));
        this.brain = new DecomposerBrain((int)getVisionRange());
    }
    
    /** Constructor for offspring with inherited brain */
    public Decomposer(int x, int y, Genes parentGenes, AnimalBrain parentBrain) {
        super(x, y, SpeciesType.DECOMPOSER, new Genes(parentGenes), 
              parentBrain);
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
        EntityManager entityManager = simulation.getEntityManager();
        
        // Make a decision based on sensory inputs
        AnimalBrain.BrainDecision decision = brain.makeDecision(this, world, entityManager);
        
        // Check if the decision is to eat (decompose)
        if (decision.eat) {
            decomposeDeadOrganics(simulation, world);
        }
        
        // Check if the decision is to reproduce
        if (decision.reproduce && energy > genes.reproductionThreshold) {
            tryReproduce(simulation);
        }
        
        // Apply movement decision
        if (decision.moveX != 0 || decision.moveY != 0) {
            tryMove(x + decision.moveX, y + decision.moveY, simulation, world);
        }
    }
    
    /**
     * Traditional rule-based behavior update.
     */
    private void updateTraditionalBehavior(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        
        // Check for predators first (survival priority)
        if (checkForPredators(simulation, world)) {
            return; // Already took flee action
        }
        
        // If energy is high enough, try to reproduce
        if (energy > genes.reproductionThreshold) {
            boolean reproduced = tryReproduce(simulation);
            if (reproduced) return;
        }
        
        // Look for dead organisms to decompose based on current state
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
                if (random.nextDouble() < 0.2) {
                    currentState = State.SEEKING_FOOD;
                }
                break;
                
            case SEEKING_FOOD:
                // Look for dead organisms or organic matter
                boolean foundTarget = findAndTargetDeadOrganics(entityManager, world);
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
                
                // Check if we've reached the target
                if (x == targetCoords[0] && y == targetCoords[1]) {
                    decomposeDeadOrganics(simulation, world);
                    currentState = State.DECOMPOSING;
                    break;
                }
                
                // Follow current path
                followPath(simulation, world);
                break;
                
            case DECOMPOSING:
                // Continue decomposing for a few ticks
                decomposeDeadOrganics(simulation, world);
                if (random.nextDouble() < 0.3) {
                    currentState = State.SEEKING_FOOD;
                }
                break;
                
            case REPRODUCING:
                // Try to reproduce, then return to seeking food
                boolean success = tryReproduce(simulation);
                currentState = State.SEEKING_FOOD;
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
     * Wander randomly around the environment.
     */
    private void wander(Simulation simulation, World world) {
        // Random direction
        int dx = random.nextInt(3) - 1; // -1, 0, or 1
        int dy = random.nextInt(3) - 1; // -1, 0, or 1
        
        // Try to move in that direction
        tryMove(x + dx, y + dy, simulation, world);
    }
    
    /**
     * Find dead organisms or areas with high organic content.
     */
    private boolean findAndTargetDeadOrganics(EntityManager entityManager, World world) {
        // Look for dead entities within vision range
        List<Entity> nearbyEntities = entityManager.findEntitiesInRange(x, y, genes.visionRange, world);
        List<Entity> deadEntities = new ArrayList<>();
        
        for (Entity entity : nearbyEntities) {
            if (!entity.isAlive() && entity.getSpeciesType() != SpeciesType.PLANT && entity != this) {
                deadEntities.add(entity);
            }
        }
        
        if (!deadEntities.isEmpty()) {
            // Find the closest dead entity
            Entity closestDead = null;
            double minDistance = Double.MAX_VALUE;
            
            for (Entity dead : deadEntities) {
                double dx = dead.getX() - x;
                double dy = dead.getY() - y;
                double distance = Math.sqrt(dx*dx + dy*dy);
                
                if (distance < minDistance) {
                    minDistance = distance;
                    closestDead = dead;
                }
            }
            
            if (closestDead != null) {
                setTarget(closestDead.getX(), closestDead.getY(), world);
                return true;
            }
        }
        
        // If no dead entities, look for tiles with high organic content
        // (Here we would ideally check for high organic content, but for simplicity,
        // we'll just pick a random valid tile within range)
        List<int[]> validTiles = new ArrayList<>();
        int range = (int)Math.ceil(genes.visionRange);
        
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (world.isValidCoordinate(nx, ny) && 
                    world.getTile(nx, ny).getTerrainType() != TerrainType.WATER) {
                    validTiles.add(new int[]{nx, ny});
                }
            }
        }
        
        if (!validTiles.isEmpty()) {
            int[] randomTile = validTiles.get(random.nextInt(validTiles.size()));
            setTarget(randomTile[0], randomTile[1], world);
            return true;
        }
        
        return false;
    }
    
    /**
     * Set a target location and calculate a path to it.
     */
    private void setTarget(int targetX, int targetY, World world) {
        targetCoords = new int[]{targetX, targetY};
        
        // Calculate path to target
        currentPath = pathfinder.findPath(world, x, y, targetX, targetY, this);
        pathRepathAttempts = 0;
    }
    
    /**
     * Follow the current path to the target.
     */
    private void followPath(Simulation simulation, World world) {
        if (currentPath == null || currentPath.isEmpty()) {
            currentState = State.SEEKING_FOOD;
            return;
        }
        
        // Get the next step in the path
        int[] nextStep = currentPath.get(0);
        currentPath.remove(0); // Remove after using
        
        // Check if the step is valid
        if (world.isValidCoordinate(nextStep[0], nextStep[1]) && 
            world.getTile(nextStep[0], nextStep[1]).getTerrainType() != TerrainType.WATER &&
            !simulation.getEntityManager().isTileOccupiedByOther(nextStep[0], nextStep[1], this)) {
            
            // Move to the next step
            tryMove(nextStep[0], nextStep[1], simulation, world);
        } else {
            // Path is blocked, try to recalculate a few times before giving up
            if (pathRepathAttempts < MAX_REPATH_ATTEMPTS && targetCoords != null) {
                currentPath = pathfinder.findPath(world, x, y, targetCoords[0], targetCoords[1], this);
                pathRepathAttempts++;
            } else {
                // Give up on this target
                currentState = State.SEEKING_FOOD;
                targetCoords = null;
                currentPath = null;
            }
        }
    }
    
    /**
     * Decompose dead organisms or organic matter, gaining energy and 
     * increasing tile fertility.
     */
    private void decomposeDeadOrganics(Simulation simulation, World world) {
        // Check if there's a dead entity here to decompose
        Entity deadEntity = null;
        List<Entity> entitiesHere = simulation.getEntityManager().findEntitiesInRange(x, y, 0.5, world);
        
        for (Entity entity : entitiesHere) {
            if (!entity.isAlive() && entity != this && entity.getSpeciesType() != SpeciesType.PLANT) {
                deadEntity = entity;
                break;
            }
        }
        
        if (deadEntity != null) {
            // Decompose the dead entity
            simulation.getEntityManager().removeEntity(deadEntity);
            
            // Gain energy from decomposition
            double energyGain = DECOMPOSE_ENERGY_GAIN_FACTOR * genes.energyEfficiency;
            gainEnergy(energyGain);
            
            // Increase soil fertility in this tile
            Tile currentTile = world.getTile(x, y);
            double newFertility = Math.min(1.0, currentTile.getFertility() + FERTILITY_BOOST_FACTOR);
            currentTile.setFertility(newFertility);
            
            // Log the decomposition
            System.out.println("Decomposer at (" + x + "," + y + ") decomposed a " + 
                               deadEntity.getSpeciesType() + ", gained " + String.format("%.2f", energyGain) + 
                               " energy, new fertility: " + String.format("%.2f", newFertility));
            
            // Return to seeking food state
            currentState = State.SEEKING_FOOD;
        } else {
            // No dead entity here, just improve soil fertility slightly
            Tile currentTile = world.getTile(x, y);
            double newFertility = Math.min(1.0, currentTile.getFertility() + FERTILITY_BOOST_FACTOR * 0.2);
            currentTile.setFertility(newFertility);
        }
    }
    
    /**
     * Try to reproduce if we have enough energy.
     */
    private boolean tryReproduce(Simulation simulation) {
        if (energy < genes.reproductionThreshold) {
            return false;
        }
        
        // Spend energy to reproduce
        energy -= genes.reproductionCost;
        
        // Create offspring with possibly mutated genes
        Entity offspring = simulation.spawnOffspring(this, SpeciesType.DECOMPOSER, new Genes(genes));
        
        return offspring != null;
    }
    
    /**
     * Try to move to a new location.
     */
    private void tryMove(int newX, int newY, Simulation simulation, World world) {
        // Check if we can actually move this tick based on our speed gene
        moveAccumulator += genes.speed;
        if (moveAccumulator < 1.0) {
            return; // Not enough accumulated movement points
        }
        moveAccumulator -= 1.0; // Consume one movement point
        
        // Check if the destination is valid
        if (!world.isValidCoordinate(newX, newY) || 
            world.getTile(newX, newY).getTerrainType() == TerrainType.WATER ||
            simulation.getEntityManager().isTileOccupiedByOther(newX, newY, this)) {
            return;
        }
        
        // Calculate energy cost for movement
        double baseMoveCost = MOVE_ENERGY_COST_FACTOR * genes.speed;
        
        // Additional costs based on terrain
        TerrainType terrain = world.getTile(newX, newY).getTerrainType();
        double terrainFactor = 1.0;
        if (terrain == TerrainType.HILL) {
            terrainFactor = 2.0;
        } else if (terrain == TerrainType.FOREST) {
            terrainFactor = 1.2;
        } else {
            terrainFactor = 1.0;
        }
        
        // Deplete energy based on movement cost
        depleteEnergy(baseMoveCost * terrainFactor);
        if (!isAlive) return;
        
        // Update position
        x = newX;
        y = newY;
    }
    
    /**
     * Check for nearby predators and flee if necessary.
     * @return true if fleeing action was taken
     */
    private boolean checkForPredators(Simulation simulation, World world) {
        double predatorDetectionRange = genes.visionRange * PREDATOR_DETECTION_RANGE_FACTOR;
        
        // Look for carnivores or apex predators
        List<Entity> nearbyPredators = new ArrayList<>();
        for (Entity entity : simulation.getEntityManager().findEntitiesInRange(x, y, predatorDetectionRange, world)) {
            if (entity.isAlive() && 
                (entity.getSpeciesType() == SpeciesType.CARNIVORE || 
                 entity.getSpeciesType() == SpeciesType.APEX_PREDATOR)) {
                nearbyPredators.add(entity);
            }
        }
        
        if (!nearbyPredators.isEmpty()) {
            // Found a predator, start fleeing
            currentState = State.FLEEING;
            flee(simulation, world);
            return true;
        }
        
        return false;
    }
    
    /**
     * Flee from nearby predators.
     */
    private void flee(Simulation simulation, World world) {
        EntityManager entityManager = simulation.getEntityManager();
        // Find the nearest predator
        Entity nearestPredator = null;
        double minDistance = Double.MAX_VALUE;
        
        double predatorDetectionRange = genes.visionRange * PREDATOR_DETECTION_RANGE_FACTOR;
        for (Entity entity : entityManager.findEntitiesInRange(x, y, predatorDetectionRange, world)) {
            if (entity.isAlive() && 
                (entity.getSpeciesType() == SpeciesType.CARNIVORE || 
                 entity.getSpeciesType() == SpeciesType.APEX_PREDATOR)) {
                
                double dx = entity.getX() - x;
                double dy = entity.getY() - y;
                double distance = Math.sqrt(dx*dx + dy*dy);
                
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPredator = entity;
                }
            }
        }
        
        if (nearestPredator != null) {
            // Calculate direction away from predator
            int fleeX = x;
            int fleeY = y;
            
            int predatorX = nearestPredator.getX();
            int predatorY = nearestPredator.getY();
            
            // Move in the opposite direction
            if (x < predatorX) fleeX = x - 1;
            else if (x > predatorX) fleeX = x + 1;
            
            if (y < predatorY) fleeY = y - 1;
            else if (y > predatorY) fleeY = y + 1;
            
            // Apply speed boost when fleeing
            moveAccumulator += FLEE_SPEED_BOOST;
            
            // Try to move to flee location
            tryMove(fleeX, fleeY, simulation, world);
        }
    }

    /**
     * Sets whether to use neural behavior for this decomposer.
     * @param useNeural true to use neural behavior, false for traditional
     */
    public void setUseNeuralBehavior(boolean useNeural) {
        this.useNeuralBehavior = useNeural;
    }
} 
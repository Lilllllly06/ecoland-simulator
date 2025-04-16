package com.ecoland.simulation;

import com.ecoland.common.Constants;
import com.ecoland.data.DataLogger; // Import DataLogger
import com.ecoland.entity.*;
import com.ecoland.generator.WorldGenerator; // Import WorldGenerator
import com.ecoland.model.World;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.BiomeType;

import java.io.*;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList; // Consider thread safety if needed
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Simulation implements Serializable {
    private static final long serialVersionUID = 1L;

    // The world (2D grid of tiles)
    private final World world;
    
    // Entity manager for tracking all entities
    private final EntityManager entityManager;
    
    // Data logger for tracking simulation statistics
    private final DataLogger dataLogger;
    
    // Current simulation tick
    private long currentTick = 0;
    
    // Random generator for various operations
    private final Random random = new Random();
    
    // Initial population settings
    private final int initialHerbivoreCount;
    private final int initialCarnivoreCount;
    private final int initialPlantCount;
    private final int initialOmnivoreCount;
    private final int initialScavengerCount;
    private final int initialApexPredatorCount;
    private final int initialDecomposerCount;

    // Class to represent the complete state of the simulation
    public static class SimulationState implements Serializable {
        private static final long serialVersionUID = 1L;
        
        // World state
        private final int worldWidth;
        private final int worldHeight;
        private final Tile[][] worldGrid;
        
        // Entity state
        private final List<EntityState> entities;
        
        // Simulation metadata
        private final long tick;
        private final int herbivoreCount;
        private final int carnivoreCount;
        private final int plantCount;
        private final int omnivoreCount;
        private final int scavengerCount;
        private final int apexPredatorCount;
        private final int decomposerCount;
        private final long savedTimestamp;
        
        public SimulationState(World world, List<Entity> entities, long tick, 
                              int herbivoreCount, int carnivoreCount, int plantCount,
                              int omnivoreCount, int scavengerCount, int apexPredatorCount, int decomposerCount) {
            this.worldWidth = world.getWidth();
            this.worldHeight = world.getHeight();
            this.worldGrid = new Tile[worldWidth][worldHeight];
            
            // Copy all tiles
            for (int x = 0; x < worldWidth; x++) {
                for (int y = 0; y < worldHeight; y++) {
                    this.worldGrid[x][y] = world.getTile(x, y);
                }
            }
            
            // Save entity states
            this.entities = new ArrayList<>();
            for (Entity entity : entities) {
                if (entity.isAlive()) {
                    this.entities.add(new EntityState(entity));
                }
            }
            
            this.tick = tick;
            this.herbivoreCount = herbivoreCount;
            this.carnivoreCount = carnivoreCount;
            this.plantCount = plantCount;
            this.omnivoreCount = omnivoreCount;
            this.scavengerCount = scavengerCount;
            this.apexPredatorCount = apexPredatorCount;
            this.decomposerCount = decomposerCount;
            this.savedTimestamp = System.currentTimeMillis();
        }
        
        // Getters for saved state
        public int getWorldWidth() { return worldWidth; }
        public int getWorldHeight() { return worldHeight; }
        public Tile[][] getWorldGrid() { return worldGrid; }
        public List<EntityState> getEntities() { return entities; }
        public long getTick() { return tick; }
        public long getSavedTimestamp() { return savedTimestamp; }
        public int getHerbivoreCount() { return herbivoreCount; }
        public int getCarnivoreCount() { return carnivoreCount; }
        public int getPlantCount() { return plantCount; }
        public int getOmnivoreCount() { return omnivoreCount; }
        public int getScavengerCount() { return scavengerCount; }
        public int getApexPredatorCount() { return apexPredatorCount; }
        public int getDecomposerCount() { return decomposerCount; }
    }
    
    // Class to represent the state of a single entity
    public static class EntityState implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final int x;
        private final int y;
        private final double energy;
        private final double health;
        private final SpeciesType speciesType;
        private final Genes genes;
        
        public EntityState(Entity entity) {
            this.x = entity.getX();
            this.y = entity.getY();
            this.energy = entity.getEnergy();
            this.health = entity.getHealth();
            this.speciesType = entity.getSpeciesType();
            this.genes = entity.getGenes();
        }
        
        // Create an actual entity from this state
        public Entity createEntity() {
            Entity entity = null;
            switch (speciesType) {
                case HERBIVORE:
                    entity = new Herbivore(x, y, genes);
                    break;
                case CARNIVORE:
                    entity = new Carnivore(x, y, genes);
                    break;
                case PLANT:
                    entity = new Plant(x, y, genes);
                    break;
                case DECOMPOSER:
                    entity = new Decomposer(x, y, genes);
                    break;
                case APEX_PREDATOR:
                    entity = new ApexPredator(x, y, genes);
                    break;
                case OMNIVORE:
                    entity = new Omnivore(x, y, genes);
                    break;
            }
            
            if (entity != null) {
                // Set energy and health directly using reflection
                try {
                    java.lang.reflect.Field energyField = Entity.class.getDeclaredField("energy");
                    energyField.setAccessible(true);
                    energyField.set(entity, energy);
                    
                    java.lang.reflect.Field healthField = Entity.class.getDeclaredField("health");
                    healthField.setAccessible(true);
                    healthField.set(entity, health);
                } catch (Exception e) {
                    System.err.println("Error restoring entity state: " + e.getMessage());
                }
            }
            
            return entity;
        }
    }

    /**
     * Create a new Simulation with a world of the given dimensions.
     * 
     * @param width Width of the world
     * @param height Height of the world
     * @param herbivoreCount Initial number of herbivores
     * @param carnivoreCount Initial number of carnivores
     * @param generator Optional world generator to use (if null, a default will be used)
     */
    public Simulation(int width, int height, int herbivoreCount, int carnivoreCount, 
                      int omnivoreCount, int scavengerCount, int apexPredatorCount, int decomposerCount,
                      WorldGenerator generator) {
        // Create the world
        this.world = new World(width, height);
        
        // Use the provided generator or create a default one
        WorldGenerator worldGen = (generator != null) ? generator : WorldGenerator.createDefaultGenerator();
        worldGen.generate(world);
        
        // Create entity manager
        this.entityManager = new EntityManager();
        
        // Set initial population counts
        this.initialHerbivoreCount = herbivoreCount;
        this.initialCarnivoreCount = carnivoreCount;
        this.initialPlantCount = 0; // Plants are handled by the world tiles
        this.initialOmnivoreCount = omnivoreCount;
        this.initialScavengerCount = scavengerCount;
        this.initialApexPredatorCount = apexPredatorCount;
        this.initialDecomposerCount = decomposerCount;
        
        // Initialize the data logger to record every 10 ticks
        this.dataLogger = new DataLogger(10);
        
        // Create initial population
        initializePopulation();
        
        // Record initial state
        dataLogger.recordTick(currentTick, entityManager);
    }
    
    /**
     * Create a new Simulation with default initial population values.
     * 
     * @param width Width of the world
     * @param height Height of the world
     */
    public Simulation(int width, int height) {
        this(width, height, 20, 5, 0, 0, 0, 5, null);
    }
    
    /**
     * Create a new Simulation with specified herbivore and carnivore counts.
     * 
     * @param width Width of the world
     * @param height Height of the world
     * @param herbivoreCount Initial number of herbivores
     * @param carnivoreCount Initial number of carnivores
     */
    public Simulation(int width, int height, int herbivoreCount, int carnivoreCount) {
        this(width, height, herbivoreCount, carnivoreCount, 0, 0, 0, 0, null);
    }

    /**
     * Create a simulation from a saved simulation state.
     * 
     * @param state The saved simulation state to restore
     */
    public Simulation(SimulationState state) {
        // Create the world with the dimensions from the state
        this.world = new World(state.getWorldWidth(), state.getWorldHeight());
        
        // Copy all tile data from the state
        for (int x = 0; x < state.getWorldWidth(); x++) {
            for (int y = 0; y < state.getWorldHeight(); y++) {
                world.setTile(x, y, state.getWorldGrid()[x][y]);
            }
        }
        
        // Create entity manager
        this.entityManager = new EntityManager();
        
        // Restore entity states
        for (EntityState entityState : state.getEntities()) {
            Entity entity = entityState.createEntity();
            if (entity != null) {
                entityManager.addEntity(entity);
            }
        }
        
        // Process entity additions immediately (unlike normal initialization)
        entityManager.updateEntityList();
        
        // Initialize data logger
        this.dataLogger = new DataLogger(10);
        
        // Set current tick from state
        this.currentTick = state.getTick();
        
        // Restore counts for information
        this.initialHerbivoreCount = state.getHerbivoreCount();
        this.initialCarnivoreCount = state.getCarnivoreCount();
        this.initialPlantCount = state.getPlantCount();
        this.initialOmnivoreCount = state.getOmnivoreCount();
        this.initialScavengerCount = state.getScavengerCount();
        this.initialApexPredatorCount = state.getApexPredatorCount();
        this.initialDecomposerCount = state.getDecomposerCount();
        
        // Record initial state for the logger
        dataLogger.recordTick(currentTick, entityManager);
    }

    private void initializePopulation() {
        System.out.println("Initializing population...");
        // Add Herbivores
        for (int i = 0; i < initialHerbivoreCount; i++) {
            spawnEntity(SpeciesType.HERBIVORE);
        }
        // Add Carnivores
        for (int i = 0; i < initialCarnivoreCount; i++) {
            spawnEntity(SpeciesType.CARNIVORE);
        }
        
        // Initialize apex predators
        if (initialApexPredatorCount > 0) {
            for (int i = 0; i < initialApexPredatorCount; i++) {
                spawnEntity(SpeciesType.APEX_PREDATOR);
            }
        }

        // Initialize omnivores
        if (initialOmnivoreCount > 0) {
            for (int i = 0; i < initialOmnivoreCount; i++) {
                spawnEntity(SpeciesType.OMNIVORE);
            }
        }
        
        // Add Scavengers
        for (int i = 0; i < initialScavengerCount; i++) {
            spawnEntity(SpeciesType.SCAVENGER);
        }
        
        // Initialize decomposers
        if (initialDecomposerCount > 0) {
            for (int i = 0; i < initialDecomposerCount; i++) {
                spawnEntity(SpeciesType.DECOMPOSER);
            }
        }

        // Add initial Plants (or ensure generator creates enough initial food)
        // For simplicity, let's rely on the WorldGenerator's initial food placement
        // and plant growth rather than explicit Plant entities initially.
        // If explicit Plant entities are desired:
        /*
        for (int i = 0; i < initialPlantCount; i++) {
             spawnEntity(SpeciesType.PLANT);
        }
        */
        entityManager.updateEntityList(); // Process initial additions
        System.out.println("Initial population: " +
                           "Herbivores: " + entityManager.getPopulationCount(SpeciesType.HERBIVORE) + ", " +
                           "Carnivores: " + entityManager.getPopulationCount(SpeciesType.CARNIVORE) + ", " +
                           "Omnivores: " + entityManager.getPopulationCount(SpeciesType.OMNIVORE) + ", " +
                           "Scavengers: " + entityManager.getPopulationCount(SpeciesType.SCAVENGER) + ", " +
                           "Apex Predators: " + entityManager.getPopulationCount(SpeciesType.APEX_PREDATOR) + ", " +
                           "Decomposers: " + entityManager.getPopulationCount(SpeciesType.DECOMPOSER));
    }

    // Helper to spawn an entity at a random valid location
    private void spawnEntity(SpeciesType type) {
        int attempts = 0;
        int maxAttempts = world.getWidth() * world.getHeight(); // Avoid infinite loop
        while(attempts < maxAttempts) {
            int x = random.nextInt(world.getWidth());
            int y = random.nextInt(world.getHeight());
            if (world.isValidCoordinate(x, y) && world.getTile(x, y).getTerrainType() != TerrainType.WATER && !entityManager.isTileOccupied(x,y) ) {
                Entity entity = null;
                switch (type) {
                    case HERBIVORE:
                        entity = new Herbivore(x, y);
                        break;
                    case CARNIVORE:
                        entity = new Carnivore(x, y);
                        break;
                    case PLANT:
                        entity = new Plant(x, y); // If using explicit Plant entities
                        break;
                    case OMNIVORE:
                        entity = new Omnivore(x, y);
                        break;
                    case SCAVENGER:
                        entity = new Scavenger(x, y);
                        break;
                    case APEX_PREDATOR:
                        entity = new ApexPredator(x, y);
                        break;
                    case DECOMPOSER:
                        entity = new Decomposer(x, y);
                        break;
                }
                if (entity != null) {
                    entityManager.addEntity(entity);
                    return; // Successfully spawned
                }
            }
            attempts++;
        }
        System.err.println("Warning: Could not find valid spawn location for " + type);
    }

    /**
     * Executes a single step (tick) of the simulation.
     */
    public void tick() {
        currentTick++;
        // System.out.println("--- Tick: " + currentTick + " ---\");

        // Get a snapshot of entities for this tick to avoid issues with concurrent modification
        List<Entity> currentEntities = entityManager.getAllEntities();

        // 1. Update all entities
        for (Entity entity : currentEntities) {
            if (entity.isAlive()) {
                 // Pass the Simulation instance and the World to the update method
                 entity.update(this, world);

                // Check if entity died during its update
                if (!entity.isAlive()) {
                    entityManager.removeEntity(entity);
                }
            }
            else {
                 // If somehow an entity in the list is already dead, ensure it's marked for removal
                 entityManager.removeEntity(entity);
            }
        }

        // 2. Process births and deaths (add new entities, remove dead ones)
        entityManager.updateEntityList();

        // 3. Record data for this tick BEFORE world state update (captures end-of-tick populations)
        dataLogger.recordTick(currentTick, entityManager);

        // 4. Update world state (e.g., plant regrowth on tiles)
        updateWorldState();

        // Optional: Print stats periodically
        if (currentTick % 50 == 0) {
            System.out.println("Tick " + currentTick + " Pop: " + entityManager.getTotalPopulation() +
                               " (H:" + entityManager.getPopulationCount(SpeciesType.HERBIVORE) +
                               ", C:" + entityManager.getPopulationCount(SpeciesType.CARNIVORE) + ")");
        }
    }

    // Help entities find food and move more effectively
    private void updateWorldState() {
        // Example: passive plant food regrowth on fertile land tiles
        double passiveRegrowthRate = 0.01;
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                 var tile = world.getTile(x,y);
                 if(tile.getTerrainType() == TerrainType.GRASS || tile.getTerrainType() == TerrainType.FOREST) {
                     // Grow based on fertility, but slower than active plants
                     tile.growPlantFood(tile.getFertility() * passiveRegrowthRate);
                     // TODO: Add max food cap based on tile type/fertility
                 }
            }
        }
        
        // Process dead bodies - allow a small chance for them to decompose naturally
        List<Entity> deadBodies = entityManager.getAllDeadBodies();
        for (Entity deadBody : deadBodies) {
            // 1% chance to decompose naturally each tick
            if (random.nextDouble() < 0.01) {
                deadBody.setDecomposed();
                entityManager.removeEntity(deadBody);
            }
        }
    }

    /**
     * Attempts to spawn a new entity near a parent entity with specific genes.
     * This is typically called when an entity reproduces.
     * @param parent The parent entity requesting the spawn.
     * @param offspringType The type of entity to spawn.
     * @param genes The genes for the offspring.
     * @return The newly spawned entity, or null if spawning failed (no valid location).
     */
    public Entity spawnOffspring(Entity parent, SpeciesType offspringType, Genes genes) {
        if (parent == null || !parent.isAlive() || genes == null) {
            return null;
        }

        int parentX = parent.getX();
        int parentY = parent.getY();

        // Find valid spawn location (existing logic)
        List<int[]> possibleLocations = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = parentX + dx;
                int ny = parentY + dy;
                if (world.isValidCoordinate(nx, ny) &&
                    world.getTile(nx, ny).getTerrainType() != TerrainType.WATER &&
                    !entityManager.isTileOccupied(nx, ny)) {
                    possibleLocations.add(new int[]{nx, ny});
                }
            }
        }

        if (possibleLocations.isEmpty()) {
            return null; // No suitable location
        }

        int[] spawnCoords = possibleLocations.get(random.nextInt(possibleLocations.size()));
        int spawnX = spawnCoords[0];
        int spawnY = spawnCoords[1];

        // Create the offspring entity with the new genes and parent's brain
        Entity offspring = null;
        switch (offspringType) {
            case HERBIVORE:
                offspring = new Herbivore(spawnX, spawnY, genes, parent.getBrain());
                break;
            case CARNIVORE:
                offspring = new Carnivore(spawnX, spawnY, genes, parent.getBrain());
                break;
            case PLANT:
                offspring = new Plant(spawnX, spawnY, genes);
                break;
            case OMNIVORE:
                if (parent.hasBrain()) {
                    offspring = new Omnivore(spawnX, spawnY, genes, parent.getBrain());
                } else {
                    offspring = new Omnivore(spawnX, spawnY, genes);
                }
                break;
            case SCAVENGER:
                if (parent.hasBrain()) {
                    // Create a neural network-enabled scavenger with parent's brain as template
                    offspring = new Scavenger(spawnX, spawnY, genes, parent.getBrain());
                } else {
                    // Create a standard rule-based scavenger
                    offspring = new Scavenger(spawnX, spawnY, genes);
                }
                break;
            case APEX_PREDATOR:
                if (parent.hasBrain()) {
                    offspring = new ApexPredator(spawnX, spawnY, genes, parent.getBrain());
                } else {
                    offspring = new ApexPredator(spawnX, spawnY, genes);
                }
                break;
            case DECOMPOSER:
                // To be implemented
                // System.out.println("Decomposer offspring not yet implemented");
                if (parent.getBrain() != null) {
                    offspring = new Decomposer(spawnX, spawnY, genes, parent.getBrain());
                } else {
                    offspring = new Decomposer(spawnX, spawnY, genes);
                }
                break;
            default:
                System.err.println("Cannot spawn unknown offspring type: " + offspringType);
                return null;
        }

        if (offspring != null) {
            entityManager.addEntity(offspring);
            return offspring;
        }
        return null;
    }

    /**
     * Creates a snapshot of the current simulation state.
     * @return SimulationState object containing the complete simulation state
     */
    public SimulationState saveState() {
        return new SimulationState(
            world,
            entityManager.getAllEntities(),
            currentTick,
            entityManager.getPopulationCount(SpeciesType.HERBIVORE),
            entityManager.getPopulationCount(SpeciesType.CARNIVORE),
            entityManager.getPopulationCount(SpeciesType.PLANT),
            entityManager.getPopulationCount(SpeciesType.OMNIVORE),
            entityManager.getPopulationCount(SpeciesType.SCAVENGER),
            entityManager.getPopulationCount(SpeciesType.APEX_PREDATOR),
            entityManager.getPopulationCount(SpeciesType.DECOMPOSER)
        );
    }
    
    /**
     * Saves the simulation state to a file.
     * @param filePath Path to save the simulation state
     * @return true if save was successful, false otherwise
     */
    public boolean saveStateToFile(String filePath) {
        SimulationState state = saveState();
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath))) {
            out.writeObject(state);
            System.out.println("Simulation state saved to: " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving simulation state: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Loads a simulation state from a file.
     * @param filePath Path to the saved simulation state file
     * @return The loaded SimulationState, or null if loading failed
     */
    public static SimulationState loadStateFromFile(String filePath) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath))) {
            SimulationState state = (SimulationState) in.readObject();
            System.out.println("Simulation state loaded from: " + filePath);
            return state;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading simulation state: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // --- Getters for UI --- //

    public World getWorld() {
        return world;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Gets the data logger for this simulation.
     * @return The DataLogger instance
     */
    public DataLogger getDataLogger() {
        return dataLogger;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    /**
     * Sets the current tick value.
     * Used primarily when loading a saved simulation.
     * @param tick the tick value to set
     */
    public void setCurrentTick(long tick) {
        this.currentTick = tick;
    }

    // --- Control Methods --- //

    // TODO: Implement methods for pause, resume, speed change, user interaction (spawn, place food)
    // These will likely interact with a timer/loop in the main application thread.

    private void reproduce(Entity parent) {
        // Get a valid spawn location near the parent
        Position pos = getValidEntitySpawnLocation(parent.getX(), parent.getY(), 3);
        if (pos == null) {
            return; // No valid position found
        }

        Entity offspring = null;
        switch (parent.getSpeciesType()) {
            case HERBIVORE:
                if (parent.hasBrain()) {
                    offspring = new Herbivore(pos.x, pos.y, parent.getGenes(), parent.getBrain());
                } else {
                    offspring = new Herbivore(pos.x, pos.y, parent.getGenes());
                }
                break;
            case CARNIVORE:
                if (parent.hasBrain()) {
                    offspring = new Carnivore(pos.x, pos.y, parent.getGenes(), parent.getBrain());
                } else {
                    offspring = new Carnivore(pos.x, pos.y, parent.getGenes());
                }
                break;
            case PLANT:
                offspring = new Plant(pos.x, pos.y, parent.getGenes());
                break;
            case SCAVENGER:
                if (parent.hasBrain()) {
                    offspring = new Scavenger(pos.x, pos.y, parent.getGenes(), parent.getBrain());
                } else {
                    offspring = new Scavenger(pos.x, pos.y, parent.getGenes());
                }
                break;
            case OMNIVORE:
                if (parent.hasBrain()) {
                    offspring = new Omnivore(pos.x, pos.y, parent.getGenes(), parent.getBrain());
                } else {
                    offspring = new Omnivore(pos.x, pos.y, parent.getGenes());
                }
                break;
            case APEX_PREDATOR:
                if (parent.hasBrain()) {
                    offspring = new ApexPredator(pos.x, pos.y, parent.getGenes(), parent.getBrain());
                } else {
                    offspring = new ApexPredator(pos.x, pos.y, parent.getGenes());
                }
                break;
            case DECOMPOSER:
                if (parent.hasBrain()) {
                    offspring = new Decomposer(pos.x, pos.y, parent.getGenes(), parent.getBrain());
                } else {
                    offspring = new Decomposer(pos.x, pos.y, parent.getGenes());
                }
                break;
        }

        if (offspring != null) {
            entityManager.addEntity(offspring);
        }
    }

    /**
     * Utility class to represent a position in the world.
     */
    private static class Position {
        final int x;
        final int y;
        
        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    
    /**
     * Get a valid spawn location near the parent entity.
     * 
     * @param parentX The parent's X coordinate
     * @param parentY The parent's Y coordinate
     * @param radius The radius to search for valid spawn locations
     * @return A valid position or null if none found
     */
    private Position getValidEntitySpawnLocation(int parentX, int parentY, int radius) {
        // Try up to 10 random positions within the radius
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = random.nextInt(2 * radius + 1) - radius;
            int dy = random.nextInt(2 * radius + 1) - radius;
            
            int newX = parentX + dx;
            int newY = parentY + dy;
            
            // Check if the position is valid
            if (world.isValidCoordinate(newX, newY) && 
                world.getTile(newX, newY).getTerrainType() != TerrainType.WATER) {
                return new Position(newX, newY);
            }
        }
        
        // If all random attempts failed, check immediate neighbors
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip parent's position
                
                int newX = parentX + dx;
                int newY = parentY + dy;
                
                if (world.isValidCoordinate(newX, newY) && 
                    world.getTile(newX, newY).getTerrainType() != TerrainType.WATER) {
                    return new Position(newX, newY);
                }
            }
        }
        
        // No valid position found
        return null;
    }
} 
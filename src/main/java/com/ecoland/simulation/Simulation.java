package com.ecoland.simulation;

import com.ecoland.common.Constants;
import com.ecoland.data.DataLogger; // Import DataLogger
import com.ecoland.entity.*;
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

public class Simulation {

    private final World world;
    private final EntityManager entityManager;
    private final DataLogger dataLogger; // Added DataLogger instance
    private long currentTick = 0;
    private final Random random = new Random();

    // Simulation parameters (could be moved to a config object)
    private int initialHerbivoreCount = 50;
    private int initialCarnivoreCount = 5;
    private int initialPlantCount = 100; // Plants represented differently, maybe seed tiles instead?

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
        private final long savedTimestamp;
        
        public SimulationState(World world, List<Entity> entities, long tick, 
                              int herbivoreCount, int carnivoreCount, int plantCount) {
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

    public Simulation(int width, int height) {
        this.world = new World(width, height);
        this.entityManager = new EntityManager();
        this.dataLogger = new DataLogger(10); // Log every 10 ticks
        initializePopulation();
        this.dataLogger.recordTick(0, this.entityManager); // Log initial state at tick 0
    }

    // Constructor using default world size
    public Simulation() {
        this(Constants.DEFAULT_WORLD_WIDTH, Constants.DEFAULT_WORLD_HEIGHT);
    }

    // Constructor to restore a simulation from saved state
    public Simulation(SimulationState state) {
        // Create a world with the saved dimensions
        this.world = new World(state.getWorldWidth(), state.getWorldHeight(), false); // false means don't initialize
        
        // Restore all tiles
        Tile[][] savedGrid = state.getWorldGrid();
        for (int x = 0; x < state.getWorldWidth(); x++) {
            for (int y = 0; y < state.getWorldHeight(); y++) {
                world.setTile(x, y, savedGrid[x][y]);
            }
        }
        
        // Create entity manager
        this.entityManager = new EntityManager();
        
        // Restore all entities
        for (EntityState entityState : state.getEntities()) {
            Entity entity = entityState.createEntity();
            if (entity != null) {
                entityManager.addEntity(entity);
            }
        }
        
        // Create a data logger and record the current state
        this.dataLogger = new DataLogger(10); // Log every 10 ticks
        this.currentTick = state.getTick();
        this.dataLogger.recordTick(currentTick, entityManager);
        
        // Process the entity additions
        entityManager.updateEntityList();
        
        System.out.println("Restored simulation state from tick " + currentTick + 
                           " with " + entityManager.getTotalPopulation() + " entities.");
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
                           "Carnivores: " + entityManager.getPopulationCount(SpeciesType.CARNIVORE));

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

    // Placeholder for world updates independent of entities (e.g., rain, passive plant growth)
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
            entityManager.getPopulationCount(SpeciesType.PLANT)
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

} 
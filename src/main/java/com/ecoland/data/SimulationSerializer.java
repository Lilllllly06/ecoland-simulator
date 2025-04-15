package com.ecoland.data;

import com.ecoland.entity.*;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.model.BiomeType;
import com.ecoland.simulation.Simulation;
import com.ecoland.simulation.Simulation.SimulationState;
import com.ecoland.simulation.EntityManager;

import java.io.*;
import java.util.*;

/**
 * Handles serialization and deserialization of the complete simulation state.
 * This allows saving and loading simulations.
 */
public class SimulationSerializer {
    
    /**
     * Saves the complete simulation state to a file.
     * @param simulation The simulation to save
     * @param filename The file to save to
     * @return true if successful, false otherwise
     */
    public static boolean saveSimulation(Simulation simulation, String filename) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            // Create a serializable simulation state object
            SimulationState state = new SimulationState(simulation);
            out.writeObject(state);
            System.out.println("Simulation successfully saved to: " + filename);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving simulation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Loads a simulation from a file.
     * @param filename The file to load from
     * @return The loaded simulation, or null if loading failed
     */
    public static Simulation loadSimulation(String filename) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
            SimulationState state = (SimulationState) in.readObject();
            Simulation simulation = state.recreateSimulation();
            System.out.println("Simulation successfully loaded from: " + filename);
            return simulation;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading simulation: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Container class that holds all the serializable simulation data.
     */
    private static class SimulationState implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final long currentTick;
        private final int worldWidth;
        private final int worldHeight;
        private final TerrainType[][] terrain;
        private final List<SerializableEntity> entities;
        
        public SimulationState(Simulation simulation) {
            this.currentTick = simulation.getCurrentTick();
            
            // Save world data
            World world = simulation.getWorld();
            this.worldWidth = world.getWidth();
            this.worldHeight = world.getHeight();
            this.terrain = new TerrainType[worldWidth][worldHeight];
            
            // Extract terrain data
            for (int x = 0; x < worldWidth; x++) {
                for (int y = 0; y < worldHeight; y++) {
                    Tile tile = world.getTile(x, y);
                    if (tile != null) {
                        terrain[x][y] = tile.getTerrainType();
                    } else {
                        terrain[x][y] = TerrainType.GRASS; // Default
                    }
                }
            }
            
            // Save entity data
            this.entities = new ArrayList<>();
            for (Entity entity : simulation.getEntityManager().getAllEntities()) {
                this.entities.add(new SerializableEntity(entity));
            }
        }
        
        public Simulation recreateSimulation() {
            // Create a new world with the saved dimensions
            World world = new World(worldWidth, worldHeight, false);
            
            // Create a new simulation with this world
            Simulation simulation = new Simulation(world.getWidth(), world.getHeight());
            
            // Set the current tick
            simulation.setCurrentTick(currentTick);
            
            // Restore terrain
            for (int x = 0; x < worldWidth; x++) {
                for (int y = 0; y < worldHeight; y++) {
                    simulation.getWorld().setTile(x, y, new Tile(terrain[x][y], 0.5, 0.0, 0.5));
                }
            }
            
            // Restore entities
            EntityManager entityManager = simulation.getEntityManager();
            for (SerializableEntity serEntity : entities) {
                Entity entity = serEntity.recreateEntity(simulation);
                if (entity != null) {
                    entityManager.addEntity(entity);
                }
            }
            
            return simulation;
        }
    }
    
    /**
     * Serializable version of an Entity.
     */
    private static class SerializableEntity implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final double x;
        private final double y;
        private final double energy;
        private final double health;
        private final SpeciesType speciesType;
        private final SerializableGenes genes;
        
        public SerializableEntity(Entity entity) {
            this.x = entity.getX();
            this.y = entity.getY();
            this.energy = entity.getEnergy();
            this.health = entity.getHealth();
            this.speciesType = entity.getSpeciesType();
            this.genes = new SerializableGenes(entity.getGenes());
        }
        
        public Entity recreateEntity(Simulation simulation) {
            // Factory method to create the appropriate entity type
            // This would need to match your entity creation process
            // For now, returning null as a placeholder
            // TODO: Implement entity recreation based on your entity system
            return null;
        }
    }
    
    /**
     * Serializable version of Genes.
     */
    private static class SerializableGenes implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final double speed;
        private final double visionRange;
        private final double maxEnergy;
        private final double energyEfficiency;
        private final double reproductionThreshold;
        private final double reproductionCost;
        private final double maxHealth;
        private final double spreadChance;
        
        public SerializableGenes(Genes genes) {
            this.speed = genes.speed;
            this.visionRange = genes.visionRange;
            this.maxEnergy = genes.maxEnergy;
            this.energyEfficiency = genes.energyEfficiency;
            this.reproductionThreshold = genes.reproductionThreshold;
            this.reproductionCost = genes.reproductionCost;
            this.maxHealth = genes.maxHealth;
            this.spreadChance = genes.spreadChance;
        }
        
        public Genes recreateGenes() {
            // Create a new Genes object with the saved values
            // This would need a custom constructor in the Genes class
            // TODO: Implement genes recreation based on your genes system
            return null;
        }
    }
} 
package com.ecoland.simulation;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.World;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the collection of entities in the simulation.
 * Provides methods for querying entities based on location, type, etc.
 * TODO: Implement spatial hashing or quadtrees for efficient querying in large worlds.
 */
public class EntityManager {
    private final List<Entity> entities = new ArrayList<>();
    private final List<Entity> entitiesToAdd = new ArrayList<>();
    private final List<Entity> entitiesToRemove = new ArrayList<>();

    /**
     * Adds an entity to be included in the simulation at the end of the current tick.
     * This avoids ConcurrentModificationExceptions during the update loop.
     * @param entity The entity to add.
     */
    public void addEntity(Entity entity) {
        if (entity != null) {
            entitiesToAdd.add(entity);
        }
    }

    /**
     * Marks an entity for removal from the simulation at the end of the current tick.
     * @param entity The entity to remove.
     */
    public void removeEntity(Entity entity) {
        if (entity != null) {
            entitiesToRemove.add(entity);
        }
    }

    /**
     * Updates the main entity list by adding pending entities and removing marked entities.
     * Should be called once per simulation tick, after all entity updates are done.
     */
    public void updateEntityList() {
        entities.removeAll(entitiesToRemove);
        entities.addAll(entitiesToAdd);
        entitiesToRemove.clear();
        entitiesToAdd.clear();
    }

    /**
     * Gets an unmodifiable view of all currently active entities.
     * @return List of all entities.
     */
    public List<Entity> getAllEntities() {
        return List.copyOf(entities); // Return an unmodifiable copy
    }

    /**
     * Finds entities within a certain radius of a point.
     * Current implementation is brute-force O(N). Needs optimization.
     * @param x Center x coordinate.
     * @param y Center y coordinate.
     * @param radius Search radius.
     * @param world The world model (needed for bounds checks perhaps, though less critical here).
     * @return List of entities within the radius.
     */
    public List<Entity> findEntitiesInRange(double x, double y, double radius, World world) {
        List<Entity> found = new ArrayList<>();
        double radiusSq = radius * radius;
        for (Entity entity : entities) {
            if (!entity.isAlive()) continue;
            double dx = entity.getX() - x;
            double dy = entity.getY() - y;
            if (dx*dx + dy*dy <= radiusSq) {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * Finds entities of a specific species within a certain radius.
     * Also O(N).
     * @param x Center x coordinate.
     * @param y Center y coordinate.
     * @param radius Search radius.
     * @param speciesType The species to filter by.
     * @param world The world model.
     * @return List of matching entities within the radius.
     */
    public List<Entity> findEntitiesInRange(double x, double y, double radius, SpeciesType speciesType, World world) {
        List<Entity> found = new ArrayList<>();
        double radiusSq = radius * radius;
        for (Entity entity : entities) {
             if (!entity.isAlive() || entity.getSpeciesType() != speciesType) continue;
             double dx = entity.getX() - x;
             double dy = entity.getY() - y;
             if (dx*dx + dy*dy <= radiusSq) {
                found.add(entity);
             }
        }
        return found;
    }

     /**
     * Gets the entity at a specific tile coordinate, if any.
     * @param x X coordinate.
     * @param y Y coordinate.
     * @return The entity at (x, y), or null if the tile is empty or occupied by multiple (returns first found).
     */
    public Entity getEntityAt(int x, int y) {
        // Inefficient O(N) search. Spatial hashing would make this O(1) average.
        for (Entity entity : entities) {
            if (entity.isAlive() && entity.getX() == x && entity.getY() == y) {
                return entity;
            }
        }
        return null;
    }

     /**
     * Checks if a specific tile is occupied by any entity.
     * @param x X coordinate.
     * @param y Y coordinate.
     * @return true if an entity exists at (x, y), false otherwise.
     */
    public boolean isTileOccupied(int x, int y) {
        // Inefficient O(N) search.
        for (Entity entity : entities) {
            if (entity.isAlive() && entity.getX() == x && entity.getY() == y) {
                return true;
            }
        }
        return false;
    }

    public int getPopulationCount(SpeciesType speciesType) {
        return (int) entities.stream()
                .filter(Entity::isAlive)
                .filter(e -> e.getSpeciesType() == speciesType)
                .count();
    }

    public int getTotalPopulation() {
         return (int) entities.stream().filter(Entity::isAlive).count();
    }
} 
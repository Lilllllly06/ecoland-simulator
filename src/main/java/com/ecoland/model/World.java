package com.ecoland.model;

import com.ecoland.common.Constants;
import com.ecoland.generator.WorldGenerator;

public class World {
    private final int width;
    private final int height;
    private final Tile[][] grid;

    public World(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("World dimensions must be positive.");
        }
        this.width = width;
        this.height = height;
        this.grid = new Tile[width][height];
        initializeWorld();
    }

    private void initializeWorld() {
        // Use a world generator to fill the grid
        // This decouples world creation logic from the World model itself
        WorldGenerator generator = WorldGenerator.createDefaultGenerator(); // Placeholder for generator selection
        generator.generate(this);
    }

    // Method to allow the generator to set tiles
    // Protected or package-private might be better depending on generator location
    public void setTile(int x, int y, Tile tile) {
        if (isValidCoordinate(x, y)) {
            grid[x][y] = tile;
        } else {
            // Consider logging a warning or throwing an exception
            System.err.println("Attempted to set tile at invalid coordinates: (" + x + ", " + y + ")");
        }
    }

    public Tile getTile(int x, int y) {
        if (isValidCoordinate(x, y)) {
            return grid[x][y];
        }
        return null; // Or throw exception for out-of-bounds access
    }

    public boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    // TODO: Add methods to get neighbors, manage entities within the world, etc.
} 
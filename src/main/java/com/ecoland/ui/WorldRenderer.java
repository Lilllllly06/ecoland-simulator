package com.ecoland.ui;

import com.ecoland.entity.Entity;
import com.ecoland.entity.SpeciesType;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;

public class WorldRenderer {

    private final Canvas canvas;
    private final World world;
    private double tileSize; // Size of each tile in pixels

    // Colors (configurable later)
    private static final Color GRASS_COLOR = Color.LIGHTGREEN;
    private static final Color WATER_COLOR = Color.DEEPSKYBLUE;
    private static final Color HILL_COLOR = Color.BURLYWOOD;
    private static final Color FOREST_COLOR = Color.FORESTGREEN;
    private static final Color DESERT_COLOR = Color.KHAKI;
    private static final Color HERBIVORE_COLOR = Color.BLUE;
    private static final Color CARNIVORE_COLOR = Color.RED;
    private static final Color PLANT_COLOR = Color.DARKGREEN; // If drawing explicit plant entities

    public WorldRenderer(Canvas canvas, World world) {
        this.canvas = canvas;
        this.world = world;
        calculateTileSize();
    }

    private void calculateTileSize() {
        // Fit the world onto the canvas
        double sizeX = canvas.getWidth() / world.getWidth();
        double sizeY = canvas.getHeight() / world.getHeight();
        this.tileSize = Math.min(sizeX, sizeY); // Use the smaller dimension to fit all tiles squarely
         if (this.tileSize < 1) this.tileSize = 1; // Ensure tiles are at least 1 pixel
    }

    public void render(List<Entity> entities) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();

        // Recalculate tile size in case canvas was resized
        calculateTileSize();

        // Clear canvas
        gc.clearRect(0, 0, canvasWidth, canvasHeight);

        // 1. Draw Terrain Tiles
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                Tile tile = world.getTile(x, y);
                if (tile != null) {
                    gc.setFill(getTerrainColor(tile.getTerrainType(), tile));
                    gc.fillRect(x * tileSize, y * tileSize, tileSize, tileSize);
                }
            }
        }

        // 2. Draw Entities
        // Simple representation: colored circles
        double entityRadius = tileSize * 0.4; // Make entities slightly smaller than tile
        for (Entity entity : entities) {
            if (entity.isAlive()) {
                gc.setFill(getEntityColor(entity.getSpeciesType()));
                double drawX = (entity.getX() + 0.5) * tileSize - entityRadius; // Center of tile
                double drawY = (entity.getY() + 0.5) * tileSize - entityRadius;
                gc.fillOval(drawX, drawY, entityRadius * 2, entityRadius * 2);
            }
        }

        // Optional: Draw grid lines for clarity, especially if tileSize is large
        // gc.setStroke(Color.LIGHTGRAY);
        // gc.setLineWidth(0.5);
        // for (int x = 0; x <= world.getWidth(); x++) {
        //     gc.strokeLine(x * tileSize, 0, x * tileSize, world.getHeight() * tileSize);
        // }
        // for (int y = 0; y <= world.getHeight(); y++) {
        //     gc.strokeLine(0, y * tileSize, world.getWidth() * tileSize, y * tileSize);
        // }
    }

    private Color getTerrainColor(TerrainType type, Tile tile) {
        Color baseColor;
        switch (type) {
            case WATER: baseColor = WATER_COLOR; break;
            case HILL: baseColor = HILL_COLOR; break;
            case FOREST: baseColor = FOREST_COLOR; break;
            case DESERT: baseColor = DESERT_COLOR; break;
            case GRASS:
            default: baseColor = GRASS_COLOR; break;
        }

        // Adjust color based on properties like food value (for grass)
        if (type == TerrainType.GRASS || type == TerrainType.FOREST) {
            double foodRatio = Math.min(1.0, tile.getPlantFoodValue() / 2.0); // Normalize food value (assume max ~2.0?)
            // Blend base color with a darker green based on food amount
            return baseColor.interpolate(Color.DARKGREEN, foodRatio * 0.6); // Make greener with more food
        }

        // TODO: Add variations based on elevation, fertility, etc.
        return baseColor;
    }

    private Color getEntityColor(SpeciesType type) {
        switch (type) {
            case HERBIVORE: return HERBIVORE_COLOR;
            case CARNIVORE: return CARNIVORE_COLOR;
            case PLANT: return PLANT_COLOR;
            default: return Color.GRAY; // Unknown entity type
        }
    }

    // Method to translate canvas coordinates to world tile coordinates (for Inspector Tool)
    public int[] getTileCoordinates(double canvasX, double canvasY) {
        if (tileSize <= 0) return null;
        int x = (int) (canvasX / tileSize);
        int y = (int) (canvasY / tileSize);
        if (world.isValidCoordinate(x, y)) {
            return new int[]{x, y};
        } else {
            return null;
        }
    }

} 
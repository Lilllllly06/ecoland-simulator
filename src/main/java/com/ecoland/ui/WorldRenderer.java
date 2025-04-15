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
    private World world;
    private double tileSize; // Size of each tile in pixels
    
    // Zoom and viewport settings
    private double zoomFactor = 1.0;
    private double viewportX = 0.0;
    private double viewportY = 0.0;

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
    
    /**
     * Set the world reference (used when loading a new world)
     */
    public void setWorld(World world) {
        this.world = world;
        calculateTileSize();
    }
    
    /**
     * Set zoom and pan values
     */
    public void setZoom(double zoomFactor, double viewportX, double viewportY) {
        this.zoomFactor = Math.max(0.1, Math.min(5.0, zoomFactor));
        this.viewportX = viewportX;
        this.viewportY = viewportY;
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
        
        // Determine effective tile size with zoom
        double effectiveTileSize = tileSize * zoomFactor;

        // Clear canvas
        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        
        // Calculate the range of visible tiles
        int startX = Math.max(0, (int)(viewportX / effectiveTileSize));
        int startY = Math.max(0, (int)(viewportY / effectiveTileSize));
        int endX = Math.min(world.getWidth(), startX + (int)(canvasWidth / effectiveTileSize) + 2);
        int endY = Math.min(world.getHeight(), startY + (int)(canvasHeight / effectiveTileSize) + 2);

        // 1. Draw Terrain Tiles (only visible tiles)
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                Tile tile = world.getTile(x, y);
                if (tile != null) {
                    gc.setFill(getTerrainColor(tile.getTerrainType(), tile));
                    double drawX = (x * effectiveTileSize) - viewportX;
                    double drawY = (y * effectiveTileSize) - viewportY;
                    gc.fillRect(drawX, drawY, effectiveTileSize, effectiveTileSize);
                }
            }
        }

        // 2. Draw Entities (only those in the visible area)
        double entityRadius = effectiveTileSize * 0.4;
        for (Entity entity : entities) {
            if (entity.isAlive()) {
                int x = entity.getX();
                int y = entity.getY();
                
                // Skip if entity is outside the visible area
                if (x < startX || x >= endX || y < startY || y >= endY) {
                    continue;
                }
                
                gc.setFill(getEntityColor(entity.getSpeciesType()));
                double drawX = ((x + 0.5) * effectiveTileSize) - viewportX - entityRadius;
                double drawY = ((y + 0.5) * effectiveTileSize) - viewportY - entityRadius;
                gc.fillOval(drawX, drawY, entityRadius * 2, entityRadius * 2);
                
                // Optional: Draw health/energy indicators
                if (effectiveTileSize > 10) { // Only if tiles are large enough
                    // Draw health bar above entity
                    double healthRatio = entity.getHealth() / entity.getMaxHealth();
                    double barWidth = effectiveTileSize * 0.8;
                    double barHeight = effectiveTileSize * 0.1;
                    double barX = ((x + 0.1) * effectiveTileSize) - viewportX;
                    double barY = (y * effectiveTileSize) - viewportY - barHeight * 2;
                    
                    // Background bar (gray)
                    gc.setFill(Color.LIGHTGRAY);
                    gc.fillRect(barX, barY, barWidth, barHeight);
                    
                    // Health bar (green)
                    gc.setFill(Color.GREEN);
                    gc.fillRect(barX, barY, barWidth * healthRatio, barHeight);
                }
            }
        }

        // Optional: Draw grid lines for clarity if zoom is high enough
        if (effectiveTileSize > 10) {
            gc.setStroke(Color.DARKGRAY);
            gc.setLineWidth(0.5);
            
            // Only draw grid lines for visible area
            for (int x = startX; x <= endX; x++) {
                double drawX = (x * effectiveTileSize) - viewportX;
                gc.strokeLine(drawX, 0, drawX, canvasHeight);
            }
            
            for (int y = startY; y <= endY; y++) {
                double drawY = (y * effectiveTileSize) - viewportY;
                gc.strokeLine(0, drawY, canvasWidth, drawY);
            }
        }
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
            double foodRatio = Math.min(1.0, tile.getPlantFoodValue() / 5.0); // Increased range to 5.0 to make changes more visible
            // Blend base color with a darker green based on food amount (increased weight to 0.8)
            return baseColor.interpolate(Color.DARKGREEN, foodRatio * 0.8); // Make greener with more food
        }

        // Adjust colors based on elevation and moisture
        if (type != TerrainType.WATER) {
            double elevationFactor = Math.min(1.0, tile.getElevation() / 1.0);
            double moistureFactor = Math.min(1.0, tile.getMoisture() / 1.0);
            
            // Slightly darken higher elevations
            baseColor = baseColor.interpolate(Color.DARKGRAY, elevationFactor * 0.2);
            
            // Make wetter areas slightly darker/richer
            baseColor = baseColor.interpolate(baseColor.darker(), moistureFactor * 0.3);
        }

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

    /**
     * Converts canvas coordinates to world coordinates
     * @return int[2] with x,y world coordinates or null if out of bounds
     */
    public int[] canvasToWorldCoordinates(double canvasX, double canvasY) {
        if (tileSize <= 0) return null;
        
        // Calculate with zoom and pan offsets
        double effectiveTileSize = tileSize * zoomFactor;
        double worldX = (canvasX + viewportX) / effectiveTileSize;
        double worldY = (canvasY + viewportY) / effectiveTileSize;
        
        int x = (int) worldX;
        int y = (int) worldY;
        
        if (world.isValidCoordinate(x, y)) {
            return new int[]{x, y};
        } else {
            return null;
        }
    }
    
    /**
     * Converts world coordinates to canvas coordinates
     */
    public double[] worldToCanvasCoordinates(int worldX, int worldY) {
        double effectiveTileSize = tileSize * zoomFactor;
        double canvasX = (worldX * effectiveTileSize) - viewportX;
        double canvasY = (worldY * effectiveTileSize) - viewportY;
        return new double[]{canvasX, canvasY};
    }

    // For backward compatibility
    @Deprecated
    public int[] getTileCoordinates(double canvasX, double canvasY) {
        return canvasToWorldCoordinates(canvasX, canvasY);
    }
} 
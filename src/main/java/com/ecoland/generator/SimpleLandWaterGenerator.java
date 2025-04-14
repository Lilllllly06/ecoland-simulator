package com.ecoland.generator;

import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;

import java.util.Random;

/**
 * A very basic world generator that randomly assigns Land (Grass) or Water tiles.
 * This serves as an initial placeholder.
 */
public class SimpleLandWaterGenerator implements WorldGenerator {

    private final Random random = new Random();
    private static final double INITIAL_FOOD_MULTIPLIER = 0.8; // How much food relative to fertility

    @Override
    public void generate(World world) {
        System.out.println("Generating world using SimpleLandWaterGenerator...");
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                // Simple random assignment for now
                TerrainType type;
                double fertility;
                double elevation = 1.0;
                double waterLevel = 0.0;

                if (random.nextDouble() < 0.7) { // 70% chance of land
                    type = TerrainType.GRASS;
                    fertility = random.nextDouble() * 0.5 + 0.2; // Random fertility for grass (0.2 to 0.7)
                } else { // 30% chance of water
                    type = TerrainType.WATER;
                    fertility = 0.0;
                    elevation = 0.0;
                    waterLevel = 1.0;
                }

                Tile tile = new Tile(type, elevation, waterLevel, fertility);

                // Initialize plant food based on fertility for land tiles
                if (type == TerrainType.GRASS) {
                    tile.setPlantFoodValue(fertility * INITIAL_FOOD_MULTIPLIER);
                }

                world.setTile(x, y, tile);
            }
        }
        System.out.println("World generation complete.");
    }
} 
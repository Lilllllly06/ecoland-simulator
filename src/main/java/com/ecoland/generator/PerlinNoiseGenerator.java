package com.ecoland.generator;

import com.ecoland.model.BiomeType;
import com.ecoland.model.TerrainType;
import com.ecoland.model.Tile;
import com.ecoland.model.World;

import java.util.Random;

/**
 * Generates a world using Perlin noise for more natural terrain features.
 * Uses multiple noise layers for elevation, moisture, and temperature to create varied biomes.
 */
public class PerlinNoiseGenerator implements WorldGenerator {

    private final Random random;
    private final long seed;
    private final double scale; // Noise scale (lower = larger features)
    private final int octaves; // Number of noise layers for detail
    private final double persistence; // Amplitude reduction per octave
    private final double lacunarity; // Frequency increase per octave

    // Thresholds for terrain types (adjust these to change biome distribution)
    private static final double WATER_LEVEL_THRESHOLD = 0.35; // Noise values below this are water
    private static final double HILL_LEVEL_THRESHOLD = 0.75;  // Noise values above this are hills
    private static final double DEEP_WATER_THRESHOLD = 0.2;   // Threshold for deep ocean vs shallow water

    // For biome determination
    private static final double HIGH_MOISTURE_THRESHOLD = 0.65;  // Above this is wet (forests, swamps)
    private static final double LOW_MOISTURE_THRESHOLD = 0.35;   // Below this is dry (desert)
    private static final double HIGH_TEMP_THRESHOLD = 0.65;      // Above this is hot
    private static final double LOW_TEMP_THRESHOLD = 0.35;       // Below this is cold

    // Precomputed gradients for Perlin noise (simple 8 directions)
    private final int[][] grad = {
        {1,1}, {-1,1}, {1,-1}, {-1,-1},
        {1,0}, {-1,0}, {0,1}, {0,-1}
    };
    private int[] p; // Permutation table

    /**
     * Creates a PerlinNoiseGenerator with the specified parameters
     */
    public PerlinNoiseGenerator(long seed, double scale, int octaves, double persistence, double lacunarity) {
        this.seed = seed;
        this.scale = scale;
        this.octaves = Math.max(1, octaves);
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        this.random = new Random(seed);
        initializePermutationTable();
    }

    /**
     * Creates a PerlinNoiseGenerator with default parameters
     */
    public PerlinNoiseGenerator() {
        this(new Random().nextLong(), 50.0, 4, 0.5, 2.0);
    }

    /**
     * Initialize the permutation table for Perlin noise
     */
    private void initializePermutationTable() {
        p = new int[512];
        int[] source = new int[256];
        
        // Initialize source array
        for (int i = 0; i < 256; i++) {
            source[i] = i;
        }
        
        // Shuffle the source array
        for (int i = 255; i > 0; i--) {
            int index = random.nextInt(i + 1);
            int temp = source[index];
            source[index] = source[i];
            source[i] = temp;
        }
        
        // Duplicate for seamless wrapping
        for (int i = 0; i < 512; i++) {
            p[i] = source[i & 255];
        }
    }

    @Override
    public void generate(World world) {
        System.out.println("Generating world using PerlinNoiseGenerator (Seed: " + seed + ", Scale: " + scale + ")...");
        
        // Generate noise maps for different aspects of the terrain
        double[][] elevationNoise = generateNoiseMap(world.getWidth(), world.getHeight(), seed);
        double[][] moistureNoise = generateNoiseMap(world.getWidth(), world.getHeight(), seed + 13);
        double[][] temperatureNoise = generateNoiseMap(world.getWidth(), world.getHeight(), seed + 37);
        
        // Additional small scale noise for terrain variation
        double[][] detailNoise = generateNoiseMap(world.getWidth(), world.getHeight(), seed + 83, scale / 4, 1, 0.5, 2.0);

        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                // Get normalized noise values
                double elevation = elevationNoise[x][y];
                double moisture = moistureNoise[x][y];
                double temperature = temperatureNoise[x][y];
                double detail = detailNoise[x][y];
                
                // Small adjustments for more variety
                elevation = adjustWithDetail(elevation, detail, 0.05);
                moisture = adjustWithDetail(moisture, detail, 0.02);
                
                // Edge water to create island-like formations (optional)
                elevation = adjustElevationForEdges(elevation, x, y, world.getWidth(), world.getHeight());

                // Determine terrain type
                TerrainType terrainType;
                BiomeType biomeType;
                double waterLevel = 0.0;
                double fertility = 0.0;
                double plantFood = 0.0;
                
                // Determine base terrain type from elevation
                if (elevation < WATER_LEVEL_THRESHOLD) {
                    terrainType = TerrainType.WATER;
                    waterLevel = 1.0 - (elevation / WATER_LEVEL_THRESHOLD); // Deeper water for lower elevation
                    
                    // Determine water biome type
                    if (elevation < DEEP_WATER_THRESHOLD) {
                        biomeType = BiomeType.OCEAN;
                    } else {
                        biomeType = BiomeType.LAKE;
                        fertility = 0.1; // Some fertility in shallow water
                    }
                } else if (elevation > HILL_LEVEL_THRESHOLD) {
                    terrainType = TerrainType.HILL;
                    biomeType = BiomeType.MOUNTAINS;
                    fertility = 0.2 + (1.0 - elevation) * 0.3; // Some fertility, decreasing with height
                } else {
                    // Land between water and hills - determine biome by moisture and temperature
                    if (moisture < LOW_MOISTURE_THRESHOLD) {
                        terrainType = TerrainType.DESERT;
                        biomeType = BiomeType.DESERT;
                        fertility = 0.1 + moisture * 0.2; // Very low fertility in deserts
                    } else if (moisture > HIGH_MOISTURE_THRESHOLD) {
                        if (temperature > HIGH_TEMP_THRESHOLD) {
                            terrainType = TerrainType.FOREST;
                            biomeType = BiomeType.FOREST;
                            fertility = 0.6 + (moisture - HIGH_MOISTURE_THRESHOLD) * 0.4; // High fertility
                            plantFood = fertility * 0.9; // Start with lots of plant food
                        } else {
                            terrainType = TerrainType.GRASS;
                            biomeType = BiomeType.SWAMP;
                            fertility = 0.5 + (moisture - HIGH_MOISTURE_THRESHOLD) * 0.3;
                            plantFood = fertility * 0.7;
                        }
                    } else {
                        terrainType = TerrainType.GRASS;
                        biomeType = BiomeType.PLAINS;
                        fertility = 0.4 + (moisture - LOW_MOISTURE_THRESHOLD) * 0.4;
                        plantFood = fertility * 0.8;
                    }
                }
                
                // Create the tile with all calculated properties
                Tile tile = new Tile(terrainType, biomeType, elevation, waterLevel, fertility, temperature, moisture);
                
                // Set initial plant food for non-water tiles
                if (terrainType != TerrainType.WATER && plantFood > 0) {
                    tile.setPlantFoodValue(plantFood);
                }
                
                world.setTile(x, y, tile);
            }
        }
        
        System.out.println("Perlin noise world generation complete.");
    }

    /**
     * Makes slight adjustments to a value based on detail noise
     */
    private double adjustWithDetail(double value, double detail, double amount) {
        // Mix in a small amount of detail noise
        return Math.max(0.0, Math.min(1.0, value + (detail - 0.5) * amount));
    }

    /**
     * Lowers elevation near the map edges to create water borders/islands
     */
    private double adjustElevationForEdges(double elevation, int x, int y, int width, int height) {
        // Calculate distance from edge as a ratio (0 = edge, 1 = center)
        double distX = Math.min(x, width - 1 - x) / (double)(width * 0.3);
        double distY = Math.min(y, height - 1 - y) / (double)(height * 0.3);
        distX = Math.min(1.0, distX);
        distY = Math.min(1.0, distY);
        
        // Square root for smoother falloff
        double edge = Math.min(distX, distY);
        
        // Apply edge adjustment (lower elevation near edges)
        return elevation * (0.5 + 0.5 * edge);
    }

    /**
     * Generates a 2D noise map using Perlin noise
     */
    private double[][] generateNoiseMap(int width, int height, long mapSeed) {
        return generateNoiseMap(width, height, mapSeed, this.scale, this.octaves, this.persistence, this.lacunarity);
    }
    
    /**
     * Generates a 2D noise map with custom parameters
     */
    private double[][] generateNoiseMap(int width, int height, long mapSeed, 
                                      double scale, int octaves, double persistence, double lacunarity) {
        double[][] noiseMap = new double[width][height];
        Random noiseRandom = new Random(mapSeed);
        
        // Offsets to make each noise map unique
        double offsetX = noiseRandom.nextDouble() * 100000;
        double offsetY = noiseRandom.nextDouble() * 100000;

        double maxNoiseHeight = Double.MIN_VALUE;
        double minNoiseHeight = Double.MAX_VALUE;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double amplitudeSum = 0;
                double frequency = 1;
                double amplitude = 1;
                double noiseValue = 0;

                // Generate octaves of noise
                for (int i = 0; i < octaves; i++) {
                    double sampleX = (x + offsetX) / scale * frequency;
                    double sampleY = (y + offsetY) / scale * frequency;
                    
                    // Get raw Perlin noise value (-1 to 1)
                    double octaveValue = perlin(sampleX, sampleY);
                    
                    // Accumulate with current amplitude
                    noiseValue += octaveValue * amplitude;
                    amplitudeSum += amplitude;
                    
                    // Adjust for next octave
                    amplitude *= persistence;
                    frequency *= lacunarity;
                }
                
                // Normalize based on amplitudes
                noiseValue /= amplitudeSum;
                
                // Store for normalization
                noiseMap[x][y] = noiseValue;
                
                if (noiseValue > maxNoiseHeight) maxNoiseHeight = noiseValue;
                if (noiseValue < minNoiseHeight) minNoiseHeight = noiseValue;
            }
        }

        // Normalize to [0,1] range
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Avoid division by zero
                if (maxNoiseHeight > minNoiseHeight) {
                    noiseMap[x][y] = (noiseMap[x][y] - minNoiseHeight) / (maxNoiseHeight - minNoiseHeight);
                } else {
                    noiseMap[x][y] = 0.5;
                }
            }
        }

        return noiseMap;
    }

    /**
     * Core Perlin noise function for a single point (x,y)
     * @return Noise value in range [-1, 1]
     */
    private double perlin(double x, double y) {
        // Grid cell coordinates
        int x0 = (int)Math.floor(x) & 255;
        int y0 = (int)Math.floor(y) & 255;
        
        // Position within cell [0,1]
        double x1 = x - Math.floor(x);
        double y1 = y - Math.floor(y);
        
        // Grid point offsets
        int gi00 = p[p[x0] + y0] & 7;
        int gi01 = p[p[x0] + y0 + 1] & 7;
        int gi10 = p[p[x0 + 1] + y0] & 7;
        int gi11 = p[p[x0 + 1] + y0 + 1] & 7;
        
        // Gradient values
        double n00 = dot(grad[gi00][0], grad[gi00][1], x1, y1);
        double n01 = dot(grad[gi01][0], grad[gi01][1], x1, y1 - 1);
        double n10 = dot(grad[gi10][0], grad[gi10][1], x1 - 1, y1);
        double n11 = dot(grad[gi11][0], grad[gi11][1], x1 - 1, y1 - 1);
        
        // Fade curves
        double u = fade(x1);
        double v = fade(y1);
        
        // Interpolate
        double nx0 = lerp(n00, n10, u);
        double nx1 = lerp(n01, n11, u);
        double nxy = lerp(nx0, nx1, v);
        
        return nxy;
    }
    
    /**
     * Fade function (smoother step): 6t^5 - 15t^4 + 10t^3
     */
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    /**
     * Linear interpolation between a and b by t
     */
    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
    
    /**
     * Dot product for vectors
     */
    private double dot(double gx, double gy, double x, double y) {
        return gx * x + gy * y;
    }
} 
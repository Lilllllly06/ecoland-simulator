package com.ecoland.generator;\n\nimport com.ecoland.model.TerrainType;\nimport com.ecoland.model.Tile;\nimport com.ecoland.model.World;\n\nimport java.util.Random;\n\n/**\n * Generates a world using Perlin noise for more natural terrain features.\n */\npublic class PerlinNoiseGenerator implements WorldGenerator {\n\n    private final Random random = new Random();\n    private final long seed;\n    private final double scale; // Noise scale (lower = larger features)\n    private final int octaves; // Number of noise layers for detail\n    private final double persistence; // Amplitude reduction per octave\n    private final double lacunarity; // Frequency increase per octave\n\n    // Thresholds for terrain types (adjust these to change biome distribution)\n    private static final double WATER_LEVEL_THRESHOLD = 0.35; // Noise values below this are water\n    private static final double HILL_LEVEL_THRESHOLD = 0.7;  // Noise values above this are hills\n    private static final double FOREST_MOISTURE_THRESHOLD = 0.5; // Moisture threshold for forests on non-hill/water land\n    private static final double DESERT_MOISTURE_THRESHOLD = 0.2; // Moisture threshold for deserts\n\n    // Precomputed gradients for Perlin noise (simple 8 directions)\n    private final int[][] grad = {\n        {1,1}, {-1,1}, {1,-1}, {-1,-1},\n        {1,0}, {-1,0}, {0,1}, {0,-1}\n    };\n    private final int[] p = new int[512]; // Permutation table\n\n    public PerlinNoiseGenerator(long seed, double scale, int octaves, double persistence, double lacunarity) {\n        this.seed = seed;\n        this.scale = scale;\n        this.octaves = Math.max(1, octaves);\n        this.persistence = persistence;\n        this.lacunarity = lacunarity;\n        initializePermutationTable(seed);\n    }\n\n    // Default constructor with reasonable values\n    public PerlinNoiseGenerator() {\n        this(new Random().nextLong(), 50.0, 4, 0.5, 2.0);\n    }\n\n    private void initializePermutationTable(long seed) {\n        Random seededRandom = new Random(seed);\n        int[] source = new int[256];\n        for (int i = 0; i < 256; i++) {\n            source[i] = i;\n        }\n        // Shuffle the source array\n        for (int i = 255; i > 0; i--) {\n            int index = seededRandom.nextInt(i + 1);\n            int temp = source[index];\n            source[index] = source[i];\n            source[i] = temp;\n        }\n        // Duplicate the permutation table for wrapping\n        for (int i = 0; i < 512; i++) {\n            p[i] = source[i & 255];\n        }\n    }\n\n    @Override\n    public void generate(World world) {\n        System.out.println(\"Generating world using PerlinNoiseGenerator (Seed: \" + seed + \", Scale: \" + scale + \")...\");\n        double[][] elevationNoise = generateNoiseMap(world.getWidth(), world.getHeight(), seed);\n        double[][] moistureNoise = generateNoiseMap(world.getWidth(), world.getHeight(), seed + 1); // Use different seed for moisture\n\n        for (int x = 0; x < world.getWidth(); x++) {\n            for (int y = 0; y < world.getHeight(); y++) {\n                double elev = elevationNoise[x][y]; // Noise value [0, 1]\n                double moist = moistureNoise[x][y]; // Noise value [0, 1]\n\n                TerrainType type;\n                double fertility = 0;\n                double plantFood = 0;\n                double elevationValue = elev; // Store raw elevation\n                double waterLevelValue = 0;\n\n                if (elev < WATER_LEVEL_THRESHOLD) {\n                    type = TerrainType.WATER;\n                    waterLevelValue = 1.0 - (elev / WATER_LEVEL_THRESHOLD); // Deeper water for lower elev\n                    elevationValue = 0; // Water is at base elevation\n                } else if (elev > HILL_LEVEL_THRESHOLD) {\n                    type = TerrainType.HILL;\n                    fertility = 0.1 + (1.0 - elev) * 0.2; // Hills are less fertile\n                } else {\n                    // Land: Determine biome based on moisture\n                    if (moist > FOREST_MOISTURE_THRESHOLD) {\n                        type = TerrainType.FOREST;\n                        fertility = 0.6 + (moist - FOREST_MOISTURE_THRESHOLD) * 0.4; // Forests are fertile\n                        plantFood = fertility * 0.9; // Start with lots of food (trees)\n                    } else if (moist < DESERT_MOISTURE_THRESHOLD) {\n                        type = TerrainType.DESERT;\n                        fertility = 0.05 + moist * 0.1; // Deserts have very low fertility\n                    } else {\n                        type = TerrainType.GRASS;\n                        fertility = 0.3 + (moist - DESERT_MOISTURE_THRESHOLD) * 0.5; // Grass fertility based on moisture\n                        plantFood = fertility * 0.8;\n                    }\n                }\n\n                Tile tile = new Tile(type, elevationValue, waterLevelValue, fertility);\n                tile.setPlantFoodValue(plantFood);\n                world.setTile(x, y, tile);\n            }\n        }\n        System.out.println(\"Perlin noise world generation complete.\");\n    }\n\n    // Generates a 2D noise map using Perlin noise\n    private double[][] generateNoiseMap(int width, int height, long mapSeed) {\n        double[][] noiseMap = new double[width][height];\n        PerlinNoiseGenerator localNoise = new PerlinNoiseGenerator(mapSeed, this.scale, this.octaves, this.persistence, this.lacunarity);\n\n        double maxNoiseHeight = Double.MIN_VALUE;\n        double minNoiseHeight = Double.MAX_VALUE;\n\n        for (int y = 0; y < height; y++) {\n            for (int x = 0; x < width; x++) {\n                double noiseValue = localNoise.getOctavePerlin(x, y);\n                noiseMap[x][y] = noiseValue;\n\n                if (noiseValue > maxNoiseHeight) maxNoiseHeight = noiseValue;\n                if (noiseValue < minNoiseHeight) minNoiseHeight = noiseValue;\n            }\n        }\n\n        // Normalize the noise map to be between 0 and 1\n        if (maxNoiseHeight != minNoiseHeight) { // Avoid division by zero\n             for (int y = 0; y < height; y++) {\n                for (int x = 0; x < width; x++) {\n                    noiseMap[x][y] = (noiseMap[x][y] - minNoiseHeight) / (maxNoiseHeight - minNoiseHeight);\n                }\n             }\n        }\ else {\n             // If all values are the same, set to 0.5 or handle as needed\n             for (int y = 0; y < height; y++) {\n                for (int x = 0; x < width; x++) {\n                    noiseMap[x][y] = 0.5;\n                }\n             }\n        }\n\n        return noiseMap;\n    }\n\n     // Generates Perlin noise value for a given point with multiple octaves\n    private double getOctavePerlin(double x, double y) {\n        double total = 0;\n        double frequency = 1;\n        double amplitude = 1;\n        double maxValue = 0;  // Used for normalizing result [-1, 1] -> potentially [0, 1] later if needed\n\n        for (int i = 0; i < octaves; i++) {\n            total += perlin(x / scale * frequency, y / scale * frequency) * amplitude;\n            maxValue += amplitude;\n            amplitude *= persistence;\n            frequency *= lacunarity;\n        }\n        // Normalize based on maxValue, though Perlin theoretically is [-1, 1], octaves can exceed this\n        // Return value should ideally be close to the [-1, 1] range\n         return total / maxValue; // This normalization might slightly compress range, adjust if needed\n    }\n\n    // Core Perlin noise function (Improved 2D version)\n    private double perlin(double x, double y) {\n        int xi = (int) Math.floor(x) & 255;\n        int yi = (int) Math.floor(y) & 255;\n        double xf = x - Math.floor(x);\n        double yf = y - Math.floor(y);\n\n        // Get gradient vectors for the 4 corners\n        int g00 = p[p[xi] + yi];\n        int g10 = p[p[xi + 1] + yi];\n        int g01 = p[p[xi] + yi + 1];\n        int g11 = p[p[xi + 1] + yi + 1];\n\n        // Calculate dot products\n        double d00 = dotGridGradient(g00, xf, yf);\n        double d10 = dotGridGradient(g10, xf - 1, yf);\n        double d01 = dotGridGradient(g01, xf, yf - 1);\n        double d11 = dotGridGradient(g11, xf - 1, yf - 1);\n\n        // Interpolate\n        double u = fade(xf);\n        double v = fade(yf);\n\n        double nx0 = lerp(d00, d10, u);\n        double nx1 = lerp(d01, d11, u);\n        double value = lerp(nx0, nx1, v);\n\n        return value; // Result is roughly in [-1, 1]\n    }\n\n    // Fade function (6t^5 - 15t^4 + 10t^3)\n    private double fade(double t) {\n        return t * t * t * (t * (t * 6 - 15) + 10);\n    }\n\n    // Linear interpolation\n    private double lerp(double a, double b, double t) {\n        return a + t * (b - a);\n    }\n\n    // Dot product of distance vector and gradient vector\n    private double dotGridGradient(int hash, double x, double y) {\n        int gradientIndex = hash & 7; // Use lower 3 bits for 8 directions\n        double gx = grad[gradientIndex][0];\n        double gy = grad[gradientIndex][1];\n        return (gx * x + gy * y);\n    }\n} 
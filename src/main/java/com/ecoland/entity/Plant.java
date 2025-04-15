package com.ecoland.entity;

import com.ecoland.model.Tile;
import com.ecoland.model.World;
import com.ecoland.model.TerrainType;
import com.ecoland.simulation.Simulation;

import java.util.Random;

public class Plant extends Entity {

    // Constants related to plant growth and decay
    private static final double BASE_GROWTH_RATE = 0.05; // Base growth per tick
    private static final double FERTILITY_SCALING = 0.1; // How much fertility affects growth
    private static final double MAX_GROWTH_PER_TICK = 0.1;
    // Plants don't really need energy/health in the same way, using energy for 'size' or 'seed potential'
    private static final double INITIAL_PLANT_ENERGY = 1.0; // Represents initial size/maturity
    private static final double PLANT_ENERGY_DEPLETION = 0.001; // Slow decay

    private static final Random random = new Random();

    /** Constructor for initial placement (uses default genes) */
    public Plant(int x, int y) {
        // Plants don't move, so use the constructor that creates default genes
        super(x, y, SpeciesType.PLANT);
        
        // Initialize specific plant values if needed
        this.energy = INITIAL_PLANT_ENERGY;
    }

    /** Constructor for offspring with inherited genes */
    public Plant(int x, int y, Genes parentGenes) {
        super(x, y, SpeciesType.PLANT, new Genes(parentGenes));
    }

    @Override
    public void update(Simulation simulation, World world) {
        if (!isAlive) return;

        Tile currentTile = world.getTile(x, y);
        if (currentTile == null || currentTile.getTerrainType() == TerrainType.WATER) {
            // Plant cannot survive here
            die();
            return;
        }

        // 1. Grow based on tile fertility
        grow(currentTile);

        // 2. Attempt to spread seeds using genetic spreadChance
        if (random.nextDouble() < getSpreadChance()) { // Use getter for genetic trait
            spreadSeeds(simulation);
        }

        // 3. Deplete internal 'energy' (size/maturity)
        depleteEnergy(PLANT_ENERGY_DEPLETION);
        if (!isAlive) {
            // If plant dies, maybe remove some food value from tile?
            currentTile.setPlantFoodValue(currentTile.getPlantFoodValue() * 0.5); // Decay
        }
    }

    private void grow(Tile tile) {
        double growthAmount = BASE_GROWTH_RATE + (tile.getFertility() * FERTILITY_SCALING);
        growthAmount = Math.min(growthAmount, MAX_GROWTH_PER_TICK); // Cap growth
        tile.growPlantFood(growthAmount);
        // Maybe link internal energy gain to growth?
        gainEnergy(growthAmount * 0.1); // Gain a little 'maturity'
    }

    private void spreadSeeds(Simulation simulation) {
        // Use reproductionThreshold and reproductionCost from genes
        if (this.energy >= getReproductionThreshold()) {
             // Pass parent's genes for mutation and inheritance
             Entity offspring = simulation.spawnOffspring(this, SpeciesType.PLANT, this.genes);
             if (offspring != null) {
                 depleteEnergy(getReproductionCost()); // Use cost from genes
             }
        }
    }

    // Override die for plants if specific logic is needed
    @Override
    protected void die() {
        super.die();
        // Plant-specific death logic if any (e.g., mark tile as recently occupied?)
    }
} 
package com.ecoland.entity;

import java.util.Random;

/**
 * Represents the genetic makeup of an entity, holding inheritable traits.
 */
public class Genes {
    private static final Random random = new Random();
    private static final double MUTATION_RATE = 0.05; // 5% chance per gene to mutate
    private static final double MUTATION_MAGNITUDE = 0.1; // Mutate by +/- 10% of original value

    // --- Inheritable Traits ---
    // Movement/Sensing
    public final double speed;
    public final double visionRange;

    // Metabolism/Life Cycle
    public final double maxEnergy;          // Max energy capacity
    public final double energyEfficiency;   // Factor for energy gain/loss (e.g., >1 more efficient)
    public final double reproductionThreshold; // Energy level to reproduce
    public final double reproductionCost;      // Energy cost to reproduce
    public final double maxHealth;          // Max health (optional, could be fixed)

    // Species-specific traits
    public final double spreadChance;       // Plant-specific: chance per tick to attempt spreading
    // public final double carnivoreAttackDamage; // Example for future

    /**
     * Create genes object based on the species type.
     * Sets reasonable defaults for each species.
     */
    public Genes(SpeciesType type) {
        // Set appropriate defaults based on species
        if (type == SpeciesType.HERBIVORE) {
            speed = 1.0;
            visionRange = 5.0;
            maxEnergy = 100.0;
            energyEfficiency = 1.0;
            reproductionThreshold = 80.0;
            reproductionCost = 40.0;
            maxHealth = 100.0;
            spreadChance = 0.0;
        } else if (type == SpeciesType.CARNIVORE) {
            speed = 1.2;
            visionRange = 7.0;
            maxEnergy = 120.0;
            energyEfficiency = 1.0;
            reproductionThreshold = 100.0;
            reproductionCost = 50.0;
            maxHealth = 100.0;
            spreadChance = 0.0;
        } else if (type == SpeciesType.OMNIVORE) {
            speed = 1.1;
            visionRange = 6.0;
            maxEnergy = 110.0;
            energyEfficiency = 1.1;
            reproductionThreshold = 90.0;
            reproductionCost = 45.0;
            maxHealth = 100.0;
            spreadChance = 0.0;
        } else if (type == SpeciesType.APEX_PREDATOR) {
            speed = 1.3;
            visionRange = 8.0;
            maxEnergy = 150.0;
            energyEfficiency = 0.9;
            reproductionThreshold = 120.0;
            reproductionCost = 60.0;
            maxHealth = 150.0;
            spreadChance = 0.0;
        } else if (type == SpeciesType.DECOMPOSER) {
            speed = 0.8;
            visionRange = 4.0;
            maxEnergy = 80.0;
            energyEfficiency = 1.2;
            reproductionThreshold = 60.0;
            reproductionCost = 30.0;
            maxHealth = 70.0;
            spreadChance = 0.0;
        } else if (type == SpeciesType.SCAVENGER) {
            speed = 1.2;
            visionRange = 7.0;
            maxEnergy = 90.0;
            energyEfficiency = 1.3; // Very efficient at processing dead bodies
            reproductionThreshold = 70.0;
            reproductionCost = 35.0;
            maxHealth = 80.0;
            spreadChance = 0.0;
        } else if (type == SpeciesType.PLANT) {
            // Plants don't move, have small vision, but can spread
            speed = 0.0;
            visionRange = 0.0;
            maxEnergy = 100.0;
            energyEfficiency = 2.0; // Very efficient at storing energy from sunlight
            reproductionThreshold = 50.0;
            reproductionCost = 20.0;
            maxHealth = 50.0;
            spreadChance = 0.01; // Small chance to spread to nearby tiles
        } else {
            // Default for unspecified types (robust defaults)
            speed = 1.0;
            visionRange = 5.0;
            maxEnergy = 100.0;
            energyEfficiency = 1.0;
            reproductionThreshold = 80.0;
            reproductionCost = 40.0;
            maxHealth = 100.0;
            spreadChance = 0.0;
        }
    }

    /**
     * Constructor for creating offspring genes based on a parent, with mutation.
     * For simplicity, using asexual reproduction model (one parent).
     * TODO: Implement sexual reproduction (mixing genes from two parents).
     */
    public Genes(Genes parentGenes) {
        this.speed = mutate(parentGenes.speed);
        this.visionRange = mutate(parentGenes.visionRange);
        this.maxEnergy = mutate(parentGenes.maxEnergy);
        this.energyEfficiency = mutate(parentGenes.energyEfficiency);
        this.reproductionThreshold = mutate(parentGenes.reproductionThreshold);
        this.reproductionCost = mutate(parentGenes.reproductionCost);
        this.maxHealth = mutate(parentGenes.maxHealth);
        this.spreadChance = mutate(parentGenes.spreadChance); // Mutate spread chance
    }

    /**
     * Applies mutation to a gene value.
     * @param value The original gene value.
     * @return The potentially mutated value.
     */
    private double mutate(double value) {
        if (random.nextDouble() < MUTATION_RATE) {
            double change = value * MUTATION_MAGNITUDE * (random.nextDouble() * 2 - 1); // +/- change
            double mutatedValue = value + change;
            // Ensure values don't go below a reasonable minimum (e.g., 0 or small epsilon)
            // For probabilities like spreadChance, also cap at 1.0
            if (value <= 1.0 && value >= 0.0) { // Crude check if it's likely a probability
                 mutatedValue = Math.max(0.0, Math.min(1.0, mutatedValue));
            } else {
                 mutatedValue = Math.max(0.01, mutatedValue); // General minimum for other stats
            }
            return mutatedValue;
        }
        return value;
    }

    @Override
    public String toString() {
        // Add spreadChance to the output string
        return String.format(
            "Genes[Spd:%.2f, Vis:%.2f, MaxE:%.1f, Eff:%.2f, RepT:%.1f, RepC:%.1f, MaxH:%.1f, Sprd:%.3f]",
            speed, visionRange, maxEnergy, energyEfficiency, reproductionThreshold, reproductionCost, maxHealth, spreadChance
        );
    }
} 
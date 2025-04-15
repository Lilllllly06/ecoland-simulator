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
     * Constructor for creating default genes for a species.
     * Values should be reasonable defaults.
     */
    public Genes(SpeciesType type) {
        // Set default values based on species type
        switch (type) {
            case HERBIVORE:
                this.speed = 1.0;
                this.visionRange = 5.0;
                this.maxEnergy = 100.0;
                this.energyEfficiency = 1.0;
                this.reproductionThreshold = 80.0;
                this.reproductionCost = 40.0;
                this.maxHealth = 100.0;
                this.spreadChance = 0.0; // Not applicable
                break;
            case CARNIVORE:
                this.speed = 1.2;
                this.visionRange = 7.0;
                this.maxEnergy = 120.0;
                this.energyEfficiency = 1.0;
                this.reproductionThreshold = 100.0;
                this.reproductionCost = 50.0;
                this.maxHealth = 100.0;
                this.spreadChance = 0.0; // Not applicable
                break;
            case PLANT:
            default:
                // Plants might have different relevant genes (e.g., growth rate, spread chance)
                this.speed = 0.0;
                this.visionRange = 0.0;
                this.maxEnergy = 10.0; // Represents max size/maturity?
                this.energyEfficiency = 1.0;
                this.reproductionThreshold = 8.0; // Maturity threshold for seeding
                this.reproductionCost = 2.0;  // Cost for seeding
                this.maxHealth = 1.0;
                this.spreadChance = 0.01; // Default spread chance gene
                break;
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
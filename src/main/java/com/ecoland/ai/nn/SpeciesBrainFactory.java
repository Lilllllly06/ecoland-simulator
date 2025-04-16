package com.ecoland.ai.nn;

import com.ecoland.entity.SpeciesType;

/**
 * Factory class that creates specialized brain instances for different species types.
 * This allows each species to have different neural network configurations and
 * learning capabilities optimized for their ecological niche.
 */
public class SpeciesBrainFactory {

    /**
     * Creates a specialized brain for a given species type with the specified vision range.
     * 
     * @param type The species type
     * @param visionRange The vision range of the entity
     * @return A new brain instance specialized for the given species
     */
    public static AnimalBrain createBrain(SpeciesType type, int visionRange) {
        switch (type) {
            case HERBIVORE:
                return new HerbivoreBrain(visionRange);
            case CARNIVORE:
                return new CarnivoreBrain(visionRange);
            case OMNIVORE:
                return new OmnivoreBrain(visionRange);
            case SCAVENGER:
                return new ScavengerBrain(visionRange);
            case APEX_PREDATOR:
                return new ApexPredatorBrain(visionRange);
            case DECOMPOSER:
                return new DecomposerBrain(visionRange);
            case PLANT:
                return new PlantBrain(); // Plants have minimal brains
            default:
                return new AnimalBrain(visionRange); // Generic fallback
        }
    }
    
    /**
     * Creates a specialized child brain based on parent brain for inheritance.
     * 
     * @param parentBrain The parent's brain
     * @param type The species type
     * @return A new brain instance inherited from the parent with species-specific mutations
     */
    public static AnimalBrain createChildBrain(AnimalBrain parentBrain, SpeciesType type) {
        switch (type) {
            case HERBIVORE:
                return parentBrain instanceof HerbivoreBrain ? 
                       ((HerbivoreBrain) parentBrain).createChild() : 
                       new HerbivoreBrain(parentBrain);
            case CARNIVORE:
                return parentBrain instanceof CarnivoreBrain ? 
                       ((CarnivoreBrain) parentBrain).createChild() : 
                       new CarnivoreBrain(parentBrain);
            case OMNIVORE:
                return parentBrain instanceof OmnivoreBrain ? 
                       ((OmnivoreBrain) parentBrain).createChild() : 
                       new OmnivoreBrain(parentBrain);
            case SCAVENGER:
                return parentBrain instanceof ScavengerBrain ? 
                       ((ScavengerBrain) parentBrain).createChild() : 
                       new ScavengerBrain(parentBrain);
            case APEX_PREDATOR:
                return parentBrain instanceof ApexPredatorBrain ? 
                       ((ApexPredatorBrain) parentBrain).createChild() : 
                       new ApexPredatorBrain(parentBrain);
            case DECOMPOSER:
                return parentBrain instanceof DecomposerBrain ? 
                       ((DecomposerBrain) parentBrain).createChild() : 
                       new DecomposerBrain(parentBrain);
            case PLANT:
                return new PlantBrain(); // Plants have minimal brains with no inheritance
            default:
                return parentBrain.createChild(); // Generic fallback
        }
    }
    
    /**
     * Creates a specialized child brain from two parents through crossover.
     * 
     * @param parent1Brain First parent's brain
     * @param parent2Brain Second parent's brain
     * @param type The species type
     * @return A new brain instance created through crossover of both parents
     */
    public static AnimalBrain createCrossoverBrain(AnimalBrain parent1Brain, AnimalBrain parent2Brain, SpeciesType type) {
        // For most cases, delegate to the standard crossover
        return AnimalBrain.crossover(parent1Brain, parent2Brain);
    }
} 
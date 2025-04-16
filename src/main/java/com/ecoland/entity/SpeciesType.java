package com.ecoland.entity;

import java.io.Serializable;

/**
 * Enum representing different species types in the ecosystem.
 * Each type has different behavior patterns and roles.
 */
public enum SpeciesType implements Serializable {
    HERBIVORE,      // Plant eaters
    CARNIVORE,      // Meat eaters (prey on herbivores)
    PLANT,          // Basic producer
    OMNIVORE,       // Can eat both plants and meat
    SCAVENGER,      // Eats dead animals but doesn't hunt
    APEX_PREDATOR,  // Top predator, hunts carnivores and herbivores
    DECOMPOSER      // Breaks down dead organisms, improving soil fertility
} 
package com.ecoland.entity;

import com.ecoland.ai.nn.AnimalBrain;
import com.ecoland.model.World;
import com.ecoland.simulation.Simulation;

public abstract class Entity {
    protected int x;
    protected int y;
    protected double energy;
    protected double health;
    protected final SpeciesType speciesType;
    protected boolean isAlive = true;
    protected final Genes genes;
    protected AnimalBrain brain; // Neural network brain for advanced decision-making

    public Entity(int x, int y, SpeciesType speciesType, Genes genes) {
        this.x = x;
        this.y = y;
        this.speciesType = speciesType;
        this.genes = genes;
        this.health = genes.maxHealth;
        this.energy = genes.maxEnergy * 0.7;
        
        // Initialize brain for animals (not plants)
        if (speciesType != SpeciesType.PLANT) {
            this.brain = new AnimalBrain((int)genes.visionRange);
        }
    }

    public Entity(int x, int y, SpeciesType speciesType) {
        this(x, y, speciesType, new Genes(speciesType));
    }
    
    /**
     * Create an entity with a specific brain (for offspring with inherited brains).
     */
    protected Entity(int x, int y, SpeciesType speciesType, Genes genes, AnimalBrain parentBrain) {
        this(x, y, speciesType, genes);
        
        // Override with parent's brain (with mutations)
        if (parentBrain != null && speciesType != SpeciesType.PLANT) {
            this.brain = new AnimalBrain(parentBrain); // Copy parent's brain
        }
    }

    /**
     * Defines the behavior of the entity for a single simulation tick.
     * Subclasses must implement their specific AI logic here.
     *
     * @param simulation The main simulation instance, providing context (world, entity manager).
     * @param world The current state of the simulation world (convenience access).
     */
    public abstract void update(Simulation simulation, World world);

    public void update(World world) {
         System.err.println("Warning: Entity.update(World) called directly. Use update(Simulation, World). Entity: " + this.speciesType);
         depleteEnergy(0.01 * genes.energyEfficiency);
    }

    protected void depleteEnergy(double baseAmount) {
        double actualDepletion = baseAmount / genes.energyEfficiency;
        this.energy -= actualDepletion;
        if (this.energy <= 0) {
            this.health = 0;
            die();
        }
    }

    public void takeDamage(double amount) {
        if (!isAlive) return;
        this.health -= amount;
        if (this.health <= 0) {
            this.health = 0;
            die();
        }
    }

    protected void gainEnergy(double baseAmount) {
        double actualGain = baseAmount * genes.energyEfficiency;
        this.energy += actualGain;
        this.energy = Math.min(this.energy, genes.maxEnergy);
    }

    protected void die() {
        this.isAlive = false;
        System.out.println(speciesType + " at (" + x + ", " + y + ") died. Genes: " + genes.toString());
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public double getEnergy() {
        return energy;
    }

    public double getHealth() {
        return health;
    }

    public SpeciesType getSpeciesType() {
        return speciesType;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public Genes getGenes() {
        return genes;
    }
    
    public AnimalBrain getBrain() {
        return brain;
    }

    public double getSpeed() {
        return genes.speed;
    }

    public double getVisionRange() {
        return genes.visionRange;
    }

    public double getMaxEnergy() {
        return genes.maxEnergy;
    }

    public double getReproductionThreshold() {
        return genes.reproductionThreshold;
    }

    public double getReproductionCost() {
        return genes.reproductionCost;
    }

    public double getMaxHealth() {
        return genes.maxHealth;
    }

    public double getSpreadChance() {
        return genes.spreadChance;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
} 
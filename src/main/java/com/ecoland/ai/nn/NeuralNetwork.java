package com.ecoland.ai.nn;

import java.io.Serializable;
import java.util.Random;

/**
 * A simple feed-forward neural network implementation with one hidden layer.
 * This network is used by creatures to make decisions based on their environment.
 */
public class NeuralNetwork implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Random random = new Random();
    
    // Network architecture
    private final int inputSize;
    private final int hiddenSize;
    private final int outputSize;
    
    // Weights and biases
    private final double[][] weightsInputToHidden;
    private final double[][] weightsHiddenToOutput;
    private final double[] biasesHidden;
    private final double[] biasesOutput;
    
    // Mutation parameters
    private static final double MUTATION_RATE = 0.1;
    private static final double MUTATION_RANGE = 0.2;
    
    /**
     * Create a new neural network with random weights.
     *
     * @param inputSize Number of input neurons
     * @param hiddenSize Number of hidden neurons
     * @param outputSize Number of output neurons
     */
    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.outputSize = outputSize;
        
        // Initialize weights with random values between -1 and 1
        weightsInputToHidden = new double[inputSize][hiddenSize];
        weightsHiddenToOutput = new double[hiddenSize][outputSize];
        biasesHidden = new double[hiddenSize];
        biasesOutput = new double[outputSize];
        
        // Initialize with random weights
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weightsInputToHidden[i][j] = random.nextDouble() * 2 - 1; // -1 to 1
            }
        }
        
        for (int i = 0; i < hiddenSize; i++) {
            biasesHidden[i] = random.nextDouble() * 2 - 1; // -1 to 1
            
            for (int j = 0; j < outputSize; j++) {
                weightsHiddenToOutput[i][j] = random.nextDouble() * 2 - 1; // -1 to 1
            }
        }
        
        for (int i = 0; i < outputSize; i++) {
            biasesOutput[i] = random.nextDouble() * 2 - 1; // -1 to 1
        }
    }
    
    /**
     * Create a new neural network by copying another network.
     *
     * @param other The network to copy
     */
    public NeuralNetwork(NeuralNetwork other) {
        this.inputSize = other.inputSize;
        this.hiddenSize = other.hiddenSize;
        this.outputSize = other.outputSize;
        
        // Deep copy the weights and biases
        this.weightsInputToHidden = new double[inputSize][hiddenSize];
        this.weightsHiddenToOutput = new double[hiddenSize][outputSize];
        this.biasesHidden = new double[hiddenSize];
        this.biasesOutput = new double[outputSize];
        
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                this.weightsInputToHidden[i][j] = other.weightsInputToHidden[i][j];
            }
        }
        
        for (int i = 0; i < hiddenSize; i++) {
            this.biasesHidden[i] = other.biasesHidden[i];
            
            for (int j = 0; j < outputSize; j++) {
                this.weightsHiddenToOutput[i][j] = other.weightsHiddenToOutput[i][j];
            }
        }
        
        for (int i = 0; i < outputSize; i++) {
            this.biasesOutput[i] = other.biasesOutput[i];
        }
    }
    
    /**
     * Forward pass through the network.
     *
     * @param inputs The input values to the network
     * @return The output values from the network
     */
    public double[] feedForward(double[] inputs) {
        if (inputs.length != inputSize) {
            throw new IllegalArgumentException("Input size doesn't match network input size");
        }
        
        // Calculate hidden layer values
        double[] hiddenValues = new double[hiddenSize];
        for (int j = 0; j < hiddenSize; j++) {
            double sum = biasesHidden[j];
            for (int i = 0; i < inputSize; i++) {
                sum += inputs[i] * weightsInputToHidden[i][j];
            }
            hiddenValues[j] = sigmoid(sum);
        }
        
        // Calculate output layer values
        double[] outputs = new double[outputSize];
        for (int j = 0; j < outputSize; j++) {
            double sum = biasesOutput[j];
            for (int i = 0; i < hiddenSize; i++) {
                sum += hiddenValues[i] * weightsHiddenToOutput[i][j];
            }
            outputs[j] = sigmoid(sum);
        }
        
        return outputs;
    }
    
    /**
     * Sigmoid activation function.
     *
     * @param x Input value
     * @return Sigmoid of the input (between 0 and 1)
     */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
    
    /**
     * Create a new neural network by crossover of two parent networks.
     *
     * @param parent1 First parent network
     * @param parent2 Second parent network
     * @return A new network with traits from both parents
     */
    public static NeuralNetwork crossover(NeuralNetwork parent1, NeuralNetwork parent2) {
        // Ensure both parents have the same architecture
        if (parent1.inputSize != parent2.inputSize || 
            parent1.hiddenSize != parent2.hiddenSize || 
            parent1.outputSize != parent2.outputSize) {
            throw new IllegalArgumentException("Parent networks must have the same architecture");
        }
        
        // Create a new network with the same architecture
        NeuralNetwork child = new NeuralNetwork(
            parent1.inputSize, parent1.hiddenSize, parent1.outputSize);
        
        // Crossover weights and biases (randomly choose from parents or average)
        for (int i = 0; i < child.inputSize; i++) {
            for (int j = 0; j < child.hiddenSize; j++) {
                if (random.nextBoolean()) {
                    child.weightsInputToHidden[i][j] = parent1.weightsInputToHidden[i][j];
                } else {
                    child.weightsInputToHidden[i][j] = parent2.weightsInputToHidden[i][j];
                }
                
                // Small chance of mutation
                if (random.nextDouble() < MUTATION_RATE) {
                    child.weightsInputToHidden[i][j] += random.nextGaussian() * MUTATION_RANGE;
                }
            }
        }
        
        for (int i = 0; i < child.hiddenSize; i++) {
            // Hidden biases
            if (random.nextBoolean()) {
                child.biasesHidden[i] = parent1.biasesHidden[i];
            } else {
                child.biasesHidden[i] = parent2.biasesHidden[i];
            }
            
            // Small chance of mutation
            if (random.nextDouble() < MUTATION_RATE) {
                child.biasesHidden[i] += random.nextGaussian() * MUTATION_RANGE;
            }
            
            // Hidden to output weights
            for (int j = 0; j < child.outputSize; j++) {
                if (random.nextBoolean()) {
                    child.weightsHiddenToOutput[i][j] = parent1.weightsHiddenToOutput[i][j];
                } else {
                    child.weightsHiddenToOutput[i][j] = parent2.weightsHiddenToOutput[i][j];
                }
                
                // Small chance of mutation
                if (random.nextDouble() < MUTATION_RATE) {
                    child.weightsHiddenToOutput[i][j] += random.nextGaussian() * MUTATION_RANGE;
                }
            }
        }
        
        for (int i = 0; i < child.outputSize; i++) {
            // Output biases
            if (random.nextBoolean()) {
                child.biasesOutput[i] = parent1.biasesOutput[i];
            } else {
                child.biasesOutput[i] = parent2.biasesOutput[i];
            }
            
            // Small chance of mutation
            if (random.nextDouble() < MUTATION_RATE) {
                child.biasesOutput[i] += random.nextGaussian() * MUTATION_RANGE;
            }
        }
        
        return child;
    }
} 
} 
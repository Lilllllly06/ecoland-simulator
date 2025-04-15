package com.ecoland.data;

import com.ecoland.simulation.EntityManager;
import com.ecoland.entity.SpeciesType;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logs simulation data over time
 */
public class DataLogger {
    
    private final List<DataPoint> dataPoints = new ArrayList<>();
    private final int logInterval;
    private long lastLoggedTick = -1;
    
    /**
     * Creates a new DataLogger that logs every N ticks
     * @param logInterval Number of ticks between logging points
     */
    public DataLogger(int logInterval) {
        this.logInterval = logInterval;
    }
    
    /**
     * Records simulation data for the current tick
     * @param tick Current simulation tick
     * @param entityManager Entity manager containing population data
     */
    public void recordTick(long tick, EntityManager entityManager) {
        // Only log at the specified interval
        if (tick % logInterval != 0 && tick != 0) {
            return;
        }
        
        // Skip if we've already logged this tick (prevent duplicate logging)
        if (tick == lastLoggedTick) {
            return;
        }
        
        int herbivoreCount = entityManager.getPopulationCount(SpeciesType.HERBIVORE);
        int carnivoreCount = entityManager.getPopulationCount(SpeciesType.CARNIVORE);
        int plantCount = entityManager.getPopulationCount(SpeciesType.PLANT);
        
        DataPoint dataPoint = new DataPoint(
            tick,
            herbivoreCount,
            carnivoreCount,
            plantCount
        );
        
        dataPoints.add(dataPoint);
        lastLoggedTick = tick;
    }
    
    /**
     * Gets all recorded data points
     * @return List of data points
     */
    public List<DataPoint> getDataPoints() {
        return dataPoints;
    }
    
    /**
     * Gets the most recent data point
     * @return The most recent data point, or null if none exist
     */
    public DataPoint getLatestDataPoint() {
        if (dataPoints.isEmpty()) {
            return null;
        }
        return dataPoints.get(dataPoints.size() - 1);
    }
    
    /**
     * Gets data for the specified number of most recent ticks
     * @param count Number of data points to return
     * @return List of recent data points
     */
    public List<DataPoint> getRecentDataPoints(int count) {
        if (dataPoints.isEmpty()) {
            return new ArrayList<>();
        }
        
        int startIndex = Math.max(0, dataPoints.size() - count);
        return dataPoints.subList(startIndex, dataPoints.size());
    }
    
    /**
     * Saves logged data to a CSV file
     * @param filePath Path to save the data
     * @return true if save was successful, false otherwise
     */
    public boolean saveData(String filePath) {
        if (dataPoints.isEmpty()) {
            System.out.println("No data to save");
            return false;
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            // Write header
            writer.println("Tick,Herbivores,Carnivores,Plants");
            
            // Write data
            for (DataPoint point : dataPoints) {
                writer.println(
                    point.tick + "," +
                    point.herbivoreCount + "," + 
                    point.carnivoreCount + "," +
                    point.plantCount
                );
            }
            
            System.out.println("Data saved to: " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Represents a single data point in time
     */
    public static class DataPoint {
        public final long tick;
        public final int herbivoreCount;
        public final int carnivoreCount;  
        public final int plantCount;
        public final long timestamp;
        
        public DataPoint(long tick, int herbivoreCount, int carnivoreCount, int plantCount) {
            this.tick = tick;
            this.herbivoreCount = herbivoreCount;
            this.carnivoreCount = carnivoreCount;
            this.plantCount = plantCount;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Gets population data history for charting
     * @param count Maximum number of data points to return
     * @return List of arrays containing [tick, herbivores, carnivores, plants, total]
     */
    public List<long[]> getPopulationData(int count) {
        List<long[]> result = new ArrayList<>();
        
        // Get recent data points, limited by count
        List<DataPoint> points = getRecentDataPoints(count);
        
        // Convert to arrays for easier processing in charts
        for (DataPoint point : points) {
            long total = point.herbivoreCount + point.carnivoreCount + point.plantCount;
            result.add(new long[]{
                point.tick,
                point.herbivoreCount,
                point.carnivoreCount,
                point.plantCount,
                total
            });
        }
        
        return result;
    }
    
    /**
     * Calculates statistics for genes of a particular species
     * @param entityManager Entity manager containing the entities
     * @param speciesType Species type to analyze
     * @return Map of gene name to statistics array [min, max, avg, stdDev]
     */
    public Map<String, double[]> getGeneStats(EntityManager entityManager, SpeciesType speciesType) {
        Map<String, double[]> result = new HashMap<>();
        
        // This is a placeholder implementation that returns empty stats
        // In a real implementation, you would:
        // 1. Get all entities of the specified species
        // 2. For each gene, calculate min, max, avg, stdDev
        // 3. Return the statistics
        
        return result;
    }
} 
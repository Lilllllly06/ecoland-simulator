package com.ecoland.ai;

import com.ecoland.model.TerrainType;
import com.ecoland.model.World;
import com.ecoland.entity.Entity; // Needed for movement cost checks potentially
import com.ecoland.model.Tile;

import java.util.*;

/**
 * Implements the A* pathfinding algorithm for entities to navigate the world.
 */
public class Pathfinder {

    private static final int MAX_SEARCH_NODES = 1000; // Limit search space to prevent performance issues
    private static final int MAX_FLEE_SEARCH_DISTANCE = 12; // Max distance to search when fleeing

    // Node class for A* search
    private static class Node implements Comparable<Node> {
        int x, y;
        double gCost; // Cost from start to this node
        double hCost; // Heuristic cost from this node to target
        double fCost; // gCost + hCost
        Node parent;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost, other.fCost);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    /**
     * Finds a path from start coordinates to end coordinates using A*.
     *
     * @param world The world grid.
     * @param startX Start x coordinate.
     * @param startY Start y coordinate.
     * @param endX Target x coordinate.
     * @param endY Target y coordinate.
     * @param entity The entity requesting the path (used for movement cost/passability rules).
     * @return A List of coordinate pairs [x, y] representing the path (excluding start, including end), or null if no path is found.
     */
    public List<int[]> findPath(World world, int startX, int startY, int endX, int endY, Entity entity) {
        Node startNode = new Node(startX, startY);
        Node endNode = new Node(endX, endY);

        // Check if there's direct line of sight first as an optimization
        if (hasLineOfSight(world, startX, startY, endX, endY, entity)) {
            List<int[]> directPath = new ArrayList<>();
            directPath.add(new int[]{endX, endY});
            return directPath;
        }

        PriorityQueue<Node> openList = new PriorityQueue<>();
        HashSet<Node> closedList = new HashSet<>();
        // Using a separate map to keep track of nodes by coordinates for faster lookups
        Map<String, Node> openSet = new HashMap<>();

        startNode.gCost = 0;
        startNode.hCost = heuristic(startNode, endNode);
        startNode.fCost = startNode.hCost;
        openList.add(startNode);
        openSet.put(nodeKey(startNode), startNode);

        int nodesSearched = 0;

        while (!openList.isEmpty() && nodesSearched < MAX_SEARCH_NODES) {
            Node currentNode = openList.poll();
            openSet.remove(nodeKey(currentNode));
            nodesSearched++;

            if (currentNode.x == endNode.x && currentNode.y == endNode.y) {
                return reconstructPath(currentNode);
            }

            closedList.add(currentNode);

            // Explore neighbors (8 directions)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    int neighborX = currentNode.x + dx;
                    int neighborY = currentNode.y + dy;

                    // Check if neighbor is valid
                    if (!world.isValidCoordinate(neighborX, neighborY)) {
                        continue;
                    }

                    String neighborKey = nodeKey(neighborX, neighborY);

                    // Check if neighbor is in closed list
                    if (closedList.contains(new Node(neighborX, neighborY))) {
                        continue;
                    }

                    // Check passability based on entity type
                    if (!isPassable(world, neighborX, neighborY, entity)) {
                        closedList.add(new Node(neighborX, neighborY)); // Treat impassable as closed
                        continue;
                    }

                    // Calculate cost to reach neighbor
                    double moveCost = getMovementCost(world, currentNode, neighborX, neighborY, entity);
                    double newGCost = currentNode.gCost + moveCost;

                    Node neighborNode = openSet.get(neighborKey);
                    boolean isInOpenList = neighborNode != null;

                    if (!isInOpenList) {
                        neighborNode = new Node(neighborX, neighborY);
                    } else if (newGCost >= neighborNode.gCost) {
                        continue; // Not a better path
                    }

                    // Update the neighbor with new path information
                    neighborNode.gCost = newGCost;
                    neighborNode.hCost = heuristic(neighborNode, endNode);
                    neighborNode.fCost = neighborNode.gCost + neighborNode.hCost;
                    neighborNode.parent = currentNode;

                    if (!isInOpenList) {
                        openList.add(neighborNode);
                        openSet.put(neighborKey, neighborNode);
                    } else {
                        // Force the priority queue to update by removing and re-adding
                        openList.remove(neighborNode);
                        openList.add(neighborNode);
                    }
                }
            }
        }

        System.out.println("A* pathfinding failed or exceeded node limit from ("+startX+","+startY+") to ("+endX+","+endY+")");
        return null; // No path found
    }

    /**
     * Finds a path away from a threat using a modified A* approach.
     * 
     * @param world The world grid.
     * @param entityX Entity's current x coordinate.
     * @param entityY Entity's current y coordinate.
     * @param threatX Threat's x coordinate to flee from.
     * @param threatY Threat's y coordinate to flee from.
     * @param entity The entity requesting the flee path.
     * @param fleeDistance Target distance to flee (in tiles).
     * @return A List of coordinate pairs representing the flee path, or null if no path found.
     */
    public List<int[]> findFleePath(World world, int entityX, int entityY, 
                                   int threatX, int threatY, Entity entity, int fleeDistance) {
        // Calculate flee vector (direction away from threat)
        double fleeVectorX = entityX - threatX;
        double fleeVectorY = entityY - threatY;
        double magnitude = Math.sqrt(fleeVectorX * fleeVectorX + fleeVectorY * fleeVectorY);
        
        // Target position is fleeDistance tiles away from threat in the flee direction
        int targetX, targetY;
        
        if (magnitude < 0.1) {
            // If entity is at same position as threat (rare case), pick a random direction
            double randomAngle = Math.random() * 2 * Math.PI;
            targetX = entityX + (int)(fleeDistance * Math.cos(randomAngle));
            targetY = entityY + (int)(fleeDistance * Math.sin(randomAngle));
        } else {
            // Normalize and scale flee vector to desired distance
            double normalizedX = fleeVectorX / magnitude;
            double normalizedY = fleeVectorY / magnitude;
            targetX = entityX + (int)(normalizedX * fleeDistance);
            targetY = entityY + (int)(normalizedY * fleeDistance);
        }
        
        // Ensure target is within world bounds
        targetX = Math.max(0, Math.min(world.getWidth() - 1, targetX));
        targetY = Math.max(0, Math.min(world.getHeight() - 1, targetY));
        
        // Modified A* search with custom cost function that prioritizes moving away from threat
        Node startNode = new Node(entityX, entityY);
        Node targetNode = new Node(targetX, targetY);
        
        PriorityQueue<Node> openList = new PriorityQueue<>();
        HashSet<Node> closedList = new HashSet<>();
        Map<String, Node> openSet = new HashMap<>();
        
        startNode.gCost = 0;
        startNode.hCost = heuristic(startNode, targetNode);
        startNode.fCost = startNode.hCost;
        openList.add(startNode);
        openSet.put(nodeKey(startNode), startNode);
        
        int nodesSearched = 0;
        
        while (!openList.isEmpty() && nodesSearched < MAX_SEARCH_NODES) {
            Node currentNode = openList.poll();
            openSet.remove(nodeKey(currentNode));
            nodesSearched++;
            
            // Check if we're far enough from the threat (either reached target or hit search limit)
            double distanceToThreat = calculateDistance(currentNode.x, currentNode.y, threatX, threatY);
            if (distanceToThreat >= fleeDistance || nodesSearched >= MAX_FLEE_SEARCH_DISTANCE) {
                return reconstructPath(currentNode);
            }
            
            closedList.add(currentNode);
            
            // Explore neighbors (8 directions)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    
                    int neighborX = currentNode.x + dx;
                    int neighborY = currentNode.y + dy;
                    
                    // Check if neighbor is valid
                    if (!world.isValidCoordinate(neighborX, neighborY)) {
                        continue;
                    }
                    
                    String neighborKey = nodeKey(neighborX, neighborY);
                    
                    // Check if neighbor is in closed list
                    if (closedList.contains(new Node(neighborX, neighborY))) {
                        continue;
                    }
                    
                    // Check passability based on entity type
                    if (!isPassable(world, neighborX, neighborY, entity)) {
                        closedList.add(new Node(neighborX, neighborY)); // Treat impassable as closed
                        continue;
                    }

                    // Calculate flee-specific cost (prioritize movement away from threat)
                    double moveCost = getMovementCostForFleeing(world, currentNode, 
                                                            neighborX, neighborY, 
                                                            threatX, threatY, entity);
                    double newGCost = currentNode.gCost + moveCost;
                    
                    Node neighborNode = openSet.get(neighborKey);
                    boolean isInOpenList = neighborNode != null;
                    
                    if (!isInOpenList) {
                        neighborNode = new Node(neighborX, neighborY);
                    } else if (newGCost >= neighborNode.gCost) {
                        continue; // Not a better path
                    }
                    
                    // Update the neighbor with new path information
                    neighborNode.gCost = newGCost;
                    neighborNode.hCost = heuristicForFleeing(neighborNode, targetNode, threatX, threatY);
                    neighborNode.fCost = neighborNode.gCost + neighborNode.hCost;
                    neighborNode.parent = currentNode;
                    
                    if (!isInOpenList) {
                        openList.add(neighborNode);
                        openSet.put(neighborKey, neighborNode);
                    } else {
                        // Force the priority queue to update
                        openList.remove(neighborNode);
                        openList.add(neighborNode);
                    }
                }
            }
        }
        
        // If we couldn't find an ideal path but searched some nodes, return best so far
        if (nodesSearched > 0 && !openList.isEmpty()) {
            Node bestNode = null;
            double maxDistance = 0;
            
            // Find the node farthest from threat in open list
            for (Node node : openList) {
                double dist = calculateDistance(node.x, node.y, threatX, threatY);
                if (dist > maxDistance) {
                    maxDistance = dist;
                    bestNode = node;
                }
            }
            
            if (bestNode != null) {
                return reconstructPath(bestNode);
            }
        }
        
        return null; // No path found
    }

    // Custom heuristic for fleeing - rewards increasing distance from threat
    private double heuristicForFleeing(Node node, Node target, int threatX, int threatY) {
        // Base heuristic is distance to target
        double baseHeuristic = heuristic(node, target);
        
        // Calculate distance to threat (higher is better when fleeing)
        double distanceToThreat = calculateDistance(node.x, node.y, threatX, threatY);
        
        // Invert and scale the threat distance to prioritize moving away
        // Lower values are better in A*, so we use a negative weight on threat distance
        return baseHeuristic - (distanceToThreat * 2.0); 
    }

    // Get movement cost for fleeing - penalizes moving toward threat
    private double getMovementCostForFleeing(World world, Node from, int toX, int toY, 
                                           int threatX, int threatY, Entity entity) {
        // Base movement cost
        double cost = getMovementCost(world, from, toX, toY, entity);
        
        // Check if moving closer to or away from threat
        double currentDist = calculateDistance(from.x, from.y, threatX, threatY);
        double newDist = calculateDistance(toX, toY, threatX, threatY);
        
        if (newDist < currentDist) {
            // Moving closer to threat - high penalty
            cost *= 3.0;
        } else if (newDist > currentDist) {
            // Moving away from threat - reward
            cost *= 0.5;
        }
        
        return cost;
    }

    // Utility method to calculate Euclidean distance
    private double calculateDistance(int x1, int y1, int x2, int y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Checks if there is a direct line of sight between two points.
     * Uses Bresenham's line algorithm to check all tiles between start and end.
     *
     * @param world The world grid.
     * @param startX Start x coordinate.
     * @param startY Start y coordinate.
     * @param endX End x coordinate.
     * @param endY End y coordinate.
     * @param entity The entity requesting the check (for passability rules).
     * @return true if there is direct line of sight, false otherwise.
     */
    public boolean hasLineOfSight(World world, int startX, int startY, int endX, int endY, Entity entity) {
        // If points are adjacent, always visible
        if (Math.abs(startX - endX) <= 1 && Math.abs(startY - endY) <= 1) {
            return isPassable(world, endX, endY, entity);
        }

        // Bresenham's line algorithm
        int dx = Math.abs(endX - startX);
        int dy = Math.abs(endY - startY);
        int sx = startX < endX ? 1 : -1;
        int sy = startY < endY ? 1 : -1;
        int err = dx - dy;
        int x = startX;
        int y = startY;

        while (x != endX || y != endY) {
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }

            // Skip start position
            if (x == startX && y == startY) continue;
            
            // Check if line of sight is blocked by an impassable tile
            if (!isPassable(world, x, y, entity)) {
                return false;
            }
        }

        return true;
    }

    // Helper method to generate a unique key for a node position
    private String nodeKey(Node node) {
        return nodeKey(node.x, node.y);
    }

    private String nodeKey(int x, int y) {
        return x + "," + y;
    }

    // Heuristic function (Manhattan distance - cheaper than Euclidean)
    private double heuristic(Node a, Node b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    // Reconstruct path from end node back to start node
    private List<int[]> reconstructPath(Node endNode) {
        LinkedList<int[]> path = new LinkedList<>();
        Node current = endNode;
        while (current.parent != null) {
            path.addFirst(new int[]{current.x, current.y});
            current = current.parent;
        }
        return path;
    }

    // Check if a tile is passable for a given entity
    private boolean isPassable(World world, int x, int y, Entity entity) {
        Tile tile = world.getTile(x, y);
        if (tile == null) return false;

        // Basic rule: No movement into water (can be refined per species)
        if (tile.getTerrainType() == TerrainType.WATER) {
            // Allow specific species through water later if needed
            return false;
        }

        // Add other checks: e.g., very steep hills might be impassable for slower entities
        if (tile.getTerrainType() == TerrainType.HILL && tile.getElevation() > 0.9) {
            // Very steep hills - only passable for entities with high speed
            return entity.getSpeed() > 1.2;
        }

        // Check for other impassable terrain types based on entity properties
        if (tile.getTerrainType() == TerrainType.DESERT && entity.getSpeed() < 0.8) {
            // Desert is hard to traverse for very slow entities
            return false;
        }

        return true;
    }

    // Get movement cost between current node and target coordinates
    private double getMovementCost(World world, Node from, int toX, int toY, Entity entity) {
        double cost = 1.0; // Base cost for adjacent tiles

        // Diagonal movement costs more (sqrt(2) ~= 1.414)
        if (from.x != toX && from.y != toY) {
            cost *= 1.414;
        }

        // Terrain cost modifier based on entity's speed and terrain type
        Tile toTile = world.getTile(toX, toY);
        if (toTile != null) {
            switch (toTile.getTerrainType()) {
                case HILL:
                    // Hills are harder to traverse, especially for slower entities
                    cost *= 1.5 + (1.0 / Math.max(0.5, entity.getSpeed()));
                    break;
                case FOREST:
                    // Forests slightly more effort, but could be easier for some entities
                    cost *= 1.2;
                    break;
                case DESERT:
                    // Deserts are harder for most entities
                    cost *= 1.1 + (0.2 / Math.max(0.5, entity.getSpeed()));
                    break;
                default:
                    break;
            }

            // Also factor in elevation changes
            double elevationChange = Math.abs(world.getTile(from.x, from.y).getElevation() - toTile.getElevation());
            cost += elevationChange * 0.5; // Penalize steep elevation changes
        }

        // Incorporate entity speed (inverse relationship - faster entities have lower cost)
        cost /= Math.sqrt(entity.getSpeed());

        return cost;
    }
}
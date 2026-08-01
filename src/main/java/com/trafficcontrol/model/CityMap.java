package com.trafficcontrol.model;

import com.trafficcontrol.exceptions.InvalidRouteException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * the whole city as a graph: intersections are nodes, roads are directed
 * edges. built once at startup (either by DefaultCityMapFactory today, or
 * a real file-based loader once persistence.PersistenceService is
 * implemented) and then only read from - so, unlike Intersection's
 * queues, this class doesn't need its own locking: nothing mutates it
 * after the simulation starts.
 */
public class CityMap {

    private final Map<String, Intersection> intersections = new LinkedHashMap<>();
    private final Map<String, Map<String, Road>> adjacency = new HashMap<>();
    private final List<Road> roads = new ArrayList<>();

    public void addIntersection(Intersection intersection) {
        intersections.put(intersection.getId(), intersection);
        adjacency.put(intersection.getId(), new HashMap<>());
    }

    public void addRoad(Road road) {
        roads.add(road);
        adjacency.get(road.getFrom().getId()).put(road.getTo().getId(), road);
    }

    public Intersection getIntersection(String id) {
        return intersections.get(id);
    }

    public Collection<Intersection> getIntersections() {
        return Collections.unmodifiableCollection(intersections.values());
    }

    public List<String> getIntersectionIds() {
        return new ArrayList<>(intersections.keySet());
    }

    public List<Road> getRoads() {
        return Collections.unmodifiableList(roads);
    }

    public Road getRoad(String fromId, String toId) {
        Map<String, Road> neighbors = adjacency.get(fromId);
        return neighbors == null ? null : neighbors.get(toId);
    }

    /**
     * shortest path (fewest hops) between two intersections, via a plain
     * breadth-first search over the road graph. good enough here since
     * every road segment is treated as roughly equal "cost" - a real
     * shortest-*distance* search would weight edges by length instead.
     */
    public List<Intersection> getRoute(String startId, String endId) throws InvalidRouteException {
        if (!intersections.containsKey(startId) || !intersections.containsKey(endId)) {
            throw new InvalidRouteException("unknown intersection id in route request: " + startId + " -> " + endId);
        }
        if (startId.equals(endId)) {
            throw new InvalidRouteException("origin and destination are the same intersection: " + startId);
        }

        Map<String, String> cameFrom = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        frontier.add(startId);
        visited.add(startId);

        while (!frontier.isEmpty()) {
            String currentId = frontier.poll();
            if (currentId.equals(endId)) {
                break;
            }
            for (String neighborId : adjacency.getOrDefault(currentId, Map.of()).keySet()) {
                if (visited.add(neighborId)) {
                    cameFrom.put(neighborId, currentId);
                    frontier.add(neighborId);
                }
            }
        }

        if (!visited.contains(endId)) {
            throw new InvalidRouteException("no path exists from " + startId + " to " + endId);
        }

        LinkedList<Intersection> path = new LinkedList<>();
        String cursor = endId;
        while (cursor != null) {
            path.addFirst(intersections.get(cursor));
            cursor = cameFrom.get(cursor);
        }
        return path;
    }
}

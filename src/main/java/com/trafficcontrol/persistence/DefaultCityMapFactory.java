package com.trafficcontrol.persistence;

import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.model.Position;
import com.trafficcontrol.model.Road;
import com.trafficcontrol.observer.TrafficEventPublisher;

/**
 * TEMPORARY bootstrap data. builds a hardcoded 3x3 grid city so the
 * simulation is runnable end-to-end right now, before Ayesha's real
 * CSV-based loader (persistence.PersistenceService, see
 * docs/TODO_Ayesha.md) exists. once that lands, Main.java should load
 * src/main/resources/maps/default-city.csv through it instead of calling
 * this class.
 */
public final class DefaultCityMapFactory {

    private static final int GRID_SIZE = 3;
    private static final double SPACING_METERS = 180.0;
    private static final int LANES = 2;

    private DefaultCityMapFactory() {
    }

    public static CityMap createDefaultGrid(TrafficEventPublisher publisher) {
        CityMap cityMap = new CityMap();
        Intersection[][] grid = new Intersection[GRID_SIZE][GRID_SIZE];

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                String id = "I" + row + col;
                Position position = new Position(col * SPACING_METERS, row * SPACING_METERS);
                Intersection intersection = new Intersection(id, position, publisher);
                grid[row][col] = intersection;
                cityMap.addIntersection(intersection);
            }
        }

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                if (col + 1 < GRID_SIZE) {
                    // eastward neighbor: approaching it you arrive from its west side, and vice versa
                    connect(cityMap, grid[row][col], grid[row][col + 1], Direction.WEST, Direction.EAST);
                }
                if (row + 1 < GRID_SIZE) {
                    // southward neighbor: approaching it you arrive from its north side, and vice versa
                    connect(cityMap, grid[row][col], grid[row + 1][col], Direction.NORTH, Direction.SOUTH);
                }
            }
        }

        return cityMap;
    }

    /** adds both directions of a two-way street between `a` and `b`. */
    private static void connect(CityMap cityMap, Intersection a, Intersection b, Direction approachAtB, Direction approachAtA) {
        cityMap.addRoad(new Road(a.getId() + "-" + b.getId(), a, b, SPACING_METERS, LANES, approachAtB));
        cityMap.addRoad(new Road(b.getId() + "-" + a.getId(), b, a, SPACING_METERS, LANES, approachAtA));
    }
}

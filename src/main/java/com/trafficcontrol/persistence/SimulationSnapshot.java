package com.trafficcontrol.persistence;

import com.trafficcontrol.model.CityMap;

import java.util.List;

/**
 * Immutable holder for a save/resume snapshot: the city map plus the
 * simulation statistics totals at the moment the snapshot was taken.
 *
 * Vehicles that were still travelling are retained as restart instructions:
 * type, origin, destination and optional label. They restart from their
 * origins because exact road/queue progress and light phases are not stored.
 */
public final class SimulationSnapshot {

    private final CityMap cityMap;
    private final long vehiclesSpawned;
    private final long vehiclesArrived;
    private final double averageWaitTimeMillis;
    private final double averageTravelTimeMillis;
    private final List<SavedVehicle> unfinishedVehicles;

    public record SavedVehicle(String type, String originId, String destinationId, String customLabel) {
    }

    public SimulationSnapshot(CityMap cityMap, long vehiclesSpawned, long vehiclesArrived,
                               double averageWaitTimeMillis, double averageTravelTimeMillis) {
        this(cityMap, vehiclesSpawned, vehiclesArrived, averageWaitTimeMillis,
                averageTravelTimeMillis, List.of());
    }

    public SimulationSnapshot(CityMap cityMap, long vehiclesSpawned, long vehiclesArrived,
                              double averageWaitTimeMillis, double averageTravelTimeMillis,
                              List<SavedVehicle> unfinishedVehicles) {
        this.cityMap = cityMap;
        this.vehiclesSpawned = vehiclesSpawned;
        this.vehiclesArrived = vehiclesArrived;
        this.averageWaitTimeMillis = averageWaitTimeMillis;
        this.averageTravelTimeMillis = averageTravelTimeMillis;
        this.unfinishedVehicles = List.copyOf(unfinishedVehicles);
    }

    public CityMap getCityMap() {
        return cityMap;
    }

    public long getVehiclesSpawned() {
        return vehiclesSpawned;
    }

    public long getVehiclesArrived() {
        return vehiclesArrived;
    }

    public double getAverageWaitTimeMillis() {
        return averageWaitTimeMillis;
    }

    public double getAverageTravelTimeMillis() {
        return averageTravelTimeMillis;
    }

    public List<SavedVehicle> getUnfinishedVehicles() {
        return unfinishedVehicles;
    }

    @Override
    public String toString() {
        return String.format(
                "snapshot[intersections=%d, roads=%d, spawned=%d, arrived=%d, unfinished=%d, avg-wait=%.2fms, avg-travel=%.2fms]",
                cityMap.getIntersections().size(), cityMap.getRoads().size(),
                vehiclesSpawned, vehiclesArrived, unfinishedVehicles.size(),
                averageWaitTimeMillis, averageTravelTimeMillis);
    }
}

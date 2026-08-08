package com.trafficcontrol.persistence;

import com.trafficcontrol.model.CityMap;

/**
 * Immutable holder for a save/resume snapshot: the city map plus the
 * simulation statistics totals at the moment the snapshot was taken.
 *
 * Deliberately does NOT capture individual vehicle positions or
 * in-progress light-cycle state - see docs/ProjectReport.md's File
 * Handling section for why full vehicle-level resume was left as a
 * nice-to-have rather than implemented. Resuming from a snapshot means
 * starting a fresh simulation on the saved CityMap with the saved
 * counters as a starting point, not literally replaying vehicles
 * mid-trip.
 */
public final class SimulationSnapshot {

    private final CityMap cityMap;
    private final long vehiclesSpawned;
    private final long vehiclesArrived;
    private final double averageWaitTimeMillis;
    private final double averageTravelTimeMillis;

    public SimulationSnapshot(CityMap cityMap, long vehiclesSpawned, long vehiclesArrived,
                               double averageWaitTimeMillis, double averageTravelTimeMillis) {
        this.cityMap = cityMap;
        this.vehiclesSpawned = vehiclesSpawned;
        this.vehiclesArrived = vehiclesArrived;
        this.averageWaitTimeMillis = averageWaitTimeMillis;
        this.averageTravelTimeMillis = averageTravelTimeMillis;
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

    @Override
    public String toString() {
        return String.format(
                "snapshot[intersections=%d, roads=%d, spawned=%d, arrived=%d, avg-wait=%.2fms, avg-travel=%.2fms]",
                cityMap.getIntersections().size(), cityMap.getRoads().size(),
                vehiclesSpawned, vehiclesArrived, averageWaitTimeMillis, averageTravelTimeMillis);
    }
}

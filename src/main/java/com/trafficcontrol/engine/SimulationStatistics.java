package com.trafficcontrol.engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * running totals for the simulation, updated from several threads at once
 * (spawner increments spawned, mover records arrivals). every field is an
 * Atomic* so updates never need a lock - there's no invariant linking the
 * counters to each other that would require them to change together.
 */
public class SimulationStatistics {

    private final AtomicLong vehiclesSpawned = new AtomicLong();
    private final AtomicLong vehiclesArrived = new AtomicLong();
    private final AtomicLong totalWaitTimeMillis = new AtomicLong();
    private final AtomicLong totalTravelTimeMillis = new AtomicLong();

    public void recordSpawn() {
        vehiclesSpawned.incrementAndGet();
    }

    public void recordArrival(long waitTimeMillis, long travelTimeMillis) {
        vehiclesArrived.incrementAndGet();
        totalWaitTimeMillis.addAndGet(waitTimeMillis);
        totalTravelTimeMillis.addAndGet(travelTimeMillis);
    }

    public long getVehiclesSpawned() {
        return vehiclesSpawned.get();
    }

    public long getVehiclesArrived() {
        return vehiclesArrived.get();
    }

    public long getVehiclesInTransit() {
        return Math.max(0, vehiclesSpawned.get() - vehiclesArrived.get());
    }

    public double getAverageWaitTimeMillis() {
        long arrived = vehiclesArrived.get();
        return arrived == 0 ? 0.0 : totalWaitTimeMillis.get() / (double) arrived;
    }

    public double getAverageTravelTimeMillis() {
        long arrived = vehiclesArrived.get();
        return arrived == 0 ? 0.0 : totalTravelTimeMillis.get() / (double) arrived;
    }

    @Override
    public String toString() {
        return String.format(
                "spawned=%d, arrived=%d, in-transit=%d, avg-wait=%.0fms, avg-travel=%.0fms",
                getVehiclesSpawned(), getVehiclesArrived(), getVehiclesInTransit(),
                getAverageWaitTimeMillis(), getAverageTravelTimeMillis());
    }
}

package com.trafficcontrol.model;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * a one-directional stretch of street from one intersection to another.
 * a normal two-way street between A and B is modelled as two Road
 * objects (A->B and B->A) so each direction can be reasoned about
 * independently - see DefaultCityMapFactory.
 *
 * vehiclesOnRoad is read by VehicleMover (one thread) and by the gui for
 * rendering (another thread), so it's a CopyOnWriteArrayList: reads
 * (frequent, from the gui) are lock-free, writes (spawn/arrive, much
 * rarer) pay the copy cost. a plain synchronized list would make every
 * gui repaint contend with the mover thread for no reason.
 */
public class Road {

    private final String id;
    private final Intersection from;
    private final Intersection to;
    private final double lengthMeters;
    private final int lanes;
    private final Direction approachDirection; // which side of `to` this road enters from
    private final List<Vehicle> vehiclesOnRoad = new CopyOnWriteArrayList<>();

    public Road(String id, Intersection from, Intersection to, double lengthMeters, int lanes, Direction approachDirection) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.lengthMeters = lengthMeters;
        this.lanes = lanes;
        this.approachDirection = approachDirection;
    }

    public String getId() {
        return id;
    }

    public Intersection getFrom() {
        return from;
    }

    public Intersection getTo() {
        return to;
    }

    public double getLengthMeters() {
        return lengthMeters;
    }

    public int getLanes() {
        return lanes;
    }

    /** the direction a vehicle finishing this road will queue as at the `to` intersection. */
    public Direction getApproachDirection() {
        return approachDirection;
    }

    public long getTravelTimeMillis(double speedMetersPerSecond) {
        return Math.round((lengthMeters / speedMetersPerSecond) * 1000);
    }

    public void addVehicle(Vehicle vehicle) {
        vehiclesOnRoad.add(vehicle);
    }

    public void removeVehicle(Vehicle vehicle) {
        vehiclesOnRoad.remove(vehicle);
    }

    /**
     * read-only view for the mover and the gui. wrapped so callers can't add or
     * remove vehicles behind this road's back; iteration stays safe without
     * locking because the underlying list is copy-on-write.
     */
    public List<Vehicle> getVehiclesOnRoad() {
        return Collections.unmodifiableList(vehiclesOnRoad);
    }

    /** rough congestion signal: how full this road is relative to a naive per-lane capacity. */
    public double getCongestionRatio() {
        int capacity = Math.max(1, lanes) * 6;
        return Math.min(1.0, vehiclesOnRoad.size() / (double) capacity);
    }

    @Override
    public String toString() {
        return id;
    }
}
